package com.cobblecompanion.api;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Von einem CobbleCompanion-Erweiterungsmodul implementierter Tab-Inhalt (z.B. der Wallet-Tab aus
 * CobbleCompanion: CobbleDollars). Wird beim Modstart über
 * {@link CompanionExtensions#registerTab(int, CompanionTabExtension)} angemeldet - ist für einen
 * Tab-Index nichts registriert, zeichnet CompanionScreen dort seinen bestehenden
 * "nicht installiert"-Platzhalter.
 *
 * Methoden bekommen bewusst KEINEN eigenen Lebenszyklus-State von CompanionScreen übergeben -
 * jede Implementierung hält ihren eigenen Zustand (Eingabefelder, Scroll-Position, ...) selbst,
 * exakt wie es heute jeder Tab innerhalb von CompanionScreen bereits tut.
 */
public interface CompanionTabExtension {

    /** true, wenn dieser Tab aktuell benutzbar ist (z.B. Server meldet CobbleDollars-Integration verfügbar). */
    boolean isAvailable(CompanionTabContext ctx);

    void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CompanionTabContext ctx);

    /**
     * Wird GANZ AM ENDE von CompanionScreen.render() aufgerufen (wie Basis' eigene
     * renderActiveSearchSuggestions) - für Elemente, die über allen anderen Widgets liegen müssen,
     * z.B. eine Suchvorschlagsbox unter einem Empfänger-Suchfeld. Getrennt von render(), damit die
     * Z-Reihenfolge mit Basis' eigenen Tabs übereinstimmt.
     */
    default void renderTopLayerContent(GuiGraphics graphics, int mouseX, int mouseY, CompanionTabContext ctx) {}

    /** @return true, wenn der Klick von dieser Extension konsumiert wurde. */
    boolean mouseClicked(double mouseX, double mouseY, int button, CompanionTabContext ctx);

    default void mouseReleased(double mouseX, double mouseY, int button, CompanionTabContext ctx) {}

    /** Für ziehbare Scrollbars innerhalb des Tabs - CompanionScreen ruft dies nur auf, während {@link #isDraggingScrollbar()} true liefert. */
    default void mouseDragged(double mouseX, double mouseY, CompanionTabContext ctx) {}

    default boolean isDraggingScrollbar() { return false; }

    default boolean mouseScrolled(double mouseX, double mouseY, double scrollY, CompanionTabContext ctx) { return false; }

    default boolean keyPressed(int keyCode, int scanCode, int modifiers, CompanionTabContext ctx) { return false; }

    default boolean charTyped(char chr, int modifiers, CompanionTabContext ctx) { return false; }

    /** true, wenn gerade ein Eingabefeld dieser Extension Texteingaben annimmt (blockt z.B. Basis' "E"-Schließen-Taste). */
    default boolean isCapturingTextInput() { return false; }

    /** Aufgerufen, sobald der Spieler zu diesem Tab wechselt (z.B. um Suchfelder neu zu positionieren). */
    default void onTabOpened(CompanionTabContext ctx) {}

    // ===== Blockierendes Ja/Nein-Bestätigungs-Overlay (optional, wie z.B. beim Wallet-Transfer) =====

    default boolean hasBlockingOverlay() { return false; }

    default void renderBlockingOverlay(GuiGraphics graphics, int mouseX, int mouseY, CompanionTabContext ctx) {}

    default boolean blockingOverlayMouseClicked(double mouseX, double mouseY, CompanionTabContext ctx) { return false; }
}
