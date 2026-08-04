package com.cobblecompanion.cobbledollarscreate.data;

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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * MEHRERE benannte Preislisten (Item-ID -> Cobbledollars-Preis + Kategorie), wählbar PRO Lagerticker-
 * NETZWERK (freqId, siehe CreateStockTickerPriceManager - gleiches Prinzip: ein Netzwerk teilt sich
 * IMMER eine Liste über alle seine Ticker hinweg). Ein Merchant/CustomNPC-Merchant hat keine Liste
 * direkt, sondern über sein verknüpftes Netzwerk (siehe CobbleMerchantSellManager.
 * getEffectiveBuyTickerLink + resolveNetworkFreqId) - kein Netzwerk verknüpft -> "default".
 *
 * Nutzer-Vorgabe: mehrere Listen statt der bisherigen EINEN globalen (Migration siehe load() -
 * bestehende Preise gehen NICHT verloren, sie werden zur Liste "default"/"Standard", auf die jedes
 * Netzwerk ohne explizite Zuweisung automatisch zeigt - Verhalten bleibt für unveränderte Welten
 * exakt gleich).
 *
 * Zusätzlich zum Preis pro Item zwei unabhängige An/Aus-Flags (siehe ältere Klassenkommentare),
 * PLUS jetzt ein Kategorie-Feld pro Item (Nutzer-Vorgabe: Gruppierung der automatisch aus der Liste
 * erzeugten CobbleMerchant-Verkaufsliste, siehe PlayerExtensionKtOpenShopMixin) - eine Liste bündelt
 * IMMER Verkauf+Ankauf+Kategorie zusammen, keine getrennte Auswahl pro Richtung.
 */
public class CentralItemPriceManager {

    public record PriceListInfo(String id, String name) {}

    public static final String DEFAULT_LIST_ID = "default";

    public static class PriceList {
        String name = "Standard";
        Map<String, Long> sellPrices = new LinkedHashMap<>();
        Map<String, Long> buyPrices = new LinkedHashMap<>();
        Set<String> sellDisabled = new HashSet<>();
        Set<String> buyDisabled = new HashSet<>();
        Map<String, String> categories = new LinkedHashMap<>();
    }

    private static class Data {
        Map<String, PriceList> lists = new LinkedHashMap<>();
        Map<String, String> networkListAssignment = new HashMap<>();
        /** Nur für die Migration aus dem alten, einzelnen globalen Format gelesen - danach ungenutzt. */
        Map<String, Long> sellPrices;
        Map<String, Long> buyPrices;
        Set<String> sellDisabled;
        Set<String> buyDisabled;
        Map<String, Long> prices;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Data data = new Data();
    private static Path dataFile;

    public static void init(MinecraftServer server) {
        dataFile = server.getWorldPath(LevelResource.ROOT).resolve("cobblecompanion_central_item_prices.json");
        load();
    }

    private static PriceList getOrCreateDefaultList() {
        return data.lists.computeIfAbsent(DEFAULT_LIST_ID, id -> {
            PriceList l = new PriceList();
            l.name = "Standard";
            return l;
        });
    }

    private static PriceList list(String listId) {
        PriceList l = data.lists.get(listId);
        return l != null ? l : getOrCreateDefaultList();
    }

    public static List<PriceListInfo> getAllLists() {
        getOrCreateDefaultList();
        List<PriceListInfo> result = new ArrayList<>();
        for (Map.Entry<String, PriceList> e : data.lists.entrySet()) {
            result.add(new PriceListInfo(e.getKey(), e.getValue().name));
        }
        return result;
    }

    public static String createList(String name) {
        String id = UUID.randomUUID().toString();
        PriceList l = new PriceList();
        l.name = (name == null || name.isBlank()) ? "Liste" : name.trim();
        data.lists.put(id, l);
        save();
        return id;
    }

    /** Verweigert bei der letzten verbleibenden Liste oder unbekannter ID. Netzwerke, die auf die gelöschte Liste zeigten, fallen automatisch auf "default" zurück. */
    public static boolean deleteList(String listId) {
        if (DEFAULT_LIST_ID.equals(listId)) return false;
        if (data.lists.size() <= 1 || !data.lists.containsKey(listId)) return false;
        data.lists.remove(listId);
        data.networkListAssignment.entrySet().removeIf(e -> listId.equals(e.getValue()));
        save();
        return true;
    }

    public static void renameList(String listId, String name) {
        PriceList l = data.lists.get(listId);
        if (l == null || name == null || name.isBlank()) return;
        l.name = name.trim();
        save();
    }

    public static String getListName(String listId) {
        return list(listId).name;
    }

    /** freqId == null oder keine Zuweisung/Liste existiert nicht mehr -> "default". */
    public static String getListIdForNetwork(UUID freqId) {
        if (freqId == null) return DEFAULT_LIST_ID;
        String id = data.networkListAssignment.get(freqId.toString());
        return (id != null && data.lists.containsKey(id)) ? id : DEFAULT_LIST_ID;
    }

    public static void setListIdForNetwork(UUID freqId, String listId) {
        if (freqId == null) return;
        if (DEFAULT_LIST_ID.equals(listId)) data.networkListAssignment.remove(freqId.toString());
        else data.networkListAssignment.put(freqId.toString(), listId);
        save();
    }

    public static Map<String, Long> getSellPrices(String listId) {
        return list(listId).sellPrices;
    }

    public static Map<String, Long> getBuyPrices(String listId) {
        return list(listId).buyPrices;
    }

    public static Set<String> getSellDisabled(String listId) {
        return list(listId).sellDisabled;
    }

    public static Set<String> getBuyDisabled(String listId) {
        return list(listId).buyDisabled;
    }

    public static Map<String, String> getCategories(String listId) {
        return list(listId).categories;
    }

