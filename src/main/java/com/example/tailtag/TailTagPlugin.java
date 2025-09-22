package com.example.tailtag;

import com.example.tailtag.player.enums.PlayerColor;
import com.example.tailtag.player.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
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

    private final PlayerData playerData = new PlayerData();
    private boolean gameActive = false;
    private Location gameCenter;
    private final int GAME_AREA_SIZE = 20; // 20청크
    private BukkitTask gameTask;
    private BukkitTask heartbeatTask;


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

        // region start exception
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
        // endregion

        gameActive = true;
        gameCenter = commander.getLocation();

        // set player data
        playerData.addPlayers(new ArrayList<>(onlinePlayers));

        spawnPlayers(new ArrayList<>(onlinePlayers));

        // send "game start" message
        for (Player player : onlinePlayers) {
            PlayerColor color = playerData.getPlayerColor(player.getUniqueId());
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

            PlayerColor targetColor = color.next();
            if (targetColor != null) {
                SendMessage.sendMessagePlayer(
                        player,
                        Component.text("잡아야 할 색깔: ", NamedTextColor.YELLOW)
                                .append(Component.text(targetColor.getDisplayName(), targetColor.getNamedTextColor())
                                        .decoration(TextDecoration.BOLD, true))
                );
            }

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
        playerData.clear();

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

                updatePlayerData();
                checkGameEnd();
                playerData.updateSlave();
                playerData.updateStunnedPlayer();
                updateDragonEggEffects();
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

    private void updatePlayerData() {
        playerData.updatePlayerState();
    }

    private void checkGameEnd() {
        if (!gameActive) return;

        List<UUID> survivedPlayerList = playerData.getSurvivedPlayer();

        if (survivedPlayerList.size() == 1) {
            UUID winnerUUID = survivedPlayerList.getFirst();
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

    private void cancelDeathEvent(EntityDamageEvent event, Player player) {
        double finalHealth = player.getHealth() - event.getFinalDamage();
        if (finalHealth > 0) return;
        event.setCancelled(true);
        useTotemOfUndying(player, false);
    }

    private void useTotemOfUndying(Player player, boolean removeFrozen) {
        UUID playerUUID = player.getUniqueId();

        // 기존 포션 효과 모두 제거 (불사의 토템 버프 제거)
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        if (removeFrozen) {
            // 노예가 주인에게 죽은 경우 - 움직임 제한 해제
            playerData.unstun(playerUUID);
            SendMessage.sendMessagePlayer(
                    player,
                    Component.text("주인말을 잘 들으십쇼.", NamedTextColor.GREEN)
            );
        } else {
            // 자연사의 경우 - 2분간 움직임 제한
            playerData.stun(playerUUID);
            SendMessage.sendMessagePlayer(
                    player,
                    Component.text("기절되어 2분동안 움직일 수 없습니다.", NamedTextColor.RED)
            );
        }

        // 불사의 토템 효과
        player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue()); // 풀피로 회복
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation(), 30);
        player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
    }
    private void updateDragonEggEffects() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getInventory().contains(Material.DRAGON_EGG)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 25, 1, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.INSTANT_DAMAGE, 25, 0, false, false));
            }
        }
    }

    private void showHeartbeat() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!gameActive) continue;

            UUID playerUUID = player.getUniqueId();

            if (playerData.isSlave(playerUUID)) {
                // if player is slave, show distance between master and slave to player
                Player master = playerData.getMasterPlayer(playerUUID);

                if (master != null && master.isOnline()) {
                    double distance = player.getLocation().distance(master.getLocation());

                    player.sendActionBar(
                        Component.text("주인과의 거리: ", NamedTextColor.YELLOW)
                            .append(Component.text((int) distance))
                            .append(Component.text("블럭"))
                    );
                }
            } else {
                // detect nearby hunter
                PlayerColor playerColor = playerData.getPlayerColor(playerUUID);
                if (playerColor != null) {
                    Player hunter = playerData.getHunterPlayer(playerUUID);
                    double distance = player.getLocation().distance(hunter.getLocation());
                    if (distance <= 30) {
                        player.sendActionBar(Component.text("❤", NamedTextColor.RED));
                        // 사운드 로직 추가
                    }
                }
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!gameActive) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        UUID playerUUID = victim.getUniqueId();

        if (!(event instanceof EntityDamageByEntityEvent entityEvent)) {
            if (playerData.isStunned(playerUUID))
                event.setCancelled(true);
            cancelDeathEvent(event, victim);
            return;
        }
            // entity damage
        if (entityEvent.getDamager() instanceof Player attacker) {
            // player damage
            UUID attackerUUID = attacker.getUniqueId();
            UUID victimUUID = victim.getUniqueId();
            double finalHealth = victim.getHealth() - event.getFinalDamage();

            // make slave
            if (finalHealth <= 0) {
                // player kill player
                event.setCancelled(true);

                PlayerColor victimColor = playerData.getPlayerColor(victimUUID);
                PlayerColor attackerColor = playerData.getPlayerColor(attackerUUID);

                // 실제 주인 찾기 (killer가 노예인 경우 주인을 찾음)
                Player actualMaster = playerData.getMasterPlayer(attackerUUID);
                UUID actualMasterUUID = actualMaster.getUniqueId();

                // 올바른 색깔 순서로 잡았는지 확인
                Player targetPlayer = playerData.getTargetPlayer(actualMasterUUID);

                if (targetPlayer.getUniqueId().equals(victimUUID)) {
                    // make slave
                    if (!playerData.isSlave(victimUUID)) {
                        playerData.makeSlave(actualMasterUUID, victimUUID);

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
                    victim.teleport(actualMaster.getLocation());

                } else if (playerData.isSlave(victimUUID) && playerData.isMaster(attackerUUID, victimUUID)) {
                    // 노예가 주인에게 죽은 경우 - 불사의 토템 사용
                    useTotemOfUndying(victim, true); // 움직임 제한 해제
                } else {
                    // 주인-노예 관계나 쫓고 쫓기는 관계가 아닌 경우 - 자연사 처리
                    playerData.deadNaturally(victimUUID);

                    SendMessage.sendMessagePlayer(
                            victim,
                            Component.text("기절되어 2분동안 움직일 수 없습니다.", NamedTextColor.RED)
                    );
                }

            } else if (playerData.isSlave(attackerUUID)) {
                // slave kill player
                if (playerData.isMaster(victimUUID, attackerUUID)) {
                    event.setCancelled(true);
                    SendMessage.sendMessagePlayer(
                            attacker,
                            Component.text("하극상은 안됩니다", NamedTextColor.RED)
                    );
                }
            }
        } else {
            // monster damage
            if (playerData.isStunned(playerUUID))
                event.setCancelled(true);
            cancelDeathEvent(event, victim);
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
        if (item != null && item.getType() == Material.DIAMOND && (event.getAction().name().contains("RIGHT_CLICK"))) {
            UUID playerUUID = player.getUniqueId();
            Player targetPlayer = playerData.getTargetPlayer(playerUUID);

            if (!targetPlayer.isOnline()) {
                SendMessage.sendMessagePlayer(
                        player,
                        Component.text("타겟이 오프라인 상태입니다.", NamedTextColor.RED)
                );
            } else if (!targetPlayer.getWorld().equals(player.getWorld())) {
                SendMessage.sendMessagePlayer(
                        player,
                        Component.text("타겟이 같은 월드에 존재하지 않습니다.", NamedTextColor.RED)
                );
            } else {

                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                } else {
                    player.getInventory().removeItem(item);
                }

                player.updateInventory();
                // 방향 표시
                showDirectionToTarget(player, targetPlayer);
            }
        }
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