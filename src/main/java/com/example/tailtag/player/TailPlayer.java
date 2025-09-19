package com.example.tailtag.player;

import com.example.tailtag.player.enums.PlayerColor;
import com.example.tailtag.player.enums.PlayerCondition;
import com.example.tailtag.player.enums.PlayerRole;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class TailPlayer {

    private final Player player;
    private final Map<UUID, TailPlayer> slaveMap = new HashMap<>();

    private PlayerColor color;
    private TailPlayer masterPlayer;
    private PlayerCondition playerCondition;
    private PlayerRole role;
    private Long deathTime;

    public TailPlayer(Player player, PlayerColor color) {
        this.player = player;
        this.color = color;
        this.playerCondition = PlayerCondition.ALIVE;
        this.role = PlayerRole.MASTER;
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

    public PlayerCondition getState() {
        return playerCondition;
    }

    public void addSlave(List<TailPlayer> slavePlayerList) {
        for (TailPlayer newSlave : slavePlayerList) {
            slaveMap.put(newSlave.getUUID(), newSlave);
        }
    }

    public int getSlaveCount() {
        return slaveMap.size();
    }

    public List<TailPlayer> enslave(TailPlayer masterPlayer) {
        this.masterPlayer = masterPlayer;
        this.role = PlayerRole.SLAVE;
        this.color = masterPlayer.getColor();

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
        playerCondition = PlayerCondition.STUN;
        deathTime = System.currentTimeMillis();
    }

    public boolean getIsSlave() {
        return role == PlayerRole.SLAVE;
    }

    public long getDeathTime() {
        return deathTime;
    }

    public void unstun() {
        playerCondition = PlayerCondition.ALIVE;
        deathTime = 0L;
    }

    public boolean isAlive() {
        return playerCondition != PlayerCondition.OUT && role == PlayerRole.MASTER ;
    }
}
