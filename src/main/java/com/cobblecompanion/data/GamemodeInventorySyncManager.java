package com.cobblecompanion.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ersetzt das externe InvSync-Datapack: pro Spieler und Gamemode (Survival/Creative/Adventure/
 * Spectator) wird ein eigenes Hauptinventar (inkl. Rüstung/Offhand) und - falls Curios installiert
 * ist - ein eigenes Curios-Inventar gespeichert. Persistenz nach dem robusten
 * PastureBuilderManager-Muster (atomarer Schreibvorgang, korruptionstolerantes Laden - ein
 * beschädigtes File darf nie den ganzen Server am Booten hindern).
 *
 * Reihenfolge-Garantie gegen Datenverlust (Root Cause des früheren Datapack-Bugs): beim Wechsel
 * wird IMMER zuerst das alte Inventar persistiert, DANN erst geleert, DANN erst das neue geladen.
 * Schlägt ein Schritt fehl, wird abgebrochen statt das Inventar des Spielers zu leeren.
 */
public final class GamemodeInventorySyncManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(GamemodeInventorySyncManager.class);

    public static final class ReclaimEntry {
        public String gameTypeName;
        public String inventoryB64;
        public String curiosB64;
        public long timestampMillis;

        public ReclaimEntry() {}

        public ReclaimEntry(String gameTypeName, String inventoryB64, String curiosB64) {
            this.gameTypeName = gameTypeName;
            this.inventoryB64 = inventoryB64;
            this.curiosB64 = curiosB64;
            this.timestampMillis = System.currentTimeMillis();
        }
    }

    private static final class PlayerInventories {
        Map<String, String> inventoryByGameType = new HashMap<>();
        Map<String, String> curiosByGameType = new HashMap<>();
    }

    private static final class Data {
        Boolean enabled = false;
        Boolean migrationDone = false;
        Map<String, PlayerInventories> players = new HashMap<>();
        Map<String, List<ReclaimEntry>> reclaimQueue = new HashMap<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Data data = new Data();
    private static Path dataFile;

    private GamemodeInventorySyncManager() {}

    public static void init(MinecraftServer server) {
        dataFile = server.getWorldPath(LevelResource.ROOT).resolve("cobblecompanion_gamemode_inventory.json");
        load();
    }

    public static boolean isEnabled() {
        return data.enabled != null && data.enabled;
    }

    public static void setEnabled(boolean enabled) {
        data.enabled = enabled;
        save();
    }

    /**
     * Admin schaltet die Funktion aus (oder setzt einen einzelnen Spieler zurück): alle
     * gespeicherten Gamemode-Inventare AUSSER dem gerade aktiven wandern in die Abhol-
     * Warteschlange statt verloren zu gehen (siehe orphanAllPlayers/orphanAllExcept).
     */
    public static void disableWithOrphanHandling(MinecraftServer server) {
        orphanAllPlayers(server);
        setEnabled(false);
    }

    public static void resetPlayerWithOrphanHandling(MinecraftServer server, UUID uuid) {
        GameType current = resolveCurrentGameType(server, uuid);
        orphanAllExcept(uuid, current);
    }

    public static boolean isMigrationDone() {
        return data.migrationDone != null && data.migrationDone;
    }

    public static void setMigrationDone(boolean done) {
        data.migrationDone = done;
        save();
    }

    // ---- Pro-Gamemode-Inventar-Speicherung ----

    private static PlayerInventories getOrCreate(String uuid) {
        return data.players.computeIfAbsent(uuid, k -> new PlayerInventories());
    }

    public static boolean hasStoredInventory(UUID uuid, GameType gameType) {
        PlayerInventories inv = data.players.get(uuid.toString());
        return inv != null && inv.inventoryByGameType.containsKey(gameType.getName());
    }

    /** Speichert das Hauptinventar (Haupt+Rüstung+Offhand, ein einziges NBT-Tag) für den gegebenen Gamemode. */
    public static void storeInventory(UUID uuid, GameType gameType, ListTag inventoryTag) {
        getOrCreate(uuid.toString()).inventoryByGameType.put(gameType.getName(), encode(inventoryTag));
        save();
    }

    public static void storeCurios(UUID uuid, GameType gameType, ListTag curiosTag) {
        getOrCreate(uuid.toString()).curiosByGameType.put(gameType.getName(), encode(curiosTag));
        save();
    }

    /** Nur setzen, wenn noch nichts für diesen Gamemode vorhanden ist - für die einmalige Migration. */
    public static boolean storeInventoryIfAbsent(UUID uuid, GameType gameType, ListTag inventoryTag) {
        PlayerInventories inv = getOrCreate(uuid.toString());
        if (inv.inventoryByGameType.containsKey(gameType.getName())) return false;
        inv.inventoryByGameType.put(gameType.getName(), encode(inventoryTag));
        save();
        return true;
    }

    public static ListTag loadInventory(UUID uuid, GameType gameType) {
        PlayerInventories inv = data.players.get(uuid.toString());
        if (inv == null) return null;
        String b64 = inv.inventoryByGameType.get(gameType.getName());
        return b64 == null ? null : decode(b64);
    }

    public static ListTag loadCurios(UUID uuid, GameType gameType) {
        PlayerInventories inv = data.players.get(uuid.toString());
        if (inv == null) return null;
        String b64 = inv.curiosByGameType.get(gameType.getName());
        return b64 == null ? null : decode(b64);
    }

    /** Entfernt den gespeicherten Eintrag für einen Gamemode (nachdem er in die Abhol-Warteschlange verschoben wurde). */
    public static void clearStored(UUID uuid, GameType gameType) {
        PlayerInventories inv = data.players.get(uuid.toString());
        if (inv == null) return;
        inv.inventoryByGameType.remove(gameType.getName());
        inv.curiosByGameType.remove(gameType.getName());
        save();
    }

    public static Map<String, PlayerInventoriesView> allStoredInventories() {
        Map<String, PlayerInventoriesView> result = new HashMap<>();
        for (Map.Entry<String, PlayerInventories> e : data.players.entrySet()) {
            result.put(e.getKey(), new PlayerInventoriesView(e.getValue()));
        }
        return result;
    }

    /** Nur-Lese-Sicht für den Migrations-/Admin-Code, ohne die interne PlayerInventories-Klasse offenzulegen. */
    public static final class PlayerInventoriesView {
        private final PlayerInventories delegate;
        private PlayerInventoriesView(PlayerInventories delegate) { this.delegate = delegate; }
        public boolean hasGameType(String gameTypeName) { return delegate.inventoryByGameType.containsKey(gameTypeName); }
    }

    // ---- Abhol-Warteschlange (bei Deaktivieren/Reset entstehende verwaiste Inventare) ----

    public static List<ReclaimEntry> getReclaimEntries(UUID uuid) {
        return data.reclaimQueue.getOrDefault(uuid.toString(), List.of());
    }

    private static void addReclaimEntry(UUID uuid, ReclaimEntry entry) {
        data.reclaimQueue.computeIfAbsent(uuid.toString(), k -> new ArrayList<>()).add(entry);
    }

    /** Für die Client-Sync-Anzeige: Gesamtanzahl der Items im gespeicherten Inventar eines Reclaim-Eintrags. */
    public static int countItems(ReclaimEntry entry) {
        ListTag tag = decode(entry.inventoryB64);
        int total = 0;
        for (int i = 0; i < tag.size(); i++) {
            CompoundTag stackTag = tag.getCompound(i);
            total += stackTag.contains("count") ? stackTag.getInt("count") : 1;
        }
        // Rückwärtskompatibilität: ältere, vor dem Curios-Flatten-Fix erstellte Einträge können
        // noch das alte, separate (kaputte) curiosB64-Feld gesetzt haben.
        if (entry.curiosB64 != null) {
            ListTag curiosFlat = flattenCurios(decode(entry.curiosB64));
            for (int i = 0; i < curiosFlat.size(); i++) {
                CompoundTag stackTag = curiosFlat.getCompound(i);
                total += stackTag.contains("count") ? stackTag.getInt("count") : 1;
            }
        }
        return total;
    }

    public static void removeReclaimEntry(UUID uuid, int index) {
        List<ReclaimEntry> list = data.reclaimQueue.get(uuid.toString());
        if (list == null || index < 0 || index >= list.size()) return;
        list.remove(index);
        if (list.isEmpty()) data.reclaimQueue.remove(uuid.toString());
        save();
    }

    public enum ClaimResult { NOT_FOUND, FULL, PARTIAL }

    /**
     * Fügt so viele Items wie möglich in das AKTUELLE Inventar des Spielers ein (vanilla-Merge-
     * Logik über Inventory.add, unterstützt Teil-Einfügung). Nicht unterbringbare Reste bleiben
     * unverändert im Eintrag stehen (kein Zeitlimit, kein Verfall - siehe Klassenkommentar). Aus
     * Vereinfachungsgründen werden auch eventuell mitgespeicherte Curios-Items in das normale
     * Inventar zurückgegeben (nicht automatisch wieder angelegt) - der Spieler kann sie von dort
     * aus selbst erneut ausrüsten.
     */
    public static ClaimResult claim(ServerPlayer player, int index) {
        List<ReclaimEntry> list = data.reclaimQueue.get(player.getUUID().toString());
        if (list == null || index < 0 || index >= list.size()) return ClaimResult.NOT_FOUND;
        ReclaimEntry entry = list.get(index);

        ListTag leftover = new ListTag();
        boolean anyLeftover = false;
        anyLeftover |= mergeIntoInventory(player, decode(entry.inventoryB64), leftover);
        // Rückwärtskompatibilität: ältere, vor dem Curios-Flatten-Fix erstellte Einträge (siehe
        // flattenCurios-Kommentar) können noch das alte, separate curiosB64-Feld gesetzt haben.
        if (entry.curiosB64 != null) {
            anyLeftover |= mergeIntoInventory(player, flattenCurios(decode(entry.curiosB64)), leftover);
        }

        if (anyLeftover) {
            list.set(index, new ReclaimEntry(entry.gameTypeName, encode(leftover), null));
        } else {
            list.remove(index);
            if (list.isEmpty()) data.reclaimQueue.remove(player.getUUID().toString());
        }
        save();
        return anyLeftover ? ClaimResult.PARTIAL : ClaimResult.FULL;
    }

    /** Gibt true zurück, wenn mindestens ein Stack nicht vollständig unterkam (in leftoverOut gesammelt). */
    private static boolean mergeIntoInventory(ServerPlayer player, ListTag stacks, ListTag leftoverOut) {
        boolean anyLeftover = false;
        for (int i = 0; i < stacks.size(); i++) {
            CompoundTag stackTag = stacks.getCompound(i);
            ItemStack stack = ItemStack.parse(player.registryAccess(), stackTag).orElse(ItemStack.EMPTY);
            if (stack.isEmpty()) continue;
            player.getInventory().add(stack);
            if (!stack.isEmpty()) {
                leftoverOut.add(stack.save(player.registryAccess()));
                anyLeftover = true;
            }
        }
        return anyLeftover;
    }

    /**
     * Verschiebt alle gespeicherten Gamemode-Inventare eines Spielers - AUSSER dem als "aktuell
     * aktiv" übergebenen GameType (dessen Inhalt lebt bereits im echten Spieler-Inventar, ein
     * erneutes Verschieben würde die Items duplizieren) - in die Abhol-Warteschlange und entfernt
     * sie aus dem Gamemode-Speicher.
     */
    public static void orphanAllExcept(UUID uuid, GameType currentGameType) {
        PlayerInventories inv = data.players.get(uuid.toString());
        if (inv == null) return;
        // Vereinigung beider Schlüsselmengen - ein Gamemode kann (selten) nur Curios-Daten ohne
        // eigenen Hauptinventar-Eintrag haben (z.B. wenn dort nie etwas ins Hauptinventar kam).
        java.util.Set<String> gameTypes = new java.util.LinkedHashSet<>(inv.inventoryByGameType.keySet());
        gameTypes.addAll(inv.curiosByGameType.keySet());
        for (String gameTypeName : gameTypes) {
            if (currentGameType != null && gameTypeName.equals(currentGameType.getName())) continue;
            String inventoryB64 = inv.inventoryByGameType.remove(gameTypeName);
            String curiosB64 = inv.curiosByGameType.remove(gameTypeName);

            // Curios-Items sofort in ein flaches, normales Item-Listenformat umwandeln (siehe
            // flattenCurios-Kommentar) - dadurch muss claim()/countItems() das verschachtelte
            // Curios-NBT-Format nie wieder anfassen, nur noch die im Rest des Systems ohnehin
            // schon genutzte flache Liste.
            ListTag combined = inventoryB64 != null ? decode(inventoryB64) : new ListTag();
            if (curiosB64 != null) {
                combined.addAll(flattenCurios(decode(curiosB64)));
            }
            if (!combined.isEmpty()) {
                addReclaimEntry(uuid, new ReclaimEntry(gameTypeName, encode(combined), null));
            }
        }
        save();
    }

    /**
     * Nutzer-Fund (Live-Test): eine ausgerüstete Create-Brille im Curios-Kopf-Slot wurde als
     * "1 Item abzuholen" angezeigt, aber beim Abholen nie tatsächlich gegeben - Root Cause: Curios'
     * eigenes ListTag-Format (CurioInventoryCapability#saveInventory) ist NICHT flach wie beim
     * Hauptinventar, sondern eine Liste PRO SLOT-TYP mit je einem "Identifier", "Stacks" (selbst
     * wieder ein ItemStackHandler-NBT mit "Items"-Liste der echten Stacks) und optional
     * "Cosmetics" (gleiche Struktur, kosmetische Items). ItemStack.parse() direkt auf so einen
     * Eintrag lieferte lautlos Optional.empty() (kein "id"/"count" auf dieser Ebene), das Item
     * wurde dadurch als "leer" übersprungen UND aus der Warteschlange entfernt - echter
     * Datenverlust. Diese Methode entpackt die echten Stacks aus "Stacks.Items" (+ "Cosmetics.Items"
     * falls vorhanden) in eine flache Liste, exakt wie ItemStackHandler.serializeNBT sie ablegt
     * (jeder Eintrag hat "Slot" + die normalen ItemStack-Felder zusammen in einem Compound - von
     * ItemStack.parse() dieselbe Toleranz wie bei Inventory.load() vorausgesetzt).
     */
    private static ListTag flattenCurios(ListTag curiosTopLevelList) {
        ListTag flat = new ListTag();
        for (int i = 0; i < curiosTopLevelList.size(); i++) {
            CompoundTag slotTypeEntry = curiosTopLevelList.getCompound(i);
            appendHandlerItems(slotTypeEntry.getCompound("Stacks"), flat);
            if (slotTypeEntry.contains("Cosmetics")) {
                appendHandlerItems(slotTypeEntry.getCompound("Cosmetics"), flat);
            }
        }
        return flat;
    }

    private static void appendHandlerItems(CompoundTag itemStackHandlerTag, ListTag flatOut) {
        if (!itemStackHandlerTag.contains("Items")) return;
        ListTag items = itemStackHandlerTag.getList("Items", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            flatOut.add(items.getCompound(i));
        }
    }

    /** Für ALLE bekannten Spieler aufgerufen, wenn die Funktion serverweit deaktiviert wird. */
    public static void orphanAllPlayers(MinecraftServer server) {
        for (String uuidStr : new ArrayList<>(data.players.keySet())) {
            UUID uuid = UUID.fromString(uuidStr);
            GameType current = resolveCurrentGameType(server, uuid);
            orphanAllExcept(uuid, current);
        }
    }

    /** Aktueller Gamemode eines Spielers - live falls online, sonst aus der Spieler-NBT-Datei gelesen. */
    public static GameType resolveCurrentGameType(MinecraftServer server, UUID uuid) {
        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online != null) return online.gameMode.getGameModeForPlayer();
        try {
            Path playerFile = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(uuid + ".dat");
            if (!Files.exists(playerFile)) return null;
            CompoundTag tag = NbtIo.readCompressed(playerFile, NbtAccounter.unlimitedHeap());
            if (!tag.contains("playerGameType")) return null;
            return GameType.byId(tag.getInt("playerGameType"));
        } catch (IOException e) {
            LOGGER.warn("[CC-GamemodeInventory] Konnte Gamemode für Offline-Spieler {} nicht aus Spieler-Datei lesen", uuid, e);
            return null;
        }
    }

    // ---- NBT <-> Base64 (gleiches Muster wie SchematicPlacementBridge.encodeSchematicItem) ----

    private static String encode(ListTag tag) {
        try {
            CompoundTag wrapper = new CompoundTag();
            wrapper.put("Data", tag);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIo.writeCompressed(wrapper, out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static ListTag decode(String base64) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            CompoundTag wrapper = NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.unlimitedHeap());
            return wrapper.getList("Data", net.minecraft.nbt.Tag.TAG_COMPOUND);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // ---- Persistenz (identisches Muster zu PastureBuilderManager) ----

    private static void load() {
        data = new Data();
        if (dataFile == null || !Files.exists(dataFile)) return;
        try (Reader reader = Files.newBufferedReader(dataFile)) {
            Data loaded = GSON.fromJson(reader, Data.class);
            if (loaded != null) data = loaded;
        } catch (IOException | com.google.gson.JsonParseException e) {
            LOGGER.error("[CC-GamemodeInventory] {} beschädigt/nicht lesbar - starte mit leeren Daten neu", dataFile, e);
            data = new Data();
        }
    }

    private static void save() {
        if (dataFile == null) return;
        try {
            Path tmp = dataFile.resolveSibling(dataFile.getFileName().toString() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tmp)) {
                GSON.toJson(data, writer);
            }
            Files.move(tmp, dataFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {}
    }
}
