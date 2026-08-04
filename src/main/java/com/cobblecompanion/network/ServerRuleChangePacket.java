package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.data.AdminPermissionManager;
import com.cobblecompanion.data.ServerRulesManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> Server: ein AdminOp möchte eine globale Server-Regel ändern. Der Server prüft die
 * Berechtigung über AdminPermissionManager (eigenständig, NICHT Vanilla-OP - siehe dortiger
 * Klassenkommentar), ändert die Regel und broadcastet den neuen Stand an alle.
 */
public record ServerRuleChangePacket(int rule, boolean value) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ServerRuleChangePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "server_rule_change"));

    public static final StreamCodec<ByteBuf, ServerRuleChangePacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, ServerRuleChangePacket::rule,
        ByteBufCodecs.BOOL, ServerRuleChangePacket::value,
        ServerRuleChangePacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ServerRuleChangePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!AdminPermissionManager.isAdminOp(player.getUUID())) return; // nur AdminOps dürfen Regeln ändern
            if (!ServerRulesManager.setRule(packet.rule(), packet.value())) return;
            // Neuen Stand an alle Online-Spieler broadcasten.
            for (ServerPlayer p : player.getServer().getPlayerList().getPlayers()) {
                ServerRulesSyncPacket.sendTo(p);
            }
        });
    }
}
