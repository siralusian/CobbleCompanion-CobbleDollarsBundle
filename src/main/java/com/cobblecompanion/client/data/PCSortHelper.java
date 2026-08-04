package com.cobblecompanion.client.data;

import com.cobblecompanion.data.TodoHelper;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.storage.ClientBox;
import com.cobblemon.mod.common.client.storage.ClientPC;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * PC-Sortierhilfe (Punkt 4 der Professor-Tab-Roadmap): berechnet, in welche Box ein gerade
 * gehaltenes Pokemon gehört (siehe PCSortHelperOverlay, das die eigentliche Infobox über
 * Cobblemons echtem PC-GUI zeichnet) und benennt Boxen automatisch. Rein client-lokal - nutzt
 * NUR die eigenen, echten (nicht getauschten) Cobblemon-Daten des lokalen Spielers, deshalb kein
 * Server-Roundtrip nötig.
 *
 * WICHTIG (per Live-Test korrigiert): Cobblemons eigene Box-Nummern (RequestRenamePCBoxPacket,
 * PCBox.getBoxNumber()) sind INTERN 0-indexiert - "Box 1" in Cobblemons eigenem UI entspricht
 * intern 0. Alle Methoden hier arbeiten mit der ANGEZEIGTEN (1-indexierten) Nummer und rechnen
 * erst beim Senden des Rename-Pakets auf 0-indexiert um.
 */
public class PCSortHelper {

    /** Fallback-Obergrenze für die Startbox-Einstellung, solange die eigene PC-Boxenzahl noch nicht bekannt ist (PC noch nicht geöffnet). */
    private static final int FALLBACK_MAX_BOXES = 50;

