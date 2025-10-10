package com.example.tailtag;

import com.example.tailtag.events.EntityDamage;
import com.example.tailtag.events.ServerAutoMessage;
import com.example.tailtag.events.TrackPlayer;
import com.example.tailtag.player.PlayerManager;
import com.example.tailtag.player.enums.PlayerColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
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

    private final PlayerManager playerManager = new PlayerManager();
    
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
                reset(player);
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

    private void startGameTasks() {
        // 게임 상태 체크 태스크 (1초마다)
        gameTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!gameActive) return;

                playerManager.updatePlayers();
                checkGameEnd();
                playerManager.updateSlave();
                playerManager.updateStunnedPlayer();
                playerManager.updateDragonEggBuff();
                playerManager.showHeartBeat();
            }
        }.runTaskTimer(this, 20L, 20L);
    }

    private void checkGameEnd() {
        Player winner = playerManager.calculateWinner();
        if (winner == null) return;

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
                reset(winner);
            }
        }.runTaskLater(this, 60L);
    }

    public void startGame(Player commander) {
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
        playerManager.addPlayers(new ArrayList<>(onlinePlayers));

        World world = gameCenter.getWorld();
        playerManager.spawnPlayers(gameCenter, GAME_AREA_SIZE);

        // send "game start" message
        for (Player player : onlinePlayers) {
            PlayerColor color = playerManager.getPlayerColor(player.getUniqueId());
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

    public void reset(Player commander) {
        gameActive = false;

        playerManager.resetGame();
        if (gameTask != null) {
            gameTask.cancel();
        }

        startGameTasks();

        SendMessage.sendMessagePlayer(
                commander,
                Component.text("게임이 리셋되었습니다.", NamedTextColor.GREEN)
        );
    }
}