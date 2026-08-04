package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.data.AdminPermissionManager;
import com.cobblecompanion.data.PlayerDataHelper;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokedex.PokedexManager;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Client -> Server: "Zurücksetzen"-Button im Professor-Tab (AdminOp, zweifach bestätigt im
 * CompanionScreen - siehe resetPlayerConfirmStage). Löscht UNWIDERRUFLICH Team, PC und
 * Pokédex/Living-Dex-Fortschritt des Zielspielers - Living Dex ergibt sich rein aus dem aktuellen
 * Pokemon-Besitz (Team+PC), ein Löschen davon ist also automatisch mit erledigt.
 */
public record AdminResetPlayerPacket(UUID targetUuid) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AdminResetPlayerPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "admin_reset_player"));

    public static final StreamCodec<ByteBuf, AdminResetPlayerPacket> CODEC = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC, AdminResetPlayerPacket::targetUuid,
        AdminResetPlayerPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AdminResetPlayerPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer admin)) return;
            if (!AdminPermissionManager.isAdminOp(admin.getUUID())) return;
            MinecraftServer server = admin.getServer();
            if (server == null) return;
            UUID targetUuid = packet.targetUuid();

            PlayerPartyStore party = PlayerDataHelper.getPartyStoreByUuid(targetUuid, server.registryAccess());
            if (party != null) {
                List<Pokemon> toRemove = new ArrayList<>();
                for (Pokemon p : party) toRemove.add(p);
                for (Pokemon p : toRemove) party.remove(p);
            }

            PCStore pc = PlayerDataHelper.getPCStoreByUuid(targetUuid, server.registryAccess());
            if (pc != null) pc.clearPC();

            try {
                PokedexManager pokedex = Cobblemon.INSTANCE.getPlayerDataManager().getPokedexData(targetUuid);
                if (pokedex != null) pokedex.getSpeciesRecords().clear();
            } catch (Exception e) {
                CobbleCompanion.LOGGER.error("[CC] Failed to reset Pokedex data for " + targetUuid, e);
            }

            // Direktes Map.clear()/PCStore.clearPC() umgeht Cobblemons normale Change-Hooks, die
            // sonst den betroffenen Client automatisch informieren - ohne diesen manuellen Resync
            // bleiben mehrere CLIENT-seitige Caches des Zielspielers (falls online) bis zum
            // nächsten Relog fälschlich auf dem alten Stand, obwohl der Server bereits korrekt leer
            // ist (bestätigt per Live-Test, u.a. hing dadurch die PC-Sortierhilfe noch an
            // längst gelöschten Pokemon fest, die nur noch im lokalen ClientPC-Cache "geisterten"):
            // 1) Pokédex-Zähler (PlayerInstancedDataStoreManager) - schon in einer früheren Runde
            //    gefixt. 2) PC/Team-Storage (eigenes Subsystem, PCLinkManager-basiert) - NEU hier
            //    gefixt via PCStore/PlayerPartyStore.sendTo(), das denselben Sync-Push auslöst wie
            //    Cobblemon es selbst beim Öffnen des PC/Teams tut.
            try {
                ServerPlayer targetPlayer = server.getPlayerList().getPlayer(targetUuid);
                if (targetPlayer != null) {
                    Cobblemon.INSTANCE.getPlayerDataManager().syncAllToPlayer(targetPlayer);
                    if (party != null) party.sendTo(targetPlayer);
                    if (pc != null) pc.sendTo(targetPlayer);
                }
            } catch (Exception e) {
                CobbleCompanion.LOGGER.error("[CC] Failed to resync reset player data to client for " + targetUuid, e);
            }

            ProfessorPCRequestPacket.buildAndSend(admin, server, targetUuid);
        });
    }
}
