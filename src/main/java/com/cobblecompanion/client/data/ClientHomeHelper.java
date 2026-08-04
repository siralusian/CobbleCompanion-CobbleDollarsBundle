package com.cobblecompanion.client.data;

/** Hält die zuletzt vom Server empfangene Home-Tab-Zusammenfassung (Dex-Zähler + ToDo-Kurzübersicht). */
public class ClientHomeHelper {

    private static int seen = 0;
    private static int caught = 0;
    private static int living = 0;
    private static int evolveReady = 0;
    private static int catchNeeded = 0;
    private static boolean hasData = false;

    private static int misplacedCount = 0;
    private static boolean hasSlotCheckData = false;

    public static void setSummary(int seen, int caught, int living, int evolveReady, int catchNeeded) {
        ClientHomeHelper.seen = seen;
        ClientHomeHelper.caught = caught;
        ClientHomeHelper.living = living;
        ClientHomeHelper.evolveReady = evolveReady;
        ClientHomeHelper.catchNeeded = catchNeeded;
        ClientHomeHelper.hasData = true;
        // Slot-Prüfung-Ergebnis der vorigen Anfrage zurücksetzen, falls die Option inzwischen
        // deaktiviert wurde (dann kommt kein HomeSlotCheckResponsePacket mehr nach).
        ClientHomeHelper.hasSlotCheckData = false;
    }

    public static void setSlotCheckResult(int misplacedCount) {
        ClientHomeHelper.misplacedCount = misplacedCount;
        ClientHomeHelper.hasSlotCheckData = true;
    }

    public static int getSeen() { return seen; }
    public static int getCaught() { return caught; }
    public static int getLiving() { return living; }
    public static int getEvolveReady() { return evolveReady; }
    public static int getCatchNeeded() { return catchNeeded; }
    public static boolean hasData() { return hasData; }
    public static int getMisplacedCount() { return misplacedCount; }
    public static boolean hasSlotCheckData() { return hasSlotCheckData; }
}
