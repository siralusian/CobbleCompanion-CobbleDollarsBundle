package com.cobblecompanion.data;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress;
import com.cobblemon.mod.common.api.pokedex.PokedexManager;
import com.cobblemon.mod.common.api.pokedex.SpeciesDexRecord;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.evolution.Evolution;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ServerDataHelper {

    public static class NeedsResult {
        public List<String> onlinePlayersNeeding = new ArrayList<>();
        public List<String> recentOfflinePlayersNeeding = new ArrayList<>();
    }

    public static NeedsResult whoNeedsByName(MinecraftServer server, String speciesName) {
    NeedsResult result = new NeedsResult();
    List<String> onlineNames = new ArrayList<>();

    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
        onlineNames.add(player.getName().getString());
        boolean hasIt = PlayerDataHelper.getAllPokemon(player).stream()
            .anyMatch(p -> p.getSpecies().getName().equalsIgnoreCase(speciesName));
        if (!hasIt) result.onlinePlayersNeeding.add(player.getName().getString());
    }

    Map<String, Long> recent = PlayerActivityTracker.getAllRecentPlayers(48);
    for (String name : recent.keySet()) {
        if (onlineNames.contains(name)) continue;

        UUID uuid = resolveUuid(server, name);
        if (uuid == null) continue;

        List<Pokemon> allPokemon = PlayerDataHelper.getAllPokemonByUuid(uuid, server.registryAccess());
        if (allPokemon == null) continue;

        boolean hasIt = allPokemon.stream()
            .anyMatch(p -> p.getSpecies().getName().equalsIgnoreCase(speciesName));
        if (!hasIt) result.recentOfflinePlayersNeeding.add(name);
    }

    return result;
}

    private static UUID resolveUuid(MinecraftServer server, String playerName) {
    if (server.getProfileCache() == null) return null;
    var profile = server.getProfileCache().get(playerName).orElse(null);
    return profile != null ? profile.getId() : null;
}

    // ===== Duplikate (Who-Needs-Tab) =====

    public static class DuplicateEntry {
        public final ResourceLocation speciesId;
        public final int level;
        public final Set<String> aspects;

        public DuplicateEntry(ResourceLocation speciesId, int level, Set<String> aspects) {
            this.speciesId = speciesId;
            this.level = level;
            this.aspects = aspects;
        }
    }

    public static Species findSpeciesByName(String name) {
        for (Species s : PokemonSpecies.INSTANCE.getSpecies()) {
            if (s.getName().equalsIgnoreCase(name)) return s;
        }
        return null;
    }

    /**
     * Sammelt rekursiv alle Spezies, die aus der Entwicklungslinie von `species` erreichbar
     * sind (mehrstufig und verzweigt), mit Besuchsschutz gegen Zyklen. Namen lowercase.
     */
    private static Set<String> collectDownstreamSpecies(Species species, Set<String> visited) {
        Set<String> result = new HashSet<>();
        if (species == null) return result;
        for (Evolution evo : species.getEvolutions()) {
            String resultName = evo.getResult().getSpecies();
            if (resultName == null) continue;
            String key = resultName.toLowerCase();
            if (!visited.add(key)) continue;
            result.add(key);
            result.addAll(collectDownstreamSpecies(findSpeciesByName(resultName), visited));
        }
        return result;
    }

    /**
     * Liefert die einzelnen Pokemon, die der Spieler über seinen Bedarf hinaus besitzt
     * ("Reserve") und daher abgeben kann. Pokédex-Modus: Reserve = 1 (Entwicklungslinie egal).
     * Living-Dex-Modus: Reserve = 1 + eine pro noch nicht besessener Folge-Entwicklung in der
     * kompletten (rekursiven) Entwicklungslinie. Innerhalb jeder Spezies werden immer die
     * Level-schwächsten Exemplare als abgebbar vorgeschlagen (das stärkste behält der Spieler).
     */
    public static List<DuplicateEntry> getMyDuplicateSpecies(ServerPlayer player, boolean livingDexMode) {
        Map<String, List<Pokemon>> bySpecies = new LinkedHashMap<>();
        for (Pokemon p : PlayerDataHelper.getAllPokemon(player)) {
            bySpecies.computeIfAbsent(p.getSpecies().getName(), k -> new ArrayList<>()).add(p);
        }

        Set<String> ownedLower = new HashSet<>();
        for (String name : bySpecies.keySet()) ownedLower.add(name.toLowerCase());

        List<DuplicateEntry> result = new ArrayList<>();
        for (Map.Entry<String, List<Pokemon>> entry : bySpecies.entrySet()) {
            String name = entry.getKey();
            List<Pokemon> owned = entry.getValue();
            int reserve = 1;

            if (livingDexMode) {
                Species species = findSpeciesByName(name);
                if (species != null) {
                    for (String downstream : collectDownstreamSpecies(species, new HashSet<>())) {
                        if (!ownedLower.contains(downstream)) reserve++;
                    }
                }
            }

            int giveable = owned.size() - reserve;
            if (giveable <= 0) continue;

            // Schwächste zuerst abgeben, das/die stärkste(n) behält der Spieler (reserve).
            owned.sort(Comparator.comparingInt(Pokemon::getLevel));
            for (int i = 0; i < giveable; i++) {
                Pokemon p = owned.get(i);
                result.add(new DuplicateEntry(p.getSpecies().getResourceIdentifier(), p.getLevel(), p.getAspects()));
            }
        }

        return result;
    }

    // ===== Detaillierte Who-Needs-Abfrage (Pokédex- vs. Living-Dex-Bedarf) =====

    public static class PlayerNeedEntry {
        public final String name;
        public final UUID uuid;
        public final boolean online;
        /** true = hat die Spezies noch nie gefangen (braucht sie für den Pokédex). */
        public final boolean needsPokedex;
        /** true = ist mit dem anfragenden Spieler befreundet. */
        public final boolean isFriend;

        public PlayerNeedEntry(String name, UUID uuid, boolean online, boolean needsPokedex, boolean isFriend) {
            this.name = name;
            this.uuid = uuid;
            this.online = online;
            this.needsPokedex = needsPokedex;
            this.isFriend = isFriend;
        }
    }

    /**
     * Wie whoNeedsByName, aber mit Unterscheidung "braucht für Pokédex" (nie gefangen) vs.
     * "braucht nur für Living Dex" (gefangen, aber aktuell keins besessen). Spieler, die die
     * Spezies aktuell besitzen, werden ausgelassen. KEIN livingDexMode-Parameter mehr (siehe
     * Nutzer-Klarstellung): der Pokédex/Living-Dex-Umschalter im Who-Needs-Tab steuert nur die
     * EIGENEN Duplikat-Vorschläge (MyDuplicatesRequestPacket), nicht diese Spielerliste - jeder
     * Spieler, der die Art nicht besitzt, wird hier immer gelistet, mit seinem tatsächlichen
     * Status (needsPokedex). onlyFriends filtert auf die Freunde des Anfragenden, friendsFirst
     * sortiert Freunde nach vorn (innerhalb der Gruppen bleibt online-vor-offline erhalten).
     */
    public static List<PlayerNeedEntry> whoNeedsDetailed(MinecraftServer server, UUID requester, String speciesName,
                                                          boolean onlyFriends, boolean friendsFirst) {
        List<PlayerNeedEntry> result = new ArrayList<>();
        Species species = findSpeciesByName(speciesName);
        ResourceLocation speciesId = species != null ? species.getResourceIdentifier() : null;
        Set<String> onlineNames = new HashSet<>();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            onlineNames.add(player.getName().getString());
            if (player.getUUID().equals(requester)) continue; // sich selbst nicht listen
            addNeedEntry(result, requester, player.getUUID(), player.getName().getString(), true,
                PlayerDataHelper.getAllPokemon(player), speciesName, speciesId, onlyFriends);
        }

        Map<String, Long> recent = PlayerActivityTracker.getAllRecentPlayers(48);
        for (String name : recent.keySet()) {
            if (onlineNames.contains(name)) continue;

            UUID uuid = resolveUuid(server, name);
            if (uuid == null || uuid.equals(requester)) continue;

            List<Pokemon> allPokemon = PlayerDataHelper.getAllPokemonByUuid(uuid, server.registryAccess());
            if (allPokemon == null) continue;

            addNeedEntry(result, requester, uuid, name, false, allPokemon, speciesName, speciesId, onlyFriends);
        }

        if (friendsFirst) {
            // Stabile Sortierung: Freunde vor Nicht-Freunden, Rest-Reihenfolge bleibt.
            result.sort(Comparator.comparing(e -> !e.isFriend));
        }

        return result;
    }

    private static void addNeedEntry(List<PlayerNeedEntry> result, UUID requester, UUID uuid, String name, boolean online,
                                      List<Pokemon> ownedPokemon, String speciesName, ResourceLocation speciesId,
                                      boolean onlyFriends) {
        boolean isFriend = FriendsManager.areFriends(requester, uuid);
        if (onlyFriends && !isFriend) return;

        boolean currentlyOwns = ownedPokemon.stream()
            .anyMatch(p -> p.getSpecies().getName().equalsIgnoreCase(speciesName));
        if (currentlyOwns) return;

        boolean hasCaught = false;
        if (speciesId != null) {
            try {
                PokedexManager pokedex = Cobblemon.INSTANCE.getPlayerDataManager().getPokedexData(uuid);
                hasCaught = pokedex != null && hasCaughtSpecies(pokedex, speciesId);
            } catch (Exception ignored) {
                // Pokedex-Daten nicht ladbar -> konservativ als "nie gefangen" behandeln
            }
        }

        // KORREKTUR (per Nutzer-Klarstellung): der Pokédex-/Living-Dex-Umschalter im Who-Needs-Tab
        // steuert NUR, welche EIGENEN Duplikate zum Abgeben vorgeschlagen werden (siehe
        // MyDuplicatesRequestPacket) - er hat KEINEN Einfluss darauf, ob/wie ein anderer Spieler
        // hier gelistet wird. Jeder Spieler, der die Art nicht besitzt, erscheint immer, mit dem
        // Label, das seinem tatsächlichen Status entspricht (needsPokedex = nie gefangen).
        boolean needsPokedex = !hasCaught;
        result.add(new PlayerNeedEntry(name, uuid, online, needsPokedex, isFriend));
    }

    /**
     * Wie pokedex.getKnowledgeForSpecies(speciesId) == CAUGHT, aber mit Fallback über die
     * tatsächlich gespeicherten Records (Pfad-Vergleich statt exakter ResourceLocation-Gleichheit)
     * - derselbe Id-Mismatch zwischen der "implemented species"-Liste und Cobblemons intern
     * gespeicherten Pokédex-Keys, der schon bei getPlayerDexCounts() zu falschen Zählern führte
     * (siehe countSeenCaughtFromRecords), betraf hier bisher unbemerkt die Who-Needs-Abfrage:
     * hasCaught blieb wegen des Mismatches fälschlich immer false, wodurch Spieler nie als
     * "braucht nur für Living Dex" erkannt wurden, sondern immer als "braucht für Pokédex".
     */
    public static boolean hasCaughtSpecies(PokedexManager pokedex, ResourceLocation speciesId) {
        if (pokedex.getKnowledgeForSpecies(speciesId) == PokedexEntryProgress.CAUGHT) return true;
        try {
            for (Map.Entry<ResourceLocation, SpeciesDexRecord> entry : pokedex.getSpeciesRecords().entrySet()) {
                if (entry.getKey().getPath().equalsIgnoreCase(speciesId.getPath())
                        && entry.getValue().getKnowledge() == PokedexEntryProgress.CAUGHT) {
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    // ===== Pokédex-Zähler für den Friends-Tab =====

    public static class DexCounts {
        public final int seen;
        public final int caught;
        public final int living;

        public DexCounts(int seen, int caught, int living) {
            this.seen = seen;
            this.caught = caught;
            this.living = living;
        }
    }

    /**
     * Zählt für einen Spieler (auch offline, per UUID): gesehene und gefangene Spezies laut
     * Pokédex sowie die aktuell im Besitz befindlichen einzigartigen Spezies (Living Dex).
     * Bewusst nur bei Sync-Anlässen aufrufen.
     */
    public static DexCounts getPlayerDexCounts(MinecraftServer server, UUID uuid) {
        int seen = 0;
        int caught = 0;
        try {
            PokedexManager pokedex = Cobblemon.INSTANCE.getPlayerDataManager().getPokedexData(uuid);
            if (pokedex != null) {
                int[] fromRecords = countSeenCaughtFromRecords(pokedex);
                if (fromRecords != null) {
                    seen = fromRecords[0];
                    caught = fromRecords[1];
                } else {
                    // Fallback: über ALLE Spezies gehen (auch nicht als "implemented" markierte,
                    // siehe getSpecies()-Umstellung unten) und per Id nachschlagen.
                    for (Species s : PokemonSpecies.INSTANCE.getSpecies()) {
                        PokedexEntryProgress progress = pokedex.getKnowledgeForSpecies(s.getResourceIdentifier());
                        if (progress == PokedexEntryProgress.CAUGHT) {
                            caught++;
                            seen++;
                        } else if (progress != PokedexEntryProgress.NONE) {
                            seen++;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Pokédex nicht ladbar -> 0/0, Living-Zähler unten trotzdem versuchen
        }

        int living = 0;
        List<Pokemon> owned = PlayerDataHelper.getAllPokemonByUuid(uuid, server.registryAccess());
        if (owned != null) {
            Set<String> unique = new HashSet<>();
            for (Pokemon p : owned) unique.add(p.getSpecies().getName().toLowerCase());
            living = unique.size();
        }
        return new DexCounts(seen, caught, living);
    }

    /**
     * Zählt Seen/Caught direkt über PokedexManager.getSpeciesRecords() (die tatsächlich
     * gespeicherten Einträge des Spielers) statt über alle Spezies + Id-Lookup zu iterieren -
     * vermeidet Zähl-Abweichungen durch Id-Mismatches zwischen der "alle Spezies"-Liste und den
     * intern gespeicherten Pokédex-Keys. Per Reflection, weil die exakte
     * Rückgabeform (Collection vs. Map) unsicher ist; gibt null zurück, wenn die API so nicht
     * nutzbar ist (dann greift der Fallback in getPlayerDexCounts).
     */
    private static int[] countSeenCaughtFromRecords(PokedexManager pokedex) {
        try {
            Object records = pokedex.getClass().getMethod("getSpeciesRecords").invoke(pokedex);
            java.util.Collection<?> recordCollection;
            if (records instanceof java.util.Map<?, ?> map) recordCollection = map.values();
            else if (records instanceof java.util.Collection<?> col) recordCollection = col;
            else return null;

            int seen = 0;
            int caught = 0;
            for (Object rec : recordCollection) {
                if (rec == null) continue;
                Object knowledge = rec.getClass().getMethod("getKnowledge").invoke(rec);
                if (knowledge == null) continue;
                String name = knowledge.toString();
                if ("CAUGHT".equals(name)) {
                    caught++;
                    seen++;
                } else if (!"NONE".equals(name)) {
                    seen++;
                }
            }
            return new int[]{seen, caught};
        } catch (Exception e) {
            return null;
        }
    }
}