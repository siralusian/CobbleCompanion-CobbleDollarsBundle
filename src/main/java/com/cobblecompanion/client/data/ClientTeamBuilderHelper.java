package com.cobblecompanion.client.data;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Hält den zuletzt vom Server berechneten Team-Builder-Vorschlag (Team-Builder-Tab, rechte Hälfte). */
public class ClientTeamBuilderHelper {

    public static final class Entry {
        public final ResourceLocation speciesId;
        public final String aspects;
        public final int level;
        /** Strukturierte Reason-Codes (z.B. "OFF:fire"/"RES:water") - clientseitig übersetzt, wie beim Types-Tab. */
        public final List<String> reasons;
        public Entry(ResourceLocation speciesId, String aspects, int level, List<String> reasons) {
            this.speciesId = speciesId;
            this.aspects = aspects;
            this.level = level;
            this.reasons = reasons;
        }
    }

    private static List<Entry> result = new ArrayList<>();
    private static List<Entry> alternates = new ArrayList<>();
    private static int version = 0;
    private static boolean hasResult = false;

    public static void setResult(List<String> lines) {
        List<Entry> primaryParsed = new ArrayList<>();
        List<Entry> alternateParsed = new ArrayList<>();
        for (String line : lines) {
            String[] parts = line.split("\\|", -1);
            if (parts.length != 5) continue;
            try {
                List<String> reasons = parts[4].isBlank() ? List.of() : List.of(parts[4].split(","));
                Entry entry = new Entry(ResourceLocation.parse(parts[0]), parts[1], Integer.parseInt(parts[2]), reasons);
                if ("A".equals(parts[3])) alternateParsed.add(entry); else primaryParsed.add(entry);
            } catch (Exception ignored) {}
        }
        result = primaryParsed;
        alternates = alternateParsed;
        hasResult = true;
        version++;
    }

    public static List<Entry> getResult() { return result; }
    public static List<Entry> getAlternates() { return alternates; }
    public static boolean hasResult() { return hasResult; }
    public static int getVersion() { return version; }
}
