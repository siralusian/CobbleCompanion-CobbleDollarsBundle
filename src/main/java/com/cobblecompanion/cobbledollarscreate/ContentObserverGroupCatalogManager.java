package com.cobblecompanion.cobbledollarscreate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Nutzer-Vorgabe (3. Live-Test, "geteilte Katalog-Liste ohne Block-Rolle"): innerhalb einer
 * Zähler/Abzieher-Gruppe (siehe ContentObserverConfigManager.BlockConfig#groupId) teilen sich ALLE
 * Beobachter EINEN gemeinsamen Item/Empfänger/Preis-Katalog, statt dass jeder Block seine eigene
 * Liste pflegt. Jeder Katalog-Eintrag ({@link CatalogEntry}) trägt zwei unabhängige Zugehörigkeits-
 * Flags ({@link CatalogEntry#inCounterList}/{@link CatalogEntry#inSubtractorList}) - ein Eintrag
 * kann zur Zähler-Liste, zur Abzieher-Liste oder zu BEIDEN gehören (z.B. wenn derselbe Preis für
 * beide Rollen gelten soll). Welche Katalog-Items ein einzelner Block tatsächlich SELBST erkennt,
 * wählt er unabhängig davon per Checkbox aus (siehe ContentObserverConfigManager#effectiveCounterRules/
 * #effectiveSubtractorRules) - der frühere feste "Rolle"-Schalter pro Block ist komplett entfallen.
 *
 * Persistiert als Gson-JSON im Weltordner, gleiches Muster wie ContentObserverConfigManager/
 * ContentObserverGroupManager.
 */
public final class ContentObserverGroupCatalogManager {

    public static final class CatalogEntry {
        public String itemId;
        public String targetPlayerUuid;
        public long amountPerItem;
        /** Erscheint dieser Eintrag in der Zähler-Liste (oben im Editor)? Nur dort braucht er einen sinnvollen Empfänger. */
        public boolean inCounterList;
        /**
         * Erscheint dieser Eintrag in der Abzieher-Liste (unten im Editor)? Empfänger ist für das
         * Abziehen irrelevant, der PREIS dagegen schon: siehe ContentObserverPromiseManager -
         * storniert dieser Eintrag ein Zähler-Versprechen, wird die Differenz (Zähler-Preis minus
         * diesem Preis) sofort verrechnet, nicht einfach nur der Eintrag gelöscht.
         */
        public boolean inSubtractorList;
    }

    private static final class Data {
        Map<String, List<CatalogEntry>> catalogs = new HashMap<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Data data = new Data();
    private static Path dataFile;

    private ContentObserverGroupCatalogManager() {}

    public static void init(MinecraftServer server) {
        dataFile = server.getWorldPath(LevelResource.ROOT).resolve("cobblecompanion_content_observer_group_catalogs.json");
        load();
    }

    /** Nie null - leere Liste, wenn die Gruppe (noch) keinen Katalog hat. */
    public static List<CatalogEntry> getCatalog(String groupId) {
        if (groupId == null) return List.of();
        return data.catalogs.getOrDefault(groupId, List.of());
    }

    /**
     * Ersetzt den KOMPLETTEN Katalog dieser Gruppe - der Editor schickt beim Speichern immer den
     * vollständigen gewünschten Stand mit (die im GUI sichtbaren Listen SIND bereits der ganze
     * Katalog, siehe ContentObserverConfigScreen), kein granulares Hinzufügen/Entfernen nötig.
     */
    public static void replaceCatalog(String groupId, List<CatalogEntry> entries) {
        if (groupId == null) return;
        data.catalogs.put(groupId, new ArrayList<>(entries));
        save();
    }

    private static void load() {
        data = new Data();
        if (dataFile == null || !Files.exists(dataFile)) return;
        try (Reader reader = Files.newBufferedReader(dataFile)) {
            Data loaded = GSON.fromJson(reader, Data.class);
            if (loaded != null) data = loaded;
        } catch (IOException ignored) {
        } catch (com.google.gson.JsonParseException e) {
            // Bugfix (Live-Fund, Server-Crash): ein Datenformat-Wechsel während der Entwicklung
            // (voriger Zwischenstand: "catalogs" war Map<groupId, {counterRules,subtractorRules}>,
            // jetzt Map<groupId, List<CatalogEntry>>) ließ Gson beim Einlesen der ALTEN Datei mit
            // einer JsonSyntaxException abstürzen - das riss den GANZEN Server-Start mit (siehe
            // onServerStarting-Aufrufkette, keine eigene Absicherung dort). Ein nicht mehr lesbares/
            // kompatibles Format wird jetzt verworfen (leer neu begonnen) statt den Start zu verhindern.
            CobbleCompanionDollarsCreate.LOGGER.warn(
                "[CC] {} ließ sich nicht lesen (inkompatibles/beschädigtes Format) - starte mit leerem Beobachter-Gruppen-Katalog neu.", dataFile, e);
            data = new Data();
        }
        if (data.catalogs == null) data.catalogs = new HashMap<>();
    }

    private static void save() {
        if (dataFile == null) return;
        try (Writer writer = Files.newBufferedWriter(dataFile)) {
            GSON.toJson(data, writer);
        } catch (IOException ignored) {}
    }
}
