package com.cobblecompanion.cobbledollarscreate.network;

import com.cobblecompanion.cobbledollarscreate.CobbleCompanionDollarsCreate;
import com.cobblecompanion.cobbledollarscreate.client.data.ClientStockTickerBuyPricesHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client: Nutzer-Vorgabe "Preisschild im Lagerticker-Bestellmenü" - sobald ein Spieler
 * das Bestellmenü eines Lagertickers öffnet (siehe StockTickerOrderScreenPriceHandler), bekommt er
 * einmalig die aktuelle Ankaufs-Preisliste (siehe CentralItemPriceManager.getBuyPrices -
 * "Ankauf" = Spieler bestellt/kauft über den Ticker) UND ob DIESER Ticker überhaupt Bezahlung
 * verlangt (siehe CreateStockTickerPriceManager.isEnabled) - für die Preisanzeige-Overlay
 * (StockTickerPriceOverlay) auf Create's eigenem Bestellmenü. entries im Format "itemId=preis",
 * nur für Items mit isBuyEnabled()==true (deaktivierte Items zeigen bewusst kein Preisschild -
 * "kostenlos" wäre irreführend, da sie trotzdem normal bestellbar bleiben).
 */
public record StockTickerBuyPricesSyncPacket(boolean paymentRequired, List<String> entries) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StockTickerBuyPricesSyncPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanionDollarsCreate.MOD_ID, "stockticker_buy_prices_sync"));

    public static final StreamCodec<ByteBuf, StockTickerBuyPricesSyncPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, StockTickerBuyPricesSyncPacket::paymentRequired,
        ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), StockTickerBuyPricesSyncPacket::entries,
        StockTickerBuyPricesSyncPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StockTickerBuyPricesSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientStockTickerBuyPricesHelper.set(packet.paymentRequired(), packet.entries()));
    }
}
