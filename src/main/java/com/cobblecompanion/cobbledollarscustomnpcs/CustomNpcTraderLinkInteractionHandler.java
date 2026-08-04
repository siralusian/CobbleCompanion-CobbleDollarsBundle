package com.cobblecompanion.cobbledollarscustomnpcs;

import com.cobblecompanion.cobbledollarscustomnpcs.data.CustomNpcMerchantShopManager;
import com.cobblecompanion.data.AdminPermissionManager;
import com.cobblecompanion.data.ClientKeyStateManager;
import com.cobblecompanion.data.CustomNpcTraderLinkManager;
import com.cobblecompanion.integrations.ModAvailability;
import com.cobblecompanion.integrations.create.CreateStockTickerBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.items.IItemHandler;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.roles.RoleTrader;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Verknüpft einen CustomNPCs-Trader-NPC per Strg+Rechtsklick (AdminOp) mit einem Lagerticker
 * (Pflicht - ohne diese Verknüpfung kann der NPC nichts mehr verkaufen, siehe
 * mixin.create.ContainerNPCTraderMixin) und optional zusätzlich mit einem Zielinventar für die
 * Bezahl-Items (wie beim CobbleMerchant, siehe CobbleMerchantLinkInteractionHandler in
 * CobbleCompanion: CobbleDollars/Create - dieselbe Ablauf-Logik: erneuter Strg+Rechtsklick auf
 * denselben NPC bricht den Verknüpfungs-Vorgang ab).
 *
 * Nur registriert, wenn ModAvailability.isCustomNpcsAvailable() (siehe
 * CobbleCompanionDollarsCustomNPCs.onServerStarting) - diese Klasse importiert CustomNPCs-Typen
 * direkt.
 */
public class CustomNpcTraderLinkInteractionHandler {

    private final Map<UUID, UUID> pendingLinkByAdmin = new HashMap<>();

    // HIGHEST + receiveCanceled: läuft garantiert vor allen anderen Listenern auf demselben Event
    // (z.B. CustomNpcCobbleMerchantInteractionHandler), robuster gegen Registrierungsreihenfolge.
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onEntityInteract(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!ClientKeyStateManager.isCtrlHeld(player.getUUID())) return;
        if (!AdminPermissionManager.isAdminOp(player.getUUID())) return;
        if (!(event.getTarget() instanceof EntityNPCInterface npc)) return;
        // Root Cause (Live-Diagnose): der NPC muss NICHT zwingend die CustomNPCs-eigene RoleTrader-
        // Rolle haben - ein NPC, der nur über unseren eigenen "CobbleMerchant-Modus" (Alt+
        // Rechtsklick, siehe CustomNpcCobbleMerchantInteractionHandler) läuft, braucht GENAUSO eine
        // Ticker-Verknüpfung (dieselbe CustomNpcTraderLinkManager-Verknüpfung wird auch dort gelesen)
        // und muss daher hier ebenfalls durchgelassen werden.
        boolean isMerchantMode = ModAvailability.isCobbleDollarsAvailable()
            && CustomNpcMerchantShopManager.isEnabled(npc.getUUID());
        if (!(npc.role instanceof RoleTrader) && !isMerchantMode) return;

        UUID npcUuid = npc.getUUID();
        event.setCanceled(true);

        if (npcUuid.equals(pendingLinkByAdmin.get(player.getUUID()))) {
            pendingLinkByAdmin.remove(player.getUUID());
            player.sendSystemMessage(Component.translatableWithFallback(
                "cobblecompanion.msg.customnpc_link_cancelled", "Linking cancelled."));
            return;
        }

        pendingLinkByAdmin.put(player.getUUID(), npcUuid);
        CustomNpcTraderLinkManager.Target existingTicker = CustomNpcTraderLinkManager.getTickerLink(npcUuid);
        CustomNpcTraderLinkManager.Target existingStorage = CustomNpcTraderLinkManager.getStorageLink(npcUuid);
        if (existingTicker != null || existingStorage != null) {
            player.sendSystemMessage(Component.translatableWithFallback(
                "cobblecompanion.msg.customnpc_link_start_existing",
                "Currently linked - ticker: %s, payment storage: %s. Ctrl+right-click a stock ticker to change that link, a chest to change payment storage, or the NPC again to cancel.",
                formatTarget(existingTicker), formatTarget(existingStorage)));
        } else {
            player.sendSystemMessage(Component.translatableWithFallback(
                "cobblecompanion.msg.customnpc_link_start",
                "Ctrl+right-click a stock ticker (required for this NPC to sell anything) or a chest (payment items land there instead of a nearby drop) to link this NPC."));
        }
    }

    private static String formatTarget(CustomNpcTraderLinkManager.Target target) {
        if (target == null) return "-";
        return target.x() + "," + target.y() + "," + target.z();
    }

    // HIGH statt Default-Priorität: CreateStockTickerInteractionHandler (CC:CobbleDollars/Create)
    // hört auf dasselbe Event (öffnet seinen eigenen Preis-Editor bei Strg+Rechtsklick auf einen
    // Lagerticker) und canceled es dabei.
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!ClientKeyStateManager.isCtrlHeld(player.getUUID())) return;

        UUID npcUuid = pendingLinkByAdmin.get(player.getUUID());
        if (npcUuid == null) return;
        if (!AdminPermissionManager.isAdminOp(player.getUUID())) return;

        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;

        if (ModAvailability.isCreateAvailable() && CreateStockTickerBridge.isStockTicker(level, pos)) {
            event.setCanceled(true);
            pendingLinkByAdmin.remove(player.getUUID());
            CustomNpcTraderLinkManager.setTickerLink(npcUuid, level.dimension(), pos);
            player.sendSystemMessage(Component.translatableWithFallback(
                "cobblecompanion.msg.customnpc_ticker_link_done",
                "NPC linked to stock ticker at %s, %s, %s - it can now sell items from that network's stock.",
                String.valueOf(pos.getX()), String.valueOf(pos.getY()), String.valueOf(pos.getZ())));
            return;
        }

        IItemHandler handler = CustomNpcTraderLinkManager.findItemHandler(serverLevel, pos);
        if (handler == null) {
            player.sendSystemMessage(Component.translatableWithFallback(
                "cobblecompanion.msg.customnpc_link_no_inventory", "This block is neither a stock ticker nor has an inventory."));
            return;
        }

        event.setCanceled(true);
        pendingLinkByAdmin.remove(player.getUUID());
        CustomNpcTraderLinkManager.setStorageLink(npcUuid, level.dimension(), pos);
        player.sendSystemMessage(Component.translatableWithFallback(
            "cobblecompanion.msg.customnpc_storage_link_done", "Payment items from this NPC will now be delivered to %s, %s, %s.",
            String.valueOf(pos.getX()), String.valueOf(pos.getY()), String.valueOf(pos.getZ())));
    }
}
