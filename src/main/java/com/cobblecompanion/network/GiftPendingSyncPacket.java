package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.client.data.ClientGiftHelper;
import com.cobblecompanion.data.GiftManager;
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
 * Server -> Client: offene Pokemon-Geschenke an diesen Spieler
 * ("fromUuid|fromName|pokemonUuid|speciesId|level|aspects|nickname|caughtBallId(oder leer)|
 * natureId(oder leer)|abilityId(oder leer)" je Eintrag), fürs Annehmen-Overlay im Home-Tab.
 * Gesendet nach jedem neuen Angebot (an den Empfänger, falls online), nach jeder Annahme/
 * Ablehnung (Rest-Liste) sowie bei Login.
 */
public record GiftPendingSyncPacket(List<String> entries) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<GiftPendingSyncPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "gift_pending_sync"));

    public static final StreamCodec<ByteBuf, GiftPendingSyncPacket> CODEC =
        ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8)
            .map(
                (ArrayList<String> list) -> new GiftPendingSyncPacket(list),
                (GiftPendingSyncPacket packet) -> new ArrayList<>(packet.entries()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void buildAndSend(ServerPlayer player) {
        List<String> lines = new ArrayList<>();
        for (GiftManager.PendingGift gift : GiftManager.getPendingFor(player.getUUID())) {
            lines.add(gift.fromUuid() + "|" + gift.fromName() + "|" + gift.pokemonUuid() + "|" + gift.speciesId()
                + "|" + gift.level() + "|" + gift.aspects() + "|" + gift.nickname()
                + "|" + (gift.caughtBallId() != null ? gift.caughtBallId() : "")
                + "|" + gift.natureId() + "|" + gift.abilityId());
        }
        PacketDistributor.sendToPlayer(player, new GiftPendingSyncPacket(lines));
    }

    // Kein @OnlyIn(Dist.CLIENT): siehe LivingDexPacket.handle (RuntimeDistCleaner).
    public static void handle(GiftPendingSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientGiftHelper.setPendingForMe(packet.entries()));
    }
}
