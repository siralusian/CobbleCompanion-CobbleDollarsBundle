package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.data.AdminPermissionManager;
import com.cobblecompanion.data.CreativeTimeManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> Server: Wallet-Tab wurde geöffnet - fordert Preis/Minute + eigene Restzeit an. */
public record CreativeTimeStatusRequestPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CreativeTimeStatusRequestPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "creative_time_status_request"));

    public static final StreamCodec<ByteBuf, CreativeTimeStatusRequestPacket> CODEC =
        StreamCodec.unit(new CreativeTimeStatusRequestPacket());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CreativeTimeStatusRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            PacketDistributor.sendToPlayer(player, new CreativeTimeStatusSyncPacket(
                CreativeTimeManager.getPricePerMinute(),
                CreativeTimeManager.getRemainingSeconds(player.getUUID()),
                AdminPermissionManager.isAdminOp(player.getUUID()),
                CreativeTimeManager.isPurchaseEnabled()));
        });
    }
}
