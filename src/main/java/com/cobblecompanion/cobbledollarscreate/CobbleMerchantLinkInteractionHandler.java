package com.cobblecompanion.cobbledollarscreate;

import com.cobblecompanion.cobbledollarscreate.data.CobbleMerchantSellManager;
import com.cobblecompanion.data.AdminPermissionManager;
import com.cobblecompanion.data.ClientKeyStateManager;
import com.cobblecompanion.integrations.ModAvailability;
import fr.harmex.cobbledollars.common.world.entity.CobbleMerchant;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Verknüpft einen CobbleMerchant per Strg+Rechtsklick (AdminOp) mit einem Zielinventar für
 * verkaufte Items UND/ODER einem Lagerticker. Ablauf: Strg+Rechtsklick auf den Merchant startet
 * den "Verknüpfungs-Modus" (erneuter Strg+Rechtsklick auf denselben Merchant bricht ab), danach
 * schließt ein Strg+Rechtsklick auf einen beliebigen Block mit Inventar (IItemHandler-Fähigkeit -
 * Truhe, Fass, Tresor, ...) ODER einen Lagerticker die jeweilige Verknüpfung ab. Nur registriert,
 * wenn ModAvailability.isCobbleDollarsAvailable() (siehe CobbleCompanionDollarsCreate.onServerStarting).
 *
 * Bewusst Strg statt Shift (Nutzer-Vorgabe): Shift+Rechtsklick auf einen Merchant öffnet direkt
 * CobbleDollars' eigenes Kauf/Verkauf-Menü, das wäre also ein Konflikt.
 *
 * Ankaufspreise kommen aus der EINEN globalen Preisliste (siehe CentralItemPriceManager, Nutzer-
 * Vorgabe "Zentrale Liste für alle Ticker und alle Merchants") - die Lagerticker-Verknüpfung hier
 * bestimmt NICHT mehr den Preis, sondern nur noch das Verhalten, wenn KEIN Lager verknüpft ist
 * (siehe SellHandlerMixin): mit Ticker verknüpft -> Item wird am Merchant fallen gelassen statt
 * verworfen zu werden; ohne Ticker-Verknüpfung -> Item wird einfach gelöscht (altes Verhalten).
 * Ist ein Lager verknüpft, landen Items dort so oder so, unabhängig von der Ticker-Verknüpfung.
 */
public class CobbleMerchantLinkInteractionHandler {

    // adminUuid -> merchantUuid, rein flüchtig (kein Persistenz-Bedarf für einen offenen
    // Verknüpfungs-Vorgang - bricht bei Serverneustart einfach ab).
    private final Map<UUID, UUID> pendingLinkByAdmin = new HashMap<>();

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!ClientKeyStateManager.isCtrlHeld(player.getUUID())) return;
        if (!AdminPermissionManager.isAdminOp(player.getUUID())) return;
        if (!(event.getTarget() instanceof CobbleMerchant merchant)) return;

        UUID merchantUuid = merchant.getMerchantUUID();
        if (merchantUuid == null) return;

        event.setCanceled(true);

        if (merchantUuid.equals(pendingLinkByAdmin.get(player.getUUID()))) {
            pendingLinkByAdmin.remove(player.getUUID());
            player.sendSystemMessage(Component.translatableWithFallback(
                "cobblecompanion.msg.merchant_link_cancelled", "Linking cancelled."));
            return;
        }

        pendingLinkByAdmin.put(player.getUUID(), merchantUuid);
        // "effective"-Varianten (siehe CobbleMerchantSellManager.getEffectiveLink()-Kommentar), damit
        // eine per CustomNPCs-Trader-Verknüpfung bereits bestehende Verknüpfung hier korrekt angezeigt
        // wird, statt fälschlich "-" (unverknüpft) zu behaupten.
        CobbleMerchantSellManager.Target existingStorage = CobbleMerchantSellManager.getEffectiveLink(merchantUuid);
        CobbleMerchantSellManager.Target existingTicker = CobbleMerchantSellManager.getEffectiveBuyTickerLink(merchantUuid);
        if (existingStorage != null || existingTicker != null) {
            player.sendSystemMessage(Component.translatableWithFallback(
                "cobblecompanion.msg.merchant_link_start_existing",
                "Currently linked - storage: %s, ticker: %s. Ctrl+right-click a new chest to change storage, a stock ticker to change that link, or the merchant again to cancel.",
                formatTarget(existingStorage), formatTarget(existingTicker)));
        } else {
            player.sendSystemMessage(Component.translatableWithFallback(
                "cobblecompanion.msg.merchant_link_start",
                "Ctrl+right-click a chest (delivery of sold items) or a stock ticker (drop instead of delete when no chest is linked) to link this merchant."));
        }
    }

    private static String formatTarget(CobbleMerchantSellManager.Target target) {
        if (target == null) return "-";
        return target.x() + "," + target.y() + "," + target.z();
    }

    // HIGH statt Default-Priorität: CreateStockTickerInteractionHandler hört auf dasselbe Event
    // (öffnet seinen eigenen Preis-Editor bei Strg+Rechtsklick auf einen Lagerticker) und
    // canceled es dabei - abgebrochene Events werden an Listener mit Default-Priorität NICHT
    // mehr zugestellt, weshalb unser Verknüpfungs-Klick sonst nie ankäme, solange ein
    // Verknüpfungs-Vorgang läuft. Bei KEINEM offenen Vorgang kehrt diese Methode früh zurück,
    // ohne das Event zu canceln - der Preis-Editor bleibt für den normalen Admin-Gebrauch
    // unangetastet.
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!ClientKeyStateManager.isCtrlHeld(player.getUUID())) return;

        UUID merchantUuid = pendingLinkByAdmin.get(player.getUUID());
        if (merchantUuid == null) return;
        if (!AdminPermissionManager.isAdminOp(player.getUUID())) return;

        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;

        if (ModAvailability.isCreateAvailable() && com.cobblecompanion.integrations.create.CreateStockTickerBridge.isStockTicker(level, pos)) {
            event.setCanceled(true);
            pendingLinkByAdmin.remove(player.getUUID());
            CobbleMerchantSellManager.setBuyTickerLink(merchantUuid, level.dimension(), pos);
            player.sendSystemMessage(Component.translatableWithFallback(
                "cobblecompanion.msg.merchant_ticker_link_done",
                "Merchant linked to stock ticker at %s, %s, %s (drop items on the ground instead of deleting them when no storage is linked).",
                String.valueOf(pos.getX()), String.valueOf(pos.getY()), String.valueOf(pos.getZ())));
            return;
        }

        IItemHandler handler = CobbleMerchantSellManager.findItemHandler(serverLevel, pos);
        if (handler == null) {
            player.sendSystemMessage(Component.translatableWithFallback(
                "cobblecompanion.msg.merchant_link_no_inventory", "This block is neither a stock ticker nor has an inventory."));
            return;
        }

        event.setCanceled(true);
        pendingLinkByAdmin.remove(player.getUUID());
        CobbleMerchantSellManager.setLink(merchantUuid, level.dimension(), pos);
        player.sendSystemMessage(Component.translatableWithFallback(
            "cobblecompanion.msg.merchant_link_done", "Merchant linked to storage at %s, %s, %s.",
            String.valueOf(pos.getX()), String.valueOf(pos.getY()), String.valueOf(pos.getZ())));
    }
}
