package com.server.core;

import com.server.core.api.CoreAddon;
import com.server.core.listener.BlockListener;
import com.server.core.system.addon.AddonManager;
import com.server.core.system.block.BlockManager;
import com.server.core.system.display.ActionBarManager;
import com.server.core.system.gimmick.GimmickManager;
import com.server.core.system.glyph.GlyphManager;
import com.server.core.system.gui.GuiManager;
import com.server.core.system.data.DataManager;
import com.server.core.system.item.ItemManager;
import com.server.core.system.resource.ResourcePackManager;
import com.server.core.system.resource.WebServerManager;
import com.server.core.system.browser.GlyphBrowser;
import com.server.core.system.browser.SoundBrowser;
import com.server.core.system.world.MapManager;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import com.server.core.listener.PlayerListener;
import com.server.core.system.browser.ItemBrowser;
import com.server.core.system.display.BossBarManager;
import com.server.core.system.display.TitleManager;
import com.server.core.system.display.SidebarManager;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import com.server.core.system.data.DatabaseManager;
import com.server.core.system.stat.StatManager;
import com.server.core.system.session.SessionManager;
import com.server.core.system.mob.MobManager;
import com.server.core.system.mob.MobListener;
import com.server.core.system.browser.MobBrowser;
import com.server.core.system.cooldown.CooldownManager;
import com.server.core.system.projectile.ProjectileManager;
import com.server.core.system.combat.DamageManager;

public class CorePlugin extends JavaPlugin implements Listener {

    //             변수 목록
    private static CorePlugin instance;
    private AddonManager addonManager;
    private ResourcePackManager resourcePackManager;
    private GlyphManager glyphManager;
    private ItemManager itemManager;
    private ActionBarManager actionBarManager;
    private GuiManager guiManager;
    private WebServerManager webServerManager;
    private ItemBrowser itemBrowser;
    private BossBarManager bossBarManager;
    private TitleManager titleManager;
    private SidebarManager sidebarManager;
    private DataManager dataManager;
    private GlyphBrowser glyphBrowser;
    private SoundBrowser soundBrowser;
    private BlockManager blockManager;
    private DatabaseManager databaseManager;
    private StatManager statManager;
    private SessionManager sessionManager;
    private MobManager mobManager;
    private MobBrowser mobBrowser;
    private CooldownManager cooldownManager;
    private ProjectileManager projectileManager;
    private DamageManager damageManager;
    private MapManager mapManager;
    private GimmickManager gimmickManager;

    @Override
    public void onEnable() {
        instance = this;
        if (!getDataFolder().exists()) getDataFolder().mkdirs();

        // [추가] Config 기본값 설정
        saveDefaultConfig();

        // 매니저 초기화
        this.databaseManager = new DatabaseManager(this);
        this.statManager = new StatManager(this);
        this.sessionManager = new SessionManager(this);
        this.addonManager = new AddonManager(this);
        this.resourcePackManager = new ResourcePackManager(this);
        this.glyphManager = new GlyphManager(this);
        this.itemManager = new ItemManager(this);
        this.actionBarManager = new ActionBarManager(this);
        this.guiManager = new GuiManager(this);
        this.webServerManager = new WebServerManager(this);
        this.itemBrowser = new ItemBrowser(this);
        this.bossBarManager = new BossBarManager(this);
        this.titleManager = new TitleManager(this);
        this.sidebarManager = new SidebarManager(this);
        this.dataManager = new DataManager(this);
        this.glyphBrowser = new GlyphBrowser(this);
        this.soundBrowser = new SoundBrowser(this);
        this.itemManager = new ItemManager(this);
        this.blockManager = new BlockManager(this);
        this.mobManager = new MobManager(this);
        this.mobBrowser = new MobBrowser(this);
        this.cooldownManager = new CooldownManager(this);
        this.projectileManager = new ProjectileManager(this);
        this.damageManager = new DamageManager(this);
        this.mapManager = new MapManager(this);
        this.gimmickManager = new GimmickManager(this);

        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("CoreFramework Enabled!");
        getServer().getPluginManager().registerEvents(this, this);
        //                    리스너
        getServer().getPluginManager().registerEvents(new PlayerListener(), this);
        getServer().getPluginManager().registerEvents(new BlockListener(), this);
        getServer().getPluginManager().registerEvents(new MobListener(), this);

        getLogger().info("CoreFramework Enabled!");
    }

