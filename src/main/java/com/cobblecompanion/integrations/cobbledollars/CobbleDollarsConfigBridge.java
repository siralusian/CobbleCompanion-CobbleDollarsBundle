package com.cobblecompanion.integrations.cobbledollars;

import fr.harmex.cobbledollars.common.CobbleDollars;
import fr.harmex.cobbledollars.common.config.CommonConfig;

/**
 * Live-Zugriff auf CobbleDollars' eigene Einnahmequellen-Einstellungen (earnCobbleDollarsFromNPC,
 * earnCobbleDollarsFromWildPokemon, cobbleDollarsIncomeMultiplier) - CobbleDollars.config ist ein
 * öffentliches, direkt veränderbares statisches Feld, CommonConfig.Companion.save() persistiert
 * die Änderung in CobbleDollars' eigener Config-Datei. Aufrufer müssen VORHER
 * ModAvailability.isCobbleDollarsAvailable() geprüft haben.
 */
public final class CobbleDollarsConfigBridge {

    private CobbleDollarsConfigBridge() {}

    public static boolean isEarnFromNPC() {
        return CobbleDollars.config.getEarnCobbleDollarsFromNPC();
    }

    public static void setEarnFromNPC(boolean value) {
        CobbleDollars.config.setEarnCobbleDollarsFromNPC(value);
        CommonConfig.Companion.save();
    }

    public static boolean isEarnFromWildPokemon() {
        return CobbleDollars.config.getEarnCobbleDollarsFromWildPokemon();
    }

    public static void setEarnFromWildPokemon(boolean value) {
        CobbleDollars.config.setEarnCobbleDollarsFromWildPokemon(value);
        CommonConfig.Companion.save();
    }

    public static double getIncomeMultiplier() {
        return CobbleDollars.config.getCobbleDollarsIncomeMultiplier();
    }

    public static void setIncomeMultiplier(double value) {
        CobbleDollars.config.setCobbleDollarsIncomeMultiplier(Math.max(0.0, value));
        CommonConfig.Companion.save();
    }
}
