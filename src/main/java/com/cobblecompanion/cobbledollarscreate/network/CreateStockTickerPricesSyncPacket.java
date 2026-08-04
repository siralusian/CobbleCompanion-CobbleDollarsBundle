package com.cobblecompanion.cobbledollarscreate.network;

import com.cobblecompanion.cobbledollarscreate.CobbleCompanionDollarsCreate;
import com.cobblecompanion.cobbledollarscreate.client.data.ClientCreateStockTickerHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client: Strg+Rechtsklick auf einen Lagerticker (siehe
 * CreateStockTickerInteractionHandler) - öffnet den eigenen Preis-Editor.
 * Nutzer-Vorgabe: mehrere Preislisten statt einer einzigen globalen - `lists` enthält ALLE
 * bekannten Listen komplett (nicht nur die diesem Netzwerk zugewiesene), `currentListId` die
 * diesem Ticker-Netzwerk aktuell zugewiesene (siehe CentralItemPriceManager.getListIdForNetwork) -
 * das Dropdown im Editor wechselt damit rein client-seitig ohne Server-Rückfrage.
 * availableItemIds: Item-IDs, die aktuell im an diesem Ticker angeschlossenen Logistiknetzwerk
 * verfügbar sind (siehe CreateStockTickerBridge.getAvailableItemIds).
 */
public record CreateStockTickerPricesSyncPacket(BlockPos pos, boolean enabled, String currentListId,
        List<PriceListPayload> lists, List<String> availableItemIds) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CreateStockTickerPricesSyncPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanionDollarsCreate.MOD_ID, "create_stockticker_prices_sync"));

    public static final StreamCodec<ByteBuf, CreateStockTickerPricesSyncPacket> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, CreateStockTickerPricesSyncPacket::pos,
        ByteBufCodecs.BOOL, CreateStockTickerPricesSyncPacket::enabled,
        ByteBufCodecs.STRING_UTF8, CreateStockTickerPricesSyncPacket::currentListId,
        ByteBufCodecs.collection(ArrayList::new, PriceListPayload.CODEC), CreateStockTickerPricesSyncPacket::lists,
        ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), CreateStockTickerPricesSyncPacket::availableItemIds,
        CreateStockTickerPricesSyncPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // Kein @OnlyIn(Dist.CLIENT): siehe LivingDexPacket.handle (RuntimeDistCleaner). Ruft bewusst
    // NUR den reinen Datenhalter auf, nicht direkt StockTickerPriceScreen - siehe dessen
    // Klassenkommentar (ClientCreateStockTickerHelper) für den Grund.
    public static void handle(CreateStockTickerPricesSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientCreateStockTickerHelper.setPendingPrices(
            packet.pos(), packet.enabled(), packet.currentListId(), packet.lists(), packet.availableItemIds()));
    }
}
