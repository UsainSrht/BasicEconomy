package me.usainsrht.basiceconomy.api;

import org.jetbrains.annotations.Nullable;

public class BasicEconomyAPI {

    private static EconomyManager manager;

    @Nullable
    public static EconomyManager getEconomyManager() {
        return manager;
    }

    public static void setEconomyManager(EconomyManager economyManager) {
        if (manager != null) {
            throw new UnsupportedOperationException("EconomyManager is already registered.");
        }
        manager = economyManager;
    }
}
