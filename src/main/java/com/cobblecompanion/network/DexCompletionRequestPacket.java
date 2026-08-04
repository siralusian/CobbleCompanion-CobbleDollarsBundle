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
 * Client -> Server: ToDo-Tab's rechte Hälfte ("Dex-Vervollständigungshilfe") wurde geöffnet oder
 * der Pokédex/Living-Dex-Modus hat sich geändert - fordert die vollständige Liste an, was dem
 * Spieler noch fehlt (siehe DexCompletionHelper). pokedexMode kommt vom Client (dieselbe
 * Pokédex/Living-Dex-Einstellung wie bei der PC-Sortierhilfe, ClientSettingsHelper.isPcSortModePokedex()).
 * ldpCategories: NUR im Living-Dex+-Modus nicht-leer (siehe HomeSummaryRequestPacket-Konvention) -
 * ergänzt die Fangen-Liste um Varianten-Kategorien (siehe DexCompletionHelper.compute()). Enthält
 * seit dem Umbau "Regionalformen-Unterkategorien von den Oberkategorien trennen" auch synthetische
 * Pro-Region-IDs (siehe LivingDexPlusRegistry.regionSyntheticId()) - ein eigenes
 * regionalFormsEncoded-Feld ist dadurch nicht mehr nötig.
 */
public record DexCompletionRequestPacket(boolean pokedexMode, List<Integer> ldpCategories) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DexCompletionRequestPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "dex_completion_request"));

    public static final StreamCodec<ByteBuf, DexCompletionRequestPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, DexCompletionRequestPacket::pokedexMode,
        ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.VAR_INT), DexCompletionRequestPacket::ldpCategories,
        DexCompletionRequestPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DexCompletionRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            DexCompletionHelper.Result result = DexCompletionHelper.compute(player, packet.pokedexMode(), packet.ldpCategories());

            List<String> catchLines = new ArrayList<>();
            for (DexCompletionHelper.CatchEntry e : result.catchEntries) {
                StringBuilder covered = new StringBuilder();
                for (int i = 0; i < e.coveredSpeciesIds.size(); i++) {
                    if (i > 0) covered.append(",");
                    covered.append(e.coveredSpeciesIds.get(i));
                }
                catchLines.add(e.speciesId + "|" + e.selfNeeded + "|" + e.extraCopies + "|" + covered
                    + "|" + e.formName + "|" + e.shiny);
            }

            List<String> evolveLines = new ArrayList<>();
            for (DexCompletionHelper.EvolveEntry e : result.evolveEntries) {
                String categoryCode = switch (e.category) {
                    case LEVEL -> "L";
                    case STONE -> "S";
                    case FRIENDSHIP -> "F";
                    case TRADE -> "T";
                    case OTHER -> "O";
                };
                evolveLines.add(categoryCode + "|" + e.fromSpeciesId + "|" + e.toSpeciesId + "|"
                    + e.requiredLevel + "|" + e.ownedLevel + "|" + e.itemPath);
            }

            PacketDistributor.sendToPlayer(player, new DexCompletionResponsePacket(catchLines, evolveLines));
        });
    }
}
