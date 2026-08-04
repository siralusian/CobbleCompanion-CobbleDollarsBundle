package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.data.AdminPermissionManager;
import com.cobblecompanion.data.DimensionGamemodeManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> Server: Listeneditor in Settings > Gamemodes - eine Dimension-Gamemode-Regel entfernen. */
public record DimensionGamemodeRemovePacket(String dimensionId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DimensionGamemodeRemovePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "dimension_gamemode_remove"));

    public static final StreamCodec<ByteBuf, DimensionGamemodeRemovePacket> CODEC =
        ByteBufCodecs.STRING_UTF8.map(DimensionGamemodeRemovePacket::new, DimensionGamemodeRemovePacket::dimensionId);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DimensionGamemodeRemovePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!AdminPermissionManager.isAdminOp(player.getUUID())) return;

            if (DimensionGamemodeManager.removeRule(packet.dimensionId())) {
                if (player.getServer() != null) DimensionGamemodeManager.applyToOnlinePlayers(player.getServer(), packet.dimensionId());
            }
            DimensionGamemodeSyncPacket.sendTo(player);
        });
    }
}
