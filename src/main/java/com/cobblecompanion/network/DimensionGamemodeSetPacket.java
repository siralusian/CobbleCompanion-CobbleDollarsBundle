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
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> Server: Listeneditor in Settings > Gamemodes - neue Dimension-Gamemode-Regel setzen. */
public record DimensionGamemodeSetPacket(String dimensionId, String gameModeName) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DimensionGamemodeSetPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "dimension_gamemode_set"));

    public static final StreamCodec<ByteBuf, DimensionGamemodeSetPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, DimensionGamemodeSetPacket::dimensionId,
        ByteBufCodecs.STRING_UTF8, DimensionGamemodeSetPacket::gameModeName,
        DimensionGamemodeSetPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DimensionGamemodeSetPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!AdminPermissionManager.isAdminOp(player.getUUID())) return;
            GameType mode = GameType.byName(packet.gameModeName(), null);
            if (mode == null || packet.dimensionId().isBlank()) return;

            DimensionGamemodeManager.setRule(packet.dimensionId(), mode);
            if (player.getServer() != null) DimensionGamemodeManager.applyToOnlinePlayers(player.getServer(), packet.dimensionId());
            DimensionGamemodeSyncPacket.sendTo(player);
        });
    }
}
