package com.cobblecompanion.cobbledollarscreate.network;

import com.cobblecompanion.cobbledollarscreate.CobbleCompanionDollarsCreate;
import com.cobblecompanion.cobbledollarscreate.ContentObserverConfigManager;
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
 * Client -> Server: Nutzer-Vorgabe (verknüpfte Zähler/Abzieher-Gruppen, Grün-Rahmen-Hervorhebung
 * um alle Blöcke der Gruppe, gleiches Muster wie Creates eigenes Lagernetzwerk-Highlight - siehe
 * ContentObserverGroupHighlightRenderer). Client schickt das, sobald er lokal feststellt, dass der
 * Spieler ein auf groupId abgestimmtes Item hält (siehe ClientEventHandler) - sowohl bei einem
 * Wechsel der Abstimmung als auch periodisch, solange das Item gehalten bleibt (Gruppenmitglieder
 * können sich ändern, während man das Item hält).
 */
public record ContentObserverGroupHighlightRequestPacket(String groupId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ContentObserverGroupHighlightRequestPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanionDollarsCreate.MOD_ID, "content_observer_group_highlight_request"));

    public static final StreamCodec<ByteBuf, ContentObserverGroupHighlightRequestPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, ContentObserverGroupHighlightRequestPacket::groupId,
        ContentObserverGroupHighlightRequestPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ContentObserverGroupHighlightRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            // Nutzer-Vorgabe: dieselbe Zugriffsschwelle wie das Abstimmen selbst - keine
            // Positionsdaten an Nicht-OPs herausgeben.
            if (!player.hasPermissions(2)) return;
            if (packet.groupId() == null || packet.groupId().isBlank()) return;

            List<net.minecraft.core.BlockPos> positions = ContentObserverConfigManager.findGroupPositions(player.level().dimension(), packet.groupId());
            PacketDistributor.sendToPlayer(player, new ContentObserverGroupHighlightSyncPacket(new ArrayList<>(positions)));
        });
    }
}
