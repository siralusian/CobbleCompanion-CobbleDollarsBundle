package com.cobblecompanion.cobbledollarscreate.mobilepackages.client;

import com.cobblecompanion.cobbledollarscreate.client.data.ClientStockTickerBuyPricesHelper;
import com.cobblecompanion.integrations.cobbledollars.CobbleDollarsScale;
import com.simibubi.create.content.logistics.BigItemStack;
import de.theidler.create_mobile_packages.items.portable_stock_ticker.PortableStockTickerScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.lang.reflect.Field;

/**
 * Preisschild-Gegenstück zu StockTickerPriceOverlay, aber für PortableStockTickerScreen (mobiler
 * Lagerticker aus create_mobile_packages). Gleiches Layout wie beim normalen Lagerticker (itemsX/
 * orderY/colWidth/windowWidth/windowHeight per javap identisch benannt UND identische
 * Bestätigen-Button-Hitbox guiLeft+143/guiTop+windowHeight-39/78x18 - beide Screens erben von
 * AbstractSimiContainerScreen), einziger Unterschied: itemsToOrder() liefert hier
 * List&lt;BigGenericStack&gt; statt List&lt;BigItemStack&gt; - über .asStack() auf dasselbe
 * BigItemStack-Format umgewandelt, damit der Rest identisch bleibt.
 *
 * Nutzt bewusst denselben ClientStockTickerBuyPricesHelper (globaler Cache, keine Positions-
 * Bindung nötig, da der mobile Ticker eh immer dieselbe zentrale Preisliste verwendet - siehe
 * MobileStockTickerPriceHandler).
 *
 * Importiert create_mobile_packages-Typen direkt - deshalb NIEMALS automatisch laden, siehe
 * CobbleCompanionDollarsCreate.clientSetup (ModList-Gate für "create" UND "create_mobile_packages").
 */
public class MobileStockTickerPriceOverlay {

    private static final int PRICE_TEXT_OFFSET_Y = 21;
    private static final float PRICE_TEXT_SCALE = 0.75f;
    private static final int PRICE_TEXT_COLOR = 0xFFFFAA00;

    private static final int CONFIRM_BUTTON_Y_OFFSET_FROM_BOTTOM = 39;
    private static final int CONFIRM_BUTTON_HEIGHT = 18;
    private static final int TOTAL_TEXT_GAP_Y = 9;
    private static final int TOTAL_ROW_MARGIN_X = 8;
    private static final int TOTAL_LABEL_OFFSET_X = 20;
    private static final int TOTAL_AMOUNT_OFFSET_X = -5;

    private static Field itemsXField;
    private static Field orderYField;
    private static Field colWidthField;
    private static Field windowWidthField;
    private static Field windowHeightField;

    @SubscribeEvent
    public void onScreenRenderPost(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof PortableStockTickerScreen screen)) return;
        if (!ClientStockTickerBuyPricesHelper.isPaymentRequired()) return;

        try {
            int itemsX = itemsXField(screen).getInt(screen);
            int orderY = orderYField(screen).getInt(screen);
            int colWidth = colWidthField(screen).getInt(screen);
            int windowWidth = windowWidthField(screen).getInt(screen);
            int windowHeight = windowHeightField(screen).getInt(screen);

            GuiGraphics graphics = event.getGuiGraphics();
            var font = net.minecraft.client.Minecraft.getInstance().font;

            long total = 0;
            int index = 0;
            for (var bigGenericStack : screen.itemsToOrder) {
                BigItemStack entry = bigGenericStack.asStack();
                if (entry.stack != null && !entry.stack.isEmpty()) {
                    String itemId = BuiltInRegistries.ITEM.getKey(entry.stack.getItem()).toString();
                    Long unitPrice = ClientStockTickerBuyPricesHelper.priceFor(itemId);
                    if (unitPrice != null) {
                        total += unitPrice * entry.count;
                        int slotX = itemsX + index * colWidth;
                        drawScaledCentered(graphics, font, "$" + CobbleDollarsScale.formatRaw(java.math.BigInteger.valueOf(unitPrice)),
                            slotX, slotX + colWidth, orderY + PRICE_TEXT_OFFSET_Y, PRICE_TEXT_SCALE);
                    }
                }
                index++;
            }

            if (total > 0) {
                int rowLeft = screen.getGuiLeft() + TOTAL_ROW_MARGIN_X;
                int rowRight = screen.getGuiLeft() + windowWidth - TOTAL_ROW_MARGIN_X;
                int rowY = screen.getGuiTop() + windowHeight - CONFIRM_BUTTON_Y_OFFSET_FROM_BOTTOM
                    + CONFIRM_BUTTON_HEIGHT + TOTAL_TEXT_GAP_Y;

                graphics.drawString(font, Component.literal("Gesamt:"), rowLeft + TOTAL_LABEL_OFFSET_X, rowY, PRICE_TEXT_COLOR, true);
                String amount = "$" + CobbleDollarsScale.formatRaw(java.math.BigInteger.valueOf(total));
                int amountWidth = font.width(amount);
                graphics.drawString(font, Component.literal(amount), rowRight - amountWidth + TOTAL_AMOUNT_OFFSET_X, rowY, PRICE_TEXT_COLOR, true);
            }
        } catch (ReflectiveOperationException ignored) {}
    }

    private static void drawScaledCentered(GuiGraphics graphics, net.minecraft.client.gui.Font font, String text, int left, int right, int y, float scale) {
        int textWidth = font.width(text);
        float scaledWidth = textWidth * scale;
        int x = left + Math.round(((right - left) - scaledWidth) / 2f);

        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1f);
        graphics.drawString(font, Component.literal(text), 0, 0, PRICE_TEXT_COLOR, true);
        graphics.pose().popPose();
    }

    private static Field itemsXField(PortableStockTickerScreen screen) throws ReflectiveOperationException {
        if (itemsXField == null) itemsXField = accessible(screen.getClass(), "itemsX");
        return itemsXField;
    }

    private static Field orderYField(PortableStockTickerScreen screen) throws ReflectiveOperationException {
        if (orderYField == null) orderYField = accessible(screen.getClass(), "orderY");
        return orderYField;
    }

    private static Field colWidthField(PortableStockTickerScreen screen) throws ReflectiveOperationException {
        if (colWidthField == null) colWidthField = accessible(screen.getClass(), "colWidth");
        return colWidthField;
    }

    private static Field windowWidthField(PortableStockTickerScreen screen) throws ReflectiveOperationException {
        if (windowWidthField == null) windowWidthField = accessible(screen.getClass(), "windowWidth");
        return windowWidthField;
    }

    private static Field windowHeightField(PortableStockTickerScreen screen) throws ReflectiveOperationException {
        if (windowHeightField == null) windowHeightField = accessible(screen.getClass(), "windowHeight");
        return windowHeightField;
    }

    private static Field accessible(Class<?> clazz, String name) throws ReflectiveOperationException {
        Field f = clazz.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }
}
