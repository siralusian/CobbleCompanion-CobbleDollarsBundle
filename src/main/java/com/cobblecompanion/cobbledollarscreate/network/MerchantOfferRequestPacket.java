package com.cobblecompanion.cobbledollarscreate.network;

import com.cobblecompanion.cobbledollarscreate.CobbleCompanionDollarsCreate;
import com.cobblecompanion.cobbledollarscreate.data.MerchantOfferManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Client -> Server: der Admin hat im Preis-Editor (StockTickerPriceScreen, Verknüpfte-NPCs-Panel)
 * "Angebot bearbeiten" für einen Merchant gewählt - fragt dessen aktuellen individuellen
 * Angebots-Zustand ab (siehe MerchantOfferManager). Antwort per MerchantOfferSyncPacket. Nur echte
 * Minecraft-OPs (gleiches Muster wie der restliche Preis-Editor).
 */
public record MerchantOfferRequestPacket(UUID merchantUuid) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MerchantOfferRequestPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanionDollarsCreate.MOD_ID, "merchant_offer_request"));

    public static final StreamCodec<ByteBuf, MerchantOfferRequestPacket> CODEC = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC, MerchantOfferRequestPacket::merchantUuid,
        MerchantOfferRequestPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MerchantOfferRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!player.hasPermissions(2)) return;

            UUID uuid = packet.merchantUuid();
            PacketDistributor.sendToPlayer(player, new MerchantOfferSyncPacket(uuid,
                MerchantOfferManager.isCustomOfferActive(uuid),
                new java.util.ArrayList<>(MerchantOfferManager.getCustomOffer(uuid))));
        });
    }
}
