package com.cobblecompanion.client.data;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Hält die zuletzt vom Server empfangenen ToDo-Einträge (Evolutions-Slot-Layout) für den ToDo-Tab. */
public class ClientTodoHelper {

    public static class TodoEntry {
        public final UUID pokemonUuid;
        public final ResourceLocation fromSpeciesId;
        public final int fromLevel;
        public final Set<String> fromAspects;
        public final ResourceLocation toSpeciesId;
        public final Set<String> toAspects;
        public final boolean canEvolveNow;
        public final ResourceLocation itemId;
        /** Ziel-Spezies noch nie gefangen (für Pokédex gebraucht). */
        public final boolean needsPokedex;
        /** Ziel-Spezies aktuell nicht im Besitz (für Living Dex gebraucht). */
        public final boolean needsLivingDex;
        /** true = Pokemon ist in der Party, false = im PC. */
        public final boolean isParty;
        /** PC-Box-Nummer (nur relevant wenn !isParty, sonst -1). */
        public final int pcBox;
        /** Vom Spieler vergebener Box-Name (nur relevant wenn !isParty, sonst leer). */
        public final String pcBoxName;
        /** Spitzname des Ausgangs-Pokemon (fromSpeciesId), leer wenn keiner vergeben. */
        public final String nickname;
        /** Item-ID des Balls, mit dem das Ausgangs-Pokemon gefangen wurde, oder null. */
        public final ResourceLocation caughtBallId;

        public TodoEntry(UUID pokemonUuid, ResourceLocation fromSpeciesId, int fromLevel, Set<String> fromAspects,
                          ResourceLocation toSpeciesId, Set<String> toAspects, boolean canEvolveNow, ResourceLocation itemId,
                          boolean needsPokedex, boolean needsLivingDex, boolean isParty, int pcBox, String pcBoxName,
                          String nickname, ResourceLocation caughtBallId) {
            this.pokemonUuid = pokemonUuid;
            this.fromSpeciesId = fromSpeciesId;
            this.fromLevel = fromLevel;
            this.fromAspects = fromAspects;
            this.toSpeciesId = toSpeciesId;
            this.toAspects = toAspects;
            this.canEvolveNow = canEvolveNow;
            this.itemId = itemId;
            this.needsPokedex = needsPokedex;
            this.needsLivingDex = needsLivingDex;
            this.isParty = isParty;
            this.pcBox = pcBox;
            this.pcBoxName = pcBoxName;
            this.nickname = nickname;
            this.caughtBallId = caughtBallId;
        }
    }

    private static List<TodoEntry> entries = new ArrayList<>();

    public static void setEntries(List<String> raw) {
        List<TodoEntry> parsed = new ArrayList<>();
        for (String line : raw) {
            String[] parts = line.split("\\|", -1);
            if (parts.length != 15) continue;
            try {
                UUID pokemonUuid = UUID.fromString(parts[0]);
                ResourceLocation fromId = ResourceLocation.parse(parts[1]);
                int level = Integer.parseInt(parts[2]);
                Set<String> aspects = parts[3].isEmpty() ? Collections.emptySet() : Set.copyOf(Arrays.asList(parts[3].split(",")));
                ResourceLocation toId = ResourceLocation.parse(parts[4]);
                Set<String> toAspects = parts[5].isEmpty() ? Collections.emptySet() : Set.copyOf(Arrays.asList(parts[5].split(",")));
                boolean canEvolveNow = Boolean.parseBoolean(parts[6]);
                ResourceLocation itemId = parts[7].isEmpty() ? null : ResourceLocation.parse(parts[7]);
                boolean needsPokedex = Boolean.parseBoolean(parts[8]);
                boolean needsLivingDex = Boolean.parseBoolean(parts[9]);
                boolean isParty = Boolean.parseBoolean(parts[10]);
                int pcBox = Integer.parseInt(parts[11]);
                String pcBoxName = parts[12];
                String nickname = parts[13];
                ResourceLocation caughtBallId = parts[14].isEmpty() ? null : ResourceLocation.parse(parts[14]);
                parsed.add(new TodoEntry(pokemonUuid, fromId, level, aspects, toId, toAspects, canEvolveNow, itemId,
                    needsPokedex, needsLivingDex, isParty, pcBox, pcBoxName, nickname, caughtBallId));
            } catch (Exception ignored) {
                // Eintrag defekt -> überspringen
            }
        }
        entries = parsed;
    }

    public static List<TodoEntry> getEntries() {
        return entries;
    }
}
