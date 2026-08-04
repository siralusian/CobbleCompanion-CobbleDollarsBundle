package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.data.AdminPermissionManager;
import com.cobblecompanion.data.GamemodeInventorySyncManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> Server: AdminOp schaltet die Gamemode-Inventar-Trennung komplett ein/aus (Settings >
 * Gamemodes). Beim Ausschalten wandern alle gespeicherten, aktuell nicht aktiven Gamemode-
 * Inventare automatisch in die Abhol-Warteschlange statt verloren zu gehen (siehe
 * GamemodeInventorySyncManager.disableWithOrphanHandling).
 */
public record GamemodeInventorySyncTogglePacket(boolean enabled) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<GamemodeInventorySyncTogglePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "gamemode_inventory_sync_toggle"));

    public static final StreamCodec<ByteBuf, GamemodeInventorySyncTogglePacket> CODEC =
        ByteBufCodecs.BOOL.map(GamemodeInventorySyncTogglePacket::new, GamemodeInventorySyncTogglePacket::enabled);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(GamemodeInventorySyncTogglePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!AdminPermissionManager.isAdminOp(player.getUUID())) return;
            if (packet.enabled()) {
                GamemodeInventorySyncManager.setEnabled(true);
            } else {
                GamemodeInventorySyncManager.disableWithOrphanHandling(player.getServer());
                // Betroffene Online-Spieler sofort über ihre neue Abhol-Warteschlange informieren,
                // statt bis zum nächsten Login zu warten.
                for (ServerPlayer online : player.getServer().getPlayerList().getPlayers()) {
                    GamemodeInventoryReclaimSyncPacket.sendTo(online);
                }
            }
            GamemodeInventorySyncStatusPacket.sendTo(player);
        });
    }
}
