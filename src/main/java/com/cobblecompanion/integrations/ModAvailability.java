package com.cobblecompanion.integrations;

import net.neoforged.fml.ModList;

/**
 * Prüft einmalig, welche optionalen Fremd-Mods (RCT, Cobbledollars, Create) auf diesem
 * Server (bzw. im Singleplayer auf dem internen Server) installiert sind. Referenziert
 * bewusst NUR Mod-IDs als Strings, keine Klassen der Fremd-Mods - diese Klasse wird
 * unbedingt beim Mod-Start geladen und darf deshalb nie NoClassDefFoundError riskieren.
 */
public final class ModAvailability {

    private static boolean cobbledollars = false;
    private static boolean create = false;
    private static boolean mobilePackages = false;
    private static boolean rct = false;
    private static boolean curios = false;
    private static boolean customNpcs = false;
    private static boolean farmAndCharm = false;

    private ModAvailability() {}

    /** Einmalig beim Server-Start aufrufen (Singleplayer und Dedicated Server gleichermaßen). */
    public static void refresh() {
        cobbledollars = ModList.get().isLoaded("cobbledollars");
        create = ModList.get().isLoaded("create");
        mobilePackages = ModList.get().isLoaded("create_mobile_packages");
        rct = ModList.get().isLoaded("rctmod");
        curios = ModList.get().isLoaded("curios");
        customNpcs = ModList.get().isLoaded("customnpcs");
        farmAndCharm = ModList.get().isLoaded("farm_and_charm");
    }

    public static boolean isCobbleDollarsAvailable() { return cobbledollars; }
    public static boolean isCreateAvailable() { return create; }
    public static boolean isMobilePackagesAvailable() { return mobilePackages; }
    public static boolean isRctAvailable() { return rct; }
    public static boolean isCuriosAvailable() { return curios; }
    public static boolean isCustomNpcsAvailable() { return customNpcs; }
    public static boolean isFarmAndCharmAvailable() { return farmAndCharm; }
}
