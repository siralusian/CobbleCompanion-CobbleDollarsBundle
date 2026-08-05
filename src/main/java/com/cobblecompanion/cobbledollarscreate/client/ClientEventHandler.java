package com.cobblecompanion.cobbledollarscreate.client;

import com.cobblecompanion.cobbledollarscreate.client.data.ClientContentObserverHelper;
import com.cobblecompanion.cobbledollarscreate.client.data.ClientCreateStockTickerHelper;
import com.cobblecompanion.cobbledollarscreate.client.data.ClientMerchantOfferHelper;
import com.cobblecompanion.cobbledollarscreate.client.screens.ContentObserverConfigScreen;
import com.cobblecompanion.cobbledollarscreate.client.screens.MerchantOfferEditScreen;
import com.cobblecompanion.cobbledollarscreate.client.screens.StockTickerPriceScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Öffnet den Lagerticker-Preis-Editor bzw. den Schlaue-Beobachter-Editor, sobald
 * ClientCreateStockTickerHelper/ClientContentObserverHelper einen neuen Stand hat (siehe deren
 * Klassenkommentare - Packets dürfen Screen-Klassen nicht direkt referenzieren, deshalb hier über
 * einen Versionszähler statt direkt im Packet-Handler geöffnet). Ursprünglich Teil von Basis'
 * eigenem ClientEventHandler - hierher ausgelagert, da Basis nicht mehr von diesem Modul abhängen
 * darf (Basis behält nur noch die modulübergreifenden Teile: CompanionScreen-Öffnen, Strg/Alt-
 * Tastenstatus-Übertragung).
 *
 * Bewusst OHNE direkte Create-Importe (auch nicht über die geöffneten Screens hinaus, die selbst
 * erst bei tatsächlichem setScreen()-Aufruf geladen werden) - @EventBusSubscriber scannt diese
 * Klasse unconditional beim Moddurchlauf, siehe CobbleCompanionDollarsCreate.clientSetup()-
 * Kommentar. Die Gruppen-Hervorhebungs-Logik (braucht direkten Zugriff auf SmartObserverBlock)
 * liegt deshalb bewusst NICHT hier, sondern in ContentObserverGroupHighlightRenderer (manuell
 * registriert, ModList-Gate).
 */
@EventBusSubscriber(modid = "cobblecompanion_cobbledollars_create", value = Dist.CLIENT)
public class ClientEventHandler {

    private static int stockTickerPricesVersionSeen = -1;
    private static int contentObserverVersionSeen = -1;
    private static int merchantOfferVersionSeen = -1;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        int version = ClientCreateStockTickerHelper.getVersion();
        if (version != stockTickerPricesVersionSeen) {
            stockTickerPricesVersionSeen = version;
            if (ClientCreateStockTickerHelper.getPos() != null) {
                Minecraft.getInstance().setScreen(new StockTickerPriceScreen(
                    ClientCreateStockTickerHelper.getPos(),
                    ClientCreateStockTickerHelper.isEnabled(),
                    ClientCreateStockTickerHelper.getCurrentListId(),
                    ClientCreateStockTickerHelper.getLists(),
                    ClientCreateStockTickerHelper.getAvailableItemIds()));
            }
        }

        int contentObserverVersion = ClientContentObserverHelper.getVersion();
        if (contentObserverVersion != contentObserverVersionSeen) {
            contentObserverVersionSeen = contentObserverVersion;
            if (ClientContentObserverHelper.getPos() != null) {
                Minecraft.getInstance().setScreen(new ContentObserverConfigScreen(
                    ClientContentObserverHelper.getPos(),
                    ClientContentObserverHelper.getRules(),
                    ClientContentObserverHelper.getGroupId(),
                    ClientContentObserverHelper.getGroupName(),
                    ClientContentObserverHelper.isSubtractorBlock(),
                    ClientContentObserverHelper.getPromiseExpiryStage(),
                    ClientContentObserverHelper.isNetworkConnected(),
                    ClientContentObserverHelper.getNetworkListName(),
                    ClientContentObserverHelper.getCounterCount(),
                    ClientContentObserverHelper.getSubtractorCount(),
                    ClientContentObserverHelper.getNetworkPrices(),
                    ClientContentObserverHelper.getEnabledCounterItemIds(),
                    ClientContentObserverHelper.getEnabledSubtractorItemIds()));
            }
        }

        int merchantOfferVersion = ClientMerchantOfferHelper.getVersion();
        if (merchantOfferVersion != merchantOfferVersionSeen) {
            merchantOfferVersionSeen = merchantOfferVersion;
            if (ClientMerchantOfferHelper.getMerchantUuid() != null) {
                Minecraft.getInstance().setScreen(new MerchantOfferEditScreen(
                    ClientMerchantOfferHelper.getTickerPos(),
                    ClientMerchantOfferHelper.getMerchantUuid(),
                    ClientMerchantOfferHelper.getMerchantName(),
                    ClientMerchantOfferHelper.isActive(),
                    ClientMerchantOfferHelper.getCustomOfferItemIds(),
                    ClientMerchantOfferHelper.getNetworkOfferItemIds(),
                    ClientMerchantOfferHelper.getNetworkCategoriesByItemId()));
            }
        }
    }
}
