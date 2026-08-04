package com.cobblecompanion.cobbledollarscreate.network;

import com.cobblecompanion.cobbledollarscreate.CobbleCompanionDollarsCreate;
import com.cobblecompanion.cobbledollarscreate.client.data.ClientMerchantOfferStockHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client: verfügbarer Netzwerk-Lagerbestand für JEDES Angebot eines CobbleMerchant-Shops
 * (nur relevant, wenn der Merchant mit einem Lagerticker verknüpft ist, siehe
 * PlayerExtensionKtOpenShopMixin/BuyHandlerMixin) - drei parallele Listen (categoryIndex/offerIndex/
 * available), da CustomPacketPayload keine verschachtelten Strukturen mag. Genutzt von
 * OfferEntryStockOverlayMixin, um ein rotes X über den Kaufen-Button zu legen, wenn nicht genug im
 * verknüpften Netzwerk vorhanden ist.
 */
public record MerchantOfferStockSyncPacket(List<Integer> categoryIndices, List<Integer> offerIndices, List<Integer> available)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MerchantOfferStockSyncPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanionDollarsCreate.MOD_ID, "merchant_offer_stock_sync"));

    public static final StreamCodec<ByteBuf, MerchantOfferStockSyncPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.VAR_INT), MerchantOfferStockSyncPacket::categoryIndices,
        ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.VAR_INT), MerchantOfferStockSyncPacket::offerIndices,
        ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.VAR_INT), MerchantOfferStockSyncPacket::available,
        MerchantOfferStockSyncPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MerchantOfferStockSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() ->
            ClientMerchantOfferStockHelper.set(packet.categoryIndices(), packet.offerIndices(), packet.available()));
    }
}
