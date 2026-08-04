package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.client.data.ClientAdminHelper;
import com.cobblecompanion.data.AdminPermissionManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> Client: ob der empfangende Spieler Op/AdminOp für den Professor-Tab hat (steuert,
 * ob der Tab überhaupt angezeigt wird). Gesendet bei Login und nach "/companion op"/"adminop",
 * falls der berechtigte Spieler gerade online ist.
 */
public record AdminPermissionSyncPacket(boolean isOp, boolean isAdminOp) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AdminPermissionSyncPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "admin_permission_sync"));

    public static final StreamCodec<ByteBuf, AdminPermissionSyncPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, AdminPermissionSyncPacket::isOp,
        ByteBufCodecs.BOOL, AdminPermissionSyncPacket::isAdminOp,
        AdminPermissionSyncPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void sendTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new AdminPermissionSyncPacket(
            AdminPermissionManager.isOp(player.getUUID()), AdminPermissionManager.isAdminOp(player.getUUID())));
    }

    // Kein @OnlyIn(Dist.CLIENT): siehe LivingDexPacket.handle (RuntimeDistCleaner).
    public static void handle(AdminPermissionSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientAdminHelper.apply(packet.isOp(), packet.isAdminOp()));
    }
}
