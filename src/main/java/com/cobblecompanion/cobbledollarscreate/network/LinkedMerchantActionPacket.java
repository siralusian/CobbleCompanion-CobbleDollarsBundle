package com.cobblecompanion.cobbledollarscreate.network;

import com.cobblecompanion.cobbledollarscreate.CobbleCompanionDollarsCreate;
import com.cobblecompanion.cobbledollarscreate.data.CobbleMerchantSellManager;
import com.cobblecompanion.data.CustomNpcTraderLinkManager;
import com.cobblecompanion.integrations.ModAvailability;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Client -> Server: Aktion aus dem Verknüpfte-NPCs-Panel im Preis-Editor (siehe
 * StockTickerPriceScreen) - Kiste/Ticker trennen oder Löschen. Nur echte Minecraft-OPs (gleiches
 * Muster wie der restliche Preis-Editor, bewusst NICHT über AdminPermissionManager).
 *
 * "Löschen": CustomNPC -> npc.delete() (siehe CustomNpcDeletionBridge, Isolationsgrenze - nur
 * aufgerufen wenn CustomNPCs installiert ist), sonst (echter CobbleMerchant) -> entity.kill(level)
 * (entspricht dem Vanilla-/kill-Befehl). Die Ziel-Entity wird über ALLE Server-Level gesucht (die
 * Verknüpfung speichert nur die Position des LAGERTICKERS, nicht die des NPCs selbst).
 */
public record LinkedMerchantActionPacket(UUID targetUuid, boolean isCustomNpc, int action) implements CustomPacketPayload {

    public static final int ACTION_DISCONNECT_STORAGE = 0;
    public static final int ACTION_DISCONNECT_TICKER = 1;
    public static final int ACTION_DELETE = 2;

    public static final CustomPacketPayload.Type<LinkedMerchantActionPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanionDollarsCreate.MOD_ID, "linked_merchant_action"));

    public static final StreamCodec<ByteBuf, LinkedMerchantActionPacket> CODEC = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC, LinkedMerchantActionPacket::targetUuid,
        ByteBufCodecs.BOOL, LinkedMerchantActionPacket::isCustomNpc,
        ByteBufCodecs.VAR_INT, LinkedMerchantActionPacket::action,
        LinkedMerchantActionPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LinkedMerchantActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!player.hasPermissions(2)) return;

            UUID uuid = packet.targetUuid();
            switch (packet.action()) {
                case ACTION_DISCONNECT_STORAGE -> {
                    if (packet.isCustomNpc()) CustomNpcTraderLinkManager.removeStorageLink(uuid);
                    else CobbleMerchantSellManager.removeLink(uuid);
                }
                case ACTION_DISCONNECT_TICKER -> {
                    if (packet.isCustomNpc()) CustomNpcTraderLinkManager.removeTickerLink(uuid);
                    else CobbleMerchantSellManager.removeBuyTickerLink(uuid);
                }
                case ACTION_DELETE -> deleteEntity(player.server, uuid, packet.isCustomNpc());
                default -> {}
            }
        });
    }

    private static void deleteEntity(MinecraftServer server, UUID uuid, boolean isCustomNpc) {
        Entity target = null;
        for (ServerLevel level : server.getAllLevels()) {
            Entity found = level.getEntity(uuid);
            if (found != null) {
                target = found;
                break;
            }
        }
        if (target == null) return;

        if (isCustomNpc && ModAvailability.isCustomNpcsAvailable()) {
            com.cobblecompanion.integrations.customnpcs.CustomNpcDeletionBridge.delete(target);
        } else if (target instanceof LivingEntity living) {
            living.kill();
        } else {
            target.discard();
        }
    }
}
