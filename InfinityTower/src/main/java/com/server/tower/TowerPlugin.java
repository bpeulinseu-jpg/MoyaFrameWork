package com.server.tower;
// 코어 및 버킷 api
import com.server.core.api.CoreAddon;
import com.server.core.api.CoreProvider;
import com.server.tower.game.*;
import com.server.tower.item.EnhanceManager;
import com.server.tower.item.ItemGenerator;
import com.server.tower.system.transcendence.TranscendenceGui;
import com.server.tower.system.transcendence.TranscendenceManager;
import com.server.tower.ui.RepairUI;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import com.server.core.api.builder.ItemBuilder;

// 자체 import
import com.server.tower.user.UserListener;
import com.server.tower.user.UserManager;
import com.server.tower.user.TowerUserData;
import com.server.tower.item.ItemRegistry;
import com.server.tower.mob.MobRegistry;
import com.server.tower.game.perk.PerkListener;
import com.server.tower.game.perk.PerkRegistry;
import com.server.tower.ui.SocketingUI;
import com.server.tower.ui.EnhanceUI;
import com.server.tower.game.DungeonListener;
import com.server.tower.game.RegenListener;

public class TowerPlugin extends JavaPlugin implements CoreAddon {

    // 변수
    private static TowerPlugin instance;
    private UserManager userManager;
    private ItemRegistry itemRegistry;
    private GameManager gameManager;
    private MobRegistry mobRegistry;
    private PerkRegistry perkRegistry;
    private ShopManager shopManager;
    private SocketingUI socketingUI;
    private EnhanceUI enhanceUI;
    private RepairUI repairUI;
    private PerkListener perkListener;
    private EnhanceManager enhanceManager;
    private TranscendenceManager transcendenceManager;


    @Override
    public void onEnable() {
        instance = this;

        // 3초마다 재생 태스크 실행
        new RegenTask().runTaskTimer(this, 60L, 60L);

        // Core에 등록
        CoreProvider.registerAddon(this);

        //매니저 초기화
        this.userManager = new UserManager(this);
        this.itemRegistry = new ItemRegistry(this);
        this.gameManager = new GameManager(this);
        this.mobRegistry = new MobRegistry(this);
        this.perkRegistry = new PerkRegistry(this);
        this.shopManager = new ShopManager(this);
        this.socketingUI = new SocketingUI(this);
        this.perkListener = new PerkListener(this);
        this.repairUI = new RepairUI(this);
        this.transcendenceManager = new TranscendenceManager(this);
        // [수정] 매니저를 먼저 생성
        this.enhanceManager = new EnhanceManager();
        // UI에 매니저를 주입
        this.enhanceUI = new EnhanceUI(this, this.enhanceManager);

        // 리소스 등록 실행
        this.itemRegistry.registerAll();
        mobRegistry.registerAll();
        perkRegistry.registerAll();

        //리스너 등록
        getServer().getPluginManager().registerEvents(new UserListener(userManager), this);
        getServer().getPluginManager().registerEvents(new CombatListener(), this);
        getServer().getPluginManager().registerEvents(new GameListener(gameManager), this);
        getServer().getPluginManager().registerEvents(new LobbyListener(this), this);
        getServer().getPluginManager().registerEvents(new EquipmentListener(), this);
        getServer().getPluginManager().registerEvents(new DungeonListener(this), this);
        getServer().getPluginManager().registerEvents(perkListener, this);
        getServer().getPluginManager().registerEvents(new DurabilityListener(), this);
        getServer().getPluginManager().registerEvents(new RegenListener(), this);
        getServer().getPluginManager().registerEvents(new ArmorListener(this), this);

        //명령어 등록
        if (getCommand("tower") != null) getCommand("tower").setExecutor(this);


        getLogger().info("🏰 Moya's Infinity Tower가 준비되었습니다.");
    }

