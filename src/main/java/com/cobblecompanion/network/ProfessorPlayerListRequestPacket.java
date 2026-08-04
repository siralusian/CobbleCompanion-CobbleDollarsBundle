package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.data.AdminPermissionManager;
import com.cobblecompanion.data.ProfessorHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> Server: Anfrage nach der Spielerliste für den Professor-Tab, gesendet beim Wechsel
 * auf den Tab. Server prüft die Berechtigung erneut (Op/AdminOp) - ein Client ohne Zugriff sieht
 * den Tab zwar clientseitig nicht, aber das Paket selbst muss trotzdem serverseitig abgesichert
 * sein (ein modifizierter Client könnte es sonst blind senden).
 */
public record ProfessorPlayerListRequestPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ProfessorPlayerListRequestPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "professor_player_list_request"));

    public static final StreamCodec<ByteBuf, ProfessorPlayerListRequestPacket> CODEC =
        StreamCodec.unit(new ProfessorPlayerListRequestPacket());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ProfessorPlayerListRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!AdminPermissionManager.isOp(player.getUUID())) return;
            if (player.getServer() == null) return;
            PacketDistributor.sendToPlayer(player, new ProfessorPlayerListResponsePacket(
                ProfessorHelper.getAllPlayers(player.getServer())));
        });
    }
}
