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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Nutzer-Vorgabe: für den Create-Block "Schlauer Beobachter" (create:content_observer) kann
 * AdminOp per Strg+Rechtsklick festlegen, welche Items er tracken soll und wieviel Cobbledollars
 * dafür an welche Spieler ausgezahlt werden - siehe ContentObserverInteractionHandler (GUI-Öffnen
 * + Speichern) und ContentObserverActivateMixin/-BeltRewardMixin/-ChuteRewardMixin/
 * -FunnelTransferMixin (eigentliche Auszahlung bei jeder erkannten Item-Bewegung). Ist ein Block
 * hier konfiguriert, darf sein Filter NICHT mehr von Nicht-AdminOp-Spielern per normalem
 * Rechtsklick verändert werden (siehe ContentObserverInteractionHandler) und der Block selbst
 * nicht mehr abgebaut werden (siehe AdminProtectedBlockHandler). Persistiert als Gson-JSON im
 * Weltordner, gleiches Muster wie PastureBuilderManager.
 *
 * Erweiterung (Nutzer-Vorgabe, mehrere Items pro Block): jeder Block hat jetzt eine LISTE von
 * {@link Rule}s statt eines einzelnen Item/Empfänger/Preis-Tripels. Zeigt der Filter-Slot des
 * Blocks bei mehreren Regeln "create:filter" statt eines echten Items, siehe
 * ContentObserverBridge.setFilter().
 *
 * Erweiterung (Nutzer-Vorgabe, Lagernetzwerk-Preise): der Editor zeigt bei angeschlossenem
 * Lagerverbinder/Lagerticker zusätzlich Ankaufs-/Verkaufspreis des Netzwerks an, editierbar UND
 * direkt zurückschreibbar (siehe ContentObserverNetworkPriceUpdatePacket) - "Nutze Ankaufspreis"/
 * "Nutze Verkaufspreis" kopiert den jeweiligen Wert EINMALIG in {@link Rule#amountPerItem}, keine
 * dauerhaft live nachgeführte Bindung (Nutzer-Vorgabe: Preis soll wählbar UND lokal fest sein).
 *
 * Erweiterung (Nutzer-Vorgabe, verknüpfte Zähler/Abzieher-Gruppen): {@link BlockConfig#groupId}
 * ordnet mehrere Beobachter einer gemeinsamen "Frequenz" zu (gleiches Muster wie Creates eigenes
 * LogisticallyLinkedBehaviour.freqId, siehe ContentObserverGroupItem).
 *
 * Erweiterung (Nutzer-Vorgabe, 3. Live-Test, "geteilte Katalog-Liste ohne Block-Rolle"): eine
 * Gruppe hat EINEN gemeinsamen Item/Empfänger/Preis-Katalog ({@link ContentObserverGroupCatalogManager}),
 * jeder Katalog-Eintrag trägt zwei unabhängige Zugehörigkeits-Flags ("gehört zur Zähler-Liste" /
 * "gehört zur Abzieher-Liste"). Jeder Block wählt per Checkbox UNABHÄNGIG für jede der beiden
 * Listen, welche Katalog-Items er selbst erkennen soll ({@link BlockConfig#enabledCounterItemIds}/
 * {@link BlockConfig#enabledSubtractorItemIds}) - ein einzelner Block kann dadurch gleichzeitig
 * manche Items zählen und andere abziehen. Ein frisch einer Gruppe beigetretener Block startet
 * bewusst mit leeren Auswahlen, nicht automatisch mit dem kompletten Katalog.
 *
 * Bugfix/Klarstellung (Nutzer-Fund, 4. Live-Test): {@link BlockConfig#subtractorBlock} wurde
 * WIEDER eingeführt - NICHT um die Checkbox-Erkennung wieder einzuschränken (das flexible Modell
 * oben bleibt), sondern rein als Klassifizierung dafür, welche Blöcke eine "Aktiv/Inaktiv für alle
 * setzen"-Bulk-Aktion (siehe ContentObserverConfigUpdatePacket) tatsächlich trifft. Ohne diese
 * Unterscheidung würde "Aktiv setzen" für einen in beiden Listen geführten Eintrag reine Abzieher-
 * Blöcke plötzlich auch zum Zählen bringen. Auch {@link #countGroupMembers} nutzt wieder dieses
 * Flag statt "hat mindestens 1 aktiviertes Item" (stabiler/vorhersehbarer).
 */
public class ContentObserverConfigManager {

    public static final class Rule {
        public String itemId;
        public String targetPlayerUuid;
        public long amountPerItem;
    }

    public record GroupCounts(int counters, int subtractors) {}

    public static final class BlockConfig {
        /** NUR relevant, wenn groupId == null - eigener unabhängiger Katalog (Alt-Verhalten, unverändert). Bei groupId != null leer/unbenutzt, siehe Klassenkommentar. */
        public List<Rule> rules = new ArrayList<>();
        /** Nutzer-Vorgabe (Gruppen-Feature): null = nicht Teil einer Zähler/Abzieher-Gruppe. */
        public String groupId;
        /**
         * "Versprechen verfallen nach"-Zeitstufe, gleiche Kodierung wie Creates Fabrikanzeiger-
         * Scrollfeld (siehe ContentObserverGroupExpiryWidget-Kommentar): -1 = nie, 0 = 30 Sekunden,
         * N&gt;=1 = N Minuten.
         */
        public int promiseExpiryStage = 0;
        /** NUR relevant, wenn groupId != null - Item-Pattern-Strings aus dem geteilten Gruppen-Katalog, die DIESER Block als ZÄHLER erkennen soll (siehe Klassenkommentar). */
        public Set<String> enabledCounterItemIds = new HashSet<>();
        /** NUR relevant, wenn groupId != null - Item-Pattern-Strings aus dem geteilten Gruppen-Katalog, die DIESER Block als ABZIEHER erkennen soll (siehe Klassenkommentar). */
        public Set<String> enabledSubtractorItemIds = new HashSet<>();
        /** Reine Klassifizierung für Bulk-Aktionen/Mitgliederzahlen, KEINE Einschränkung der Checkboxen - siehe Klassenkommentar. false = Zähler-Block, true = Abzieher-Block. */
        public boolean subtractorBlock;
    }

    private static final class Data {
        Map<String, BlockConfig> configs = new HashMap<>();
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

    public static BlockConfig get(ResourceKey<Level> dimension, BlockPos pos) {
        return data.configs.get(key(dimension, pos));
    }

    public static boolean isConfigured(ResourceKey<Level> dimension, BlockPos pos) {
        return get(dimension, pos) != null;
    }

    public static void set(ResourceKey<Level> dimension, BlockPos pos, BlockConfig config) {
        data.configs.put(key(dimension, pos), config);
        save();
    }

    public static void remove(ResourceKey<Level> dimension, BlockPos pos) {
        if (data.configs.remove(key(dimension, pos)) != null) save();
    }

    /** Alle BlockConfigs dieser Gruppe (über alle Dimensionen) - LIVE-Referenzen, zum gemeinsamen Bearbeiten (siehe ContentObserverConfigUpdatePacket, "Aktiv/Inaktiv für alle setzen"). Änderungen müssen mit {@link #saveNow()} persistiert werden. */
    public static List<BlockConfig> getGroupConfigs(String groupId) {
        if (groupId == null) return List.of();
        List<BlockConfig> result = new ArrayList<>();
        for (BlockConfig cfg : data.configs.values()) {
            if (groupId.equals(cfg.groupId)) result.add(cfg);
        }
        return result;
    }

    /** Erzwingt ein sofortiges Schreiben - für Bulk-Änderungen über {@link #getGroupConfigs}, die nicht über {@link #set} laufen. */
    public static void saveNow() {
        save();
    }

    /** Wie viele Blöcke (über alle Dimensionen) dieser Gruppe als Zähler- bzw. Abzieher-Block klassifiziert sind (siehe BlockConfig#subtractorBlock). */
    public static GroupCounts countGroupMembers(String groupId) {
        if (groupId == null) return new GroupCounts(0, 0);
        int counters = 0, subtractors = 0;
        for (BlockConfig cfg : data.configs.values()) {
            if (!groupId.equals(cfg.groupId)) continue;
            if (cfg.subtractorBlock) subtractors++; else counters++;
        }
        return new GroupCounts(counters, subtractors);
    }

    /** Positionen aller Blöcke dieser Gruppe IN DER ANGEGEBENEN DIMENSION - für die Grün-Rahmen-Hervorhebung (siehe ContentObserverGroupHighlightSyncPacket). */
    public static List<BlockPos> findGroupPositions(ResourceKey<Level> dimension, String groupId) {
        if (groupId == null) return List.of();
        String dimPrefix = dimension.location() + "|";
        List<BlockPos> result = new ArrayList<>();
        for (Map.Entry<String, BlockConfig> entry : data.configs.entrySet()) {
            if (!groupId.equals(entry.getValue().groupId)) continue;
            String key = entry.getKey();
            if (!key.startsWith(dimPrefix)) continue;
            String[] coords = key.substring(dimPrefix.length()).split(",");
            if (coords.length != 3) continue;
            try {
                result.add(new BlockPos(Integer.parseInt(coords[0]), Integer.parseInt(coords[1]), Integer.parseInt(coords[2])));
            } catch (NumberFormatException ignored) {}
        }
        return result;
    }

    /** Die für DIESEN Block als ZÄHLER wirksamen Regeln - bei groupId==null die eigene Liste (Alt-Verhalten), sonst der geteilte Katalog gefiltert auf enabledCounterItemIds UND inCounterList. */
    public static List<Rule> effectiveCounterRules(BlockConfig cfg) {
        if (cfg == null) return List.of();
        if (cfg.groupId == null) return cfg.rules != null ? cfg.rules : List.of();
        if (cfg.enabledCounterItemIds == null || cfg.enabledCounterItemIds.isEmpty()) return List.of();
        List<Rule> result = new ArrayList<>();
        for (ContentObserverGroupCatalogManager.CatalogEntry entry : ContentObserverGroupCatalogManager.getCatalog(cfg.groupId)) {
            if (entry.inCounterList && cfg.enabledCounterItemIds.contains(entry.itemId)) result.add(toRule(entry));
        }
        return result;
    }

    /** Die für DIESEN Block als ABZIEHER wirksamen Regeln - NUR für gruppierte Blöcke (Abziehen ohne Gruppe ergibt keinen Sinn), gefiltert auf enabledSubtractorItemIds UND inSubtractorList. */
    public static List<Rule> effectiveSubtractorRules(BlockConfig cfg) {
        if (cfg == null || cfg.groupId == null) return List.of();
        if (cfg.enabledSubtractorItemIds == null || cfg.enabledSubtractorItemIds.isEmpty()) return List.of();
        List<Rule> result = new ArrayList<>();
        for (ContentObserverGroupCatalogManager.CatalogEntry entry : ContentObserverGroupCatalogManager.getCatalog(cfg.groupId)) {
            if (entry.inSubtractorList && cfg.enabledSubtractorItemIds.contains(entry.itemId)) result.add(toRule(entry));
        }
        return result;
    }

    private static Rule toRule(ContentObserverGroupCatalogManager.CatalogEntry entry) {
        Rule rule = new Rule();
        rule.itemId = entry.itemId;
        rule.targetPlayerUuid = entry.targetPlayerUuid;
        rule.amountPerItem = entry.amountPerItem;
        return rule;
    }

    /** Erste zu stack passende Regel aus rules, oder null - siehe {@link #matches}. */
    public static Rule findMatchingRule(List<Rule> rules, ItemStack stack) {
        if (rules == null) return null;
        for (Rule rule : rules) {
            if (matches(rule.itemId, stack)) return rule;
        }
        return null;
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

    /** Einmalige Migration, siehe com.cobblecompanion.cobbledollarscreate.data.CentralItemPriceManager.scaleAllPricesBy10(). */
    public static void scaleAmountsBy10() {
        for (BlockConfig cfg : data.configs.values()) {
            for (Rule rule : cfg.rules) rule.amountPerItem *= 10;
        }
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
            // Bugfix (Live-Fund, Server-Crash): siehe ContentObserverGroupCatalogManager#load -
            // ein inkompatibles/beschädigtes Format darf den Server-Start nie verhindern.
            CobbleCompanionDollarsCreate.LOGGER.warn(
                "[CC] {} ließ sich nicht lesen (inkompatibles/beschädigtes Format) - starte mit leeren Schlauer-Beobachter-Konfigurationen neu.", dataFile, e);
            data = new Data();
        }
        if (data.configs == null) data.configs = new HashMap<>();
        for (BlockConfig cfg : data.configs.values()) {
            if (cfg.rules == null) cfg.rules = new ArrayList<>();
            if (cfg.enabledCounterItemIds == null) cfg.enabledCounterItemIds = new HashSet<>();
            if (cfg.enabledSubtractorItemIds == null) cfg.enabledSubtractorItemIds = new HashSet<>();
        }
    }

    private static void save() {
        if (dataFile == null) return;
        try (Writer writer = Files.newBufferedWriter(dataFile)) {
            GSON.toJson(data, writer);
        } catch (IOException ignored) {}
    }
}
