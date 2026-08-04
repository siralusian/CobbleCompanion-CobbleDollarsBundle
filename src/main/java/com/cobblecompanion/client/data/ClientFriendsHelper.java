package com.cobblecompanion.client.data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Client-seitiger Datenspeicher für den Friends-Tab. Wird per FriendsSyncPacket vom Server
 * befüllt (Freundesliste + offene Anfragen). Reine Datenhaltung - das Senden von Aktionen
 * (Anfrage/Annehmen/Entfernen) läuft über FriendActionPacket in CompanionScreen bzw.
 * FriendsSyncPacket.handle (Auto-Accept).
 */
public class ClientFriendsHelper {

    public static class FriendItem {
        public final String name;
        public final UUID uuid;
        public final boolean online;
        public final int seenCount;
        public final int caughtCount;
        public final int livingDexCount;
        /** Hat dieser Freund "Freunde dürfen zu mir teleportieren" aktiviert (steuert den Teleport-Button). */
        public final boolean allowsTeleport;

        public FriendItem(String name, UUID uuid, boolean online, int seenCount, int caughtCount, int livingDexCount, boolean allowsTeleport) {
            this.name = name;
            this.uuid = uuid;
            this.online = online;
            this.seenCount = seenCount;
            this.caughtCount = caughtCount;
            this.livingDexCount = livingDexCount;
            this.allowsTeleport = allowsTeleport;
        }
    }

    public static class RequestItem {
        public final String name;
        public final UUID uuid;

        public RequestItem(String name, UUID uuid) {
            this.name = name;
            this.uuid = uuid;
        }
    }

    private static List<FriendItem> friends = new ArrayList<>();
    private static List<RequestItem> requests = new ArrayList<>();
    private static UUID selectedFriend = null;

    /** Übernimmt die vom Server gesendeten Roh-Zeilen (Freunde: 6 Felder, Anfragen: 2 Felder). */
    public static void applySync(List<String> friendLines, List<String> requestLines) {
        List<FriendItem> parsedFriends = new ArrayList<>();
        for (String line : friendLines) {
            String[] p = line.split("\\|", 7);
            if (p.length != 7) continue;
            try {
                parsedFriends.add(new FriendItem(
                    p[0], UUID.fromString(p[1]), Boolean.parseBoolean(p[2]),
                    Integer.parseInt(p[3]), Integer.parseInt(p[4]), Integer.parseInt(p[5]),
                    Boolean.parseBoolean(p[6])));
            } catch (Exception ignored) {}
        }
        friends = parsedFriends;

        List<RequestItem> parsedRequests = new ArrayList<>();
        for (String line : requestLines) {
            String[] p = line.split("\\|", 2);
            if (p.length != 2) continue;
            try {
                parsedRequests.add(new RequestItem(p[0], UUID.fromString(p[1])));
            } catch (Exception ignored) {}
        }
        requests = parsedRequests;

        // Auswahl aufräumen, falls der ausgewählte Freund nicht mehr existiert.
        if (selectedFriend != null && getSelected() == null) selectedFriend = null;
    }

    public static List<FriendItem> getFriends() {
        return friends;
    }

    public static List<RequestItem> getRequests() {
        return requests;
    }

    public static void setSelectedFriend(UUID uuid) {
        selectedFriend = uuid;
    }

    public static FriendItem getSelected() {
        if (selectedFriend == null) return null;
        for (FriendItem f : friends) {
            if (f.uuid.equals(selectedFriend)) return f;
        }
        return null;
    }
}