    /** Bis zu allen bekannten Kategorienamen dieser Liste, dedupliziert - Aufrufer filtert/kürzt selbst (siehe StockTickerPriceScreen Autocomplete). */
    public static Set<String> getAllCategoriesUsed(String listId) {
        return new LinkedHashSet<>(list(listId).categories.values());
    }

    /** 0, wenn das Item nicht in der Liste steht (Nutzer-Vorgabe: Default 0 statt Ablehnung). */
    public static long sellPriceForItem(String listId, String itemId) {
        return list(listId).sellPrices.getOrDefault(itemId, 0L);
    }

    /** 0, wenn das Item nicht in der Liste steht (Nutzer-Vorgabe: Default 0 statt Ablehnung). */
    public static long buyPriceForItem(String listId, String itemId) {
        return list(listId).buyPrices.getOrDefault(itemId, 0L);
    }

    public static boolean isSellEnabled(String listId, String itemId) {
        return !list(listId).sellDisabled.contains(itemId);
    }

    public static boolean isBuyEnabled(String listId, String itemId) {
        return !list(listId).buyDisabled.contains(itemId);
    }

    /** null, wenn dem Item in dieser Liste keine Kategorie zugewiesen ist. */
    public static String getCategory(String listId, String itemId) {
        return list(listId).categories.get(itemId);
    }

    public static void setListContents(String listId, String name, Map<String, Long> sellPrices, Map<String, Long> buyPrices,
                                        Set<String> sellDisabled, Set<String> buyDisabled, Map<String, String> categories) {
        PriceList l = data.lists.computeIfAbsent(listId, id -> new PriceList());
        if (name != null && !name.isBlank()) l.name = name.trim();
        l.sellPrices = new LinkedHashMap<>(sellPrices);
        l.buyPrices = new LinkedHashMap<>(buyPrices);
        l.sellDisabled = new HashSet<>(sellDisabled);
        l.buyDisabled = new HashSet<>(buyDisabled);
        l.categories = new LinkedHashMap<>(categories);
        save();
    }

    /** Union der Verkaufspreise über ALLE Listen hinweg - nur für CobbleDollarsBankBridge (rein clientseitige "Verkaufen"-Button-Freischaltung, keine echte Preislogik). */
    public static Map<String, Long> getUnionSellPrices() {
        Map<String, Long> union = new LinkedHashMap<>();
        for (PriceList l : data.lists.values()) union.putAll(l.sellPrices);
        return union;
    }

    /**
     * Einmalige Migration (siehe com.cobblecompanion.integrations.cobbledollars.CobbleDollarsScaleMigration
     * in CobbleCompanion: Basis): multipliziert JEDEN Preis in JEDER Liste mit 10, damit der reale
     * Wert nach Einführung der Ein-Nachkommastellen-Anzeige (SCALE=10) unverändert bleibt.
     */
    public static void scaleAllPricesBy10() {
        for (PriceList l : data.lists.values()) {
            l.sellPrices.replaceAll((k, v) -> v * 10);
            l.buyPrices.replaceAll((k, v) -> v * 10);
        }
        save();
    }

    private static void load() {
        data = new Data();
        if (dataFile == null || !Files.exists(dataFile)) {
            getOrCreateDefaultList();
            return;
        }
        try (Reader reader = Files.newBufferedReader(dataFile)) {
            Data loaded = GSON.fromJson(reader, Data.class);
            if (loaded != null) data = loaded;
        } catch (IOException | com.google.gson.JsonParseException ignored) {
            data = new Data();
        }
        if (data.lists == null) data.lists = new LinkedHashMap<>();
        if (data.networkListAssignment == null) data.networkListAssignment = new HashMap<>();

        // Migration: eine vor diesem Update gespeicherte Welt kennt nur die alten Top-Level-Felder
        // (getrennte sellPrices/buyPrices ODER noch älter: EIN gemeinsames "prices") - beim ersten
        // Laden danach als Liste "default"/"Standard" übernehmen, damit nichts verloren geht. Jedes
        // Netzwerk ohne explizite Zuweisung nutzt "default" automatisch (siehe getListIdForNetwork).
        boolean needsSave = false;
        if (data.lists.isEmpty()) {
            boolean hasSeparate = (data.sellPrices != null && !data.sellPrices.isEmpty())
                || (data.buyPrices != null && !data.buyPrices.isEmpty());
            if (hasSeparate) {
                PriceList def = new PriceList();
                def.name = "Standard";
                if (data.sellPrices != null) def.sellPrices = new LinkedHashMap<>(data.sellPrices);
                if (data.buyPrices != null) def.buyPrices = new LinkedHashMap<>(data.buyPrices);
                if (data.sellDisabled != null) def.sellDisabled = new HashSet<>(data.sellDisabled);
                if (data.buyDisabled != null) def.buyDisabled = new HashSet<>(data.buyDisabled);
                data.lists.put(DEFAULT_LIST_ID, def);
                needsSave = true;
            } else if (data.prices != null && !data.prices.isEmpty()) {
                PriceList def = new PriceList();
                def.name = "Standard";
                def.sellPrices = new LinkedHashMap<>(data.prices);
                def.buyPrices = new LinkedHashMap<>(data.prices);
                data.lists.put(DEFAULT_LIST_ID, def);
                needsSave = true;
            }
        }
        data.sellPrices = null;
        data.buyPrices = null;
        data.sellDisabled = null;
        data.buyDisabled = null;
        data.prices = null;
        getOrCreateDefaultList();
        if (needsSave) save();
    }

    private static void save() {
        if (dataFile == null) return;
        try (Writer writer = Files.newBufferedWriter(dataFile)) {
            GSON.toJson(data, writer);
        } catch (IOException ignored) {}
    }
}
