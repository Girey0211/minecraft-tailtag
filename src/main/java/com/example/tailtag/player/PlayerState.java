package com.example.tailtag.player;

public enum PlayerState {

    ALIVE("생존"),
    STUN("기절"),
    OUT("탈락");

    private final String displayName;

    PlayerState(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

}
