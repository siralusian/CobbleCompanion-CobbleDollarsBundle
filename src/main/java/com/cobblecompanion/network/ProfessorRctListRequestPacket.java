package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.data.AdminPermissionManager;
import com.cobblecompanion.integrations.ModAvailability;
import com.cobblecompanion.integrations.rct.RctBridge;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Client -> Server: "RCT"-Knopf im Professor-Tab wurde geklickt - fordert die Liste aller
 * Trainerpfade (Serien) inkl. Abschluss-Status für den ausgewählten Spieler an. Nur relevant,
 * wenn ModAvailability.isRctAvailable() (siehe integrations.ModAvailability) - der Button
 * erscheint clientseitig nur dann, hier zusätzlich serverseitig abgesichert.
 */
public record ProfessorRctListRequestPacket(UUID targetUuid) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ProfessorRctListRequestPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "professor_rct_list_request"));

    public static final StreamCodec<ByteBuf, ProfessorRctListRequestPacket> CODEC =
        UUIDUtil.STREAM_CODEC.map(ProfessorRctListRequestPacket::new, ProfessorRctListRequestPacket::targetUuid);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ProfessorRctListRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer admin)) return;
            if (!AdminPermissionManager.isAdminOp(admin.getUUID())) return;
            MinecraftServer server = admin.getServer();
            if (server == null) return;
            buildAndSend(admin, server, packet.targetUuid());
        });
    }

    /**
     * Baut die RCT-Serienliste neu und schickt sie an den Admin - genutzt vom initialen "RCT"-
     * Knopf-Klick UND von AdminResetRctPacket, damit die Liste nach einem Reset sofort den
     * frischen Stand zeigt (gleiches Muster wie ProfessorPCRequestPacket.buildAndSend).
     */
    public static void buildAndSend(ServerPlayer admin, MinecraftServer server, UUID targetUuid) {
        if (!ModAvailability.isRctAvailable()) return;
        ServerPlayer target = server.getPlayerList().getPlayer(targetUuid);
        if (target == null) {
            // RCTs öffentliche API verlangt zwingend ein Player-Objekt (siehe Recherche) - für
            // offline Spieler gibt es keinen Zugriff, der Client zeigt dafür einen Hinweis.
            PacketDistributor.sendToPlayer(admin, new ProfessorRctListResponsePacket(targetUuid, false, new ArrayList<>()));
            return;
        }

        List<RctBridge.SeriesEntry> series = RctBridge.listSeries(target);
        List<String> entries = new ArrayList<>();
        for (RctBridge.SeriesEntry entry : series) {
            entries.add(entry.id() + "|" + entry.title() + "|" + entry.completed());
        }
        PacketDistributor.sendToPlayer(admin, new ProfessorRctListResponsePacket(targetUuid, true, entries));
    }
}
