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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Nutzer-Vorgabe: Verkaufserlöse aus einem Lagerticker-Netzwerk (CobbleMerchant/CustomNPC-Trader)
 * sollen nicht mehr einfach verschwinden, sondern einem konfigurierbaren Empfänger gutgeschrieben
 * werden - entweder EIN Spieler für das gesamte Netzwerk (SINGLE), oder pro Entity einzeln
 * zugewiesen (VARIES). Fehlt ein Eintrag in networkMode, gilt NONE (unverändertes Altverhalten).
 * Gleiches flaches Map<String,X>-Persistenzmuster wie CentralItemPriceManager/CobbleMerchantSellManager.
 */
public final class CobbleMerchantPayoutManager {

    public static final String MODE_NONE = "NONE";
    public static final String MODE_SINGLE = "SINGLE";
    public static final String MODE_VARIES = "VARIES";

    private static class Data {
        Map<String, String> networkMode = new HashMap<>();
        Map<String, String> networkRecipient = new HashMap<>();
        Map<String, String> entityRecipient = new HashMap<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Data data = new Data();
    private static Path dataFile;

    private CobbleMerchantPayoutManager() {}

    public static void init(MinecraftServer server) {
        dataFile = server.getWorldPath(LevelResource.ROOT).resolve("cobblecompanion_merchant_payouts.json");
        load();
    }

    public static String getMode(UUID freqId) {
        if (freqId == null) return MODE_NONE;
        return data.networkMode.getOrDefault(freqId.toString(), MODE_NONE);
    }

    public static void setNetworkPayout(UUID freqId, String mode, UUID recipientUuid) {
        if (freqId == null) return;
        String key = freqId.toString();
        if (MODE_NONE.equals(mode)) {
            data.networkMode.remove(key);
            data.networkRecipient.remove(key);
        } else {
            data.networkMode.put(key, mode);
            if (MODE_SINGLE.equals(mode) && recipientUuid != null) {
                data.networkRecipient.put(key, recipientUuid.toString());
            } else if (MODE_SINGLE.equals(mode)) {
                data.networkRecipient.remove(key);
            }
        }
        save();
    }

    public static UUID getNetworkRecipient(UUID freqId) {
        if (freqId == null) return null;
        String value = data.networkRecipient.get(freqId.toString());
        return value == null ? null : UUID.fromString(value);
    }

    public static void setEntityRecipient(UUID entityUuid, UUID recipientUuid) {
        if (entityUuid == null) return;
        if (recipientUuid == null) {
            data.entityRecipient.remove(entityUuid.toString());
        } else {
            data.entityRecipient.put(entityUuid.toString(), recipientUuid.toString());
        }
        save();
    }

    public static UUID getEntityRecipient(UUID entityUuid) {
        if (entityUuid == null) return null;
        String value = data.entityRecipient.get(entityUuid.toString());
        return value == null ? null : UUID.fromString(value);
    }

    /** Zentrale Auflösung für den Kauf-Hook (siehe BuyHandlerMixin): null = kein Empfänger konfiguriert, Geld verhält sich wie bisher. */
    public static UUID resolveRecipient(UUID freqId, UUID entityUuid) {
        String mode = getMode(freqId);
        return switch (mode) {
            case MODE_SINGLE -> getNetworkRecipient(freqId);
            case MODE_VARIES -> getEntityRecipient(entityUuid);
            default -> null;
        };
    }

    private static void load() {
        data = new Data();
        if (dataFile == null || !Files.exists(dataFile)) return;
        try (Reader reader = Files.newBufferedReader(dataFile)) {
            Data loaded = GSON.fromJson(reader, Data.class);
            if (loaded != null) data = loaded;
        } catch (IOException ignored) {}
        if (data.networkMode == null) data.networkMode = new HashMap<>();
        if (data.networkRecipient == null) data.networkRecipient = new HashMap<>();
        if (data.entityRecipient == null) data.entityRecipient = new HashMap<>();
    }

    private static void save() {
        if (dataFile == null) return;
        try (Writer writer = Files.newBufferedWriter(dataFile)) {
            GSON.toJson(data, writer);
        } catch (IOException ignored) {}
    }
}
