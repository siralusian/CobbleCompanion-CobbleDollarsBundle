package com.cobblecompanion.cobbledollarscreate.data;

import com.cobblecompanion.cobbledollarscreate.CobbleCompanionDollarsCreate;
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
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Verkauf-Weiterleitung für CobbleMerchant-Verkäufe (siehe mixin.SellHandlerMixin): pro Merchant
 * (per Entity-UUID) eine feste Ziel-Inventar-Position. Rein datenhaltend + reine NeoForge-
 * Capability-API (IItemHandler) - importiert bewusst weder Create- noch CustomNPCs-Typen. Das
 * Zielinventar kann JEDER Block sein, der eine IItemHandler-Capability anbietet (Truhe, Fass,
 * Tresor, Creates eigene Inventare, ...) - der Spieler entscheidet selbst, wie er die Items von
 * dort aus weitertransportiert (Trichter, Schacht, Schleuse, o.ä.), das ist bewusst nicht unsere
 * Verantwortung.
 */
public class CobbleMerchantSellManager {

    public record Target(String dimension, int x, int y, int z) {
        BlockPos pos() { return new BlockPos(x, y, z); }
    }

    private static class Data {
        Map<String, Target> links = new HashMap<>();
        // Rein als Verhaltens-Schalter genutzt (siehe Klassenkommentar) - die Position selbst
        // wird nicht mehr für Preise ausgewertet (das übernimmt CentralItemPriceManager).
        Map<String, Target> buyTickerLinks = new HashMap<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Data data = new Data();
    private static Path dataFile;

    public static void init(MinecraftServer server) {
        dataFile = server.getWorldPath(LevelResource.ROOT).resolve("cobblecompanion_merchant_sell_links.json");
        load();
    }

    public static void setLink(UUID merchantUuid, ResourceKey<Level> dimension, BlockPos pos) {
        data.links.put(merchantUuid.toString(), new Target(dimension.location().toString(), pos.getX(), pos.getY(), pos.getZ()));
        save();
    }

    public static Target getLink(UUID merchantUuid) {
        return data.links.get(merchantUuid.toString());
    }

    public static void setBuyTickerLink(UUID merchantUuid, ResourceKey<Level> dimension, BlockPos pos) {
        data.buyTickerLinks.put(merchantUuid.toString(), new Target(dimension.location().toString(), pos.getX(), pos.getY(), pos.getZ()));
        save();
    }

    public static Target getBuyTickerLink(UUID merchantUuid) {
        return data.buyTickerLinks.get(merchantUuid.toString());
    }

    /** Unveränderliche Kopie aller Kauf-Ticker-Verknüpfungen (Merchant-UUID -> Ziel) - für das Verknüpfte-NPCs-Panel im Preis-Editor. */
    public static Map<UUID, Target> getAllBuyTickerLinks() {
        Map<UUID, Target> result = new HashMap<>();
        for (Map.Entry<String, Target> entry : data.buyTickerLinks.entrySet()) {
            result.put(UUID.fromString(entry.getKey()), entry.getValue());
        }
        return result;
    }

    /** Wie getAllBuyTickerLinks(), aber für die Verkaufs-Kiste (siehe getLink()). */
    public static Map<UUID, Target> getAllLinks() {
        Map<UUID, Target> result = new HashMap<>();
        for (Map.Entry<String, Target> entry : data.links.entrySet()) {
            result.put(UUID.fromString(entry.getKey()), entry.getValue());
        }
        return result;
    }

    public static void removeBuyTickerLink(UUID merchantUuid) {
        data.buyTickerLinks.remove(merchantUuid.toString());
        save();
    }

    public static void removeLink(UUID merchantUuid) {
        data.links.remove(merchantUuid.toString());
        save();
    }

    /**
     * Wie getBuyTickerLink(), fällt aber zusätzlich auf CustomNpcTraderLinkManager.getTickerLink()
     * zurück - Nutzer-Vorgabe: ein per Strg+Rechtsklick verknüpfter CustomNPC-Trader soll dieselbe
     * Verknüpfung auch im CobbleMerchant-Modus nutzen können, statt ein zweites Mal verknüpft
     * werden zu müssen.
     */
    public static Target getEffectiveBuyTickerLink(UUID merchantUuid) {
        Target own = getBuyTickerLink(merchantUuid);
        if (own != null) return own;
        // CustomNpcTraderLinkManager lebt in CobbleCompanion: Basis (rein UUID-basiert, importiert
        // selbst keine CustomNPCs-Typen) - cross-modul aufrufbar ohne CustomNPCs als Abhängigkeit.
        com.cobblecompanion.data.CustomNpcTraderLinkManager.Target npcLink =
            com.cobblecompanion.data.CustomNpcTraderLinkManager.getTickerLink(merchantUuid);
        if (npcLink == null) return null;
        return new Target(npcLink.dimension(), npcLink.x(), npcLink.y(), npcLink.z());
    }

