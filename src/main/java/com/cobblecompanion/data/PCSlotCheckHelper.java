package com.cobblecompanion.data;

import com.cobblemon.mod.common.api.storage.pc.PCBox;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.server.level.ServerPlayer;

/**
 * Serverseitiges Gegenstück zur clientseitigen PC-Sortierhilfe (PCSortHelper) - zählt, wie viele
 * PC-Pokemon eines Spielers gerade NICHT in ihrer zuständigen Box liegen (Settings "Slot Prüfung",
 * Home-Tab-Zähler). Bewusst eine vereinfachte Neuberechnung, keine 1:1-Kopie von
 * PCSortHelper.computeTarget() (keine Duplikat-/Entwicklungs-Sonderfälle, die für einen reinen
 * "liegt es in der richtigen Box"-Zähler nicht gebraucht werden).
 *
 * Seit dem Pokédex-Familien-Fix hängt die Box-Zuordnung SEHR WOHL vom Sortiermodus ab (früherer
 * Kommentar hier war falsch/veraltet): im Pokédex-Modus nutzt EvolutionFamilyHelper einen
 * Familien-Anker statt der eigenen Pokédex-Nummer der Art - läuft serverseitig ohne Roundtrip,
 * da hier (anders als PCSortHelper) direkt auf Species.getEvolutions()/getPreEvolution() zugegriffen
 * werden darf.
 */
public class PCSlotCheckHelper {

    /**
     * Zählt PC-Pokemon (Team zählt nicht mit) außerhalb ihrer Dex-Box. Ausnahmen: die letzte Box
     * gilt als reservierte "Doppelte"-Box (siehe PCSortHelper.autoNameBoxes()) und wird komplett
     * übersprungen; Vorentwicklungen (Arten, die noch mindestens eine Entwicklungsmöglichkeit vor
     * sich haben) zählen laut Nutzer-Vorgabe nicht mit, da ihre Platzierung oft bewusst vorläufig ist.
     */
    public static int countMisplaced(ServerPlayer player, int startBox, int pcSortMode, java.util.List<Integer> ldpCategories) {
        PCStore pcStore = PlayerDataHelper.getPCStoreByUuid(player.getUUID(), player.server.registryAccess());
        if (pcStore == null) return 0;

        int totalBoxes = pcStore.getBoxes().size();
        if (totalBoxes < 2) return 0;

        boolean livingDexPlus = pcSortMode == 2;
        boolean pokedexMode = pcSortMode == 0;
        java.util.List<LivingDexPlusRegistry.Entry> ldpFlat = livingDexPlus
            ? LivingDexPlusLayoutHelper.buildFlatList(ldpCategories) : null;
        // BUGFIX (wie PCSortHelper.computeTarget()): Familien-Anker-Werte sind lückenhaft - die
        // Auto-Beschriftung nutzt die POSITION in der dicht sortierten Familien-Liste, nicht den
        // rohen Dex-Wert. Ohne diesen Fix driftete der Home-Zähler systematisch von der
        // tatsächlichen Auto-Beschriftung ab.
        java.util.List<LivingDexPlusRegistry.Entry> pokedexFlat = pokedexMode
            ? LivingDexPlusLayoutHelper.buildFlatList(java.util.List.of(0)) : null;

        int misplaced = 0;
        for (PCBox box : pcStore.getBoxes()) {
            int actualBox = box.getBoxNumber() + 1; // intern 0-indexiert, wie überall sonst in dieser Codebase
            if (actualBox == totalBoxes) continue; // reservierte "Doppelte"-Box

            for (Pokemon p : box.getNonEmptySlots().values()) {
                Species species = p.getSpecies();
                if (!species.getEvolutions().isEmpty()) continue; // Vorentwicklung -> nicht mitzählen

                int targetBox;
                if (livingDexPlus) {
                    int index = LivingDexPlusLayoutHelper.indexForPokemon(p, ldpFlat, ldpCategories);
                    if (index < 0) continue; // zu keiner aktivierten Kategorie gehörig -> nicht getrackt, zählt nicht als falsch abgelegt
                    int offset = index / 30;
                    targetBox = Math.max(startBox, Math.min(startBox + offset, Math.max(startBox, totalBoxes - 1)));
                } else if (pokedexMode) {
                    int anchor = EvolutionFamilyHelper.effectiveAnchorDexNumber(species);
                    int pos = -1;
                    for (int i = 0; i < pokedexFlat.size(); i++) {
                        if (pokedexFlat.get(i).dexNumber() == anchor) { pos = i; break; }
                    }
                    if (pos < 0) continue;
                    int offset = pos / 30;
                    targetBox = Math.max(startBox, Math.min(startBox + offset, Math.max(startBox, totalBoxes - 1)));
                } else {
                    int dexNumber = species.getNationalPokedexNumber();
                    if (dexNumber <= 0) continue;
                    int offset = (dexNumber - 1) / 30;
                    int rawBox = startBox + offset;
                    targetBox = Math.max(startBox, Math.min(rawBox, Math.max(startBox, totalBoxes - 1)));
                }

                if (targetBox != actualBox) misplaced++;
            }
        }
        return misplaced;
    }
}
