package com.cobblecompanion.data;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Serverseitig: einmalig berechneter Katalog ALLER (Kategorie, Art, Form)-Einträge für Living
 * Dex+ (siehe VariantCategory) - unabhängig davon, welche Kategorien ein Spieler aktiviert hat
 * (das entscheidet der Client lokal, siehe LivingDexPlusHelper). Läuft serverseitig, weil sowohl
 * die Formen-Liste als auch generell die Species-Registry client-seitig auf einem echten
 * Dedicated Server unzuverlässig sind (dieselbe Begründung wie bei EvolutionChainRequestPacket).
 *
 * Regional- vs. kosmetische Formen: Cobblemon hat dafür kein eigenes Kategorie-Flag. Bei Prüfung
 * der echten Artendaten (z.B. raichu.json/meowth.json) folgen ALLE Regionalformen im Spiel
 * konsequent der Namenskonvention "Alola"/"Galar"/"Hisui"/"Paldea" (Formname = Regionsname) -
 * das reicht als zuverlässiges Unterscheidungsmerkmal, ohne eine von Hand gepflegte Artenliste zu
 * brauchen. Kampf-/Mega-/Gigadynamax-Formen (aspects enthalten "gmax"/"mega"/"primal"/"totem")
 * werden komplett ausgeschlossen - die sind nicht dauerhaft im PC ablegbar.
 */
public class LivingDexPlusRegistry {

    public record Entry(int categoryId, String speciesName, String formName, int dexNumber) {}

    /**
     * Geordnete Regionsliste (Formname-Schreibweise) für die Regionalformen-Unterkategorien
     * (Task "Region/Form-Unterkategorien") - gemeinsam von Client (Settings-UI/PCSortHelper) und
     * Server (LivingDexPlusLayoutHelper/DexCompletionHelper/PCSlotCheckHelper) genutzt, da beide
     * Seiten im selben Modul kompiliert werden (kein Client/Server-Split in diesem Projekt).
     */
    public static final String[] REGIONS = {"Alola", "Galar", "Hisui", "Paldea"};

    /**
     * Nutzer-Vorgabe (Umbau "Regionalformen-Unterkategorien von den Oberkategorien trennen"):
     * jede einzelne Region ist jetzt ein VOLLWERTIGES, frei einordenbares Mitglied derselben
     * Kategorie-Reihenfolge-Liste wie Pokédex/Living Dex/Kosmetische Formen (nicht mehr an die
     * feste Position der ehemaligen Gruppen-Kategorien 3/4 gebunden) - dafür bekommt jede Region
     * eine SYNTHETISCHE ID oberhalb des echten Kategorie-ID-Bereichs (0-7), die NIRGENDS in
     * LivingDexPlusRegistry.Entry.categoryId() auftaucht (dort bleibt es bei den echten IDs 3/4 +
     * Formname=Region) - nur in ClientSettingsHelper.livingDexPlusCategories (Auswahl+Reihenfolge)
     * und den davon abgeleiteten Sortier-/Box-Aufbau-Routinen (LivingDexPlusLayoutHelper.
     * buildFlatList()/PCSortHelper.ldpFlatList()), die eine synthetische ID beim Traversieren in
     * (echte Kategorie-ID, Region) auflösen.
     */
    public static final int REGION_ID_BASE = 100;
    public static final int REGION_SHINY_ID_BASE = 200;

    public static boolean isRegionSyntheticId(int id) { return id >= REGION_ID_BASE && id < REGION_ID_BASE + REGIONS.length; }
    public static boolean isRegionShinySyntheticId(int id) { return id >= REGION_SHINY_ID_BASE && id < REGION_SHINY_ID_BASE + REGIONS.length; }
    public static boolean isAnyRegionSyntheticId(int id) { return isRegionSyntheticId(id) || isRegionShinySyntheticId(id); }

