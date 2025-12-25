package com.server.addon.test;

import com.server.core.CorePlugin;
import com.server.core.api.CoreAddon;
import com.server.core.api.CoreProvider; // API 사용 권장
import com.server.core.api.builder.ItemBuilder;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.bukkit.block.Block;
import com.server.core.system.block.BlockManager;
import com.server.core.api.event.CustomBlockInteractEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import com.server.core.api.event.SessionLevelUpEvent;
import org.bukkit.entity.EntityType;
import com.server.core.api.builder.MobBuilder;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class TestAddonPlugin extends JavaPlugin implements CoreAddon, Listener {

    @Override
    public void onEnable() {
        CoreProvider.registerAddon(this);
        prepareResources();

        // 명령어 등록 (plugin.yml에 없어도 작동)
        if (getCommand("test") != null) getCommand("test").setExecutor(this);

        // 리스너 이벤트 등록
        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("TestAddon 대기 중...");

    }

    @Override
    public void onCoreReady() {
        getLogger().info("🚀 TestAddon 로직 시작 (Core Ready)");
    }

    @Override
    public void onReload() {
        reloadConfig();
        getLogger().info("TestAddon 리로드 됨");
    }

    // 레벨업 이벤트 리스너
    /*
    @EventHandler
    public void onSessionLevelUp(SessionLevelUpEvent event) {
        Player player = event.getPlayer();
        int level = event.getNewLevel();

        player.sendMessage("§b[Level Up!] §f레벨 " + level + " 달성! 보너스를 선택하세요.");

        // GUI 생성 (퍽 선택창)
        Inventory gui = CoreProvider.openGui(player, "test_addon:menu_bg", "레벨업 보너스 선택", -16, 3, true);

        // 퍽 1: 공격력 증가
        ItemStack perkStr = ItemBuilder.from("test_addon:bat") // 아이콘 재활용
                .name("§c[공격력 강화]")
                .lore("§7이번 판 동안 공격력 +10")
                .build();

        CoreProvider.setGuiButton(gui, 11, perkStr, (e) -> {
            CoreProvider.addSessionStat(player, "str", 10); // 세션 스탯 추가
            player.sendMessage("§c공격력이 증가했습니다!");
            player.closeInventory();
        });

        // 퍽 2: 체력 회복
        ItemStack perkHeal = ItemBuilder.from("test_addon:heart") // 아이콘 재활용
                .name("§a[완전 회복]")
                .lore("§7체력을 모두 회복합니다.")
                .build();

        CoreProvider.setGuiButton(gui, 15, perkHeal, (e) -> {
            player.setHealth(player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue());
            player.sendMessage("§a체력이 회복되었습니다!");
            player.closeInventory();
        });
    }
    */


    private void prepareResources() {
        // 1. 아이템
        // 빠다
        saveResourceIfNotExists("bat.png");
        File batFile = new File(getDataFolder(), "bat.png");
        CoreProvider.registerItem(this, "bat", Material.PAPER, new File(getDataFolder(), "bat.png"), "배트맨");

        // 루비
        saveResourceIfNotExists("test_ruby.png");
        File rubyFile = new File(getDataFolder(), "test_ruby.png");
        CoreProvider.registerItem(this, "ruby", Material.PAPER, rubyFile, "§c신비한 루비");

        // 빛마법 이팩트
        saveResourceIfNotExists("light_effect.png");
        File lightFile = new File(getDataFolder(), "light_effect.png");
        CoreProvider.registerItem(this, "light", Material.FIRE_CHARGE, lightFile, "§c빛");

        // 마법 지팡이
        saveResourceIfNotExists("wand.png");
        File wandFile = new File(getDataFolder(), "wand.png");
        CoreProvider.registerItem(this, "wand", Material.DIAMOND_AXE, wandFile, "§b초보자 지팡이");

        //============================================================================================================================
        // 2. 글리프 (HUD/GUI)
        saveResourceIfNotExists("hud_heart.png");
        CoreProvider.registerGlyph(this, "heart", new File(getDataFolder(), "hud_heart.png"), 8, 8);

        saveResourceIfNotExists("menu_bg.png");
        CoreProvider.registerGlyph(this, "menu_bg", new File(getDataFolder(), "menu_bg.png"), 0, 8);

        // 3. 사운드
        saveResourceIfNotExists("effect.ogg");
        CorePlugin.getResourcePackManager().registerSoundFile(this, "effect.ogg", new File(getDataFolder(), "effect.ogg"));

        saveResourceIfNotExists("magic.ogg");
        CorePlugin.getResourcePackManager().registerSoundFile(this, "magic.ogg", new File(getDataFolder(), "magic.ogg"));

        saveResource("sounds.json", true);

        saveResourceIfNotExists("sounds.json");
        CorePlugin.getResourcePackManager().registerSoundConfig(this, new File(getDataFolder(), "sounds.json"));

        // 4. 아이템 프리셋 등록
        // 전설 빠따
        ItemStack legendaryBat = ItemBuilder.from("test_addon:bat")
                .name("§6§l전설의 배트맨 표식")
                .lore("§7브라우저에 등록된", "§7커스텀 데이터 아이템입니다.")
                .setData("power", 9999)
                .glow()
                .build();
        // ID: test_addon:legendary_bat 로 등록
        CoreProvider.registerItemPreset("test_addon:legendary_bat", legendaryBat);

        //

        // 5. 커스텀 블록 등록
        saveResourceIfNotExists("ruby_ore.png");
        File oreFile = new File(getDataFolder(), "ruby_ore.png");
        var blockData = CoreProvider.registerBlock(this, "ruby_ore", oreFile, 3.0f, 2);

        if (blockData != null) {
            blockData.setSounds(org.bukkit.Sound.BLOCK_GLASS_PLACE, org.bukkit.Sound.BLOCK_GLASS_BREAK);

            // 드랍 테이블 설정
            // 1. 루비 아이템 가져오기
            org.bukkit.inventory.ItemStack rubyItem = CorePlugin.getItemManager().getItem("test_addon:ruby");
            org.bukkit.inventory.ItemStack batItem = CorePlugin.getItemManager().getItem("test_addon:bat");

            if (rubyItem != null) {
                // A. 100% 확률로 루비 1~2개 드랍 (행운 적용됨)
                blockData.addDrop(rubyItem, 1.0, 2, 5, true);

                // B. 5% 확률로 "전설의 박쥐" 드랍 (행운 미적용) - 희귀 보상
                ItemStack rareItem = CorePlugin.getItemManager().getItem("test_addon:bat");
                blockData.addDrop(rareItem, 1.0, 1, 1, false);
            }

            // 경험치 5 드랍
            blockData.setExp(5);
        }

        // 몬스터 등록 (고블린)

        var goblin = CoreProvider.registerMob(this, "goblin", EntityType.ZOMBIE, "§c사악한 고블린");

        // 스탯: 체력 50, 공격력 5, 속도 0.25 (좀비보다 빠름)
        goblin.setStats(50.0, 5.0, 0.25);

        // 드랍: 루비 (50% 확률)
        ItemStack ruby = CorePlugin.getItemManager().getItem("test_addon:ruby");
        if (ruby != null) {
            goblin.addDrop(ruby, 0.5, 1, 2);
            goblin.setExp(10);
        }

        // 장비: 가죽 투구, 철검 (ItemBuilder로 만들어서 넣어도 됨)
        ItemStack sword = new ItemStack(Material.IRON_SWORD);
        ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
        goblin.setEquipment(sword, helmet, null, null, null);
    }
//=======================================================================================================================
    private void saveResourceIfNotExists(String name) {
        if (!new File(getDataFolder(), name).exists()) {
            try { saveResource(name, false); } catch (Exception ignored) {}
        }
    }

    // 아이템 교체 시 스탯 재계산
    // (참고: 갑옷 장착 이벤트는 Bukkit에 기본으로 없어서 별도 라이브러리나 복잡한 로직이 필요하지만,
    // 테스트를 위해 '/test refresh' 명령어로 수동 갱신하거나,
    // InventoryClickEvent 등을 잡아서 처리해야 함. 여기선 명령어 테스트로 대체)
    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        // 1틱 뒤에 실행 (이벤트 시점에는 아직 아이템이 안 바뀐 상태일 수 있음)
        getServer().getScheduler().runTask(this, () -> {
            CoreProvider.recalculateStats(event.getPlayer());
        });
    }

    // 접속 시 db 데이터 로드 -> statmanager로 전송
    // 테스트용 주석처리
    /*
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String uuid = player.getUniqueId().toString();

        // 1. 비동기로 DB 로드
        CoreProvider.loadDBAsync(uuid, "rpg_stats", PlayerDataDTO.class).thenAccept(data -> {
            if (data != null) {
                // 2. 메인 스레드에서 StatManager에 적용 (비동기에서 Bukkit API 호출 방지)
                getServer().getScheduler().runTask(this, () -> {
                    // DB에서 불러온 값을 Base Stat으로 설정
                    CoreProvider.setBaseStat(player, "str", data.str); // 10
                    CoreProvider.setBaseStat(player, "dex", data.dex); // 5

                    // (골드 등 다른 데이터는 메모리에 캐싱하거나 변수에 저장)

                    player.sendMessage("§e[System] DB에서 스탯을 불러왔습니다. (STR: " + data.str + ")");
                });
            } else {
                // 데이터가 없으면 초기값 설정 (선택 사항)
                getServer().getScheduler().runTask(this, () -> {
                    CoreProvider.setBaseStat(player, "str", 0);
                    CoreProvider.setBaseStat(player, "dex", 0);
                });
            }
        });
    }

     */
    // 마법 지팡이 우클릭 이벤트
    @EventHandler
    public void onWandUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null) return;

        if (CorePlugin.getItemManager().isCustomItem(item, "test_addon:wand")) {
            Player player = event.getPlayer();
            String skillId = "magic_missile";

            if (CoreProvider.hasCooldown(player, skillId)) {
                double left = CoreProvider.getCooldown(player, skillId);
                player.sendMessage("§c[쿨타임] §f마법을 쓰려면 §e" + String.format("%.1f", left) + "초§f 기다려야 합니다.");
                return; // 쿨타임 중
            }

            // [수정] 투사체 발사!
            // 날아갈 모습: 루비 아이템 (test_addon:light)
            ItemStack projectileVisual = CorePlugin.getItemManager().getItem("test_addon:light");
            if (projectileVisual == null) projectileVisual = new ItemStack(Material.REDSTONE_BLOCK); // 없으면 대체품

            // 속도: 1.5, 사거리: 30칸
            CoreProvider.shootProjectile(player, projectileVisual, 1.5, 30.0, 1.0f, (target) -> {
                // [명중 시 실행될 코드]
                if (target instanceof org.bukkit.entity.LivingEntity livingTarget) {
                    // 1. 대미지 계산 (예: 지능 스탯 비례)
                    // double damage = CoreProvider.getStat(player, "int") * 2.0;
                    double damage = 15.0; // 테스트용 고정값

                    // 2. 크리티컬 계산 (30% 확률)
                    boolean isCrit = Math.random() < 0.3;
                    if (isCrit) damage *= 1.5;

                    // 3. 타격 처리 (숫자 뜸!)
                    CoreProvider.dealDamage(player, livingTarget, damage, isCrit);

                    // (기존의 player.playSound는 DamageManager가 처리하므로 제거해도 됨)
                    // player.sendMessage("§c명중!");
                }
            });

            // 발사음 (커스텀 사운드)
            player.playSound(player.getLocation(), "test_addon.magic_cast", 1.0f, 1.2f);

            CoreProvider.setCooldown(player, skillId, 20L); // 1초 쿨타임
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return false;

        if (label.equalsIgnoreCase("test")) {
            if (args.length == 0) {
                player.sendMessage("§6=== [ TestAddon Commands ] ===");
                player.sendMessage("§e/test item  §7- 아이템 생성 및 데이터 주입");
                player.sendMessage("§e/test sound §7- 커스텀 사운드 재생");
                player.sendMessage("§e/test parse <msg> §7- 태그 파싱 테스트");
                player.sendMessage("§e/test boss  §7- 보스바 테스트");
                player.sendMessage("§e/test title §7- 타이틀 테스트");
                player.sendMessage("§e/test side  §7- 스코어보드 테스트");
                player.sendMessage("§e/test gui   §7- GUI 및 콜백 테스트");
                return true;
            }

            String sub = args[0].toLowerCase();

            // 1. 아이템 & 데이터 API 테스트
            if (sub.equals("item")) {
                ItemStack item = ItemBuilder.from("test_addon:bat")
                        .name("§6데이터가 담긴 박쥐")
                        .setData("power", 999) // 데이터 주입
                        .setData("owner", player.getName())
                        .glow()
                        .build();
                player.getInventory().addItem(item);
                player.sendMessage("§a아이템 지급 완료! '/core inspect item'으로 확인하세요.");
                return true;
            }

            // 2. 사운드 테스트
            if (sub.equals("sound")) {
                player.playSound(player.getLocation(), "test_addon.effect", 1.0f, 1.0f);
                player.sendMessage("§a🎵 소리가 들리나요?");
                return true;
            }

            // 3. 태그 파서 테스트
            if (sub.equals("parse")) {
                String raw = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                if (raw.isEmpty()) raw = "테스트 :test_addon:heart: 하트";
                String parsed = CoreProvider.parseTags(raw);
                player.sendMessage("§e[원본] " + raw);
                player.sendMessage("§a[결과] " + parsed);
                return true;
            }

            // 4. 보스바 테스트
            if (sub.equals("boss")) {
                String heart = CoreProvider.getGlyphTag("test_addon:heart");
                Component title = Component.text("§f" + heart + " §c§l레이드 보스");
                CoreProvider.showBossBar(player, "boss_1", title, 0.7f, BossBar.Color.RED, BossBar.Overlay.NOTCHED_10);
                player.sendMessage("§a보스바 출력됨.");
                return true;
            }

            // 5. 타이틀 테스트
            if (sub.equals("title")) {
                String bg = CoreProvider.getGlyphTag("test_addon:menu_bg");
                Component main = Component.text("§f" + bg).append(CoreProvider.getSpace(-100)).append(Component.text("§eVICTORY"));
                CoreProvider.sendTitle(player, main, Component.text("§7성공했습니다"), 10, 40, 10, 10);
                return true;
            }

            // 6. 스코어보드 테스트
            if (sub.equals("side")) {
                List<Component> lines = Arrays.asList(
                        Component.text("§7---------------"),
                        Component.text("§f내 정보:"),
                        Component.text("§a" + player.getName()),
                        Component.text("§7---------------")
                );
                CoreProvider.setSidebar(player, "test_side", Component.text("§e[ STATS ]"), lines, 10);
                player.sendMessage("§a사이드바 출력됨.");
                return true;
            }

            // 7. GUI & 콜백 & 도난방지 테스트
            if (sub.equals("gui")) {
                Inventory gui = CoreProvider.openGui(player, "test_addon:menu_bg", "테스트 메뉴", -16, 3, true);

                ItemStack btn = ItemBuilder.from("test_addon:bat").name("§e클릭하세요").build();

                CoreProvider.setGuiButton(gui, 13, btn, (e) -> {
                    player.sendMessage("§a[Callback] 버튼 클릭됨!");
                    player.closeInventory();
                });
                return true;
            }

            // 8. 블록 설치 테스트 (/test block)
            if (args[0].equalsIgnoreCase("block")) {
                // 등록된 블록 데이터 가져오기
                BlockManager.CustomBlockData blockData = CorePlugin.getBlockManager().getBlock("test_addon:ruby_ore");

                if (blockData != null) {
                    // 플레이어 발 밑 블록을 변경
                    Block target = player.getLocation().getBlock();
                    target.setBlockData(blockData.blockData); // 노트 블록 상태 적용

                    player.sendMessage("§a발 밑에 커스텀 블록(루비 광석)을 설치했습니다!");
                    player.sendMessage("§7State: " + blockData.blockData.getAsString());
                } else {
                    player.sendMessage("§c블록을 찾을 수 없습니다.");
                }
                return true;
            }

            // 9. 블록 아이템 지급 (/getore)
            if (label.equalsIgnoreCase("getore")) {
                // ItemManager에 자동으로 등록된 "test_addon:ruby_ore" 아이템을 가져옴
                org.bukkit.inventory.ItemStack item = CorePlugin.getItemManager().getItem("test_addon:ruby_ore");

                if (item != null) {
                    player.getInventory().addItem(item);
                    player.sendMessage("§a루비 광석 아이템 지급 완료!");
                } else {
                    player.sendMessage("§c아이템을 찾을 수 없습니다.");
                }
                return true;
            }

            // 유틸리티 테스트
            if (args[0].equalsIgnoreCase("util")) {
                // 1. 아이템 지급
                ItemStack bat = CoreProvider.getItem("test_addon:bat");
                bat.setAmount(5);
                player.getInventory().addItem(bat);
                player.sendMessage("§e박쥐 5개를 지급했습니다.");

                // 2. 인벤토리 체크 테스트 (1초 뒤 실행)
                getServer().getScheduler().runTaskLater(this, () -> {
                    if (CoreProvider.hasItem(player, "test_addon:bat", 3)) {
                        player.sendMessage("§a[Check] 박쥐가 3개 이상 있습니다.");

                        // 3. 제거 테스트
                        if (CoreProvider.removeItem(player, "test_addon:bat", 3)) {
                            player.sendMessage("§c[Remove] 박쥐 3개를 제거했습니다.");
                        }
                    } else {
                        player.sendMessage("§c[Check] 박쥐가 부족합니다.");
                    }
                }, 40L);

                return true;
            }

            // 스탯 아이템 지급 (test statitem)
            if (args[0].equalsIgnoreCase("statitem")) {
                ItemStack sword = ItemBuilder.from("test_addon:bat")
                        .name("§c광전사의 검")
                        .lore("§7STR +50", "§7SPEED +20", "§7VIT +10")
                        .setData("stat_str", 50)   // 공격력
                        .setData("stat_speed", 20) // 이동속도
                        .setData("stat_vit", 10)   // 체력
                        .build();

                player.getInventory().addItem(sword);
                player.sendMessage("§a스탯 아이템 지급 완료!");
                return true;
            }

            // 스탯 확인 및 갱신 (test stat)
            if (args[0].equalsIgnoreCase("stat")) {
                CoreProvider.recalculateStats(player); // 강제 갱신

                double str = CoreProvider.getStat(player, "str");
                double vit = CoreProvider.getStat(player, "vit");
                double speed = CoreProvider.getStat(player, "speed");

                player.sendMessage("§6=== [ My Stats ] ===");
                player.sendMessage("§cSTR (공격력): " + str);
                player.sendMessage("§aVIT (추가체력): " + vit);
                player.sendMessage("§bSPEED (이속): " + speed);
                return true;
            }

            // 던전 입장 테스트 (/test dungeon)
            if (args[0].equalsIgnoreCase("dungeon")) {
                if (CoreProvider.isInSession(player)) {
                    CoreProvider.endSession(player);
                } else {
                    CoreProvider.startSession(player);
                    // 테스트를 위해 경험치 구슬 소환
                    player.getWorld().spawn(player.getLocation(), org.bukkit.entity.ExperienceOrb.class).setExperience(100);
                }
                return true;
            }

            // 몬스터 소환 테스트 (/test mob)
            if (args[0].equalsIgnoreCase("mob")) {
                CoreProvider.spawnMob(player.getLocation(), "test_addon:goblin");
                player.sendMessage("§c고블린이 나타났습니다!");
                return true;
            }

            // 몹 빌더 / 동적 드랍 테이블 테스트 (test bossmob)
            if (args[0].equalsIgnoreCase("bossmob")) {

                // 보스 전용 드랍 아이템 (전설의 박쥐)
                ItemStack legendaryDrop = ItemBuilder.from("test_addon:bat")
                        .name("§6보스의 전리품")
                        .glow()
                        .build();

                // 빌더를 사용해 '기본 고블린'을 '보스'로 개조
                MobBuilder.from("test_addon:goblin")
                        .name("§4§l[BOSS] 킹 고블린") // 이름 변경
                        .health(100) // 체력 2배
                        .damage(10)  // 공격력 2배
                        .clearDrops() // 기존 드랍(루비) 삭제
                        .addDrop(legendaryDrop, 1.0, 1, 1) // 새로운 드랍(전설박쥐) 100% 추가
                        .spawn(player.getLocation());

                player.sendMessage("§c보스 몬스터가 소환되었습니다!");
                return true;
            }
            // 마법 지팡이 지급 명령어 (test wand)
            if (label.equalsIgnoreCase("test") && args.length > 0) {
                // [신규] 지팡이 지급 명령어 (/test wand)
                if (args[0].equalsIgnoreCase("wand")) {
                    ItemStack wand = ItemBuilder.from("test_addon:wand")
                            .name("§b초보자의 지팡이")
                            .lore("§7우클릭 시 마법 발사", "§7쿨타임: 3초")
                            .glow()
                            .build();
                    player.getInventory().addItem(wand);
                    player.sendMessage("§a지팡이 지급 완료!");
                    return true;
                }
                // ...
            }

            // DB 테스트 (test db <save/load>
            if (label.equalsIgnoreCase("test") && args.length > 0) {
                if (args[0].equalsIgnoreCase("db")) {
                    String uuid = player.getUniqueId().toString();
                    String key = "rpg_stats"; // 데이터 키

                    if (args.length > 1 && args[1].equalsIgnoreCase("save")) {
                        // 1. 데이터 객체 생성
                        PlayerDataDTO data = new PlayerDataDTO(5000, 10, 5, java.time.LocalDateTime.now().toString());

                        // 2. 저장 요청
                        CoreProvider.saveDB(uuid, key, data);
                        player.sendMessage("§a[DB] 데이터 저장 요청됨!");
                        return true;
                    }

                    if (args.length > 1 && args[1].equalsIgnoreCase("load")) {
                        // 3. 데이터 로드
                        CoreProvider.loadDBAsync(uuid, "rpg_stats", PlayerDataDTO.class).thenAccept(data -> {
                            if (data != null) {
                                // [수정] 메인 스레드에서 StatManager에 값 적용!
                                getServer().getScheduler().runTask(this, () -> {
                                    CoreProvider.setBaseStat(player, "str", data.str);
                                    CoreProvider.setBaseStat(player, "dex", data.dex);
                                    // CoreProvider.setBaseStat(player, "gold", data.gold); // 골드는 스탯 아님

                                    player.sendMessage("§e[DB] 로드 및 스탯 적용 완료!");
                                    player.sendMessage("§fSTR: " + data.str + ", DEX: " + data.dex);
                                });
                            } else {
                                player.sendMessage("§c[DB] 저장된 데이터가 없습니다.");
                            }
                        });
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @EventHandler
    public void onCustomBlockClick(CustomBlockInteractEvent event) {
        // 1. 클릭한 블록이 내가 등록한 "ruby_ore"인지 확인
        // (BlockManager에 등록할 때 썼던 ID와 일치하는지 확인)
        // uniqueName: test_addon_ruby_ore
        if (event.getBlockData().uniqueName.equals("test_addon_ruby_ore")) {

            Player player = event.getPlayer();

            // 2. 웅크리고 클릭하면? -> 그냥 둠 (Core가 설치 로직 등을 처리하도록 무시)
            if (player.isSneaking()) return;

            // 3. 그냥 우클릭하면? -> GUI 오픈 (상호작용)
            player.sendMessage("§a[Interaction] 루비 광석을 터치했습니다!");

            // GUI 열기
            CorePlugin.getGuiManager().openGui(player, "test_addon:menu_bg", "광석 정보", -16, 3, true);

            // 4. [중요] 이벤트 캔슬 -> Core에게 "내가 처리했으니 설치 로직 돌리지 마"라고 알림
            event.setCancelled(true);
        }
    }

    @Override
    public String getNamespace() { return "test_addon"; }
    @Override
    public JavaPlugin getPlugin() { return this; }
}