    @Override
    public void onCoreReady() {
        getLogger().info("⚔️ 게임 로직을 시작합니다.");

        // 리로드 시 온라인 플레이어 데이터 다시 로드 (개발 편의성)
        getServer().getOnlinePlayers().forEach(userManager::loadUser);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return false;

        // 상점 열기 (tower shop)
        if (args[0].equalsIgnoreCase("shop")) {
            shopManager.openStatShop(player);
            return true;
        }

        //아이템 강화 ui 열기 (tower enhance)
            if (args[0].equalsIgnoreCase("enhance")) {
                enhanceUI.open(player);
                return true;
            }

        // 보석 세공 gui 열기 (tower socket)
        if (args[0].equalsIgnoreCase("socket")) {
            socketingUI.open(player);
            return true;
        }

        // 보석 아이템 받기 (테스트용)
        if (args[0].equalsIgnoreCase("gem")) {
            player.getInventory().addItem(itemRegistry.createGem("str", 10));
            player.sendMessage("§a힘의 보석 획득");
            return true;
        }


        // 던전 입장 (tower enter)
        if (args[0].equalsIgnoreCase("enter")) {
            gameManager.startGame(player);
            return true;
        }

        // 던전 퇴장 (tower leave)
        if (args[0].equalsIgnoreCase("leave")) {
            gameManager.endGame(player);
            return true;
        }

        // 무기지급 (tower weapon <sword/wand>
        if (args[0].equalsIgnoreCase("weapon") && args.length > 1) {
            String type = args[1].toLowerCase();
            // ID: infinity_tower:beginner_sword
            String id = "infinity_tower:beginner_" + type;

            // 아이템 생성 시도
            org.bukkit.inventory.ItemStack item = ItemBuilder.from(id).build();

            // ItemBuilder는 실패 시 배리어(Error)를 반환하므로 그대로 지급
            player.getInventory().addItem(item);
            player.sendMessage("§a무기 지급 시도: " + id);
            return true;
        }

        // [신규] 랜덤 장비 생성 (/tower gen)
        if (args[0].equalsIgnoreCase("gen")) {
            int level = args.length > 1 ? Integer.parseInt(args[1]) : 1;
            ItemStack randomItem = ItemGenerator.generateWeapon(level);
            player.getInventory().addItem(randomItem);
            player.sendMessage("§a랜덤 장비 생성 완료 (Lv." + level + ")");
            return true;
        }

        // 아레나 위치 설정 (tower setarena)
        if (args[0].equalsIgnoreCase("setarena")) {
            gameManager.setArenaCenter(player.getLocation());
            player.sendMessage("§a현재 위치가 던전 아레나 중심으로 설정되었습니다.");
            return true;
        }

        // 초월 gui 열기 (tower transcend)
        if (args[0].equalsIgnoreCase("transcend")) {
            new TranscendenceGui(this).open(player);
        }

        // 랜덤 방어구 생성 (/tower armor)
        if (args[0].equalsIgnoreCase("armor")) {
            int level = args.length > 1 ? Integer.parseInt(args[1]) : 1;
            ItemStack armor = com.server.tower.item.ArmorGenerator.generateArmor(level);
            player.getInventory().addItem(armor);
            player.sendMessage("§a방어구 생성 완료 (Lv." + level + ")");
            return true;
        }

        // stat 수정
        if (args.length > 0) {
            // /tower stat <str|vit> <amount>
            if (args[0].equalsIgnoreCase("stat") && args.length > 2) {
                String type = args[1].toLowerCase();
                int amount = Integer.parseInt(args[2]);

                // 1. 내 데이터 가져오기
                TowerUserData data = userManager.getUser(player);
                if (data == null) {
                    player.sendMessage("§c데이터가 로드되지 않았습니다.");
                    return true;
                }

                // 2. 데이터 수정
                switch (type) {
                    case "str" -> data.statStr = amount;
                    case "vit" -> data.statVit = amount;
                    case "dex" -> data.statDex = amount;
                    case "int" -> data.statInt = amount;
                    case "gold" -> data.gold = amount;
                    case "luk" -> data.statLuk = amount;
                    case "etr" -> data.ether = amount;
                }

                // 3. Core에 반영 및 스코어보드 갱신
                userManager.applyStatsToCore(player, data);
                userManager.updateSidebar(player);

                // 4. DB 저장 (비동기)
                userManager.saveUser(player);

                player.sendMessage("§a[Tower] 스탯 변경 완료: " + type + " = " + amount);
                return true;
            }
        }
        return false;
    }

    @Override
    public void onReload() {
        reloadConfig();
    }

    private void registerResources() {
        // 추후 아이템, 몬스터, GUI 이미지 등록
    }

    @Override
    public String getNamespace() {
        return "infinity_tower";
    }

    @Override
    public JavaPlugin getPlugin() {
        return this;
    }
    //스태틱
    public static TowerPlugin getInstance() { return instance; }
    //getter
    public PerkRegistry getPerkRegistry() { return perkRegistry; }
    public UserManager getUserManager() { return userManager; }
    public ShopManager getShopManager() { return shopManager; }
    public GameManager getGameManager() { return gameManager; }
    public EnhanceUI getEnhanceUI() { return enhanceUI; }
    public SocketingUI getSocketingUI() { return socketingUI; }
    public PerkListener getPerkListener() { return perkListener; }
    public RepairUI getRepairUI() { return repairUI; }
    public TranscendenceManager getTranscendenceManager() {
        return transcendenceManager;
    }
}