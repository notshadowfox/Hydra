package hgloot.plugin.network;

import java.util.Locale;

public enum BackendRole {
    STANDALONE,
    HUB,
    GAMEHUB,
    HG;

    public static BackendRole parse(String value) {
        if (value == null || value.isBlank()) {
            return STANDALONE;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return STANDALONE;
        }
    }

    public boolean networked() {
        return this != STANDALONE;
    }

    public boolean hubServices() {
        return this == STANDALONE || this == HUB;
    }

    public boolean gameHubServices() {
        return this == STANDALONE || this == GAMEHUB;
    }

    public boolean hungerGamesServices() {
        return this == STANDALONE || this == HG;
    }

    public boolean legacyMinigameServices() {
        return this == STANDALONE;
    }
}
