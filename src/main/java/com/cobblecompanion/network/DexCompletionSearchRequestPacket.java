package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.data.DexCompletionHelper;
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
 * Client -> Server: Suchfeld der Dex-Vervollständigungshilfe (ToDo-Tab, rechte Hälfte) wurde mit
 * einem Pokémon-Namen bestätigt - fragt Vorentwicklung + alle direkten Entwicklungsziele mit
 * Bedingung ab (siehe DexCompletionHelper.describeSpecies()), unabhängig davon, ob die Art bereits
 * gefangen/besessen ist.
 */
public record DexCompletionSearchRequestPacket(String speciesName) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DexCompletionSearchRequestPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "dex_completion_search_request"));

    public static final StreamCodec<ByteBuf, DexCompletionSearchRequestPacket> CODEC =
        ByteBufCodecs.STRING_UTF8.map(DexCompletionSearchRequestPacket::new, DexCompletionSearchRequestPacket::speciesName);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DexCompletionSearchRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            DexCompletionHelper.SpeciesInfo info = DexCompletionHelper.describeSpecies(player, packet.speciesName());

            List<String> evolveLines = new ArrayList<>();
            for (DexCompletionHelper.EvolveEntry e : info.evolutions) {
                evolveLines.add(encodeEntry(e));
            }

            String preEvo = info.preEvolutionId != null ? info.preEvolutionId.toString() : "";
            String preEvoEdge = info.preEvolutionEdge != null ? encodeEntry(info.preEvolutionEdge) : "";
            String selfStatus = info.hasCaughtPokedex + "," + info.ownsLivingDex + ","
                + (info.speciesId != null ? info.speciesId : "");
            PacketDistributor.sendToPlayer(player, new DexCompletionSearchResponsePacket(packet.speciesName(), preEvo, preEvoEdge, evolveLines, selfStatus));
        });
    }

    private static String encodeEntry(DexCompletionHelper.EvolveEntry e) {
        String categoryCode = switch (e.category) {
            case LEVEL -> "L";
            case STONE -> "S";
            case FRIENDSHIP -> "F";
            case TRADE -> "T";
            case OTHER -> "O";
        };
        return categoryCode + "|" + e.fromSpeciesId + "|" + e.toSpeciesId + "|"
            + e.requiredLevel + "|" + e.ownedLevel + "|" + e.itemPath + "|" + e.resultAspects;
    }
}
