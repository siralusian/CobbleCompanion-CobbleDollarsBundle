package com.cobblecompanion.cobbledollarscustomnpcs.network;

import com.cobblecompanion.cobbledollarscustomnpcs.CobbleCompanionDollarsCustomNPCs;
import com.cobblecompanion.cobbledollarscustomnpcs.client.data.ClientCustomNpcTraderStockHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client: verfügbarer Netzwerk-Lagerbestand pro Verkaufsslot (0..17) eines CustomNPCs-
 * Traders, gesendet beim Öffnen des Bestell-GUI (siehe CustomNpcTraderStockSyncHandler). -1 pro
 * Slot bedeutet "kein Item in diesem Slot bzw. keine Beschränkung anwendbar" - genutzt vom
 * CustomNpcTraderStockOverlay, um die Verfügbarkeit direkt neben jedem Item anzuzeigen.
 */
public record CustomNpcTraderStockSyncPacket(List<Integer> available) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CustomNpcTraderStockSyncPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanionDollarsCustomNPCs.MOD_ID, "customnpc_trader_stock_sync"));

    public static final StreamCodec<ByteBuf, CustomNpcTraderStockSyncPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.VAR_INT), CustomNpcTraderStockSyncPacket::available,
        CustomNpcTraderStockSyncPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CustomNpcTraderStockSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientCustomNpcTraderStockHelper.set(packet.available()));
    }
}