    /**
     * Nutzer-Fund/Bugfix: wie getEffectiveBuyTickerLink(), aber für die Lager-Verknüpfung -
     * fällt zusätzlich auf CustomNpcTraderLinkManager.getStorageLink() zurück. Vorher nutzte
     * deliver() ausschließlich getLink() (rein CobbleMerchant-eigene Verknüpfung); ein per
     * CustomNPCs-Trader-Verknüpfung (Strg+Rechtsklick am NPC, nicht am CobbleMerchant) verlinktes
     * Lager wurde dadurch nie gefunden, obwohl derselbe NPC im CobbleMerchant-Modus verkauft (siehe
     * ContainerNPCTraderMixin) und die Ticker-Verknüpfung über getEffectiveBuyTickerLink() bereits
     * korrekt zurückfiel - Items landeten trotz sichtbar verknüpfter Kiste im Nichts (gelöscht).
     */
    public static Target getEffectiveLink(UUID merchantUuid) {
        Target own = getLink(merchantUuid);
        if (own != null) return own;
        com.cobblecompanion.data.CustomNpcTraderLinkManager.Target npcLink =
            com.cobblecompanion.data.CustomNpcTraderLinkManager.getStorageLink(merchantUuid);
        if (npcLink == null) return null;
        return new Target(npcLink.dimension(), npcLink.x(), npcLink.y(), npcLink.z());
    }

    /**
     * Lässt items einfach am Boden bei dropPos fallen (kein Zielinventar) - für Merchants, die
     * mit einem Lagerticker, aber keinem Lager verknüpft sind (siehe SellHandlerMixin).
     */
    public static void dropOnGround(ServerLevel level, java.util.List<ItemStack> items, BlockPos dropPos) {
        for (ItemStack stack : items) {
            if (stack.isEmpty()) continue;
            level.addFreshEntity(new ItemEntity(level,
                dropPos.getX() + 0.5, dropPos.getY() + 0.5, dropPos.getZ() + 0.5, stack.copy()));
        }
    }

    /**
     * Fügt items in das an merchantUuid verknüpfte Zielinventar ein (falls verknüpft und das
     * Zielinventar noch existiert). Nicht unterbringbare/übrige Items werden am Boden bei
     * dropPos fallen gelassen statt verloren zu gehen. Gibt false zurück, wenn keine Verknüpfung
     * existiert (Aufrufer entscheidet dann selbst, z.B. normal löschen wie bisher).
     */
    public static boolean deliver(MinecraftServer server, UUID merchantUuid, java.util.List<ItemStack> items, BlockPos dropPos) {
        Target target = getEffectiveLink(merchantUuid);
        if (target == null) {
            CobbleCompanionDollarsCreate.LOGGER.info("[CC-Create] CobbleMerchantSellManager.deliver: kein Link für Merchant {}", merchantUuid);
            return false;
        }

        ResourceKey<Level> dimensionKey = ResourceKey.create(
            net.minecraft.core.registries.Registries.DIMENSION,
            net.minecraft.resources.ResourceLocation.parse(target.dimension()));
        ServerLevel level = server.getLevel(dimensionKey);
        if (level == null) {
            CobbleCompanionDollarsCreate.LOGGER.info("[CC-Create] CobbleMerchantSellManager.deliver: Dimension {} nicht gefunden", target.dimension());
            return false;
        }

        BlockPos pos = target.pos();
        IItemHandler handler = findItemHandler(level, pos);
        CobbleCompanionDollarsCreate.LOGGER.info("[CC-Create] CobbleMerchantSellManager.deliver: Ziel {} in {}, Inventar gefunden={}, {} Item-Stack(s)",
            pos, target.dimension(), handler != null, items.size());

        for (ItemStack stack : items) {
            if (stack.isEmpty()) continue;
            ItemStack remainder = handler != null
                ? ItemHandlerHelper.insertItemStacked(handler, stack.copy(), false)
                : stack.copy();
            if (!remainder.isEmpty()) {
                CobbleCompanionDollarsCreate.LOGGER.info("[CC-Create] CobbleMerchantSellManager.deliver: {} passte nicht (mehr) ins Ziel, wird bei {} fallen gelassen",
                    remainder, dropPos);
                level.addFreshEntity(new ItemEntity(level,
                    dropPos.getX() + 0.5, dropPos.getY() + 0.5, dropPos.getZ() + 0.5, remainder));
            }
        }
        return true;
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
        } catch (IOException ignored) {}
    }

    private static void save() {
        if (dataFile == null) return;
        try (Writer writer = Files.newBufferedWriter(dataFile)) {
            GSON.toJson(data, writer);
        } catch (IOException ignored) {}
    }
}
