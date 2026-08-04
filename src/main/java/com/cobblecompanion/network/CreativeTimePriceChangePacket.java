package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.data.AdminPermissionManager;
import com.cobblecompanion.data.CreativeTimeManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> Server: Admin ändert den Cobbledollars-Preis pro Creative-Minute (Wallet-Tab). */
public record CreativeTimePriceChangePacket(long pricePerMinute) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CreativeTimePriceChangePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "creative_time_price_change"));

    public static final StreamCodec<ByteBuf, CreativeTimePriceChangePacket> CODEC =
        ByteBufCodecs.VAR_LONG.map(CreativeTimePriceChangePacket::new, CreativeTimePriceChangePacket::pricePerMinute);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CreativeTimePriceChangePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!AdminPermissionManager.isAdminOp(player.getUUID())) return;
            CreativeTimeManager.setPricePerMinute(packet.pricePerMinute());
            PacketDistributor.sendToPlayer(player, new CreativeTimeStatusSyncPacket(
                CreativeTimeManager.getPricePerMinute(),
                CreativeTimeManager.getRemainingSeconds(player.getUUID()),
                true,
                CreativeTimeManager.isPurchaseEnabled()));
        });
    }
}
