package com.cobblecompanion.cobbledollarscreate;

import com.cobblecompanion.cobbledollarscreate.data.CentralItemPriceManager;
import com.cobblecompanion.cobbledollarscreate.data.CobbleMerchantPayoutManager;
import com.cobblecompanion.cobbledollarscreate.data.CobbleMerchantSellManager;
import com.cobblecompanion.cobbledollarscreate.data.CreateStockTickerPriceManager;
import com.cobblecompanion.cobbledollarscreate.data.KnownPlayersHelper;
import com.cobblecompanion.cobbledollarscreate.network.CreateStockTickerPricesSyncPacket;
import com.cobblecompanion.cobbledollarscreate.network.KnownPlayersSyncPacket;
import com.cobblecompanion.cobbledollarscreate.network.LinkedMerchantInfo;
import com.cobblecompanion.cobbledollarscreate.network.LinkedMerchantsSyncPacket;
import com.cobblecompanion.cobbledollarscreate.network.PriceListPayload;
import com.cobblecompanion.cobbledollarscreate.network.SaleRecipientSyncPacket;
import com.cobblecompanion.data.ClientKeyStateManager;
import com.cobblecompanion.data.CustomNpcTraderLinkManager;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Strg+Rechtsklick (echter Minecraft-OP, siehe Nutzer-Vorgabe unten) auf einen Lagerticker
 * öffnet unseren eigenen Preis-Editor statt Creates normaler Bestell-GUI (die bleibt bei
 * normalem Rechtsklick unangetastet). Bewusst Strg statt Shift (Nutzer-Vorgabe) - Shift+
 * Rechtsklick öffnet bei Create-Blöcken teils direkt andere Menüs. Nur registriert, wenn
 * ModAvailability.isCreateAvailable() (siehe CobbleCompanionDollarsCreate.onServerStarting).
 *
 * Nutzer-Vorgabe: mehrere Preislisten statt einer einzigen globalen (siehe
 * CentralItemPriceManager) - sendet ALLE bekannten Listen komplett (nicht nur die diesem Netzwerk
 * zugewiesene), damit das Dropdown im Editor rein client-seitig wechseln kann. Das "verlangt
 * Bezahlung"-Flag bleibt weiterhin pro NETZWERK (CreateStockTickerPriceManager). Bearbeitung
 * bewusst NICHT über AdminPermissionManager, sondern echte Minecraft-OP-Rechte
 * (player.hasPermissions(2)) - Nutzer-Vorgabe für dieses Feature.
 */
