package com.cobblecompanion.cobbledollarscreate;

import com.cobblecompanion.cobbledollarscreate.data.KnownPlayersHelper;
import com.cobblecompanion.cobbledollarscreate.network.ContentObserverConfigSyncPacket;
import com.cobblecompanion.cobbledollarscreate.network.KnownPlayersSyncPacket;
import com.cobblecompanion.data.ClientKeyStateManager;
import com.cobblecompanion.data.FriendsManager;
import com.simibubi.create.content.redstone.smartObserver.SmartObserverBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Nutzer-Vorgabe: für den "Schlauen Beobachter" (create:content_observer) kann nur ein echter
 * Minecraft-OP per Strg+Rechtsklick einstellen, welches Item er tracken soll und wieviel
 * Cobbledollars dafür an welchen Spieler ausgezahlt werden (siehe ContentObserverConfigManager/
 * ContentObserverActivateMixin) - gleiches Muster wie CreateStockTickerInteractionHandler (Strg
 * statt Shift, echte OP-Rechte statt AdminPermissionManager).
 *
 * Zusätzlich (Nutzer-Vorgabe): ist ein Beobachter bereits konfiguriert, darf sein Filter-Item NICHT
 * mehr von Nicht-OP-Spielern per normalem Rechtsklick verändert werden (Creates eigene Filter-
 * Slot-Interaktion) - nur Strg+Rechtsklick von einem OP darf ihn (über unseren Editor) noch ändern.
 */
public class ContentObserverInteractionHandler {

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (!(level.getBlockEntity(pos) instanceof SmartObserverBlockEntity)) return;

        boolean ctrlHeld = ClientKeyStateManager.isCtrlHeld(player.getUUID());

        if (ctrlHeld) {
            if (!player.hasPermissions(2)) return; // kein OP - Strg+Rechtsklick hat hier keine Wirkung
            event.setCanceled(true);
            event.setUseBlock(net.neoforged.neoforge.common.util.TriState.FALSE);
            event.setUseItem(net.neoforged.neoforge.common.util.TriState.FALSE);

            ContentObserverConfigManager.Entry cfg = ContentObserverConfigManager.get(level.dimension(), pos);
            String itemId = cfg != null ? cfg.itemId : "";
            String targetPlayerName = "";
            long amount = cfg != null ? cfg.amountPerItem : 0;
            if (cfg != null && cfg.targetPlayerUuid != null) {
                try {
                    String known = FriendsManager.getKnownName(java.util.UUID.fromString(cfg.targetPlayerUuid));
                    if (known != null) targetPlayerName = known;
                } catch (IllegalArgumentException ignored) {}
            }
            PacketDistributor.sendToPlayer(player, new ContentObserverConfigSyncPacket(pos, itemId, targetPlayerName, amount));

            // Nutzer-Vorgabe: Zielspieler-Feld bekommt eine Vorschlagsfunktion wie der Verkaufserlöse-
            // Empfänger-Picker im Lagerticker - dafür braucht der Client dieselbe bekannte-Spieler-Liste.
            if (player.getServer() != null) {
                PacketDistributor.sendToPlayer(player, new KnownPlayersSyncPacket(
                    KnownPlayersSyncPacket.from(KnownPlayersHelper.getAllKnownPlayers(player.getServer()))));
            }
            return;
        }

        // Nutzer-Vorgabe: konfigurierte Beobachter dürfen Nicht-OPs nicht mehr per normalem
        // Rechtsklick den Filter ändern lassen (Creates eigene Kurz-Interaktion, siehe
        // FilteringBehaviour.onShortInteract) - der Filter bleibt fest auf dem admin-eingestellten
        // Item, bis ein OP ihn über den eigenen Editor wieder ändert.
        if (player.hasPermissions(2)) return;
        if (!ContentObserverConfigManager.isConfigured(level.dimension(), pos)) return;
        event.setCanceled(true);
        event.setUseBlock(net.neoforged.neoforge.common.util.TriState.FALSE);
        event.setUseItem(net.neoforged.neoforge.common.util.TriState.FALSE);
    }
}
