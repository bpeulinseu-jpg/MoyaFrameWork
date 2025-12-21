package com.server.core.system.resource;

import com.server.core.CorePlugin;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.security.MessageDigest;

public class WebServerManager {

    private final CorePlugin plugin;
    private HttpServer server;
    private int port = 8123; // 기본 포트
    private String host = "127.0.0.1"; // 외부 IP 설정 필요

    public WebServerManager(CorePlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        // config.yml에서 설정 불러오기 (없으면 기본값)
        this.port = plugin.getConfig().getInt("http.port", 8123);
        this.host = plugin.getConfig().getString("http.host", "127.0.0.1");
    }

    public void start() {
        stop(); // 이미 켜져있으면 끄고 재시작
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);

            // "/resourcepack.zip" 경로로 요청이 오면 파일 전송
            server.createContext("/resourcepack.zip", exchange -> {
                File file = new File(plugin.getDataFolder(), "resourcepack.zip");

                if (!file.exists()) {
                    String response = "Resource pack not ready.";
                    exchange.sendResponseHeaders(404, response.length());
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes());
                    }
                    return;
                }

                byte[] bytes = Files.readAllBytes(file.toPath());
                exchange.getResponseHeaders().add("Content-Type", "application/zip");
                exchange.sendResponseHeaders(200, bytes.length);

                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            });

            server.setExecutor(null);
            server.start();
            plugin.getLogger().info("🌐 HTTP 서버 시작됨: http://" + host + ":" + port + "/resourcepack.zip");

        } catch (IOException e) {
            plugin.getLogger().severe("❌ HTTP 서버 시작 실패! 포트(" + port + ")가 사용 중이거나 권한이 없습니다.");
            e.printStackTrace();
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    public String getDownloadUrl() {
        return "http://" + host + ":" + port + "/resourcepack.zip";
    }

    // SHA-1 해시 계산 (클라이언트 캐싱 및 변경 감지용)
    public byte[] getPackHash() {
        File file = new File(plugin.getDataFolder(), "resourcepack.zip");
        if (!file.exists()) return null;

        try (FileInputStream fis = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[8192];
            int n;
            while ((n = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, n);
            }
            return digest.digest();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}