public class CreateStockTickerInteractionHandler {

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!ClientKeyStateManager.isCtrlHeld(player.getUUID())) return;
        if (!player.hasPermissions(2)) return;

        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (!(level.getBlockEntity(pos) instanceof StockTickerBlockEntity ticker)) return;

        event.setCanceled(true);
        event.setUseBlock(net.neoforged.neoforge.common.util.TriState.FALSE);
        event.setUseItem(net.neoforged.neoforge.common.util.TriState.FALSE);

        // Nutzer-Vorgabe: Bezahlpflicht gilt pro Netzwerk (freqId), nicht mehr pro Ticker-Block.
        boolean enabled = ticker.behaviour != null && CreateStockTickerPriceManager.isEnabled(ticker.behaviour.freqId);
        String currentListId = CentralItemPriceManager.getListIdForNetwork(ticker.behaviour != null ? ticker.behaviour.freqId : null);

        List<PriceListPayload> lists = new ArrayList<>();
        for (CentralItemPriceManager.PriceListInfo info : CentralItemPriceManager.getAllLists()) {
            // Union aus bepreisten Items UND Items, die nur ein von der Norm abweichendes Verkauf/
            // Ankauf-Flag oder nur eine Kategorie haben - sonst würden diese beim Öffnen des Editors
            // verloren gehen, weil sie nicht in getSellPrices()/getBuyPrices() auftauchen.
            java.util.Set<String> itemIds = new java.util.LinkedHashSet<>(CentralItemPriceManager.getSellPrices(info.id()).keySet());
            itemIds.addAll(CentralItemPriceManager.getBuyPrices(info.id()).keySet());
            itemIds.addAll(CentralItemPriceManager.getSellDisabled(info.id()));
            itemIds.addAll(CentralItemPriceManager.getBuyDisabled(info.id()));
            itemIds.addAll(CentralItemPriceManager.getCategories(info.id()).keySet());
            List<String> entries = new ArrayList<>();
            for (String itemId : itemIds) {
                long sellPrice = CentralItemPriceManager.sellPriceForItem(info.id(), itemId);
                long buyPrice = CentralItemPriceManager.buyPriceForItem(info.id(), itemId);
                boolean sellEnabled = CentralItemPriceManager.isSellEnabled(info.id(), itemId);
                boolean buyEnabled = CentralItemPriceManager.isBuyEnabled(info.id(), itemId);
                String category = CentralItemPriceManager.getCategory(info.id(), itemId);
                entries.add(itemId + "=" + sellPrice + "=" + buyPrice + "=" + (sellEnabled ? 1 : 0) + "=" + (buyEnabled ? 1 : 0)
                    + "=" + (category != null ? category : ""));
            }
            lists.add(new PriceListPayload(info.id(), info.name(), entries));
        }

        List<String> availableItemIds = com.cobblecompanion.integrations.create.CreateStockTickerBridge.getAvailableItemIds(level, pos);
        PacketDistributor.sendToPlayer(player, new CreateStockTickerPricesSyncPacket(pos, enabled, currentListId, lists, availableItemIds));

        // Nutzer-Vorgabe: Übersichts-Panel im Preis-Editor - alle CustomNPC-Trader/CobbleMerchants,
        // deren verknüpfter Ticker zum SELBEN Netzwerk (freqId) gehört wie der gerade geöffnete.
        if (ticker.behaviour != null && player.getServer() != null) {
            UUID freqId = ticker.behaviour.freqId;
            List<LinkedMerchantInfo> linked = collectLinkedMerchants(player.getServer(), freqId);
            PacketDistributor.sendToPlayer(player, new LinkedMerchantsSyncPacket(linked));

            // Nutzer-Vorgabe: Verkaufserlöse an Shop-Betreiber - Empfänger-Zustand + bekannte Spieler
            // für den Empfänger-Picker mitschicken (siehe CobbleMerchantPayoutManager).
            PacketDistributor.sendToPlayer(player, buildSaleRecipientSyncPacket(player.getServer(), freqId, linked));
            PacketDistributor.sendToPlayer(player, new KnownPlayersSyncPacket(
                KnownPlayersSyncPacket.from(KnownPlayersHelper.getAllKnownPlayers(player.getServer()))));
        }
    }

    /** Netzwerk-freqId an einer Ticker-Position auflösen - für die C2S-Handler der Empfänger-Update-Pakete. */
    public static UUID resolveFreqIdAt(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof StockTickerBlockEntity ticker) || ticker.behaviour == null) return null;
        return ticker.behaviour.freqId;
    }

    /** Nach einer Empfänger-Änderung (siehe SaleRecipientNetworkUpdatePacket/SaleRecipientEntityUpdatePacket) den aktuellen Zustand erneut senden. */
    public static void resyncSaleRecipient(ServerPlayer player, BlockPos tickerPos) {
        MinecraftServer server = player.server;
        UUID freqId = resolveFreqIdAt(player.serverLevel(), tickerPos);
        if (freqId == null) return;
        List<LinkedMerchantInfo> linked = collectLinkedMerchants(server, freqId);
        PacketDistributor.sendToPlayer(player, buildSaleRecipientSyncPacket(server, freqId, linked));
    }

    private static SaleRecipientSyncPacket buildSaleRecipientSyncPacket(MinecraftServer server, UUID freqId, List<LinkedMerchantInfo> linked) {
        String mode = CobbleMerchantPayoutManager.getMode(freqId);
        String recipientName = "";
        if (CobbleMerchantPayoutManager.MODE_SINGLE.equals(mode)) {
            UUID recipientUuid = CobbleMerchantPayoutManager.getNetworkRecipient(freqId);
            recipientName = resolvePlayerName(server, recipientUuid);
        }

        List<SaleRecipientSyncPacket.EntityRecipientEntry> entityRecipients = new ArrayList<>();
        if (CobbleMerchantPayoutManager.MODE_VARIES.equals(mode)) {
            for (LinkedMerchantInfo info : linked) {
                UUID recipientUuid = CobbleMerchantPayoutManager.getEntityRecipient(info.uuid());
                entityRecipients.add(new SaleRecipientSyncPacket.EntityRecipientEntry(info.uuid(), resolvePlayerName(server, recipientUuid)));
            }
        }

        return new SaleRecipientSyncPacket(mode, recipientName, entityRecipients);
    }

    private static String resolvePlayerName(MinecraftServer server, UUID uuid) {
        if (uuid == null) return "";
        return server.getProfileCache() != null
            ? server.getProfileCache().get(uuid).map(profile -> profile.getName()).orElse("")
            : "";
    }

    private static List<LinkedMerchantInfo> collectLinkedMerchants(MinecraftServer server, UUID currentFreqId) {
        List<LinkedMerchantInfo> result = new ArrayList<>();
        for (Map.Entry<UUID, CustomNpcTraderLinkManager.Target> entry : CustomNpcTraderLinkManager.getAllTickerLinks().entrySet()) {
            UUID npcUuid = entry.getKey();
            UUID freqId = CreateStockTickerBridge.resolveNetworkFreqId(server, toBridgeTarget(entry.getValue()));
            if (freqId == null || !freqId.equals(currentFreqId)) continue;
            LinkedMerchantInfo info = buildInfo(server, npcUuid, true, CustomNpcTraderLinkManager.getStorageLink(npcUuid));
            if (info != null) result.add(info);
        }
        for (Map.Entry<UUID, CobbleMerchantSellManager.Target> entry : CobbleMerchantSellManager.getAllBuyTickerLinks().entrySet()) {
            UUID merchantUuid = entry.getKey();
            UUID freqId = CreateStockTickerBridge.resolveNetworkFreqId(server, entry.getValue());
            if (freqId == null || !freqId.equals(currentFreqId)) continue;
            LinkedMerchantInfo info = buildInfo(server, merchantUuid, false, CobbleMerchantSellManager.getLink(merchantUuid));
            if (info != null) result.add(info);
        }
        return result;
    }

    /** CreateStockTickerBridge.resolveNetworkFreqId erwartet CobbleMerchantSellManager.Target - CustomNpcTraderLinkManager.Target hat dieselbe Form, daher hier kurz umgewandelt. */
    private static CobbleMerchantSellManager.Target toBridgeTarget(CustomNpcTraderLinkManager.Target target) {
        return new CobbleMerchantSellManager.Target(target.dimension(), target.x(), target.y(), target.z());
    }

    private static LinkedMerchantInfo buildInfo(MinecraftServer server, UUID uuid, boolean isCustomNpc, CobbleMerchantSellManager.Target storage) {
        Entity entity = null;
        for (ServerLevel lvl : server.getAllLevels()) {
            Entity found = lvl.getEntity(uuid);
            if (found != null) {
                entity = found;
                break;
            }
        }
        if (entity == null) return null;

        String name = entity.getName().getString();
        String dimension = entity.level().dimension().location().toString();
        BlockPos pos = entity.blockPosition();

        boolean hasStorage = storage != null;
        String storageDimension = hasStorage ? storage.dimension() : "";
        int storageX = hasStorage ? storage.x() : 0;
        int storageY = hasStorage ? storage.y() : 0;
        int storageZ = hasStorage ? storage.z() : 0;

        return new LinkedMerchantInfo(uuid, isCustomNpc, name, dimension, pos.getX(), pos.getY(), pos.getZ(),
            hasStorage, storageDimension, storageX, storageY, storageZ);
    }

    /** Wie buildInfo(), aber für CustomNpcTraderLinkManager.Target (Kisten-Verknüpfung des NPC). */
    private static LinkedMerchantInfo buildInfo(MinecraftServer server, UUID uuid, boolean isCustomNpc, CustomNpcTraderLinkManager.Target storage) {
        return buildInfo(server, uuid, isCustomNpc, storage != null ? toBridgeTarget(storage) : null);
    }
}
