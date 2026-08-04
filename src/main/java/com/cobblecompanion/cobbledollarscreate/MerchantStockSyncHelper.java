package com.cobblecompanion.cobbledollarscreate;

import com.cobblecompanion.cobbledollarscreate.data.CobbleMerchantSellManager;
import com.cobblecompanion.cobbledollarscreate.network.MerchantOfferStockSyncPacket;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import fr.harmex.cobbledollars.common.world.item.trading.CobbleDollarsShopHolder;
import fr.harmex.cobbledollars.common.world.item.trading.shop.Category;
import fr.harmex.cobbledollars.common.world.item.trading.shop.Offer;
import fr.harmex.cobbledollars.common.world.item.trading.shop.Shop;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Gemeinsame Logik zum (Neu-)Senden des Netzwerk-Lagerbestands pro Angebot an einen Spieler mit
 * offenem CobbleMerchant-Kaufinterface (siehe MerchantOfferStockSyncPacket/MerchantShopStockOverlay) -
 * ursprünglich nur beim Öffnen des Shops (PlayerExtensionKtOpenShopMixin), hierher ausgelagert,
 * damit auch BuyHandlerMixin (sofort nach einem Kauf) und MerchantShopPeriodicSyncHandler (alle
 * paar Sekunden, deckt Käufe ANDERER Spieler am selben Netzwerk ab) dieselbe Logik nutzen können -
 * Nutzer-Vorgabe: "Anzeige soll aktualisieren ohne das Fenster neu öffnen zu müssen".
 *
 * Liegt bewusst NICHT im "mixin.create"-Paket: Mixin verbietet das Laden JEDER Klasse aus einem per
 * "package"-Eintrag in einer .mixins.json beanspruchten Paket, außer durch den Mixin-Transformer
 * selbst - auch der Aufruf aus einer Mixin-Methode heraus (hier PlayerExtensionKtOpenShopMixin/
 * BuyHandlerMixin) zählt als "direkte Referenz" und crasht sonst mit IllegalClassLoadError beim
 * ersten Aufruf (live bestätigt).
 *
 * Bugfix (Live-Fund): sync() MUSS immer ein Paket senden, auch mit leeren Listen, wenn der Merchant
 * nicht (mehr) verknüpft ist o.ä. - der Client-Cache (ClientMerchantOfferStockHelper) ist rein
 * positionsbasiert (categoryIndex/offerIndex, keine Merchant-Identität) und wird nur beim Empfang
 * eines Pakets überschrieben. Frühere Version brach bei fehlender Verknüpfung einfach ohne Senden ab -
 * beim Öffnen eines NICHT verknüpften Merchants blieben dadurch die Bestandsdaten des zuletzt
 * angesehenen VERKNÜPFTEN Merchants im Client-Cache stehen und wurden an denselben Positionen
 * (categoryIndex/offerIndex) über andere, unbeteiligte Items gelegt (z.B. Pokébälle/Bonbons zeigten
 * "ausverkauft", obwohl gar nicht gelistet).
 */
public final class MerchantStockSyncHelper {

    private MerchantStockSyncHelper() {}

    public static void sync(ServerPlayer player, CobbleDollarsShopHolder holder) {
        if (holder == null) return;
        UUID merchantUuid = holder.getMerchantUUID();
        if (merchantUuid == null) return;

        List<Integer> categoryIndices = new ArrayList<>();
        List<Integer> offerIndices = new ArrayList<>();
        List<Integer> available = new ArrayList<>();

        CobbleMerchantSellManager.Target tickerTarget = CobbleMerchantSellManager.getEffectiveBuyTickerLink(merchantUuid);
        if (tickerTarget != null) {
            ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tickerTarget.dimension()));
            ServerLevel tickerLevel = player.server.getLevel(dimensionKey);
            BlockPos tickerPos = new BlockPos(tickerTarget.x(), tickerTarget.y(), tickerTarget.z());
            Shop shop = holder.getShop();
            if (tickerLevel != null && tickerLevel.getBlockEntity(tickerPos) instanceof StockTickerBlockEntity ticker
                    && ticker.behaviour != null && shop != null) {
                for (int c = 0; c < shop.size(); c++) {
                    Category category = shop.get(c);
                    if (category == null) continue;
                    var offers = category.getOffers();
                    if (offers == null) continue;
                    for (int o = 0; o < offers.size(); o++) {
                        Offer offer = offers.get(o);
                        if (offer == null || offer.getItem() == null) continue;
                        categoryIndices.add(c);
                        offerIndices.add(o);
                        available.add(ticker.getRecentSummary().getCountOf(offer.getItem()));
                    }
                }
            }
        }

        // Immer senden (auch leer) - siehe Klassenkommentar.
        PacketDistributor.sendToPlayer(player, new MerchantOfferStockSyncPacket(categoryIndices, offerIndices, available));
    }
}
