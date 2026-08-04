package com.cobblecompanion.cobbledollarscreate.mixin.client;

import com.cobblecompanion.cobbledollarscreate.client.data.ClientMerchantOfferStockHelper;
import fr.harmex.cobbledollars.common.client.gui.screen.ShopScreen;
import fr.harmex.cobbledollars.common.client.gui.screen.widget.CategoryListWidget;
import fr.harmex.cobbledollars.common.client.gui.screen.widget.OfferListWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Nutzer-Vorgabe: der Mengen-Pfeil-hoch-Button im CobbleMerchant-Kaufinterface soll die gewählte
 * Menge nie über den verfügbaren Netzwerk-Lagerbestand hinaus erhöhen (siehe
 * OfferEntryStockOverlayMixin für die begleitende Zahlen-/Rot-X-Anzeige, BuyHandlerMixin für die
 * eigentliche serverseitige Kaufsperre - dies ist rein die client-seitige Bedienungs-Grenze).
 *
 * Nutzer-Fund: Strg/Umschalt+Klick auf den Pfeil springt in CobbleDollars intern um größere
 * Schritte (z.B. einen ganzen 64er-Stack) - ein reiner HEAD-Check ("schon am Limit? dann
 * abbrechen") verhindert nur das Überschreiten bei Einzelschritten, nicht das Überspringen des
 * Limits in einem großen Sprung. Deshalb TAIL statt HEAD: der Klick läuft normal durch (egal wie
 * groß der interne Sprung ist), danach wird die tatsächliche Menge einfach auf den verfügbaren
 * Bestand zurückgekappt - deckt jede Sprunggröße gleichermaßen ab.
 *
 * Rein client-seitig (ShopScreen existiert nur clientseitig), daher eigene "client"-Mixin-Liste,
 * nur CobbleDollars als requiredMod nötig - die Bestandsdaten kommen bereits fertig vom Server
 * (MerchantOfferStockSyncPacket).
 */
@Mixin(ShopScreen.class)
public abstract class ShopScreenAmountMixin {

    @Inject(method = "amountButtonClick", at = @At("TAIL"))
    private void cobblecompanion$clampToStock(boolean increase, CallbackInfo ci) {
        if (!increase) return;
        ShopScreen self = (ShopScreen) (Object) this;

        CategoryListWidget categoryList = self.getCategoryList();
        OfferListWidget offerList = self.getOfferList();
        if (categoryList == null || offerList == null) return;

        CategoryListWidget.CategoryEntry selectedCategory = categoryList.getSelected();
        OfferListWidget.OfferEntry selectedOffer = offerList.getSelected();
        if (selectedCategory == null || selectedOffer == null) return;
        Integer categoryIndex = selectedCategory.getCategoryIndex();
        Integer offerIndex = selectedOffer.getOfferIndex();
        if (categoryIndex == null || offerIndex == null) return;

        Integer available = ClientMerchantOfferStockHelper.get(categoryIndex, offerIndex);
        if (available == null) return; // Nicht mit einem Lagerticker verknüpft -> kein Limit.

        if (self.getBuyAmount() > available) {
            self.setBuyAmount(available);
            if (self.getBuyAmountBox() != null) {
                self.getBuyAmountBox().setValue(String.valueOf(available));
            }
        }
    }
}
