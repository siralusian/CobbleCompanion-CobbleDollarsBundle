package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.data.AdminPermissionManager;
import com.cobblecompanion.data.FriendsManager;
import com.cobblecompanion.data.PlayerDataHelper;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.storage.pc.PCBox;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Client -> Server: "PC"-Knopf im Professor-Tab wurde geklickt - fordert PC-Boxen + Team des
 * ausgewählten Spielers an. Server prüft Op/AdminOp erneut, serialisiert jedes Pokemon per NBT
 * (Pokemon.saveToNBT) und schreibt Box/Slot-Position direkt als Extra-Felder in dessen Tag (statt
 * einer zweiten parallelen Liste - einfacher zu synchron halten). Team wird mitgeschickt, damit
 * Cobblemons PCGUI (die immer beide Storages gleichzeitig zeigt) vollständig ist.
 */
public record ProfessorPCRequestPacket(UUID targetUuid) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ProfessorPCRequestPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "professor_pc_request"));

    public static final StreamCodec<ByteBuf, ProfessorPCRequestPacket> CODEC =
        UUIDUtil.STREAM_CODEC.map(ProfessorPCRequestPacket::new, ProfessorPCRequestPacket::targetUuid);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ProfessorPCRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!AdminPermissionManager.isOp(player.getUUID())) return;
            MinecraftServer server = player.getServer();
            if (server == null) return;
            buildAndSend(player, server, packet.targetUuid());
        });
    }

    /**
     * Baut die PC-Antwort neu und schickt sie an den Admin - genutzt vom initialen "PC"-Knopf-
     * Klick UND von AdminEditPokemonPacket/AdminReleasePokemonPacket, damit die eingebettete
     * PCGUI nach einer Änderung sofort den frischen Stand zeigt (siehe CompanionScreen.
     * checkProfessorSubScreenUpdate(), das bei neuer pcDataVersion automatisch neu baut).
     */
    public static void buildAndSend(ServerPlayer admin, MinecraftServer server, UUID targetUuid) {
        PCStore pcStore = PlayerDataHelper.getPCStoreByUuid(targetUuid, server.registryAccess());
        if (pcStore == null) return;

        List<String> boxNames = new ArrayList<>();
        List<CompoundTag> pcEntries = new ArrayList<>();
        for (PCBox box : pcStore.getBoxes()) {
            String name = box.getName();
            boxNames.add(name != null ? name : "");
            for (Map.Entry<Integer, Pokemon> slotEntry : box.getNonEmptySlots().entrySet()) {
                CompoundTag tag = slotEntry.getValue().saveToNBT(server.registryAccess(), new CompoundTag());
                tag.putInt("cc_box", box.getBoxNumber());
                tag.putInt("cc_slot", slotEntry.getKey());
                pcEntries.add(tag);
            }
        }

        // WICHTIG (Live-Test-Fund): PlayerDataHelper.getPartyByUuid() iteriert über
        // PartyStore.iterator(), das intern Kotlins filterNotNull() nutzt und leere Slots damit
        // STILLSCHWEIGEND ÜBERSPRINGT - die zurückgegebene Liste ist also IMMER lückenlos, egal
        // wie die echte Party aussieht. Würde man diese kompaktierte Liste einfach 1:1 in
        // partyEntries übernehmen (ohne Slot-Info), würde der Client beim Wiederaufbau
        // (buildProfessorPCScreen()) jedes Pokemon fälschlich an der LISTEN-Position statt am
        // ECHTEN Party-Slot einsortieren - bei einer lückenhaften Party (z.B. nur Slot 2 belegt)
        // würde das Pokemon dann bei jedem Refresh fälschlich auf Slot 0 "springen". Deshalb hier
        // wie bei den PC-Boxen (cc_box/cc_slot) den ECHTEN Slot-Index separat mitschicken, per
        // direktem indiziertem PartyStore.get(int)-Zugriff statt über den lückenlosen Iterator.
        PlayerPartyStore partyStore = PlayerDataHelper.getPartyStoreByUuid(targetUuid, server.registryAccess());
        List<CompoundTag> partyEntries = new ArrayList<>();
        if (partyStore != null) {
            for (int i = 0; i < partyStore.size(); i++) {
                Pokemon p = partyStore.get(i);
                if (p == null) continue;
                CompoundTag tag = p.saveToNBT(server.registryAccess(), new CompoundTag());
                tag.putInt("cc_slot", i);
                partyEntries.add(tag);
            }
        }

        String targetName = FriendsManager.getAllKnownPlayers().getOrDefault(targetUuid, targetUuid.toString());
        PacketDistributor.sendToPlayer(admin, new ProfessorPCResponsePacket(targetName, boxNames, pcEntries, partyEntries));
    }
}