    public static int regionSyntheticId(int regionIndex, boolean shiny) { return (shiny ? REGION_SHINY_ID_BASE : REGION_ID_BASE) + regionIndex; }

    /** Der Region-Index (0..3, siehe REGIONS) einer synthetischen ID - nur gültig, wenn isAnyRegionSyntheticId() true liefert. */
    public static int regionIndexFromSyntheticId(int id) {
        return isRegionShinySyntheticId(id) ? id - REGION_SHINY_ID_BASE : id - REGION_ID_BASE;
    }

    public static String regionNameFromSyntheticId(int id) { return REGIONS[regionIndexFromSyntheticId(id)]; }

    /** Die ECHTE Katalog-Kategorie-ID (3 bzw. 4), der eine synthetische Region-ID entspricht. */
    public static int realCategoryIdFromSyntheticId(int id) { return isRegionShinySyntheticId(id) ? 4 : 3; }

    /**
     * Nutzer-Vorgabe (feste Standard-/Fallback-Reihenfolge): Pokédex, Pokédex Shiny, Living Dex,
     * Living Dex Shiny, dann jede Region einzeln normal, dann jede Region einzeln Shiny, dann die
     * Kosmetisch-Art-Unterkategorien (siehe unten, zur Laufzeit angehängt), zuletzt die
     * "Kosmetische Formen"-Sammelkategorie für alle übrigen Arten. Dient sowohl als initiale
     * Box-Reihenfolge-Vorlage als auch als Sortierung für noch nicht aktivierte Kategorien/
     * Regionen in der Settings-Liste.
     */
    public static final List<Integer> CANONICAL_ORDER_BEFORE_COSMETIC = List.of(
        0, 7, 1, 2,
        regionSyntheticId(0, false), regionSyntheticId(1, false), regionSyntheticId(2, false), regionSyntheticId(3, false),
        regionSyntheticId(0, true), regionSyntheticId(1, true), regionSyntheticId(2, true), regionSyntheticId(3, true)
    );

    /** Die gemeinsame Basis-Kategorie "Kosmetische Formen" (alle Arten außerhalb der Whitelist) - kommt in der Reihenfolge NACH den Kosmetisch-Art-Unterkategorien. */
    public static final List<Integer> CANONICAL_ORDER_COSMETIC_TAIL = List.of(5, 6);

    /**
     * Nutzer-Vorgabe (Umbau "Kosmetische Formen: Unterkategorien", eingeschränkt nach Live-Test-
     * Feedback: "das ist etwas ausgeartet"): NICHT mehr jede Art mit kosmetischen Formen bekommt
     * automatisch eine eigene Unterkategorie - nur eine kleine, von Hand kuratierte Auswahl an
     * Arten mit einer "Vielzahl" an Formen (z.B. Alcremie: 63 echte Kombinationen). Alle anderen Arten
     * mit nur 1-2 Formen landen weiter in der gemeinsamen Basis-Kategorie 5/6 ("Kosmetische
     * Formen"), die dafür wieder direkt toggle-bar ist (siehe CANONICAL_ORDER_COSMETIC_TAIL).
     */
    public static final Set<String> COSMETIC_SUBCATEGORY_WHITELIST =
        Set.of("alcremie", "magikarp", "gyarados", "vivillon", "torterra", "arbok");

