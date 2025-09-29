package com.example.tailtag.events;

import com.example.tailtag.SendMessage;
import com.example.tailtag.player.PlayerManager;
import com.example.tailtag.player.enums.PlayerColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;

import java.util.UUID;

public class EntityDamage implements Listener {

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        UUID playerUUID = victim.getUniqueId();

        if (!(event instanceof EntityDamageByEntityEvent entityEvent)) {
            if (PlayerManager.isStunned(playerUUID))
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

                PlayerColor victimColor = PlayerManager.getPlayerColor(victimUUID);
                PlayerColor attackerColor = PlayerManager.getPlayerColor(attackerUUID);

                // 실제 주인 찾기 (killer가 노예인 경우 주인을 찾음)
                Player actualMaster = PlayerManager.getMasterPlayer(attackerUUID);
                UUID actualMasterUUID = actualMaster.getUniqueId();

                // 올바른 색깔 순서로 잡았는지 확인
                Player targetPlayer = PlayerManager.getTargetPlayer(actualMasterUUID);

                if (targetPlayer.getUniqueId().equals(victimUUID)) {
                    // make slave
                    if (!PlayerManager.isSlave(victimUUID)) {
                        PlayerManager.makeSlave(actualMasterUUID, victimUUID);

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

                } else if (PlayerManager.isSlave(victimUUID) && PlayerManager.isMaster(attackerUUID, victimUUID)) {
                    // 노예가 주인에게 죽은 경우 - 불사의 토템 사용
                    useTotemOfUndying(victim, true); // 움직임 제한 해제
                } else {
                    // 주인-노예 관계나 쫓고 쫓기는 관계가 아닌 경우 - 자연사 처리
                    PlayerManager.deadNaturally(victimUUID);

                    SendMessage.sendMessagePlayer(
                            victim,
                            Component.text("기절되어 2분동안 움직일 수 없습니다.", NamedTextColor.RED)
                    );
                }

            } else if (PlayerManager.isSlave(attackerUUID)) {
                // slave kill player
                if (PlayerManager.isMaster(victimUUID, attackerUUID)) {
                    event.setCancelled(true);
                    SendMessage.sendMessagePlayer(
                            attacker,
                            Component.text("하극상은 안됩니다", NamedTextColor.RED)
                    );
                }
            }
        } else {
            // monster damage
            if (PlayerManager.isStunned(playerUUID))
                event.setCancelled(true);
            cancelDeathEvent(event, victim);
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
            PlayerManager.unstun(playerUUID);
            SendMessage.sendMessagePlayer(
                    player,
                    Component.text("주인말을 잘 들으십쇼.", NamedTextColor.GREEN)
            );
        } else {
            // 자연사의 경우 - 2분간 움직임 제한
            PlayerManager.stun(playerUUID);
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
}
