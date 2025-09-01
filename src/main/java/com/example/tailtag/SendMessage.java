package com.example.tailtag;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

import static org.bukkit.Bukkit.broadcast;
import static org.bukkit.Bukkit.getOnlinePlayers;

public class SendMessage extends JavaPlugin {
    private static final String name = TailTagPlugin.name;
    private static final String version = TailTagPlugin.version;

    public static void logConsole(String msg) {
        Logger logger = Bukkit.getServer().getLogger();
        logger.info("[%plugin%] ".replace("%plugin%", name) + msg);
    }

    public static void broadcastMessage(Component msg) {
        broadcast(Component.text("[%plugin%] ".replace("%plugin%", name))
                .color(NamedTextColor.GOLD)
                .append(msg)
        );
    }

    public static void sendMessagePlayer(Player player, Component msg) {
        player.sendMessage(Component.text("[%plugin% → %name%] "
                        .replace("%plugin%", name)
                        .replace("%name%", player.getName()))
                .color(NamedTextColor.GOLD)
                .append(msg)
        );

        logConsole(player.getName() + "  -  " + msg);
    }

    public static void sendMessageOP(Component msg) {
        for (Player player : getOnlinePlayers())
            if (player.isOp())
                sendMessagePlayer(player, msg);
    }

    public static void sendTitle(Player player, Component component, Title.Times time) {
        Component title = Component.text("");
        Title newTitle = Title.title(title, component, time);
        player.showTitle(newTitle);
    }

    public static void sendActionBar(Player player, Component msg) {
        player.sendActionBar(msg);
    }
}