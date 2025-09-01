package com.example.tailtag;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class TailTagPlugin extends JavaPlugin implements Listener {
    public static final String name = "TailTag";
    public static final String version = "1.0";

    private final Map<UUID, TeamColor> playerColors = new HashMap<>();
    private final Map<UUID, UUID> slaves = new HashMap<>(); // 노예 UUID -> 주인 UUID
    private final Map<UUID, Set<UUID>> masters = new HashMap<>(); // 주인 UUID -> 노예들 Set
    private final Map<UUID, Long> deadPlayers = new HashMap<>(); // 자연사한 플레이어와 사망 시간
    private final Map<UUID, Integer> frozenPlayers = new HashMap<>(); // 움직일 수 없는 플레이어
    private boolean gameActive = false;
    private Location gameCenter;
    private final int GAME_AREA_SIZE = 20; // 20청크
    private BukkitTask gameTask;
    private BukkitTask heartbeatTask;

    public enum TeamColor {
        RED(NamedTextColor.RED, "빨강"),
        ORANGE(NamedTextColor.GOLD, "주황"),
        YELLOW(NamedTextColor.YELLOW, "노랑"),
        GREEN(NamedTextColor.GREEN, "초록"),
        BLUE(NamedTextColor.BLUE, "파랑"),
        INDIGO(NamedTextColor.DARK_BLUE, "남색"),
        PURPLE(NamedTextColor.DARK_PURPLE, "보라"),
        PINK(NamedTextColor.LIGHT_PURPLE, "핑크"),
        GRAY(NamedTextColor.GRAY, "회색"),
        BLACK(NamedTextColor.BLACK, "검정");

        private final NamedTextColor chatColor;
        private final String displayName;

        TeamColor(NamedTextColor chatColor, String displayName) {
            this.chatColor = chatColor;
            this.displayName = displayName;
        }

        public NamedTextColor getNamedTextColor() {
            return chatColor;
        }

        public String getDisplayName() {
            return displayName;
        }

        public TeamColor next() {
            TeamColor[] vals = values();
            return vals[(this.ordinal() + 1) % vals.length];
        }
    }

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("꼬리잡기 플러그인이 활성화되었습니다!");

        // 게임 상태 체크 태스크
        startGameTasks();
    }

    @Override
    public void onDisable() {
        if (gameTask != null) {
            gameTask.cancel();
        }
        if (heartbeatTask != null) {
            heartbeatTask.cancel();
        }
        getLogger().info("꼬리잡기 플러그인이 비활성화되었습니다!");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, Command command, @NotNull String label, String @NotNull [] args) {
        if (!command.getName().equalsIgnoreCase("tailtag")) {
            return false;
        }

        if (!(sender instanceof Player player)) {
            SendMessage.sendMessagePlayer(
                    (Player) sender,
                    Component.text("플레이어만 사용할 수 있는 명령어입니다.", NamedTextColor.RED)
            );
            return true;
        }

        if (args.length == 0) {
            SendMessage.sendMessagePlayer(
                    player,
                    Component.text("사용법: /tailtag <start | reset>", NamedTextColor.RED)
            );
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start":
                startGame(player);
                break;
            case "reset":
                resetGame(player);
                break;
            default:
                SendMessage.sendMessagePlayer(
                        player,
                        Component.text("사용법: /tailtag <start | reset>", NamedTextColor.RED)
                );
                break;
        }

        return true;
    }

    private void startGame(Player commander) {
        if (gameActive) {
            SendMessage.sendMessagePlayer(
                    commander,
                    Component.text("게임이 이미 진행 중입니다.", NamedTextColor.RED)
            );
            return;
        }

        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
        if (onlinePlayers.size() < 2) {
            SendMessage.sendMessagePlayer(
                    commander,
                    Component.text("최소 2명의 플레이어가 필요합니다.", NamedTextColor.RED)
            );
            return;
        }

        if (onlinePlayers.size() > 10) {
            SendMessage.sendMessagePlayer(
                    commander,
                    Component.text("최대 10명까지만 게임에 참여할 수 있습니다.", NamedTextColor.RED)
            );
            return;
        }

        gameActive = true;
        gameCenter = commander.getLocation();

        // 플레이어 색깔 배정
        assignColors(new ArrayList<>(onlinePlayers));

        // 플레이어 스폰
        spawnPlayers(new ArrayList<>(onlinePlayers));

        // 게임 시작 안내
        for (Player player : onlinePlayers) {
            TeamColor color = playerColors.get(player.getUniqueId());
            SendMessage.sendMessagePlayer(
                    player,
                    Component.text("게임이 시작되었습니다!", NamedTextColor.GREEN)
            );
            SendMessage.sendMessagePlayer(
                    player,
                    Component.text("당신의 색깔은 ")
                            .append(Component.text(color.getDisplayName(), color.getNamedTextColor())
                                    .decoration(TextDecoration.BOLD, true))
                            .append(Component.text("입니다."))
            );

            TeamColor targetColor = getTargetColor(color, onlinePlayers.size());
            if (targetColor != null) {
                SendMessage.sendMessagePlayer(
                        player,
                        Component.text("잡아야 할 색깔: ", NamedTextColor.YELLOW)
                                .append(Component.text(targetColor.getDisplayName(), targetColor.getNamedTextColor())
                                        .decoration(TextDecoration.BOLD, true))
                );
            }

            // 인벤토리 저장
            saveInventory(player);
        }

        SendMessage.sendMessagePlayer(
                commander,
                Component.text("게임이 시작되었습니다!", NamedTextColor.GREEN)
        );
    }

    private void resetGame(Player commander) {
        gameActive = false;

        // 모든 플레이어 상태 초기화
        for (Player player : Bukkit.getOnlinePlayers()) {
            restoreInventory(player);
            player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
            player.clearActivePotionEffects();
            player.setFireTicks(0);
            player.setFoodLevel(20);
            player.setSaturation(20);
        }

        // 데이터 초기화
        playerColors.clear();
        slaves.clear();
        masters.clear();
        deadPlayers.clear();
        frozenPlayers.clear();

        if (gameTask != null) {
            gameTask.cancel();
        }
        if (heartbeatTask != null) {
            heartbeatTask.cancel();
        }

        startGameTasks();

        SendMessage.sendMessagePlayer(
                commander,
                Component.text("게임이 리셋되었습니다.", NamedTextColor.GREEN)
        );
    }

    private void assignColors(List<Player> players) {
        Collections.shuffle(players);
        TeamColor[] colors = TeamColor.values();

        for (int i = 0; i < players.size(); i++) {
            TeamColor color = colors[i % players.size()];
            playerColors.put(players.get(i).getUniqueId(), color);
            masters.put(players.get(i).getUniqueId(), new HashSet<>());
        }
    }

    private void spawnPlayers(List<Player> players) {
        Random random = new Random();
        World world = gameCenter.getWorld();

        for (Player player : players) {
            // 20청크 범위 내 랜덤 위치 생성
            int offsetX = (random.nextInt(GAME_AREA_SIZE * 2) - GAME_AREA_SIZE) * 16;
            int offsetZ = (random.nextInt(GAME_AREA_SIZE * 2) - GAME_AREA_SIZE) * 16;

            // 안전한 스폰 위치 찾기
            Location spawnLocation = findSafeSpawnLocation(world,
                    gameCenter.getBlockX() + offsetX,
                    gameCenter.getBlockZ() + offsetZ);

            player.teleport(spawnLocation);
        }
    }

    private Location findSafeSpawnLocation(World world, int x, int z) {
        // 최고 높이부터 시작해서 안전한 위치를 찾음
        int highestY = world.getHighestBlockYAt(x, z);

        // 하늘에서 스폰되는 것을 방지하기 위해 최대 높이 제한
        if (highestY > 100) {
            highestY = 100;
        }

        // 위에서부터 아래로 내려가면서 안전한 위치 찾기
        for (int y = highestY; y > 0; y--) {
            Location checkLoc = new Location(world, x, y, z);

            // 현재 블럭이 고체이고, 위 2블럭이 공기인지 확인
            if (checkLoc.getBlock().getType().isSolid() &&
                    !checkLoc.getBlock().getType().equals(Material.LAVA) &&
                    !checkLoc.getBlock().getType().equals(Material.WATER) &&
                    checkLoc.clone().add(0, 1, 0).getBlock().getType().equals(Material.AIR) &&
                    checkLoc.clone().add(0, 2, 0).getBlock().getType().equals(Material.AIR)) {

                return checkLoc.clone().add(0.5, 1, 0.5); // 블럭 중앙, 1블럭 위
            }
        }

        // 조건에 맞는 위치가 없을 경우, 기본 위치 사용 (예: Y=64)
        return new Location(world, x + 0.5, 65, z + 0.5);
    }

    private TeamColor getTargetColor(TeamColor currentColor, int totalPlayers) {
        TeamColor[] colors = Arrays.copyOf(TeamColor.values(), totalPlayers);

        for (int i = 0; i < colors.length; i++) {
            if (colors[i] == currentColor) {
                return colors[(i + 1) % colors.length];
            }
        }
        return null;
    }

    private void saveInventory(Player player) {
        // 인벤토리 완전 초기화
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));

        // 상태 초기화
        player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
        player.setFoodLevel(20);
        player.setSaturation(20);
        player.setFireTicks(0);
        player.clearActivePotionEffects();
        player.setExp(0);
        player.setLevel(0);
    }

    private void restoreInventory(Player player) {
        // 게임 종료 후 상태 초기화
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));

        // 체력과 상태 복원
        player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20.0);
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20);
        player.setFireTicks(0);
        player.clearActivePotionEffects();
        player.setExp(0);
        player.setLevel(0);
    }

    private void startGameTasks() {
        // 게임 상태 체크 태스크 (1초마다)
        gameTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!gameActive) return;

                checkGameEnd();
                checkSlaveDistance();
                checkDeadPlayers();
                checkFrozenPlayers();
                updateDragonEggEffects();
                updateSlaveEffects();
            }
        }.runTaskTimer(this, 20L, 20L);

        // 하트비트 표시 태스크 (1초마다)
        heartbeatTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!gameActive) return;
                showHeartbeat();
            }
        }.runTaskTimer(this, 20L, 20L);
    }

    private void checkGameEnd() {
        if (!gameActive) return;

        Set<UUID> activeMasters = new HashSet<>();
        for (Map.Entry<UUID, Set<UUID>> entry : masters.entrySet()) {
            UUID masterUUID = entry.getKey();
            Player master = Bukkit.getPlayer(masterUUID);

            if (master != null && master.isOnline() && !slaves.containsKey(masterUUID)) {
                activeMasters.add(masterUUID);
            }
        }

        if (activeMasters.size() == 1) {
            UUID winnerUUID = activeMasters.iterator().next();
            Player winner = Bukkit.getPlayer(winnerUUID);

            if (winner != null) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.showTitle(Title.title(
                            Component.text(winner.getName(), NamedTextColor.GOLD)
                                    .append(Component.text("님이 승리하셨습니다!")),
                            Component.text(""), 10, 70, 20));
                }

                // 게임 종료 후 3초 뒤 리셋
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        resetGame(winner);
                    }
                }.runTaskLater(this, 60L);
            }
        }
    }

    private void checkSlaveDistance() {
        for (Map.Entry<UUID, UUID> entry : slaves.entrySet()) {
            UUID slaveUUID = entry.getKey();
            UUID masterUUID = entry.getValue();

            Player slave = Bukkit.getPlayer(slaveUUID);
            Player master = Bukkit.getPlayer(masterUUID);

            if (slave != null && master != null && slave.isOnline() && master.isOnline()) {
                double distance = slave.getLocation().distance(master.getLocation());

                if (distance > 30) {
                    // 노예에게 데미지
                    slave.damage(1.0); // 0.5 하트 데미지

                    if (slave.getHealth() <= 0) {
                        slave.teleport(master.getLocation());
                        slave.setHealth(slave.getAttribute(Attribute.MAX_HEALTH).getValue());
                    }
                }
            }
        }
    }

    private void checkDeadPlayers() {
        Iterator<Map.Entry<UUID, Long>> iterator = deadPlayers.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            UUID playerUUID = entry.getKey();
            long deathTime = entry.getValue();

            if (System.currentTimeMillis() - deathTime >= 120000) { // 2분
                Player player = Bukkit.getPlayer(playerUUID);
                if (player != null && player.isOnline()) {
                    // 모든 포션 효과 제거
                    for (PotionEffect effect : player.getActivePotionEffects()) {
                        player.removePotionEffect(effect.getType());
                    }

                    // HP를 최대치로 설정
                    player.setHealth(20);
                    player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());

                    // 사용자 정의 효과만 적용 (화염 저항 1분)
                    player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 1200, 0));

                    frozenPlayers.remove(playerUUID);
                }
                iterator.remove();
            }
        }
    }

    private void cancelDeathEvent(EntityDamageEvent event, Player player) {
        double finalHealth = player.getHealth() - event.getFinalDamage();
        if (finalHealth > 0) return;
        event.setCancelled(true);
        useTotemOfUndying(player, false);
    }

    private void useTotemOfUndying(Player player, boolean removeFrozen) {
        UUID playerUUID = player.getUniqueId();

        // 불사의 토템 효과 적용
        player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue()); // 풀피로 회복

        // 기존 포션 효과 모두 제거 (불사의 토템 버프 제거)
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        if (removeFrozen) {
            // 노예가 주인에게 죽은 경우 - 움직임 제한 해제
            frozenPlayers.remove(playerUUID);
            SendMessage.sendMessagePlayer(
                    player,
                    Component.text("주인말을 잘 들으십쇼.", NamedTextColor.GREEN)
            );
        } else {
            // 자연사의 경우 - 2분간 움직임 제한
            deadPlayers.put(playerUUID, System.currentTimeMillis());
            frozenPlayers.put(playerUUID, 120); // 120초
            SendMessage.sendMessagePlayer(
                    player,
                    Component.text("2분간 움직일 수 없습니다.", NamedTextColor.RED)
            );
        }

        // 불사의 토템 효과 시각적 표시
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation(), 30);
        player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
    }

    private void handleNaturalDeath(Player victim) {
        UUID victimUUID = victim.getUniqueId();
        deadPlayers.put(victimUUID, System.currentTimeMillis());
        frozenPlayers.put(victimUUID, 120); // 120초

        SendMessage.sendMessagePlayer(
                victim,
                Component.text("자연사로 인해 2분간 움직일 수 없습니다.", NamedTextColor.RED)
        );
    }

    private void checkFrozenPlayers() {
        Iterator<Map.Entry<UUID, Integer>> iterator = frozenPlayers.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            UUID playerUUID = entry.getKey();
            int timeLeft = entry.getValue() - 1;

            if (timeLeft <= 0) {
                iterator.remove();
            } else {
                frozenPlayers.put(playerUUID, timeLeft);

                // 플레이어 이동 제한
                Player player = Bukkit.getPlayer(playerUUID);
                if (player != null && player.isOnline()) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 25, 255, false, false));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 25, -10, false, false));
                }
            }
        }
    }

    private void updateDragonEggEffects() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getInventory().contains(Material.DRAGON_EGG)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 25, 1, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.INSTANT_DAMAGE, 25, 0, false, false));
            }
        }
    }

    private void updateSlaveEffects() {
        for (UUID slaveUUID : slaves.keySet()) {
            Player slave = Bukkit.getPlayer(slaveUUID);
            if (slave != null && slave.isOnline()) {
                // 노예에게 나약함 2 효과 지속 부여
                slave.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 25, 1, false, false));
            }
        }
    }

    private void showHeartbeat() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!gameActive) continue;

            UUID playerUUID = player.getUniqueId();

            if (slaves.containsKey(playerUUID)) {
                // 노예인 경우 주인과의 거리 표시
                UUID masterUUID = slaves.get(playerUUID);
                Player master = Bukkit.getPlayer(masterUUID);

                if (master != null && master.isOnline()) {
                    double distance = player.getLocation().distance(master.getLocation());

                    player.sendActionBar(
                            Component.text("주인과의 거리: ", NamedTextColor.YELLOW)
                                    .append(Component.text((int) distance))
                                    .append(Component.text("블럭"))
                    );
                }
            } else {
                // 주인인 경우 추적자 감지
                TeamColor playerColor = playerColors.get(playerUUID);
                if (playerColor != null) {
                    TeamColor hunterColor = getHunterColor(playerColor, Bukkit.getOnlinePlayers().size());

                    if (hunterColor != null) {
                        for (Player other : Bukkit.getOnlinePlayers()) {
                            TeamColor otherColor = playerColors.get(other.getUniqueId());

                            if (otherColor == hunterColor && !slaves.containsKey(other.getUniqueId())) {
                                double distance = player.getLocation().distance(other.getLocation());

                                if (distance <= 30) {
                                    player.sendActionBar(Component.text("❤", NamedTextColor.RED));
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private TeamColor getHunterColor(TeamColor currentColor, int totalPlayers) {
        TeamColor[] colors = Arrays.copyOf(TeamColor.values(), totalPlayers);

        for (int i = 0; i < colors.length; i++) {
            if (colors[i] == currentColor) {
                return colors[(i - 1 + colors.length) % colors.length];
            }
        }
        return null;
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!gameActive) return;

        if (event.getEntity() instanceof Player victim) {
            UUID playerUUID = victim.getUniqueId();

            if (event instanceof EntityDamageByEntityEvent entityEvent) {
                // 엔티티 공격
                if (entityEvent.getDamager() instanceof Player attacker) {
                    UUID attackerUUID = attacker.getUniqueId();
                    UUID victimUUID = victim.getUniqueId();
                    double finalHealth = victim.getHealth() - event.getFinalDamage();

                    // 탈락 로직
                    if (finalHealth <= 0) {
                        event.setCancelled(true); // 죽음 방지

                        TeamColor victimColor = playerColors.get(victimUUID);
                        TeamColor attackerColor = playerColors.get(attackerUUID);

                        // 실제 주인 찾기 (killer가 노예인 경우 주인을 찾음)
                        UUID actualMasterUUID = attackerUUID;
                        if (slaves.containsKey(attackerUUID)) {
                            actualMasterUUID = slaves.get(attackerUUID);
                        }
                        Player actualMaster = Bukkit.getPlayer(actualMasterUUID);

                        // 올바른 색깔 순서로 잡았는지 확인
                        TeamColor targetColor = getTargetColor(attackerColor, Bukkit.getOnlinePlayers().size());

                        if (targetColor == victimColor) {
                            // 노예로 만들기 (새로운 노예인 경우에만)
                            if (!slaves.containsKey(victimUUID)) {
                                slaves.put(victimUUID, actualMasterUUID); // 실제 주인의 노예로 만듦

                                // masters Map 초기화 확인
                                if (!masters.containsKey(actualMasterUUID)) {
                                    masters.put(actualMasterUUID, new HashSet<>());
                                }
                                masters.get(actualMasterUUID).add(victimUUID);

                                // 노예의 색깔을 주인의 색깔로 변경
                                TeamColor masterColor = playerColors.get(actualMasterUUID);

                                // 노예 체력 제한 (4칸 = 8.0)
                                AttributeInstance victimMaxHealthAttr = victim.getAttribute(Attribute.MAX_HEALTH);
                                if (victimMaxHealthAttr != null) {
                                    victimMaxHealthAttr.setBaseValue(8.0);
                                    victim.setHealth(8.0);
                                }

                                // 실제 주인의 체력 감소 (새로운 노예를 만들 때만)
                                if (actualMaster != null && actualMaster.getAttribute(Attribute.MAX_HEALTH) != null) {
                                    double currentMaxHealth = actualMaster.getAttribute(Attribute.MAX_HEALTH).getBaseValue();
                                    actualMaster.getAttribute(Attribute.MAX_HEALTH).setBaseValue(Math.max(8.0, currentMaxHealth - 2.0));
                                    actualMaster.setHealth(Math.min(actualMaster.getHealth(), actualMaster.getAttribute(Attribute.MAX_HEALTH).getBaseValue()));
                                }

                                // 메시지 전송
                                SendMessage.sendMessagePlayer(
                                        victim,
                                        Component.text(actualMaster.getName() + "님의 노예가 되었습니다.", NamedTextColor.RED)
                                );
                                if (attacker.equals(actualMaster)) {
                                    SendMessage.sendMessagePlayer(
                                            attacker,
                                            Component.text(victim.getName() + "님이 노예가 되었습니다.", NamedTextColor.GREEN)
                                    );
                                } else {
                                    SendMessage.sendMessagePlayer(
                                            attacker,
                                            Component.text(victim.getName() + "님을 주인을 위해 노예로 만들었습니다.", NamedTextColor.GREEN)
                                    );
                                    SendMessage.sendMessagePlayer(
                                            actualMaster,
                                            Component.text(attacker.getName() + "님이 " + victim.getName() + "님을 노예로 만들어주었습니다.", NamedTextColor.GREEN)
                                    );
                                }
                            }

                            // 노예를 실제 주인 위치로 텔레포트 (항상 실행)
                            if (actualMaster != null) {
                                victim.teleport(actualMaster.getLocation());
                            }

                        } else if (slaves.containsKey(victimUUID) && slaves.get(victimUUID).equals(attackerUUID)) {
                            // 노예가 주인에게 죽은 경우 - 불사의 토템 사용
                            useTotemOfUndying(victim, true); // 움직임 제한 해제

                        } else {
                            // 주인-노예 관계나 쫓고 쫓기는 관계가 아닌 경우 - 자연사 처리
                            deadPlayers.put(victimUUID, System.currentTimeMillis());
                            frozenPlayers.put(victimUUID, 120); // 120초

                            SendMessage.sendMessagePlayer(
                                    victim,
                                    Component.text("기절되어 2분동안 움직일 수 없습니다.", NamedTextColor.RED)
                            );
                        }

                    } else if (slaves.containsKey(attackerUUID)) {
                        // 노예가 주인을 공격하려는 경우
                        UUID masterUUID = slaves.get(attackerUUID);
                        if (masterUUID.equals(victimUUID)) {
                            event.setCancelled(true);
                            SendMessage.sendMessagePlayer(
                                    attacker,
                                    Component.text("하극상은 안됩니다", NamedTextColor.RED)
                            );
                        }
                    }
                } else {
                    if (frozenPlayers.containsKey(playerUUID))
                        event.setCancelled(true);
                    cancelDeathEvent(event, victim);
                }
            } else {
                // 자연사
                if (frozenPlayers.containsKey(playerUUID))
                    event.setCancelled(true);
                cancelDeathEvent(event, victim);
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!gameActive) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (item != null && item.getType() == Material.DIAMOND &&
                (event.getAction().name().contains("RIGHT_CLICK"))) {

            UUID playerUUID = player.getUniqueId();
            TeamColor playerColor = playerColors.get(playerUUID);
            TeamColor currentColor = playerColor;

            while (true) {
                TeamColor nextColor = currentColor.next();
                OfflinePlayer target = findPlayerWithColor(player, nextColor);

                if (target == null || slaves.containsKey(target.getUniqueId()) || playerColor == nextColor) {
                    currentColor = nextColor;
                    continue;
                }

                if (!target.isOnline()) {
                    SendMessage.sendMessagePlayer(
                            player,
                            Component.text("타겟이 오프라인 상태입니다.", NamedTextColor.RED)
                    );
                    break;
                } else if (!target.getPlayer().getWorld().equals(player.getWorld())) {
                    SendMessage.sendMessagePlayer(
                            player,
                            Component.text("타겟이 같은 월드에 존재하지 않습니다.", NamedTextColor.RED)
                    );
                    break;
                }
                // 수동 다이아 소모
                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                } else {
                    // 아이템이 1개면 제거
                    player.getInventory().removeItem(item);
                }

                // 인벤토리 업데이트 (안전하게)
                player.updateInventory();
                // 방향 표시
                showDirectionToTarget(player, target.getPlayer());
                break;
            }
        }
    }

    private OfflinePlayer findPlayerWithColor(Player sender, TeamColor color) {
        for (Map.Entry<UUID, TeamColor> entry : playerColors.entrySet()) {
            if (entry.getValue() == color) {
                return Bukkit.getOfflinePlayer(entry.getKey());
            }
        }
        return null;
    }

    private void showDirectionToTarget(Player player, Player target) {
        Location playerLoc = player.getLocation();
        Location targetLoc = target.getLocation();

        // 방향 벡터 계산
        double tempDx = targetLoc.getX() - playerLoc.getX();
        double tempDy = targetLoc.getY() - playerLoc.getY();
        double tempDz = targetLoc.getZ() - playerLoc.getZ();

        // 정규화
        double length = Math.sqrt(tempDx * tempDx + tempDy * tempDy + tempDz * tempDz);
        final double dx = tempDx / length;
        final double dy = tempDy / length;
        final double dz = tempDz / length;

        // 3초 동안 파티클 표시 (20틱 = 1초)
        final int duration = 60; // 3초 = 60틱
        final int interval = 2; // 2틱마다 실행 (더 부드러운 효과)

        BukkitRunnable particleTask = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= duration) {
                    this.cancel();
                    return;
                }

                // 파티클 생성
                for (int i = 1; i <= 8; i++) {
                    Location particleLoc = playerLoc.clone().add(dx * i, dy * i + 1.5, dz * i);
                    player.getWorld().spawnParticle(Particle.DUST, particleLoc, 1,
                            new Particle.DustOptions(org.bukkit.Color.RED, 1.5f)); // 크기도 조금 키움
                }

                ticks += interval;
            }
        };

        // 스케줄러 실행
        particleTask.runTaskTimer(this, 0L, interval); // this는 현재 플러그인 인스턴스

        SendMessage.sendMessagePlayer(
                player,
                Component.text("타겟 방향을 3초간 표시합니다!", NamedTextColor.YELLOW)
        );
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (gameActive) {
            SendMessage.sendMessagePlayer(
                    event.getPlayer(),
                    Component.text("게임이 진행 중입니다.", NamedTextColor.YELLOW)
            );
        }
    }

    @EventHandler
    public void onPlayerAdvancement(PlayerAdvancementDoneEvent event) {
        // 발전과제 메시지 숨김
        event.message(null);
    }
}