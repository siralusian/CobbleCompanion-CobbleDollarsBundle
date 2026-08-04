package com.cobblecompanion.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Nutzer-Vorgabe: CustomNPCs-Trader-NPCs sollen Items nur verkaufen können, wenn der NPC (als
 * Ganzes, wie beim CobbleMerchant) mit einem Lagerticker verknüpft ist UND die benötigte Menge im
 * Netzwerk dieses Tickers vorhanden ist - siehe integrations.customnpcs.mixin für die eigentliche
 * Kauf-Logik (Lagerprüfung + echtes Entnehmen aus dem Netzwerk). Die Bezahl-Items landen (wie beim
 * CobbleMerchant, siehe CobbleMerchantSellManager) in einer optional zusätzlich verknüpften Kiste
 * statt gelöscht zu werden; ohne Kisten-Verknüpfung (oder wenn sie voll ist) fallen sie stattdessen
 * am NPC zu Boden.
 *
 * Rein datenhaltend, gleiches Persistenz-/Target-Muster wie CobbleMerchantSellManager - bewusst
 * NICHT dieselbe Klasse wiederverwendet, um die beiden optionalen Integrationen (CobbleDollars vs.
 * CustomNPCs) unabhängig voneinander zu halten.
 */
public class CustomNpcTraderLinkManager {

    public record Target(String dimension, int x, int y, int z) {
        public BlockPos pos() { return new BlockPos(x, y, z); }
    }

    private static class Data {
        Map<String, Target> tickerLinks = new HashMap<>();
        Map<String, Target> storageLinks = new HashMap<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Data data = new Data();
    private static Path dataFile;

    public static void init(MinecraftServer server) {
        dataFile = server.getWorldPath(LevelResource.ROOT).resolve("cobblecompanion_customnpcs_trader_links.json");
        load();
    }

    public static void setTickerLink(UUID npcUuid, ResourceKey<Level> dimension, BlockPos pos) {
        data.tickerLinks.put(npcUuid.toString(), new Target(dimension.location().toString(), pos.getX(), pos.getY(), pos.getZ()));
        save();
    }

    public static Target getTickerLink(UUID npcUuid) {
        return data.tickerLinks.get(npcUuid.toString());
    }

    public static void setStorageLink(UUID npcUuid, ResourceKey<Level> dimension, BlockPos pos) {
        data.storageLinks.put(npcUuid.toString(), new Target(dimension.location().toString(), pos.getX(), pos.getY(), pos.getZ()));
        save();
    }

    public static Target getStorageLink(UUID npcUuid) {
        return data.storageLinks.get(npcUuid.toString());
    }

    /** Unveränderliche Kopie aller Ticker-Verknüpfungen (NPC-UUID -> Ziel) - für das Verknüpfte-NPCs-Panel im Preis-Editor. */
    public static Map<UUID, Target> getAllTickerLinks() {
        Map<UUID, Target> result = new HashMap<>();
        for (Map.Entry<String, Target> entry : data.tickerLinks.entrySet()) {
            result.put(UUID.fromString(entry.getKey()), entry.getValue());
        }
        return result;
    }

    /** Wie getAllTickerLinks(), aber für die Kisten-Verknüpfung. */
    public static Map<UUID, Target> getAllStorageLinks() {
        Map<UUID, Target> result = new HashMap<>();
        for (Map.Entry<String, Target> entry : data.storageLinks.entrySet()) {
            result.put(UUID.fromString(entry.getKey()), entry.getValue());
        }
        return result;
    }

    public static void removeTickerLink(UUID npcUuid) {
        data.tickerLinks.remove(npcUuid.toString());
        save();
    }

    public static void removeStorageLink(UUID npcUuid) {
        data.storageLinks.remove(npcUuid.toString());
        save();
    }

    /** Fügt currency in die verknüpfte Kiste (falls vorhanden) ein - Rest/kein Link -> am Boden bei dropPos fallen lassen. */
    public static void depositOrDrop(ServerLevel level, UUID npcUuid, ItemStack currency, BlockPos dropPos) {
        if (currency.isEmpty()) return;
        Target target = getStorageLink(npcUuid);
        IItemHandler handler = null;
        ServerLevel targetLevel = level;
        if (target != null) {
            ResourceKey<Level> dimensionKey = ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                net.minecraft.resources.ResourceLocation.parse(target.dimension()));
            ServerLevel resolvedLevel = level.getServer().getLevel(dimensionKey);
            if (resolvedLevel != null) {
                targetLevel = resolvedLevel;
                handler = findItemHandler(resolvedLevel, target.pos());
            }
        }

        ItemStack remainder = handler != null
            ? net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(handler, currency.copy(), false)
            : currency.copy();
        if (!remainder.isEmpty()) {
            level.addFreshEntity(new ItemEntity(level,
                dropPos.getX() + 0.5, dropPos.getY() + 0.5, dropPos.getZ() + 0.5, remainder));
        }
    }

    /**
     * Erst ohne bestimmte Seite (funktioniert für die meisten Vanilla-Inventare), dann alle 6
     * Seiten durchprobieren - manche (v.a. modded) Blöcke registrieren ihre IItemHandler-
     * Capability nur für bestimmte Seiten und liefern bei null-Kontext nichts zurück.
     */
    public static IItemHandler findItemHandler(ServerLevel level, BlockPos pos) {
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        if (handler != null) return handler;
        for (Direction side : Direction.values()) {
            handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
            if (handler != null) return handler;
        }
        return null;
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
        if (data.tickerLinks == null) data.tickerLinks = new HashMap<>();
        if (data.storageLinks == null) data.storageLinks = new HashMap<>();
    }

    private static void save() {
        if (dataFile == null) return;
        try (Writer writer = Files.newBufferedWriter(dataFile)) {
            GSON.toJson(data, writer);
        } catch (IOException ignored) {}
    }
}
