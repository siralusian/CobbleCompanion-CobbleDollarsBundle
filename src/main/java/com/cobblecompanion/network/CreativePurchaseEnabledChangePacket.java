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

/** Client -> Server: AdminOp schaltet den Creative-Kauf komplett ein/aus (Settings > Gamemodes). */
public record CreativePurchaseEnabledChangePacket(boolean enabled) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CreativePurchaseEnabledChangePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "creative_purchase_enabled_change"));

    public static final StreamCodec<ByteBuf, CreativePurchaseEnabledChangePacket> CODEC =
        ByteBufCodecs.BOOL.map(CreativePurchaseEnabledChangePacket::new, CreativePurchaseEnabledChangePacket::enabled);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CreativePurchaseEnabledChangePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!AdminPermissionManager.isAdminOp(player.getUUID())) return;
            CreativeTimeManager.setPurchaseEnabled(packet.enabled());
            PacketDistributor.sendToPlayer(player, new CreativeTimeStatusSyncPacket(
                CreativeTimeManager.getPricePerMinute(),
                CreativeTimeManager.getRemainingSeconds(player.getUUID()),
                true,
                CreativeTimeManager.isPurchaseEnabled()));
        });
    }
}
