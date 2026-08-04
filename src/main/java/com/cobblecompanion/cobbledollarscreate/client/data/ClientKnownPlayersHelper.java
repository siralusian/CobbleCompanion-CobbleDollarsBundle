package com.cobblecompanion.cobbledollarscreate.client.data;

import com.cobblecompanion.cobbledollarscreate.network.KnownPlayersSyncPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * Reiner Datenhalter für die zuletzt vom Server empfangene Liste aller bekannten Spieler (siehe
 * KnownPlayersSyncPacket) - Grundlage für den durchsuchbaren Spieler-Picker im
 * Verkaufserlöse-Empfänger-UI.
 */
public class ClientKnownPlayersHelper {

    private static List<KnownPlayersSyncPacket.Entry> players = new ArrayList<>();

    public static void set(List<KnownPlayersSyncPacket.Entry> newPlayers) {
        players = newPlayers;
    }

    public static List<KnownPlayersSyncPacket.Entry> getPlayers() {
        return players;
    }
}
