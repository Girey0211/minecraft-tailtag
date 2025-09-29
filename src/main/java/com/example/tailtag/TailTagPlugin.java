package com.example.tailtag;

import com.example.tailtag.events.EntityDamage;
import com.example.tailtag.events.ServerAutoMessage;
import com.example.tailtag.events.TrackPlayer;
import com.example.tailtag.player.enums.PlayerColor;
import com.example.tailtag.player.PlayerManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
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

    private boolean gameActive = false;
    private Location gameCenter;
    private final int GAME_AREA_SIZE = 20; // 20청크
    private BukkitTask gameTask;
    private BukkitTask heartbeatTask;


    @Override
    public void onEnable() {
        getLogger().info("꼬리잡기 플러그인이 활성화되었습니다!");

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(this, this);
        pm.registerEvents(new EntityDamage(), this);
        pm.registerEvents(new TrackPlayer(this), this);
        pm.registerEvents(new ServerAutoMessage(), this);

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
        PlayerManager.addPlayers(new ArrayList<>(onlinePlayers));

        spawnPlayers(new ArrayList<>(onlinePlayers));

        // send "game start" message
        for (Player player : onlinePlayers) {
            PlayerColor color = PlayerManager.getPlayerColor(player.getUniqueId());
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
        PlayerManager.clear();

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
                PlayerManager.updateSlave();
                PlayerManager.updateStunnedPlayer();
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
        PlayerManager.updatePlayerState();
    }

    private void checkGameEnd() {
        if (!gameActive) return;

        List<UUID> survivedPlayerList = PlayerManager.getSurvivedPlayer();

        if (survivedPlayerList.size() == 1) {
            UUID winnerUUID = survivedPlayerList.getFirst();
            Player winner = Bukkit.getPlayer(winnerUUID);

            if (winner != null) {
                SendMessage.broadcastMessage(
                        Component.text(
                                winner.getName(), NamedTextColor.GOLD)
                                .append(Component.text("님이 승리하셨습니다!")
                                )
                );

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

            if (PlayerManager.isSlave(playerUUID)) {
                // if player is slave, show distance between master and slave to player
                Player master = PlayerManager.getMasterPlayer(playerUUID);

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
                PlayerColor playerColor = PlayerManager.getPlayerColor(playerUUID);
                if (playerColor != null) {
                    Player hunter = PlayerManager.getHunterPlayer(playerUUID);
                    double distance = player.getLocation().distance(hunter.getLocation());
                    if (distance <= 30) {
                        Location loc = player.getLocation();
                        player.sendActionBar(Component.text("❤", NamedTextColor.RED));
                        player.playSound(loc, Sound.ENTITY_WARDEN_HEARTBEAT, 0.5f, 1.0f);
                    }
                }
            }
        }
    }
}