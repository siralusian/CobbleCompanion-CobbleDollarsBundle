package com.cobblecompanion.cobbledollarscreate.client;

import com.cobblecompanion.cobbledollarscreate.client.data.ClientStockTickerBuyPricesHelper;
import com.cobblecompanion.integrations.cobbledollars.CobbleDollarsScale;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.lang.reflect.Field;

/**
 * Nutzer-Vorgabe: "Im Lagerticker hat man keinerlei Info wieviel die Items kosten wenn man sie
 * bestellt" - zeichnet ein kleines Preisschild über jedem Item in Create's eigenem Bestellmenü
 * (StockKeeperRequestScreen.itemsToOrder), sobald der aktuell geöffnete Ticker Bezahlung verlangt
 * (siehe ClientStockTickerBuyPricesHelper, gefüllt durch StockTickerOrderScreenPriceHandler beim
 * Öffnen des Menüs).
 *
 * Bewusst OHNE Mixin gelöst: ScreenEvent.Render.Post feuert für JEDEN offenen Screen inkl. Create's
 * eigenem Bestellmenü, ohne dessen kompilierten Code zu verändern - reines "Draufmalen". Diese
 * Klasse importiert Create-Typen direkt und darf deshalb NIEMALS automatisch (z.B. via
 * @EventBusSubscriber-Scan) geladen werden, wenn Create nicht installiert ist - Registrierung
 * erfolgt deshalb manuell und bewusst NUR clientseitig gated über ModList.isLoaded("create")
 * (siehe CobbleCompanionDollarsCreate.clientSetup), nicht per Annotation.
 *
 * itemsX/orderY/colWidth/windowHeight sind auf StockKeeperRequestScreen package-private (per javap
 * verifiziert) - deshalb Reflection statt direktem Feldzugriff (itemsToOrder ist public, getGuiLeft/
 * getGuiTop sind public geerbt von AbstractContainerScreen).
 *
 * Nutzer-Vorgabe (nach Live-Tests): Preis unterhalb des Items statt darüber, verkleinerte
 * Schriftgröße, zeigt nur den Einzelpreis (keine Stückzahl-Multiplikation). Gesamtpreis (inkl.
 * Stückzahl) unter dem "Bestellen"-Button (Hitbox von StockKeeperRequestScreen.isConfirmHovered
 * per javap: guiLeft+143, guiTop+windowHeight-39, 78x18) - über die volle Fensterbreite gelegt
 * (nicht auf die 78px-Buttonbreite begrenzt), damit "Gesamt:"-Label und rechtsbündiger Betrag
 * auch bei bis zu 999.999.999 (mit Tausenderpunkten) nicht überlappen oder über den Fensterrahmen
 * hinausragen.
 */
public class StockTickerPriceOverlay {

    private static final int PRICE_TEXT_OFFSET_Y = 21; // relativ zur Slot-Oberkante (unterhalb des 16px-Icons)
    private static final float PRICE_TEXT_SCALE = 0.75f;
    private static final int PRICE_TEXT_COLOR = 0xFFFFAA00;

    private static final int CONFIRM_BUTTON_Y_OFFSET_FROM_BOTTOM = 39;
    private static final int CONFIRM_BUTTON_HEIGHT = 18;
    private static final int TOTAL_TEXT_GAP_Y = 9;
    private static final int TOTAL_ROW_MARGIN_X = 8; // Abstand zum Fensterrahmen links/rechts
    private static final int TOTAL_LABEL_OFFSET_X = 20; // "Gesamt:"-Label zusätzlich nach rechts
    private static final int TOTAL_AMOUNT_OFFSET_X = -5; // Betrag zusätzlich nach links

    private static Field itemsXField;
    private static Field orderYField;
    private static Field colWidthField;
    private static Field windowWidthField;
    private static Field windowHeightField;

    @SubscribeEvent
    public void onScreenRenderPost(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof StockKeeperRequestScreen screen)) return;
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
            for (BigItemStack entry : screen.itemsToOrder) {
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

    /** Zeichnet Text mittig zwischen left/right, skaliert auf scale, mit der Textoberkante bei y. */
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

    private static Field itemsXField(StockKeeperRequestScreen screen) throws ReflectiveOperationException {
        if (itemsXField == null) itemsXField = accessible(screen.getClass(), "itemsX");
        return itemsXField;
    }

    private static Field orderYField(StockKeeperRequestScreen screen) throws ReflectiveOperationException {
        if (orderYField == null) orderYField = accessible(screen.getClass(), "orderY");
        return orderYField;
    }

    private static Field colWidthField(StockKeeperRequestScreen screen) throws ReflectiveOperationException {
        if (colWidthField == null) colWidthField = accessible(screen.getClass(), "colWidth");
        return colWidthField;
    }

    private static Field windowWidthField(StockKeeperRequestScreen screen) throws ReflectiveOperationException {
        if (windowWidthField == null) windowWidthField = accessible(screen.getClass(), "windowWidth");
        return windowWidthField;
    }

    private static Field windowHeightField(StockKeeperRequestScreen screen) throws ReflectiveOperationException {
        if (windowHeightField == null) windowHeightField = accessible(screen.getClass(), "windowHeight");
        return windowHeightField;
    }

    private static Field accessible(Class<?> clazz, String name) throws ReflectiveOperationException {
        Field f = clazz.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }
}
