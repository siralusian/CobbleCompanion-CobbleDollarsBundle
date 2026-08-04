package com.cobblecompanion.client.data;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Hält die eigene Party für das Party-Auswahl-Overlay beim Pokemon-Verschenken im Friends-Tab. */
public class ClientGiftHelper {

    public static class PartySlot {
        public final UUID pokemonUuid;
        public final ResourceLocation speciesId;
        public final int level;
        public final Set<String> aspects;
        public final String nickname;

        public PartySlot(UUID pokemonUuid, ResourceLocation speciesId, int level, Set<String> aspects, String nickname) {
            this.pokemonUuid = pokemonUuid;
            this.speciesId = speciesId;
            this.level = level;
            this.aspects = aspects;
            this.nickname = nickname;
        }
    }

    /** Ein offenes Geschenk an mich, für das Annehmen-Overlay im Home-Tab. */
    public static class PendingGift {
        public final UUID fromUuid;
        public final String fromName;
        public final UUID pokemonUuid;
        public final ResourceLocation speciesId;
        public final int level;
        public final Set<String> aspects;
        public final String nickname;
        public final ResourceLocation caughtBallId;
        /** Rohe IDs (z.B. "adamant"/"overgrow") - Client übersetzt selbst via I18n. Leer = unbekannt. */
        public final String natureId;
        public final String abilityId;

        public PendingGift(UUID fromUuid, String fromName, UUID pokemonUuid, ResourceLocation speciesId,
                            int level, Set<String> aspects, String nickname, ResourceLocation caughtBallId,
                            String natureId, String abilityId) {
            this.fromUuid = fromUuid;
            this.fromName = fromName;
            this.pokemonUuid = pokemonUuid;
            this.speciesId = speciesId;
            this.level = level;
            this.aspects = aspects;
            this.nickname = nickname;
            this.caughtBallId = caughtBallId;
            this.natureId = natureId;
            this.abilityId = abilityId;
        }
    }

    private static List<PartySlot> party = new ArrayList<>();
    private static List<PendingGift> pendingForMe = new ArrayList<>();

    public static void setParty(List<String> raw) {
        List<PartySlot> parsed = new ArrayList<>();
        for (String line : raw) {
            String[] parts = line.split("\\|", -1);
            if (parts.length != 5) continue;
            try {
                UUID uuid = UUID.fromString(parts[0]);
                ResourceLocation speciesId = ResourceLocation.parse(parts[1]);
                int level = Integer.parseInt(parts[2]);
                Set<String> aspects = parts[3].isEmpty() ? Collections.emptySet() : Set.copyOf(Arrays.asList(parts[3].split(",")));
                String nickname = parts[4];
                parsed.add(new PartySlot(uuid, speciesId, level, aspects, nickname));
            } catch (Exception ignored) {}
        }
        party = parsed;
    }

    public static List<PartySlot> getParty() {
        return party;
    }

    public static void setPendingForMe(List<String> raw) {
        List<PendingGift> parsed = new ArrayList<>();
        for (String line : raw) {
            String[] parts = line.split("\\|", -1);
            if (parts.length != 10) continue;
            try {
                UUID fromUuid = UUID.fromString(parts[0]);
                String fromName = parts[1];
                UUID pokemonUuid = UUID.fromString(parts[2]);
                ResourceLocation speciesId = ResourceLocation.parse(parts[3]);
                int level = Integer.parseInt(parts[4]);
                Set<String> aspects = parts[5].isEmpty() ? Collections.emptySet() : Set.copyOf(Arrays.asList(parts[5].split(",")));
                String nickname = parts[6];
                ResourceLocation caughtBallId = parts[7].isEmpty() ? null : ResourceLocation.parse(parts[7]);
                String natureId = parts[8];
                String abilityId = parts[9];
                parsed.add(new PendingGift(fromUuid, fromName, pokemonUuid, speciesId, level, aspects, nickname, caughtBallId, natureId, abilityId));
            } catch (Exception ignored) {}
        }
        pendingForMe = parsed;
    }

    public static List<PendingGift> getPendingForMe() {
        return pendingForMe;
    }
}