    /**
     * Verifiziert per javap/Cobblemon-Datendateien (data/cobblemon/species_feature_assignments/*
     * bzw. species_features/* im Cobblemon-Jar) - NICHT geraten: Karpador+Garados teilen sich
     * "magikarp_jump" (32 Werte, dieselbe Zuordnungsdatei nennt BEIDE Arten!), Vivillon
     * (vivillon_wings, 23 Regionalmuster), Chelterrar/Torterra (tree, 14 echte Setzling-Werte,
     * "none" bewusst ausgeschlossen - das ist die Standardform ohne Setzling), Arbok
     * (snake_pattern, 8 Werte - Ekans trägt dieselbe Funktion, wird hier aber bewusst NICHT
     * mit-getrackt, da nur Arbok genannt wurde), Hokumil/Alcremie (ZWEI gleichzeitige Merkmale:
     * "cream" [9 Werte inkl. vanilla] UND "decoration" [7 Werte] - macht 9x7=63 echte
     * Kombinationen, nicht nur die 8 Nicht-Standard-Formen aus Species.getForms()!). Diese Formen
     * laufen NICHT über Species.getForms() (dort real 0-1 Einträge, die zudem nur EIN Merkmal
     * abdecken), sondern über Cobblemons separates "Species Feature"-System
     * (ChoiceSpeciesFeatureProvider) - der gewählte Wert eines Pokemon-EXEMPLARS steckt in
     * Pokemon.getAspects(), nicht in Pokemon.getForm(). "aspectFormat" ist das Muster, mit dem aus
     * dem gewählten choice-Wert der tatsächliche Aspekt-String wird (per String.format mit %s
     * statt dem Cobblemon-eigenen "{{choice}}"). Arten mit MEHREREN Dimensionen (aktuell nur
     * Alcremie) bekommen einen kombinierten Formen-Bezeichner ("ruby-strawberry" usw.) aus dem
     * kartesischen Produkt aller Dimensionen.
     * BEWUSST NICHT abgedeckt: Monetigo/Gholdengo ("gimmighoul_coins"/"gimmighoul_netherite" sind
     * TYPE=integer, 0-999 bzw. 0-256 - eine fortlaufende Zahl, keine feste Formen-Liste. Welcher
     * Zahlenbereich zu welcher sichtbaren Gholdengo-Optik führt, ist nicht in den Daten hinterlegt
     * (vermutlich hart in Cobblemons Renderer-Code) - eigenständige Baustelle für später.
     */
    public record FeatureDimension(String aspectFormat, List<String> choices) {}
    public record FeatureSpecies(List<FeatureDimension> dimensions) {}

    private static FeatureSpecies singleDimension(String aspectFormat, List<String> choices) {
        return new FeatureSpecies(List.of(new FeatureDimension(aspectFormat, choices)));
    }

    private static final List<String> MAGIKARP_JUMP_CHOICES = List.of(
        "standard", "apricot-stripes", "apricot-tiger", "apricot-zebra", "black-forehead", "black-mask",
        "blue-raindrops", "blue-saucy", "brown-stripes", "brown-tiger", "brown-zebra", "calico-orange-gold",
        "calico-orange-white", "calico-orange-white-black", "calico-white-orange", "gray-bubbles",
        "gray-diamonds", "gray-patches", "orange-dapples", "orange-forehead", "orange-mask", "orange-orca",
        "orange-two-tone", "pink-dapples", "pink-orca", "pink-two-tone", "purple-bubbles", "purple-diamonds",
        "purple-patches", "skelly", "violet-raindrops", "violet-saucy");