    /** Höchste bekannte Box-Nummer (echte PC-Boxenzahl, falls schon geladen, sonst ein großzügiger Fallback). */
    public static int getKnownMaxBox() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return FALLBACK_MAX_BOXES;
            ClientPC pc = CobblemonClient.INSTANCE.getStorage().getPcStores().get(mc.player.getUUID());
            if (pc != null) return pc.getBoxes().size();
        } catch (Exception ignored) {}
        return FALLBACK_MAX_BOXES;
    }

    /**
     * Ergebnis von computeTarget(): entweder eine Zielbox+Zeile+Slot, oder "duplicate" (Ziel-Box
     * bereits belegt - keine Box vorschlagen), oder (nur Living-Dex-Modus) "needsEvolution" -
     * die Zielbox+Zeile+Slot beziehen sich dann auf die NOCH NICHT besessene Entwicklung.
     */
    public static final class SortTarget {
        public final int box;   // 1-indexiert, nur gültig wenn !duplicate
        public final int row;   // 1-5, nur gültig wenn !duplicate
        public final int slot;  // 1-6, nur gültig wenn !duplicate
        public final boolean duplicate;
        public final String evolvesTo; // Anzeigename der Zielentwicklung, nur gesetzt wenn needsEvolution

        private SortTarget(int box, int row, int slot, boolean duplicate, String evolvesTo) {
            this.box = box;
            this.row = row;
            this.slot = slot;
            this.duplicate = duplicate;
            this.evolvesTo = evolvesTo;
        }

        static SortTarget of(int box, int row, int slot) {
            return new SortTarget(box, row, slot, false, null);
        }

        static SortTarget needsEvolution(int box, int row, int slot, String evolvesTo) {
            return new SortTarget(box, row, slot, false, evolvesTo);
        }

        static SortTarget duplicate() {
            return new SortTarget(-1, -1, -1, true, null);
        }
    }

    /**
     * Zielbox/-zeile/-slot für ein Pokemon (1-indexiert wie in Cobblemons eigenem UI). null, wenn
     * die Sortierhilfe deaktiviert ist oder die Boxenzahl zu klein für eine sinnvolle Berechnung ist.
     * "Doppelt" (SortTarget.duplicate) bedeutet: die für die Dex-Nummer zuständige Zielbox enthält
     * BEREITS eine andere Kopie dieser Art UND (im Living-Dex-Modus) entweder hat die Art keine
     * Entwicklung mehr, oder der Spieler besitzt diese Entwicklung bereits irgendwo - dann wird
     * bewusst KEINE Box vorgeschlagen (der Spieler legt Duplikate selbst irgendwo ab, siehe
     * Nutzer-Feedback: bei vielen Duplikaten reicht eine einzelne "Doppelte"-Box ohnehin nicht).
     * Andernfalls (Living-Dex-Modus, Duplikat gefunden, Entwicklung existiert und wird noch nicht
     * besessen) wird stattdessen die Zielposition der ENTWICKLUNG vorgeschlagen (needsEvolution) -
     * das doppelte Exemplar soll dann entwickelt werden, statt als Duplikat betrachtet zu werden.
     */
    /**
     * Reine Dex-Box-Zuordnung OHNE Duplikat-/Entwicklungs-Sonderfälle - für Massenprüfungen über
     * viele Pokemon gleichzeitig (z.B. PCSortHelperOverlay's Fehlplatzierungs-Markierungen für
     * alle Pokemon der gerade offenen Box), wo computeTarget() pro Slot zu teuer/riskant wäre.
     * -1, falls keine sinnvolle Dex-Nummer vorhanden ist ODER (Pokédex-Modus) die Familien-Anker-
     * Antwort vom Server noch aussteht (Pending-Cache, siehe familyAnchorDexNumber()) - der
     * Aufrufer überspringt dieses Pokemon dann einfach für diesen Frame, der Cache füllt sich
     * binnen weniger Frames von selbst.
     */
    public static int computeDexBoxOnly(Pokemon pokemon, int totalBoxes) {
        if (totalBoxes < 2) return -1;
        int startBox = Math.min(ClientSettingsHelper.getPcSortStartBox(), totalBoxes);
        if (ClientSettingsHelper.isPcSortModeLivingDexPlus()) {
            if (!LivingDexPlusHelper.isCatalogReady()) {
                LivingDexPlusHelper.ensureRequested();
                return -1;
            }
            int index = ldpIndexForPokemon(pokemon);
            if (index < 0) return -1;
            int boxOffset = index / 30;
            return Math.max(startBox, Math.min(startBox + boxOffset, Math.max(startBox, totalBoxes - 1)));
        }
        int offset;
        if (ClientSettingsHelper.isPcSortModePokedex()) {
            Integer anchor = familyAnchorDexNumber(pokemon.getSpecies().getName());
            if (anchor == null) return -1;
            if (!LivingDexPlusHelper.isCatalogReady()) {
                LivingDexPlusHelper.ensureRequested();
                return -1;
            }
            int pos = pokedexFamilyPosition(anchor);
            if (pos < 0) return -1;
            offset = pos / 30;
        } else {
            int dexNumber = pokemon.getSpecies().getNationalPokedexNumber();
            if (dexNumber <= 0) return -1;
            offset = (dexNumber - 1) / 30;
        }
        int rawBox = startBox + offset;
        return Math.max(startBox, Math.min(rawBox, Math.max(startBox, totalBoxes - 1)));
    }

    /**
     * Wie computeDexBoxOnly(), liefert aber zusätzlich Zeile+Slot (für die Rahmen-Farbcode-Logik
     * im Overlay, siehe PCSortHelperOverlay) - {box, row, slot} (1-indexiert) oder null, falls
     * (noch) nicht berechenbar (Katalog/Familien-Anker-Antwort steht aus).
     */
    public static int[] computeDexPosition(Pokemon pokemon, int totalBoxes) {
        if (totalBoxes < 2) return null;
        int startBox = Math.min(ClientSettingsHelper.getPcSortStartBox(), totalBoxes);
        int posIndex;
        if (ClientSettingsHelper.isPcSortModeLivingDexPlus()) {
            if (!LivingDexPlusHelper.isCatalogReady()) {
                LivingDexPlusHelper.ensureRequested();
                return null;
            }
            int index = ldpIndexForPokemon(pokemon);
            if (index < 0) return null;
            posIndex = index;
        } else if (ClientSettingsHelper.isPcSortModePokedex()) {
            Integer anchor = familyAnchorDexNumber(pokemon.getSpecies().getName());
            if (anchor == null) return null;
            if (!LivingDexPlusHelper.isCatalogReady()) {
                LivingDexPlusHelper.ensureRequested();
                return null;
            }
            int pos = pokedexFamilyPosition(anchor);
            if (pos < 0) return null;
            posIndex = pos;
        } else {
            int dexNumber = pokemon.getSpecies().getNationalPokedexNumber();
            if (dexNumber <= 0) return null;
            posIndex = dexNumber - 1;
        }
        int offset = posIndex / 30;
        int rawBox = startBox + offset;
        int box = Math.max(startBox, Math.min(rawBox, Math.max(startBox, totalBoxes - 1)));
        int posInBox = posIndex % 30;
        int row = posInBox / 6 + 1;
        int slot = posInBox % 6 + 1;
        return new int[]{box, row, slot};
    }

    /** true, wenn der angegebene Ziel-Slot (1-indexierte Box, Zeile 1-5, Slot 1-6) aktuell frei ist. */
    public static boolean isTargetSlotFree(int box, int row, int slot) {
        int posInBox = (row - 1) * 6 + (slot - 1);
        return !isSlotOccupied(box, posInBox);
    }

    /** true, ob "box" (1-indexiert) eine echte "Sortier-Box" ist (nicht "Neu" und nicht die reservierte "Doppelte"-Box am Ende). */
    public static boolean isSortingBox(int box, int totalBoxes) {
        int startBox = Math.min(ClientSettingsHelper.getPcSortStartBox(), totalBoxes);
        if (box == totalBoxes) return false; // "Doppelte"
        if (startBox > 1 && box == 1) return false; // "Neu"
        return box >= startBox;
    }

    public static SortTarget computeTarget(Pokemon pokemon, int totalBoxes) {
        if (!ClientSettingsHelper.isPcSortHelperEnabled()) return null;
        if (totalBoxes < 2) return null; // braucht mind. 1 Dex-Box + die reservierte "Doppelte"-Box

        if (ClientSettingsHelper.isPcSortModeLivingDexPlus()) {
            return computeLivingDexPlusTarget(pokemon, totalBoxes);
        }

        int startBox = Math.min(ClientSettingsHelper.getPcSortStartBox(), totalBoxes);
        boolean pokedexMode = ClientSettingsHelper.isPcSortModePokedex();

        // BUGFIX (Nutzer-Report: Pokédex-Modus zeigt bei praktisch jedem Pokemon "wird nicht
        // gebraucht"): posIndex war hier bisher der ROHE Familien-Anker-Dex-Wert selbst
        // ((dexNumber-1)/30) - Familien-Anker sind aber LÜCKENHAFT (die meisten Dex-Nummern
        // gehören zu Nicht-Wurzel-Familienmitgliedern, die wegfallen), während die Auto-
        // Beschriftung (AutoNameBoxesPacket.legacyBoxName()) die POSITION in der dicht sortierten
        // Liste aller Familien-Anker verwendet - beide Systeme liefen dadurch systematisch
        // auseinander. Jetzt: dieselbe dichte Positions-Berechnung wie die Auto-Beschriftung
        // (siehe pokedexFamilyPosition(), basiert auf demselben LDP-Katalog, Kategorie 0).
        int posIndex;
        Integer anchor = null; // nur im Pokédex-Modus gesetzt, wird unten für isFamilySlotOccupied() gebraucht
        if (pokedexMode) {
            // Pokédex-Modus: eine ganze Entwicklungsfamilie belegt EINEN Slot (Ausnahme:
            // Verzweigungen wie Evoli bekommen einen Slot PRO Zweig) - siehe EvolutionFamilyHelper.
            // Muss serverseitig berechnet werden (Species.getEvolutions() ist clientseitig auf
            // einem echten Server unzuverlässig), deshalb derselbe Pending-Cache-Roundtrip wie bei
            // der Entwicklungsketten-Berechnung für den Living-Dex-Modus.
            anchor = familyAnchorDexNumber(pokemon.getSpecies().getName());
            if (anchor == null) return null; // Antwort noch unterwegs
            if (!LivingDexPlusHelper.isCatalogReady()) {
                LivingDexPlusHelper.ensureRequested();
                return null; // Katalog (für die dichte Positions-Liste) noch unterwegs
            }
            int pos = pokedexFamilyPosition(anchor);
            if (pos < 0) return null;
            posIndex = pos;
        } else {
            int dexNumber = pokemon.getSpecies().getNationalPokedexNumber();
            if (dexNumber <= 0) return null;
            posIndex = dexNumber - 1;
        }
        int offset = posIndex / 30;
        int rawBox = startBox + offset;
        // Letzte Box bleibt für "Doppelte" reserviert (siehe autoNameBoxes()) - Dex-Boxen gehen nie darüber hinaus.
        int dexBox = Math.max(startBox, Math.min(rawBox, Math.max(startBox, totalBoxes - 1)));
        int posInBox = posIndex % 30;
        int row = posInBox / 6 + 1;
        int slot = posInBox % 6 + 1;

        Boolean occupiedBoxed = pokedexMode
            // BUGFIX (Nutzer-Report, Singleplayer-Test: Bisasam-Familie zeigt "wird nicht mehr
            // gebraucht", obwohl auf dem berechneten Zielslot fälschlich Nidoqueen/-ran/-rino
            // liegen - eine KOMPLETT ANDERE Familie): "belegt" hieß hier bisher schlicht "liegt
            // IRGENDEIN Pokemon dort" - in einer frisch per /giveallpokemon befüllten, noch nicht
            // sortierten Box liegt dort aber oft eine völlig fremde Art, kein echtes Duplikat.
            // Jetzt: nur als "belegt" (= echtes Duplikat) werten, wenn der Slot-Insasse SELBST zur
            // GLEICHEN Familie gehört (gleicher Anker) - sonst gilt der Slot als frei und wird
            // trotzdem vorgeschlagen (der Insasse wird beim Ablegen automatisch verdrängt/getauscht).
            ? isFamilySlotOccupied(dexBox, posInBox, anchor)
            : hasOtherCopyInBox(pokemon, dexBox);

        if (occupiedBoxed == null) return null; // fremder Familien-Anker noch unterwegs, diesen Frame überspringen
        if (occupiedBoxed) {
            if (!pokedexMode) {
                SortTarget evoTarget = computeEvolutionTarget(pokemon, totalBoxes, startBox);
                if (evoTarget != null) return evoTarget;
            }
            return SortTarget.duplicate();
        }

        return SortTarget.of(dexBox, row, slot);
    }

    // ===== Pokédex-Modus: dichte Familien-Positionsliste =====
    // Nutzt denselben LDP-Katalog (Kategorie 0 = BASE_POKEDEX, Familien-Anker) wie Living Dex+ -
    // wird auch im reinen Pokédex-Modus angefordert, siehe computeDexBoxOnly()/computeTarget().
    private static List<LivingDexPlusHelper.Entry> pokedexFamilyListCache = null;
    private static int pokedexFamilyListCacheCatalogSize = -1;

    private static List<LivingDexPlusHelper.Entry> pokedexFamilyList() {
        int catalogSize = LivingDexPlusHelper.getCatalog().size();
        if (pokedexFamilyListCache != null && pokedexFamilyListCacheCatalogSize == catalogSize) return pokedexFamilyListCache;
        List<LivingDexPlusHelper.Entry> list = LivingDexPlusHelper.byCategory(0);
        list.sort(java.util.Comparator.comparingInt(LivingDexPlusHelper.Entry::dexNumber));
        pokedexFamilyListCache = list;
        pokedexFamilyListCacheCatalogSize = catalogSize;
        return list;
    }

    /** Position (0-basiert) der Familie mit diesem Anker-Wert in der dicht sortierten Liste, oder -1. */
    private static int pokedexFamilyPosition(int anchorDexNumber) {
        List<LivingDexPlusHelper.Entry> list = pokedexFamilyList();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).dexNumber() == anchorDexNumber) return i;
        }
        return -1;
    }

    // ===== Living Dex+ =====
    // Flache, geordnete Liste über alle AKTIVIERTEN Kategorien (siehe LivingDexPlusHelper/
    // ClientSettingsHelper.getLivingDexPlusCategoryOrder) - Box/Zeile/Slot folgen rein aus dem
    // Index in dieser Liste (30 Slots/Box), NICHT mehr aus einer Dex-Nummern-Formel (Formen haben
    // keine eigene Pokédex-Nummer). Wird gecacht und nur neu gebaut, wenn sich Katalog oder
    // Kategorie-Reihenfolge seit dem letzten Aufruf geändert haben - sonst müsste bei jedem
    // gerenderten Frame (während ein Pokemon gehalten wird) die komplette Liste (mehrere tausend
    // Einträge) neu sortiert werden.
    private static List<LivingDexPlusHelper.Entry> ldpFlatListCache = null;
    private static String ldpFlatListCacheSignature = null;

    private static final int LDP_BOX_SIZE = 30;

    /**
     * Nutzer-Vorgabe: 2 verschiedene Kategorien teilen sich nie eine Box, auch wenn das Slots
     * verschwendet - jede Kategorie wird mit null-Platzhaltern auf ein Vielfaches von
     * LDP_BOX_SIZE aufgefüllt (gleicher Mechanismus wie LivingDexPlusLayoutHelper serverseitig,
     * MUSS für identische Box-Zuordnung exakt gleich bleiben). null-Einträge matchen nie ein
     * Pokemon (siehe ldpIndexForPokemon()).
     */
    private static List<LivingDexPlusHelper.Entry> ldpFlatList() {
        List<Integer> order = ClientSettingsHelper.getLivingDexPlusCategoryOrder();
        String signature = order.toString() + "#" + LivingDexPlusHelper.getCatalog().size();
        if (ldpFlatListCache != null && signature.equals(ldpFlatListCacheSignature)) return ldpFlatListCache;

        List<LivingDexPlusHelper.Entry> flat = new java.util.ArrayList<>();
        java.util.Set<Integer> consumedCosmeticIds = new java.util.HashSet<>();
        for (int id : order) {
            // Umbau "Regionalformen-Unterkategorien von den Oberkategorien trennen": jede
            // synthetische Pro-Region-ID (siehe LivingDexPlusRegistry.regionSyntheticId()) wird
            // hier direkt zu (echte Kategorie 3/4, Region-Filter) aufgelöst - kein Gruppen-Marker/
            // Sonderfall mehr, jede Region ist genauso ein eigenständiges Listenmitglied wie jede
            // Basis-Kategorie.
            if (com.cobblecompanion.data.LivingDexPlusRegistry.isAnyRegionSyntheticId(id)) {
                int realCatId = com.cobblecompanion.data.LivingDexPlusRegistry.realCategoryIdFromSyntheticId(id);
                String region = com.cobblecompanion.data.LivingDexPlusRegistry.regionNameFromSyntheticId(id);
                List<LivingDexPlusHelper.Entry> regionEntries = new java.util.ArrayList<>();
                for (LivingDexPlusHelper.Entry e : LivingDexPlusHelper.byCategory(realCatId)) {
                    if (region.equalsIgnoreCase(e.formName())) regionEntries.add(e);
                }
                regionEntries.sort(java.util.Comparator.comparingInt(LivingDexPlusHelper.Entry::dexNumber));
                appendPaddedLdp(flat, regionEntries);
                continue;
            }
            // Umbau "Kosmetische Formen: Unterkategorien" (analog zu Regionen, aber Artenliste
            // dynamisch aus dem Katalog statt einer festen Konstante - siehe
            // LivingDexPlusRegistry.cosmeticSpeciesNames()). Nutzer-Vorgabe: Karpador+Garados
            // teilen sich - falls beide aktiv - eine gemeinsame Box-Sequenz statt zwei getrennter
            // (siehe LivingDexPlusRegistry.resolveCosmeticBoxGroup()).
            if (com.cobblecompanion.data.LivingDexPlusRegistry.isAnyCosmeticSyntheticId(id)) {
                if (consumedCosmeticIds.contains(id)) continue; // schon Teil einer vorherigen Gruppe
                int realCatId = com.cobblecompanion.data.LivingDexPlusRegistry.realCategoryIdFromCosmeticSyntheticId(id);
                List<String> cosmeticSpecies = LivingDexPlusHelper.cosmeticSpeciesNames();
                var group = com.cobblecompanion.data.LivingDexPlusRegistry.resolveCosmeticBoxGroup(id, order, cosmeticSpecies);
                consumedCosmeticIds.addAll(group.consumedSyntheticIds());
                if (group.speciesNames().isEmpty()) continue; // Katalog hat sich seit dem Speichern geändert
                List<LivingDexPlusHelper.Entry> speciesEntries = new java.util.ArrayList<>();
                for (LivingDexPlusHelper.Entry e : LivingDexPlusHelper.byCategory(realCatId)) {
                    for (String species : group.speciesNames()) {
                        if (species.equalsIgnoreCase(e.speciesName())) { speciesEntries.add(e); break; }
                    }
                }
                speciesEntries.sort(java.util.Comparator.comparingInt(LivingDexPlusHelper.Entry::dexNumber)
                    .thenComparing(LivingDexPlusHelper.Entry::formName));
                appendPaddedLdp(flat, speciesEntries);
                continue;
            }
            List<LivingDexPlusHelper.Entry> catEntries = LivingDexPlusHelper.byCategory(id);
            if (id == 5 || id == 6) {
                // Basis-Kategorie "Kosmetische Formen" deckt bewusst NICHT die Arten aus der
                // Unterkategorien-Whitelist ab (siehe LivingDexPlusRegistry.
                // COSMETIC_SUBCATEGORY_WHITELIST) - die haben ihre eigene Box/Unterkategorie und
                // würden sonst doppelt gezählt.
                catEntries.removeIf(e -> com.cobblecompanion.data.LivingDexPlusRegistry.COSMETIC_SUBCATEGORY_WHITELIST.contains(e.speciesName().toLowerCase()));
            }
            catEntries.sort(java.util.Comparator.comparingInt(LivingDexPlusHelper.Entry::dexNumber)
                .thenComparing(LivingDexPlusHelper.Entry::formName));
            appendPaddedLdp(flat, catEntries);
        }
        ldpFlatListCache = flat;
        ldpFlatListCacheSignature = signature;
        return flat;
    }

    private static void appendPaddedLdp(List<LivingDexPlusHelper.Entry> flat, List<LivingDexPlusHelper.Entry> entries) {
        if (entries.isEmpty()) return;
        flat.addAll(entries);
        int remainder = flat.size() % LDP_BOX_SIZE;
        if (remainder != 0) {
            for (int i = 0; i < LDP_BOX_SIZE - remainder; i++) flat.add(null);
        }
    }

    /**
     * Index (0-basiert) des Katalog-Eintrags, dem dieses Pokemon in der Flach-Liste entspricht,
     * oder -1, wenn es zu KEINER aktivierten Kategorie gehört (nicht getrackt) bzw. -2, solange
     * für den Pokédex-Familien-Anker (Kategorie BASE_POKEDEX) die Server-Antwort noch aussteht.
     */
    private static int ldpIndexForPokemon(Pokemon pokemon) {
        List<LivingDexPlusHelper.Entry> flat = ldpFlatList();
        String speciesName = pokemon.getSpecies().getName();
        // Berücksichtigt sowohl klassische Formen (Pokemon.getForm(), inkl. dem BUGFIX dass
        // Pokemon.getForm() die Standardform generisch "Normal" statt nach der Art nennt) als
        // auch das Species-Feature-System (Pokemon.getAspects(), z.B. Karpador-Jump-Farben,
        // Vivillon-Muster) - siehe LivingDexPlusRegistry.effectiveFormChoice()/FEATURE_SPECIES.
        String formChoice = com.cobblecompanion.data.LivingDexPlusRegistry.effectiveFormChoice(pokemon);
        boolean standardForm = formChoice == null;
        String formName = standardForm ? speciesName : formChoice;
        boolean shiny = pokemon.getShiny();

        // BUGFIX (Nutzer-Report: "kein einziger farbiger Rahmen" trotz falsch abgelegter Pokemon,
        // sobald Shiny Pokédex mit ausgewählt ist): der Familien-Anker wurde bisher VORAB für JEDES
        // Pokemon angefragt, sobald Kategorie 0 ODER 7 irgendwo aktiv war - blockierte (return -2)
        // damit auch ganz normale, nicht-shiny Pokemon, die den Anker (Kategorie 7 = NUR shiny)
        // gar nicht brauchen. Jetzt: der Anker wird NUR dann (und nur einmal, lokal gecacht) geholt,
        // wenn tatsächlich ein passender Kategorie-0/7-Eintrag für den jeweiligen Shiny-Status
        // dieses Pokemon durchlaufen wird.
        Integer familyAnchor = null;
        boolean familyAnchorFetched = false;

        for (int i = 0; i < flat.size(); i++) {
            LivingDexPlusHelper.Entry e = flat.get(i);
            if (e == null) continue; // Box-Trennungs-Padding zwischen Kategorien
            switch (e.categoryId()) {
                case 0 -> { // BASE_POKEDEX (Familien-Repräsentant, nicht-shiny)
                    if (shiny) break;
                    if (!familyAnchorFetched) { familyAnchor = familyAnchorDexNumber(speciesName); familyAnchorFetched = true; }
                    if (familyAnchor == null) return -2; // Server-Antwort steht noch aus
                    if (e.dexNumber() == familyAnchor) return i;
                }
                case 1 -> { // BASE_LIVING_DEX
                    if (standardForm && !shiny && e.speciesName().equalsIgnoreCase(speciesName)) return i;
                }
                case 2 -> { // Shiny Living Dex
                    if (standardForm && shiny && e.speciesName().equalsIgnoreCase(speciesName)) return i;
                }
                case 3 -> { // REGIONAL_FORMS
                    if (!standardForm && !shiny && e.speciesName().equalsIgnoreCase(speciesName) && e.formName().equalsIgnoreCase(formName)) return i;
                }
                case 4 -> { // REGIONAL_FORMS_SHINY
                    if (!standardForm && shiny && e.speciesName().equalsIgnoreCase(speciesName) && e.formName().equalsIgnoreCase(formName)) return i;
                }
                case 5 -> { // COSMETIC_FORMS
                    if (!standardForm && !shiny && e.speciesName().equalsIgnoreCase(speciesName) && e.formName().equalsIgnoreCase(formName)) return i;
                }
                case 6 -> { // COSMETIC_FORMS_SHINY
                    if (!standardForm && shiny && e.speciesName().equalsIgnoreCase(speciesName) && e.formName().equalsIgnoreCase(formName)) return i;
                }
                case 7 -> { // Shiny Pokédex (Familien-Repräsentant, shiny)
                    if (!shiny) break;
                    if (!familyAnchorFetched) { familyAnchor = familyAnchorDexNumber(speciesName); familyAnchorFetched = true; }
                    if (familyAnchor == null) return -2; // Server-Antwort steht noch aus
                    if (e.dexNumber() == familyAnchor) return i;
                }
                default -> {}
            }
        }
        return -1; // keine passende Kategorie aktiv -> nicht getrackt
    }

    private static SortTarget computeLivingDexPlusTarget(Pokemon pokemon, int totalBoxes) {
        if (!LivingDexPlusHelper.isCatalogReady()) {
            LivingDexPlusHelper.ensureRequested();
            return null; // Katalog noch unterwegs
        }
        int startBox = Math.min(ClientSettingsHelper.getPcSortStartBox(), totalBoxes);
        int index = ldpIndexForPokemon(pokemon);
        if (index < 0) return null; // nicht getrackt, oder Familien-Anker-Antwort steht noch aus

        int boxOffset = index / 30;
        int posInBox = index % 30;
        int box = Math.max(startBox, Math.min(startBox + boxOffset, Math.max(startBox, totalBoxes - 1)));
        int row = posInBox / 6 + 1;
        int slot = posInBox % 6 + 1;

        if (isSlotOccupied(box, posInBox)) {
            SortTarget evoTarget = computeLivingDexPlusEvolutionTarget(pokemon, startBox, totalBoxes);
            if (evoTarget != null) return evoTarget;
            return SortTarget.duplicate();
        }
        return SortTarget.of(box, row, slot);
    }

    /**
     * Living-Dex+-Gegenstück zu computeEvolutionTarget() (Living-Dex-Modus): läuft bei einem
     * Duplikat die komplette Entwicklungskette weiter (alle Zweige, geschlechts-gefiltert wie im
     * normalen Living-Dex-Modus) und schlägt die erste Stufe vor, deren Ziel-Index in der
     * Living-Dex+-Flach-Liste noch frei ist. Form/Shiny des gehaltenen Pokemon werden dabei als
     * unverändert angenommen (Entwicklung ändert normalerweise weder Form noch Shiny-Status).
     * Die Kategorie BASE_POKEDEX wird hier bewusst übersprungen - dort teilen sich alle Stufen
     * derselben Familie ohnehin denselben Slot (Familien-Anker), Weiterentwickeln ändert daran nichts.
     */
    private static SortTarget computeLivingDexPlusEvolutionTarget(Pokemon pokemon, int startBox, int totalBoxes) {
        try {
            String startName = pokemon.getSpecies().getName().toLowerCase();
            List<String> chain = evolutionChainCache.get(startName);
            if (chain == null) {
                if (pendingChainRequests.add(startName)
                        && ClientNetworkUtil.canSendToServer(com.cobblecompanion.network.EvolutionChainRequestPacket.TYPE.id())) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        new com.cobblecompanion.network.EvolutionChainRequestPacket(startName));
                }
                return null;
            }

            com.cobblemon.mod.common.pokemon.Gender ownGender = pokemon.getGender();
            boolean shiny = pokemon.getShiny();
            // Siehe ldpIndexForPokemon() - berücksichtigt Formen UND das Species-Feature-System.
            String ownFormChoice = com.cobblecompanion.data.LivingDexPlusRegistry.effectiveFormChoice(pokemon);
            boolean standardForm = ownFormChoice == null;
            String ownFormName = standardForm ? pokemon.getSpecies().getName() : ownFormChoice;
            List<LivingDexPlusHelper.Entry> flat = ldpFlatList();

            for (String entry : chain) {
                int sep = entry.lastIndexOf(':');
                String stageName = sep >= 0 ? entry.substring(0, sep) : entry;
                String genderStr = sep >= 0 ? entry.substring(sep + 1) : "";
                if (!genderStr.isEmpty()) {
                    try {
                        com.cobblemon.mod.common.pokemon.Gender requiredGender =
                            com.cobblemon.mod.common.pokemon.Gender.valueOf(genderStr);
                        if (ownGender != null && ownGender != requiredGender) continue;
                    } catch (IllegalArgumentException ignored) {}
                }

                for (int i = 0; i < flat.size(); i++) {
                    LivingDexPlusHelper.Entry e = flat.get(i);
                    if (e == null) continue; // Box-Trennungs-Padding zwischen Kategorien
                    boolean match = switch (e.categoryId()) {
                        case 1 -> standardForm && !shiny && e.speciesName().equalsIgnoreCase(stageName);
                        case 2 -> standardForm && shiny && e.speciesName().equalsIgnoreCase(stageName);
                        case 3 -> !standardForm && !shiny && e.speciesName().equalsIgnoreCase(stageName) && e.formName().equalsIgnoreCase(ownFormName);
                        case 4 -> !standardForm && shiny && e.speciesName().equalsIgnoreCase(stageName) && e.formName().equalsIgnoreCase(ownFormName);
                        case 5 -> !standardForm && !shiny && e.speciesName().equalsIgnoreCase(stageName) && e.formName().equalsIgnoreCase(ownFormName);
                        case 6 -> !standardForm && shiny && e.speciesName().equalsIgnoreCase(stageName) && e.formName().equalsIgnoreCase(ownFormName);
                        default -> false; // Kategorie 0 (BASE_POKEDEX) bewusst ausgenommen, siehe Doc-Kommentar
                    };
                    if (!match) continue;

                    int boxOffset = i / 30;
                    int posInBox = i % 30;
                    int evoBox = Math.max(startBox, Math.min(startBox + boxOffset, Math.max(startBox, totalBoxes - 1)));
                    if (!isSlotOccupied(evoBox, posInBox)) {
                        ResourceLocation toId = TodoHelper.resolveSpeciesId(stageName);
                        Species evoSpecies = toId != null ? resolveSpeciesRobust(toId) : null;
                        String evoDisplayName = evoSpecies != null ? evoSpecies.getTranslatedName().getString() : stageName;
                        int row = posInBox / 6 + 1;
                        int slot = posInBox % 6 + 1;
                        return SortTarget.needsEvolution(evoBox, row, slot, evoDisplayName);
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    // ===== Familien-Anker-Cache für den Pokédex-Sortiermodus (siehe FamilySlotRequestPacket) =====
    private static final Map<String, Integer> familyAnchorCache = new HashMap<>();
    private static final Set<String> pendingFamilyAnchorRequests = new HashSet<>();

    /** Von FamilySlotResponsePacket aufgerufen. anchorDexNumber -1 bedeutet "nicht auflösbar". */
    public static void setFamilyAnchor(String speciesName, int anchorDexNumber) {
        familyAnchorCache.put(speciesName.toLowerCase(), anchorDexNumber);
        pendingFamilyAnchorRequests.remove(speciesName.toLowerCase());
    }

    /** null, solange die Server-Antwort noch aussteht. */
    private static Integer familyAnchorDexNumber(String speciesName) {
        String key = speciesName.toLowerCase();
        Integer cached = familyAnchorCache.get(key);
        if (cached != null) return cached;
        if (pendingFamilyAnchorRequests.add(key)
                && ClientNetworkUtil.canSendToServer(com.cobblecompanion.network.FamilySlotRequestPacket.TYPE.id())) {
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new com.cobblecompanion.network.FamilySlotRequestPacket(key));
        }
        return null;
    }

    // ===== Entwicklungsketten-Cache (siehe EvolutionChainRequestPacket) =====
    // WICHTIG: Species.getEvolutions() liefert auf einem ECHTEN Server client-seitig eine LEERE
    // Menge, selbst für Arten mit echten Entwicklungen (per Live-Test-Chat-Debug bestätigt: "Evolutions-
    // Anzahl=0" für Schiggy) - Cobblemons Client-Sync der Spezies-Registry enthält die
    // Entwicklungsdaten offenbar nicht (Entwicklung läuft rein serverseitig ab). Im Singleplayer
    // (Integrated Server, gleicher Prozess wie der Client) fiel das nie auf, weil dort direkt auf
    // den vollständigen Server-Objektgraphen zugegriffen wird. Deshalb jetzt zwingend ein
    // Server-Roundtrip - Ergebnis wird pro Startart gecacht (rein statische Daten, ändert sich nie
    // innerhalb einer Sitzung), damit nicht bei jedem Frame erneut angefragt wird.
    private static final Map<String, List<String>> evolutionChainCache = new HashMap<>();
    private static final Set<String> pendingChainRequests = new HashSet<>();

    /** Von EvolutionChainResponsePacket aufgerufen. */
    public static void setEvolutionChain(String speciesName, List<String> chain) {
        evolutionChainCache.put(speciesName.toLowerCase(), chain);
        pendingChainRequests.remove(speciesName.toLowerCase());
    }

    /**
     * BUGFIX (Nutzer-Report: Vorentwicklungen wie Mampfaxo werden trotzdem als "falsch abgelegt"
     * rot markiert): PCSortHelperOverlay.renderMisplacedMarkers() nutzte bisher direkt
     * species.getEvolutions().isEmpty() zur "hat diese Art noch eine Entwicklung vor sich"-Prüfung
     * - aber genau wie bei computeEvolutionTarget() oben ist Species.getEvolutions() auf einem
     * echten Dedicated Server CLIENT-seitig unzuverlässig (liefert immer eine leere Menge), die
     * Vorentwicklungs-Ausnahme griff dadurch faktisch nie. Nutzt jetzt denselben Server-Roundtrip-
     * Cache wie computeEvolutionTarget() - null, solange die Antwort noch aussteht (Aufrufer
     * überspringt das Pokemon dann für diesen Frame, der Cache füllt sich von selbst).
     */
    public static Boolean hasKnownEvolutions(String speciesName) {
        String key = speciesName.toLowerCase();
        List<String> chain = evolutionChainCache.get(key);
        if (chain == null) {
            if (pendingChainRequests.add(key)
                    && ClientNetworkUtil.canSendToServer(com.cobblecompanion.network.EvolutionChainRequestPacket.TYPE.id())) {
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                    new com.cobblecompanion.network.EvolutionChainRequestPacket(key));
            }
            return null;
        }
        return !chain.isEmpty();
    }

    /**
     * Für ein bereits als "Doppelt" erkanntes Pokemon (Living-Dex-Modus): läuft die komplette
     * Entwicklungskette weiter (nicht nur eine Stufe!) und schlägt die erste Stufe vor, deren
     * Ziel-Slot im PC noch WIRKLICH FREI ist - "frei" heißt hier: kein Pokemon liegt dort, EGAL
     * welcher Art (nicht "besitzt der Spieler diese Art schon irgendwo", das war ein früherer Bug:
     * ein noch nicht entwickeltes Duplikat, das rein zufällig schon auf dem Zielslot der NÄCHSTEN
     * Stufe liegt, zählte vorher nicht als "besetzt", weil dort ja (noch) die falsche Art lag).
     * Die Kette selbst kommt aus evolutionChainCache (siehe oben) - liegt sie noch nicht vor, wird
     * sie einmalig beim Server angefragt und in der Zwischenzeit null zurückgegeben (zeigt normal
     * "Doppelt", bis die Antwort da ist - meist binnen weniger Frames).
     */
    private static SortTarget computeEvolutionTarget(Pokemon pokemon, int totalBoxes, int startBox) {
        try {
            String startName = pokemon.getSpecies().getName().toLowerCase();
            List<String> chain = evolutionChainCache.get(startName);
            if (chain == null) {
                if (pendingChainRequests.add(startName)
                        && ClientNetworkUtil.canSendToServer(com.cobblecompanion.network.EvolutionChainRequestPacket.TYPE.id())) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        new com.cobblecompanion.network.EvolutionChainRequestPacket(startName));
                }
                return null;
            }

            com.cobblemon.mod.common.pokemon.Gender ownGender = pokemon.getGender();

            for (String entry : chain) {
                // Wire-Format "speziesName:GENDER" (siehe EvolutionChainResponsePacket) - GENDER
                // leer, falls dieser Zweig an kein bestimmtes Geschlecht gebunden ist.
                int sep = entry.lastIndexOf(':');
                String stageName = sep >= 0 ? entry.substring(0, sep) : entry;
                String genderStr = sep >= 0 ? entry.substring(sep + 1) : "";
                if (!genderStr.isEmpty()) {
                    try {
                        com.cobblemon.mod.common.pokemon.Gender requiredGender =
                            com.cobblemon.mod.common.pokemon.Gender.valueOf(genderStr);
                        // Dieser Zweig verlangt ein bestimmtes Geschlecht (z.B. Kirlia->Gallade nur
                        // männlich) - einem Duplikat des FALSCHEN Geschlechts darf dieser Zweig nie
                        // als Zielslot vorgeschlagen werden, egal ob der Slot frei ist.
                        if (ownGender != null && ownGender != requiredGender) continue;
                    } catch (IllegalArgumentException ignored) {}
                }

                ResourceLocation toId = TodoHelper.resolveSpeciesId(stageName);
                if (toId == null) continue;
                Species evoSpecies = resolveSpeciesRobust(toId);
                if (evoSpecies == null) continue;

                int evoDexNumber = evoSpecies.getNationalPokedexNumber();
                if (evoDexNumber <= 0) continue;
                int offset = (evoDexNumber - 1) / 30;
                int rawBox = startBox + offset;
                int evoBox = Math.max(startBox, Math.min(rawBox, Math.max(startBox, totalBoxes - 1)));
                int posInBox = (evoDexNumber - 1) % 30;
                int row = posInBox / 6 + 1;
                int slot = posInBox % 6 + 1;

                if (!isSlotOccupied(evoBox, posInBox)) {
                    return SortTarget.needsEvolution(evoBox, row, slot, evoSpecies.getTranslatedName().getString());
                }
                // Zielslot schon belegt (z.B. von einem noch unentwickelten Duplikat) -> zur
                // NÄCHSTEN Stufe in der Kette weitergehen.
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Wie isSlotOccupied(), aber Pokédex-modus-spezifisch: "belegt" nur, wenn der Insasse selbst
     * zur GLEICHEN Familie gehört (gleicher Anker-Dex-Wert) - siehe computeTarget()-Bugfix-
     * Kommentar. null = der Familien-Anker des Insassen steht noch aus (Server-Roundtrip-Cache,
     * gleicher Mechanismus wie familyAnchorDexNumber() selbst), Aufrufer überspringt diesen Frame.
     */
    private static Boolean isFamilySlotOccupied(int boxNumber, int posInBox, int expectedAnchor) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return true; // im Zweifel als belegt behandeln
            ClientPC pc = CobblemonClient.INSTANCE.getStorage().getPcStores().get(mc.player.getUUID());
            if (pc == null) return true;
            ClientBox box = pc.getBoxes().get(boxNumber - 1);
            List<Pokemon> slots = box.getSlots();
            if (posInBox < 0 || posInBox >= slots.size()) return true;
            Pokemon occupant = slots.get(posInBox);
            if (occupant == null) return false;
            Integer occupantAnchor = familyAnchorDexNumber(occupant.getSpecies().getName());
            if (occupantAnchor == null) return null; // Antwort noch unterwegs
            return occupantAnchor == expectedAnchor;
        } catch (Exception ignored) {
            return true;
        }
    }

    /** true, wenn im angegebenen Box-Slot (1-indexierte Box, 0-indexierte Position 0-29 innerhalb der Box) bereits IRGENDEIN Pokemon liegt. */
    private static boolean isSlotOccupied(int boxNumber, int posInBox) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return true; // im Zweifel als belegt behandeln
            ClientPC pc = CobblemonClient.INSTANCE.getStorage().getPcStores().get(mc.player.getUUID());
            if (pc == null) return true;
            ClientBox box = pc.getBoxes().get(boxNumber - 1);
            List<Pokemon> slots = box.getSlots();
            if (posInBox < 0 || posInBox >= slots.size()) return true;
            return slots.get(posInBox) != null;
        } catch (Exception ignored) {
            return true;
        }
    }

    /** Wie PokemonSpecies.INSTANCE.getByIdentifier(id), aber mit Pfad-Fallback falls der direkte Lookup nichts liefert. */
    private static Species resolveSpeciesRobust(ResourceLocation id) {
        Species direct = com.cobblemon.mod.common.api.pokemon.PokemonSpecies.INSTANCE.getByIdentifier(id);
        if (direct != null) return direct;
        for (Species s : com.cobblemon.mod.common.api.pokemon.PokemonSpecies.INSTANCE.getSpecies()) {
            if (s.getResourceIdentifier().getPath().equalsIgnoreCase(id.getPath())) return s;
        }
        return null;
    }


    /**
     * true, wenn in der angegebenen Box (1-indexiert) bereits eine ANDERE eigene Kopie dieser Art
     * liegt (das gehaltene Pokemon selbst ausgenommen) - die Zielbox ist für diese Art also schon
     * "erledigt", jede weitere Kopie ist ein Duplikat. Gilt bewusst für beide Sortiermodi gleich
     * (Pokédex wie Living Dex brauchen jeweils nur eine Kopie pro Art in der Zielbox).
     */
    private static boolean hasOtherCopyInBox(Pokemon pokemon, int boxNumber) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return false;
            ClientPC pc = CobblemonClient.INSTANCE.getStorage().getPcStores().get(mc.player.getUUID());
            if (pc == null) return false;
            ResourceLocation speciesId = pokemon.getSpecies().getResourceIdentifier();
            ClientBox box = pc.getBoxes().get(boxNumber - 1);
            for (Pokemon p : box) {
                if (p != null && !p.getUuid().equals(pokemon.getUuid()) && p.getSpecies().getResourceIdentifier().equals(speciesId)) {
                    return true;
                }
            }
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Benennt alle Boxen nach dem Schema aus der Roadmap: erste Box vor der Startbox = "Neu"
     * (nur wenn Startbox > 1 - bei Startbox 1 gibt es keine separate "Neu"-Box), ab der Startbox
     * fortlaufend "#1-30"/"#31-60"/..., letzte Box = "Doppelte". Die eigentliche Umbenennung
     * passiert SERVERSEITIG in AutoNameBoxesPacket - Cobblemons eigenes RequestRenamePCBoxPacket
     * (das Cobblemons BoxNameWidget beim Umbenennen per Hand verschickt) funktioniert hier NICHT:
     * dessen Handler holt die PCStore über PCLinkManager.getPC(player), was ohne aktuell offenes
     * PC-Fenster null liefert - der Button im Settings-Tab wird aber typischerweise ohne offenes
     * PC-Fenster geklickt. AutoNameBoxesPacket nutzt stattdessen dieselbe UUID-basierte
     * Storage-API wie die restlichen AdminOp-Packets, die davon unabhängig funktioniert.
     */
    public static void autoNameBoxes() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        UUID uuid = mc.player.getUUID();
        ClientPC pc = CobblemonClient.INSTANCE.getStorage().getPcStores().get(uuid);
        if (pc == null) {
            mc.player.displayClientMessage(Component.translatable("cobblecompanion.msg.pc_not_loaded"), false);
            return;
        }
        if (!ClientNetworkUtil.canSendToServerOrWarn(com.cobblecompanion.network.AutoNameBoxesPacket.TYPE.id())) return;
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
            new com.cobblecompanion.network.AutoNameBoxesPacket(ClientSettingsHelper.getPcSortStartBox(),
                ClientSettingsHelper.getPcSortMode(), ClientSettingsHelper.getLivingDexPlusCategoryOrder()));
    }
}
