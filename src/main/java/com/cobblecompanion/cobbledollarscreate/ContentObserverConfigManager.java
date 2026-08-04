package com.cobblecompanion.cobbledollarscreate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Nutzer-Vorgabe: für den Create-Block "Schlauer Beobachter" (create:content_observer) kann
 * AdminOp per Strg+Rechtsklick festlegen, welches Item er tracken soll und wieviel Cobbledollars
 * dafür an welchen Spieler ausgezahlt werden - siehe ContentObserverInteractionHandler (GUI-Öffnen
 * + Speichern, setzt den Filter ZUSÄTZLICH direkt auf der echten FilteringBehaviour, damit er ganz
 * normal in der Beobachter-eigenen Anzeige auftaucht) und ContentObserverActivateMixin (eigentliche
 * Auszahlung bei jeder erkannten Item-Bewegung). Ist ein Block hier konfiguriert, darf sein Filter
 * NICHT mehr von Nicht-AdminOp-Spielern per normalem Rechtsklick verändert werden (siehe
 * ContentObserverInteractionHandler) und der Block selbst nicht mehr abgebaut werden (siehe
 * AdminProtectedBlockHandler). Persistiert als Gson-JSON im Weltordner, gleiches Muster wie
 * PastureBuilderManager.
 */
public class ContentObserverConfigManager {

    public static final class Entry {
        public String itemId;
        public String targetPlayerUuid;
        public long amountPerItem;
    }

    private static final class Data {
        Map<String, Entry> configs = new HashMap<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Data data = new Data();
    private static Path dataFile;

    public static void init(MinecraftServer server) {
        dataFile = server.getWorldPath(LevelResource.ROOT).resolve("cobblecompanion_content_observer.json");
        load();
    }

    public static String key(ResourceKey<Level> dimension, BlockPos pos) {
        return dimension.location() + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    public static Entry get(ResourceKey<Level> dimension, BlockPos pos) {
        return data.configs.get(key(dimension, pos));
    }

    public static boolean isConfigured(ResourceKey<Level> dimension, BlockPos pos) {
        return get(dimension, pos) != null;
    }

    public static void set(ResourceKey<Level> dimension, BlockPos pos, Entry entry) {
        data.configs.put(key(dimension, pos), entry);
        save();
    }

    public static void remove(ResourceKey<Level> dimension, BlockPos pos) {
        if (data.configs.remove(key(dimension, pos)) != null) save();
    }

    /**
     * Nutzer-Vorgabe: prüft, ob stack zum konfigurierten Item-Muster passt - akzeptiert neben
     * einer konkreten Item-ID zusätzlich: leer oder "*" (JEDES Item), einen Namespace-Wildcard
     * wie "minecraft:*" (alle Items dieses Namespace) und einen Tag-Verweis wie "#minecraft:logs"
     * (alle Items mit diesem Tag). Create's eigene FilteringBehaviour kennt nur konkrete
     * Einzel-Items - für die drei nicht-konkreten Muster übernehmen die Reward-Mixins deshalb
     * diese Methode statt filtering.test(stack) (siehe deren Klassenkommentare).
     */
    public static boolean matches(String pattern, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (pattern == null || pattern.isBlank() || pattern.equals("*")) return true;

        if (pattern.startsWith("#")) {
            ResourceLocation tagRl = ResourceLocation.tryParse(pattern.substring(1));
            if (tagRl == null) return false;
            return stack.is(TagKey.create(Registries.ITEM, tagRl));
        }

        if (pattern.endsWith(":*")) {
            String namespace = pattern.substring(0, pattern.length() - 2);
            return BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals(namespace);
        }

        ResourceLocation patternRl = ResourceLocation.tryParse(pattern);
        if (patternRl == null) return false;
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(patternRl);
    }

    /** Einmalige Migration, siehe com.cobblecompanion.data.CentralItemPriceManager.scaleAllPricesBy10(). */
    public static void scaleAmountsBy10() {
        for (Entry entry : data.configs.values()) entry.amountPerItem *= 10;
        save();
    }

    private static void load() {
        data = new Data();
        if (dataFile == null || !Files.exists(dataFile)) return;
        try (Reader reader = Files.newBufferedReader(dataFile)) {
            Data loaded = GSON.fromJson(reader, Data.class);
            if (loaded != null) data = loaded;
        } catch (IOException ignored) {}
        if (data.configs == null) data.configs = new HashMap<>();
    }

    private static void save() {
        if (dataFile == null) return;
        try (Writer writer = Files.newBufferedWriter(dataFile)) {
            GSON.toJson(data, writer);
        } catch (IOException ignored) {}
    }
}
