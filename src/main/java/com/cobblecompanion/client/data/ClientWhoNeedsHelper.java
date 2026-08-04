package com.cobblecompanion.client.data;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Hält Duplikat-Liste, Spieler-Bedarfsliste und Modus/Query-Zustand für den Who-Needs-Tab. */
public class ClientWhoNeedsHelper {

    /** Ein einzelnes abgebbares Pokemon (nicht mehr aggregiert - jedes Exemplar für sich, mit eigenem Level). */
    public static class DuplicateItem {
        public final ResourceLocation speciesId;
        public final int level;
        public final Set<String> aspects;

        public DuplicateItem(ResourceLocation speciesId, int level, Set<String> aspects) {
            this.speciesId = speciesId;
            this.level = level;
            this.aspects = aspects;
        }
    }

    public static class PlayerNeedItem {
        public final String name;
        public final UUID uuid;
        public final boolean online;
        public final boolean needsPokedex;
        public final boolean isFriend;

        public PlayerNeedItem(String name, UUID uuid, boolean online, boolean needsPokedex, boolean isFriend) {
            this.name = name;
            this.uuid = uuid;
            this.online = online;
            this.needsPokedex = needsPokedex;
            this.isFriend = isFriend;
        }
    }

    private static List<DuplicateItem> duplicates = new ArrayList<>();
    private static List<PlayerNeedItem> playerNeeds = new ArrayList<>();
    private static boolean livingDexMode = false;
    private static String lastQuery = "";

    public static void setDuplicates(List<String> entries) {
        List<DuplicateItem> parsed = new ArrayList<>();
        for (String e : entries) {
            String[] parts = e.split("\\|", -1);
            if (parts.length != 3) continue;
            try {
                ResourceLocation speciesId = ResourceLocation.parse(parts[0]);
                int level = Integer.parseInt(parts[1]);
                Set<String> aspects = parts[2].isEmpty() ? Collections.emptySet() : Set.copyOf(Arrays.asList(parts[2].split(",")));
                parsed.add(new DuplicateItem(speciesId, level, aspects));
            } catch (Exception ignored) {
                // Eintrag defekt -> überspringen
            }
        }
        duplicates = parsed;
    }

    public static void setPlayerNeeds(List<String> entries) {
        List<PlayerNeedItem> parsed = new ArrayList<>();
        for (String e : entries) {
            String[] parts = e.split("\\|", 5);
            if (parts.length != 5) continue;
            try {
                parsed.add(new PlayerNeedItem(
                    parts[0], UUID.fromString(parts[1]),
                    Boolean.parseBoolean(parts[2]), Boolean.parseBoolean(parts[3]),
                    Boolean.parseBoolean(parts[4])));
            } catch (Exception ignored) {
                // Eintrag defekt -> überspringen
            }
        }
        playerNeeds = parsed;
    }

    public static List<DuplicateItem> getDuplicates() {
        return duplicates;
    }

    public static List<PlayerNeedItem> getPlayerNeeds() {
        return playerNeeds;
    }

    public static void setLivingDexMode(boolean mode) {
        livingDexMode = mode;
    }

    public static boolean isLivingDexMode() {
        return livingDexMode;
    }

    public static void setLastQuery(String query) {
        lastQuery = query != null ? query : "";
    }

    public static String getLastQuery() {
        return lastQuery;
    }
}
