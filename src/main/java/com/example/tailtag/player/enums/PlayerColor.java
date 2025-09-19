package com.example.tailtag.player.enums;

import net.kyori.adventure.text.format.NamedTextColor;

public enum PlayerColor {

    RED(NamedTextColor.RED, "빨강"),
    ORANGE(NamedTextColor.GOLD, "주황"),
    YELLOW(NamedTextColor.YELLOW, "노랑"),
    GREEN(NamedTextColor.GREEN, "초록"),
    BLUE(NamedTextColor.BLUE, "파랑"),
    INDIGO(NamedTextColor.DARK_BLUE, "남색"),
    PURPLE(NamedTextColor.DARK_PURPLE, "보라"),
    PINK(NamedTextColor.LIGHT_PURPLE, "핑크"),
    GRAY(NamedTextColor.GRAY, "회색"),
    BLACK(NamedTextColor.BLACK, "검정");

    private final NamedTextColor chatColor;
    private final String displayName;

    PlayerColor(NamedTextColor chatColor, String displayName) {
        this.chatColor = chatColor;
        this.displayName = displayName;
    }

    public NamedTextColor getNamedTextColor() {
        return chatColor;
    }

    public String getDisplayName() {
        return displayName;
    }

    public PlayerColor next() {
        PlayerColor[] vals = values();
        return vals[(this.ordinal() + 1) % vals.length];
    }

    public PlayerColor prev() {
        PlayerColor[] vals = values();
        return vals[(this.ordinal() - 1) % vals.length];
    }

}
