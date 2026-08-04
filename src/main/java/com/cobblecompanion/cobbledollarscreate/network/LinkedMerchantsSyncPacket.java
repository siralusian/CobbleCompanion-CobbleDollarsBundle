package com.cobblecompanion.cobbledollarscreate.network;

import com.cobblecompanion.cobbledollarscreate.CobbleCompanionDollarsCreate;
import com.cobblecompanion.cobbledollarscreate.client.data.ClientLinkedMerchantsHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client: wird zusammen mit CreateStockTickerPricesSyncPacket verschickt (siehe
 * CreateStockTickerInteractionHandler) - alle CustomNPC-Trader/CobbleMerchants, deren verknüpfter
 * Lagerticker zum selben Netzwerk (freqId) gehört wie der gerade geöffnete. Für das Übersichts-
 * Panel im Preis-Editor (StockTickerPriceScreen).
 */
public record LinkedMerchantsSyncPacket(List<LinkedMerchantInfo> entries) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<LinkedMerchantsSyncPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanionDollarsCreate.MOD_ID, "linked_merchants_sync"));

    public static final StreamCodec<ByteBuf, LinkedMerchantsSyncPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.collection(ArrayList::new, LinkedMerchantInfo.CODEC), LinkedMerchantsSyncPacket::entries,
        LinkedMerchantsSyncPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LinkedMerchantsSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientLinkedMerchantsHelper.set(packet.entries()));
    }
}
