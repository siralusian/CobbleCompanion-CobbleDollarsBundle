package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.data.GamemodeInventorySyncManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> Server: Spieler klickt "Abholen" auf einen Eintrag in seiner Abhol-Warteschlange
 * (siehe GamemodeInventorySyncManager.claim - fügt so viel wie möglich ins aktuelle Inventar ein,
 * Rest bleibt stehen). Danach wird die aktualisierte Warteschlange erneut gesendet.
 */
public record GamemodeInventoryReclaimClaimPacket(int index) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<GamemodeInventoryReclaimClaimPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "gamemode_inventory_reclaim_claim"));

    public static final StreamCodec<ByteBuf, GamemodeInventoryReclaimClaimPacket> CODEC =
        ByteBufCodecs.VAR_INT.map(GamemodeInventoryReclaimClaimPacket::new, GamemodeInventoryReclaimClaimPacket::index);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(GamemodeInventoryReclaimClaimPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            GamemodeInventorySyncManager.ClaimResult result = GamemodeInventorySyncManager.claim(player, packet.index());
            if (result == GamemodeInventorySyncManager.ClaimResult.PARTIAL) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatableWithFallback(
                    "cobblecompanion.msg.gamemode_inventory_reclaim_partial",
                    "Not everything fit into your inventory - the rest stays in the reclaim queue."));
            }
            GamemodeInventoryReclaimSyncPacket.sendTo(player);
        });
    }
}
