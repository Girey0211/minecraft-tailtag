package com.example.tailtag.player;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class TailPlayer {

    private final Player player;
    private final Map<UUID, TailPlayer> slaveMap = new HashMap<>();

    private PlayerColor color;
    private TailPlayer masterPlayer;
    private PlayerState playerState;
    private Boolean isSlave;
    private Long deathTime;

    public TailPlayer(Player player, PlayerColor color) {
        this.player = player;
        this.color = color;
        this.playerState = PlayerState.ALIVE;
        this.isSlave = false;
        this.deathTime = 0L;
    }

    public Player getPlayer() {
        return player;
    }

    public PlayerColor getColor() {
        return color;
    }

    // get master player
    // if class has no master, return itself
    public @NotNull TailPlayer getMaster() {
        return masterPlayer != null ? masterPlayer : this;
    }

    public UUID getUUID() {
        return player.getUniqueId();
    }

    public PlayerState getState() {
        return playerState;
    }

    public void addSlave(UUID slaveUUID, List<TailPlayer> slavePlayerList) {
        for (TailPlayer newSlave : slavePlayerList) {
            slaveMap.put(newSlave.getUUID(), newSlave);
        }

        if (player.getAttribute(Attribute.MAX_HEALTH) != null) {
            player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(Math.max(8.0, 20.0 - 2.0 * getSlaveCount()));
            player.setHealth(Math.min(player.getHealth(), player.getAttribute(Attribute.MAX_HEALTH).getBaseValue()));
        }
    }

    public int getSlaveCount() {
        return slaveMap.size();
    }

    public List<TailPlayer> enslave(TailPlayer masterPlayer) {
        this.masterPlayer = masterPlayer;
        this.isSlave = true;
        this.color = masterPlayer.getColor();

        AttributeInstance victimMaxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (victimMaxHealthAttr != null) {
            victimMaxHealthAttr.setBaseValue(8.0);
            player.setHealth(8.0);
        }

        List<TailPlayer> newSlaves = new ArrayList<>(slaveMap.values().stream()
                .peek(obj -> obj.changeMaster(masterPlayer))
                .toList());
        newSlaves.add(this);
        return newSlaves;
    }

    private void changeMaster(TailPlayer masterPlayer) {
        this.masterPlayer = masterPlayer;
        this.color = masterPlayer.getColor();
    }

    public boolean isMaster(UUID uuid) {
        return uuid.equals(player.getUniqueId());
    }

    public boolean isMaster(TailPlayer player) {
        return masterPlayer.getUUID().equals(player.getUUID());
    }

    public void stun() {
        playerState = PlayerState.STUN;
        deathTime = System.currentTimeMillis();
    }

    public boolean getIsSlave() {
        return isSlave;
    }

    public long getDeathTime() {
        return deathTime;
    }

    public void unstun() {
        playerState = PlayerState.ALIVE;
        deathTime = 0L;
    }

    public boolean isAlive() {
        return playerState != PlayerState.OUT && !isSlave;
    }
}
