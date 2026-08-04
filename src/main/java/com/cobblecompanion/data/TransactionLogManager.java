package com.cobblecompanion.data;

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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Verlauf der Cobbledollars-Bewegungen pro Spieler (Wallet-Tab, linke Spalte) - Überweisungen
 * gesendet/empfangen, Verkauf an einen CobbleMerchant, Bezahlung an einem designierten
 * Lagerticker, Bezahlung für gekaufte Creative-Zeit. Rein datenhaltend, wird von den jeweiligen
 * Packet-Handlern/Mixins bei jeder erfolgreichen Transaktion ergänzt (siehe EntryType). Je Spieler
 * auf die letzten MAX_ENTRIES begrenzt (neueste zuerst), ältere fallen automatisch raus.
 * Persistiert als Gson-JSON im Weltordner, gleiches Muster wie {@link ServerRulesManager}.
 */
public class TransactionLogManager {

    public static final int TRANSFER_SENT = 0;
    public static final int TRANSFER_RECEIVED = 1;
    public static final int MERCHANT_SOLD = 2;
    public static final int TICKER_PAID = 3;
    public static final int CREATIVE_PAID = 4;
    public static final int OBSERVER_REWARD = 5;
    public static final int OBSERVER_CHARGE = 6;
    public static final int ONLINE_REWARD = 7;
    public static final int MERCHANT_BOUGHT = 8;
    /** Verkaufserlös an den konfigurierten Shop-Betreiber (siehe CobbleMerchantPayoutManager) - counterpart packt Käufername+Item+Menge (siehe SALE_DETAIL_DELIMITER), analog zu TICKER_PAID. */
    public static final int SALE_DETAIL = 9;
    public static final String SALE_DETAIL_DELIMITER = "";

    private static final int MAX_ENTRIES = 50;

    public record Entry(int type, String amount, String counterpart) {}

    private static class Data {
        Map<String, List<Entry>> entries = new HashMap<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Data data = new Data();
    private static Path dataFile;

    public static void init(MinecraftServer server) {
        dataFile = server.getWorldPath(LevelResource.ROOT).resolve("cobblecompanion_transaction_log.json");
        load();
    }

    public static void addEntry(UUID player, int type, String amount, String counterpart) {
        List<Entry> list = data.entries.computeIfAbsent(player.toString(), k -> new ArrayList<>());
        list.add(0, new Entry(type, amount, counterpart));
        while (list.size() > MAX_ENTRIES) list.remove(list.size() - 1);
        save();
    }

    /** Neueste zuerst. */
    public static List<Entry> getEntries(UUID player) {
        List<Entry> list = data.entries.get(player.toString());
        return list == null ? List.of() : Collections.unmodifiableList(list);
    }

    private static void load() {
        data = new Data();
        if (dataFile == null || !Files.exists(dataFile)) return;
        try (Reader reader = Files.newBufferedReader(dataFile)) {
            Data loaded = GSON.fromJson(reader, Data.class);
            if (loaded != null) data = loaded;
        } catch (IOException ignored) {}
    }

    private static void save() {
        if (dataFile == null) return;
        try (Writer writer = Files.newBufferedWriter(dataFile)) {
            GSON.toJson(data, writer);
        } catch (IOException ignored) {}
    }
}
