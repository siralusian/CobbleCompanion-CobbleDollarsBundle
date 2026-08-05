package com.cobblecompanion.cobbledollarscreate.client.data;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Reiner Datenhalter für die zuletzt vom Server empfangenen Positionen der Zähler/Abzieher-Gruppe,
 * auf die das aktuell gehaltene Schlauer-Beobachter-Item abgestimmt ist (siehe
 * ContentObserverGroupHighlightSyncPacket) - vom Renderer (ContentObserverGroupHighlightRenderer)
 * getrennt gehalten, gleiches Muster wie ClientContentObserverHelper.
 */
public final class ClientContentObserverHighlightHelper {

    private static List<BlockPos> positions = new ArrayList<>();

    private ClientContentObserverHighlightHelper() {}

    public static void setPositions(List<BlockPos> newPositions) {
        positions = newPositions != null ? newPositions : new ArrayList<>();
    }

    public static List<BlockPos> getPositions() {
        return positions;
    }
}
