package com.example.tailtag.events;

import com.example.tailtag.SendMessage;
import com.example.tailtag.player.PlayerManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

public class TrackPlayer implements Listener {

    private final JavaPlugin plugin;

    public TrackPlayer(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (item != null && item.getType() == Material.DIAMOND && (event.getAction().name().contains("RIGHT_CLICK"))) {
            UUID playerUUID = player.getUniqueId();
            Player targetPlayer = PlayerManager.getTargetPlayer(playerUUID);

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
        particleTask.runTaskTimer(plugin, 0L, interval); // this는 현재 플러그인 인스턴스

        SendMessage.sendMessagePlayer(
                player,
                Component.text("타겟 방향을 3초간 표시합니다!", NamedTextColor.YELLOW)
        );
    }

}
