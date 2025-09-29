package com.example.tailtag.events;

import com.example.tailtag.SendMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ServerAutoMessage implements Listener {

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        event.joinMessage(null); // 접속 메시지 숨기기
        SendMessage.sendMessagePlayer(
                event.getPlayer(),
                Component.text("게임이 진행 중입니다.", NamedTextColor.YELLOW)
        );
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        event.quitMessage(null); // 퇴장 메시지 숨기기
    }

    @EventHandler
    public void onPlayerAdvancement(PlayerAdvancementDoneEvent event) {
        // 발전과제 메시지 숨김
        event.message(null);
    }
}