    public static final Map<String, FeatureSpecies> FEATURE_SPECIES = Map.of(
        // BUGFIX (Nutzer-Report: "es gibt ja auch 32 Garados"): die Zuordnungsdatei
        // magikarp_jump.json nennt AUSDRÜCKLICH sowohl magikarp als auch gyarados - beide teilen
        // sich exakt dieselben 32 Muster (dieselben Aspekt-Strings "magikarp-jump-*").
        "magikarp", singleDimension("magikarp-jump-%s", MAGIKARP_JUMP_CHOICES),
        "gyarados", singleDimension("magikarp-jump-%s", MAGIKARP_JUMP_CHOICES),
        "vivillon", singleDimension("vivillon-wings-%s", List.of(
            "icy-snow", "polar", "tundra", "continental", "garden", "elegant", "meadow", "modern", "marine",
            "archipelago", "high-plains", "sandstorm", "river", "monsoon", "savanna", "sun", "ocean", "jungle",
            "fancy", "poke-ball", "inferno", "void", "forsaken")),
        "torterra", singleDimension("tree-%s", List.of(
            "oak", "birch", "darkoak", "acacia", "azalea", "swamp", "jungle", "spruce", "mangrove", "cherry",
            "apricorn", "crimson", "warped", "saccharine")),
        "arbok", singleDimension("snake-pattern-%s", List.of(
            "classic", "legacy", "attack", "dark", "elusive", "heart", "speed", "sound")),
        // BUGFIX (Nutzer-Report: "weit mehr als 9 Hokumil-Varianten"): Alcremie hat ZWEI
        // gleichzeitige Merkmale (Creme-Sorte UND Deko-Topping), nicht nur eins - 9x7=63 echte
        // Kombinationen (data/cobblemon/species_features/cream.json + decoration.json).
        "alcremie", new FeatureSpecies(List.of(
            new FeatureDimension("cream-%s", List.of(
                "vanilla", "ruby", "matcha", "mint", "lemon", "salted", "ruby_swirl", "caramel_swirl", "rainbow_swirl")),
            new FeatureDimension("decoration-%s", List.of(
                "strawberry", "berry", "love", "star", "clover", "flower", "ribbon"))
        ))
    );

    /**
     * Nutzer-Vorgabe: Karpador und Garados bleiben in den Optionen zwei GETRENNTE Unterkategorien
     * (man will vielleicht nur einen von beiden vervollständigen), teilen sich aber - falls BEIDE
     * aktiviert sind - dieselben Boxen statt je eigener (siehe ldpFlatList()/buildFlatList()).
     */
    public static final Set<String> MAGIKARP_GYARADOS_BOX_GROUP = Set.of("magikarp", "gyarados");

    /**
     * Anzeigename für Box-Beschriftungen (serverseitig gesetzter Klartext, KEIN Übersetzungs-
     * schlüssel - PC-Boxnamen werden nie pro Spieler neu übersetzt) - deutsch, falls der
     * anfragende Spieler-Client auf Deutsch steht (siehe AutoNameBoxesPacket), sonst der englische
     * Artname aus dem Katalog. Nur für die Kosmetisch-Unterkategorien-Whitelist relevant.
     */
    private static final Map<String, String> COSMETIC_SPECIES_DE_NAMES = Map.of(
        "magikarp", "Karpador",
        "gyarados", "Garados",
        "vivillon", "Vivillon",
        "torterra", "Chelterrar",
        "arbok", "Arbok",
        "alcremie", "Hokumil"
    );

    /** Anzeigename für eine Kosmetisch-Whitelist-Art (Karpador/Garados-Gruppe -> kombinierter Name) - siehe COSMETIC_SPECIES_DE_NAMES-Doc-Kommentar. */
    public static String cosmeticSpeciesDisplayName(String speciesName, boolean german) {
        String lower = speciesName.toLowerCase();
        if (MAGIKARP_GYARADOS_BOX_GROUP.contains(lower)) {
            return german ? "Karpador/Garados" : "Magikarp/Gyarados";
        }
        if (german) return COSMETIC_SPECIES_DE_NAMES.getOrDefault(lower, speciesName);
        return speciesName;
    }

