package com.cobblecompanion.cobbledollarscreate.network;

import com.cobblecompanion.cobbledollarscreate.CobbleCompanionDollarsCreate;
import com.cobblecompanion.cobbledollarscreate.client.data.ClientMerchantOfferHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server -> Client: Antwort auf MerchantOfferRequestPacket - aktueller individueller
 * Angebots-Zustand eines Merchants (siehe MerchantOfferManager). active=false bedeutet, dass der
 * Merchant aktuell das volle Netzwerk-Angebot zeigt (itemIds ist dann die zuletzt gepflegte, aber
 * inaktive Auswahl - bleibt im Editor als Ausgangspunkt erhalten).
 */
public record MerchantOfferSyncPacket(UUID merchantUuid, boolean active, List<String> itemIds) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MerchantOfferSyncPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanionDollarsCreate.MOD_ID, "merchant_offer_sync"));

    public static final StreamCodec<ByteBuf, MerchantOfferSyncPacket> CODEC = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC, MerchantOfferSyncPacket::merchantUuid,
        ByteBufCodecs.BOOL, MerchantOfferSyncPacket::active,
        ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), MerchantOfferSyncPacket::itemIds,
        MerchantOfferSyncPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MerchantOfferSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientMerchantOfferHelper.onSync(packet.merchantUuid(), packet.active(), packet.itemIds()));
    }
}
