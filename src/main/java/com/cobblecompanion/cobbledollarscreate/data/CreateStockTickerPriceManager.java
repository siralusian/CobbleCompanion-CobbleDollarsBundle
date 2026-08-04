package com.cobblecompanion.cobbledollarscreate.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Pro Create-Lagerticker-NETZWERK (nicht mehr pro einzelnem Ticker-Block, Nutzer-Vorgabe) nur
 * noch ein einziges Flag: verlangt es Cobbledollars bei Entnahme oder nicht (siehe
 * mixin.PackageOrderRequestPacketMixin). Alle Lagerticker, die über
 * LogisticallyLinkedBehaviour.freqId (Create's Frequenz-basiertes Netzwerk, siehe
 * StockTickerBlockEntity.behaviour) miteinander verbunden sind, teilen sich deshalb GENAU EIN
 * Bezahlpflicht-Flag - ein Umschalten an EINEM Ticker wirkt sich auf ALLE anderen desselben
 * Netzwerks aus. Die tatsächlichen Preise kommen weiterhin aus der EINEN globalen Liste (siehe
 * CentralItemPriceManager). Rein datenhaltend - importiert bewusst keine Create-Klassen, die
 * Aufrufer lösen freqId selbst über StockTickerBlockEntity.behaviour auf. Persistiert als
 * Gson-JSON im Weltordner, gleiches Muster wie ServerRulesManager.
 *
 * Nutzer-Vorgabe (Korrektur, 2x): Lagerticker-Netzwerke verlangen PER DEFAULT KEINE Bezahlung
 * mehr (ursprünglich umgekehrt: alles war per Default bezahlpflichtig, eine "disabledTickers"-
 * Menge listete die Ausnahmen) - jetzt eine "enabledNetworks"-Menge, ein Netzwerk, das noch nie
 * per Strg+Rechtsklick angefasst wurde, ist also automatisch KOSTENLOS. Nur ein echter Minecraft-
 * OP kann das über den Preis-Editor einschalten (siehe CreateStockTickerInteractionHandler/
 * CreateStockTickerPricesUpdatePacket - beide bereits über player.hasPermissions(2) abgesichert).
 */
public class CreateStockTickerPriceManager {

    private static class Data {
        Set<String> enabledNetworks = new HashSet<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Data data = new Data();
    private static Path dataFile;

    public static void init(MinecraftServer server) {
        dataFile = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("cobblecompanion_stock_ticker_prices.json");
        load();
    }

    /** freqId == null (Ticker noch nicht in ein Netzwerk eingebunden) -> immer kostenlos, kein Flag zum Speichern vorhanden. */
    public static boolean isEnabled(UUID freqId) {
        if (freqId == null) return false;
        return data.enabledNetworks.contains(freqId.toString());
    }

    public static void setEnabled(UUID freqId, boolean enabled) {
        if (freqId == null) return;
        String key = freqId.toString();
        if (enabled) data.enabledNetworks.add(key);
        else data.enabledNetworks.remove(key);
        save();
    }

    private static void load() {
        data = new Data();
        if (dataFile == null || !Files.exists(dataFile)) return;
        try (Reader reader = Files.newBufferedReader(dataFile)) {
            Data loaded = GSON.fromJson(reader, Data.class);
            if (loaded != null) data = loaded;
        } catch (IOException ignored) {}
        if (data.enabledNetworks == null) data.enabledNetworks = new HashSet<>();
    }

    private static void save() {
        if (dataFile == null) return;
        try (Writer writer = Files.newBufferedWriter(dataFile)) {
            GSON.toJson(data, writer);
        } catch (IOException ignored) {}
    }
}
