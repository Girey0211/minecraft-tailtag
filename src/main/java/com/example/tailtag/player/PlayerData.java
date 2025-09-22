package com.example.tailtag.player;

import com.example.tailtag.player.enums.PlayerColor;
import com.example.tailtag.player.enums.PlayerCondition;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.util.*;

// 좀 짬뽕이긴 한데 일단 개발 하고
// 후에 db겸 구현체로써 더 다듬을 예정
// 기본적으로 main <-> PlayerData(Service와 같은 구현체) <-> TailPlayer(유사 DAO느낌)
// main은 uniqueId만 사용하여 플로우만 관리하는 방향으로 하고싶음(가능하면)
// PlayerData에서 메인 로직 관리
public class PlayerData {

    private final Map<UUID, TailPlayer> playerMap = new HashMap<>();
    private final Map<PlayerColor, TailPlayer> originalColorMap = new HashMap<>();
    private final Map<PlayerColor, Boolean> colorMap = new HashMap<>(); // key: player color, value: whether player (slave, out) or not
    private final List<UUID> survivedPlayers = new ArrayList<>();
    private final List<UUID> slavePlayers = new ArrayList<>();
    private final List<UUID> stunnedPlayers = new ArrayList<>();
    private final List<UUID> outPlayers = new ArrayList<>();

    public void addPlayers(List<Player> players) {
        Collections.shuffle(players);
        PlayerColor[] colors = PlayerColor.values();
        for (int i = 0; i < players.size(); i++) {
            PlayerColor color = colors[i % players.size()];
            TailPlayer tailPlayer = new TailPlayer(players.get(i), color);
            playerMap.put(players.get(i).getUniqueId(), tailPlayer);
            colorMap.put(color, false);
            originalColorMap.put(color, tailPlayer);
        }
    }

    public void makeSlave(UUID masterUUID, UUID slaveUUID) {
        TailPlayer masterPlayer = playerMap.get(masterUUID);
        TailPlayer slavePlayer = playerMap.get(slaveUUID);

        List<TailPlayer> newSlaves = slavePlayer.enslave(slavePlayer);
        Player slave = slavePlayer.getPlayer();
        AttributeInstance victimMaxHealthAttr = slave.getAttribute(Attribute.MAX_HEALTH);
        if (victimMaxHealthAttr != null) {
            victimMaxHealthAttr.setBaseValue(8.0);
            slave.setHealth(8.0);
        }

        masterPlayer.addSlave(newSlaves);
        Player master = masterPlayer.getPlayer();
        if (master.getAttribute(Attribute.MAX_HEALTH) != null) {
            master.getAttribute(Attribute.MAX_HEALTH).setBaseValue(Math.max(8.0, 20.0 - 2.0 * masterPlayer.getSlaveCount()));
            master.setHealth(Math.min(master.getHealth(), master.getAttribute(Attribute.MAX_HEALTH).getBaseValue()));
        }
    }

    public TailPlayer getPlayer(@NotNull UUID uniqueId) {
        return playerMap.get(uniqueId);
    }

    public Player getMasterPlayer(UUID uniqueId) {
        return playerMap.get(uniqueId).getMaster().getPlayer();
    }

    public PlayerColor getPlayerColor(@NotNull UUID uniqueId) {
        return playerMap.get(uniqueId).getColor();
    }

    public void clear() {
        playerMap.clear();
        originalColorMap.clear();
        colorMap.clear();
        clearLists();
    }

    public boolean isSlave(UUID uniqueId) {
        return playerMap.get(uniqueId).getIsSlave();
    }

    /**
     * isMaster
     * @param masterUUID target master player
     * @param slaveUUID target slave player
     * @return whether the first player is the master of the second player.
     */
    public boolean isMaster(UUID masterUUID, UUID slaveUUID) {
        return getMasterPlayer(slaveUUID).getUniqueId().equals(masterUUID);
    }

    public void deadNaturally(UUID uniqueId) {
        TailPlayer player = playerMap.get(uniqueId);
        player.stun();
    }

    public boolean isStunned(UUID uniqueId) {
        return playerMap.get(uniqueId).getState() == PlayerCondition.STUN;
    }

    public List<UUID> getSurvivedPlayer() {
        return survivedPlayers;
    }

    public void updateSlave() {
        for (UUID uniqueId : slavePlayers) {
            TailPlayer player = playerMap.get(uniqueId);
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
        for (UUID uniqueId : stunnedPlayers) {
            TailPlayer stunnedPlayer = playerMap.get(uniqueId);
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
            UUID uniqueId = entry.getKey();
            TailPlayer player = entry.getValue();

            if (player.isAlive()) survivedPlayers.add(uniqueId);
            else if (player.getIsSlave()) slavePlayers.add(uniqueId);
            switch (player.getState()) {
                case STUN -> stunnedPlayers.add(uniqueId);
                case OUT -> outPlayers.add(uniqueId);
            }
        }
    }

    public Player getPlayerByColor(PlayerColor color) {
        return originalColorMap.get(color).getPlayer();
    }

    public void unstun(UUID uniqueId) {
        playerMap.get(uniqueId).unstun();
    }

    public void stun(UUID uniqueId) {
        playerMap.get(uniqueId).stun();
    }

    public Player getTargetPlayer(UUID uniqueId) {
        PlayerColor nowColor = playerMap.get(uniqueId).getColor().next();
        Player target = null;
        while (target == null) {
            if (isColorEliminated(nowColor)) {
                nowColor = nowColor.next();
                continue;
            }
            target = getTailPlayerByColor(nowColor).getPlayer();
        }
        return target;
    }
    
    public Player getHunterPlayer(UUID uniqueId) {
        PlayerColor nowColor = playerMap.get(uniqueId).getColor().prev();
        Player hunter = null;
        while (hunter == null) {
            if (isColorEliminated(nowColor)) {
                nowColor = nowColor.prev();
                continue;
            }
            hunter = getTailPlayerByColor(nowColor).getPlayer();
        }
        return hunter;
    }

    private TailPlayer getTailPlayerByColor(PlayerColor hunterColor) {
        return originalColorMap.get(hunterColor);
    }

    private void clearLists() {
        survivedPlayers.clear();
        slavePlayers.clear();
        stunnedPlayers.clear();
        outPlayers.clear();
    }
    
    private boolean isColorEliminated(PlayerColor color) {
        return colorMap.get(color);
    }
}
