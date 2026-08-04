package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.data.AdminPermissionManager;
import com.cobblecompanion.integrations.ModAvailability;
import com.cobblecompanion.integrations.rct.RctBridge;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Client -> Server: RCT-Reset-Button im Professor-Tab (AdminOp, per Ja/Nein bestätigt im
 * CompanionScreen). seriesId == ALL_SERIES ("*") bedeutet: kompletter RCT-Fortschritt des
 * Zielspielers wird zurückgesetzt, sonst nur der genannte Trainerpfad. Wirkt nur auf ONLINE
 * Zielspieler - RCTs öffentliche API verlangt zwingend ein Player-Objekt (siehe RctBridge).
 */
public record AdminResetRctPacket(UUID targetUuid, String seriesId) implements CustomPacketPayload {

    public static final String ALL_SERIES = "*";

    public static final CustomPacketPayload.Type<AdminResetRctPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "admin_reset_rct"));

    public static final StreamCodec<ByteBuf, AdminResetRctPacket> CODEC = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC, AdminResetRctPacket::targetUuid,
        ByteBufCodecs.STRING_UTF8, AdminResetRctPacket::seriesId,
        AdminResetRctPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AdminResetRctPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer admin)) return;
            if (!AdminPermissionManager.isAdminOp(admin.getUUID())) return;
            if (!ModAvailability.isRctAvailable()) return;
            MinecraftServer server = admin.getServer();
            if (server == null) return;
            UUID targetUuid = packet.targetUuid();

            ServerPlayer target = server.getPlayerList().getPlayer(targetUuid);
            if (target == null) return;

            try {
                if (ALL_SERIES.equals(packet.seriesId())) {
                    RctBridge.resetAll(target);
                } else {
                    RctBridge.resetSeries(target, packet.seriesId());
                }
            } catch (Exception e) {
                CobbleCompanion.LOGGER.error("[CC] RCT-Reset fehlgeschlagen für " + targetUuid, e);
            }

            ProfessorRctListRequestPacket.buildAndSend(admin, server, targetUuid);
        });
    }
}
