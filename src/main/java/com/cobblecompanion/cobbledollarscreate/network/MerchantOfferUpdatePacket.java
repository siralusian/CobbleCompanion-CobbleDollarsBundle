package com.cobblecompanion.cobbledollarscreate.network;

import com.cobblecompanion.cobbledollarscreate.CobbleCompanionDollarsCreate;
import com.cobblecompanion.cobbledollarscreate.data.MerchantOfferManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * Client -> Server: speichert das individuelle Angebot eines Merchants aus dem
 * MerchantOfferEditScreen (siehe MerchantOfferManager). Nur echte Minecraft-OPs. Wirkt sich erst
 * beim NÄCHSTEN Öffnen des Shops durch einen Spieler aus (PlayerExtensionKtOpenShopMixin baut den
 * Shop bei jedem Öffnen neu auf) - kein sofortiger Push an bereits offene Kaufmenüs nötig.
 */
public record MerchantOfferUpdatePacket(UUID merchantUuid, boolean active, List<String> itemIds) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MerchantOfferUpdatePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanionDollarsCreate.MOD_ID, "merchant_offer_update"));

    public static final StreamCodec<ByteBuf, MerchantOfferUpdatePacket> CODEC = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC, MerchantOfferUpdatePacket::merchantUuid,
        ByteBufCodecs.BOOL, MerchantOfferUpdatePacket::active,
        ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), MerchantOfferUpdatePacket::itemIds,
        MerchantOfferUpdatePacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MerchantOfferUpdatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!player.hasPermissions(2)) return;

            MerchantOfferManager.setCustomOffer(packet.merchantUuid(), new LinkedHashSet<>(packet.itemIds()));
            MerchantOfferManager.setCustomOfferActive(packet.merchantUuid(), packet.active());
        });
    }
}
