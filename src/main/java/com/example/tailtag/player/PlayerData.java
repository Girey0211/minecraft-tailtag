package com.example.tailtag.player;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.util.*;

// 좀 짬뽕이긴 한데 일단 개발 하고
// 후에 db겸 구현체로써 더 다듬을 예정
// 기본적으로 main <-> PlayerData(Service와 같은 구현체) <-> TailPlayer(유사 DAO느낌)
// main은 uuid만 사용하여 플로우만 관리하는 방향으로 하고싶음(가능하면)
// PlayerData에서 메인 로직 관리
public class PlayerData {

    private final Map<UUID, TailPlayer> playerMap = new HashMap<>();
    private final Map<PlayerColor, TailPlayer> originalColorMap = new HashMap<>();
    private final List<UUID> survivedPlayers = new ArrayList<>();
    private final List<UUID> slavePlayers = new ArrayList<>();
    private final List<UUID> stunnedPlayers = new ArrayList<>();
    private final List<UUID> outPlayers = new ArrayList<>();

    public void addPlayers(List<Player> players) {
        Collections.shuffle(players);
//        players.addAll(players);

        PlayerColor[] colors = PlayerColor.values();

        for (int i = 0; i < players.size(); i++) {
            PlayerColor color = colors[i % players.size()];
            playerMap.put(players.get(i).getUniqueId(), new TailPlayer(players.get(i), color));
            originalColorMap.put(color, playerMap.get(players.get(i).getUniqueId()));
        }
    }

    public void makeSlave(UUID masterUUID, UUID slaveUUID) {
        TailPlayer masterPlayer = playerMap.get(masterUUID);
        TailPlayer slavePlayer = playerMap.get(masterUUID);

        List<TailPlayer> newSlaves = slavePlayer.enslave(masterPlayer);
        masterPlayer.addSlave(slaveUUID, newSlaves);
    }

    public TailPlayer getPlayer(@NotNull UUID uniqueId) {
        return playerMap.get(uniqueId);
    }

    public Player getMasterPlayer(UUID uuid) {
        return playerMap.get(uuid).getMaster().getPlayer();
    }

    public PlayerColor getPlayerColor(@NotNull UUID uniqueId) {
        return playerMap.get(uniqueId).getColor();
    }

    public void clear() {
        playerMap.clear();
        originalColorMap.clear();
        clearLists();
    }

    private void clearLists() {
        survivedPlayers.clear();
        slavePlayers.clear();
        stunnedPlayers.clear();
        outPlayers.clear();
    }

    public boolean isSlave(UUID uuid) {
        return playerMap.get(uuid).getIsSlave();
    }

    /**
     * isMaster
     * @param masterUUID
     * @param slaveUUID
     * return whether the first player is the master of the second player.\
     */
    public boolean isMaster(UUID masterUUID, UUID slaveUUID) {
        return getMasterPlayer(slaveUUID).getUniqueId().equals(masterUUID);
    }

    public void deadNaturally(UUID uuid) {
        TailPlayer player = playerMap.get(uuid);
        player.stun();
    }

    public boolean isStunned(UUID uuid) {
        return playerMap.get(uuid).getState() == PlayerState.STUN;
    }

    public List<UUID> getSurvivedPlayer() {
        return survivedPlayers;
    }

    public void updateSlave() {
        for (UUID uuid : slavePlayers) {
            TailPlayer player = playerMap.get(uuid);
            Player slave = player.getPlayer();
            Player master = player.getMaster().getPlayer();

            if (!slave.isOnline()) continue;

            // damage if too far from master
            if (master.isOnline()) {
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

            // slave debuff
            slave.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 25, 1, false, false));
        }
    }

    public void updateStunnedPlayer() {
        for (UUID uuid : stunnedPlayers) {
            TailPlayer stunnedPlayer = playerMap.get(uuid);
            Player player = stunnedPlayer.getPlayer();

            long deathDuration = System.currentTimeMillis() - stunnedPlayer.getDeathTime();
            if (deathDuration >= 120000) {
                stunnedPlayer.unstun();
                for (PotionEffect effect : player.getActivePotionEffects()) {
                    player.removePotionEffect(effect.getType());
                }
                player.setHealth(20);
                player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
                player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 1200, 0));
            } else {
                if (!player.isOnline()) continue;
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 25, 255, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 25, -10, false, false));
            }
        }
    }

    public void updatePlayerState() {
        clearLists();
        for (Map.Entry<UUID, TailPlayer> entry : playerMap.entrySet()) {
            UUID uuid = entry.getKey();
            TailPlayer player = entry.getValue();

            if (player.getIsSlave()) slavePlayers.add(uuid);
            else survivedPlayers.add(uuid);
            switch (player.getState()) {
                case STUN -> stunnedPlayers.add(uuid);
                case OUT -> outPlayers.add(uuid);
            }
        }
    }

    public Player getPlayerByColor(PlayerColor hunterColor) {
        return originalColorMap.get(hunterColor).getPlayer();
    }

    private TailPlayer getTailPlayerByColor(PlayerColor hunterColor) {
        return originalColorMap.get(hunterColor);
    }

    public Player getTargetPlayer(UUID uuid) {
        Player targetPlayer = null;
        PlayerColor nowColor = getPlayerColor(uuid);
        while (targetPlayer == null) {
            PlayerColor nextColor = nowColor.next();
            TailPlayer target = getTailPlayerByColor(nextColor);
            nowColor = nextColor;
            if (target.isAlive()) {
                targetPlayer = target.getPlayer();
            }
        }

        return targetPlayer;
    }

    public void unstun(UUID uuid) {
        playerMap.get(uuid).unstun();
    }

    public void stun(UUID uuid) {
        playerMap.get(uuid).stun();
    }
}
