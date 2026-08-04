package com.cobblecompanion.data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-seitiger Cache, ob ein Spieler gerade Strg gedrückt hält - Minecraft synct das (anders
 * als Shift/Sneak, das über den Spieler-Bewegungszustand ohnehin repliziert wird) standardmäßig
 * NICHT zum Server, deshalb schickt der Client per CtrlKeyStatePacket eine Änderung, sobald sie
 * passiert (siehe ClientEventHandler.onClientTick). Rein flüchtig, keine Persistenz nötig - beim
 * Ausloggen wird der Eintrag entfernt (siehe CobbleCompanion.onPlayerLogout), damit ein neu
 * eingeloggter/erneut beigetretener Spieler nicht versehentlich einen alten "gedrückt"-Zustand erbt.
 */
public class ClientKeyStateManager {

    private static final Map<UUID, Boolean> ctrlHeld = new HashMap<>();
    /** Wie ctrlHeld, aber für Alt (siehe AltKeyStatePacket) - Nutzer-Vorgabe: Alt+Rechtsklick als eigener Kurzbefehl, getrennt von Strg+Rechtsklick, um Überschneidungen zu vermeiden. */
    private static final Map<UUID, Boolean> altHeld = new HashMap<>();

    public static void setCtrlHeld(UUID player, boolean held) {
        if (held) ctrlHeld.put(player, true);
        else ctrlHeld.remove(player);
    }

    public static boolean isCtrlHeld(UUID player) {
        return ctrlHeld.getOrDefault(player, false);
    }

    public static void setAltHeld(UUID player, boolean held) {
        if (held) altHeld.put(player, true);
        else altHeld.remove(player);
    }

    public static boolean isAltHeld(UUID player) {
        return altHeld.getOrDefault(player, false);
    }

    public static void clear(UUID player) {
        ctrlHeld.remove(player);
        altHeld.remove(player);
    }
}
