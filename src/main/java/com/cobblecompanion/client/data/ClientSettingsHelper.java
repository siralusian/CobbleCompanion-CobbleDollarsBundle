package com.cobblecompanion.client.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Persistierte Client-Einstellungen für den Settings-Tab (Gson-JSON im Config-Ordner, wie {@code PlayerActivityTracker} serverseitig). */
public class ClientSettingsHelper {

    private static class Data {
        boolean todoShowEvolveButton = true;
        boolean todoConfirmEvolution = true;
        boolean todoSendOutBeforeEvolve = false;
        boolean todoCloseOnEvolve = false;
        // "Modus" (Pokédex/Living Dex): EIN gemeinsamer Schalter für ToDo-Liste-Filterung UND die
        // Dex-Vervollständigungshilfe (ToDo-Tab, rechte Hälfte) - ehemals zwei getrennte Settings
        // ("Entwicklungen zeigen" mit ALL/POKEDEX_ONLY/LIVING_DEX_ONLY plus ein eigener DexHelp-
        // Modus-Schalter), auf ausdrücklichen Nutzer-Wunsch zu einer Option zusammengelegt - die
        // "ALL"-Option von "Entwicklungen zeigen" gibt es dadurch nicht mehr, beide Tab-Hälften
        // teilen sich jetzt exakt denselben Pokédex/Living-Dex-Wert. Bewusst weiterhin GETRENNT
        // von pcSortModePokedex (PC-Sortierhilfe) - das ist ein eigenes Feature mit eigenem Zweck.
        boolean modusPokedex = true;

        boolean whoNeedsOnlyFriends = false;
        boolean whoNeedsFriendsFirst = true;

        boolean friendsAutoAcceptRequests = false;
        boolean friendsShowOffline = true;
        // Merker fürs spätere Teleport-Feature (noch nicht gebaut) - Freunde dürfen sich
        // (falls true) zu mir teleportieren. Bleibt vorerst client-lokal.
        boolean friendsAllowTeleportToMe = false;

        // PC-Sortierhilfe (Punkt 4): rein client-lokal, kein Server-Sync nötig, da nur eine
        // Anzeige-Hilfe über Cobblemons eigenem PC-GUI (siehe PCSortHelperOverlay).
        boolean pcSortHelperEnabled = true;
        // 0=Pokédex, 1=Living Dex, 2=Living Dex+ (siehe VariantCategory) - ersetzt das frühere
        // boolean-Feld pcSortModePokedex. Alte Config-Dateien ohne dieses Feld fallen automatisch
        // auf 0 zurück (Gson-Default) - dasselbe Verhalten wie der frühere Default true.
        int pcSortMode = 0;
        int pcSortStartBox = 2;
        // "Slot Prüfung": Home-Tab zeigt bei aktiver Option, wie viele PC-Pokemon gerade außerhalb
        // ihrer Dex-Box liegen (siehe PCSlotCheckHelper, serverseitig berechnet).
        boolean pcSlotCheckEnabled = false;
        // Nutzer-Vorgabe: Meldungen für automatische Einnahmen/Ausgaben (Schlauer Beobachter,
        // siehe AutomaticIncomeReportPacket) ausblenden können - rein client-lokal, da die
        // Nachricht selbst inzwischen komplett client-seitig aus den vom Server gesendeten
        // Rohzahlen gebaut wird (siehe AutomaticIncomeReportPacket.handle).
        boolean hideObserverMessages = false;
        // Living Dex+ (Punkt: "Living Dex Extrem"): welche Varianten-Kategorien (siehe
        // VariantCategory) der Spieler tracken will UND in welcher Reihenfolge sie in den PC-
        // Boxen angelegt werden - ein LinkedHashSet kodiert beides gleichzeitig (Mitgliedschaft
        // UND Einfüge-Reihenfolge = Box-Reihenfolge), Drag&Drop schreibt die Reihenfolge einfach neu.
        // Seit dem Umbau "Regionalformen-Unterkategorien von den Oberkategorien trennen" enthält
        // dieses Set NEBEN den echten Basis-Kategorie-IDs (0-7) auch synthetische Pro-Region-IDs
        // (siehe LivingDexPlusRegistry.regionSyntheticId()) - jede Region ist dadurch ein
        // vollwertiges, frei einordenbares Mitglied derselben Liste, kein Sonderfall mehr.
        java.util.LinkedHashSet<Integer> livingDexPlusCategories = new java.util.LinkedHashSet<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Data data;
    private static Path dataFile;

