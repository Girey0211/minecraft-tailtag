package com.example.tailtag.player;

import com.example.tailtag.player.enums.PlayerColor;
import org.bukkit.entity.Player;

import java.util.*;

public class PlayerRepository {

    private static final Map<UUID, TailPlayer> playerMap = new HashMap<>();
    private static final Map<PlayerColor, TailPlayer> originalColorMap = new HashMap<>();
    private static final Map<PlayerColor, Boolean> eleminatedColorMap = new HashMap<>(); // key: player color, value: whether player (slave, out) or not
    private static final List<UUID> survivedPlayers = new ArrayList<>();
    private static final List<UUID> slavePlayers = new ArrayList<>();
    private static final List<UUID> stunnedPlayers = new ArrayList<>();
    private static final List<UUID> outPlayers = new ArrayList<>();


    public static void addPlayer(Player player, PlayerColor color) {
        UUID uniqueId = player.getUniqueId();
        TailPlayer tailPlayer = new TailPlayer(player, color);
        playerMap.put(uniqueId, tailPlayer);
        eleminatedColorMap.put(color, false);
        originalColorMap.put(color, tailPlayer);
    }

    public static TailPlayer getPlayerByUniqueId(UUID uniqueId) {
        return playerMap.get(uniqueId);
    }

    public static TailPlayer getMasterPlayerByUniqueId(UUID slaveUniqueId) {
        return playerMap.get(slaveUniqueId).getMaster();
    }

    public static void resetData() {
        playerMap.clear();
        originalColorMap.clear();
        eleminatedColorMap.clear();
        clearPlayerState();
    }

    public static void clearPlayerState() {
        survivedPlayers.clear();
        slavePlayers.clear();
        stunnedPlayers.clear();
        outPlayers.clear();
    }

    public static List<TailPlayer> getPlayers() {
        return playerMap.values().stream().toList();
    }

    public static Collection<TailPlayer> getPlayerMap() {
        return playerMap.values();
    }

    public static void addSurvivedPlayer(UUID uniqueId) {
        survivedPlayers.add(uniqueId);
    }

    public static void addSlavePlayer(UUID uniqueId) {
        slavePlayers.add(uniqueId);
    }

    public static void addStunnedPlayer(UUID uniqueId) {
        stunnedPlayers.add(uniqueId);
    }

    public static void addOutPlayer(UUID uniqueId) {
        outPlayers.add(uniqueId);
    }

    public static List<UUID> getSlavePlayerUniqueIds() {
        return slavePlayers;
    }

    public static List<UUID> getStunnedPlayerUniqueIds() {
        return stunnedPlayers;
    }

    public static TailPlayer getPlayerByColor(PlayerColor color) {
        return originalColorMap.get(color);
    }

    public static boolean isColorEliminated(PlayerColor color) {
        return eleminatedColorMap.get(color);
    }

    public static List<UUID> getSurvivedPlayer() {
        return survivedPlayers;
    }
}