    /**
     * Formen-/Wahl-Bezeichner dieses Pokemon-EXEMPLARS für die Kategorie-3/4/5/6-Zuordnung - null,
     * falls Standardform (kein Unterschied). Für Arten in FEATURE_SPECIES kommt der Wert aus
     * Pokemon.getAspects() (Species-Feature-System, siehe FEATURE_SPECIES-Doc-Kommentar) - bei
     * MEHREREN Dimensionen (Alcremie) werden alle Dimensionen-Werte mit "-" verbunden (z.B.
     * "ruby-strawberry"), sonst wie gewohnt aus Pokemon.getForm() - BUGFIX (Nutzer-Report "kein
     * einziger Rahmen in Living Dex+"): Pokemon.getForm() nennt die Standardform generisch
     * "Normal", nicht nach der Art selbst.
     */
    public static String effectiveFormChoice(Pokemon pokemon) {
        String speciesName = pokemon.getSpecies().getName();
        FeatureSpecies fs = FEATURE_SPECIES.get(speciesName.toLowerCase());
        if (fs != null) {
            try {
                Set<String> aspects = pokemon.getAspects();
                List<String> parts = new ArrayList<>();
                for (FeatureDimension dim : fs.dimensions()) {
                    String matched = null;
                    for (String choice : dim.choices()) {
                        if (aspects.contains(String.format(dim.aspectFormat(), choice))) { matched = choice; break; }
                    }
                    if (matched == null) return null; // Dimension nicht (mehr) auflösbar -> als Standardform behandeln
                    parts.add(matched);
                }
                return String.join("-", parts);
            } catch (Exception ignored) {}
            return null;
        }
        String formName = pokemon.getForm() != null ? pokemon.getForm().getName() : speciesName;
        if (formName == null || formName.equalsIgnoreCase(speciesName) || formName.equalsIgnoreCase("normal")) return null;
        return formName;
    }

    /** Alle kombinierten Formen-Bezeichner (kartesisches Produkt aller Dimensionen) einer FEATURE_SPECIES-Art. */
    private static List<String> combinedChoices(FeatureSpecies fs) {
        List<String> result = new ArrayList<>(List.of(""));
        for (FeatureDimension dim : fs.dimensions()) {
            List<String> next = new ArrayList<>();
            for (String prefix : result) {
                for (String choice : dim.choices()) {
                    next.add(prefix.isEmpty() ? choice : prefix + "-" + choice);
                }
            }
            result = next;
        }
        return result;
    }

    public static final int COSMETIC_ID_BASE = 1000;
    public static final int COSMETIC_SHINY_ID_BASE = 2000;
    private static final int MAX_COSMETIC_SPECIES = 1000;

    public static boolean isCosmeticSyntheticId(int id) { return id >= COSMETIC_ID_BASE && id < COSMETIC_ID_BASE + MAX_COSMETIC_SPECIES; }
    public static boolean isCosmeticShinySyntheticId(int id) { return id >= COSMETIC_SHINY_ID_BASE && id < COSMETIC_SHINY_ID_BASE + MAX_COSMETIC_SPECIES; }
    public static boolean isAnyCosmeticSyntheticId(int id) { return isCosmeticSyntheticId(id) || isCosmeticShinySyntheticId(id); }

    public static int cosmeticSyntheticId(int speciesIndex, boolean shiny) { return (shiny ? COSMETIC_SHINY_ID_BASE : COSMETIC_ID_BASE) + speciesIndex; }

    /** Der Art-Index (siehe cosmeticSpeciesNames()) einer synthetischen ID - nur gültig, wenn isAnyCosmeticSyntheticId() true liefert. */
    public static int cosmeticSpeciesIndexFromSyntheticId(int id) {
        return isCosmeticShinySyntheticId(id) ? id - COSMETIC_SHINY_ID_BASE : id - COSMETIC_ID_BASE;
    }

    /** Die ECHTE Katalog-Kategorie-ID (5 bzw. 6), der eine synthetische Kosmetisch-Art-ID entspricht. */
    public static int realCategoryIdFromCosmeticSyntheticId(int id) { return isCosmeticShinySyntheticId(id) ? 6 : 5; }

