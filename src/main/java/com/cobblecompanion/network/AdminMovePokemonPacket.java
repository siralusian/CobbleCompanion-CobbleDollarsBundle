package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.data.AdminPermissionManager;
import com.cobblecompanion.data.PlayerDataHelper;
import com.cobblemon.mod.common.api.storage.party.PartyPosition;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.storage.pc.PCPosition;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Client -> Server: verschiebt (bzw. tauscht) ein Pokemon innerhalb der PC/Party-Ansicht eines
 * ANDEREN Spielers im Professor-Tab (AdminOp). Eigenes Packet statt Cobblemons
 * MovePCPokemonPacket/SwapPCPokemonPacket/Move*ToParty*Packet & Co., weil deren Handler laut
 * Bytecode-Analyse KEINE storeID mitbekommen und stattdessen immer auf der Storage DES SENDERS
 * (also des Admins) arbeiten - für das Bearbeiten eines FREMDEN Spielers unbrauchbar bzw. sogar
 * gefährlich (könnte versehentlich die eigene Party/PC des Admins verändern). Arbeitet stattdessen
 * UUID-basiert über PlayerDataHelper, genau wie die anderen Admin*Packet-Handler.
 */
public record AdminMovePokemonPacket(UUID targetUuid, UUID pokemonUuid,
                                      boolean fromParty, int fromBox, int fromSlot,
                                      boolean toParty, int toBox, int toSlot) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AdminMovePokemonPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "admin_move_pokemon"));

    // StreamCodec.composite unterstützt maximal 5 Feld-Paare (siehe AdminEditPokemonPacket-Kommentar)
    // - die 6 Positions-Werte werden deshalb zu einem kommagetrennten String zusammengefasst.
    public static final StreamCodec<ByteBuf, AdminMovePokemonPacket> CODEC = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC, AdminMovePokemonPacket::targetUuid,
        UUIDUtil.STREAM_CODEC, AdminMovePokemonPacket::pokemonUuid,
        ByteBufCodecs.STRING_UTF8,
        (AdminMovePokemonPacket p) -> p.fromParty() + "," + p.fromBox() + "," + p.fromSlot()
            + "," + p.toParty() + "," + p.toBox() + "," + p.toSlot(),
        (targetUuid, pokemonUuid, packed) -> {
            String[] parts = packed.split(",", -1);
            return new AdminMovePokemonPacket(targetUuid, pokemonUuid,
                Boolean.parseBoolean(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
                Boolean.parseBoolean(parts[3]), Integer.parseInt(parts[4]), Integer.parseInt(parts[5]));
        });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AdminMovePokemonPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer admin)) return;
            if (!AdminPermissionManager.isAdminOp(admin.getUUID())) return;
            MinecraftServer server = admin.getServer();
            if (server == null) return;
            UUID targetUuid = packet.targetUuid();

            Pokemon pokemon = PlayerDataHelper.findPokemonByUuid(targetUuid, packet.pokemonUuid(), server.registryAccess());
            if (pokemon == null) return;

            PlayerPartyStore party = PlayerDataHelper.getPartyStoreByUuid(targetUuid, server.registryAccess());
            PCStore pc = PlayerDataHelper.getPCStoreByUuid(targetUuid, server.registryAccess());

            Pokemon destOccupant = packet.toParty()
                ? (party != null ? party.get(new PartyPosition(packet.toSlot())) : null)
                : (pc != null ? pc.get(new PCPosition(packet.toBox(), packet.toSlot())) : null);

            if (packet.fromParty()) {
                if (party != null) party.remove(pokemon);
            } else {
                if (pc != null) pc.remove(pokemon);
            }

            if (destOccupant != null) {
                if (packet.toParty()) {
                    if (party != null) party.remove(destOccupant);
                } else {
                    if (pc != null) pc.remove(destOccupant);
                }
            }

            if (packet.toParty()) {
                if (party != null) party.set(new PartyPosition(packet.toSlot()), pokemon);
            } else {
                if (pc != null) pc.set(new PCPosition(packet.toBox(), packet.toSlot()), pokemon);
            }

            if (destOccupant != null) {
                if (packet.fromParty()) {
                    if (party != null) party.set(new PartyPosition(packet.fromSlot()), destOccupant);
                } else {
                    if (pc != null) pc.set(new PCPosition(packet.fromBox(), packet.fromSlot()), destOccupant);
                }
            }

            // WICHTIG (Live-Test-Fund, vom User selbst korrekt erklärt): eine echte Cobblemon-Party
            // hat NIE Lücken - das Spiel schiebt beim Einfügen immer an den vordersten freien Platz.
            // set()/remove() auf PartyStore sind aber laut Bytecode-Analyse reine indizierte
            // Operationen ohne jede Auto-Auffüll-Logik - die "Lücken-Sperre" existiert offenbar nur
            // in Cobblemons eigenen High-Level-UI-Flows (die wir bewusst umgehen, siehe Klassenkommentar
            // oben). Ohne diesen Schritt könnte unser Admin-Tool also einen Party-Zustand erzeugen, den
            // das echte Spiel nie zulässt (z.B. nur Slot 3 belegt, 1+2 frei) - PC-Boxen sind davon
            // NICHT betroffen (dort sind Lücken normal und werden vom Spiel selbst nie automatisch
            // aufgefüllt), deshalb nur bei Party-Beteiligung kompaktieren.
            if (party != null && (packet.fromParty() || packet.toParty())) {
                compactParty(party);
            }

            ProfessorPCRequestPacket.buildAndSend(admin, server, targetUuid);
        });
    }

    /**
     * Schiebt alle Party-Mitglieder lückenlos nach vorne (Slot 0 zuerst), relative Reihenfolge
     * bleibt erhalten - bildet Cobblemons eigenes "Lücken sind unmöglich"-Verhalten nach, das die
     * Roh-API selbst nicht durchsetzt (siehe Aufrufer-Kommentar).
     *
     * WICHTIG (Live-Test-Fund - Ursache der gemeldeten Pokemon-Vervielfältigung): set() verdrängt
     * laut PokemonStore.set()-Bytecode nur den Inhalt der ZIEL-Position, räumt aber NIE die ALTE
     * Position des zu setzenden Pokemons auf (das übernimmt setAtPosition() nur für die eine
     * übergebene Position - nicht für die davor). Ein naives "an jeder Zielposition neu setzen"
     * ließ dadurch pro verschobenem Pokemon eine Geisterkopie an dessen alter Position zurück.
     * Fix: ERST werden ALLE aktuell belegten Slots per remove() komplett geleert (jedes Pokemon
     * danach nur noch als reine Objektreferenz in "occupied" vorhanden, nirgends mehr in der
     * Party), ERST DANACH werden sie in kompakter Reihenfolge neu gesetzt - so kann keine alte
     * Position je stehen bleiben.
     */
    private static void compactParty(PlayerPartyStore party) {
        List<Pokemon> occupied = new ArrayList<>();
        for (int i = 0; i < party.size(); i++) {
            Pokemon p = party.get(i);
            if (p != null) occupied.add(p);
        }
        for (Pokemon p : occupied) {
            party.remove(p);
        }
        for (int i = 0; i < occupied.size(); i++) {
            party.set(new PartyPosition(i), occupied.get(i));
        }
    }
}
