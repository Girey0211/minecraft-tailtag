package com.example.tailtag.player.enums;

public enum PlayerRole {

    MASTER("마스터"),
    SLAVE("노예");

    private final String displayName;

    PlayerRole(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

}