    /**
     * Nur noch die in COSMETIC_SUBCATEGORY_WHITELIST gelisteten Arten (siehe dort), alphabetisch
     * sortiert (case-insensitive) - die Sortierung muss stabil sein, da der INDEX in dieser Liste
     * Teil der persistierten synthetischen ID ist (siehe cosmeticSyntheticId()). Dieselbe
     * Artenliste gilt für Kategorie 6 (Shiny).
     */
    public static List<String> cosmeticSpeciesNames() {
        java.util.TreeSet<String> names = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Entry e : getAll()) {
            if (e.categoryId() == 5 && COSMETIC_SUBCATEGORY_WHITELIST.contains(e.speciesName().toLowerCase())) names.add(e.speciesName());
        }
        return new ArrayList<>(names);
    }

    /** Ergebnis von resolveCosmeticBoxGroup(): welche Art(en) für diese Box zusammen aufgebaut werden, und welche synthetischen IDs damit "verbraucht" sind (nicht nochmal einzeln verarbeiten). */
    public record CosmeticBoxGroup(List<String> speciesNames, Set<Integer> consumedSyntheticIds) {}

    /**
     * Nutzer-Vorgabe: Karpador und Garados bleiben in den Settings getrennt togglebar, teilen sich
     * aber - falls BEIDE gerade aktiv sind - eine gemeinsame Box-Sequenz statt zwei getrennter
     * (siehe MAGIKARP_GYARADOS_BOX_GROUP). Wird von PCSortHelper.ldpFlatList() (Client) und
     * LivingDexPlusLayoutHelper.buildFlatList() (Server) gleichermaßen genutzt, damit Box-Aufbau
     * und Box-Beschriftung nie auseinanderlaufen.
     */
    public static CosmeticBoxGroup resolveCosmeticBoxGroup(int id, List<Integer> order, List<String> allCosmeticSpecies) {
        boolean shiny = isCosmeticShinySyntheticId(id);
        int idx = cosmeticSpeciesIndexFromSyntheticId(id);
        if (idx < 0 || idx >= allCosmeticSpecies.size()) return new CosmeticBoxGroup(List.of(), Set.of(id));
        String species = allCosmeticSpecies.get(idx);
        List<String> names = new ArrayList<>(List.of(species));
        java.util.Set<Integer> consumed = new java.util.HashSet<>(Set.of(id));
        if (MAGIKARP_GYARADOS_BOX_GROUP.contains(species.toLowerCase())) {
            for (String sibling : MAGIKARP_GYARADOS_BOX_GROUP) {
                if (sibling.equalsIgnoreCase(species)) continue;
                int siblingIdx = -1;
                for (int i = 0; i < allCosmeticSpecies.size(); i++) {
                    if (allCosmeticSpecies.get(i).equalsIgnoreCase(sibling)) { siblingIdx = i; break; }
                }
                if (siblingIdx < 0) continue;
                int siblingId = cosmeticSyntheticId(siblingIdx, shiny);
                if (order.contains(siblingId)) {
                    names.add(allCosmeticSpecies.get(siblingIdx));
                    consumed.add(siblingId);
                }
            }
        }
        return new CosmeticBoxGroup(names, consumed);
    }

    private static final Set<String> REGIONAL_FORM_NAMES = Set.of("alola", "galar", "hisui", "paldea");
    private static final Set<String> EXCLUDED_ASPECT_KEYWORDS = Set.of("gmax", "mega", "primal", "totem");

    private static List<Entry> cache;

    public static synchronized List<Entry> getAll() {
        if (cache == null) cache = build();
        return cache;
    }

    private static List<Entry> build() {
        List<Entry> result = new ArrayList<>();
        // Familien-Kollabierung für BASE_POKEDEX (Kategorie 0): pro Familie/Zweig nur EIN Eintrag,
        // dedupliziert über den Familien-Anker (siehe EvolutionFamilyHelper, derselbe Mechanismus
        // wie beim Pokédex-Sortiermodus-Fix in Phase 0) - läuft hier serverseitig direkt, ohne den
        // Client-Roundtrip aus FamilySlotRequestPacket (der ist für Einzelabfragen gedacht, nicht
        // für 1000+ Arten auf einmal).
        java.util.Set<Integer> seenFamilyAnchors = new java.util.HashSet<>();

        // BUGFIX (Nutzer-Report: Living Dex+ endet bei 851 statt 1025 Arten, Home-Zähler ebenso
        // betroffen): PokemonSpecies.INSTANCE.getImplemented() filtert auf das "implemented"-Flag
        // der Species-JSONs - 174 Arten (u.a. Celebi/Entei/Raikou/Suicune sowie diverse Gen-3-
        // Arten) haben dieses Feld in ihrer JSON schlicht nicht gesetzt und werden von Cobblemon
        // dadurch als NICHT implementiert behandelt (851 explizit true + 174 ohne Feld = 1025).
        // getSpecies() liefert dagegen ALLE geparsten Arten unabhängig vom Flag - für Dex-Zähler/
        // Box-Layout/Fangen-Listen ist das korrekt, da diese Arten trotzdem besessen/gefangen
        // werden können (z.B. per Command), auch ohne eigenes 3D-Modell.
        for (Species species : PokemonSpecies.INSTANCE.getSpecies()) {
            int dexNumber = species.getNationalPokedexNumber();
            if (dexNumber <= 0) continue;
            String name = species.getName();

            int familyAnchor = EvolutionFamilyHelper.effectiveAnchorDexNumber(species);
            if (seenFamilyAnchors.add(familyAnchor)) {
                result.add(new Entry(0, name, "", familyAnchor)); // BASE_POKEDEX
                result.add(new Entry(7, name, "", familyAnchor)); // SHINY_POKEDEX (dieselbe Familien-Kollabierung wie Kategorie 0)
            }
            result.add(new Entry(1, name, "", dexNumber)); // BASE_LIVING_DEX
            result.add(new Entry(2, name, "", dexNumber)); // BASE_SHINY (= "Shiny Living Dex")

            // Kosmetische Formen über Cobblemons "Species Feature"-System statt Species.getForms()
            // (siehe FEATURE_SPECIES-Doc-Kommentar) - läuft NICHT über species.getForms() (dort 0-1
            // Einträge, die zudem nur EIN Merkmal abdecken würden), sondern über eine von Hand
            // kuratierte Werteliste, deren reale Existenz per Cobblemon-Datendateien verifiziert
            // wurde. Für diese Arten wird species.getForms() bewusst GAR NICHT durchlaufen, sonst
            // gäbe es unvollständige Doppel-Einträge (z.B. Alcremies 8 Formen-Einträge NEBEN den
            // 63 echten Feature-Kombinationen).
            FeatureSpecies fs = FEATURE_SPECIES.get(name.toLowerCase());
            if (fs != null) {
                for (String choice : combinedChoices(fs)) {
                    result.add(new Entry(5, name, choice, dexNumber)); // COSMETIC_FORMS (Feature-basiert)
                    result.add(new Entry(6, name, choice, dexNumber)); // COSMETIC_FORMS_SHINY (Feature-basiert)
                }
                continue;
            }

            for (FormData form : species.getForms()) {
                String formName = form.getName();
                if (formName == null || formName.equalsIgnoreCase(name)) continue; // Standardform selbst -> schon oben erfasst

                if (isExcludedBattleForm(form)) continue;

                boolean regional = REGIONAL_FORM_NAMES.contains(formName.toLowerCase());
                if (regional) {
                    result.add(new Entry(3, name, formName, dexNumber)); // REGIONAL_FORMS
                    result.add(new Entry(4, name, formName, dexNumber)); // REGIONAL_FORMS_SHINY
                } else {
                    result.add(new Entry(5, name, formName, dexNumber)); // COSMETIC_FORMS
                    result.add(new Entry(6, name, formName, dexNumber)); // COSMETIC_FORMS_SHINY
                }
            }
        }
        return result;
    }

    private static boolean isExcludedBattleForm(FormData form) {
        try {
            List<String> aspects = form.getAspects();
            if (aspects == null) return false;
            for (String aspect : aspects) {
                String lower = aspect.toLowerCase();
                for (String keyword : EXCLUDED_ASPECT_KEYWORDS) {
                    if (lower.contains(keyword)) return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }
}
