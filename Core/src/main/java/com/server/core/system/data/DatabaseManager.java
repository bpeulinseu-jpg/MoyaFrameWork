package com.server.core.system.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.server.core.CorePlugin;
import org.bukkit.Bukkit;

import java.io.File;
import java.sql.*;
import java.util.concurrent.CompletableFuture;

public class DatabaseManager {

    private final CorePlugin plugin;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private Connection connection;
    private final String dbUrl;

    public DatabaseManager(CorePlugin plugin) {
        this.plugin = plugin;
        // 플러그인 데이터 폴더 안에 database.db 파일 생성
        this.dbUrl = "jdbc:sqlite:" + new File(plugin.getDataFolder(), "database.db").getAbsolutePath();

        initialize();
    }

    private void initialize() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            // 테이블 생성 (없으면)
            // uuid와 data_key를 합쳐서 고유 키로 사용
            String sql = "CREATE TABLE IF NOT EXISTS player_data (" +
                    "uuid VARCHAR(36) NOT NULL, " +
                    "data_key VARCHAR(64) NOT NULL, " +
                    "json_value TEXT, " +
                    "PRIMARY KEY (uuid, data_key)" +
                    ");";
            stmt.execute(sql);
            plugin.getLogger().info("💾 데이터베이스(SQLite) 연결 및 초기화 완료.");
        } catch (SQLException e) {
            plugin.getLogger().severe("❌ 데이터베이스 초기화 실패!");
            e.printStackTrace();
        }
    }

    // SQLite 연결 객체 반환
    private Connection getConnection() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            return connection;
        }
        connection = DriverManager.getConnection(dbUrl);
        return connection;
    }

    /**
     * 데이터 비동기 저장 (Save Async)
     * @param uuid 플레이어 UUID
     * @param key 데이터 키 (예: "stats")
     * @param data 저장할 객체 (자동으로 JSON 변환됨)
     */
    public <T> void saveData(String uuid, String key, T data) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String json = gson.toJson(data);
            String sql = "INSERT OR REPLACE INTO player_data (uuid, data_key, json_value) VALUES (?, ?, ?)";

            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, uuid);
                pstmt.setString(2, key);
                pstmt.setString(3, json);
                pstmt.executeUpdate();

                // plugin.getLogger().info("데이터 저장 완료: " + key + " (" + uuid + ")");
            } catch (SQLException e) {
                plugin.getLogger().severe("데이터 저장 실패: " + key);
                e.printStackTrace();
            }
        });
    }

    /**
     * 데이터 동기 로드 (Load Sync) - 주의: 메인 스레드에서 호출 시 렉 유발 가능성 있음
     * @param uuid 플레이어 UUID
     * @param key 데이터 키
     * @param type 변환할 클래스 타입 (예: MyStats.class)
     * @return 저장된 객체 또는 null
     */
    public <T> T loadData(String uuid, String key, Class<T> type) {
        String sql = "SELECT json_value FROM player_data WHERE uuid = ? AND data_key = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, uuid);
            pstmt.setString(2, key);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String json = rs.getString("json_value");
                    return gson.fromJson(json, type);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("데이터 로드 실패: " + key);
            e.printStackTrace();
        }
        return null; // 데이터 없음
    }

    /**
     * 데이터 비동기 로드 (Load Async) - 권장
     * @return CompletableFuture (비동기 결과)
     */
    public <T> CompletableFuture<T> loadDataAsync(String uuid, String key, Class<T> type) {
        return CompletableFuture.supplyAsync(() -> loadData(uuid, key, type));
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}