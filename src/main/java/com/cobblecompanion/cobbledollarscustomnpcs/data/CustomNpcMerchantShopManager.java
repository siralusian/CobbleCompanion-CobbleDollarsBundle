package com.cobblecompanion.cobbledollarscustomnpcs.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.harmex.cobbledollars.common.world.item.trading.shop.Shop;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Nutzer-Vorgabe: ein CustomNPC soll wahlweise die Funktion/das Kauf-GUI eines echten CobbleMerchant
 * bekommen, dabei aber sein eingestelltes CustomNPCs-Aussehen behalten - siehe
 * mixin.EntityNPCInterfaceMerchantMixin (macht EntityNPCInterface per Mixin selbst zu einem
 * CobbleDollarsShopHolder, dadurch öffnet ein Rechtsklick das ECHTE CobbleDollars-Shop-Fenster
 * inkl. dessen eingebauter Bearbeitung - kein eigenes Options-Fenster nötig). Diese Klasse hält nur
 * den Ein/Aus-Schalter (siehe CustomNpcCobbleMerchantInteractionHandler, Alt+Rechtsklick) und die
 * vom Spieler über das echte Shop-Fenster editierten Shop-Daten - rein datenhaltend, importiert
 * bewusst nur CobbleDollars-Typen (kein CustomNPCs-Import nötig, der Schlüssel ist einfach die
 * Entity-UUID).
 *
 * Persistiert als Gson-JSON im Weltordner (atomarer Schreibvorgang), gleiches Muster wie
 * GamemodeInventorySyncManager. Shop-Objekte werden wie ItemStack-Listen in anderen Managern als
 * Base64-NBT eingebettet (Shop.save()/Shop.Companion.read()).
 */
public class CustomNpcMerchantShopManager {

    private static class Data {
        Map<String, Boolean> enabled = new HashMap<>();
        Map<String, String> shopB64 = new HashMap<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Data data = new Data();
    private static Path dataFile;

    public static void init(MinecraftServer server) {
        dataFile = server.getWorldPath(LevelResource.ROOT).resolve("cobblecompanion_customnpc_merchant_shops.json");
        load();
    }

    public static boolean isEnabled(UUID npcUuid) {
        Boolean value = data.enabled.get(npcUuid.toString());
        return value != null && value;
    }

    public static void setEnabled(UUID npcUuid, boolean enabled) {
        data.enabled.put(npcUuid.toString(), enabled);
        save();
    }

    /** Leerer (aber niemals null) Shop, falls noch keine Angebote eingestellt wurden. */
    public static Shop getShop(UUID npcUuid, RegistryAccess registryAccess) {
        String b64 = data.shopB64.get(npcUuid.toString());
        if (b64 == null) return new Shop();
        try {
            byte[] bytes = Base64.getDecoder().decode(b64);
            CompoundTag wrapper = NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.unlimitedHeap());
            ListTag listTag = wrapper.getList("Data", net.minecraft.nbt.Tag.TAG_COMPOUND);
            Shop shop = Shop.Companion.read(registryAccess, listTag);
            return shop != null ? shop : new Shop();
        } catch (IOException e) {
            return new Shop();
        }
    }

    public static void setShop(UUID npcUuid, Shop shop, RegistryAccess registryAccess) {
        try {
            ListTag listTag = shop.save(registryAccess);
            CompoundTag wrapper = new CompoundTag();
            wrapper.put("Data", listTag);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIo.writeCompressed(wrapper, out);
            data.shopB64.put(npcUuid.toString(), Base64.getEncoder().encodeToString(out.toByteArray()));
            save();
        } catch (IOException ignored) {}
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
        if (data.enabled == null) data.enabled = new HashMap<>();
        if (data.shopB64 == null) data.shopB64 = new HashMap<>();
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
