package com.cobblecompanion.client.screens;

import com.cobblecompanion.api.CompanionTabContext;
import com.cobblecompanion.client.gui.CobblemonSearchBox;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Reicht CompanionScreens paketprivate Zeichen-/Geometrie-Hilfsmethoden als {@link CompanionTabContext}
 * an registrierte Erweiterungen (siehe com.cobblecompanion.api) durch. Als eigene Klasse statt
 * CompanionScreen selbst implements CompanionTabContext, weil CompanionScreen bereits eigene
 * private-static tr(...)/sendToServer(...)-Methoden hat, die auch aus statischen Kontexten dieser
 * Klasse aufgerufen werden - Java erlaubt keine gleichnamige static/instance-Kollision mit einer
 * geerbten Interface-Methode. tr(...)/sendToServer(...) kommen deshalb unverändert aus
 * CompanionTabContexts eigenen default-Implementierungen.
 */
final class CompanionScreenTabContext implements CompanionTabContext {

    private final CompanionScreen screen;

    CompanionScreenTabContext(CompanionScreen screen) {
        this.screen = screen;
    }

    @Override public int guiLeft() { return screen.guiLeft(); }
    @Override public int guiTop() { return screen.guiTop(); }
    @Override public int guiWidth() { return screen.guiWidth(); }
    @Override public int guiHeight() { return screen.guiHeight(); }
    @Override public int screenWidth() { return screen.screenWidth(); }
    @Override public int screenHeight() { return screen.screenHeight(); }

    @Override
    public void drawSmallLabel(GuiGraphics graphics, String text, int x, int y, float scale, int color, boolean bold, boolean uniformFont) {
        screen.drawSmallLabel(graphics, text, x, y, scale, color, bold, uniformFont);
    }

    @Override
    public void drawScaledBoldText(GuiGraphics graphics, String text, int x, int y, float scale, int color) {
        screen.drawScaledBoldText(graphics, text, x, y, scale, color);
    }

    @Override
    public int smallLabelWidth(String text, float scale, boolean bold, boolean uniformFont) {
        return screen.smallLabelWidth(text, scale, bold, uniformFont);
    }

    @Override
    public List<String> wrapText(String text, float scale, boolean bold, boolean uniformFont, int maxWidth) {
        return screen.wrapText(text, scale, bold, uniformFont, maxWidth);
    }

    @Override
    public void renderConfirmButton(GuiGraphics graphics, int x, int y, int w, int h, String label, int color, int mouseX, int mouseY) {
        screen.renderConfirmButton(graphics, x, y, w, h, label, color, mouseX, mouseY);
    }

    @Override
    public void renderScrollbar(GuiGraphics graphics, int barX, int barWidth, int trackTop, int trackHeight, double scrollAmount, int maxScroll) {
        screen.renderScrollbar(graphics, barX, barWidth, trackTop, trackHeight, scrollAmount, maxScroll);
    }

    @Override
    public boolean isMouseOverScrollbar(double mouseX, double mouseY, int barX, int barWidth, int trackTop, int trackHeight) {
        return screen.isMouseOverScrollbar(mouseX, mouseY, barX, barWidth, trackTop, trackHeight);
    }

    @Override
    public double scrollAmountFromMouseY(double mouseY, int trackTop, int trackHeight, int maxScroll) {
        return screen.scrollAmountFromMouseY(mouseY, trackTop, trackHeight, maxScroll);
    }

    @Override
    public boolean isInRect(double mouseX, double mouseY, int x, int y, int w, int h) {
        return screen.isInRect(mouseX, mouseY, x, y, w, h);
    }

    @Override
    public void renderSearchSuggestions(GuiGraphics graphics, int mouseX, int mouseY, CobblemonSearchBox box, List<String> suggestions) {
        screen.renderSearchSuggestions(graphics, mouseX, mouseY, box, suggestions);
    }

    @Override
    public boolean handleSearchSuggestionClick(double mouseX, double mouseY, CobblemonSearchBox box, List<String> suggestions) {
        return screen.handleSearchSuggestionClick(mouseX, mouseY, box, suggestions);
    }

    // tr(...) und sendToServer(...): bewusst NICHT überschrieben, siehe Klassenkommentar - nutzt
    // CompanionTabContexts eigene default-Implementierung.
}
