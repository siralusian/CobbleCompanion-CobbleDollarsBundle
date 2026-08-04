package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.data.DexCompletionHelper;
import com.cobblecompanion.data.PCSlotCheckHelper;
import com.cobblecompanion.data.ServerDataHelper;
import com.cobblecompanion.data.TodoHelper;
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
 * Client -> Server: Home-Tab wurde geöffnet - fordert die Dashboard-Zusammenfassung an (Dex-
 * Zähler, wie viele Entwicklungen gerade möglich sind, wie viele Pokémon noch fehlen).
 * pokedexMode kommt vom Client (dieselbe Einstellung wie bei der Dex-Vervollständigungshilfe,
 * ClientSettingsHelper.isModusPokedex() - siehe DexCompletionRequestPacket).
 * slotCheckEnabled/pcSortStartBox kommen von der neuen Settings-Option "Slot Prüfung" (PC-
 * Kategorie) - die Startbox-Einstellung wird gebraucht, weil PCSlotCheckHelper dieselbe Box-
 * Zuordnungs-Formel wie die clientseitige PCSortHelper nachbildet, dortige Client-Settings dem
 * Server aber sonst unbekannt sind.
 * pcSortMode/ldpCategories sind eine EIGENE, unabhängige Einstellung (PC-Sortiermodus) - bewusst
 * NICHT dasselbe wie pokedexMode oben (das ist ClientSettingsHelper.isModusPokedex(), der ToDo-/
 * Dex-Vervollständigungs-Filter) - siehe die Settings-Entkopplungs-Historie dieses Projekts.
 * ldpCategories enthält seit dem Umbau "Regionalformen-Unterkategorien von den Oberkategorien
 * trennen" NEBEN den echten Kategorie-IDs auch synthetische Pro-Region-IDs (siehe
 * LivingDexPlusRegistry.regionSyntheticId()) - ein eigenes regionalFormsEncoded-Feld ist dadurch
 * nicht mehr nötig, die Regionen-Auswahl steckt vollständig in dieser einen Liste.
 */
public record HomeSummaryRequestPacket(boolean pokedexMode, boolean slotCheckEnabled, int pcSortStartBox,
                                        int pcSortMode, List<Integer> ldpCategories)
    implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<HomeSummaryRequestPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "home_summary_request"));

    public static final StreamCodec<ByteBuf, HomeSummaryRequestPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, HomeSummaryRequestPacket::pokedexMode,
        ByteBufCodecs.BOOL, HomeSummaryRequestPacket::slotCheckEnabled,
        ByteBufCodecs.VAR_INT, HomeSummaryRequestPacket::pcSortStartBox,
        ByteBufCodecs.VAR_INT, HomeSummaryRequestPacket::pcSortMode,
        ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.VAR_INT), HomeSummaryRequestPacket::ldpCategories,
        HomeSummaryRequestPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HomeSummaryRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            ServerDataHelper.DexCounts counts = ServerDataHelper.getPlayerDexCounts(player.getServer(), player.getUUID());

            int evolveReady = 0;
            for (String line : TodoHelper.getTodoEntries(player)) {
                String[] parts = line.split("\\|", -1);
                if (parts.length > 6 && "true".equals(parts[6])) evolveReady++;
            }

            // BUGFIX: .size() zählte nur Wurzel-Arten, nicht die tatsächlich benötigte Stückzahl
            // (Living-Dex-Mengen-Konzept, siehe DexCompletionHelper.totalCatchQuantity()).
            // ldpCategories nur im Living-Dex+-Modus (pcSortMode==2) mitgeben, siehe
            // DexCompletionHelper.compute() - sonst identisch zum reinen Pokédex-/Living-Dex-Fall.
            List<Integer> ldpCategoriesForCatch = packet.pcSortMode() == 2 ? packet.ldpCategories() : List.of();
            int catchNeeded = DexCompletionHelper.totalCatchQuantity(
                DexCompletionHelper.compute(player, packet.pokedexMode(), ldpCategoriesForCatch).catchEntries);

            PacketDistributor.sendToPlayer(player,
                new HomeSummaryResponsePacket(counts.seen, counts.caught, counts.living, evolveReady, catchNeeded));

            if (packet.slotCheckEnabled()) {
                int misplaced = PCSlotCheckHelper.countMisplaced(player, packet.pcSortStartBox(), packet.pcSortMode(),
                    packet.ldpCategories());
                PacketDistributor.sendToPlayer(player, new HomeSlotCheckResponsePacket(misplaced));
            }
        });
    }
}
