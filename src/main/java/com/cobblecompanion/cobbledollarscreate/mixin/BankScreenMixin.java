package com.cobblecompanion.cobbledollarscreate.mixin;

import fr.harmex.cobbledollars.common.client.gui.screen.BankScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * CLIENT-seitiger Mixin (siehe cobblecompanion_cobbledollars_create.mixins.json "client"-Block
 * statt "mixins" - BankScreen ist eine reine GUI-Klasse, existiert auf einem echten Dedicated
 * Server gar nicht, darf dort also nie berührt werden, siehe RuntimeDistCleaner-Fallstricke).
 *
 * BankScreen.canSell() (Original) gibt nur dann true zurück, wenn der von CobbleDollars selbst
 * berechnete Verkaufspreis > 0 ist - unabhängig davon, ob das Item überhaupt in dessen globaler
 * Bank-Liste vorkommt. Das würde den "Verkaufen"-Button für JEDES Item mit Preis 0 dauerhaft
 * deaktiviert lassen, egal wie die Bank-Liste befüllt ist (Nutzer-Vorgabe: Merchant muss alles
 * annehmen, auch für 0 Cobbledollars) - der eigentliche Preis kommt ohnehin ausschließlich aus
 * unserem eigenen Server-seitigen Override (SellHandlerMixin), das Original-Preisfeld hier ist
 * für unsere Zwecke irrelevant.
 */
@Mixin(BankScreen.class)
public abstract class BankScreenMixin {

    @Inject(method = "canSell", at = @At("HEAD"), cancellable = true)
    private void cobblecompanion$alwaysAllowSell(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }
}
