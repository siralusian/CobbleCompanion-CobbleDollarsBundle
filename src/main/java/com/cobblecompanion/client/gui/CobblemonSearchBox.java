package com.cobblecompanion.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

/**
 * Nachbau von Cobblemons eigener SearchWidget (com.cobblemon.mod.common.client.gui.pokedex
 * .widgets.SearchWidget.kt): dunkles Overlay-Panel + Lupen-Icon + fette "uniform"-Schrift mit
 * blinkendem Cursor bei Fokus, statt der nackten Vanilla-EditBox-Optik.
 *
 * Ursprünglich eine private innere Klasse von CompanionScreen (com.cobblecompanion.client.screens) -
 * hierher gezogen als eigenständige, öffentliche Klasse, damit auch Erweiterungsmodule
 * (com.cobblecompanion.api.CompanionTabExtension-Implementierungen wie der Wallet-Tab aus
 * CobbleCompanion: CobbleDollars) denselben Suchfeld-Look nutzen können, ohne CompanionScreens
 * Interna zu kennen. Deshalb vollständig eigenständig (eigener Font-Bezug statt
 * CompanionScreen.this.font, eigene Konstanten statt CompanionScreens private SEARCH_-Felder
 * bzw. COUNTER_TEXT_-Felder) - identisches Aussehen/Verhalten, keine Abhängigkeit mehr nach außen.
 */
public class CobblemonSearchBox extends EditBox {

    private static final ResourceLocation SEARCH_OVERLAY =
        ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/pokedex/pokedex_screen_search_overlay.png");
    private static final ResourceLocation SEARCH_ICON =
        ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/pokedex/search_icon.png");
    // Cobblemons Overlay-Textur deckt nicht nur das Suchfeld ab, sondern das gesamte linke
    // Panel (Suchfeld + Liste darunter) - native Auflösung der PNG, 1:1 übernommen.
    public static final int SEARCH_OVERLAY_W = 139;
    public static final int SEARCH_OVERLAY_H = 163;
    private static final int SEARCH_ICON_SIZE = 7; // Ziel-Größe auf dem Bildschirm (Quelle 14x14, 0.5 skaliert)

    private static final ResourceLocation TEXT_FONT = ResourceLocation.fromNamespaceAndPath("minecraft", "uniform");
    private static final float TEXT_SCALE = 1f;
    private static final int TEXT_COLOR = 0xFFFFFF;

    private final String hintText;
    private long focusedTime;

    public CobblemonSearchBox(int x, int y, int width, int height, String hintText) {
        super(Minecraft.getInstance().font, x, y, width, height, Component.literal("Search"));
        this.hintText = hintText;
        this.setMaxLength(48);
        this.focusedTime = System.currentTimeMillis();
    }

    @Override
    public void setFocused(boolean focused) {
        if (focused) focusedTime = System.currentTimeMillis();
        super.setFocused(focused);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        int x = getX();
        int y = getY();

        // Overlay deckt das gesamte linke Panel ab (Suchfeld + Liste darunter), exakt wie
        // in Cobblemons SearchWidget.renderWidget().
        graphics.blit(SEARCH_OVERLAY, x, y,
            SEARCH_OVERLAY_W, SEARCH_OVERLAY_H,
            0f, 0f,
            SEARCH_OVERLAY_W, SEARCH_OVERLAY_H,
            SEARCH_OVERLAY_W, SEARCH_OVERLAY_H);

        graphics.blit(SEARCH_ICON, x + 3, y + 2,
            SEARCH_ICON_SIZE, SEARCH_ICON_SIZE,
            0f, 0f,
            14, 14,
            14, 14);

        String value = getValue();
        int cursor = getCursorPosition();
        boolean showCursor = isFocused() && ((System.currentTimeMillis() - focusedTime) / 300L % 2L == 0L);
        String display;
        if (isFocused()) {
            display = value + (cursor == value.length() && showCursor ? "_" : "");
        } else {
            display = value.isEmpty() ? hintText : value;
        }

        int startX = x + 13;
        int startY = y + 1;
        drawBoldText(graphics, display, startX, startY);

        if (showCursor && !value.isEmpty() && cursor != value.length()) {
            int startToCursorWidth = Minecraft.getInstance().font.width(value.substring(0, cursor));
            graphics.fill(
                startX + startToCursorWidth - 1, startY,
                startX + startToCursorWidth, startY + 9,
                0xFFD0D0D0);
        }
    }

    /** Entspricht CompanionScreen.drawScaledBoldText(graphics, text, x, y, TEXT_SCALE, TEXT_COLOR). */
    private static void drawBoldText(GuiGraphics graphics, String text, int x, int y) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1f);

        MutableComponent component = Component.literal(text)
            .withStyle(Style.EMPTY.withFont(TEXT_FONT))
            .withStyle(ChatFormatting.BOLD);

        graphics.drawString(Minecraft.getInstance().font, component, 0, 0, TEXT_COLOR, true);
        graphics.pose().popPose();
    }
}