    private static Data get() {
        if (data == null) load();
        return data;
    }

    private static Path resolveDataFile() {
        if (dataFile == null) {
            dataFile = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("cobblecompanion_settings.json");
        }
        return dataFile;
    }

    private static void load() {
        data = new Data();
        Path file = resolveDataFile();
        if (!Files.exists(file)) return;
        try (Reader reader = Files.newBufferedReader(file)) {
            Data loaded = GSON.fromJson(reader, Data.class);
            if (loaded != null) data = loaded;
        } catch (IOException ignored) {}
    }

    private static void save() {
        Path file = resolveDataFile();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(get(), writer);
            }
        } catch (IOException ignored) {}
    }

    public static boolean isTodoShowEvolveButton() { return get().todoShowEvolveButton; }
    public static void toggleTodoShowEvolveButton() { get().todoShowEvolveButton = !get().todoShowEvolveButton; save(); }

    public static boolean isTodoConfirmEvolution() { return get().todoConfirmEvolution; }
    public static void toggleTodoConfirmEvolution() { get().todoConfirmEvolution = !get().todoConfirmEvolution; save(); }

    public static boolean isTodoSendOutBeforeEvolve() { return get().todoSendOutBeforeEvolve; }
    public static void toggleTodoSendOutBeforeEvolve() { get().todoSendOutBeforeEvolve = !get().todoSendOutBeforeEvolve; save(); }

    public static boolean isTodoCloseOnEvolve() { return get().todoCloseOnEvolve; }
    public static void toggleTodoCloseOnEvolve() { get().todoCloseOnEvolve = !get().todoCloseOnEvolve; save(); }

    public static boolean isModusPokedex() { return get().modusPokedex; }
    public static void toggleModus() { get().modusPokedex = !get().modusPokedex; save(); }

    public static boolean isWhoNeedsOnlyFriends() { return get().whoNeedsOnlyFriends; }
    public static void toggleWhoNeedsOnlyFriends() { get().whoNeedsOnlyFriends = !get().whoNeedsOnlyFriends; save(); }

    public static boolean isWhoNeedsFriendsFirst() { return get().whoNeedsFriendsFirst; }
    public static void toggleWhoNeedsFriendsFirst() { get().whoNeedsFriendsFirst = !get().whoNeedsFriendsFirst; save(); }

    public static boolean isFriendsAutoAcceptRequests() { return get().friendsAutoAcceptRequests; }
    public static void toggleFriendsAutoAcceptRequests() { get().friendsAutoAcceptRequests = !get().friendsAutoAcceptRequests; save(); }

    public static boolean isFriendsShowOffline() { return get().friendsShowOffline; }
    public static void toggleFriendsShowOffline() { get().friendsShowOffline = !get().friendsShowOffline; save(); }

    public static boolean isFriendsAllowTeleportToMe() { return get().friendsAllowTeleportToMe; }
    public static void toggleFriendsAllowTeleportToMe() { get().friendsAllowTeleportToMe = !get().friendsAllowTeleportToMe; save(); }

    public static boolean isPcSortHelperEnabled() { return get().pcSortHelperEnabled; }
    public static void togglePcSortHelperEnabled() { get().pcSortHelperEnabled = !get().pcSortHelperEnabled; save(); }

    public static final int PC_SORT_MODE_POKEDEX = 0;
    public static final int PC_SORT_MODE_LIVING_DEX = 1;
    public static final int PC_SORT_MODE_LIVING_DEX_PLUS = 2;

    /** Bleibt aus Kompatibilitätsgründen erhalten (viele bestehende Call-Sites) - true nur im reinen Pokédex-Modus. */
    public static boolean isPcSortModePokedex() { return get().pcSortMode == PC_SORT_MODE_POKEDEX; }

    public static int getPcSortMode() { return get().pcSortMode; }
    public static boolean isPcSortModeLivingDexPlus() { return get().pcSortMode == PC_SORT_MODE_LIVING_DEX_PLUS; }

    /**
     * forward=true: nächster Modus (Linksklick), forward=false: vorheriger (Rechtsklick). Die
     * "Dex Wahl"-Zeile (Settings-Kategorie "Pokédex") ist der EINZIGE UI-Weg, den PC-Sortiermodus
     * zu ändern - schreibt deshalb bewusst BEIDES: pcSortMode (PC-Sortierhilfe/Living-Dex+) UND
     * das eigenständige modusPokedex-Feld (ToDo-Filter/DexCompletionHelper). BUGFIX: modusPokedex
     * blieb hier vorher unangetastet und damit für immer auf seinem alten/Default-Wert stehen,
     * seit die alte "Modus"-Zeile (SKEY_MODUS/toggleModus()) aus dem ToDo-Tab entfernt und durch
     * diese Zeile ersetzt wurde - ToDo/WhoNeeds zeigten dadurch immer denselben (eingefrorenen)
     * Stand, egal was hier ausgewählt wurde. modusPokedex bleibt bewusst ein EIGENES, direkt
     * gespeichertes Feld (nicht aus pcSortMode abgeleitet) - falls andere Codepfade es künftig
     * wieder unabhängig ändern müssen, bleibt das möglich, ohne pcSortMode mit zu beeinflussen.
     */
    public static void cyclePcSortMode(boolean forward) {
        int next = get().pcSortMode + (forward ? 1 : -1);
        if (next > PC_SORT_MODE_LIVING_DEX_PLUS) next = PC_SORT_MODE_POKEDEX;
        if (next < PC_SORT_MODE_POKEDEX) next = PC_SORT_MODE_LIVING_DEX_PLUS;
        get().pcSortMode = next;
        get().modusPokedex = (next == PC_SORT_MODE_POKEDEX);
        save();
    }

    public static int getPcSortStartBox() { return get().pcSortStartBox; }
    public static void setPcSortStartBox(int box) { get().pcSortStartBox = Math.max(1, box); save(); }

    public static boolean isPcSlotCheckEnabled() { return get().pcSlotCheckEnabled; }
    public static void togglePcSlotCheckEnabled() { get().pcSlotCheckEnabled = !get().pcSlotCheckEnabled; save(); }

    public static boolean isHideObserverMessages() { return get().hideObserverMessages; }
    public static void toggleHideObserverMessages() { get().hideObserverMessages = !get().hideObserverMessages; save(); }

    // ===== Living Dex+ Kategorien (siehe VariantCategory) =====

    public static boolean isLivingDexPlusCategoryEnabled(int categoryId) {
        return get().livingDexPlusCategories.contains(categoryId);
    }

    // Gegenseitig ausschließende Kategorie-Paare (Nutzer-Vorgabe): Pokédex(0)+Living Dex(1)
    // dürfen nicht gleichzeitig aktiv sein, ebenso Shiny Pokédex(7)+Shiny Living Dex(2) - alle
    // anderen Kombinationen (auch z.B. Pokédex+Shiny Living Dex) sind ausdrücklich erlaubt.
    private static final int[][] EXCLUSIVE_PAIRS = { {0, 1}, {7, 2} };

    public static void toggleLivingDexPlusCategory(int categoryId) {
        java.util.LinkedHashSet<Integer> cats = get().livingDexPlusCategories;
        if (!cats.remove(categoryId)) {
            for (int[] pair : EXCLUSIVE_PAIRS) {
                if (pair[0] == categoryId) cats.remove(pair[1]);
                if (pair[1] == categoryId) cats.remove(pair[0]);
            }
            cats.add(categoryId);
        }
        save();
    }

    /** Aktivierte Kategorien in Box-Reihenfolge (Einfüge-Reihenfolge des LinkedHashSet). */
    public static java.util.List<Integer> getLivingDexPlusCategoryOrder() {
        return new java.util.ArrayList<>(get().livingDexPlusCategories);
    }

    /** Schreibt die komplette Reihenfolge neu (Drag&Drop-Commit) - newOrder muss dieselben IDs wie vorher enthalten. */
    public static void reorderLivingDexPlusCategories(java.util.List<Integer> newOrder) {
        java.util.LinkedHashSet<Integer> rebuilt = new java.util.LinkedHashSet<>(newOrder);
        get().livingDexPlusCategories = rebuilt;
        save();
    }
}
