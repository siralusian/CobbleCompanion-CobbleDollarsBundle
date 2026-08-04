package com.cobblecompanion.client.data;

/**
 * Client-seitiger Spiegel der globalen Server-Regeln (per ServerRulesSyncPacket empfangen).
 * canEdit gibt an, ob der lokale Spieler OP ist und die Regeln im Settings-Tab ändern darf.
 * Rein flüchtig - wird bei jedem Login/Änderung neu vom Server gesetzt, nicht persistiert.
 */
public class ClientServerRulesHelper {

    private static boolean forbidGifting = false;
    private static boolean allowTeleportToFriends = false;
    private static boolean canEdit = false;
    private static boolean cobbleDollarsAvailable = false;
    private static boolean createAvailable = false;
    private static boolean mobilePackagesAvailable = false;
    private static boolean rctAvailable = false;
    private static boolean earnFromNPC = false;
    private static boolean earnFromWildPokemon = false;
    private static double incomeMultiplier = 1.0;
    private static boolean onlineRewardEnabled = false;
    private static int onlineRewardIntervalMinutes = 30;
    private static long onlineRewardAmount = 1000;

    public static void apply(boolean forbidGifting, boolean allowTeleportToFriends, boolean canEdit,
            boolean cobbleDollarsAvailable, boolean createAvailable, boolean mobilePackagesAvailable,
            boolean rctAvailable, boolean earnFromNPC, boolean earnFromWildPokemon, double incomeMultiplier,
            boolean onlineRewardEnabled, int onlineRewardIntervalMinutes, long onlineRewardAmount) {
        ClientServerRulesHelper.forbidGifting = forbidGifting;
        ClientServerRulesHelper.allowTeleportToFriends = allowTeleportToFriends;
        ClientServerRulesHelper.canEdit = canEdit;
        ClientServerRulesHelper.cobbleDollarsAvailable = cobbleDollarsAvailable;
        ClientServerRulesHelper.createAvailable = createAvailable;
        ClientServerRulesHelper.mobilePackagesAvailable = mobilePackagesAvailable;
        ClientServerRulesHelper.rctAvailable = rctAvailable;
        ClientServerRulesHelper.earnFromNPC = earnFromNPC;
        ClientServerRulesHelper.earnFromWildPokemon = earnFromWildPokemon;
        ClientServerRulesHelper.incomeMultiplier = incomeMultiplier;
        ClientServerRulesHelper.onlineRewardEnabled = onlineRewardEnabled;
        ClientServerRulesHelper.onlineRewardIntervalMinutes = onlineRewardIntervalMinutes;
        ClientServerRulesHelper.onlineRewardAmount = onlineRewardAmount;
    }

    public static boolean isForbidGifting() { return forbidGifting; }
    public static boolean isAllowTeleportToFriends() { return allowTeleportToFriends; }
    public static boolean canEdit() { return canEdit; }
    public static boolean isCobbleDollarsAvailable() { return cobbleDollarsAvailable; }
    public static boolean isCreateAvailable() { return createAvailable; }
    public static boolean isMobilePackagesAvailable() { return mobilePackagesAvailable; }
    public static boolean isRctAvailable() { return rctAvailable; }
    public static boolean isEarnFromNPC() { return earnFromNPC; }
    public static boolean isEarnFromWildPokemon() { return earnFromWildPokemon; }
    public static double getIncomeMultiplier() { return incomeMultiplier; }
    public static boolean isOnlineRewardEnabled() { return onlineRewardEnabled; }
    public static int getOnlineRewardIntervalMinutes() { return onlineRewardIntervalMinutes; }
    public static long getOnlineRewardAmount() { return onlineRewardAmount; }
}
