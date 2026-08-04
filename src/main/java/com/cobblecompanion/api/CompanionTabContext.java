package com.cobblecompanion.api;

import com.cobblecompanion.client.data.ClientNetworkUtil;
import com.cobblecompanion.client.gui.CobblemonSearchBox;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Schmale Fassade, die CompanionScreen an registrierte {@link CompanionTabExtension}s reicht -
 * gibt Zugriff auf Fenstergeometrie und das gemeinsame Mini-GUI-Toolkit (Text/Buttons/Scrollbars/
 * Netzwerkversand), ohne die privaten Interna von CompanionScreen offenzulegen. Implementiert von
 * CompanionScreen selbst (this wird als Context durchgereicht).
 *
 * Layout-Konstanten (CONFIRM_BOX_*, SETTINGS_CONTENT_*, ...) sind bewusst NICHT Teil dieser
 * Schnittstelle - jede Extension bringt für ihren eigenen Tab-Inhalt eigene Maße mit, exakt wie es
 * heute schon jeder Tab innerhalb von CompanionScreen tut.
 */
public interface CompanionTabContext {

    int guiLeft();
    int guiTop();
    int guiWidth();
    int guiHeight();
    /** Volle Fenstergröße (für Vollbild-Verdunklung von Bestätigungs-Overlays), siehe Screen.width/height. */
    int screenWidth();
    int screenHeight();

    void drawSmallLabel(GuiGraphics graphics, String text, int x, int y, float scale, int color, boolean bold, boolean uniformFont);
    void drawScaledBoldText(GuiGraphics graphics, String text, int x, int y, float scale, int color);
    int smallLabelWidth(String text, float scale, boolean bold, boolean uniformFont);
    List<String> wrapText(String text, float scale, boolean bold, boolean uniformFont, int maxWidth);

    void renderConfirmButton(GuiGraphics graphics, int x, int y, int w, int h, String label, int color, int mouseX, int mouseY);
    void renderScrollbar(GuiGraphics graphics, int barX, int barWidth, int trackTop, int trackHeight, double scrollAmount, int maxScroll);
    boolean isMouseOverScrollbar(double mouseX, double mouseY, int barX, int barWidth, int trackTop, int trackHeight);
    double scrollAmountFromMouseY(double mouseY, int trackTop, int trackHeight, int maxScroll);
    boolean isInRect(double mouseX, double mouseY, int x, int y, int w, int h);

    /** Vorschlagsbox unter einem CobblemonSearchBox-Suchfeld, gleicher Look wie bei Basis' eigenen Tabs. */
    void renderSearchSuggestions(GuiGraphics graphics, int mouseX, int mouseY, CobblemonSearchBox box, List<String> suggestions);

    /** @return true, wenn der Klick eine Vorschlagszeile getroffen und konsumiert hat. */
    boolean handleSearchSuggestionClick(double mouseX, double mouseY, CobblemonSearchBox box, List<String> suggestions);

    /**
     * Sendet ein Payload zum Server, nur wenn der verbundene Server dieses Payload überhaupt
     * ausgehandelt hat (siehe ClientNetworkUtil-Klassenkommentar - sonst UnsupportedOperationException/
     * Client-Crash bei fehlender/inkompatibler Server-Mod). Eigene default-Implementierung aus
     * demselben Grund wie tr(...).
     */
    default void sendToServer(CustomPacketPayload payload) {
        if (ClientNetworkUtil.canSendToServerOrWarn(payload.type().id())) {
            PacketDistributor.sendToServer(payload);
        }
    }

    /**
     * Übersetzt einen Sprachschlüssel (respektiert das im Client aktive Sprachpaket) - eigene
     * default-Implementierung statt CompanionScreens private-static tr(...) zu verbiegen, die
     * intern auch aus statischen Kontexten heraus aufgerufen wird.
     */
    default String tr(String key) {
        return net.minecraft.client.resources.language.I18n.get(key);
    }

    default String tr(String key, Object... args) {
        return net.minecraft.client.resources.language.I18n.get(key, args);
    }
}
