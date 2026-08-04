package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.data.TeamBuilderHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Client -> Server: Team-Builder-Tab wurde bedient - fordert einen Team-Vorschlag an.
 * mode: 0="Allgemein" (breite Typ-Abdeckung, targetType/opponentEntries ignoriert),
 * 1="Type" (bester Konter gegen targetType, opponentEntries ignoriert),
 * 2="Team" (bester Konter gegen opponentEntries, je "speziesName|level", targetType ignoriert).
 * Spezies-Namen kommen bereits client-seitig auf den internen englischen Namen aufgelöst
 * (resolveSearchQuery), wie bei allen anderen Suchfeldern dieser Codebase.
 */
public record TeamBuilderRequestPacket(int mode, String targetType, List<String> opponentEntries) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TeamBuilderRequestPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "team_builder_request"));

    public static final StreamCodec<ByteBuf, TeamBuilderRequestPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, TeamBuilderRequestPacket::mode,
        ByteBufCodecs.STRING_UTF8, TeamBuilderRequestPacket::targetType,
        ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), TeamBuilderRequestPacket::opponentEntries,
        TeamBuilderRequestPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TeamBuilderRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            TeamBuilderHelper.TeamResult result;
            switch (packet.mode()) {
                case 1 -> result = TeamBuilderHelper.computeAgainstType(player, packet.targetType());
                case 2 -> {
                    List<TeamBuilderHelper.OpponentEntry> opponents = new ArrayList<>();
                    for (String entry : packet.opponentEntries()) {
                        String[] parts = entry.split("\\|", -1);
                        if (parts.length != 2 || parts[0].isBlank()) continue;
                        TeamBuilderHelper.OpponentEntry o = TeamBuilderHelper.resolveOpponentTypes(parts[0]);
                        if (o != null) opponents.add(o);
                    }
                    result = opponents.isEmpty()
                        ? new TeamBuilderHelper.TeamResult(List.of(), List.of())
                        : TeamBuilderHelper.computeAgainstOpponents(player, opponents);
                }
                default -> result = TeamBuilderHelper.computeGeneral(player);
            }

            List<String> lines = new ArrayList<>();
            for (TeamBuilderHelper.Candidate c : result.primary) {
                lines.add(c.speciesId + "|" + c.aspects + "|" + c.level + "|P|" + String.join(",", c.reasons));
            }
            for (TeamBuilderHelper.Candidate c : result.alternates) {
                lines.add(c.speciesId + "|" + c.aspects + "|" + c.level + "|A|" + String.join(",", c.reasons));
            }
            PacketDistributor.sendToPlayer(player, new TeamBuilderResponsePacket(lines));
        });
    }
}
