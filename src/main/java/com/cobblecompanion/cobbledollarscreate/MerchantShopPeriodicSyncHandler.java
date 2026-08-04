package com.cobblecompanion.cobbledollarscreate;

import fr.harmex.cobbledollars.common.world.inventory.ShopMenu;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Nutzer-Vorgabe: die Bestandsanzeige im CobbleMerchant-Kaufinterface soll sich aktualisieren,
 * ohne dass der Spieler das Fenster schließen/neu öffnen muss - deckt Käufe ANDERER Spieler am
 * selben Netzwerk ab (der eigene Kauf wird bereits sofort über BuyHandlerMixin aktualisiert).
 * Alle ~2 Sekunden für jeden Spieler mit offenem ShopMenu.
 *
 * Liegt bewusst NICHT im "mixin.create"-Paket: das ist Mixin-Hoheitsgebiet (siehe
 * cobblecompanion_cobbledollars_create.mixins.json "package"-Eintrag) - Klassen darin dürfen nur
 * vom Mixin-Transformer selbst geladen werden, ein direktes `new ...()` (hier über
 * NeoForge.EVENT_BUS.register in CobbleCompanionDollarsCreate.onServerStarting) crasht sonst mit
 * IllegalClassLoadError.
 */
public class MerchantShopPeriodicSyncHandler {

    private static final int INTERVAL_TICKS = 40; // ~2 Sekunden

    private int counter = 0;

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        counter++;
        if (counter < INTERVAL_TICKS) return;
        counter = 0;

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof ShopMenu menu) {
                MerchantStockSyncHelper.sync(player, menu.getCobbleMerchant());
            }
        }
    }
}
