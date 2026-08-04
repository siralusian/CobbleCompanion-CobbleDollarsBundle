package com.cobblecompanion.data;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.storage.pc.PCBox;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PlayerDataHelper {

    public static List<Pokemon> getParty(ServerPlayer player) {
        List<Pokemon> result = new ArrayList<>();
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        party.forEach(pokemon -> {
            if (pokemon != null) result.add(pokemon);
        });
        return result;
    }

    public static List<Pokemon> getPC(ServerPlayer player) {
        List<Pokemon> result = new ArrayList<>();
        PCStore pc = Cobblemon.INSTANCE.getStorage().getPC(player);
        pc.forEach(pokemon -> {
            if (pokemon != null) result.add(pokemon);
        });
        return result;
    }

    public static List<Pokemon> getAllPokemon(ServerPlayer player) {
        List<Pokemon> all = new ArrayList<>();
        all.addAll(getParty(player));
        all.addAll(getPC(player));
        return all;
    }

    /**
     * PC-Box-Nummer je Pokemon-UUID (für die Box-Sortierung im ToDo-Tab). Pokemon, die nicht
     * gefunden werden (sollte nicht vorkommen), fehlen einfach in der Map.
     */
    public static Map<UUID, Integer> getPCBoxNumbers(ServerPlayer player) {
        Map<UUID, Integer> result = new HashMap<>();
        PCStore pc = Cobblemon.INSTANCE.getStorage().getPC(player);
        for (PCBox box : pc.getBoxes()) {
            int boxNumber = box.getBoxNumber();
            box.forEach(pokemon -> {
                if (pokemon != null) result.put(pokemon.getUuid(), boxNumber);
            });
        }
        return result;
    }

    /** Vom Spieler vergebene Box-Namen je Box-Nummer (leer/nicht vorhanden = Standardname "Box N"). */
    public static Map<Integer, String> getPCBoxNames(ServerPlayer player) {
        Map<Integer, String> result = new HashMap<>();
        PCStore pc = Cobblemon.INSTANCE.getStorage().getPC(player);
        for (PCBox box : pc.getBoxes()) {
            String name = box.getName();
            if (name != null && !name.isBlank()) result.put(box.getBoxNumber(), name);
        }
        return result;
    }

    public static boolean hasPokemon(ServerPlayer player, int dexNumber) {
        for (Pokemon p : getAllPokemon(player)) {
            if (p.getSpecies().getNationalPokedexNumber() == dexNumber) return true;
        }
        return false;
    }

    public static String getPartySummary(ServerPlayer player) {
        List<Pokemon> party = getParty(player);
        if (party.isEmpty()) return "\u00a77Your party is empty.";
        StringBuilder sb = new StringBuilder();
        for (Pokemon p : party) {
            sb.append("\u00a7e").append(p.getSpecies().getName())
              .append(" \u00a77Lv.").append(p.getLevel());
            if (p.getShiny()) sb.append(" \u00a76\u2605");
            sb.append(" \u00a77[");
            p.getTypes().forEach(type -> sb.append("\u00a7b").append(type.getName()).append(" "));
            sb.append("\u00a77]");
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    // ===== UUID-based methods for offline player lookup =====

    /**
     * Attempts to load a player's PC + Party Pokemon by UUID,
     * even if they are currently offline. Returns null on failure.
     */
    public static List<Pokemon> getAllPokemonByUuid(UUID uuid, net.minecraft.core.RegistryAccess registryAccess) {
    try {
        List<Pokemon> all = new ArrayList<>();
        Object storage = Cobblemon.INSTANCE.getStorage();

        try {
            java.lang.reflect.Method getPartyMethod = storage.getClass()
                .getMethod("getParty", UUID.class, net.minecraft.core.RegistryAccess.class);
            Object party = getPartyMethod.invoke(storage, uuid, registryAccess);
            addAllFromIterable(party, all);
        } catch (Exception e) {
            com.cobblecompanion.CobbleCompanion.LOGGER.error("[CobbleCompanion] getParty(UUID) failed", e);
        }

        try {
            java.lang.reflect.Method getPcMethod = storage.getClass()
                .getMethod("getPC", UUID.class, net.minecraft.core.RegistryAccess.class);
            Object pc = getPcMethod.invoke(storage, uuid, registryAccess);
            addAllFromIterable(pc, all);
        } catch (Exception e) {
            com.cobblecompanion.CobbleCompanion.LOGGER.error("[CobbleCompanion] getPC(UUID) failed", e);
        }

        return all;
    } catch (Exception e) {
        com.cobblecompanion.CobbleCompanion.LOGGER.error("[CobbleCompanion] Error in getAllPokemonByUuid", e);
        return null;
    }
}

    /** Das echte PCStore-Objekt (mit Boxen/Slots/Namen) eines Spielers per UUID, auch offline. */
    public static PCStore getPCStoreByUuid(UUID uuid, net.minecraft.core.RegistryAccess registryAccess) {
        try {
            Object storage = Cobblemon.INSTANCE.getStorage();
            java.lang.reflect.Method getPcMethod = storage.getClass()
                .getMethod("getPC", UUID.class, net.minecraft.core.RegistryAccess.class);
            return (PCStore) getPcMethod.invoke(storage, uuid, registryAccess);
        } catch (Exception e) {
            com.cobblecompanion.CobbleCompanion.LOGGER.error("[CobbleCompanion] getPCStoreByUuid failed", e);
            return null;
        }
    }

    /** Das echte PlayerPartyStore-Objekt eines Spielers per UUID, auch offline (siehe getPCStoreByUuid). */
    public static PlayerPartyStore getPartyStoreByUuid(UUID uuid, net.minecraft.core.RegistryAccess registryAccess) {
        try {
            Object storage = Cobblemon.INSTANCE.getStorage();
            java.lang.reflect.Method getPartyMethod = storage.getClass()
                .getMethod("getParty", UUID.class, net.minecraft.core.RegistryAccess.class);
            return (PlayerPartyStore) getPartyMethod.invoke(storage, uuid, registryAccess);
        } catch (Exception e) {
            com.cobblecompanion.CobbleCompanion.LOGGER.error("[CobbleCompanion] getPartyStoreByUuid failed", e);
            return null;
        }
    }

    /** Sucht ein Pokemon eines Spielers (Team+PC) per UUID, auch offline - für AdminOp-Bearbeitung/Freilassen/Verschenken. */
    public static Pokemon findPokemonByUuid(UUID playerUuid, UUID pokemonUuid, net.minecraft.core.RegistryAccess registryAccess) {
        List<Pokemon> all = getAllPokemonByUuid(playerUuid, registryAccess);
        if (all == null) return null;
        for (Pokemon p : all) {
            if (p.getUuid().equals(pokemonUuid)) return p;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static void addAllFromIterable(Object iterable, List<Pokemon> target) {
        if (iterable instanceof Iterable<?> it) {
            for (Object obj : it) {
                if (obj instanceof Pokemon p) target.add(p);
            }
        }
    }
}