package com.example.tailtag.player.enums;

public enum PlayerCondition {

    ALIVE("생존"),
    STUN("기절"),
    OUT("탈락"); // only made by command (normal game does not have this state)

    private final String displayName;

    PlayerCondition(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

}
