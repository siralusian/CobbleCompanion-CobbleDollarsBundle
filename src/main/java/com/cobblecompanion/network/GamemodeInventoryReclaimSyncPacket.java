package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.client.data.ClientGamemodeInventoryHelper;
import com.cobblecompanion.data.GamemodeInventorySyncManager;
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
 * Server -> Client: eigene ausstehende Abhol-Warteschlange (siehe GamemodeInventorySyncManager -
 * entsteht, wenn die Gamemode-Inventar-Trennung deaktiviert wird oder ein Admin einen Spieler
 * zurücksetzt) für das Home-Tab-Panel. Jeder Eintrag = ein GameType-Name + Item-Gesamtanzahl.
 */
public record GamemodeInventoryReclaimSyncPacket(List<String> gameTypeNames, List<Integer> itemCounts) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<GamemodeInventoryReclaimSyncPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "gamemode_inventory_reclaim_sync"));

    public static final StreamCodec<ByteBuf, GamemodeInventoryReclaimSyncPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), GamemodeInventoryReclaimSyncPacket::gameTypeNames,
        ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.VAR_INT), GamemodeInventoryReclaimSyncPacket::itemCounts,
        GamemodeInventoryReclaimSyncPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void sendTo(ServerPlayer player) {
        List<String> names = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        for (GamemodeInventorySyncManager.ReclaimEntry entry : GamemodeInventorySyncManager.getReclaimEntries(player.getUUID())) {
            names.add(entry.gameTypeName);
            counts.add(GamemodeInventorySyncManager.countItems(entry));
        }
        PacketDistributor.sendToPlayer(player, new GamemodeInventoryReclaimSyncPacket(names, counts));
    }

    public static void handle(GamemodeInventoryReclaimSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            List<ClientGamemodeInventoryHelper.ReclaimEntryView> views = new ArrayList<>();
            for (int i = 0; i < packet.gameTypeNames().size(); i++) {
                views.add(new ClientGamemodeInventoryHelper.ReclaimEntryView(packet.gameTypeNames().get(i), packet.itemCounts().get(i)));
            }
            ClientGamemodeInventoryHelper.setReclaimEntries(views);
        });
    }
}
