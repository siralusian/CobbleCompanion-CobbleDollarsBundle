package com.cobblecompanion.cobbledollarscustomnpcs;

import com.cobblecompanion.cobbledollarscustomnpcs.data.CustomNpcMerchantShopManager;
import com.cobblecompanion.data.AdminPermissionManager;
import com.cobblecompanion.data.ClientKeyStateManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import noppes.npcs.entity.EntityNPCInterface;

/**
 * Nutzer-Vorgabe: "CustomNPC soll wahlweise Funktion/GUI eines CobbleMerchant haben, Aussehen
 * bleibt" - Alt+Rechtsklick (AdminOp) auf einen BELIEBIGEN CustomNPC schaltet den
 * "CobbleMerchant-Modus" ein/aus (siehe CustomNpcMerchantShopManager +
 * mixin.EntityNPCInterfaceMerchantMixin für die eigentliche Umleitung des Rechtsklicks).
 *
 * Nutzer-Korrektur: ursprünglich Strg+Umschalt+Rechtsklick, das überschnitt sich aber mit dem
 * reinen Strg+Rechtsklick-Ticker/Kisten-Verknüpfungs-Kurzbefehl (siehe
 * CustomNpcTraderLinkInteractionHandler) - sobald ein NPC bereits im CobbleMerchant-Modus war,
 * fing dessen Handler (prüft nur Strg, nicht Umschalt) JEDEN Strg+Rechtsklick ab, bevor dieser
 * Handler die Umschalt-Taste überhaupt auswerten konnte. Jetzt eigene, überschneidungsfreie Taste
 * (Alt statt Strg+Umschalt, siehe AltKeyStatePacket/ClientKeyStateManager.isAltHeld).
 *
 * Nur registriert, wenn ModAvailability.isCustomNpcsAvailable() UND isCobbleDollarsAvailable()
 * (siehe CobbleCompanionDollarsCustomNPCs.onServerStarting) - importiert Typen aus beiden Mods.
 */
public class CustomNpcCobbleMerchantInteractionHandler {

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!ClientKeyStateManager.isAltHeld(player.getUUID())) return;
        if (!AdminPermissionManager.isAdminOp(player.getUUID())) return;
        if (!(event.getTarget() instanceof EntityNPCInterface npc)) return;

        event.setCanceled(true);
        boolean newState = !CustomNpcMerchantShopManager.isEnabled(npc.getUUID());
        CustomNpcMerchantShopManager.setEnabled(npc.getUUID(), newState);
        player.sendSystemMessage(Component.translatableWithFallback(
            newState ? "cobblecompanion.msg.customnpc_merchant_enabled" : "cobblecompanion.msg.customnpc_merchant_disabled",
            newState
                ? "This NPC now works like a CobbleMerchant (right-click opens the shop, its own editing tool still works as usual)."
                : "This NPC behaves normally again."));
    }
}