    @Override
    public void onDisable() {
        // 서버 꺼질 때 기믹들 남지 않게 정리
        if (gimmickManager != null) gimmickManager.removeAll();

        if (databaseManager != null)
            databaseManager.close();

        if (webServerManager != null) {
            webServerManager.stop();
        }
    }

    // [핵심] 서버 로딩 완료 시점 (모든 플러그인 onEnable 이후)
    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {

        // 1. 리소스팩 생성 (오래 걸리므로 비동기 권장하지만, 순서를 위해 동기 처리 혹은 콜백 필요)
        // 여기서는 간단하게 호출만 합니다.
        resourcePackManager.generatePack();

        // 2. 애드온들에게 "준비 끝" 알림
        // (주의: 리소스팩 생성이 비동기라면, 생성이 끝난 후 콜백으로 호출해야 완벽함.
        //  현재 단계에서는 동시에 실행해도 큰 문제는 없음)
        addonManager.notifyCoreReady();
    }

    // [명령어] /core reload 구현
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.isOp()) return true;

            sender.sendMessage("§e[Core] 시스템을 리로드합니다...");

            // 1. Core 설정 리로드 (필요시)
            reloadConfig();

            // 2. 리소스팩 재생성
            resourcePackManager.generatePack();

            // 3. 애드온 전파
            addonManager.notifyReload();

            sender.sendMessage("§a[Core] 리로드 완료!");
            return true;
        }
        if (args[0].equalsIgnoreCase("items")) {
            if (sender instanceof Player player) {
                itemBrowser.open(player, 0); // 0페이지 열기
            } else {
                sender.sendMessage("플레이어만 사용 가능합니다.");
            }
            return true;
        }

        // 글리프 브라우저 (core glyphs)
        if (args[0].equalsIgnoreCase("glyphs")) {
            if (sender instanceof Player player) glyphBrowser.open(player, 0);
            return true;
        }

        // 사운드 브라우저 (core sounds)
        if (args[0].equalsIgnoreCase("sounds")) {
            if (sender instanceof Player player) soundBrowser.open(player, 0);
            return true;
        }

        // 몬스터 브라우저 (core mobs)
        if (args[0].equalsIgnoreCase("mobs")) {
            if (sender instanceof Player player) {
                mobBrowser.open(player, 0);
            }
            return true;
        }


        // 인스펙터 (core inspect <item/hud>)
        if (args[0].equalsIgnoreCase("inspect")) {
            if (!(sender instanceof Player player)) return true;

            if (args.length < 2) {
                player.sendMessage("§c사용법: /core inspect <item|hud>");
                return true;
            }

            // A. 아이템 검사
            if (args[1].equalsIgnoreCase("item")) {
                ItemStack item = player.getInventory().getItemInMainHand();
                if (item.getType().isAir()) {
                    player.sendMessage("§c손에 아이템이 없습니다.");
                    return true;
                }

                player.sendMessage("§8§m                                       ");
                player.sendMessage("§6🔍 아이템 상세 정보 (Inspector)");
                player.sendMessage("§fType: §7" + item.getType());

                if (item.hasItemMeta()) {
                    if (item.getItemMeta().hasCustomModelData()) {
                        player.sendMessage("§fCMD: §b" + item.getItemMeta().getCustomModelData());
                    }

                    // PDC(NBT) 데이터 덤프 (안전한 방식)
                    PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
                    if (!pdc.getKeys().isEmpty()) {
                        player.sendMessage("§e[ Data Tags ]");
                        for (NamespacedKey key : pdc.getKeys()) {
                            String val = "Unknown Type";

                            // [수정] has() 메소드로 타입을 먼저 체크해야 에러가 안 납니다.
                            if (pdc.has(key, PersistentDataType.STRING)) {
                                val = pdc.get(key, PersistentDataType.STRING);
                            } else if (pdc.has(key, PersistentDataType.INTEGER)) {
                                val = String.valueOf(pdc.get(key, PersistentDataType.INTEGER));
                            } else if (pdc.has(key, PersistentDataType.BYTE)) { // Boolean 등
                                val = String.valueOf(pdc.get(key, PersistentDataType.BYTE));
                            } else if (pdc.has(key, PersistentDataType.DOUBLE)) {
                                val = String.valueOf(pdc.get(key, PersistentDataType.DOUBLE));
                            } else if (pdc.has(key, PersistentDataType.LONG)) {
                                val = String.valueOf(pdc.get(key, PersistentDataType.LONG));
                            }

                            player.sendMessage(" - §7" + key.getKey() + ": §f" + val);
                        }
                    }
                }
                player.sendMessage("§8§m                                       ");
            }

            // B. HUD 검사
            if (args[1].equalsIgnoreCase("hud")) {
                for (String line : actionBarManager.getDebugInfo(player)) {
                    player.sendMessage(line);
                }
            }
            return true;
        }


        if (args[0].equalsIgnoreCase("addons")) {
            sender.sendMessage("§8§m                                       ");
            sender.sendMessage("§6[ CoreFramework Addons ]");

            int activeCount = 0;
            int errorCount = 0;

            for (AddonManager.AddonInfo info : addonManager.getAddonList()) {
                String statusIcon = switch (info.status) {
                    case ACTIVE -> "§a[✔]";
                    case ERROR -> "§c[✘]";
                    default -> "§7[-]";
                };

                String version = info.addon.getPlugin().getDescription().getVersion();

                // 출력 포맷: [✔] namespace (v1.0) - 정상
                sender.sendMessage(String.format("%s §f%s §7(v%s) - %s",
                        statusIcon,
                        info.addon.getNamespace(),
                        version,
                        info.status.display));

                if (info.status == AddonManager.AddonStatus.ACTIVE) activeCount++;
                if (info.status == AddonManager.AddonStatus.ERROR) errorCount++;
            }

            sender.sendMessage("");
            sender.sendMessage("§f총합: §e" + addonManager.getAddonList().size() + "개 " +
                    "(§a정상 " + activeCount + "§f, §c오류 " + errorCount + "§f)");
            sender.sendMessage("§8§m                                       ");
            return true;
        }

        return false;

    }


    // --- 스태틱 매니저 ---
    public static CorePlugin getInstance() { return instance; }
    public static AddonManager getAddonManager() { return instance.addonManager; }
    public static ResourcePackManager getResourcePackManager() { return instance.resourcePackManager; }
    public static GlyphManager getGlyphManager() { return instance.glyphManager; }
    public static ItemManager getItemManager() { return instance.itemManager; }
    public static ActionBarManager getHudManager() { return instance.actionBarManager; }
    public static GuiManager getGuiManager() { return instance.guiManager; }
    public static WebServerManager getWebServerManager() { return instance.webServerManager; }
    public static BossBarManager getBossBarManager() { return instance.bossBarManager; }
    public static TitleManager getTitleManager() { return instance.titleManager; }
    public static SidebarManager getSidebarManager() { return instance.sidebarManager; }
    public static DataManager getDataManager() { return instance.dataManager; }
    public static BlockManager getBlockManager() { return instance.blockManager; }
    public static DatabaseManager getDatabaseManager() { return instance.databaseManager; }
    public static StatManager getStatManager() { return instance.statManager; }
    public static SessionManager getSessionManager() { return instance.sessionManager; }
    public static MobManager getMobManager() { return instance.mobManager; }
    public static CooldownManager getCooldownManager() { return instance.cooldownManager; }
    public static ProjectileManager getProjectileManager() { return instance.projectileManager; }
    public static DamageManager getDamageManager() { return instance.damageManager; }
    public static MapManager getMapManager() { return instance.mapManager; }
    public static GimmickManager getGimmickManager() { return instance.gimmickManager; }

    public static void registerAddon(CoreAddon addon) {
        if (instance != null) instance.addonManager.register(addon);
    }
}