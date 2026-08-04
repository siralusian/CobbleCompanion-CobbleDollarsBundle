package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblemon.mod.common.Cobblemon;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> Server: OP möchte Cobblemons konfigurierte PC-Box-Anzahl (defaultBoxCount) ändern.
 * Server prüft die Berechtigung (OP-Level 2), setzt den Config-Wert und speichert ihn dauerhaft
 * (Cobblemon.INSTANCE.saveConfig) - wirkt WIE BESTÄTIGT erst nach Server-Neustart bzw. Neu-Login
 * der Spieler (kein Live-Resize bereits geladener PC-Speicher, siehe Umsetzungsplan Phase 8:
 * bewusst der sichere Weg statt PCStore.resize() für Online-Spieler).
 */
public record PcBoxCountChangePacket(int newBoxCount) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PcBoxCountChangePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "pc_box_count_change"));

    public static final StreamCodec<ByteBuf, PcBoxCountChangePacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, PcBoxCountChangePacket::newBoxCount,
        PcBoxCountChangePacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PcBoxCountChangePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!com.cobblecompanion.data.AdminPermissionManager.isAdminOp(player.getUUID())) return; // nur AdminOps dürfen die Box-Anzahl ändern
            int newCount = Math.max(1, packet.newBoxCount());

            Cobblemon.INSTANCE.getConfig().setDefaultBoxCount(newCount);
            Cobblemon.INSTANCE.saveConfig(Cobblemon.INSTANCE.getConfig());

            // Neuen Stand an alle Online-Spieler broadcasten (gleiches Muster wie ServerRuleChangePacket).
            for (ServerPlayer p : player.getServer().getPlayerList().getPlayers()) {
                PcBoxCountSyncPacket.sendTo(p);
            }
        });
    }
}
