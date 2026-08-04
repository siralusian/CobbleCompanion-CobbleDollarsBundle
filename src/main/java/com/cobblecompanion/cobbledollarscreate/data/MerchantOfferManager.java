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
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Individuelles Verkaufs-Angebot pro CobbleMerchant/CustomNPC-Trader (siehe
 * PlayerExtensionKtOpenShopMixin) - Nutzer-Vorgabe: standardmäßig bietet ein verknüpfter Merchant
 * das GESAMTE Angebot seines Lagerticker-Netzwerks an; hier lässt sich das pro Merchant auf eine
 * feste Item-Auswahl einschränken ("Eigenes Angebot" statt "Lagerticker Angebot", siehe
 * MerchantOfferEditScreen). Rein datenhaltend, gleiches Persistenz-Muster wie
 * CobbleMerchantSellManager.
 */
public final class MerchantOfferManager {

    private static class Data {
        // Nur Einträge mit active=true schränken den Shop tatsächlich ein (siehe
        // PlayerExtensionKtOpenShopMixin) - ein deaktivierter Eintrag bleibt gespeichert, damit die
        // zuletzt gepflegte Auswahl beim erneuten Umschalten auf "Eigenes Angebot" nicht verloren geht.
        Map<String, Boolean> active = new HashMap<>();
        Map<String, Set<String>> offers = new HashMap<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Data data = new Data();
    private static Path dataFile;

    private MerchantOfferManager() {}

    public static void init(MinecraftServer server) {
        dataFile = server.getWorldPath(LevelResource.ROOT).resolve("cobblecompanion_merchant_custom_offers.json");
        load();
    }

    public static boolean isCustomOfferActive(UUID merchantUuid) {
        return data.active.getOrDefault(merchantUuid.toString(), false);
    }

    public static Set<String> getCustomOffer(UUID merchantUuid) {
        return new LinkedHashSet<>(data.offers.getOrDefault(merchantUuid.toString(), Set.of()));
    }

    public static void setCustomOffer(UUID merchantUuid, Set<String> itemIds) {
        data.offers.put(merchantUuid.toString(), new LinkedHashSet<>(itemIds));
        save();
    }

    public static void setCustomOfferActive(UUID merchantUuid, boolean active) {
        data.active.put(merchantUuid.toString(), active);
        save();
    }

    /** @return die Item-Einschränkung, die PlayerExtensionKtOpenShopMixin anwenden soll - null bedeutet "kein Filter, volles Netzwerk-Angebot". */
    public static Set<String> getActiveRestriction(UUID merchantUuid) {
        return isCustomOfferActive(merchantUuid) ? getCustomOffer(merchantUuid) : null;
    }

    private static void load() {
        data = new Data();
        if (dataFile == null || !Files.exists(dataFile)) return;
        try (Reader reader = Files.newBufferedReader(dataFile)) {
            Data loaded = GSON.fromJson(reader, Data.class);
            if (loaded != null) data = loaded;
        } catch (IOException | com.google.gson.JsonParseException ignored) {
            data = new Data();
        }
        if (data.active == null) data.active = new HashMap<>();
        if (data.offers == null) data.offers = new HashMap<>();
    }

    private static void save() {
        if (dataFile == null) return;
        try (Writer writer = Files.newBufferedWriter(dataFile)) {
            GSON.toJson(data, writer);
        } catch (IOException ignored) {}
    }
}
