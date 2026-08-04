package com.cobblecompanion.cobbledollarscreate.mixin.client;

import com.cobblecompanion.cobbledollarscreate.client.data.ClientMerchantOfferStockHelper;
import com.mojang.math.Axis;
import fr.harmex.cobbledollars.common.client.gui.screen.ShopScreen;
import fr.harmex.cobbledollars.common.client.gui.screen.widget.CategoryListWidget;
import fr.harmex.cobbledollars.common.client.gui.screen.widget.OfferListWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Nutzer-Fund (per Screenshot bestätigt, mehrfach nachgebessert): der ursprüngliche Ansatz über
 * ScreenEvent.Render.Post + OfferEntry.getX()/getY() war unzuverlässig - diese Felder werden von
 * CobbleDollars NUR innerhalb von OfferEntry.render() selbst gesetzt (siehe
 * AbstractSelectionList.renderListItems: render() wird NUR für gerade sichtbare Zeilen aufgerufen),
 * bleiben für aus dem sichtbaren Bereich gescrollte Zeilen also auf einem veralteten Wert stehen -
 * ein nachträglicher Sichtbarkeits-Bounds-Check auf Basis dieser Felder blieb deshalb fehleranfällig
 * (besonders beim Ziehen der Scrollbar, wo sich der Scroll pro Frame stärker ändert).
 *
 * Robuste Lösung: direkt in OfferEntry.render() selbst injizieren (TAIL) und die von CobbleDollars
 * an render() übergebenen top/left-Parameter verwenden statt der (potenziell veralteten)
 * Instanzfelder - render() wird garantiert nur für die JETZT sichtbaren Zeilen mit ihrer JETZT
 * korrekten Position aufgerufen, unabhängig von Mausrad- oder Scrollbar-Bedienung.
 */
@Mixin(OfferListWidget.OfferEntry.class)
public abstract class OfferEntryStockOverlayMixin {

    private static final int ICON_LEFT_OFFSET = 1;
    private static final int ICON_SIZE = 16;
    private static final int PRICE_CROSS_X_OFFSET = 19;
    private static final int PRICE_CROSS_Y_OFFSET = 2;
    private static final int PRICE_CROSS_W = 34;
    private static final int PRICE_CROSS_H = 14;

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIIIIIIZF)V", at = @At("TAIL"))
    private void cobblecompanion$renderStock(GuiGraphics graphics, int index, int top, int left, int width, int height,
            int mouseX, int mouseY, boolean hovering, float partialTick, CallbackInfo ci) {
        if (!(Minecraft.getInstance().screen instanceof ShopScreen shop)) return;

        CategoryListWidget categoryList = shop.getCategoryList();
        OfferListWidget offerList = shop.getOfferList();
        if (categoryList == null || offerList == null) return;
        CategoryListWidget.CategoryEntry selectedCategory = categoryList.getSelected();
        Integer categoryIndex = selectedCategory != null ? selectedCategory.getCategoryIndex() : null;
        if (categoryIndex == null) return;

        OfferListWidget.OfferEntry entry = (OfferListWidget.OfferEntry) (Object) this;
        Integer offerIndex = entry.getOfferIndex();
        if (offerIndex == null) return;
        Integer available = ClientMerchantOfferStockHelper.get(categoryIndex, offerIndex);
        if (available == null) return; // Nicht verknüpft -> keine Anzeige für dieses Angebot.

        OfferListWidget.OfferEntry selectedOffer = offerList.getSelected();
        int needed = entry == selectedOffer ? Math.max(1, shop.getBuyAmount()) : entry.getItem().getCount();
        boolean insufficient = available < needed;

        Font font = Minecraft.getInstance().font;
        int iconLeft = left + ICON_LEFT_OFFSET;
        int iconTop = top + ICON_LEFT_OFFSET;
        int badgeRight = iconLeft + ICON_SIZE;
        int badgeBottom = iconTop + ICON_SIZE;
        // Nutzer-Vorgabe: ab 1000 auf glatte Tausender abgerundet als "Nk" anzeigen (z.B. 1999 -> "1k", 2000 -> "2k").
        String text = available >= 1000 ? (available / 1000) + "k" : String.valueOf(available);
        float scale = 0.7f;
        int textWidth = font.width(text);
        graphics.pose().pushPose();
        graphics.pose().translate(badgeRight - textWidth * scale, badgeBottom - 8 * scale, 400);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.drawString(font, text, 0, 0, insufficient ? 0xFFFF5555 : 0xFFFFFFFF, true);
        graphics.pose().popPose();

        if (insufficient) {
            int crossLeft = left + PRICE_CROSS_X_OFFSET;
            int crossTop = top + PRICE_CROSS_Y_OFFSET;
            drawCross(graphics, crossLeft, crossTop, PRICE_CROSS_W, PRICE_CROSS_H, 0xF0FF5555);
        }
    }

    private static void drawCross(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        double diag = Math.sqrt((double) w * w + (double) h * h);
        float angleDeg = (float) Math.toDegrees(Math.atan2(h, w));
        int thickness = 2;

        graphics.pose().pushPose();
        graphics.pose().translate(x + w / 2.0, y + h / 2.0, 400);

        graphics.pose().pushPose();
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(angleDeg));
        graphics.fill((int) (-diag / 2), -thickness / 2, (int) (diag / 2), thickness / 2, color);
        graphics.pose().popPose();

        graphics.pose().pushPose();
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(-angleDeg));
        graphics.fill((int) (-diag / 2), -thickness / 2, (int) (diag / 2), thickness / 2, color);
        graphics.pose().popPose();

        graphics.pose().popPose();
    }
}
