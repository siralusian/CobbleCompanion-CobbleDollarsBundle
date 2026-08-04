package com.cobblecompanion.client.screens;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.api.CompanionExtensions;
import com.cobblecompanion.api.CompanionTabContext;
import com.cobblecompanion.api.CompanionTabExtension;
import com.cobblecompanion.client.data.ClientAdminHelper;
import com.cobblecompanion.client.data.ClientDexCompletionHelper;
import com.cobblecompanion.client.data.ClientNetworkUtil;
import com.cobblecompanion.client.data.ClientFriendsHelper;
import com.cobblecompanion.client.data.ClientHomeHelper;
import com.cobblecompanion.client.data.ClientTeamBuilderHelper;
import com.cobblecompanion.client.data.ClientGamemodeInventoryHelper;
import com.cobblecompanion.client.data.ClientGiftHelper;
import com.cobblecompanion.client.data.ClientProfessorHelper;
import com.cobblecompanion.client.data.ClientLivingDexHelper;
import com.cobblecompanion.client.data.ClientServerRulesHelper;
import com.cobblecompanion.client.data.ClientCreativeTimeHelper;
import com.cobblecompanion.client.data.ClientSettingsHelper;
import com.cobblecompanion.client.data.PCSortHelper;
import com.cobblecompanion.client.data.ClientTodoHelper;
import com.cobblecompanion.client.data.ClientTypeHelper;
import com.cobblecompanion.client.data.ClientWhoNeedsHelper;
import com.cobblecompanion.data.TodoHelper;
import com.cobblecompanion.client.gui.CobblemonSearchBox;
import com.cobblecompanion.client.gui.PokemonSlotRenderer;
import com.cobblecompanion.data.ServerRulesManager;
import com.cobblecompanion.data.TypeHelper;
import com.cobblecompanion.network.EvolvePokemonRequestPacket;
import com.cobblecompanion.network.FriendActionPacket;
import com.cobblecompanion.network.FriendsListRequestPacket;
import com.cobblecompanion.network.LivingDexRequestPacket;
import com.cobblecompanion.network.MyDuplicatesRequestPacket;
import com.cobblecompanion.network.ServerRuleChangePacket;
import com.cobblecompanion.network.TeleportPreferencePacket;
import com.cobblecompanion.network.TeleportToFriendPacket;
import com.cobblecompanion.network.GiftOfferPacket;
import com.cobblecompanion.network.GiftAcceptPacket;
import com.cobblecompanion.network.GiftDeclinePacket;
import com.cobblecompanion.network.MyPartyRequestPacket;
import com.cobblecompanion.network.ProfessorPlayerListRequestPacket;
import com.cobblecompanion.network.ProfessorRctListRequestPacket;
import com.cobblecompanion.network.ProfessorPCRequestPacket;
import com.cobblecompanion.network.ProfessorPokedexRequestPacket;
import com.cobblecompanion.network.ProfessorLivingDexRequestPacket;
import com.cobblecompanion.network.AdminEditPokemonPacket;
import com.cobblecompanion.network.AdminReleasePokemonPacket;
import com.cobblecompanion.network.AdminGiftPokemonPacket;
import com.cobblecompanion.network.TodoRequestPacket;
import com.cobblecompanion.network.TypeRequestPacket;
import com.cobblecompanion.network.WhoNeedsQueryPacket;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.client.gui.TypeIcon;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public class CompanionScreen extends Screen {

    /**
     * Zentrale Sende-Stelle für ALLE Client->Server-Pakete dieses Screens (Live-Crash-Fund, siehe
     * ClientEventHandler-Klassenkommentar zum Ctrl/Alt-Bugfix): dieser Screen wird rein
     * client-seitig anstelle von Cobblemons Pokedex-GUI eingeblendet (ClientEventHandler.
     * onScreenOpen matcht nur den Klassennamen), unabhängig davon, ob der verbundene Server
     * überhaupt eine kompatible CobbleCompanion-Version hat. Jedes der früher direkten
     * sendToServer(...)-Aufrufe hier lief deshalb Gefahr, bei fehlendem/
     * inkompatiblem Server eine UnsupportedOperationException zu werfen und den Client zu
     * crashen. Prüft jetzt per ClientNetworkUtil, ob der Server DIESES konkrete Payload
     * überhaupt ausgehandelt hat (nicht pauschal für alle CobbleCompanion-Kanäle - siehe
     * ClientNetworkUtil-Klassenkommentar, die werden unabhängig voneinander negoziiert), bevor
     * gesendet wird, und zeigt beim ersten Fehlschlag pro Verbindung einen Chat-Hinweis statt
     * die Aktion stillschweigend wirkungslos zu lassen.
     *
     * Bleibt private static (wird auch aus statischen Kontexten dieser Klasse aufgerufen) -
     * CompanionTabContext.sendToServer ist eine eigene default-Methode mit identischer Logik für
     * Extensions, siehe dortigen Klassenkommentar.
     */
    private static void sendToServer(CustomPacketPayload payload) {
        if (ClientNetworkUtil.canSendToServerOrWarn(payload.type().id())) {
            PacketDistributor.sendToServer(payload);
        }
    }

    // Einmalig erzeugte Fassade für registrierte CompanionTabExtension-Implementierungen (siehe
    // com.cobblecompanion.api) - lazy statt Feld-Initialisierer, da guiLeft/guiTop erst in init()
    // gesetzt werden, der Context selbst aber nur delegiert (kein eigener Zustand nötig).
    private CompanionTabContext tabContextInstance;

    private CompanionTabContext tabContext() {
        if (tabContextInstance == null) tabContextInstance = new CompanionScreenTabContext(this);
        return tabContextInstance;
    }

    /** true, wenn die Extension des aktuell offenen Tabs gerade ein blockierendes Ja/Nein-Overlay zeigen will. */
    private boolean extensionHasBlockingOverlay() {
        CompanionTabExtension ext = CompanionExtensions.getTab(currentTab);
        return ext != null && ext.hasBlockingOverlay();
    }

    // ===== Für CompanionScreenTabContext (siehe dort): reine Zugriffs-Fassade, keine eigene Logik =====
    int guiLeft() { return guiLeft; }
    int guiTop() { return guiTop; }
    int guiWidth() { return GUI_WIDTH; }
    int guiHeight() { return GUI_HEIGHT; }
    int screenWidth() { return this.width; }
    int screenHeight() { return this.height; }

    // GUI Dimensionen
    private static final int GUI_WIDTH = 345;
    private static final int GUI_HEIGHT = 207;
    private static final int BUTTON_STRIP_WIDTH = 53;
    private static final int BUTTON_STRIP_HEIGHT = 130;
    // Tab-Indizes als Konstanten
    public static final int TAB_POKEDEX     = 0;
    public static final int TAB_LIVINGDEX   = 1;
    public static final int TAB_TODO        = 2;
    public static final int TAB_WHONEEDS    = 3;
    public static final int TAB_TYPES       = 4;
    public static final int TAB_TEAMBUILDER = 5;
    public static final int TAB_WALLET      = 6;  // Cobbledollars-Überweisung (nur wenn Cobbledollars-Mod verfügbar)
    public static final int TAB_SEARCH      = 7;
    public static final int TAB_HOME        = 8;
    public static final int TAB_FRIENDS     = 9;
    public static final int TAB_PROFESSOR   = 10;
    public static final int TAB_SETTINGS    = 11;

    // Sprachschlüssel je Tab (Index = TAB_*). Übersetzt beim Zeichnen via tr(...).
    private static final String[] TAB_NAMES = {
        "cobblecompanion.tab.pokedex", "cobblecompanion.tab.livingdex", "cobblecompanion.tab.todo", "cobblecompanion.tab.whoneeds",
        "cobblecompanion.tab.types", "cobblecompanion.tab.teambuilder", "cobblecompanion.tab.wallet", "cobblecompanion.tab.search",
        "cobblecompanion.tab.home", "cobblecompanion.tab.friends", "cobblecompanion.tab.professor", "cobblecompanion.tab.settings"
};

    private final Screen originalScreen;
    private int guiLeft;
    private int guiTop;
    private int currentTab = 8; // Standard: Home
    private int settingsSubTab = 0; // 0=All Options, 1=ToDo, 2=Who Needs?, 3=Friends, 4=Server

    // Scroll-Zustand für ToDo- und Type-Tab (Cobblemon-Style Scrollbar, siehe renderScrollbar()).
    // Nur eine Scrollbar kann gleichzeitig gezogen werden, da immer nur ein Tab aktiv ist.
    private double todoScrollAmount = 0;
    private double typeResultScrollAmount = 0;
    private double whoNeedsScrollAmount = 0;
    private double homeGiftScrollAmount = 0;
    private double professorScrollAmount = 0;
    private boolean todoScrollbarDragging = false;
    private boolean typeScrollbarDragging = false;
    private boolean whoNeedsScrollbarDragging = false;
    private boolean homeGiftScrollbarDragging = false;
    private boolean professorScrollbarDragging = false;
    private boolean teamBuilderResultScrollbarDragging = false;

    // ===== Cobblemon-Style Suchleiste (gemeinsam für Type- und Who-Needs-Tab) =====
    // Nachgebaut aus Cobblemons eigener SearchWidget.kt (com.cobblemon.mod.common.client.gui
    // .pokedex.widgets.SearchWidget): dunkles Overlay-Panel hinter Suchfeld + Liste, Lupen-Icon
    // links im Suchfeld, fette "uniform"-Schrift, blinkender Cursor bei Fokus.
    private static final ResourceLocation SEARCH_OVERLAY =
        ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/pokedex/pokedex_screen_search_overlay.png");
    private static final ResourceLocation SEARCH_ICON =
        ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/pokedex/search_icon.png");
    // Cobblemons Overlay-Textur deckt nicht nur das Suchfeld ab, sondern das gesamte linke
    // Panel (Suchfeld + Liste darunter) - native Auflösung der PNG, 1:1 übernommen.
    private static final int SEARCH_OVERLAY_W = 139;
    private static final int SEARCH_OVERLAY_H = 163;
    private static final int SEARCH_ICON_SIZE = 7; // Ziel-Größe auf dem Bildschirm (Quelle 14x14, 0.5 skaliert)

    // Cobblemons eigener kleiner Pokedex-Tooltip (com.cobblemon.mod.common.client.gui.pokedex
    // .PokedexTooltip.kt): schmale, um posX zentrierte Box statt der breiten Standard-
    // Minecraft-Tooltip-Box - Rand-Textur links/rechts (je 1px, gestreckt) + Hintergrund-
    // Textur in der Mitte.
    private static final ResourceLocation TOOLTIP_EDGE =
        ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/pokedex/tooltip_edge.png");
    private static final ResourceLocation TOOLTIP_BACKGROUND =
        ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/pokedex/tooltip_background.png");
    private static final int TOOLTIP_HEIGHT = 11;

    // ===== Gemeinsame Pokemon-Slot-Beschriftung (ToDo/Who-Needs/Types) =====
    // Nummer (oben links) + Level (unten rechts) auf einem pokedex_slot.png - genutzt von
    // renderPokemonNumberedSlot()/renderPokemonSlotNumber() und damit von allen drei Tabs, die
    // Slots zeichnen. EINE zentrale Justierschraube pro Eigenschaft statt einer pro Tab, ändert
    // also Nummer/Level gleichzeitig überall. Nummer und Level teilen sich sonst dieselbe obere
    // Kante eines nur 25px breiten Slots und überlagern sich leicht schon bei mittlerer
    // Schriftgröße - deshalb Nummer oben links, Level unten rechts (statt beide oben
    // nebeneinander), das erlaubt eine deutlich größere, lesbare Schrift. Cobblemon nutzt für
    // seine Slot-Nummer exakt Standardschrift + scale=0.5F, kein Bold (siehe drawSmallLabel-
    // Doku) - hier als Ausgangswert übernommen, leicht größer für unsere insgesamt etwas
    // größere GUI. JUSTIERSCHRAUBEN: Größe/Bold/Schriftart gemeinsam, X/Y je Ecke einzeln.
    private static final float POKEMON_SLOT_LABEL_SCALE = 0.5f;
    private static final boolean POKEMON_SLOT_LABEL_BOLD = false;
    private static final boolean POKEMON_SLOT_LABEL_UNIFORM_FONT = false;
    private static final int POKEMON_SLOT_NUMBER_OFFSET_X = 1;
    private static final int POKEMON_SLOT_NUMBER_OFFSET_Y = 1;
    private static final int POKEMON_SLOT_LEVEL_OFFSET_X = 1; // von der rechten Kante gemessen
    private static final int POKEMON_SLOT_LEVEL_OFFSET_Y = 0; // von der unteren Kante gemessen

    // ===== ToDo-Tab =====
    // Jede Zeile: links ein pokedex_slot.png mit dem aktuellen Pokemon (Nummer im Slot, wie
    // Who-Needs/Types), eine kurze type_bar.png oben rechts daneben (Typ-Icon + Name). Rechts
    // ein zweiter Slot mit dem Entwicklungs-Ergebnis + gespiegelte type_bar davor (Typ-Icon
    // nächst dem Slot, Name davor - Inhalt spiegelverkehrt zur oberen Bar). Dazwischen eine
    // helle Lücke mit Entwickeln-Button, falls die Entwicklung sofort ausgelöst werden kann.
    // Bei reiner Item-Entwicklung (Item nicht gehalten) ersetzt Item-Icon + Name den zweiten
    // Slot/die untere Bar/den Button.
    private static final ResourceLocation TODO_TYPE_BAR =
        ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/pokedex/type_bar.png");
    private static final int TODO_TYPE_BAR_NATIVE_W = 139;
    private static final int TODO_TYPE_BAR_NATIVE_H = 25;

    // --- Zeilen-Layout: JUSTIERSCHRAUBE TODO_ROW_WIDTH muss die gesamte Zeile (beide Slots +
    // beide Bars) innerhalb des verfügbaren Feldes halten - der TO-Slot darf nicht über den
    // rechten Rand hinausragen. TODO_ROW_X etwas nach links verschoben (war 25), damit direkt
    // rechts von der Zeile noch die Scrollbar Platz hat, ohne die rechte Begrenzung (gelbe
    // Linie) zu verletzen. Liste ist jetzt unbegrenzt scrollbar statt auf 5 Zeilen gedeckelt -
    // TODO_LIST_VISIBLE_HEIGHT steuert, wie viele Zeilen ohne Scrollen sichtbar sind. ---
    private static final int TODO_ROW_X = 21;
    private static final int TODO_ROW_Y = 30;
    private static final int TODO_ROW_WIDTH = 140;
    private static final int TODO_ROW_HEIGHT = 25; // = PokemonSlotRenderer.SLOT_SIZE
    private static final int TODO_ROW_SPACING = 8;
    private static final int TODO_LIST_VISIBLE_HEIGHT = 160;
    private static final int TODO_SCROLLBAR_GAP = 4;
    private static final int TODO_SCROLLBAR_WIDTH = 3;

    // --- Bars: JUSTIERSCHRAUBE Höhe der type_bar-Ausschnitte an den Slots. Breite ergibt sich
    // automatisch aus TODO_ROW_WIDTH (Bars überbrücken jetzt wieder die gesamte Lücke zwischen
    // den beiden Slots, wie ursprünglich bei Cobblemon). ---
    private static final int TODO_BAR_WIDTH = TODO_ROW_WIDTH - (2 * PokemonSlotRenderer.SLOT_SIZE);
    private static final int TODO_BAR_HEIGHT = 14;

    // --- Namens-Text in den Bars: Cobblemon zeichnet Namen (z.B. im Info-Panel oder im
    // Evolve-Auswahlbildschirm) durchgängig fett + in der "uniform"-Schrift statt Minecrafts
    // Standardschrift - hier übernommen. Ausgangs- (obere Bar) und Ziel-Pokemon (untere,
    // gespiegelte Bar) haben JEWEILS EIGENE Justierschrauben (Größe/X/Y/Bold), da sich z.B.
    // die Y-Position der beiden Zeilen unabhängig voneinander verschieben lassen soll. ---
    private static final float TODO_FROM_NAME_SCALE = 0.8f;
    private static final int TODO_FROM_NAME_OFFSET_X = 18;
    private static final int TODO_FROM_NAME_OFFSET_Y = -1;
    private static final boolean TODO_FROM_NAME_BOLD = true;
    private static final boolean TODO_FROM_NAME_UNIFORM_FONT = true;

    private static final float TODO_TO_NAME_SCALE = 0.8f;
    private static final int TODO_TO_NAME_OFFSET_X = 18;
    private static final int TODO_TO_NAME_OFFSET_Y = 8;
    private static final boolean TODO_TO_NAME_BOLD = true;
    private static final boolean TODO_TO_NAME_UNIFORM_FONT = true;

    // --- Typ-Icons in den Bars: Größe fix (Cobblemons TypeIcon kennt nur groß/klein via
    // small=true/false). X/Y unabhängig vom Namen einstellbar (jeweils von der Ecke der Bar
    // gemessen, an der das Icon sitzt - linke Ecke bei der oberen Bar, rechte Ecke bei der
    // gespiegelten unteren Bar), und wie bei den Namen für Ausgangs-/Ziel-Pokemon getrennt.
    // TODO_BAR_ICON_GAP ist nur eine grobe Platzreservierung für die Namens-Kürzung
    // (truncateLabel), kein Positions-Offset. ---
    private static final int TODO_BAR_TYPE_ICON_SIZE = 9;
    private static final int TODO_BAR_ICON_GAP = 2;
    // JUSTIERSCHRAUBE: Abstand zwischen 1. und 2. Typ-Icon bei zweityp-Pokemon (Cobblemons
    // eigener TypeIcon-Parameter "secondaryOffset") - war 15F (2. Icon komplett neben dem 1.,
    // kein Überlapp), jetzt kleiner damit das 2. Icon ca. 1/3 des 1. verdeckt.
    private static final float TODO_BAR_TYPE_ICON_SECONDARY_OFFSET = 5.5F;
    private static final int TODO_FROM_ICON_OFFSET_X = 1;
    private static final int TODO_FROM_ICON_OFFSET_Y = 2;
    private static final int TODO_TO_ICON_OFFSET_X = 6;
    private static final int TODO_TO_ICON_OFFSET_Y = 2;

    private static final int TODO_ITEM_ICON_SIZE = 16;

    // --- Entwickeln-Button, mittig über der Nahtstelle der beiden Bars (Cobblemons eigene
    // summary_evolve_button.png aus dem Party-Menü, liegt einfach über den Bars). Löst
    // Evolution.forceEvolve() serverseitig aus statt Cobblemons normalem "optional"-
    // Bestätigungsdialog, da der Klick hier schon die Bestätigung ist. Button-Text + Größe wie
    // Cobblemons eigener SummaryButton für den Evolve-Button (uniform+fett, textScale~1,
    // zentriert - siehe com.cobblemon.mod.common.client.gui.summary.SummaryButton).
    // JUSTIERSCHRAUBEN: Button-Größe, Text-Größe/Bold. ---
    private static final ResourceLocation TODO_EVOLVE_BUTTON =
        ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/summary/summary_evolve_button.png");
    private static final int TODO_EVOLVE_BTN_NATIVE_W = 54;
    private static final int TODO_EVOLVE_BTN_NATIVE_H = 15;
    private static final int TODO_EVOLVE_BTN_W = 56;
    private static final int TODO_EVOLVE_BTN_H = 13;
    private static final float TODO_EVOLVE_BTN_TEXT_SCALE = 1.0f;
    private static final boolean TODO_EVOLVE_BTN_TEXT_BOLD = true;
    private static final boolean TODO_EVOLVE_BTN_TEXT_UNIFORM_FONT = true;

    // ===== Type-Tab =====
    private CobblemonSearchBox typeSearchBox;
    // JUSTIERSCHRAUBE: Position/Größe des Suchfelds (Cobblemons eigene SearchWidget
    // sitzt bei x+26,y+28,128x11 - hier als Ausgangswert übernommen).
    private static final int TYPE_SEARCH_X = 26;
    private static final int TYPE_SEARCH_Y = 28;
    private static final int TYPE_SEARCH_W = 128;
    private static final int TYPE_SEARCH_H = 11;
    private static final String TYPE_SEARCH_TOOLTIP_KEY = "cobblecompanion.tooltip.type_search";
    // Cobblemons eigenes Typ-Icon-Sprite-Sheet (18 Icons, 1 Reihe, Reihenfolge nach
    // ElementalTypes.textureXMultiplier verifiziert: Normal..Fairy).
    private static final ResourceLocation TYPE_ICONS_SHEET =
        ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/types.png");
    private static final int TYPE_ICON_SHEET_W = 648;
    private static final int TYPE_ICON_SHEET_H = 36;
    private static final int TYPE_ICON_SRC_SIZE = 36;
    private static final String[] TYPE_ORDER = {
        "normal", "fire", "water", "grass", "electric", "ice", "fighting", "poison",
        "ground", "flying", "psychic", "bug", "rock", "ghost", "dragon", "dark", "steel", "fairy"
    };
    // JUSTIERSCHRAUBE: Raster-Layout des Typ-Icon-Grids (unter der Suchleiste, wo bei
    // Cobblemon normalerweise die Scroll-Liste sitzt). Jede Zelle zeigt Icon + Typname.
    private static final int TYPE_GRID_X = 22;
    private static final int TYPE_GRID_Y = 44;
    private static final int TYPE_GRID_COLUMNS = 3;
    private static final int TYPE_GRID_ICON_SIZE = 11; // 75% von vorher (18)
    private static final int TYPE_GRID_COL_WIDTH = 50;
    private static final int TYPE_GRID_ROW_H = 16;
    private static final int TYPE_GRID_TEXT_OFFSET_X = 14;
    private static final int TYPE_GRID_TEXT_OFFSET_Y = 1;
    // JUSTIERSCHRAUBE: linker Rand des Ergebnisbereichs rechts vom Icon-Grid.
    private static final int TYPE_RESULT_X = 180;
    private static final int TYPE_RESULT_Y = 28;
    private static final int TYPE_RESULT_ROW_H = 27; // 25 Slot + 2 Abstand, wie Cobblemons eigene Liste
    private static final int TYPE_SECTION_LABEL_HEIGHT = 10; // Höhe der "Your team:"/"Your PC:"-Zwischenüberschrift
    // --- Pokemon-Name (Punkte-Tier-Farbe): wie Cobblemons eigene Namen (uniform+fett, siehe
    // PokemonInfoWidget/SummaryButton). JUSTIERSCHRAUBEN Größe/X/Y/Bold. ---
    private static final float TYPE_RESULT_NAME_SCALE = 1.0f;
    private static final int TYPE_RESULT_NAME_OFFSET_X = 3;
    private static final int TYPE_RESULT_NAME_OFFSET_Y = -1;
    private static final boolean TYPE_RESULT_NAME_BOLD = true;
    private static final boolean TYPE_RESULT_NAME_UNIFORM_FONT = true;
    // --- Stichwort-Zeile (Attacke/Typ/Top-Level): wie Cobblemons Slot-Nummern (Standardschrift,
    // kein Bold) - kleinere Nebeninfo. Bricht automatisch in eine zweite Zeile um, falls sie
    // sonst über den rechten Rand des GUI hinauslaufen würde (TYPE_RESULT_LABEL_RIGHT_MARGIN
    // steuert, wie viel Platz zum Rand frei bleibt). JUSTIERSCHRAUBEN Größe/X/Y/Bold/Farbe. ---
    private static final float TYPE_RESULT_LABEL_SCALE = 1.0f;
    private static final int TYPE_RESULT_LABEL_OFFSET_X = 3;
    private static final int TYPE_RESULT_LABEL_OFFSET_Y = 7;
    private static final boolean TYPE_RESULT_LABEL_BOLD = false;
    private static final boolean TYPE_RESULT_LABEL_UNIFORM_FONT = true;
    private static final int TYPE_RESULT_LABEL_COLOR = 0xFFFFFF;
    private static final int TYPE_RESULT_LABEL_LINE_SPACING = 8; // Abstand zwischen 1. und 2. Zeile bei Umbruch
    private static final int TYPE_RESULT_LABEL_RIGHT_MARGIN = 15; // Puffer zum rechten GUI-Rand, ab dem umgebrochen wird

    // --- Ergebnisliste (Team/PC) ist jetzt unbegrenzt scrollbar statt auf 4 Zeilen gedeckelt,
    // Scrollbar sitzt am rechten GUI-Rand (dort ist mehr Platz als links Richtung GUI-Mitte). ---
    private static final int TYPE_SCROLLBAR_MARGIN = 20; // reservierter Platz am rechten GUI-Rand für die Scrollbar
    private static final int TYPE_SCROLLBAR_WIDTH = 3;
    private static final int TYPE_SCROLLBAR_GAP = 4;
    private static final int TYPE_RESULT_BOTTOM_MARGIN = 12; // Puffer zum unteren GUI-Rand

    // ===== ToDo-Tab rechte Hälfte: Dex-Vervollständigungshilfe (Pokédex/Living Dex, je nach
    // ClientSettingsHelper.isPcSortModePokedex() - dieselbe Einstellung wie bei der PC-
    // Sortierhilfe). Layout an TYPE_RESULT_X/Y angelehnt (rechte Hälfte, gleiche X-Konvention). =====
    private CobblemonSearchBox dexHelpSearchBox;
    private static final int DEXHELP_X = 180;
    private static final int DEXHELP_SEARCH_Y = 28;
    private static final int DEXHELP_SEARCH_W = 128;
    private static final int DEXHELP_SEARCH_H = 11;
    // JUSTIERSCHRAUBEN (Live-Test, Runde 1+2): Y -2 (Punkt 4/R1, oberes Ende nach oben), Höhe
    // -5 (R1 Punkt 3) -4 (R2 Punkt 5, unteres Ende nochmal höher) = -9 gesamt.
    private static final int DEXHELP_LIST_Y = 42;
    private static final int DEXHELP_LIST_VISIBLE_HEIGHT = 137;
    private static final int DEXHELP_SCROLLBAR_GAP = 4;
    // JUSTIERSCHRAUBE (R1 Punkt 1): +5 (Inhaltsbereich/rechter Rand nach links).
    private static final int DEXHELP_SCROLLBAR_MARGIN = 11; // Puffer zum rechten GUI-Rand
    private static final int DEXHELP_SCROLLBAR_WIDTH = 3;
    // JUSTIERSCHRAUBE: Scroll-Leiste zusätzlich zum Inhaltsbereich nach links, unabhängig von
    // DEXHELP_SCROLLBAR_MARGIN (R1 Punkt 2: 8px, R2 Punkt 6: nochmal 2px = 10px gesamt).
    private static final int DEXHELP_SCROLLBAR_EXTRA_LEFT = 10;
    // JUSTIERSCHRAUBEN: Zeilenhöhen je Zeilentyp - Fang-/Entwicklungs-Einträge nutzen einen Slot
    // (wie die Types-Tab-Ergebnisliste, TYPE_RESULT_ROW_H=27), Kategorie/Unterkategorie/Bedarf-
    // Zeilen sind reine Textzeilen (kleiner).
    private static final int DEXHELP_ROW_H = 27;
    private static final int DEXHELP_CATEGORY_H = 12;
    private static final int DEXHELP_SUBCATEGORY_H = 11;
    private static final int DEXHELP_NEED_ROW_H = 11; // etwas höher als vorher, damit das neue Item-Icon (Punkt 8) reinpasst
    private static final int DEXHELP_INDENT = 8;
    private static final int DEXHELP_NAME_OFFSET_X = 3;
    private static final int DEXHELP_NAME_OFFSET_Y = -1;
    private static final int DEXHELP_LABEL_OFFSET_X = 3;
    private static final int DEXHELP_LABEL_OFFSET_Y = 7;
    private static final int DEXHELP_LABEL_LINE_SPACING = 8;
    private static final int DEXHELP_LABEL_COLOR = 0xAAAAAA;
    private static final int DEXHELP_CATEGORY_COLOR = 0xFFAA00;
    private static final int DEXHELP_SUBCATEGORY_COLOR = 0x1FA8A8; // R1 Punkt 5 (Türkis) -> R2 Punkt 2: etwas dunkler
    // R3 Punkt 4: Such-Info-Blöcke (Vorentwicklung + gesuchte Art selbst) sind jetzt 3-zeilig
    // (Name/Status-Zeile/Status-Zeile) statt 2-zeilig - eigene, etwas höhere Zeilenhöhe.
    private static final int DEXHELP_INFO_ROW_H = 35;
    private static final int DEXHELP_STATUS_YES_COLOR = 0x55FF55;
    private static final int DEXHELP_STATUS_NO_COLOR = 0xFF5555;
    // Neu: Pfeile zwischen den gestapelten Slots im Suchmodus zur besseren Veranschaulichung -
    // zusätzlicher Vertikalabstand, in dem der Pfeil Platz hat (kommt zusätzlich zur normalen
    // Zeilenhöhe des jeweils VORHERIGEN Blocks). JUSTIERSCHRAUBE: -6px (Live-Test-Wunsch, engerer
    // Abstand) und der Pfeil selbst +3px länger (DEXHELP_ARROW_EXTRA_LENGTH).
    private static final int DEXHELP_SEARCH_ARROW_GAP = 4;
    private static final float DEXHELP_ARROW_EXTRA_LENGTH = 3f;
    private static final int DEXHELP_ARROW_OFFSET_Y = -9;
    // Verzweigungen (z.B. Evoli -> 8 Entwicklungen): der Pfeil kommt NUR einmal vom
    // Ausgangs-Pokemon zur ERSTEN Option - alle weiteren Optionen aus DERSELBEN Quelle werden
    // direkt darunter gestapelt, nur mit diesem kleinen Abstand, ohne eigenen Pfeil.
    private static final int DEXHELP_SEARCH_SIBLING_GAP = 3;
    // JUSTIERSCHRAUBEN: Icon-Größe/Position in der Bedarfsliste (R1 Punkt 8, R2 Punkt 3: Icon
    // weiter nach unten, Text mittig zum Icon).
    private static final int DEXHELP_NEED_ICON_SIZE = 16;
    private static final float DEXHELP_NEED_ICON_SCALE = 0.6f;
    private static final int DEXHELP_NEED_ICON_OFFSET_Y = 1;
    private static final int DEXHELP_NEED_TEXT_OFFSET_X = 11; // Platz nach dem Icon
    private static final int DEXHELP_NEED_TEXT_OFFSET_Y = 2; // Text vertikal mittig zum Icon
    // Punkt 7: Linkkabel Blau/Grau, Sonderbonbon Lila - feste Farben statt Typ-Zuordnung.
    private static final int DEXHELP_LINK_CABLE_COLOR = 0x7799AA;
    private static final int DEXHELP_RARE_CANDY_COLOR = 0xB266E0;
    // Punkt 6: Entwicklungssteine nach dem Typ eingefärbt, den sie repräsentieren (Community-
    // Konvention, offiziell sind Steine nicht selbst typisiert) - Standard-Typenfarben-Palette.
    private static final Map<String, Integer> DEXHELP_STONE_TYPE_COLORS = Map.ofEntries(
        Map.entry("fire_stone", 0xF08030),
        Map.entry("water_stone", 0x6890F0),
        Map.entry("thunder_stone", 0xF8D030),
        Map.entry("leaf_stone", 0x78C850),
        Map.entry("moon_stone", 0xEE99AC),
        Map.entry("sun_stone", 0xF08030),
        Map.entry("shiny_stone", 0xEE99AC),
        Map.entry("dusk_stone", 0x705848),
        Map.entry("dawn_stone", 0xF85888),
        Map.entry("ice_stone", 0x98D8D8)
    );

    // Standard-Typenfarben-Palette (Bulbapedia/Serebii-Konvention) - für die Team-Builder "Type"-
    // Liste, damit jeder Typname in seiner eigenen Farbe erscheint.
    private static final Map<String, Integer> TYPE_TEXT_COLORS = Map.ofEntries(
        Map.entry("normal", 0xA8A878),
        Map.entry("fire", 0xF08030),
        Map.entry("water", 0x6890F0),
        Map.entry("electric", 0xF8D030),
        Map.entry("grass", 0x78C850),
        Map.entry("ice", 0x98D8D8),
        Map.entry("fighting", 0xC03028),
        Map.entry("poison", 0xA040A0),
        Map.entry("ground", 0xE0C068),
        Map.entry("flying", 0xA890F0),
        Map.entry("psychic", 0xF85888),
        Map.entry("bug", 0xA8B820),
        Map.entry("rock", 0xB8A038),
        Map.entry("ghost", 0x705898),
        Map.entry("dragon", 0x7038F8),
        Map.entry("dark", 0x705848),
        Map.entry("steel", 0xB8B8D0),
        Map.entry("fairy", 0xEE99AC)
    );

    private double dexHelpScrollAmount = 0;
    private boolean dexHelpScrollbarDragging = false;
    // Ausklappbare Kategorien/Unterkategorien - Set aus Schlüsseln ("CATCH", "EVOLVE",
    // "EVOLVE_LEVEL", ...), alle standardmäßig eingeklappt (die Gesamtliste kann sehr lang
    // werden, siehe Nutzer-Vorgabe).
    private final Set<String> dexHelpExpanded = new java.util.HashSet<>();
    // true = Suchfeld hat eine Spezies bestätigt -> zeigt deren Vorentwicklung + direkte
    // Entwicklungsziele statt der kategorisierten Gesamtliste (siehe Nutzer-Vorgabe).
    private boolean dexHelpSearchActive = false;

    // --- Typ-Icon vor "Strong against X" und vor jedem Eintrag in "Effective types: ...".
    // Minimal größer als der Text daneben, wie beim Grid links (TYPE_GRID_ICON_SIZE=11).
    // JUSTIERSCHRAUBEN: Größe, Abstand zum Text. ---
    private static final int TYPE_INLINE_ICON_SIZE = 12;
    private static final int TYPE_INLINE_ICON_GAP = 3;
    // --- Titelzeile ("Strong against X"): JUSTIERSCHRAUBEN Größe/Y/Bold/Farbe/Zeilenhöhe. ---
    private static final float TYPE_TITLE_SCALE = 1.0f;
    private static final int TYPE_TITLE_OFFSET_Y = 2;
    private static final boolean TYPE_TITLE_BOLD = true;
    private static final boolean TYPE_TITLE_UNIFORM_FONT = true;
    private static final int TYPE_TITLE_COLOR = 0xFFAA00;
    private static final int TYPE_TITLE_LINE_HEIGHT = 13;
    // --- "Effective types: ..."-Zeile: JUSTIERSCHRAUBEN Größe/Y/Bold/Farbe/Zeilenhöhe. ---
    private static final float TYPE_EFFECTIVE_SCALE = 1.0f;
    private static final int TYPE_EFFECTIVE_OFFSET_Y = 2;
    private static final boolean TYPE_EFFECTIVE_BOLD = false;
    private static final boolean TYPE_EFFECTIVE_UNIFORM_FONT = true;
    private static final int TYPE_EFFECTIVE_COLOR = 0xFFFF55;
    private static final int TYPE_EFFECTIVE_LINE_HEIGHT = 13;

    // --- Gesuchtes Pokemon (falls die Type-Suche ein Pokemon statt eines Typs war): Panel
    // über dem Titel. Klick springt zum Pokédex-Eintrag (siehe jumpToPokedexEntry()), Tooltip
    // "To Pokédex entry" beim Hovern, bewusst OHNE Hover-Highlight. JUSTIERSCHRAUBE: Abstand zum
    // Titel darunter. ---
    private static final int TYPE_QUERY_SLOT_GAP = 5;

    // EXAKT per Bytecode-Analyse von Cobblemons PokedexGUI.class ermittelt (kein Schätzwert!):
    // dort wird "pokemonInfoWidget" bei super(x, y, 137, 68, ...) mit x = frameX+180,
    // y = frameY+28 konstruiert, wobei frameX/Y = (screenWidth-345)/2 bzw. (screenHeight-207)/2.
    // Da unser eigenes GUI_WIDTH/HEIGHT = 345/207 (identisch zu Cobblemons Rahmen) und
    // guiLeft/guiTop genauso zentriert werden, UND TYPE_RESULT_X/Y bereits zufällig exakt 180/28
    // sind, liegt "guiLeft+TYPE_RESULT_X, guiTop+TYPE_RESULT_Y" bereits GENAU an der Stelle, wo
    // Cobblemon das Info-Widget (Dex-Nr., Name, Typen, Modell, Pfeile, Sound) tatsächlich
    // rendert - keine Zentrierung/Justierschraube nötig, nur die Originalgröße übernehmen.
    private static final int TYPE_QUERY_PANEL_W = 137;
    // JUSTIERSCHRAUBE: 68 (Original-Widget-Höhe) + 30px nach unten, damit auch die unteren
    // Elemente (Pfeile/Sound-Button) sichtbar sind.
    private static final int TYPE_QUERY_PANEL_H = 98;

    // Kein eigenes Custom-Rendering mehr (alte Fassung in backup_type_query_panel_custom_render.txt
    // gesichert): stattdessen wird an der Stelle von Titel + "Effective types"-Zeile(n) der ECHTE
    // Cobblemon-Pokedex als Hintergrund gerendert (siehe renderPokedexBackdrop()/typeQueryHoleRect()),
    // der Text wird einfach obendrauf gezeichnet - kein separates Rechteck/keine eigenen Maße
    // mehr nötig, die Fläche ergibt sich direkt aus Titel+Effective-Types (typeHeaderTextHeight()).

    // ===== Who-Needs-Tab =====
    private CobblemonSearchBox whoNeedsSearchBox;
    // JUSTIERSCHRAUBE: gleiche Suchfeld-Position wie beim Type-Tab.
    private static final int WHONEEDS_SEARCH_X = 26;
    private static final int WHONEEDS_SEARCH_Y = 28;
    private static final int WHONEEDS_SEARCH_W = 128;
    private static final int WHONEEDS_SEARCH_H = 11;
    private static final String WHONEEDS_SEARCH_TOOLTIP_KEY = "cobblecompanion.tooltip.whoneeds_search";
    // Umschalt-Button Pokédex-/Living-Dex-Modus, als Icon-Button direkt am rechten Ende
    // der Suchleiste (genau wie Cobblemons eigener "Search By..."-Button direkt rechts
    // neben dessen 128px breitem Suchfeld sitzt). Bündig bis zum rechten Rand des
    // Overlay-Panels (SEARCH_OVERLAY_W=139 ab WHONEEDS_SEARCH_X), damit er nicht darüber hinausragt.
    private static final int WHONEEDS_MODE_BTN_X = WHONEEDS_SEARCH_X + WHONEEDS_SEARCH_W;
    private static final int WHONEEDS_MODE_BTN_Y = WHONEEDS_SEARCH_Y;
    private static final int WHONEEDS_MODE_BTN_W = SEARCH_OVERLAY_W - WHONEEDS_SEARCH_W;
    private static final int WHONEEDS_MODE_BTN_H = WHONEEDS_SEARCH_H;
    private static final int WHONEEDS_MODE_BTN_ICON_SIZE = 7;
    private static final int WHONEEDS_MODE_BTN_ICON_OFFSET_X = 2;
    private static final int WHONEEDS_MODE_BTN_ICON_OFFSET_Y = 2;
    // Icons für die beiden Modi: Cobblemons eigene Pokedex-Tab-Icons, hier zweckentfremdet
    // zum Umschalten zwischen Pokédex-Modus (Species-Tab-Icon) und Living-Dex-Modus (Drops-Tab-Icon).
    private static final ResourceLocation WHONEEDS_MODE_ICON_POKEDEX =
        ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/pokedex/tab_species.png");
    private static final ResourceLocation WHONEEDS_MODE_ICON_LIVINGDEX =
        ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/pokedex/tab_drops.png");
    // Native Größe einer Icon-Variante (Sprite-Sheet ist Icon-Größe x Icon-Größe*2: obere
    // Hälfte = normal, untere Hälfte = hovered/aktiv, nach Cobblemons ScaledButton-Konvention).
    private static final int WHONEEDS_MODE_ICON_SRC_SIZE = 16;
    // JUSTIERSCHRAUBE: eigenes Duplikat-Slot-Grid links (wo bei Cobblemon die Scroll-Liste
    // sitzt) - ein pokedex_slot.png je abgebbarem Pokemon, 5 pro Reihe wie in Cobblemons
    // eigener EntriesScrollingWidget (25px Slot + 2px Abstand = 27px Raster).
    private static final int WHONEEDS_SLOT_X = 26;
    private static final int WHONEEDS_SLOT_Y = 44;
    private static final int WHONEEDS_SLOT_COLUMNS = 5;
    private static final int WHONEEDS_SLOT_SPACING = 27;
    // JUSTIERSCHRAUBE: Grid war bisher unbegrenzt scrollbar/lief nach unten aus dem GUI heraus -
    // jetzt per Scissor+Scrollbar begrenzt wie beim Type-Tab-Ergebnisbereich. Scrollbar sitzt
    // knapp links von der Spielerliste (WHONEEDS_PLAYERS_X), da das Grid nur die linke Hälfte nutzt.
    private static final int WHONEEDS_GRID_BOTTOM_MARGIN = 12;
    private static final int WHONEEDS_GRID_SCROLLBAR_GAP = 8;
    private static final int WHONEEDS_GRID_SCROLLBAR_WIDTH = 3;
    // JUSTIERSCHRAUBE: rechte Spielerliste (Kopf + Name + Status), wo bei Cobblemon das
    // Info-Panel des ausgewählten Pokémon sitzt.
    private static final int WHONEEDS_PLAYERS_X = 180;
    private static final int WHONEEDS_PLAYERS_Y = 28;
    private static final int WHONEEDS_HEAD_SIZE = 16;
    private static final int WHONEEDS_ROW_H = 20;
    private static final int WHONEEDS_BADGE_SIZE = 4;
    // JUSTIERSCHRAUBE: Offset des Online/Offline-Badges relativ zur Standardposition
    // (untere rechte Ecke des Kopfes). Positiv = weiter nach rechts/unten verschieben.
    private static final int WHONEEDS_BADGE_OFFSET_X = 1;
    private static final int WHONEEDS_BADGE_OFFSET_Y = 1;
    private static final ResourceLocation ICON_ONLINE =
        ResourceLocation.fromNamespaceAndPath("cobblecompanion", "textures/gui/icons/icon_online.png");
    private static final ResourceLocation ICON_OFFLINE =
        ResourceLocation.fromNamespaceAndPath("cobblecompanion", "textures/gui/icons/icon_offline.png");

    // Dynamische Texturen basierend auf Pokedex-Farbe
    private ResourceLocation pokedexBase;
    private ResourceLocation pokedexScreen;
    private ResourceLocation buttonsLeft;
    private ResourceLocation buttonsRight;

    // Cobblemon's Original-Icon (14x28px: oben "Seen"-Lupe v=0, unten "Caught"-Pokeball v=14).
    // Wir nutzen überall nur den Caught/Pokeball-Ausschnitt und legen bei "Living" unser
    // eigenes Blatt-Icon (living_blatt.png, 16x16) darüber.
    private static final ResourceLocation CAUGHT_SEEN_ICON =
        ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/pokedex/caught_seen_icon.png");
    private static final ResourceLocation LIVING_LEAF_ICON =
        ResourceLocation.fromNamespaceAndPath("cobblecompanion", "textures/gui/icons/living_blatt.png");
    private static final int LEAF_SHEET_SIZE = 16; // Quellgröße von living_blatt.png

    // ===== JUSTIERSCHRAUBEN: Counter oben rechts im Living-Dex-Tab =====
    // Bereich, der Cobblemon's Original-Counter übermalt. Falls der noch durchscheint:
    // COUNTER_PATCH_V verkleinern und/oder COUNTER_PATCH_H vergrößern. Falls der Patch
    // links/rechts einen sichtbaren Schleier über den Rahmen legt: COUNTER_PATCH_U (Start)
    // bzw. COUNTER_PATCH_W (Breite) verkleinern.
    private static final int COUNTER_PATCH_U = 250;
    private static final int COUNTER_PATCH_V = 13;
    private static final int COUNTER_PATCH_W = 70;
    private static final int COUNTER_PATCH_H = 15;
    // Position + Größe der beiden Pokeball-Icons (links=Caught, rechts=Living).
    private static final int COUNTER_CAUGHT_ICON_X = 252;
    private static final int COUNTER_LIVING_ICON_X = 290;
    private static final int COUNTER_ICON_Y = 15;
    private static final int COUNTER_ICON_SIZE = 7;
    // Blatt-Overlay auf dem rechten (Living) Icon: Offset relativ zum Icon + eigene Größe.
    private static final int COUNTER_LEAF_OFFSET_X = 2;
    private static final int COUNTER_LEAF_OFFSET_Y = 1;
    private static final int COUNTER_LEAF_SIZE = 7;
    // Position, Skalierung, Farbe der Zahlen. Y ist pro Zahl einzeln einstellbar,
    // falls Caught/Living mal nicht mehr auf einer Höhe sitzen sollen.
    private static final int COUNTER_CAUGHT_TEXT_X = 262;
    private static final int COUNTER_CAUGHT_TEXT_Y = 14;
    private static final int COUNTER_LIVING_TEXT_X = 300;
    private static final int COUNTER_LIVING_TEXT_Y = 14;
    private static final float COUNTER_TEXT_SCALE = 1f;
    private static final int COUNTER_TEXT_COLOR = 0xFFFFFF;
    private static final boolean COUNTER_TEXT_BOLD = true;
    // Cobblemon nutzt für seine eigenen Zahlen (Seen/Caught) tatsächlich keine eigene
    // Schriftart, sondern Minecrafts eingebautes "uniform" (verifiziert per Bytecode:
    // CobblemonResources.DEFAULT_LARGE = ResourceLocation.parse("uniform")). Blockiger/
    // fester als die Standardschrift, oft besser lesbar bei kleiner Skalierung als Bold.
    private static final boolean COUNTER_TEXT_USE_UNIFORM_FONT = true;
    private static final ResourceLocation COUNTER_TEXT_FONT =
        ResourceLocation.fromNamespaceAndPath("minecraft", "uniform");
    // JUSTIERSCHRAUBE: Skalierung für mehrzeilige Fließtexte (ToDo/Type/Who-Needs-Reports),
    // nutzt dieselbe uniform/Bold-Schrift wie der Counter (drawScaledBoldText).
    private static final float BODY_TEXT_SCALE = 1f;
    // JUSTIERSCHRAUBEN für die übrigen GUI-Texte, alle über drawScaledBoldText (uniform/Bold).
    private static final float TAB_TITLE_TEXT_SCALE = 1f;      // Tab-Name oben neben Icon
    private static final float DEFAULT_TEXT_SCALE = 1f;        // Home-Tab, "Coming Soon", Fehlermeldungen
    private static final float TYPE_GRID_TEXT_SCALE = 1f;      // Typname neben Icon im Type-Tab-Raster

    // ===== JUSTIERSCHRAUBEN: Blatt-Overlay in der Pokémon-Liste (links im Living-Dex-Tab) =====
    // Position von Cobblemon's eigenem Caught-Icon relativ zur Slot-Ecke (startPosX/startPosY).
    private static final float LIST_LEAF_OFFSET_X = 19.7f;
    private static final float LIST_LEAF_OFFSET_Y = 2.5f;
    private static final int LIST_LEAF_SIZE = 6;
    // Cobblemon hebt sein Caught-Icon in der Liste per Z-Translate über das 3D-Modell.
    // Falls unser Blatt dort trotz disableDepthTest() noch dahinter liegt: diesen Wert
    // erhöhen, bis er sicher über Cobblemon's Z-Ebene liegt.
    private static final float LIST_LEAF_Z_OFFSET = 200f;

    // ===== JUSTIERSCHRAUBEN: Blatt-Overlay im Info-Panel (rechts, ausgewähltes Pokémon) =====
    // Position von Cobblemon's eigenem Caught-Icon relativ zu pX/pY des Info-Panels.
    private static final int INFO_LEAF_OFFSET_X = 131;
    private static final int INFO_LEAF_OFFSET_Y = 3;
    private static final int INFO_LEAF_SIZE = 8;

    // ===== Settings-Tab =====
    // Wiederverwendete Cobblemon-Button-Grafik (normal/hover übereinander gestapelt),
    // schon als TODO_EVOLVE_BUTTON weiter oben deklariert - hier nur die Maße nochmal
    // benannt, damit die Settings-Konstanten für sich lesbar bleiben.
    private static final int SETTINGS_BTN_NATIVE_W = TODO_EVOLVE_BTN_NATIVE_W;
    private static final int SETTINGS_BTN_NATIVE_H = TODO_EVOLVE_BTN_NATIVE_H;

    // Reihenfolge der Sub-Tabs links. "All Options" zuerst, danach je eine Kategorie -
    // wird sowohl für die Navigation als auch als Filter für buildSettingsRows() genutzt.
    private static final int SETTINGS_TAB_ALL = 0;
    private static final int SETTINGS_TAB_POKEDEX = 1;
    private static final int SETTINGS_TAB_TODO = 2;
    private static final int SETTINGS_TAB_WHONEEDS = 3;
    private static final int SETTINGS_TAB_FRIENDS = 4;
    private static final int SETTINGS_TAB_PC = 5;
    private static final int SETTINGS_TAB_SERVER = 6; // ans Ende verschoben (Nutzer-Vorgabe)
    // Nutzer-Vorgabe: eigene Kategorie NUR für AdminOp (nicht bloß "sichtbar aber gesperrt" wie
    // Server, siehe SETTINGS_TAB_SERVER) - Sichtbarkeit über isGamemodesTabVisible() gesteuert,
    // taucht deshalb auch NICHT unter "All Options" auf (siehe buildSettingsRows()).
    private static final int SETTINGS_TAB_GAMEMODES = 7;
    // Sprachschlüssel je Sub-Tab (Index = SETTINGS_TAB_*). Übersetzt beim Zeichnen via tr(...).
    private static final String[] SETTINGS_SUBTAB_NAMES = {
        "cobblecompanion.settings.tab.all", "cobblecompanion.settings.tab.pokedex", "cobblecompanion.settings.tab.todo",
        "cobblecompanion.settings.tab.whoneeds", "cobblecompanion.settings.tab.friends",
        "cobblecompanion.settings.tab.pc", "cobblecompanion.settings.tab.server", "cobblecompanion.settings.tab.gamemodes"};

    // JUSTIERSCHRAUBE: linke Spalte - Sub-Tab-Buttons untereinander statt nebeneinander,
    // die optische Trennung übernimmt die Pokedex-Hintergrundgrafik selbst (kein Overlay nötig).
    private static final int SETTINGS_NAV_X = 26;
    private static final int SETTINGS_NAV_Y = 28;
    private static final int SETTINGS_NAV_W = 108;
    private static final int SETTINGS_NAV_H = 15;
    private static final int SETTINGS_NAV_GAP = 3;
    private static final float SETTINGS_SUBTAB_TEXT_SCALE = 1.0f;
    private static final boolean SETTINGS_SUBTAB_TEXT_BOLD = true;
    private static final boolean SETTINGS_SUBTAB_TEXT_UNIFORM_FONT = true;
    private static final int SETTINGS_SUBTAB_ACTIVE_COLOR = 0xFFFFAA00;
    private static final int SETTINGS_SUBTAB_INACTIVE_COLOR = 0xFFFFFFFF;
    private static final int SETTINGS_SUBTAB_ACTIVE_FILL = 0x40FFFFFF;

    // JUSTIERSCHRAUBE: rechte Spalte - Überschriften + Options-Zeilen, scrollbar sobald
    // der Inhalt (v.a. bei "All Options") über SETTINGS_CONTENT_VISIBLE_HEIGHT hinausgeht.
    // X-Historie: 145 (zu weit links) -> 209 (genauso weit zu weit rechts) -> jetzt 180,
    // der Mittelwert - deckt sich mit WHONEEDS_PLAYERS_X/TYPE_RESULT_X, also vermutlich die
    // tatsächliche Bezel-Trennlinie der Pokedex-Grafik. Rechte Kante bewusst unverändert
    // gelassen (nur die linke Kante war diesmal das Problem), Breite passt sich also an.
    private static final int SETTINGS_CONTENT_X = 180;
    private static final int SETTINGS_CONTENT_Y = 28;
    private static final int SETTINGS_CONTENT_WIDTH = 135;
    private static final int SETTINGS_CONTENT_VISIBLE_HEIGHT = 163;
    private static final int SETTINGS_SCROLLBAR_GAP = 4;
    private static final int SETTINGS_SCROLLBAR_WIDTH = 3;

    // Überschrift pro Kategorie (bei "All Options" trennt sie die Bereiche, sonst ist sie
    // schlicht der Name des aktiven Sub-Tabs).
    private static final int SETTINGS_HEADING_H = 14;
    private static final float SETTINGS_HEADING_TEXT_SCALE = 1.0f;
    private static final boolean SETTINGS_HEADING_BOLD = true;
    private static final boolean SETTINGS_HEADING_UNIFORM_FONT = true;
    private static final int SETTINGS_HEADING_COLOR = 0xFFAA00;

    // Options-Zeile: Label links (bricht bei Bedarf um statt abzuschneiden - v.a. deutsche
    // Texte sind oft länger), Toggle-/Cycle-Button rechtsbündig am Rand von
    // SETTINGS_CONTENT_WIDTH. Alle Textgrößen im Settings-Tab mindestens 1.0 - bei kleinerer
    // Skalierung war es nicht klar lesbar. Überschriften bold (s.o.), einzelne Optionen
    // bewusst nicht bold. Zeilenhöhe wächst automatisch mit der Anzahl umgebrochener Zeilen.
    private static final int SETTINGS_OPTION_ROW_H = 18;
    private static final int SETTINGS_OPTION_LINE_H = 10; // Zeilenabstand bei mehrzeiligem Label
    private static final int SETTINGS_OPTION_ROW_GAP = 5; // Puffer nach der letzten Zeile bis zur nächsten Reihe
    private static final float SETTINGS_OPTION_LABEL_SCALE = 1.0f;
    private static final boolean SETTINGS_OPTION_LABEL_BOLD = false;
    private static final boolean SETTINGS_OPTION_LABEL_UNIFORM_FONT = true;
    private static final int SETTINGS_OPTION_LABEL_COLOR = 0xFFFFFFFF;
    private static final int SETTINGS_OPTION_LABEL_Y_OFFSET = 3; // vertikal zentriert in der Zeile

    // Button-Breiten bei Scale 1.0 neu bemessen, damit "Living Dex"/"Yes"/"No" noch reinpassen.
    // Cycle-Button um ca. 1/3 eingekürzt (88 -> 59), damit das Label links davon ("Show
    // evolutions") wieder mehr Platz bekommt statt fast vollständig wegtruncatet zu werden.
    private static final int SETTINGS_TOGGLE_W = 32;
    private static final int SETTINGS_TOGGLE_H = 13;
    private static final int SETTINGS_CYCLE_W = 59;
    private static final float SETTINGS_CONTROL_TEXT_SCALE = 1.0f;
    private static final boolean SETTINGS_CONTROL_TEXT_BOLD = true;
    private static final boolean SETTINGS_CONTROL_TEXT_UNIFORM_FONT = true;
    private static final int SETTINGS_TOGGLE_ON_COLOR = 0xFF55FF55;
    private static final int SETTINGS_TOGGLE_OFF_COLOR = 0xFFFF5555;
    private static final int SETTINGS_CYCLE_TEXT_COLOR = 0xFFFFFFFF;
    // Living Dex+ wird auf Nutzer-Wunsch IMMER in Gold/Gelb dargestellt (Label der 7 Kategorie-
    // Zeilen sowie der Modus-Text selbst, wenn Living Dex+ ausgewählt ist).
    private static final int SETTINGS_LDP_GOLD_COLOR = 0xFFFFD700;

    // Setting-Keys für buildSettingsRows()/readToggleValue()/applyToggle() - Umschalten per
    // switch statt Lambda/Methodenreferenz (siehe Kommentar an drawScaledBoldText zu Hot-Swap).
    // Vereinheitlichte "Dex Wahl" (eigene Kategorie "Pokédex") - ersetzt sowohl das frühere
    // ToDo-"Modus" als auch das PC-"Sortierung für": EIN 3-Way-Schalter (Pokédex/Living Dex/
    // Living Dex+) treibt jetzt ToDo/WhoNeeds-Filterung UND den PC-Sortiermodus gemeinsam an.
    private static final int SKEY_DEX_WAHL = 0;
    private static final int SKEY_TODO_SHOW_BUTTON = 1;
    private static final int SKEY_TODO_CONFIRM = 2;
    private static final int SKEY_TODO_SEND_OUT = 3;
    private static final int SKEY_TODO_CLOSE = 4;
    private static final int SKEY_WHONEEDS_ONLY_FRIENDS = 5;
    private static final int SKEY_WHONEEDS_FRIENDS_FIRST = 6;
    private static final int SKEY_FRIENDS_AUTO_ACCEPT = 7;
    private static final int SKEY_FRIENDS_SHOW_OFFLINE = 8;
    private static final int SKEY_FRIENDS_ALLOW_TELEPORT_TO_ME = 9;
    private static final int SKEY_SERVER_FORBID_GIFTING = 10;
    private static final int SKEY_SERVER_ALLOW_TELEPORT_TO_FRIENDS = 11;
    private static final int SKEY_PC_SORT_HELPER = 12;
    // 13 frei (ehemals SKEY_PC_SORT_MODE, jetzt in SKEY_DEX_WAHL vereinheitlicht)
    private static final int SKEY_PC_SORT_START_BOX = 14;
    private static final int SKEY_PC_AUTONAME_BUTTON = 15;
    private static final int SKEY_PC_SLOT_CHECK = 17;
    // Living Dex+ Kategorien (nur sichtbar, wenn SKEY_DEX_WAHL == LIVING_DEX_PLUS) - Reihenfolge
    // hier ist rein deklaratorisch, die tatsächliche Box-Reihenfolge kommt aus
    // ClientSettingsHelper.getLivingDexPlusCategoryOrder() (Drag&Drop, siehe Phase 5). Key = 18 +
    // Kategorie-/Regions-ID (siehe ldpKeyForCategoryId()) - funktioniert sowohl für die echten
    // Basis-Kategorie-IDs (0-7, Keys 18-25) als auch für die synthetischen Pro-Region-IDs (100-103/
    // 200-203, siehe LivingDexPlusRegistry.regionSyntheticId(), Umbau "Regionalformen-
    // Unterkategorien von den Oberkategorien trennen") - Keys 118-121/218-221, kollidiert mit
    // nichts. Kategorie 3/4 (Regionalformen/-Shiny) selbst haben KEINE eigene Toggle-Zeile mehr -
    // nur noch ihre einzelnen Regionen (siehe buildSettingsRows()).
    private static final int SKEY_LDP_CAT_BASE_POKEDEX = 18;
    private static final int SKEY_LDP_CAT_BASE_LIVING_DEX = 19;
    private static final int SKEY_LDP_CAT_BASE_SHINY = 20;
    private static final int SKEY_LDP_CAT_COSMETIC_FORMS = 23;
    private static final int SKEY_LDP_CAT_COSMETIC_FORMS_SHINY = 24;
    // BUGFIX: SHINY_POKEDEX (Kategorie-ID 7, per ldpKeyForCategoryId() -> 18+7=25) kollidierte
    // bisher mit SKEY_PC_BOX_COUNT (auch 25) - Klicks auf "Shiny Pokédex" landeten dadurch in der
    // Box-Anzahl-Cycle-Logik statt im Kategorie-Toggle (Root Cause des "lässt sich nicht ohne
    // Shiny Living Dex aktivieren"-Bugreports). Eigener Slot + isLdpCategoryKey()-Grenze erweitert.
    private static final int SKEY_LDP_CAT_SHINY_POKEDEX = 25;
    // Server-Admin: Cobblemons konfigurierte PC-Box-Anzahl - OP-only, wirkt erst nach
    // Server-Neustart/Neu-Login (siehe PcBoxCountChangePacket, bewusst kein Live-Resize).
    private static final int SKEY_PC_BOX_COUNT = 26;
    // Server-Admin: CobbleDollars-Einnahmequellen + Online-Belohnung + Creative-Preis (letzterer
    // war bisher im Wallet-Tab, siehe Verschiebe-Anfrage). Werte kommen live vom Server
    // (ServerRulesSyncPacket bzw. ClientCreativeTimeHelper), keine eigene Client-Persistenz.
    private static final int SKEY_SERVER_EARN_FROM_NPC = 27;
    private static final int SKEY_SERVER_EARN_FROM_WILD_POKEMON = 28;
    private static final int SKEY_SERVER_INCOME_MULTIPLIER = 29;
    private static final int SKEY_SERVER_ONLINE_REWARD_ENABLED = 30;
    private static final int SKEY_SERVER_ONLINE_REWARD_INTERVAL = 31;
    private static final int SKEY_SERVER_ONLINE_REWARD_AMOUNT = 32;
    private static final int SKEY_SERVER_CREATIVE_PRICE_PER_MINUTE = 33;
    // Nutzer-Vorgabe (Gamemodes-Kategorie, AdminOp-only): Kauf-Ein/Aus-Schalter, unabhängig vom
    // Preis (behebt den Preis=0-Nebeneffekt-Bug, siehe CreativeTimePurchasePacket-Kommentar).
    private static final int SKEY_GAMEMODES_CREATIVE_PURCHASE_ENABLED = 34;
    // Nutzer-Vorgabe: echter Listeneditor statt nur eines Hinweistexts (siehe buildSettingsRows-
    // Kommentar) - zwei Cycle-Buttons komponieren eine neue Regel (Dimension aus der vom Server
    // gesendeten Liste, Gamemode aus den 4 Vanilla-Modi), der Add-Button sendet sie. Bestehende
    // Regeln sind als BUTTON-Zeilen mit dynamischem Key (GAMEMODES_REMOVE_KEY_BASE + Listenindex)
    // klickbar - Index bezieht sich auf ClientDimensionGamemodeHelper.getSortedRules(), das bei
    // Render UND Klick im selben Tick identisch sortiert ist (stabil, da Datenänderungen nur per
    // Server-Antwort dazwischenkommen, nie mitten in einem Klick-Tick).
    private static final int SKEY_GAMEMODES_NEW_DIMENSION = 35;
    private static final int SKEY_GAMEMODES_NEW_MODE = 36;
    private static final int SKEY_GAMEMODES_ADD_BUTTON = 37;
    private static final int SKEY_GAMEMODES_INVENTORY_SYNC_ENABLED = 38;
    private static final int SKEY_HIDE_OBSERVER_MESSAGES = 39;
    private static final int GAMEMODES_REMOVE_KEY_BASE = 1000;
    private static final net.minecraft.world.level.GameType[] GAMEMODES_CYCLE_TYPES = {
        net.minecraft.world.level.GameType.SURVIVAL, net.minecraft.world.level.GameType.CREATIVE,
        net.minecraft.world.level.GameType.ADVENTURE, net.minecraft.world.level.GameType.SPECTATOR
    };

    private static final int SETTINGS_ROW_HEADING = 0;
    private static final int SETTINGS_ROW_TOGGLE = 1;
    private static final int SETTINGS_ROW_CYCLE = 2;
    private static final int SETTINGS_ROW_BUTTON = 3;

    private double settingsScrollAmount = 0;
    private boolean settingsScrollbarDragging = false;

    // Living Dex+ Kategorie-Drag&Drop (Reihenfolge = Box-Reihenfolge, siehe
    // ClientSettingsHelper.reorderLivingDexPlusCategories) - komplettes Neuland in dieser
    // Codebase, kein bestehendes Vorbild. ldpDragOrder ist eine Arbeitskopie der aktivierten
    // Kategorie-Reihenfolge, die während des Ziehens live verschoben wird; erst beim Loslassen
    // (mouseReleased) wird sie tatsächlich committet/gespeichert.
    private static final int LDP_DRAG_THRESHOLD_PX = 4;
    private int ldpDragCategoryId = -1; // -1 = kein Drag aktiv
    private double ldpDragStartMouseY = 0;
    private double ldpDragCurrentMouseY = 0;
    private boolean ldpDragArmed = false;
    private java.util.List<Integer> ldpDragOrder = null;

    // Sicherheitsabfrage vor einer Entwicklung (Setting "Confirm before evolving"): solange
    // gesetzt, liegt ein modales Ja/Nein-Overlay über dem ToDo-Tab.
    private ClientTodoHelper.TodoEntry pendingEvolveEntry = null;

    // Sicherheitsabfrage vor dem Entfernen eines Freundes: solange pendingRemoveFriendUuid
    // gesetzt ist, liegt ein modales Ja/Nein-Overlay über dem Friends-Tab.
    private UUID pendingRemoveFriendUuid = null;
    private String pendingRemoveFriendName = "";

    // Party-Auswahl-Overlay beim Pokemon-Verschenken: solange gesetzt, liegt das Overlay über
    // dem Friends-Tab und zeigt die eigene Party zur Auswahl (siehe renderGiftPartyOverlay()).
    private UUID giftOverlayTargetUuid = null;
    private String giftOverlayTargetName = "";

    // Abhol-Warteschlange für verwaiste Gamemode-Inventare (Gamemode-Inventar-Trennung wurde
    // deaktiviert bzw. ein Admin hat zurückgesetzt, siehe GamemodeInventorySyncManager) - Klick auf
    // den Home-Tab-Hinweis öffnet dieses modale Overlay.
    private boolean reclaimOverlayOpen = false;

    private static class SettingsRow {
        final int type;
        final String label;
        final int key;
        final int overrideColor; // -1 = Standardfarbe des Row-Typs nutzen
        SettingsRow(int type, String label, int key) {
            this(type, label, key, -1);
        }
        SettingsRow(int type, String label, int key, int overrideColor) {
            this.type = type;
            this.label = label;
            this.key = key;
            this.overrideColor = overrideColor;
        }
    }

    // ===== Such-Tab =====
    private CobblemonSearchBox searchTabSearchBox;
    private double searchTabScrollAmount = 0;
    private boolean searchTabScrollbarDragging = false;

    // ===== Team-Builder-Tab =====
    // -1 = noch kein Modus gewählt (Startzustand), sonst 0=Allgemein, 1=Type, 2=Team.
    private int teamBuilderMode = -1;
    private String teamBuilderSelectedType = "";
    private final String[] tbOpponentName = {"", "", "", "", "", ""};
    private final String[] tbOpponentLevel = {"", "", "", "", "", ""};
    // Fokussiertes Eingabefeld, kodiert als row*2+col (col 0=Name, 1=Level), -1 = keins fokussiert.
    private int tbFocusedField = -1;

    // Wallet-Tab: komplett ausgelagert nach CobbleCompanion: CobbleDollars, siehe
    // com.cobblecompanion.cobbledollars.client.WalletTabExtension - registriert sich selbst über
    // com.cobblecompanion.api.CompanionExtensions.registerTab(TAB_WALLET, ...).

    // ===== Professor-Tab (Op/AdminOp) =====
    private CobblemonSearchBox professorSearchBox;

    // ===== Friends-Tab =====
    private CobblemonSearchBox friendsSearchBox;
    // JUSTIERSCHRAUBE: rechtes Panel (Suchfeld + Add-Button + Freundesliste), an derselben
    // X-Position wie das Spieler-Panel im Who-Needs-Tab (WHONEEDS_PLAYERS_X). Add-Button sitzt
    // UNTER der Suchleiste, rechtsbündig zu ihr. Breite bewusst nicht exakt die Hälfte, sondern
    // gerade so viel wie "Hinzufügen" bei Textgröße 1.0 (Mindestgröße laut Vorgabe) braucht -
    // schmaler als die volle Suchleisten-Breite, aber ohne abgeschnittenen/unleserlichen Text.
    private static final int FRIENDS_PANEL_X = 180;
    private static final int FRIENDS_PANEL_Y = 28;
    private static final int FRIENDS_SEARCH_W = SEARCH_OVERLAY_W;
    private static final int FRIENDS_SEARCH_H = 11;
    private static final int FRIENDS_ADD_BTN_GAP = 3;
    private static final int FRIENDS_ADD_BTN_Y = FRIENDS_PANEL_Y + FRIENDS_SEARCH_H + FRIENDS_ADD_BTN_GAP;
    private static final int FRIENDS_ADD_BTN_W = 90;
    private static final int FRIENDS_ADD_BTN_H = 13;
    private static final float FRIENDS_ADD_BTN_TEXT_SCALE = 1.0f;

    private static final int FRIENDS_LIST_Y = FRIENDS_ADD_BTN_Y + FRIENDS_ADD_BTN_H + 3;
    private static final int FRIENDS_ROW_H = 20;
    private static final int FRIENDS_HEAD_SIZE = 16;
    private static final int FRIENDS_BADGE_SIZE = 4;
    private static final int FRIENDS_BADGE_OFFSET_X = 1;
    private static final int FRIENDS_BADGE_OFFSET_Y = 1;
    private static final int FRIENDS_REMOVE_SIZE = 8;
    private static final int FRIENDS_REMOVE_OFFSET_X = 2; // Abstand vom rechten Panel-Rand
    private static final int FRIENDS_NAME_OFFSET_X = 4;

    // JUSTIERSCHRAUBE: Anfragen-Sektion (über der Freundesliste, nur wenn offene Anfragen da
    // sind). Pro Anfrage: Kopf + Name + Annehmen(✓)/Ablehnen(✗)-Glyphen.
    private static final int FRIENDS_REQ_HEADING_H = 10;
    private static final int FRIENDS_REQ_ACCEPT_OFFSET_X = 18; // Abstand des ✓ vom rechten Panel-Rand
    private static final int FRIENDS_REQ_DECLINE_OFFSET_X = 6; // Abstand des ✗ vom rechten Panel-Rand
    private static final int FRIENDS_REQ_BTN_SIZE = 8;

    // JUSTIERSCHRAUBE: linkes Panel mit Statistiken des ausgewählten Freundes.
    private static final int FRIENDS_DETAIL_X = 26;
    private static final int FRIENDS_DETAIL_Y = 28;
    private static final int FRIENDS_DETAIL_HEAD_SIZE = 32;
    private static final int FRIENDS_DETAIL_NAME_OFFSET_Y = 38;
    private static final int FRIENDS_DETAIL_STAT_START_OFFSET_Y = 52;
    private static final int FRIENDS_DETAIL_STAT_ROW_H = 12;

    // JUSTIERSCHRAUBE: Teleport-/Geschenk-Button unter den Statistik-Zeilen des ausgewählten
    // Freundes (Schritt 5/6: Teleport zu Freund + Pokemon verschenken).
    private static final int FRIENDS_ACTION_BTN_GAP_Y = 6; // Abstand von der letzten Stat-Zeile
    private static final int FRIENDS_ACTION_BTN_W = 90;
    private static final int FRIENDS_ACTION_BTN_H = 13;
    private static final int FRIENDS_ACTION_BTN_SPACING_Y = 3;
    private static final float FRIENDS_ACTION_BTN_TEXT_SCALE = 1.0f;

    // JUSTIERSCHRAUBE: Party-Auswahl-Overlay beim Verschenken (6 Slots wie Party-Größe).
    private static final int GIFT_OVERLAY_BOX_W = 200;
    private static final int GIFT_OVERLAY_SLOT_COLUMNS = 3;
    private static final int GIFT_OVERLAY_SLOT_SPACING = 30;
    private static final int GIFT_OVERLAY_SLOT_START_Y = 30;
    private static final int GIFT_OVERLAY_BOX_H = 110;

    // JUSTIERSCHRAUBE: Home-Tab, linke Hälfte - Dashboard (Dex-Fortschrittsbalken, ToDo-
    // Kurzübersicht, offene Freundschaftsanfragen mit Link zum Friends-Tab).
    private static final int HOME_DASH_X = 26;
    private static final int HOME_DASH_Y = 56;
    private static final int HOME_DASH_BAR_W = 140;
    private static final int HOME_DASH_BAR_H = 8;
    // War vorher ein hart-schwarzer Fill (0x552A2A2A) - zu starker Kontrast (Nutzer-Feedback).
    // Jetzt an den Look unserer Suchleisten angelehnt (dunkles, leicht bläuliches Overlay +
    // dünner Teal-Akzentrand oben/unten, wie Cobblemons SearchWidget-Overlay), statt der reinen
    // Textur (die ist für ein großes Suchfeld-Panel gedacht, passt nicht in eine 8px-Leiste).
    private static final int HOME_DASH_BAR_BG_COLOR = 0xB0202832;
    private static final int HOME_DASH_BAR_ACCENT_COLOR = 0xFF3E9CB5;
    private static final int HOME_DASH_BAR_FILL_COLOR = 0xFF5FA05F; // etwas matteres/entsättigteres Grün
    private static final int HOME_DASH_BAR_BORDER_COLOR = 0xFF1A1A1A;
    private static final int HOME_DASH_BAR_PERCENT_COLOR = 0xFFFFFFFF;
    private static final int HOME_DASH_BAR_PERCENT_FILLED_COLOR = 0xFFAAAAAA;
    private static final int HOME_DASH_LABEL_OFFSET_Y = -9; // Label über der Leiste
    private static final int HOME_DASH_BAR_SPACING = 24; // Abstand zwischen den 3 Leisten
    private static final int HOME_DASH_TODO_Y_OFFSET = 12; // Abstand ToDo-Zeilen zur letzten Leiste
    private static final int HOME_DASH_TODO_LINE_H = 12;
    private static final int HOME_DASH_LINK_COLOR = 0x66CCFF;
    private static final int HOME_DASH_LINK_HOVER_COLOR = 0xFFFF55;
    private static final int HOME_DASH_FRIENDS_Y_OFFSET = 16; // Abstand Freund-Zeile zu den ToDo-Zeilen

    // JUSTIERSCHRAUBE: Home-Tab, rechte Hälfte - offene Pokemon-Geschenke zum Annehmen.
    // Gleiche X-Konvention wie TYPE_RESULT_X/WHONEEDS_PLAYERS_X (180 = Start der rechten Hälfte).
    private static final int HOME_GIFT_X = 180;
    private static final int HOME_GIFT_Y = 28;
    private static final int HOME_GIFT_BOTTOM_MARGIN = 12;
    private static final int HOME_GIFT_HEAD_SIZE = 16;
    private static final int HOME_GIFT_TEXT_OFFSET_X = 20; // Abstand Text vom Kopf
    private static final int HOME_GIFT_TEXT_LINE_H = 8; // Zeilenhöhe (Scale 1.0, Standardschrift)
    private static final float HOME_GIFT_TEXT_SCALE = 1.0f;
    private static final int HOME_GIFT_SLOT_OFFSET_Y = 19; // Abstand Slot vom Zeilenanfang
    private static final int HOME_GIFT_NAME_OFFSET_X = 28; // Abstand Name vom Slot (SLOT_SIZE+etwas Luft)
    private static final int HOME_GIFT_INFO_OFFSET_X = 28; // Abstand Typ-Icon/Wesen/Fähigkeit vom Slot
    private static final int HOME_GIFT_INFO_OFFSET_Y = 11; // Abstand Info-Zeile vom Slot-Anfang (unter dem Namen)
    private static final int HOME_GIFT_INFO_ICON_SIZE = 9;
    private static final int HOME_GIFT_INFO_TEXT_OFFSET_X = 11; // Abstand Wesen/Fähigkeit-Text vom Typ-Icon
    private static final int HOME_GIFT_BTN_W = 80;
    private static final int HOME_GIFT_BTN_H = 13;
    private static final int HOME_GIFT_BTN_OFFSET_Y = 40; // Abstand Annehmen-Button vom Slot-Anfang
    private static final int HOME_GIFT_DECLINE_BTN_W = 60;
    private static final int HOME_GIFT_BTN_GAP = 4; // Abstand zwischen Annehmen- und Ablehnen-Button
    private static final int HOME_GIFT_ENTRY_H = 74; // Gesamthöhe einer Geschenk-Zeile inkl. Abstand zur nächsten
    private static final int HOME_GIFT_SCROLLBAR_WIDTH = 3;
    private static final int HOME_GIFT_SCROLLBAR_GAP = 8;

    // JUSTIERSCHRAUBE: Such-Tab, linke Hälfte - Suchfeld + scrollbare Liste "wo finde ich das".
    private static final int SEARCH_TAB_X = 26;
    private static final int SEARCH_TAB_SEARCH_Y = 28;
    private static final int SEARCH_TAB_SEARCH_W = SEARCH_OVERLAY_W;
    private static final int SEARCH_TAB_SEARCH_H = 11;
    private static final int SEARCH_TAB_LIST_Y = SEARCH_TAB_SEARCH_Y + SEARCH_TAB_SEARCH_H + 4;
    private static final int SEARCH_TAB_BOTTOM_MARGIN = 32; // +20px (Nutzer-Wunsch: Scrollbar endet höher)
    private static final int SEARCH_TAB_ROW_H = 12;
    private static final int SEARCH_TAB_SCROLLBAR_WIDTH = 3;
    private static final int SEARCH_TAB_SCROLLBAR_GAP = 4;
    private static final int SEARCH_TAB_ROW_TAB_COLOR = 0xFFAA00;
    private static final int SEARCH_TAB_ROW_TERM_COLOR = 0xFFFFFF;
    private static final int SEARCH_TAB_ROW_HOVER_COLOR = 0xFFFF55;
    private static final int SEARCH_TAB_MAX_SUGGESTIONS = 40;

    // JUSTIERSCHRAUBE: Such-Tab, rechte Hälfte - zuletzt verwendete Suchbegriffe (kein Scrollen,
    // die Liste ist fest auf SEARCH_HISTORY_MAX begrenzt - älteste fallen beim Nachrücken raus).
    private static final int SEARCH_HISTORY_X = 180;
    private static final int SEARCH_HISTORY_Y = 28;
    private static final int SEARCH_HISTORY_ROW_H = 12;
    private static final int SEARCH_HISTORY_ROW_COLOR = 0xFFFFFF;
    private static final int SEARCH_HISTORY_ROW_HOVER_COLOR = 0xFFFF55;

    // JUSTIERSCHRAUBE: Team-Builder-Tab, linke Hälfte - 3 Modus-Buttons, darunter je nach Modus
    // eine Typ-Liste ("Type") oder ein 2x6-Eingabegitter für ein Gegner-Team ("Team").
    private static final int TEAMBUILDER_LEFT_X = 26;
    private static final int TEAMBUILDER_LEFT_Y = 30;
    private static final int TEAMBUILDER_BTN_W = 100;
    private static final int TEAMBUILDER_BTN_H = 16;
    private static final int TEAMBUILDER_BTN_GAP_Y = 6;
    private static final int TEAMBUILDER_SUB_Y_OFFSET = 12; // Abstand Modus-Inhalt zu den 3 Buttons
    private static final int TEAMBUILDER_TYPE_COLS = 3;
    private static final int TEAMBUILDER_TYPE_COL_W = 50; // wie TYPE_GRID_COL_WIDTH (Types-Tab-Raster)
    private static final int TEAMBUILDER_TYPE_ROW_H = 16; // wie TYPE_GRID_ROW_H
    private static final int TEAMBUILDER_TYPE_ICON_TEXT_GAP = 3;
    private static final int TEAMBUILDER_OPP_ROW_H = 15;
    private static final int TEAMBUILDER_OPP_NAME_W = 100;
    private static final int TEAMBUILDER_OPP_NAME_H = 13;
    private static final int TEAMBUILDER_OPP_LEVEL_W = 28;
    private static final int TEAMBUILDER_OPP_LEVEL_GAP = 2; // -2px (Nutzer-Wunsch)
    private static final int TEAMBUILDER_OPP_CLEAR_BTN_W = 12;
    private static final int TEAMBUILDER_OPP_CLEAR_GAP = 2; // -2px (Nutzer-Wunsch)
    private static final int TEAMBUILDER_OPP_FIELD_BG = 0x80000000;
    private static final int TEAMBUILDER_OPP_FIELD_BORDER = 0xFF555555;
    private static final int TEAMBUILDER_OPP_FIELD_BORDER_FOCUS = 0xFFFFAA00;
    private static final int TEAMBUILDER_OPP_TEXT_OFFSET_X = 3;
    private static final int TEAMBUILDER_OPP_TEXT_OFFSET_Y = 3;
    private static final int TEAMBUILDER_OPP_CONFIRM_BTN_W = 100;
    private static final int TEAMBUILDER_OPP_CONFIRM_BTN_H = 15;
    private static final int TEAMBUILDER_OPP_CONFIRM_GAP_Y = 8;
    private static final int TEAMBUILDER_OPP_NAME_MAX_LEN = 20;
    private static final int TEAMBUILDER_OPP_LEVEL_MAX_LEN = 3;
    // Hinweistext zwischen Modus-Buttons und erster Eingabezeile ("Team"-Modus).
    private static final int TEAMBUILDER_OPP_HINT_OFFSET_Y = -12;

    // JUSTIERSCHRAUBE: Team-Builder-Tab, rechte Hälfte - Ergebnis-Team als 3x2-Slot-Raster.
    private static final int TEAMBUILDER_RESULT_X = 180;
    // Justierschraube (Nutzer-Wunsch, Runde 4+5): Ergebnisliste 2px höher starten (30 -> 28),
    // dann nochmal 1px höher (28 -> 27).
    private static final int TEAMBUILDER_RESULT_Y = 27;
    // Vom 3x2-Slot-Raster auf eine vertikale Liste umgestellt: Gründe (siehe TeamBuilderHelper)
    // brauchen Textbreite, die in einem 3-spaltigen Raster nicht vorhanden wäre - der Abstand
    // zwischen aufeinanderfolgenden Einträgen richtet sich jetzt nach der Anzahl Grund-Zeilen
    // des jeweils VORHERIGEN Eintrags (Nutzer-Vorgabe).
    private static final int TEAMBUILDER_RESULT_ICON_SIZE = 25; // wie PokemonSlotRenderer.SLOT_SIZE
    private static final int TEAMBUILDER_RESULT_TEXT_GAP_X = 4;
    private static final int TEAMBUILDER_RESULT_NAME_OFFSET_Y = 2;
    private static final int TEAMBUILDER_RESULT_REASON_LINE_H = 8;
    private static final int TEAMBUILDER_RESULT_REASON_OFFSET_Y = 10; // Abstand Name -> erste Grund-Zeile
    private static final int TEAMBUILDER_RESULT_ROW_MIN_H = TEAMBUILDER_RESULT_ICON_SIZE + 3; // mind. Icon-Höhe + Puffer
    private static final int TEAMBUILDER_RESULT_ALT_HEADING_GAP_Y = 6;
    // Scrollbar + Ausklapp-Gründe (Nutzer-Wunsch, Runde 3) - Layout/Klick-Logik gleiches Prinzip
    // wie dexHelp (buildDexHelpRows()/dexHelpRowHeight()): variable Zeilenhöhe je nach Ausklapp-
    // Zustand, ein flacher Zeilen-Index dient als Klick-/Scroll-Referenz für render UND Klicks.
    // Justierschraube (Nutzer-Wunsch, Runde 5): Liste unten um 6px gekürzt (8 -> 14).
    private static final int TEAMBUILDER_RESULT_BOTTOM_MARGIN = 14;
    private static final int TEAMBUILDER_RESULT_W = GUI_WIDTH - TEAMBUILDER_RESULT_X - 26;
    private static final int TEAMBUILDER_SCROLLBAR_GAP = 4;
    private static final int TEAMBUILDER_SCROLLBAR_WIDTH = 3;
    private double teamBuilderResultScroll = 0;
    // Grund-Zeilen sind standardmäßig eingeklappt (Nutzer-Wunsch, wie ToDo-Tab) - Set aus
    // entryIndex (flache Position über Haupt- + Alternativ-Liste), wird bei neuem Ergebnis geleert.
    private final java.util.Set<Integer> teamBuilderExpandedEntries = new java.util.HashSet<>();
    private int teamBuilderExpandedResultVersion = -1;

    /** Eine Zeile der Ergebnisliste: entweder ein Pokemon-Eintrag oder die "Alternativen"-Überschrift. */
    private static final class TeamBuilderRow {
        final boolean heading;
        final ClientTeamBuilderHelper.Entry entry;
        final int entryIndex;
        TeamBuilderRow(boolean heading, ClientTeamBuilderHelper.Entry entry, int entryIndex) {
            this.heading = heading;
            this.entry = entry;
            this.entryIndex = entryIndex;
        }
    }

    private List<TeamBuilderRow> buildTeamBuilderRows() {
        List<TeamBuilderRow> rows = new java.util.ArrayList<>();
        int idx = 0;
        for (ClientTeamBuilderHelper.Entry e : ClientTeamBuilderHelper.getResult()) {
            rows.add(new TeamBuilderRow(false, e, idx++));
        }
        List<ClientTeamBuilderHelper.Entry> alternates = ClientTeamBuilderHelper.getAlternates();
        if (!alternates.isEmpty()) {
            rows.add(new TeamBuilderRow(true, null, -1));
            for (ClientTeamBuilderHelper.Entry e : alternates) {
                rows.add(new TeamBuilderRow(false, e, idx++));
            }
        }
        return rows;
    }

    private int teamBuilderRowHeight(TeamBuilderRow row) {
        if (row.heading) return TEAMBUILDER_RESULT_REASON_LINE_H + 2 + TEAMBUILDER_RESULT_ALT_HEADING_GAP_Y;
        boolean expanded = teamBuilderExpandedEntries.contains(row.entryIndex);
        int reasonCount = expanded ? row.entry.reasons.size() : 0;
        return Math.max(TEAMBUILDER_RESULT_ROW_MIN_H,
            (TEAMBUILDER_RESULT_REASON_OFFSET_Y - TEAMBUILDER_RESULT_NAME_OFFSET_Y) + reasonCount * TEAMBUILDER_RESULT_REASON_LINE_H + 3);
    }

    private int teamBuilderResultVisibleHeight() {
        return (guiTop + GUI_HEIGHT - TEAMBUILDER_RESULT_BOTTOM_MARGIN) - (guiTop + TEAMBUILDER_RESULT_Y);
    }

    private int teamBuilderResultMaxScroll(List<TeamBuilderRow> rows) {
        int total = 0;
        for (TeamBuilderRow row : rows) total += teamBuilderRowHeight(row);
        return Math.max(0, total - teamBuilderResultVisibleHeight());
    }

    // JUSTIERSCHRAUBE: Professor-Tab (Op/AdminOp-Feature) - anders als sonst gewohnt liegt hier
    // die Suche+Liste LINKS (wie Freund-Detail sonst links liegt) und die Auswahl/Aktionen
    // RECHTS, weil der Nutzer das explizit so gewünscht hat (Punkt 1 der Roadmap).
    private static final int PROFESSOR_LIST_X = 26;
    private static final int PROFESSOR_SEARCH_Y = 28;
    private static final int PROFESSOR_SEARCH_W = SEARCH_OVERLAY_W;
    private static final int PROFESSOR_SEARCH_H = 11;
    private static final int PROFESSOR_LIST_Y = PROFESSOR_SEARCH_Y + PROFESSOR_SEARCH_H + 4;
    private static final int PROFESSOR_ROW_H = 20;
    private static final int PROFESSOR_HEAD_SIZE = 16;
    private static final int PROFESSOR_BADGE_SIZE = 4;
    private static final int PROFESSOR_BADGE_OFFSET_X = 1;
    private static final int PROFESSOR_BADGE_OFFSET_Y = 1;
    private static final int PROFESSOR_NAME_OFFSET_X = 4;
    private static final int PROFESSOR_SCROLLBAR_WIDTH = 3;
    private static final int PROFESSOR_SCROLLBAR_GAP = 4;
    private static final int PROFESSOR_BOTTOM_MARGIN = 12;

    private static final int PROFESSOR_DETAIL_X = 180;
    private static final int PROFESSOR_DETAIL_Y = 28;
    private static final int PROFESSOR_DETAIL_HEAD_SIZE = 32;
    private static final int PROFESSOR_DETAIL_NAME_OFFSET_Y = 36;
    private static final int PROFESSOR_BTN_W = 100;
    private static final int PROFESSOR_BTN_H = 16;
    private static final int PROFESSOR_BTN_GAP_Y = 6;
    private static final int PROFESSOR_RESET_BTN_GAP_Y = 10; // Justierschraube: extra Abstand vor dem gefährlichen "Zurücksetzen"-Button
    private static final int PROFESSOR_BTN_START_OFFSET_Y = 16; // Abstand erster Button vom Namen

    // JUSTIERSCHRAUBE: RCT-Trainerpfad-Ansicht (AdminOp, nur wenn ClientServerRulesHelper.
    // isRctAvailable()) - ersetzt Liste+Detail komplett durch eine eigene Vollbreiten-Liste
    // (kein Cobblemon-GUI zum Einbetten vorhanden wie bei PC/Pokédex/Living Dex).
    private static final int RCT_PANEL_X = PROFESSOR_LIST_X;
    private static final int RCT_PANEL_Y = PROFESSOR_SEARCH_Y;
    private static final int RCT_PANEL_W = 293;
    private static final int RCT_HEADER_H = 20;
    private static final int RCT_BACK_BTN_W = 44;
    private static final int RCT_BACK_BTN_H = 14;
    private static final int RCT_RESET_ALL_BTN_W = 110;
    private static final int RCT_RESET_ALL_BTN_H = 14;
    private static final int RCT_LIST_Y_OFFSET = RCT_HEADER_H + 6;
    private static final int RCT_ROW_H = 16;
    private static final int RCT_ROW_GAP = 2;
    private static final int RCT_ROW_RESET_BTN_W = 44;
    private static final int RCT_ROW_RESET_BTN_H = 12;
    private static final int RCT_SCROLLBAR_WIDTH = 3;
    private static final int RCT_SCROLLBAR_GAP = 4;

    // Tab-Definitionen
    private TabDefinition[] tabs;

    public CompanionScreen(Screen originalScreen) {
        super(Component.literal("CobbleCompanion"));
        this.originalScreen = originalScreen;
    }

    @Override
    protected void init() {
        super.init();
        this.guiLeft = (this.width - GUI_WIDTH) / 2;
        this.guiTop = (this.height - GUI_HEIGHT) / 2;

        // WICHTIG: ClientProfessorHelper hält seine Versionszähler (teamDataVersion/pcDataVersion/
        // pokedexDataVersion) als STATISCHE Felder, die eine GUI-Öffnung überleben (jede Öffnung
        // erzeugt laut ClientEventHandler eine KOMPLETT NEUE CompanionScreen-Instanz - siehe
        // "new CompanionScreen(screen)"). Die frisch mit -1 initialisierten professor*VersionSeen-
        // Felder dieser neuen Instanz erkannten die von der VORHERIGEN Sitzung übrig gebliebenen
        // (höheren) statischen Zähler fälschlich als "neue Server-Antwort" und bauten beim ersten
        // Rendern des Professor-Tabs automatisch die ZULETZT angeschaute Unteransicht (Pokédex/
        // Team/PC eines anderen Spielers) wieder auf - das war der eigentliche Grund für den
        // "springt in fremde Pokédex"-Bug, nicht die Klick-Logik selbst (die wurde in der letzten
        // Runde schon korrekt gefixt, wurde hier aber sofort im nächsten Frame überschrieben).
        // Fix: aktuellen Stand einfach übernehmen ("gesehen"), ohne etwas neu zu bauen.
        professorPCDataVersionSeen = ClientProfessorHelper.getPCDataVersion();
        professorPokedexDataVersionSeen = ClientProfessorHelper.getPokedexDataVersion();
        professorLivingDexDataVersionSeen = ClientProfessorHelper.getLivingDexDataVersion();

        // Farbe des gehaltenen Pokedex auslesen
        String color = getPokedexColor();

        pokedexBase = ResourceLocation.fromNamespaceAndPath(
            "cobblemon", "textures/gui/pokedex/pokedex_base_" + color + ".png");
        pokedexScreen = ResourceLocation.fromNamespaceAndPath(
            "cobblemon", "textures/gui/pokedex/pokedex_screen.png");
        buttonsLeft = ResourceLocation.fromNamespaceAndPath(
            "cobblecompanion", "textures/gui/buttons_left_" + color + ".png");
        buttonsRight = ResourceLocation.fromNamespaceAndPath(
            "cobblecompanion", "textures/gui/buttons_right_" + color + ".png");

        // Tab Definitionen
        // TabDefinition(tabIndex, "icon_name", iconX, iconY, iconW, iconH, clickX, clickY, clickW, clickH)
        // Alle Koordinaten relativ zu guiLeft/guiTop
        java.util.List<TabDefinition> tabList = new java.util.ArrayList<>(java.util.List.of(

            // === LINKE LEISTE ===
            // Spalte 1 (links), Zeile 1-4
            new TabDefinition(0,  "tab_pokedex",     -26, 57,  12, 12,  -26, 51,  12, 24),
            new TabDefinition(2,  "tab_todo",        -26, 84,  12, 12,  -26, 78,  12, 24),
            new TabDefinition(4,  "tab_types",       -26, 111,  12, 12,  -26, 105,  12, 24),

            // Spalte 2 (rechts in linker Leiste), Zeile 1-4
            new TabDefinition(1,  "tab_livingdex",   -10, 57,  12, 12,  -10, 51,  12, 24),
            new TabDefinition(3,  "tab_whoneeds",    -10, 84,  12, 12,  -10, 78,  12, 24),
            new TabDefinition(5,  "tab_teambuilder", -10, 111,  12, 12,  -10, 105,  12, 24),
            new TabDefinition(7,  "tab_search",      -10, 138, 12, 12,  -10, 132, 12, 24),

            // === RECHTE LEISTE ===
            new TabDefinition(8,  "tab_home",        GUI_WIDTH + 2, 54, 20, 20,  GUI_WIDTH + -2, 52,  28, 23),
            new TabDefinition(9,  "tab_friends",     GUI_WIDTH + 2, 81, 20, 20,  GUI_WIDTH + -2, 79,  28, 23),
            new TabDefinition(11,  "tab_settings",    GUI_WIDTH + 2, 135, 20, 20,  GUI_WIDTH + -2, 133,  28, 23)
        ));
        // Professor-Tab nur für Op/AdminOp sichtbar - kein Eintrag in der Liste bedeutet weder
        // Icon-Rendering noch Klickbarkeit (siehe renderTabIcons()/mouseClicked()-Schleifen, die
        // beide einfach über `tabs` iterieren).
        if (ClientAdminHelper.hasAccess()) {
            tabList.add(new TabDefinition(10, "tab_professor", GUI_WIDTH + 2, 108, 20, 20, GUI_WIDTH + -2, 106, 28, 23));
        }
        // Wallet-Tab (Cobbledollars-Überweisung) nur sichtbar, wenn der Server die Cobbledollars-
        // Mod-Integration meldet (siehe ServerRulesSyncPacket/ClientServerRulesHelper) UND das
        // CobbleCompanion: CobbleDollars-Erweiterungsjar tatsächlich installiert ist (liefert den
        // Tab-Inhalt nach, siehe com.cobblecompanion.api.CompanionExtensions) - füllt den seit
        // Anfang vorbereiteten Platzhalter-Slot in Spalte 1, Zeile 4 der linken Leiste.
        if (ClientServerRulesHelper.isCobbleDollarsAvailable() && com.cobblecompanion.api.CompanionExtensions.hasTab(TAB_WALLET)) {
            tabList.add(new TabDefinition(6, "tab_wallet", -26, 138, 12, 12, -26, 132, 12, 24));
        }
        tabs = tabList.toArray(new TabDefinition[0]);

        // Suchfeld für den Type-Tab, an Cobblemons eigener SearchWidget-Position
        // orientiert (x+26, y+28, 128x11 in Cobblemons PokedexGUI) und im selben Look
        // (Overlay-Panel + Lupen-Icon + fette uniform-Schrift) nachgebaut.
        typeSearchBox = new CobblemonSearchBox(
            guiLeft + TYPE_SEARCH_X, guiTop + TYPE_SEARCH_Y,
            TYPE_SEARCH_W, TYPE_SEARCH_H, tr("cobblecompanion.gui.search.type_hint"));

        // Suchfeld für den Who-Needs-Tab, gleicher Look.
        whoNeedsSearchBox = new CobblemonSearchBox(
            guiLeft + WHONEEDS_SEARCH_X, guiTop + WHONEEDS_SEARCH_Y,
            WHONEEDS_SEARCH_W, WHONEEDS_SEARCH_H, tr("cobblecompanion.gui.search.pokemon_hint"));

        // Suchfeld für den Friends-Tab, gleicher Look, filtert die lokale Freundesliste live.
        friendsSearchBox = new CobblemonSearchBox(
            guiLeft + FRIENDS_PANEL_X, guiTop + FRIENDS_PANEL_Y,
            FRIENDS_SEARCH_W, FRIENDS_SEARCH_H, tr("cobblecompanion.gui.search.friends_hint"));

        // Suchfeld für den Professor-Tab (Op/AdminOp), gleicher Look, filtert die Spielerliste live.
        professorSearchBox = new CobblemonSearchBox(
            guiLeft + PROFESSOR_LIST_X, guiTop + PROFESSOR_SEARCH_Y,
            PROFESSOR_SEARCH_W, PROFESSOR_SEARCH_H, tr("cobblecompanion.gui.search.professor_hint"));

        // Empfänger-Suchfeld für den Wallet-Tab: WalletTabExtension legt es selbst an (siehe
        // dessen onTabOpened), sobald der Tab zum ersten Mal geöffnet wird.

        // Suchfeld für die Dex-Vervollständigungshilfe (ToDo-Tab, rechte Hälfte), gleicher Look.
        dexHelpSearchBox = new CobblemonSearchBox(
            guiLeft + DEXHELP_X, guiTop + DEXHELP_SEARCH_Y,
            DEXHELP_SEARCH_W, DEXHELP_SEARCH_H, tr("cobblecompanion.gui.search.pokemon_hint"));

        // Suchfeld für den Such-Tab, gleicher Look.
        searchTabSearchBox = new CobblemonSearchBox(
            guiLeft + SEARCH_TAB_X, guiTop + SEARCH_TAB_SEARCH_Y,
            SEARCH_TAB_SEARCH_W, SEARCH_TAB_SEARCH_H, tr("cobblecompanion.gui.search.general_hint"));

        // Home ist der Standard-Tab (currentTab==TAB_HOME) - die Dashboard-Daten müssen also
        // schon beim ersten Öffnen angefordert werden, nicht erst bei einem Tab-Wechsel weg und
        // wieder zurück (anders als bei allen anderen Tabs, die ihre Anfrage im mouseClicked-
        // Tab-Icon-Block auslösen).
        if (currentTab == TAB_HOME) {
            sendHomeSummaryRequest();
        }
    }

    /** Zentrale Sendestelle für HomeSummaryRequestPacket - bündelt alle Client-Settings, die die Antwort beeinflussen. */
    private static void sendHomeSummaryRequest() {
        sendToServer(new com.cobblecompanion.network.HomeSummaryRequestPacket(
            ClientSettingsHelper.isModusPokedex(),
            ClientSettingsHelper.isPcSlotCheckEnabled(),
            ClientSettingsHelper.getPcSortStartBox(),
            ClientSettingsHelper.getPcSortMode(),
            ClientSettingsHelper.getLivingDexPlusCategoryOrder()));
    }

    private String getPokedexColor() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return "red";

        ItemStack main = mc.player.getMainHandItem();
        ItemStack off = mc.player.getOffhandItem();

        String color = extractColorFromItem(main);
        if (color == null) color = extractColorFromItem(off);
        if (color == null) color = "red";

        return color;
    }

    private String extractColorFromItem(ItemStack stack) {
        if (stack.isEmpty()) return null;
        String itemId = stack.getItem().toString().toLowerCase();
        for (String color : new String[]{"black", "blue", "green", "pink", "red", "white", "yellow"}) {
            if (itemId.contains(color)) return color;
        }
        return null;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Professor-Tab zeigt gerade ein eingebettetes Cobblemon-Fenster eines ANDEREN Spielers
        // (Pokédex/Living Dex/PC) - dessen Optik passt nicht zu unserem eigenen Rahmen, deshalb
        // verschwindet der Companion dahinter komplett (nur Tab-Icons bleiben für die Navigation
        // zurück sichtbar, siehe Schritt 6).
        boolean hideCompanionChrome = currentTab == TAB_PROFESSOR && professorSubScreen != null;

        if (!hideCompanionChrome) {
            // Halbtransparenter Hintergrund (verhindert Pixelbrei)
            graphics.fill(0, 0, this.width, this.height, 0x80000000);

            // 2. Pokedex Screen darüber
            graphics.blit(pokedexScreen,
                guiLeft, guiTop,
                0, 0,
                GUI_WIDTH, GUI_HEIGHT,
                GUI_WIDTH, GUI_HEIGHT);

            // 1. Pokedex Rahmen zuerst
            graphics.blit(pokedexBase,
                guiLeft, guiTop,
                0, 0,
                GUI_WIDTH, GUI_HEIGHT,
                GUI_WIDTH, GUI_HEIGHT);
        }

        // 3. Tab-Inhalt
        renderCurrentTab(graphics, mouseX, mouseY);

        if (!hideCompanionChrome) {
            // 4. Linke Button-Leiste
            graphics.blit(buttonsLeft,
                guiLeft - BUTTON_STRIP_WIDTH + 15, guiTop + 39,
                0, 0,
                BUTTON_STRIP_WIDTH, BUTTON_STRIP_HEIGHT,
                BUTTON_STRIP_WIDTH, BUTTON_STRIP_HEIGHT);

            // 5. Rechte Button-Leiste
            graphics.blit(buttonsRight,
                guiLeft + GUI_WIDTH - 15, guiTop + 39,
                0, 0,
                BUTTON_STRIP_WIDTH, BUTTON_STRIP_HEIGHT,
                BUTTON_STRIP_WIDTH, BUTTON_STRIP_HEIGHT);
        }

        // 6. Tab-Icons - während einer Professor-Unteransicht (Pokédex/Living Dex/PC eines anderen
        // Spielers) ebenfalls ausgeblendet: die Zurück-Navigation läuft dort über Cobblemons
        // eigene Tür-Knöpfe (PC/Team) bzw. den "< Zurück"-Link (Pokédex), ein erneuter Klick
        // auf das Professor-Icon wäre dafür nicht mehr nötig und störte nur optisch.
        if (!hideCompanionChrome) {
            renderTabIcons(graphics);
        }

        // 7. Aktives Tab Icon + Name (nur wenn nicht Pokédex, Living Dex oder eine Professor-
        // Unteransicht - die haben alle ihre eigene, andersartige Kopfzeile).
        if (currentTab != TAB_POKEDEX && currentTab != TAB_LIVINGDEX && !hideCompanionChrome) {
            for (TabDefinition tab : tabs) {
                if (tab.tabIndex == currentTab) {
                    graphics.blit(tab.icon,
                        guiLeft + 22, guiTop + 13,
                        10, 10,
                        0, 0,
                        32, 32,
                        32, 32);
                    break;
                }
            }
            drawScaledBoldText(graphics,
                tr(TAB_NAMES[currentTab]),
                guiLeft + 35, guiTop + 14,
                TAB_TITLE_TEXT_SCALE, 0xFFFFFF);
        }

        // 8. Widgets
        for (var widget : this.renderables) {
            widget.render(graphics, mouseX, mouseY, partialTick);
        }

        // 9. Autocomplete-Vorschläge (oberste Ebene wie ein Tooltip, außer über Modalen).
        renderActiveSearchSuggestions(graphics, mouseX, mouseY);

        // 10. Modale Sicherheitsabfragen (liegen über allem, auch über Vorschlägen). Auf eine
        // höhere Ebene als alles andere im GUI gehoben (Pokemon-Slot-Nummern/Level rendern
        // selbst schon auf Ebene 300, siehe renderPokemonNumberedSlot) - sonst könnten deren
        // Zahlen durch den Modal-Hintergrund "durchscheinen" statt sauber verdeckt zu werden.
        if (pendingEvolveEntry != null || pendingRemoveFriendUuid != null || giftOverlayTargetUuid != null || adminEditOverlayOpen || resetPlayerConfirmStage > 0 || pendingRctReset != null || extensionHasBlockingOverlay() || reclaimOverlayOpen) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 500);
            if (pendingEvolveEntry != null) {
                renderEvolveConfirmOverlay(graphics, mouseX, mouseY);
            }
            if (pendingRemoveFriendUuid != null) {
                renderRemoveFriendConfirmOverlay(graphics, mouseX, mouseY);
            }
            if (reclaimOverlayOpen) {
                renderReclaimOverlay(graphics, mouseX, mouseY);
            }
            if (giftOverlayTargetUuid != null) {
                renderGiftPartyOverlay(graphics, mouseX, mouseY);
            }
            if (resetPlayerConfirmStage > 0) {
                renderResetPlayerConfirmOverlay(graphics, mouseX, mouseY);
            }
            if (pendingRctReset != null) {
                renderRctResetConfirmOverlay(graphics, mouseX, mouseY);
            }
            if (extensionHasBlockingOverlay()) {
                CompanionExtensions.getTab(currentTab).renderBlockingOverlay(graphics, mouseX, mouseY, tabContext());
            }
            if (adminEditOverlayOpen) {
                // Spieler-Auswahl-Overlay ERSETZT das Editor-Overlay komplett (statt es nur zu
                // überdecken) - beide haben einen eigenen Vollbild-Verdunklungs-Hintergrund, ein
                // gleichzeitiges Zeichnen beider ließ Reste des Editor-Overlays durchscheinen.
                if (adminGiftOverlayOpen) {
                    renderAdminGiftOverlay(graphics, mouseX, mouseY);
                } else if (adminEvolveOverlayOpen) {
                    renderAdminEvolveOverlay(graphics, mouseX, mouseY);
                } else {
                    renderAdminEditOverlay(graphics, mouseX, mouseY);
                }
            }
            graphics.pose().popPose();
        }
    }

    // ===== Sicherheitsabfrage-Overlay (Entwicklung) =====
    private static final int CONFIRM_BOX_W = 180;
    private static final int CONFIRM_BOX_H = 60;
    private static final int CONFIRM_BTN_W = 56;
    private static final int CONFIRM_BTN_H = 15;
    private static final int CONFIRM_BTN_GAP = 12;

    // ===== Home-Tab: Hinweise auf ausstehende Abholungen (Inventar + Offline-Auszahlung) (Justierschrauben) =====
    // Nutzer-Vorgabe: beide Hinweise teilen sich dieselbe Position (rechte, bisher ungenutzte
    // Hälfte des Home-Tabs, unterhalb des Willkommens-Schriftzugs bei contentY=guiTop+30) - sind
    // beide gleichzeitig fällig, stapeln sie sich (Inventar-Hinweis oben, Auszahlungs-Hinweis
    // darunter) statt sich zu überlappen, siehe homeReclaimBadgeY()/homePendingCdBadgeY().
    private static final int HOME_RECLAIM_BADGE_X = HOME_GIFT_X;
    private static final int HOME_RECLAIM_BADGE_BASE_Y = 44;
    private static final int HOME_RECLAIM_BADGE_W = 100;
    private static final int HOME_RECLAIM_BADGE_H = 10;
    private static final float HOME_RECLAIM_BADGE_TEXT_SCALE = 1.0f;

    private static final int HOME_PENDING_CD_BADGE_X = HOME_GIFT_X;
    private static final int HOME_PENDING_CD_BADGE_W = 100;
    private static final int HOME_PENDING_CD_BADGE_H = 10;
    private static final float HOME_PENDING_CD_BADGE_TEXT_SCALE = 1.0f;

    /** Absolute Y-Position des Inventar-Abhol-Hinweises - immer an der Basisposition. */
    private int homeReclaimBadgeY() {
        return guiTop + HOME_RECLAIM_BADGE_BASE_Y;
    }

    /** Absolute Y-Position des Offline-Auszahlung-Hinweises - rutscht unter den Inventar-Hinweis, wenn beide fällig sind. */
    private int homePendingCdBadgeY() {
        int y = guiTop + HOME_RECLAIM_BADGE_BASE_Y;
        if (!ClientGamemodeInventoryHelper.getReclaimEntries().isEmpty()) {
            y += HOME_RECLAIM_BADGE_H + 3;
        }
        return y;
    }

    // ===== Abhol-Warteschlange-Overlay (modal, siehe reclaimOverlayOpen) =====
    private static final int RECLAIM_OVERLAY_W = 220;
    private static final int RECLAIM_OVERLAY_H = 150;
    private static final int RECLAIM_TITLE_Y_OFFSET = 10;
    private static final int RECLAIM_LIST_TOP_OFFSET = 26;
    private static final int RECLAIM_LIST_SIDE_PADDING = 12;
    private static final int RECLAIM_ENTRY_H = 24;
    private static final int RECLAIM_ENTRY_TEXT_Y_OFFSET = 5;
    private static final int RECLAIM_ENTRY_BTN_W = 60;
    private static final int RECLAIM_ENTRY_BTN_H = 14;
    private static final int RECLAIM_ENTRY_BTN_Y_OFFSET = 4;
    private static final int RECLAIM_EMPTY_TEXT_Y_OFFSET = 60;
    private static final int RECLAIM_CLOSE_BTN_W = 70;
    private static final int RECLAIM_CLOSE_BTN_H = 14;
    private static final int RECLAIM_CLOSE_BTN_BOTTOM_MARGIN = 10;

    private void renderEvolveConfirmOverlay(GuiGraphics graphics, int mouseX, int mouseY) {
        // Abdunkeln + zentrierte Box.
        graphics.fill(0, 0, this.width, this.height, 0xC0000000);
        int boxX = guiLeft + (GUI_WIDTH - CONFIRM_BOX_W) / 2;
        int boxY = guiTop + (GUI_HEIGHT - CONFIRM_BOX_H) / 2;
        graphics.fill(boxX, boxY, boxX + CONFIRM_BOX_W, boxY + CONFIRM_BOX_H, 0xFF202020);
        graphics.fill(boxX, boxY, boxX + CONFIRM_BOX_W, boxY + 1, 0xFF00A0C0);
        graphics.fill(boxX, boxY + CONFIRM_BOX_H - 1, boxX + CONFIRM_BOX_W, boxY + CONFIRM_BOX_H, 0xFF00A0C0);

        String question = tr("cobblecompanion.gui.todo.confirm");
        int qWidth = smallLabelWidth(question, 1.0f, true, true);
        drawSmallLabel(graphics, question, boxX + (CONFIRM_BOX_W - qWidth) / 2, boxY + 10, 1.0f, 0xFFFFFF, true, true);

        int btnY = boxY + CONFIRM_BOX_H - CONFIRM_BTN_H - 8;
        int yesX = boxX + CONFIRM_BOX_W / 2 - CONFIRM_BTN_W - CONFIRM_BTN_GAP / 2;
        int noX = boxX + CONFIRM_BOX_W / 2 + CONFIRM_BTN_GAP / 2;
        renderConfirmButton(graphics, yesX, btnY, tr("cobblecompanion.gui.confirm.yes"), 0xFF55FF55, mouseX, mouseY);
        renderConfirmButton(graphics, noX, btnY, tr("cobblecompanion.gui.confirm.no"), 0xFFFF5555, mouseX, mouseY);
    }

    private void renderConfirmButton(GuiGraphics graphics, int x, int y, String label, int color, int mouseX, int mouseY) {
        renderConfirmButton(graphics, x, y, CONFIRM_BTN_W, CONFIRM_BTN_H, label, color, mouseX, mouseY);
    }

    void renderConfirmButton(GuiGraphics graphics, int x, int y, int w, int h, String label, int color, int mouseX, int mouseY) {
        boolean hovered = isInRect(mouseX, mouseY, x, y, w, h);
        graphics.blit(TODO_EVOLVE_BUTTON, x, y, w, h,
            0f, hovered ? TODO_EVOLVE_BTN_NATIVE_H : 0f,
            TODO_EVOLVE_BTN_NATIVE_W, TODO_EVOLVE_BTN_NATIVE_H, TODO_EVOLVE_BTN_NATIVE_W, TODO_EVOLVE_BTN_NATIVE_H * 2);
        int lw = smallLabelWidth(label, 1.0f, true, true);
        drawSmallLabel(graphics, label, x + (w - lw) / 2, y + (h - 9) / 2, 1.0f, color, true, true);
    }

    /** Verarbeitet Klicks auf das Confirm-Overlay. Gibt true zurück, wenn der Klick konsumiert wurde. */
    private boolean handleEvolveConfirmClick(double mouseX, double mouseY) {
        int boxX = guiLeft + (GUI_WIDTH - CONFIRM_BOX_W) / 2;
        int boxY = guiTop + (GUI_HEIGHT - CONFIRM_BOX_H) / 2;
        int btnY = boxY + CONFIRM_BOX_H - CONFIRM_BTN_H - 8;
        int yesX = boxX + CONFIRM_BOX_W / 2 - CONFIRM_BTN_W - CONFIRM_BTN_GAP / 2;
        int noX = boxX + CONFIRM_BOX_W / 2 + CONFIRM_BTN_GAP / 2;
        if (isInRect(mouseX, mouseY, yesX, btnY, CONFIRM_BTN_W, CONFIRM_BTN_H)) {
            ClientTodoHelper.TodoEntry entry = pendingEvolveEntry;
            if (entry != null) performEvolve(entry);
            return true;
        }
        // Nein-Button oder Klick irgendwo sonst -> abbrechen.
        pendingEvolveEntry = null;
        return true;
    }

    /** Wie renderEvolveConfirmOverlay, aber für "Freund wirklich entfernen?". */
    private void renderRemoveFriendConfirmOverlay(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(0, 0, this.width, this.height, 0xC0000000);
        int boxX = guiLeft + (GUI_WIDTH - CONFIRM_BOX_W) / 2;
        int boxY = guiTop + (GUI_HEIGHT - CONFIRM_BOX_H) / 2;
        graphics.fill(boxX, boxY, boxX + CONFIRM_BOX_W, boxY + CONFIRM_BOX_H, 0xFF202020);
        graphics.fill(boxX, boxY, boxX + CONFIRM_BOX_W, boxY + 1, 0xFF00A0C0);
        graphics.fill(boxX, boxY + CONFIRM_BOX_H - 1, boxX + CONFIRM_BOX_W, boxY + CONFIRM_BOX_H, 0xFF00A0C0);

        String question = tr("cobblecompanion.gui.friends.confirm_remove", pendingRemoveFriendName);
        int qWidth = smallLabelWidth(question, 1.0f, true, true);
        drawSmallLabel(graphics, question, boxX + (CONFIRM_BOX_W - qWidth) / 2, boxY + 10, 1.0f, 0xFFFFFF, true, true);

        int btnY = boxY + CONFIRM_BOX_H - CONFIRM_BTN_H - 8;
        int yesX = boxX + CONFIRM_BOX_W / 2 - CONFIRM_BTN_W - CONFIRM_BTN_GAP / 2;
        int noX = boxX + CONFIRM_BOX_W / 2 + CONFIRM_BTN_GAP / 2;
        renderConfirmButton(graphics, yesX, btnY, tr("cobblecompanion.gui.confirm.yes"), 0xFF55FF55, mouseX, mouseY);
        renderConfirmButton(graphics, noX, btnY, tr("cobblecompanion.gui.confirm.no"), 0xFFFF5555, mouseX, mouseY);
    }

    /**
     * Nutzer-Vorgabe: verwaiste Gamemode-Inventare (Trennung deaktiviert/Admin-Reset) dürfen nie
     * verloren gehen - dauerhafte Warteschlange statt Zeitlimit, Teil-Abholung bei vollem
     * Inventar möglich (siehe GamemodeInventorySyncManager.claim). Optisch am Sicherheitsabfrage-
     * Overlay orientiert, aber mit scrollfreier fester Liste (max. 4 GameTypes gleichzeitig).
     */
    private void renderReclaimOverlay(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(0, 0, this.width, this.height, 0xC0000000);
        int boxX = guiLeft + (GUI_WIDTH - RECLAIM_OVERLAY_W) / 2;
        int boxY = guiTop + (GUI_HEIGHT - RECLAIM_OVERLAY_H) / 2;
        graphics.fill(boxX, boxY, boxX + RECLAIM_OVERLAY_W, boxY + RECLAIM_OVERLAY_H, 0xFF202020);
        graphics.fill(boxX, boxY, boxX + RECLAIM_OVERLAY_W, boxY + 1, 0xFF00A0C0);
        graphics.fill(boxX, boxY + RECLAIM_OVERLAY_H - 1, boxX + RECLAIM_OVERLAY_W, boxY + RECLAIM_OVERLAY_H, 0xFF00A0C0);

        String title = tr("cobblecompanion.gui.home.reclaim_title");
        int titleWidth = smallLabelWidth(title, 1.0f, true, true);
        drawSmallLabel(graphics, title, boxX + (RECLAIM_OVERLAY_W - titleWidth) / 2, boxY + RECLAIM_TITLE_Y_OFFSET, 1.0f, 0xFFFFFF, true, true);

        List<ClientGamemodeInventoryHelper.ReclaimEntryView> entries = ClientGamemodeInventoryHelper.getReclaimEntries();
        if (entries.isEmpty()) {
            String empty = tr("cobblecompanion.gui.home.reclaim_empty");
            int emptyWidth = smallLabelWidth(empty, 1.0f, false, true);
            drawSmallLabel(graphics, empty, boxX + (RECLAIM_OVERLAY_W - emptyWidth) / 2, boxY + RECLAIM_EMPTY_TEXT_Y_OFFSET, 1.0f, 0xFFAAAAAA, false, true);
        } else {
            int y = boxY + RECLAIM_LIST_TOP_OFFSET;
            for (ClientGamemodeInventoryHelper.ReclaimEntryView entry : entries) {
                String label = tr("cobblecompanion.gui.home.reclaim_entry", tr("cobblecompanion.gamemode." + entry.gameTypeName()), entry.itemCount());
                drawSmallLabel(graphics, label, boxX + RECLAIM_LIST_SIDE_PADDING, y + RECLAIM_ENTRY_TEXT_Y_OFFSET, 1.0f, 0xFFFFFF, false, true);

                int btnX = boxX + RECLAIM_OVERLAY_W - RECLAIM_LIST_SIDE_PADDING - RECLAIM_ENTRY_BTN_W;
                int btnY = y + RECLAIM_ENTRY_BTN_Y_OFFSET;
                renderConfirmButton(graphics, btnX, btnY, RECLAIM_ENTRY_BTN_W, RECLAIM_ENTRY_BTN_H,
                    tr("cobblecompanion.gui.home.reclaim_claim"), 0xFF55FF55, mouseX, mouseY);
                y += RECLAIM_ENTRY_H;
            }
        }

        int closeX = boxX + (RECLAIM_OVERLAY_W - RECLAIM_CLOSE_BTN_W) / 2;
        int closeY = boxY + RECLAIM_OVERLAY_H - RECLAIM_CLOSE_BTN_H - RECLAIM_CLOSE_BTN_BOTTOM_MARGIN;
        renderConfirmButton(graphics, closeX, closeY, RECLAIM_CLOSE_BTN_W, RECLAIM_CLOSE_BTN_H,
            tr("cobblecompanion.gui.confirm.close"), 0xFFAAAAAA, mouseX, mouseY);
    }

    private boolean handleReclaimOverlayClick(double mouseX, double mouseY) {
        int boxX = guiLeft + (GUI_WIDTH - RECLAIM_OVERLAY_W) / 2;
        int boxY = guiTop + (GUI_HEIGHT - RECLAIM_OVERLAY_H) / 2;

        List<ClientGamemodeInventoryHelper.ReclaimEntryView> entries = ClientGamemodeInventoryHelper.getReclaimEntries();
        int y = boxY + RECLAIM_LIST_TOP_OFFSET;
        for (int i = 0; i < entries.size(); i++) {
            int btnX = boxX + RECLAIM_OVERLAY_W - RECLAIM_LIST_SIDE_PADDING - RECLAIM_ENTRY_BTN_W;
            int btnY = y + RECLAIM_ENTRY_BTN_Y_OFFSET;
            if (isInRect(mouseX, mouseY, btnX, btnY, RECLAIM_ENTRY_BTN_W, RECLAIM_ENTRY_BTN_H)) {
                sendToServer(new com.cobblecompanion.network.GamemodeInventoryReclaimClaimPacket(i));
                return true;
            }
            y += RECLAIM_ENTRY_H;
        }

        int closeX = boxX + (RECLAIM_OVERLAY_W - RECLAIM_CLOSE_BTN_W) / 2;
        int closeY = boxY + RECLAIM_OVERLAY_H - RECLAIM_CLOSE_BTN_H - RECLAIM_CLOSE_BTN_BOTTOM_MARGIN;
        if (isInRect(mouseX, mouseY, closeX, closeY, RECLAIM_CLOSE_BTN_W, RECLAIM_CLOSE_BTN_H)) {
            reclaimOverlayOpen = false;
            return true;
        }
        return true;
    }

    private boolean handleRemoveFriendConfirmClick(double mouseX, double mouseY) {
        int boxX = guiLeft + (GUI_WIDTH - CONFIRM_BOX_W) / 2;
        int boxY = guiTop + (GUI_HEIGHT - CONFIRM_BOX_H) / 2;
        int btnY = boxY + CONFIRM_BOX_H - CONFIRM_BTN_H - 8;
        int yesX = boxX + CONFIRM_BOX_W / 2 - CONFIRM_BTN_W - CONFIRM_BTN_GAP / 2;
        int noX = boxX + CONFIRM_BOX_W / 2 + CONFIRM_BTN_GAP / 2;
        if (isInRect(mouseX, mouseY, yesX, btnY, CONFIRM_BTN_W, CONFIRM_BTN_H) && pendingRemoveFriendUuid != null) {
            sendToServer(FriendActionPacket.forUuid(FriendActionPacket.REMOVE, pendingRemoveFriendUuid));
        }
        // Nein-Button oder Klick irgendwo sonst -> abbrechen (Ja-Fall ist bereits gesendet).
        pendingRemoveFriendUuid = null;
        pendingRemoveFriendName = "";
        return true;
    }

    // ===== "Spieler zurücksetzen"-Bestätigung (AdminOp, Professor-Tab) - zweistufig: erst Ja/Nein,
    // dann muss "BESTÄTIGT" exakt eingetippt werden. Löscht Team+PC+Pokédex/Living-Dex-Fortschritt
    // des Zielspielers unwiderruflich (siehe AdminResetPlayerPacket). =====
    private static final String RESET_PLAYER_CONFIRM_WORD = "BESTÄTIGT";
    private static final int RESET_STAGE2_BOX_H = 90;
    private static final int RESET_INPUT_W = 140;
    private static final int RESET_INPUT_H = 16;

    private void renderResetPlayerConfirmOverlay(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(0, 0, this.width, this.height, 0xC0000000);
        if (resetPlayerConfirmStage == 1) {
            int boxX = guiLeft + (GUI_WIDTH - CONFIRM_BOX_W) / 2;
            int boxY = guiTop + (GUI_HEIGHT - CONFIRM_BOX_H) / 2;
            graphics.fill(boxX, boxY, boxX + CONFIRM_BOX_W, boxY + CONFIRM_BOX_H, 0xFF202020);
            graphics.fill(boxX, boxY, boxX + CONFIRM_BOX_W, boxY + 1, 0xFFFF5555);
            graphics.fill(boxX, boxY + CONFIRM_BOX_H - 1, boxX + CONFIRM_BOX_W, boxY + CONFIRM_BOX_H, 0xFFFF5555);

            String question = tr("cobblecompanion.gui.admin.reset_confirm1");
            int qWidth = smallLabelWidth(question, 1.0f, true, true);
            drawSmallLabel(graphics, question, boxX + (CONFIRM_BOX_W - qWidth) / 2, boxY + 10, 1.0f, 0xFFFFFF, true, true);

            int btnY = boxY + CONFIRM_BOX_H - CONFIRM_BTN_H - 8;
            int yesX = boxX + CONFIRM_BOX_W / 2 - CONFIRM_BTN_W - CONFIRM_BTN_GAP / 2;
            int noX = boxX + CONFIRM_BOX_W / 2 + CONFIRM_BTN_GAP / 2;
            renderConfirmButton(graphics, yesX, btnY, tr("cobblecompanion.gui.confirm.yes"), 0xFF55FF55, mouseX, mouseY);
            renderConfirmButton(graphics, noX, btnY, tr("cobblecompanion.gui.confirm.no"), 0xFFFF5555, mouseX, mouseY);
        } else {
            int boxX = guiLeft + (GUI_WIDTH - CONFIRM_BOX_W) / 2;
            int boxY = guiTop + (GUI_HEIGHT - RESET_STAGE2_BOX_H) / 2;
            graphics.fill(boxX, boxY, boxX + CONFIRM_BOX_W, boxY + RESET_STAGE2_BOX_H, 0xFF202020);
            graphics.fill(boxX, boxY, boxX + CONFIRM_BOX_W, boxY + 1, 0xFFFF5555);
            graphics.fill(boxX, boxY + RESET_STAGE2_BOX_H - 1, boxX + CONFIRM_BOX_W, boxY + RESET_STAGE2_BOX_H, 0xFFFF5555);

            String question = Component.translatable("cobblecompanion.gui.admin.reset_confirm2", RESET_PLAYER_CONFIRM_WORD).getString();
            List<String> questionLines = wrapText(question, 1.0f, true, true, CONFIRM_BOX_W - 16);
            int lineY = boxY + 8;
            for (String line : questionLines) {
                drawSmallLabel(graphics, line, boxX + 8, lineY, 1.0f, 0xFFFFFF, true, true);
                lineY += 9;
            }

            int inputX = boxX + (CONFIRM_BOX_W - RESET_INPUT_W) / 2;
            int inputY = boxY + 38;
            graphics.fill(inputX - 2, inputY - 2, inputX + RESET_INPUT_W + 2, inputY + RESET_INPUT_H + 2, 0xFF000000);
            boolean matches = resetPlayerConfirmInput.equals(RESET_PLAYER_CONFIRM_WORD);
            graphics.fill(inputX - 1, inputY - 1, inputX + RESET_INPUT_W + 1, inputY + RESET_INPUT_H + 1, matches ? 0xFF55FF55 : 0xFF808080);
            graphics.fill(inputX, inputY, inputX + RESET_INPUT_W, inputY + RESET_INPUT_H, 0xFF000000);
            drawSmallLabel(graphics, resetPlayerConfirmInput + "_", inputX + 4, inputY + 4, 1.0f, 0xFFFFFF, true, true);

            int btnY = boxY + RESET_STAGE2_BOX_H - CONFIRM_BTN_H - 8;
            int yesX = boxX + CONFIRM_BOX_W / 2 - CONFIRM_BTN_W - CONFIRM_BTN_GAP / 2;
            int noX = boxX + CONFIRM_BOX_W / 2 + CONFIRM_BTN_GAP / 2;
            renderConfirmButton(graphics, yesX, btnY, tr("cobblecompanion.gui.confirm.yes"), matches ? 0xFF55FF55 : 0xFF606060, mouseX, mouseY);
            renderConfirmButton(graphics, noX, btnY, tr("cobblecompanion.gui.confirm.no"), 0xFFFF5555, mouseX, mouseY);
        }
    }

    private boolean handleResetPlayerConfirmClick(double mouseX, double mouseY) {
        if (resetPlayerConfirmStage == 1) {
            int boxX = guiLeft + (GUI_WIDTH - CONFIRM_BOX_W) / 2;
            int boxY = guiTop + (GUI_HEIGHT - CONFIRM_BOX_H) / 2;
            int btnY = boxY + CONFIRM_BOX_H - CONFIRM_BTN_H - 8;
            int yesX = boxX + CONFIRM_BOX_W / 2 - CONFIRM_BTN_W - CONFIRM_BTN_GAP / 2;
            int noX = boxX + CONFIRM_BOX_W / 2 + CONFIRM_BTN_GAP / 2;
            if (isInRect(mouseX, mouseY, yesX, btnY, CONFIRM_BTN_W, CONFIRM_BTN_H)) {
                resetPlayerConfirmStage = 2;
                resetPlayerConfirmInput = "";
                return true;
            }
            resetPlayerConfirmStage = 0;
            return true;
        }
        int boxX = guiLeft + (GUI_WIDTH - CONFIRM_BOX_W) / 2;
        int boxY = guiTop + (GUI_HEIGHT - RESET_STAGE2_BOX_H) / 2;
        int btnY = boxY + RESET_STAGE2_BOX_H - CONFIRM_BTN_H - 8;
        int yesX = boxX + CONFIRM_BOX_W / 2 - CONFIRM_BTN_W - CONFIRM_BTN_GAP / 2;
        int noX = boxX + CONFIRM_BOX_W / 2 + CONFIRM_BTN_GAP / 2;
        if (isInRect(mouseX, mouseY, yesX, btnY, CONFIRM_BTN_W, CONFIRM_BTN_H)
                && resetPlayerConfirmInput.equals(RESET_PLAYER_CONFIRM_WORD)) {
            ClientProfessorHelper.PlayerItem selected = ClientProfessorHelper.getSelected();
            if (selected != null) {
                sendToServer(new com.cobblecompanion.network.AdminResetPlayerPacket(selected.uuid));
            }
            resetPlayerConfirmStage = 0;
            return true;
        }
        resetPlayerConfirmStage = 0;
        return true;
    }

    // ===== Party-Auswahl-Overlay (Pokemon verschenken) =====

    /** Zeigt die eigene Party (ClientGiftHelper) zur Auswahl, welches Pokemon verschenkt werden soll. */
    private void renderGiftPartyOverlay(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(0, 0, this.width, this.height, 0xC0000000);
        int boxX = guiLeft + (GUI_WIDTH - GIFT_OVERLAY_BOX_W) / 2;
        int boxY = guiTop + (GUI_HEIGHT - GIFT_OVERLAY_BOX_H) / 2;
        graphics.fill(boxX, boxY, boxX + GIFT_OVERLAY_BOX_W, boxY + GIFT_OVERLAY_BOX_H, 0xFF202020);
        graphics.fill(boxX, boxY, boxX + GIFT_OVERLAY_BOX_W, boxY + 1, 0xFF00A0C0);
        graphics.fill(boxX, boxY + GIFT_OVERLAY_BOX_H - 1, boxX + GIFT_OVERLAY_BOX_W, boxY + GIFT_OVERLAY_BOX_H, 0xFF00A0C0);

        String title = tr("cobblecompanion.gui.friends.gift_pick", giftOverlayTargetName);
        int titleWidth = smallLabelWidth(title, 1.0f, true, true);
        drawSmallLabel(graphics, title, boxX + (GIFT_OVERLAY_BOX_W - titleWidth) / 2, boxY + 8, 1.0f, 0xFFFFFF, true, true);

        List<ClientGiftHelper.PartySlot> party = ClientGiftHelper.getParty();
        if (party.isEmpty()) {
            String empty = tr("cobblecompanion.gui.friends.gift_empty");
            int ew = smallLabelWidth(empty, 1.0f, false, true);
            drawSmallLabel(graphics, empty, boxX + (GIFT_OVERLAY_BOX_W - ew) / 2, boxY + GIFT_OVERLAY_SLOT_START_Y + 10, 1.0f, 0xAAAAAA, false, true);
        } else {
            int gridW = GIFT_OVERLAY_SLOT_COLUMNS * GIFT_OVERLAY_SLOT_SPACING;
            int startX = boxX + (GIFT_OVERLAY_BOX_W - gridW) / 2;
            for (int i = 0; i < party.size(); i++) {
                ClientGiftHelper.PartySlot slot = party.get(i);
                int col = i % GIFT_OVERLAY_SLOT_COLUMNS;
                int row = i / GIFT_OVERLAY_SLOT_COLUMNS;
                int slotX = startX + col * GIFT_OVERLAY_SLOT_SPACING;
                int slotY = boxY + GIFT_OVERLAY_SLOT_START_Y + row * GIFT_OVERLAY_SLOT_SPACING;
                boolean hovered = isInRect(mouseX, mouseY, slotX, slotY, PokemonSlotRenderer.SLOT_SIZE, PokemonSlotRenderer.SLOT_SIZE);
                if (hovered) graphics.fill(slotX - 1, slotY - 1, slotX + PokemonSlotRenderer.SLOT_SIZE + 1, slotY + PokemonSlotRenderer.SLOT_SIZE + 1, 0x60FFFFFF);
                renderPokemonNumberedSlot(graphics, slotX, slotY, slot.speciesId, slot.aspects, slot.level);
            }
        }

        String cancel = tr("cobblecompanion.gui.confirm.no");
        int cw = smallLabelWidth(cancel, 1.0f, true, true);
        int cancelX = boxX + (GIFT_OVERLAY_BOX_W - cw) / 2;
        int cancelY = boxY + GIFT_OVERLAY_BOX_H - 14;
        drawSmallLabel(graphics, cancel, cancelX, cancelY, 1.0f, 0xFFFF5555, true, true);
    }

    private boolean handleGiftPartyOverlayClick(double mouseX, double mouseY) {
        int boxX = guiLeft + (GUI_WIDTH - GIFT_OVERLAY_BOX_W) / 2;
        int boxY = guiTop + (GUI_HEIGHT - GIFT_OVERLAY_BOX_H) / 2;

        List<ClientGiftHelper.PartySlot> party = ClientGiftHelper.getParty();
        int gridW = GIFT_OVERLAY_SLOT_COLUMNS * GIFT_OVERLAY_SLOT_SPACING;
        int startX = boxX + (GIFT_OVERLAY_BOX_W - gridW) / 2;
        for (int i = 0; i < party.size(); i++) {
            ClientGiftHelper.PartySlot slot = party.get(i);
            int col = i % GIFT_OVERLAY_SLOT_COLUMNS;
            int row = i / GIFT_OVERLAY_SLOT_COLUMNS;
            int slotX = startX + col * GIFT_OVERLAY_SLOT_SPACING;
            int slotY = boxY + GIFT_OVERLAY_SLOT_START_Y + row * GIFT_OVERLAY_SLOT_SPACING;
            if (isInRect(mouseX, mouseY, slotX, slotY, PokemonSlotRenderer.SLOT_SIZE, PokemonSlotRenderer.SLOT_SIZE)) {
                sendToServer(new GiftOfferPacket(slot.pokemonUuid, giftOverlayTargetUuid));
                giftOverlayTargetUuid = null;
                giftOverlayTargetName = "";
                return true;
            }
        }
        // Klick auf Abbrechen-Text oder irgendwo sonst -> Overlay schließen.
        giftOverlayTargetUuid = null;
        giftOverlayTargetName = "";
        return true;
    }

    private void renderTabIcons(GuiGraphics graphics) {
        for (TabDefinition tab : tabs) {
            int renderX = guiLeft + tab.iconX;
            int renderY = guiTop + tab.iconY;

            // Aktiver Tab hervorheben
            if (currentTab == tab.tabIndex) {
                graphics.fill(
                    guiLeft + tab.clickX,
                    guiTop + tab.clickY,
                    guiLeft + tab.clickX + tab.clickW,
                    guiTop + tab.clickY + tab.clickH,
                    0x40FFFFFF);
            }

            // Icon rendern
            graphics.blit(tab.icon,
                renderX, renderY,
                tab.iconW, tab.iconH,
                0, 0,
                32, 32,
                32, 32);
        }
    }

    private void renderCurrentTab(GuiGraphics graphics, int mouseX, int mouseY) {
        int contentX = guiLeft + 25;
        int contentY = guiTop + 30;

        switch (currentTab) {
            case TAB_POKEDEX     -> renderPokedexTab(graphics, mouseX, mouseY);
            // Bugfix: hier wurde bisher contentX/contentY (fixer Punkt) statt der echten
            // Mausposition durchgereicht. Cobblemons eigene Widgets (Buttons, Scroll-Liste,
            // Search-By-Tooltip) berechnen ihren Hover-Status jeden Frame neu direkt aus den
            // an render() übergebenen mouseX/mouseY (siehe AbstractWidget.render()) - ohne
            // echte Mausposition gab es dadurch nie Hover-/Tooltip-Effekte im Living-Dex-Tab.
            case TAB_LIVINGDEX   -> renderLivingDexTab(graphics, mouseX, mouseY);
            // Braucht die echte Mausposition für den Hover-Zustand des Entwickeln-Buttons.
            case TAB_TODO        -> renderTodoTab(graphics, mouseX, mouseY);
            case TAB_TYPES       -> renderTypesTab(graphics, mouseX, mouseY);
            case TAB_WHONEEDS    -> renderWhoNeedsTab(graphics, mouseX, mouseY);
            case TAB_WALLET      -> renderExtensionTab(graphics, mouseX, mouseY);
            case TAB_HOME        -> renderHomeTab(graphics, contentX, contentY, mouseX, mouseY);
            case TAB_SEARCH      -> renderSearchTab(graphics, mouseX, mouseY);
            case TAB_TEAMBUILDER -> renderTeamBuilderTab(graphics, mouseX, mouseY);
            case TAB_FRIENDS     -> renderFriendsTab(graphics, mouseX, mouseY);
            case TAB_PROFESSOR   -> renderProfessorTab(graphics, mouseX, mouseY);
            case TAB_SETTINGS    -> renderSettingsTab(graphics, mouseX, mouseY);
            default -> drawScaledBoldText(graphics,
                tr("cobblecompanion.gui.coming_soon", tr(TAB_NAMES[currentTab])),
                contentX, contentY, DEFAULT_TEXT_SCALE, 0x000000);
        }
    }

    /**
     * Rendert den aktuell aktiven Tab, sofern dafür eine {@link CompanionTabExtension} registriert
     * ist (siehe com.cobblecompanion.api.CompanionExtensions) - sonst den bestehenden
     * "nicht installiert"-Platzhalter. Aktuell nur für TAB_WALLET genutzt, funktioniert aber für
     * jeden künftigen Extension-Tab gleichermaßen.
     */
    private void renderExtensionTab(GuiGraphics graphics, int mouseX, int mouseY) {
        CompanionTabExtension ext = CompanionExtensions.getTab(currentTab);
        if (ext == null) {
            drawScaledBoldText(graphics,
                tr("cobblecompanion.gui.coming_soon", tr(TAB_NAMES[currentTab])),
                guiLeft + 25, guiTop + 30, DEFAULT_TEXT_SCALE, 0x000000);
            return;
        }
        ext.render(graphics, mouseX, mouseY, 0f, tabContext());
    }

    private void renderHomeTab(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        String welcome = tr("cobblecompanion.gui.home.welcome");
        int welcomeWidth = smallLabelWidth(welcome, DEFAULT_TEXT_SCALE, COUNTER_TEXT_BOLD, COUNTER_TEXT_USE_UNIFORM_FONT);
        int welcomeX = guiLeft + (GUI_WIDTH - welcomeWidth) / 2 + 5;
        drawScaledBoldText(graphics, welcome, welcomeX, y, DEFAULT_TEXT_SCALE, 0xFFAA00);
        renderHomeDashboard(graphics, mouseX, mouseY);
        renderHomeGiftPanel(graphics, mouseX, mouseY);
        renderHomeReclaimBadge(graphics, mouseX, mouseY);
        renderHomePendingCobbleDollarsBadge(graphics, mouseX, mouseY);
    }

    /**
     * Bugfix (Nutzer-Fund): Cobbledollars eines offline Spielers können nicht direkt verändert
     * werden (siehe PendingCobbleDollarsManager) - der Schlaue Beobachter sammelt Beträge deshalb
     * an, bis der Spieler sie hier selbst abholt. Klick bucht sofort (kein Bestätigungs-Overlay
     * nötig, nur ein einziger Betrag statt einer Liste wie beim Inventar-Abhol-Badge).
     */
    private void renderHomePendingCobbleDollarsBadge(GuiGraphics graphics, int mouseX, int mouseY) {
        long pending = com.cobblecompanion.client.data.ClientPendingCobbleDollarsHelper.get();
        if (pending == 0) return;
        int badgeX = guiLeft + HOME_PENDING_CD_BADGE_X;
        int badgeY = homePendingCdBadgeY();
        boolean hovered = isInRect(mouseX, mouseY, badgeX, badgeY, HOME_PENDING_CD_BADGE_W, HOME_PENDING_CD_BADGE_H);
        String label = pending > 0
            ? tr("cobblecompanion.gui.home.pending_cobbledollars_badge",
                com.cobblecompanion.integrations.cobbledollars.CobbleDollarsScale.formatRaw(java.math.BigInteger.valueOf(pending)))
            : tr("cobblecompanion.gui.home.pending_cobbledollars_debt_badge",
                com.cobblecompanion.integrations.cobbledollars.CobbleDollarsScale.formatRaw(java.math.BigInteger.valueOf(-pending)));
        int baseColor = pending > 0 ? 0xFF55FF55 : 0xFFFF5555;
        drawSmallLabel(graphics, label, badgeX, badgeY, HOME_PENDING_CD_BADGE_TEXT_SCALE, hovered ? 0xFFFFFF55 : baseColor, true, true);
    }

    private boolean handleHomePendingCobbleDollarsBadgeClick(double mouseX, double mouseY) {
        if (com.cobblecompanion.client.data.ClientPendingCobbleDollarsHelper.get() == 0) return false;
        int badgeX = guiLeft + HOME_PENDING_CD_BADGE_X;
        int badgeY = homePendingCdBadgeY();
        if (isInRect(mouseX, mouseY, badgeX, badgeY, HOME_PENDING_CD_BADGE_W, HOME_PENDING_CD_BADGE_H)) {
            sendToServer(new com.cobblecompanion.network.PendingCobbleDollarsClaimPacket());
            return true;
        }
        return false;
    }

    /** Kleiner klickbarer Hinweis, nur sichtbar wenn eine Abhol-Warteschlange existiert (siehe reclaimOverlayOpen). */
    private void renderHomeReclaimBadge(GuiGraphics graphics, int mouseX, int mouseY) {
        List<ClientGamemodeInventoryHelper.ReclaimEntryView> entries = ClientGamemodeInventoryHelper.getReclaimEntries();
        if (entries.isEmpty()) return;
        int badgeX = guiLeft + HOME_RECLAIM_BADGE_X;
        int badgeY = homeReclaimBadgeY();
        boolean hovered = isInRect(mouseX, mouseY, badgeX, badgeY, HOME_RECLAIM_BADGE_W, HOME_RECLAIM_BADGE_H);
        String label = tr("cobblecompanion.gui.home.reclaim_badge", entries.size());
        drawSmallLabel(graphics, label, badgeX, badgeY, HOME_RECLAIM_BADGE_TEXT_SCALE, hovered ? 0xFFFFFF55 : 0xFFFFAA00, true, true);
    }

    private boolean handleHomeReclaimBadgeClick(double mouseX, double mouseY) {
        if (ClientGamemodeInventoryHelper.getReclaimEntries().isEmpty()) return false;
        int badgeX = guiLeft + HOME_RECLAIM_BADGE_X;
        int badgeY = homeReclaimBadgeY();
        if (isInRect(mouseX, mouseY, badgeX, badgeY, HOME_RECLAIM_BADGE_W, HOME_RECLAIM_BADGE_H)) {
            reclaimOverlayOpen = true;
            return true;
        }
        return false;
    }

    /** Farbcodierung für "X Entwicklungen möglich" nach Nutzer-Vorgabe. */
    private static int homeEvolveCountColor(int count) {
        if (count == 0) return 0x55FF55;
        if (count <= 5) return 0xFFFF55;
        if (count <= 15) return 0xFFAA00;
        if (count <= 30) return 0xFF5555;
        return 0x808080;
    }

    /** Farbcodierung für "X Pokémon müssen noch gefangen werden" nach Nutzer-Vorgabe. */
    private static int homeCatchCountColor(int count) {
        if (count <= 10) return 0x55FF55;
        if (count <= 151) return 0xFFFF55;
        if (count <= 300) return 0xFFAA00;
        if (count <= 600) return 0xFF5555;
        return 0x808080;
    }

    // Klickbare Bereiche des Home-Dashboards (Sprung zu ToDo bzw. Friends) - null solange nicht gerendert.
    private int[] homeTodoEvolveLinkRect;
    private int[] homeTodoCatchLinkRect;
    private int[] homeFriendsLinkRect;

    /**
     * Linke Hälfte des Home-Tabs: Dex-Fortschrittsbalken (Gesehen/Gefangen/Living), ToDo-
     * Kurzübersicht (wie viele Entwicklungen gerade möglich sind / wie viele Pokémon noch
     * gefangen werden müssen - beides anklickbar, springt zum ToDo-Tab) und offene
     * Freundschaftsanfragen (anklickbar, springt zum Friends-Tab).
     */
    private void renderHomeDashboard(GuiGraphics graphics, int mouseX, int mouseY) {
        homeTodoEvolveLinkRect = null;
        homeTodoCatchLinkRect = null;
        homeFriendsLinkRect = null;
        if (!ClientHomeHelper.hasData()) return;

        int x = guiLeft + HOME_DASH_X;
        int y = guiTop + HOME_DASH_Y;
        int totalSpecies = PokemonSpecies.INSTANCE.getSpecies().size();

        y = renderHomeDashBar(graphics, x, y, tr("cobblecompanion.gui.home.seen"), ClientHomeHelper.getSeen(), totalSpecies);
        y += HOME_DASH_BAR_SPACING;
        y = renderHomeDashBar(graphics, x, y, tr("cobblecompanion.gui.home.caught"), ClientHomeHelper.getCaught(), totalSpecies);
        y += HOME_DASH_BAR_SPACING;
        y = renderHomeDashBar(graphics, x, y, tr("cobblecompanion.gui.home.living"), ClientHomeHelper.getLiving(), totalSpecies);

        y += HOME_DASH_TODO_Y_OFFSET;
        int evolveReady = ClientHomeHelper.getEvolveReady();
        String evolveText = tr("cobblecompanion.gui.home.evolutions_ready", evolveReady);
        int evolveW = smallLabelWidth(evolveText, 1.0f, false, true);
        drawSmallLabel(graphics, evolveText, x, y, 1.0f, homeEvolveCountColor(evolveReady), false, true);
        homeTodoEvolveLinkRect = new int[]{x, y, evolveW, 8};
        y += HOME_DASH_TODO_LINE_H;

        int catchNeeded = ClientHomeHelper.getCatchNeeded();
        String catchText = tr("cobblecompanion.gui.home.catch_needed", catchNeeded);
        int catchW = smallLabelWidth(catchText, 1.0f, false, true);
        drawSmallLabel(graphics, catchText, x, y, 1.0f, homeCatchCountColor(catchNeeded), false, true);
        homeTodoCatchLinkRect = new int[]{x, y, catchW, 8};
        y += HOME_DASH_FRIENDS_Y_OFFSET;

        int requestCount = ClientFriendsHelper.getRequests().size();
        if (requestCount > 0) {
            String friendsText = tr("cobblecompanion.gui.home.friend_requests", requestCount);
            int friendsW = smallLabelWidth(friendsText, 1.0f, false, true);
            boolean friendsHovered = isInRect(mouseX, mouseY, x, y, friendsW, 8);
            drawSmallLabel(graphics, friendsText, x, y, 1.0f, friendsHovered ? HOME_DASH_LINK_HOVER_COLOR : HOME_DASH_LINK_COLOR, false, true);
            homeFriendsLinkRect = new int[]{x, y, friendsW, 8};
            y += HOME_DASH_TODO_LINE_H;
        }

        if (ClientSettingsHelper.isPcSlotCheckEnabled() && ClientHomeHelper.hasSlotCheckData()) {
            int misplaced = ClientHomeHelper.getMisplacedCount();
            String slotText = tr("cobblecompanion.gui.home.pc_misplaced", misplaced);
            int slotColor = misplaced == 0 ? 0x55FF55 : (misplaced <= 10 ? 0xFFFF55 : 0xFF5555);
            drawSmallLabel(graphics, slotText, x, y, 1.0f, slotColor, false, true);
        }
    }

    /** Zeichnet eine einzelne Fortschrittsleiste mit Label darüber, gibt die Y-Position unter der Leiste zurück. */
    private int renderHomeDashBar(GuiGraphics graphics, int x, int y, String label, int value, int total) {
        String labelText = tr("cobblecompanion.gui.home.bar_label", label, value, total);
        drawSmallLabel(graphics, labelText, x, y + HOME_DASH_LABEL_OFFSET_Y, 1.0f, 0xFFFFFF, true, true);

        graphics.fill(x - 1, y - 1, x + HOME_DASH_BAR_W + 1, y + HOME_DASH_BAR_H + 1, HOME_DASH_BAR_BORDER_COLOR);
        // Hintergrund an den Look unserer Suchleisten angelehnt: dunkles, leicht bläuliches
        // Overlay statt hart-schwarz + dünner Teal-Akzentrand oben/unten (Nutzer-Feedback: der
        // alte Kontrast war zu stark für ihre Freundin).
        graphics.fill(x, y, x + HOME_DASH_BAR_W, y + HOME_DASH_BAR_H, HOME_DASH_BAR_BG_COLOR);
        graphics.fill(x, y, x + HOME_DASH_BAR_W, y + 1, HOME_DASH_BAR_ACCENT_COLOR);
        graphics.fill(x, y + HOME_DASH_BAR_H - 1, x + HOME_DASH_BAR_W, y + HOME_DASH_BAR_H, HOME_DASH_BAR_ACCENT_COLOR);

        float pct = total > 0 ? Math.min(1f, value / (float) total) : 0f;
        int fillW = Math.round(HOME_DASH_BAR_W * pct);
        if (fillW > 0) graphics.fill(x, y, x + fillW, y + HOME_DASH_BAR_H, HOME_DASH_BAR_FILL_COLOR);

        // Prozent-Text am Ende der Leiste, rechtsbündig - über dem noch unausgefüllten Bereich
        // weiß, über dem bereits ausgefüllten (grünen) Bereich grau für besseren Kontrast. Der
        // Textbereich wird per Scissor exakt an der Füllgrenze zweigeteilt.
        int percent = Math.round(pct * 100f);
        String percentText = percent + "%";
        int textW = smallLabelWidth(percentText, 1.0f, true, true);
        int textX = x + HOME_DASH_BAR_W - textW - 2;
        int textY = y + (HOME_DASH_BAR_H - 8) / 2;
        int fillEndX = x + fillW;

        if (textX < fillEndX) {
            graphics.enableScissor(x, y, Math.min(fillEndX, x + HOME_DASH_BAR_W), y + HOME_DASH_BAR_H);
            drawSmallLabel(graphics, percentText, textX, textY, 1.0f, HOME_DASH_BAR_PERCENT_FILLED_COLOR, true, true);
            graphics.disableScissor();
        }
        if (fillEndX < x + HOME_DASH_BAR_W) {
            graphics.enableScissor(Math.max(fillEndX, x), y, x + HOME_DASH_BAR_W, y + HOME_DASH_BAR_H);
            drawSmallLabel(graphics, percentText, textX, textY, 1.0f, HOME_DASH_BAR_PERCENT_COLOR, true, true);
            graphics.disableScissor();
        }
        return y + HOME_DASH_BAR_H;
    }

    /**
     * ldpCategories für DexCompletionRequestPacket - NUR im Living-Dex+-Modus nicht-leer (siehe
     * DexCompletionHelper.compute()-Doc), sonst identisch zum reinen Pokédex-/Living-Dex-Fall.
     */
    private List<Integer> dexCompletionLdpCategories() {
        return ClientSettingsHelper.isPcSortModeLivingDexPlus()
            ? ClientSettingsHelper.getLivingDexPlusCategoryOrder() : List.of();
    }

    /** Springt zum angegebenen Tab und stößt (wie ein Klick auf das jeweilige Tab-Icon) dessen Datenabfrage an. */
    private void jumpToTab(int tab) {
        if (tab == TAB_TODO) {
            sendToServer(new TodoRequestPacket());
            sendToServer(new com.cobblecompanion.network.DexCompletionRequestPacket(
                ClientSettingsHelper.isModusPokedex(), dexCompletionLdpCategories()));
        } else if (tab == TAB_FRIENDS) {
            sendToServer(new FriendsListRequestPacket());
            sendToServer(new TeleportPreferencePacket(ClientSettingsHelper.isFriendsAllowTeleportToMe()));
        }
        currentTab = tab;
    }

    /** Klicks auf die Dashboard-Sprunglinks (Entwicklungen/Fangen -> ToDo, Freundschaftsanfragen -> Friends). */
    private boolean handleHomeDashboardClicks(double mouseX, double mouseY) {
        if (homeTodoEvolveLinkRect != null && isInRect(mouseX, mouseY,
                homeTodoEvolveLinkRect[0], homeTodoEvolveLinkRect[1], homeTodoEvolveLinkRect[2], homeTodoEvolveLinkRect[3])) {
            jumpToTab(TAB_TODO);
            return true;
        }
        if (homeTodoCatchLinkRect != null && isInRect(mouseX, mouseY,
                homeTodoCatchLinkRect[0], homeTodoCatchLinkRect[1], homeTodoCatchLinkRect[2], homeTodoCatchLinkRect[3])) {
            jumpToTab(TAB_TODO);
            return true;
        }
        if (homeFriendsLinkRect != null && isInRect(mouseX, mouseY,
                homeFriendsLinkRect[0], homeFriendsLinkRect[1], homeFriendsLinkRect[2], homeFriendsLinkRect[3])) {
            jumpToTab(TAB_FRIENDS);
            return true;
        }
        return false;
    }

    // ===== Such-Tab =====

    /**
     * Ein Vorschlag im Such-Tab: "in diesem Tab gibt es etwas zum gesuchten Begriff" - Klick
     * führt die übergebene Navigations-Aktion aus (setzt das jeweilige Ziel-Suchfeld, löst dessen
     * Anfrage aus und wechselt den Tab).
     */
    private static final class SearchNavSuggestion {
        final String tabLabel;
        final String term;
        final Runnable action;
        SearchNavSuggestion(String tabLabel, String term, Runnable action) {
            this.tabLabel = tabLabel;
            this.term = term;
            this.action = action;
        }
    }

    /**
     * Baut die Vorschlagsliste "wo finde ich das" für die aktuelle Sucheingabe - deckt Pokémon-,
     * Typ- und Freund-Namen ab und verlinkt auf Types/WhoNeeds/ToDo (Vervollständigungshilfe) bzw.
     * Friends, weil deren Suchfelder/Query-Sender bereits programmatisch ansteuerbar sind. Der
     * Pokédex-/Living-Dex-/Professor-Tab nutzen Cobblemons eigene, nicht von außen ansteuerbare
     * Such-Widgets und werden deshalb bewusst NICHT als Sprungziel angeboten.
     */
    private List<SearchNavSuggestion> searchTabSuggestions() {
        List<SearchNavSuggestion> result = new java.util.ArrayList<>();
        String q = searchTabSearchBox.getValue().trim().toLowerCase();
        if (q.isEmpty()) return result;

        String typesLabel = tr("cobblecompanion.tab.types");
        String whoNeedsLabel = tr("cobblecompanion.tab.whoneeds");
        String todoLabel = tr("cobblecompanion.tab.todo");
        String friendsLabel = tr("cobblecompanion.tab.friends");

        int speciesMatched = 0;
        for (Species s : PokemonSpecies.INSTANCE.getSpecies()) {
            if (result.size() >= SEARCH_TAB_MAX_SUGGESTIONS) break;
            String display = speciesDisplayName(s);
            if (!display.toLowerCase().startsWith(q)) continue;
            speciesMatched++;
            if (speciesMatched > 8) break;

            result.add(new SearchNavSuggestion(typesLabel, display, () -> {
                typeSearchBox.setValue(display);
                sendTypeRequest(display);
                currentTab = TAB_TYPES;
            }));
            result.add(new SearchNavSuggestion(whoNeedsLabel, display, () -> {
                whoNeedsSearchBox.setValue(display);
                sendWhoNeedsQuery(display);
                currentTab = TAB_WHONEEDS;
            }));
            result.add(new SearchNavSuggestion(todoLabel, display, () -> {
                jumpToTab(TAB_TODO);
                dexHelpSearchBox.setValue(display);
                sendDexHelpSearch(display);
            }));
        }

        for (String type : TYPE_ORDER) {
            if (result.size() >= SEARCH_TAB_MAX_SUGGESTIONS) break;
            String display = tr("cobblemon.type." + type);
            if (!display.toLowerCase().startsWith(q)) continue;
            result.add(new SearchNavSuggestion(typesLabel, display, () -> {
                typeSearchBox.setValue(display);
                sendTypeRequest(display);
                currentTab = TAB_TYPES;
            }));
        }

        for (ClientFriendsHelper.FriendItem f : ClientFriendsHelper.getFriends()) {
            if (result.size() >= SEARCH_TAB_MAX_SUGGESTIONS) break;
            if (!f.name.toLowerCase().startsWith(q)) continue;
            String name = f.name;
            result.add(new SearchNavSuggestion(friendsLabel, name, () -> {
                jumpToTab(TAB_FRIENDS);
                friendsSearchBox.setValue(name);
            }));
        }

        return result;
    }

    private int searchTabVisibleHeight() {
        return (guiTop + GUI_HEIGHT - SEARCH_TAB_BOTTOM_MARGIN) - (guiTop + SEARCH_TAB_LIST_Y);
    }

    private int searchTabMaxScroll(int count) {
        return Math.max(0, count * SEARCH_TAB_ROW_H - searchTabVisibleHeight());
    }

    private void renderSearchTab(GuiGraphics graphics, int mouseX, int mouseY) {
        searchTabSearchBox.render(graphics, mouseX, mouseY, 0f);

        List<SearchNavSuggestion> suggestions = searchTabSuggestions();
        int listX = guiLeft + SEARCH_TAB_X;
        int listTop = guiTop + SEARCH_TAB_LIST_Y;
        int visibleHeight = searchTabVisibleHeight();
        int maxScroll = searchTabMaxScroll(suggestions.size());
        searchTabScrollAmount = Math.max(0, Math.min(maxScroll, searchTabScrollAmount));

        if (searchTabSearchBox.getValue().isBlank()) {
            drawScaledBoldText(graphics, tr("cobblecompanion.gui.search.hint_empty_line1"), listX, listTop, DEFAULT_TEXT_SCALE, 0x808080);
            drawScaledBoldText(graphics, tr("cobblecompanion.gui.search.hint_empty_line2"), listX, listTop + 10, DEFAULT_TEXT_SCALE, 0x808080);
        } else if (suggestions.isEmpty()) {
            drawScaledBoldText(graphics, tr("cobblecompanion.gui.search.none_found"), listX, listTop, DEFAULT_TEXT_SCALE, 0x808080);
        } else {
            graphics.enableScissor(listX, listTop, listX + SEARCH_TAB_SEARCH_W, listTop + visibleHeight);
            int y = listTop - (int) Math.round(searchTabScrollAmount);
            for (SearchNavSuggestion s : suggestions) {
                if (y + SEARCH_TAB_ROW_H >= listTop && y <= listTop + visibleHeight) {
                    renderSearchTabRow(graphics, mouseX, mouseY, listX, y, s);
                }
                y += SEARCH_TAB_ROW_H;
            }
            graphics.disableScissor();

            if (maxScroll > 0) {
                int scrollbarX = listX + SEARCH_TAB_SEARCH_W + SEARCH_TAB_SCROLLBAR_GAP;
                renderScrollbar(graphics, scrollbarX, SEARCH_TAB_SCROLLBAR_WIDTH, listTop, visibleHeight, searchTabScrollAmount, maxScroll);
            }
        }

        renderSearchHistoryPanel(graphics, mouseX, mouseY);
    }

    private void renderSearchTabRow(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, SearchNavSuggestion s) {
        boolean hovered = isInRect(mouseX, mouseY, x, y, SEARCH_TAB_SEARCH_W, SEARCH_TAB_ROW_H);
        String tabPart = s.tabLabel + ": ";
        int tabPartW = smallLabelWidth(tabPart, 1.0f, false, true);
        int color1 = hovered ? SEARCH_TAB_ROW_HOVER_COLOR : SEARCH_TAB_ROW_TAB_COLOR;
        int color2 = hovered ? SEARCH_TAB_ROW_HOVER_COLOR : SEARCH_TAB_ROW_TERM_COLOR;
        drawSmallLabel(graphics, tabPart, x, y, 1.0f, color1, false, true);
        drawSmallLabel(graphics, s.term, x + tabPartW, y, 1.0f, color2, false, true);
    }

    /** Rechte Hälfte: zuletzt verwendete Suchbegriffe, ohne Scrollbar (feste Kapazität, siehe ClientSearchHistoryHelper). */
    private void renderSearchHistoryPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        List<String> history = com.cobblecompanion.client.data.ClientSearchHistoryHelper.getHistory();
        if (history.isEmpty()) return;

        int x = guiLeft + SEARCH_HISTORY_X;
        int y = guiTop + SEARCH_HISTORY_Y;
        drawScaledBoldText(graphics, tr("cobblecompanion.gui.search.history_heading"), x, y, DEFAULT_TEXT_SCALE, 0xFFAA00);
        y += SEARCH_HISTORY_ROW_H;

        for (String term : history) {
            boolean hovered = isInRect(mouseX, mouseY, x, y, SEARCH_TAB_SEARCH_W, SEARCH_HISTORY_ROW_H);
            drawSmallLabel(graphics, term, x, y, 1.0f, hovered ? SEARCH_HISTORY_ROW_HOVER_COLOR : SEARCH_HISTORY_ROW_COLOR, false, true);
            y += SEARCH_HISTORY_ROW_H;
        }
    }

    /** Klicks auf Vorschlagszeilen, die Scrollbar und die Verlaufsliste. */
    private boolean handleSearchTabClicks(double mouseX, double mouseY) {
        List<SearchNavSuggestion> suggestions = searchTabSuggestions();
        int listX = guiLeft + SEARCH_TAB_X;
        int listTop = guiTop + SEARCH_TAB_LIST_Y;
        int visibleHeight = searchTabVisibleHeight();
        int maxScroll = searchTabMaxScroll(suggestions.size());

        if (maxScroll > 0) {
            int scrollbarX = listX + SEARCH_TAB_SEARCH_W + SEARCH_TAB_SCROLLBAR_GAP;
            if (isMouseOverScrollbar(mouseX, mouseY, scrollbarX, SEARCH_TAB_SCROLLBAR_WIDTH, listTop, visibleHeight)) {
                searchTabScrollbarDragging = true;
                searchTabScrollAmount = scrollAmountFromMouseY(mouseY, listTop, visibleHeight, maxScroll);
                return true;
            }
        }

        int y = listTop - (int) Math.round(searchTabScrollAmount);
        for (SearchNavSuggestion s : suggestions) {
            if (y >= listTop && y <= listTop + visibleHeight
                    && isInRect(mouseX, mouseY, listX, y, SEARCH_TAB_SEARCH_W, SEARCH_TAB_ROW_H)) {
                com.cobblecompanion.client.data.ClientSearchHistoryHelper.addSearch(searchTabSearchBox.getValue());
                s.action.run();
                return true;
            }
            y += SEARCH_TAB_ROW_H;
        }

        // Verlaufsliste: Klick füllt das Suchfeld erneut mit dem Begriff (löst KEINE Navigation
        // aus - der Nutzer sieht dann wieder dieselben "wo finde ich das"-Vorschläge wie vorher).
        List<String> history = com.cobblecompanion.client.data.ClientSearchHistoryHelper.getHistory();
        if (!history.isEmpty()) {
            int hx = guiLeft + SEARCH_HISTORY_X;
            int hy = guiTop + SEARCH_HISTORY_Y + SEARCH_HISTORY_ROW_H;
            for (String term : history) {
                if (isInRect(mouseX, mouseY, hx, hy, SEARCH_TAB_SEARCH_W, SEARCH_HISTORY_ROW_H)) {
                    searchTabSearchBox.setValue(term);
                    searchTabSearchBox.setCursorPosition(term.length());
                    return true;
                }
                hy += SEARCH_HISTORY_ROW_H;
            }
        }
        return false;
    }

    // ===== Team-Builder-Tab =====

    /**
     * Layout der 3 Modus-Buttons: Zeile 1 = "Type"+"Team" nebeneinander (schmaler), Zeile 2 =
     * "Allgemein" mittig darunter (gleiche Gesamtbreite wie Zeile 1, daher automatisch zentriert).
     */
    private static final int TEAMBUILDER_ROW1_BTN_W = (TEAMBUILDER_BTN_W - TEAMBUILDER_BTN_GAP_Y) / 2;
    // JUSTIERSCHRAUBE: Buttons mittig der linken Hälfte (Nutzer-Wunsch) - eigener X-Versatz NUR
    // für die 3 Modus-Buttons, Typ-Liste/Gegner-Gitter bleiben bei TEAMBUILDER_LEFT_X.
    private static final int TEAMBUILDER_BTN_X_OFFSET = 20;

    private void renderTeamBuilderTab(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = guiLeft + TEAMBUILDER_LEFT_X;
        int y = guiTop + TEAMBUILDER_LEFT_Y;
        int btnX = x + TEAMBUILDER_BTN_X_OFFSET;

        // Zeile 1: "Allgemein" (breit) - Zeile 2: "Type"/"Team" (schmales Paar). Vertauscht auf
        // Nutzer-Wunsch gegenüber der vorherigen Anordnung.
        renderTeamBuilderModeButton(graphics, mouseX, mouseY, btnX, y,
            tr("cobblecompanion.gui.teambuilder.general"), teamBuilderMode == 0);

        int row2Y = y + TEAMBUILDER_BTN_H + TEAMBUILDER_BTN_GAP_Y;
        int typeX = btnX;
        int teamX = btnX + TEAMBUILDER_ROW1_BTN_W + TEAMBUILDER_BTN_GAP_Y;
        renderTeamBuilderModeButtonW(graphics, mouseX, mouseY, typeX, row2Y, TEAMBUILDER_ROW1_BTN_W,
            tr("cobblecompanion.gui.teambuilder.type"), teamBuilderMode == 1);
        renderTeamBuilderModeButtonW(graphics, mouseX, mouseY, teamX, row2Y, TEAMBUILDER_ROW1_BTN_W,
            tr("cobblecompanion.gui.teambuilder.team"), teamBuilderMode == 2);

        if (teamBuilderMode == 1) {
            renderTeamBuilderTypeList(graphics, mouseX, mouseY, x, teamBuilderTypeListY());
        } else if (teamBuilderMode == 2) {
            renderTeamBuilderOpponentGrid(graphics, mouseX, mouseY, x, teamBuilderOppGridY());
        }

        renderTeamBuilderResult(graphics);
    }

    /** Basis-Y-Position unter den 2 Button-Zeilen, bevor modus-eigene Justierschrauben angewendet werden. */
    private int teamBuilderSubYBase() {
        int y = guiTop + TEAMBUILDER_LEFT_Y;
        int row2Y = y + TEAMBUILDER_BTN_H + TEAMBUILDER_BTN_GAP_Y;
        return row2Y + TEAMBUILDER_BTN_H + TEAMBUILDER_SUB_Y_OFFSET;
    }

    /** Y-Position der Typ-Liste ("Type"-Modus) - Justierschraube: -10 (Runde 1) +15 (Runde 2) = +5. */
    private int teamBuilderTypeListY() {
        return teamBuilderSubYBase() + 5;
    }

    /** Y-Position des Gegner-Eingabegitters ("Team"-Modus) - Justierschraube: -10 (Runde 2) +10 (Runde 3) = 0. */
    private int teamBuilderOppGridY() {
        return teamBuilderSubYBase();
    }

    private void renderTeamBuilderModeButton(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, String label, boolean active) {
        renderTeamBuilderModeButtonW(graphics, mouseX, mouseY, x, y, TEAMBUILDER_BTN_W, label, active);
    }

    private void renderTeamBuilderModeButtonW(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, String label, boolean active) {
        boolean hovered = isInRect(mouseX, mouseY, x, y, w, TEAMBUILDER_BTN_H);
        graphics.blit(TODO_EVOLVE_BUTTON, x, y, w, TEAMBUILDER_BTN_H,
            0f, hovered ? SETTINGS_BTN_NATIVE_H : 0f,
            SETTINGS_BTN_NATIVE_W, SETTINGS_BTN_NATIVE_H, SETTINGS_BTN_NATIVE_W, SETTINGS_BTN_NATIVE_H * 2);
        if (active) graphics.fill(x, y, x + w, y + TEAMBUILDER_BTN_H, SETTINGS_SUBTAB_ACTIVE_FILL);
        int labelWidth = smallLabelWidth(label, 1.0f, true, true);
        drawSmallLabel(graphics, label, x + (w - labelWidth) / 2, y + (TEAMBUILDER_BTN_H - 8) / 2, 1.0f, 0xFFFFFF, true, true);
    }

    /** "Type"-Modus: alle 18 Typen als 3-spaltige, anklickbare Liste mit Typ-Icon + typfarbenem Text. */
    private void renderTeamBuilderTypeList(GuiGraphics graphics, int mouseX, int mouseY, int x, int y) {
        for (int i = 0; i < TYPE_ORDER.length; i++) {
            String type = TYPE_ORDER[i];
            int col = i % TEAMBUILDER_TYPE_COLS;
            int row = i / TEAMBUILDER_TYPE_COLS;
            int rowX = x + col * TEAMBUILDER_TYPE_COL_W;
            int rowY = y + row * TEAMBUILDER_TYPE_ROW_H;

            boolean selected = type.equals(teamBuilderSelectedType);
            boolean hovered = isInRect(mouseX, mouseY, rowX, rowY, TEAMBUILDER_TYPE_COL_W, TEAMBUILDER_TYPE_ROW_H);
            if (selected || hovered) {
                graphics.fill(rowX - 1, rowY - 1, rowX + TEAMBUILDER_TYPE_COL_W - 1, rowY + TEAMBUILDER_TYPE_ROW_H - 1,
                    selected ? 0x60FFFF55 : 0x30FFFFFF);
            }

            graphics.blit(TYPE_ICONS_SHEET, rowX, rowY, TYPE_GRID_ICON_SIZE, TYPE_GRID_ICON_SIZE,
                (float) (i * TYPE_ICON_SRC_SIZE), 0f, TYPE_ICON_SRC_SIZE, TYPE_ICON_SRC_SIZE,
                TYPE_ICON_SHEET_W, TYPE_ICON_SHEET_H);

            String display = tr("cobblemon.type." + type);
            int color = TYPE_TEXT_COLORS.getOrDefault(type, 0xFFFFFF);
            drawSmallLabel(graphics, display, rowX + TYPE_GRID_ICON_SIZE + TEAMBUILDER_TYPE_ICON_TEXT_GAP, rowY + 1, 1.0f, color, false, true);
        }
    }

    /** "Team"-Modus: 2x6-Eingabegitter (Name+Level je Zeile) + Zeile-Leeren-Button je Zeile + Berechnen-Button. */
    private void renderTeamBuilderOpponentGrid(GuiGraphics graphics, int mouseX, int mouseY, int x, int y) {
        drawSmallLabel(graphics, tr("cobblecompanion.gui.teambuilder.opponent_intro"),
            x, y + TEAMBUILDER_OPP_HINT_OFFSET_Y, 1.0f, 0x808080, false, true);

        for (int row = 0; row < 6; row++) {
            int rowY = y + row * TEAMBUILDER_OPP_ROW_H;
            renderTeamBuilderField(graphics, x, rowY, TEAMBUILDER_OPP_NAME_W, TEAMBUILDER_OPP_NAME_H,
                tbOpponentName[row], tbFocusedField == row * 2, tr("cobblecompanion.gui.teambuilder.opponent_hint"));
            int levelX = x + TEAMBUILDER_OPP_NAME_W + TEAMBUILDER_OPP_LEVEL_GAP;
            renderTeamBuilderField(graphics, levelX, rowY, TEAMBUILDER_OPP_LEVEL_W, TEAMBUILDER_OPP_NAME_H,
                tbOpponentLevel[row], tbFocusedField == row * 2 + 1, "Lv");

            // Rotes "✗" ohne Rahmen/Hintergrund - gleicher Look wie der Freund-Löschen-Button im
            // Friends-Tab (drawScaledBoldText mit "✗", 0xFFFF5555), statt eines Buttons mit Box.
            drawScaledBoldText(graphics, "✗", teamBuilderClearColumnX(x), rowY + TEAMBUILDER_OPP_TEXT_OFFSET_Y, BODY_TEXT_SCALE, 0xFFFF5555);
        }

        int btnY = y + 6 * TEAMBUILDER_OPP_ROW_H + TEAMBUILDER_OPP_CONFIRM_GAP_Y;
        boolean hovered = isInRect(mouseX, mouseY, x, btnY, TEAMBUILDER_OPP_CONFIRM_BTN_W, TEAMBUILDER_OPP_CONFIRM_BTN_H);
        graphics.blit(TODO_EVOLVE_BUTTON, x, btnY, TEAMBUILDER_OPP_CONFIRM_BTN_W, TEAMBUILDER_OPP_CONFIRM_BTN_H,
            0f, hovered ? SETTINGS_BTN_NATIVE_H : 0f,
            SETTINGS_BTN_NATIVE_W, SETTINGS_BTN_NATIVE_H, SETTINGS_BTN_NATIVE_W, SETTINGS_BTN_NATIVE_H * 2);
        String label = tr("cobblecompanion.gui.teambuilder.calculate");
        int labelWidth = smallLabelWidth(label, 1.0f, true, true);
        drawSmallLabel(graphics, label, x + (TEAMBUILDER_OPP_CONFIRM_BTN_W - labelWidth) / 2,
            btnY + (TEAMBUILDER_OPP_CONFIRM_BTN_H - 8) / 2, 1.0f, 0xFFFFFF, true, true);

        // "Alles leeren": ohne Text, rechts neben "Berechnen", in derselben Spalte wie die
        // Zeilen-"✗"-Buttons (Nutzer-Wunsch) statt darunter mit Textlabel.
        drawScaledBoldText(graphics, "✗", teamBuilderClearColumnX(x),
            btnY + (TEAMBUILDER_OPP_CONFIRM_BTN_H - 8) / 2, BODY_TEXT_SCALE, 0xFFFF5555);
    }

    /** X-Spalte der Zeilen-"✗"-Buttons (und des "Alles leeren"-Buttons, gleiche Spalte). */
    private int teamBuilderClearColumnX(int x) {
        int levelX = x + TEAMBUILDER_OPP_NAME_W + TEAMBUILDER_OPP_LEVEL_GAP;
        return levelX + TEAMBUILDER_OPP_LEVEL_W + TEAMBUILDER_OPP_CLEAR_GAP;
    }

    private void renderTeamBuilderField(GuiGraphics graphics, int x, int y, int w, int h, String value, boolean focused, String placeholder) {
        graphics.fill(x, y, x + w, y + h, TEAMBUILDER_OPP_FIELD_BG);
        int border = focused ? TEAMBUILDER_OPP_FIELD_BORDER_FOCUS : TEAMBUILDER_OPP_FIELD_BORDER;
        graphics.fill(x, y, x + w, y + 1, border);
        graphics.fill(x, y + h - 1, x + w, y + h, border);
        graphics.fill(x, y, x + 1, y + h, border);
        graphics.fill(x + w - 1, y, x + w, y + h, border);
        String text = value.isBlank() ? placeholder : value;
        int color = value.isBlank() ? 0x808080 : 0xFFFFFF;
        drawSmallLabel(graphics, text, x + TEAMBUILDER_OPP_TEXT_OFFSET_X, y + TEAMBUILDER_OPP_TEXT_OFFSET_Y, 1.0f, color, false, true);
    }

    /**
     * Rechte Hälfte: Ergebnis-Team als vertikale Liste (statt des früheren 3x2-Slot-Rasters -
     * die neuen Grund-Zeilen je Pokemon brauchen Textbreite, die ein 3-Spalten-Raster nicht
     * hätte). Darunter, falls vorhanden, eine zweite Liste mit Alternativ-Vorschlägen.
     */
    private void renderTeamBuilderResult(GuiGraphics graphics) {
        int baseX = guiLeft + TEAMBUILDER_RESULT_X;
        int baseY = guiTop + TEAMBUILDER_RESULT_Y;

        if (!ClientTeamBuilderHelper.hasResult()) {
            if (teamBuilderMode != -1) {
                drawScaledBoldText(graphics, tr("cobblecompanion.gui.teambuilder.hint"), baseX, baseY, DEFAULT_TEXT_SCALE, 0x808080);
            }
            return;
        }
        if (ClientTeamBuilderHelper.getResult().isEmpty()) {
            drawScaledBoldText(graphics, tr("cobblecompanion.gui.teambuilder.none"), baseX, baseY, DEFAULT_TEXT_SCALE, 0x808080);
            return;
        }

        if (ClientTeamBuilderHelper.getVersion() != teamBuilderExpandedResultVersion) {
            teamBuilderExpandedEntries.clear();
            teamBuilderExpandedResultVersion = ClientTeamBuilderHelper.getVersion();
            teamBuilderResultScroll = 0;
        }

        List<TeamBuilderRow> rows = buildTeamBuilderRows();
        int visibleHeight = teamBuilderResultVisibleHeight();
        int maxScroll = teamBuilderResultMaxScroll(rows);
        teamBuilderResultScroll = Math.max(0, Math.min(maxScroll, teamBuilderResultScroll));

        graphics.enableScissor(baseX, baseY, baseX + TEAMBUILDER_RESULT_W, baseY + visibleHeight);
        int y = baseY - (int) Math.round(teamBuilderResultScroll);
        for (TeamBuilderRow row : rows) {
            int rowH = teamBuilderRowHeight(row);
            if (y + rowH >= baseY && y <= baseY + visibleHeight) {
                if (row.heading) {
                    drawSmallLabel(graphics, tr("cobblecompanion.gui.teambuilder.alternatives"), baseX, y, 1.0f, SETTINGS_HEADING_COLOR, true, true);
                } else {
                    renderTeamBuilderEntry(graphics, baseX, y, row.entry, teamBuilderExpandedEntries.contains(row.entryIndex));
                }
            }
            y += rowH;
        }
        graphics.disableScissor();

        if (maxScroll > 0) {
            int scrollbarX = baseX + TEAMBUILDER_RESULT_W + TEAMBUILDER_SCROLLBAR_GAP;
            renderScrollbar(graphics, scrollbarX, TEAMBUILDER_SCROLLBAR_WIDTH, baseY, visibleHeight, teamBuilderResultScroll, maxScroll);
        }
    }

    /** Zeichnet EINEN Ergebnis-Eintrag (Icon links, Name+optional Gründe rechts). */
    private void renderTeamBuilderEntry(GuiGraphics graphics, int x, int y, ClientTeamBuilderHelper.Entry e, boolean expanded) {
        java.util.Set<String> aspects = e.aspects.isBlank() ? java.util.Set.of() : java.util.Set.of(e.aspects.split(","));
        renderPokemonNumberedSlot(graphics, x, y, e.speciesId, aspects, e.level);

        int textX = x + TEAMBUILDER_RESULT_ICON_SIZE + TEAMBUILDER_RESULT_TEXT_GAP_X;
        Species species = PokemonSpecies.INSTANCE.getByIdentifier(e.speciesId);
        String name = species != null ? speciesDisplayName(species) : e.speciesId.getPath();
        // Ausklapp-Pfeil (wie ToDo-Tab) nur wenn es überhaupt Gründe gibt.
        String prefix = e.reasons.isEmpty() ? "" : (expanded ? "▼ " : "▶ ");
        drawSmallLabel(graphics, prefix + name, textX, y + TEAMBUILDER_RESULT_NAME_OFFSET_Y, 1.0f, 0xFFFFFF, false, true);

        if (expanded) {
            int reasonY = y + TEAMBUILDER_RESULT_REASON_OFFSET_Y;
            for (String code : e.reasons) {
                drawSmallLabel(graphics, teamBuilderReasonText(code), textX, reasonY, 1.0f, 0xAAAAAA, false, true);
                reasonY += TEAMBUILDER_RESULT_REASON_LINE_H;
            }
        }
    }

    /** Übersetzt einen Reason-Code (siehe TeamBuilderHelper) in lesbaren, übersetzten Text - gleiches Prinzip wie beim Types-Tab. */
    private String teamBuilderReasonText(String code) {
        int sep = code.indexOf(':');
        if (sep < 0) return code;
        String kind = code.substring(0, sep);
        String type = code.substring(sep + 1);
        String typeName = tr("cobblemon.type." + type);
        return switch (kind) {
            case "OFF" -> tr("cobblecompanion.gui.teambuilder.reason_off", typeName);
            case "DEF" -> tr("cobblecompanion.gui.teambuilder.reason_def", typeName);
            case "RES" -> tr("cobblecompanion.gui.teambuilder.reason_res", typeName);
            case "SE" -> tr("cobblecompanion.gui.teambuilder.reason_se", typeName);
            default -> code;
        };
    }

    /** Klicks auf Modus-Buttons, Typ-Liste, Eingabefelder und den Berechnen-Button. */
    private boolean handleTeamBuilderClicks(double mouseX, double mouseY) {
        int x = guiLeft + TEAMBUILDER_LEFT_X;
        int y = guiTop + TEAMBUILDER_LEFT_Y;
        int btnX = x + TEAMBUILDER_BTN_X_OFFSET;

        if (isInRect(mouseX, mouseY, btnX, y, TEAMBUILDER_BTN_W, TEAMBUILDER_BTN_H)) {
            teamBuilderMode = 0;
            tbFocusedField = -1;
            sendToServer(new com.cobblecompanion.network.TeamBuilderRequestPacket(0, "", List.of()));
            return true;
        }
        int row2Y = y + TEAMBUILDER_BTN_H + TEAMBUILDER_BTN_GAP_Y;
        int typeX = btnX;
        int teamX = btnX + TEAMBUILDER_ROW1_BTN_W + TEAMBUILDER_BTN_GAP_Y;
        if (isInRect(mouseX, mouseY, typeX, row2Y, TEAMBUILDER_ROW1_BTN_W, TEAMBUILDER_BTN_H)) {
            teamBuilderMode = 1;
            tbFocusedField = -1;
            return true;
        }
        if (isInRect(mouseX, mouseY, teamX, row2Y, TEAMBUILDER_ROW1_BTN_W, TEAMBUILDER_BTN_H)) {
            teamBuilderMode = 2;
            tbFocusedField = -1;
            return true;
        }
        if (teamBuilderMode == 1) {
            int subY = teamBuilderTypeListY();
            for (int i = 0; i < TYPE_ORDER.length; i++) {
                int col = i % TEAMBUILDER_TYPE_COLS;
                int row = i / TEAMBUILDER_TYPE_COLS;
                int rowX = x + col * TEAMBUILDER_TYPE_COL_W;
                int rowY = subY + row * TEAMBUILDER_TYPE_ROW_H;
                if (isInRect(mouseX, mouseY, rowX, rowY, TEAMBUILDER_TYPE_COL_W, TEAMBUILDER_TYPE_ROW_H)) {
                    teamBuilderSelectedType = TYPE_ORDER[i];
                    sendToServer(new com.cobblecompanion.network.TeamBuilderRequestPacket(1, teamBuilderSelectedType, List.of()));
                    return true;
                }
            }
        } else if (teamBuilderMode == 2) {
            int subY = teamBuilderOppGridY();
            for (int row = 0; row < 6; row++) {
                int rowY = subY + row * TEAMBUILDER_OPP_ROW_H;
                if (isInRect(mouseX, mouseY, x, rowY, TEAMBUILDER_OPP_NAME_W, TEAMBUILDER_OPP_NAME_H)) {
                    tbFocusedField = row * 2;
                    return true;
                }
                int levelX = x + TEAMBUILDER_OPP_NAME_W + TEAMBUILDER_OPP_LEVEL_GAP;
                if (isInRect(mouseX, mouseY, levelX, rowY, TEAMBUILDER_OPP_LEVEL_W, TEAMBUILDER_OPP_NAME_H)) {
                    tbFocusedField = row * 2 + 1;
                    return true;
                }
                int clearX = teamBuilderClearColumnX(x);
                if (isInRect(mouseX, mouseY, clearX, rowY, TEAMBUILDER_OPP_CLEAR_BTN_W, TEAMBUILDER_OPP_NAME_H)) {
                    tbOpponentName[row] = "";
                    tbOpponentLevel[row] = "";
                    if (tbFocusedField == row * 2 || tbFocusedField == row * 2 + 1) tbFocusedField = -1;
                    return true;
                }
            }
            int confirmY = subY + 6 * TEAMBUILDER_OPP_ROW_H + TEAMBUILDER_OPP_CONFIRM_GAP_Y;
            if (isInRect(mouseX, mouseY, x, confirmY, TEAMBUILDER_OPP_CONFIRM_BTN_W, TEAMBUILDER_OPP_CONFIRM_BTN_H)) {
                sendTeamBuilderOpponentRequest();
                return true;
            }
            // "Alles leeren": jetzt neben (statt unter) "Berechnen", gleiche Spalte wie Zeilen-"✗".
            int clearAllX = teamBuilderClearColumnX(x);
            if (isInRect(mouseX, mouseY, clearAllX, confirmY, TEAMBUILDER_OPP_CLEAR_BTN_W, TEAMBUILDER_OPP_CONFIRM_BTN_H)) {
                for (int i = 0; i < 6; i++) {
                    tbOpponentName[i] = "";
                    tbOpponentLevel[i] = "";
                }
                tbFocusedField = -1;
                return true;
            }
        }

        if (ClientTeamBuilderHelper.hasResult() && !ClientTeamBuilderHelper.getResult().isEmpty()) {
            int baseX = guiLeft + TEAMBUILDER_RESULT_X;
            int baseY = guiTop + TEAMBUILDER_RESULT_Y;
            int visibleHeight = teamBuilderResultVisibleHeight();
            List<TeamBuilderRow> rows = buildTeamBuilderRows();
            int maxScroll = teamBuilderResultMaxScroll(rows);

            int scrollbarX = baseX + TEAMBUILDER_RESULT_W + TEAMBUILDER_SCROLLBAR_GAP;
            if (maxScroll > 0 && isMouseOverScrollbar(mouseX, mouseY, scrollbarX, TEAMBUILDER_SCROLLBAR_WIDTH, baseY, visibleHeight)) {
                teamBuilderResultScrollbarDragging = true;
                teamBuilderResultScroll = scrollAmountFromMouseY(mouseY, baseY, visibleHeight, maxScroll);
                return true;
            }

            if (mouseX >= baseX && mouseX < baseX + TEAMBUILDER_RESULT_W && mouseY >= baseY && mouseY < baseY + visibleHeight) {
                int rowY = baseY - (int) Math.round(teamBuilderResultScroll);
                for (TeamBuilderRow row : rows) {
                    int rowH = teamBuilderRowHeight(row);
                    if (!row.heading && !row.entry.reasons.isEmpty() && mouseY >= rowY && mouseY < rowY + rowH) {
                        if (!teamBuilderExpandedEntries.remove(row.entryIndex)) teamBuilderExpandedEntries.add(row.entryIndex);
                        return true;
                    }
                    rowY += rowH;
                }
            }
        }

        tbFocusedField = -1;
        return false;
    }

    private void sendTeamBuilderOpponentRequest() {
        List<String> entries = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            String name = tbOpponentName[i].trim();
            if (name.isBlank()) continue;
            String resolved = resolveSearchQuery(name);
            String level = tbOpponentLevel[i].trim();
            entries.add(resolved + "|" + (level.isBlank() ? "50" : level));
        }
        sendToServer(new com.cobblecompanion.network.TeamBuilderRequestPacket(2, "", entries));
    }

    /** Pixel-Rechteck (x,y,w,h) des Eingabefelds fieldIndex (row*2+col) - gleiche Geometrie wie render/handleTeamBuilderClicks. */
    private int[] teamBuilderOpponentFieldRect(int fieldIndex) {
        int x = guiLeft + TEAMBUILDER_LEFT_X;
        int subY = teamBuilderOppGridY();
        int row = fieldIndex / 2;
        int rowY = subY + row * TEAMBUILDER_OPP_ROW_H;
        if (fieldIndex % 2 == 0) {
            return new int[]{x, rowY, TEAMBUILDER_OPP_NAME_W, TEAMBUILDER_OPP_NAME_H};
        }
        int levelX = x + TEAMBUILDER_OPP_NAME_W + TEAMBUILDER_OPP_LEVEL_GAP;
        return new int[]{levelX, rowY, TEAMBUILDER_OPP_LEVEL_W, TEAMBUILDER_OPP_NAME_H};
    }

    /** Pokémon-Namen (übersetzt), die zum fokussierten Gegner-Namensfeld passen - nur im Team-Modus, nur für Namensfelder. */
    private List<String> teamBuilderOpponentSuggestions() {
        if (teamBuilderMode != 2 || tbFocusedField == -1 || tbFocusedField % 2 != 0) return List.of();
        int row = tbFocusedField / 2;
        String q = tbOpponentName[row].trim().toLowerCase();
        if (q.isEmpty()) return List.of();
        List<String> result = new java.util.ArrayList<>();
        for (Species s : PokemonSpecies.INSTANCE.getSpecies()) {
            String display = speciesDisplayName(s);
            if (display.toLowerCase().startsWith(q) && !display.equalsIgnoreCase(q)) result.add(display);
            if (result.size() >= SEARCH_SUGGEST_MAX) break;
        }
        return result;
    }

    /** Klick auf einen Autovervollständigungs-Vorschlag im Team-Modus - übernimmt den Namen ins fokussierte Feld. */
    private boolean handleTeamBuilderSuggestionClick(double mouseX, double mouseY) {
        List<String> suggestions = teamBuilderOpponentSuggestions();
        if (suggestions.isEmpty()) return false;
        int[] rect = teamBuilderOpponentFieldRect(tbFocusedField);
        int sx = rect[0];
        int rowY = rect[1] + rect[3];
        for (String s : suggestions) {
            if (isInRect(mouseX, mouseY, sx, rowY, TEAMBUILDER_OPP_NAME_W, SEARCH_SUGGEST_ROW_H)) {
                tbOpponentName[tbFocusedField / 2] = s;
                tbFocusedField = -1;
                return true;
            }
            rowY += SEARCH_SUGGEST_ROW_H;
        }
        return false;
    }

    private int homeGiftVisibleHeight() {
        return (guiTop + GUI_HEIGHT - HOME_GIFT_BOTTOM_MARGIN) - (guiTop + HOME_GIFT_Y);
    }

    private int homeGiftMaxScroll(int count) {
        return Math.max(0, count * HOME_GIFT_ENTRY_H - homeGiftVisibleHeight());
    }

    /**
     * Rechte Hälfte des Home-Tabs: offene Pokemon-Geschenke zum Annehmen (Stream-Wunsch) -
     * Spielerkopf + Text, Pokemon-Slot (Nummer/Level/Fangball wie ToDo/WhoNeeds) und
     * Akzeptieren-Button je Angebot. Per Scissor+Scrollbar begrenzt, falls mehrere offen sind.
     */
    private void renderHomeGiftPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        List<ClientGiftHelper.PendingGift> gifts = ClientGiftHelper.getPendingForMe();
        if (gifts.isEmpty()) return;

        int panelX = guiLeft + HOME_GIFT_X;
        int listTop = guiTop + HOME_GIFT_Y;
        int visibleHeight = homeGiftVisibleHeight();
        int maxScroll = homeGiftMaxScroll(gifts.size());
        homeGiftScrollAmount = Math.max(0, Math.min(maxScroll, homeGiftScrollAmount));

        int scrollbarX = guiLeft + GUI_WIDTH - HOME_GIFT_SCROLLBAR_GAP - HOME_GIFT_SCROLLBAR_WIDTH;
        graphics.enableScissor(panelX, listTop, scrollbarX - 2, listTop + visibleHeight);

        int y = listTop - (int) Math.round(homeGiftScrollAmount);
        for (ClientGiftHelper.PendingGift gift : gifts) {
            if (y + HOME_GIFT_ENTRY_H >= listTop && y <= listTop + visibleHeight) {
                renderHomeGiftEntry(graphics, mouseX, mouseY, panelX, y, gift);
            }
            y += HOME_GIFT_ENTRY_H;
        }
        graphics.disableScissor();

        renderScrollbar(graphics, scrollbarX, HOME_GIFT_SCROLLBAR_WIDTH, listTop, visibleHeight, homeGiftScrollAmount, maxScroll);
    }

    private void renderHomeGiftEntry(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, ClientGiftHelper.PendingGift gift) {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.client.multiplayer.PlayerInfo info = (mc.getConnection() != null)
            ? mc.getConnection().getPlayerInfo(gift.fromUuid) : null;
        ResourceLocation skinTexture = info != null
            ? info.getSkin().texture()
            : net.minecraft.client.resources.DefaultPlayerSkin.getDefaultTexture();
        net.minecraft.client.gui.components.PlayerFaceRenderer.draw(graphics, skinTexture, x, y, HOME_GIFT_HEAD_SIZE);

        // Zweizeilig: obere Zeile oben bündig mit dem Spielerkopf, untere Zeile unten bündig -
        // Lücke zum Kopf bleibt HOME_GIFT_TEXT_OFFSET_X (horizontal unverändert).
        String line1 = tr("cobblecompanion.gui.home.gift_offer_line1", gift.fromName);
        String line2 = tr("cobblecompanion.gui.home.gift_offer_line2");
        int textX = x + HOME_GIFT_TEXT_OFFSET_X;
        drawSmallLabel(graphics, line1, textX, y, HOME_GIFT_TEXT_SCALE, 0xFFFFFF, true, true);
        drawSmallLabel(graphics, line2, textX, y + HOME_GIFT_HEAD_SIZE - HOME_GIFT_TEXT_LINE_H, HOME_GIFT_TEXT_SCALE, 0xFFFFFF, true, true);

        int slotY = y + HOME_GIFT_SLOT_OFFSET_Y;
        renderPokemonNumberedSlot(graphics, x, slotY, gift.speciesId, gift.aspects, gift.level);
        renderTodoCaughtBallIcon(graphics, x, slotY, gift.caughtBallId);

        Species species = PokemonSpecies.INSTANCE.getByIdentifier(gift.speciesId);
        String name = species != null ? speciesDisplayName(species) : gift.speciesId.getPath();
        if (gift.nickname != null && !gift.nickname.isBlank()) name = name + " - " + gift.nickname;
        drawSmallLabel(graphics, name, x + HOME_GIFT_NAME_OFFSET_X, slotY + 2, 1.0f, 0xFFFFFF, true, true);

        // Typ-Icon(s) + Wesen/Fähigkeit unter dem Namen.
        int infoX = x + HOME_GIFT_INFO_OFFSET_X;
        int infoY = slotY + HOME_GIFT_INFO_OFFSET_Y;
        int infoTextX = infoX;
        if (species != null) {
            FormData form = species.getForm(gift.aspects);
            ElementalType primary = form.getPrimaryType();
            ElementalType secondary = form.getSecondaryType();
            if (primary != null) {
                new TypeIcon(infoX, infoY, primary, secondary, false, true, TODO_BAR_TYPE_ICON_SECONDARY_OFFSET, 7.5F, 1F).render(graphics);
                infoTextX = infoX + HOME_GIFT_INFO_ICON_SIZE + HOME_GIFT_INFO_TEXT_OFFSET_X;
            }
        }
        String natureName = !gift.natureId.isBlank() ? tr("cobblemon.nature." + gift.natureId) : "";
        String abilityName = !gift.abilityId.isBlank() ? tr("cobblemon.ability." + gift.abilityId) : "";
        String infoText = natureName.isBlank() ? abilityName
            : abilityName.isBlank() ? natureName
            : natureName + " - " + abilityName;
        if (!infoText.isBlank()) {
            drawSmallLabel(graphics, infoText, infoTextX, infoY, 1.0f, 0xAAAAAA, false, true);
        }

        int btnY = slotY + HOME_GIFT_BTN_OFFSET_Y;
        boolean acceptHovered = isInRect(mouseX, mouseY, x, btnY, HOME_GIFT_BTN_W, HOME_GIFT_BTN_H);
        graphics.blit(TODO_EVOLVE_BUTTON, x, btnY, HOME_GIFT_BTN_W, HOME_GIFT_BTN_H,
            0f, acceptHovered ? SETTINGS_BTN_NATIVE_H : 0f,
            SETTINGS_BTN_NATIVE_W, SETTINGS_BTN_NATIVE_H, SETTINGS_BTN_NATIVE_W, SETTINGS_BTN_NATIVE_H * 2);
        String acceptLabel = tr("cobblecompanion.gui.home.gift_accept");
        int acceptLabelWidth = smallLabelWidth(acceptLabel, 1.0f, true, true);
        drawSmallLabel(graphics, acceptLabel, x + (HOME_GIFT_BTN_W - acceptLabelWidth) / 2, btnY + (HOME_GIFT_BTN_H - 8) / 2, 1.0f, 0xFFFFFF, true, true);

        int declineX = x + HOME_GIFT_BTN_W + HOME_GIFT_BTN_GAP;
        boolean declineHovered = isInRect(mouseX, mouseY, declineX, btnY, HOME_GIFT_DECLINE_BTN_W, HOME_GIFT_BTN_H);
        graphics.blit(TODO_EVOLVE_BUTTON, declineX, btnY, HOME_GIFT_DECLINE_BTN_W, HOME_GIFT_BTN_H,
            0f, declineHovered ? SETTINGS_BTN_NATIVE_H : 0f,
            SETTINGS_BTN_NATIVE_W, SETTINGS_BTN_NATIVE_H, SETTINGS_BTN_NATIVE_W, SETTINGS_BTN_NATIVE_H * 2);
        String declineLabel = tr("cobblecompanion.gui.home.gift_decline");
        int declineLabelWidth = smallLabelWidth(declineLabel, 1.0f, true, true);
        drawSmallLabel(graphics, declineLabel, declineX + (HOME_GIFT_DECLINE_BTN_W - declineLabelWidth) / 2, btnY + (HOME_GIFT_BTN_H - 8) / 2, 1.0f, 0xFFFFFF, true, true);
    }

    /** Klicks auf Scrollbar oder Akzeptieren-Button im Home-Gift-Panel. */
    private boolean handleHomeGiftClicks(double mouseX, double mouseY) {
        List<ClientGiftHelper.PendingGift> gifts = ClientGiftHelper.getPendingForMe();
        if (gifts.isEmpty()) return false;

        int panelX = guiLeft + HOME_GIFT_X;
        int listTop = guiTop + HOME_GIFT_Y;
        int visibleHeight = homeGiftVisibleHeight();
        int maxScroll = homeGiftMaxScroll(gifts.size());

        if (maxScroll > 0) {
            int scrollbarX = guiLeft + GUI_WIDTH - HOME_GIFT_SCROLLBAR_GAP - HOME_GIFT_SCROLLBAR_WIDTH;
            if (isMouseOverScrollbar(mouseX, mouseY, scrollbarX, HOME_GIFT_SCROLLBAR_WIDTH, listTop, visibleHeight)) {
                homeGiftScrollbarDragging = true;
                homeGiftScrollAmount = scrollAmountFromMouseY(mouseY, listTop, visibleHeight, maxScroll);
                return true;
            }
        }

        int y = listTop - (int) Math.round(homeGiftScrollAmount);
        for (ClientGiftHelper.PendingGift gift : gifts) {
            int btnY = y + HOME_GIFT_SLOT_OFFSET_Y + HOME_GIFT_BTN_OFFSET_Y;
            if (btnY >= listTop && btnY <= listTop + visibleHeight) {
                if (isInRect(mouseX, mouseY, panelX, btnY, HOME_GIFT_BTN_W, HOME_GIFT_BTN_H)) {
                    sendToServer(new GiftAcceptPacket(gift.fromUuid));
                    return true;
                }
                int declineX = panelX + HOME_GIFT_BTN_W + HOME_GIFT_BTN_GAP;
                if (isInRect(mouseX, mouseY, declineX, btnY, HOME_GIFT_DECLINE_BTN_W, HOME_GIFT_BTN_H)) {
                    sendToServer(new GiftDeclinePacket(gift.fromUuid));
                    return true;
                }
            }
            y += HOME_GIFT_ENTRY_H;
        }
        return false;
    }

    boolean isInRect(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    /** true, wenn gerade eines unserer Suchfelder Texteingaben annimmt (blockt z.B. die "E"-Schließen-Taste). */
    private boolean isAnySearchBoxFocused() {
        CompanionTabExtension ext = CompanionExtensions.getTab(currentTab);
        return typeSearchBox.isFocused() || whoNeedsSearchBox.isFocused() || friendsSearchBox.isFocused()
            || professorSearchBox.isFocused() || dexHelpSearchBox.isFocused() || searchTabSearchBox.isFocused()
            || (ext != null && ext.isCapturingTextInput());
    }

    // ===== Generische Autovervollständigung für alle Suchfelder (Type/Who-Needs/Friends) =====
    // Wird ganz am Ende von render() gezeichnet (siehe renderActiveSearchSuggestions), damit
    // sie wirklich über allem liegt (Tab-Icons, Button-Leisten, Widgets) statt dahinter.
    private static final int SEARCH_SUGGEST_MAX = 5;
    private static final int SEARCH_SUGGEST_ROW_H = 8;
    private static final int SEARCH_SUGGEST_HOVER_BG = 0x60335577;
    private static final int SEARCH_SUGGEST_TEXT_OFFSET_X = 6;
    private static final int SEARCH_SUGGEST_TEXT_OFFSET_Y = -1;
    // Hintergrund hat IMMER die volle Höhe für SEARCH_SUGGEST_MAX Zeilen, unabhängig davon wie
    // viele Vorschläge tatsächlich da sind - sonst staucht sich das Gitter-Muster bei wenigen
    // Treffern zusammen und die Linien passen nicht mehr zur Zeilenhöhe.
    private static final int SEARCH_SUGGEST_BG_H = SEARCH_SUGGEST_MAX * SEARCH_SUGGEST_ROW_H + 4;
    // Muss über den per Z-Translate hochgezogenen 3D-Pokemon-Modellen + Nummer/Level-Labels
    // liegen (siehe POKEMON_SLOT_LABEL bei Z=300, LIST_LEAF_Z_OFFSET=200) - sonst "liegen die
    // Modelle vor der Box". Bewusst deutlich höher als beide.
    private static final float SEARCH_SUGGEST_Z = 400f;
    // Cobblemons eigene "Größenvergleich"-Gitter-Textur als blickdichter Vorschlags-Hintergrund
    // (Wunsch: keine Transparenz mehr, damit Slots/Modelle im Hintergrund nicht durchscheinen).
    private static final ResourceLocation SEARCH_SUGGEST_BG_TEXTURE =
        ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/pokedex/height_grid.png");
    private static final int SEARCH_SUGGEST_BG_NATIVE_W = 137;
    private static final int SEARCH_SUGGEST_BG_NATIVE_H = 42;

    /** Typ- und Pokemon-Namen (übersetzt), die mit der Eingabe im Type-Tab-Suchfeld beginnen. */
    private List<String> typeSearchSuggestions() {
        if (!typeSearchBox.isFocused()) return java.util.List.of();
        String q = typeSearchBox.getValue().trim().toLowerCase();
        if (q.isEmpty()) return java.util.List.of();
        List<String> result = new java.util.ArrayList<>();
        for (String type : TYPE_ORDER) {
            String display = tr("cobblemon.type." + type);
            if (display.toLowerCase().startsWith(q) && !display.equalsIgnoreCase(q)) result.add(display);
            if (result.size() >= SEARCH_SUGGEST_MAX) return result;
        }
        for (Species s : PokemonSpecies.INSTANCE.getSpecies()) {
            String display = speciesDisplayName(s);
            if (display.toLowerCase().startsWith(q) && !display.equalsIgnoreCase(q)) result.add(display);
            if (result.size() >= SEARCH_SUGGEST_MAX) break;
        }
        return result;
    }

    /** Pokemon-Namen (übersetzt), die mit der Eingabe im Who-Needs-Suchfeld beginnen. */
    private List<String> whoNeedsSearchSuggestions() {
        if (!whoNeedsSearchBox.isFocused()) return java.util.List.of();
        String q = whoNeedsSearchBox.getValue().trim().toLowerCase();
        if (q.isEmpty()) return java.util.List.of();
        List<String> result = new java.util.ArrayList<>();
        for (Species s : PokemonSpecies.INSTANCE.getSpecies()) {
            String display = speciesDisplayName(s);
            if (display.toLowerCase().startsWith(q) && !display.equalsIgnoreCase(q)) result.add(display);
            if (result.size() >= SEARCH_SUGGEST_MAX) break;
        }
        return result;
    }

    /**
     * Zeichnet eine Vorschlagsliste bei (x,y), so breit wie das Suchfeld. Schrift exakt wie im
     * Suchfeld selbst (drawScaledBoldText, uniform+bold), nur schwarz statt weiß, da der
     * Grid-Hintergrund deutlich heller ist.
     */
    private void renderSuggestionsBox(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, List<String> suggestions) {
        if (suggestions.isEmpty()) return;

        // Z-Translate, damit die Box wirklich über den 3D-Pokemon-Modellen + ihren Nummer/Level-
        // Labels liegt (die selbst schon per Z-Translate über dem Slot-Hintergrund schweben).
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, SEARCH_SUGGEST_Z);

        graphics.blit(SEARCH_SUGGEST_BG_TEXTURE, x, y, w, SEARCH_SUGGEST_BG_H,
            0f, 0f, SEARCH_SUGGEST_BG_NATIVE_W, SEARCH_SUGGEST_BG_NATIVE_H, SEARCH_SUGGEST_BG_NATIVE_W, SEARCH_SUGGEST_BG_NATIVE_H);

        int rowY = y;
        for (String s : suggestions) {
            boolean hovered = isInRect(mouseX, mouseY, x, rowY, w, SEARCH_SUGGEST_ROW_H);
            if (hovered) graphics.fill(x, rowY, x + w, rowY + SEARCH_SUGGEST_ROW_H, SEARCH_SUGGEST_HOVER_BG);
            drawScaledBoldText(graphics, s, x + SEARCH_SUGGEST_TEXT_OFFSET_X, rowY + SEARCH_SUGGEST_TEXT_OFFSET_Y, BODY_TEXT_SCALE, 0x000000);
            rowY += SEARCH_SUGGEST_ROW_H;
        }
        graphics.pose().popPose();
    }

    /** Prüft Klicks auf eine mit renderSuggestionsBox gezeichnete Vorschlagsliste. */
    private boolean handleSuggestionsClick(double mouseX, double mouseY, int x, int y, int w, List<String> suggestions, CobblemonSearchBox targetBox) {
        return handleSuggestionsClick(mouseX, mouseY, x, y, w, suggestions, targetBox, null);
    }

    /**
     * Wie handleSuggestionsClick, aber mit optionalem onSelect-Callback - Mausklick auf einen
     * Vorschlag bestätigt die Suche damit SOFORT (kein zusätzliches Enter mehr nötig), analog zum
     * bisherigen Verhalten beim Klick auf eine Typ-Grid-Zelle (handleTypeGridClick). null = wie
     * bisher nur den Wert übernehmen (Friends/Professor filtern ohnehin live, kein Submit nötig).
     */
    private boolean handleSuggestionsClick(double mouseX, double mouseY, int x, int y, int w, List<String> suggestions,
                                            CobblemonSearchBox targetBox, Runnable onSelect) {
        if (suggestions.isEmpty()) return false;
        int rowY = y;
        for (String s : suggestions) {
            if (isInRect(mouseX, mouseY, x, rowY, w, SEARCH_SUGGEST_ROW_H)) {
                targetBox.setValue(s);
                targetBox.setCursorPosition(s.length());
                if (onSelect != null) onSelect.run();
                return true;
            }
            rowY += SEARCH_SUGGEST_ROW_H;
        }
        return false;
    }

    // box.getWidth() ist nur die EditBox-Breite (schmaler als das sichtbare Suchfeld) - das
    // eigentliche Suchfeld-Overlay ist immer SEARCH_OVERLAY_W breit, das nutzen wir hier auch
    // für die Vorschlagsbox, sonst bleibt rechts Luft bis zum Rand der Search-Box übrig.
    void renderSearchSuggestions(GuiGraphics graphics, int mouseX, int mouseY, CobblemonSearchBox box, List<String> suggestions) {
        renderSuggestionsBox(graphics, mouseX, mouseY, box.getX(), box.getY() + box.getHeight(), SEARCH_OVERLAY_W, suggestions);
    }

    boolean handleSearchSuggestionClick(double mouseX, double mouseY, CobblemonSearchBox box, List<String> suggestions) {
        return handleSuggestionsClick(mouseX, mouseY, box.getX(), box.getY() + box.getHeight(), SEARCH_OVERLAY_W, suggestions, box);
    }

    /** Wie handleSearchSuggestionClick, aber bestätigt die Suche sofort per onSelect-Callback (siehe handleSuggestionsClick). */
    private boolean handleSearchSuggestionClick(double mouseX, double mouseY, CobblemonSearchBox box, List<String> suggestions, Runnable onSelect) {
        return handleSuggestionsClick(mouseX, mouseY, box.getX(), box.getY() + box.getHeight(), SEARCH_OVERLAY_W, suggestions, box, onSelect);
    }

    /**
     * Zeichnet die Vorschlagsliste des aktuell aktiven Tabs - wird bewusst ganz am Ende von
     * render() aufgerufen (wie die Bestätigungs-Overlays), damit sie wirklich die oberste
     * Ebene ist statt von Tab-Icons/Button-Leisten/Widgets überdeckt zu werden.
     */
    private void renderActiveSearchSuggestions(GuiGraphics graphics, int mouseX, int mouseY) {
        switch (currentTab) {
            case TAB_TODO -> renderSearchSuggestions(graphics, mouseX, mouseY, dexHelpSearchBox, dexHelpSearchSuggestions());
            case TAB_TYPES -> renderSearchSuggestions(graphics, mouseX, mouseY, typeSearchBox, typeSearchSuggestions());
            case TAB_WHONEEDS -> renderSearchSuggestions(graphics, mouseX, mouseY, whoNeedsSearchBox, whoNeedsSearchSuggestions());
            case TAB_FRIENDS -> {
                int x = guiLeft + FRIENDS_PANEL_X;
                int y = guiTop + FRIENDS_ADD_BTN_Y + FRIENDS_ADD_BTN_H + 2;
                renderSuggestionsBox(graphics, mouseX, mouseY, x, y, FRIENDS_SEARCH_W, friendSuggestions());
            }
            case TAB_WALLET -> {
                CompanionTabExtension ext = CompanionExtensions.getTab(currentTab);
                if (ext != null) ext.renderTopLayerContent(graphics, mouseX, mouseY, tabContext());
            }
            case TAB_TEAMBUILDER -> {
                List<String> suggestions = teamBuilderOpponentSuggestions();
                if (!suggestions.isEmpty()) {
                    int[] rect = teamBuilderOpponentFieldRect(tbFocusedField);
                    renderSuggestionsBox(graphics, mouseX, mouseY, rect[0], rect[1] + rect[3], TEAMBUILDER_OPP_NAME_W, suggestions);
                }
            }
        }
    }

    // ===== Settings-Tab =====

    private void renderSettingsTab(GuiGraphics graphics, int mouseX, int mouseY) {
        renderSettingsNav(graphics, mouseX, mouseY);
        renderSettingsContent(graphics, mouseX, mouseY);
    }

    /** Nutzer-Vorgabe: die Gamemodes-Kategorie ist NUR für AdminOp überhaupt sichtbar (nicht bloß gesperrt wie Server, siehe SETTINGS_TAB_SERVER). */
    private static boolean isGamemodesTabVisible() {
        return com.cobblecompanion.client.data.ClientAdminHelper.isAdminOp();
    }

    /** Linke Spalte: Sub-Tab-Buttons untereinander ("All Options" zuerst, dann je Kategorie). */
    private void renderSettingsNav(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = guiLeft + SETTINGS_NAV_X;
        int y = guiTop + SETTINGS_NAV_Y;
        for (int i = 0; i < SETTINGS_SUBTAB_NAMES.length; i++) {
            if (i == SETTINGS_TAB_GAMEMODES && !isGamemodesTabVisible()) continue;
            int btnY = y + i * (SETTINGS_NAV_H + SETTINGS_NAV_GAP);
            boolean hovered = isInRect(mouseX, mouseY, x, btnY, SETTINGS_NAV_W, SETTINGS_NAV_H);
            boolean active = settingsSubTab == i;

            graphics.blit(TODO_EVOLVE_BUTTON, x, btnY, SETTINGS_NAV_W, SETTINGS_NAV_H,
                0f, hovered ? SETTINGS_BTN_NATIVE_H : 0f,
                SETTINGS_BTN_NATIVE_W, SETTINGS_BTN_NATIVE_H, SETTINGS_BTN_NATIVE_W, SETTINGS_BTN_NATIVE_H * 2);
            if (active) {
                graphics.fill(x, btnY, x + SETTINGS_NAV_W, btnY + SETTINGS_NAV_H, SETTINGS_SUBTAB_ACTIVE_FILL);
            }

            String label = tr(SETTINGS_SUBTAB_NAMES[i]);
            int labelWidth = smallLabelWidth(label, SETTINGS_SUBTAB_TEXT_SCALE, SETTINGS_SUBTAB_TEXT_BOLD, SETTINGS_SUBTAB_TEXT_UNIFORM_FONT);
            int labelX = x + (SETTINGS_NAV_W - labelWidth) / 2;
            int labelY = btnY + (SETTINGS_NAV_H - Math.round(9 * SETTINGS_SUBTAB_TEXT_SCALE)) / 2;
            drawSmallLabel(graphics, label, labelX, labelY, SETTINGS_SUBTAB_TEXT_SCALE,
                active ? SETTINGS_SUBTAB_ACTIVE_COLOR : SETTINGS_SUBTAB_INACTIVE_COLOR,
                SETTINGS_SUBTAB_TEXT_BOLD, SETTINGS_SUBTAB_TEXT_UNIFORM_FONT);
        }
    }

    /** Rechte Spalte: Überschrift(en) + Options-Zeilen des aktiven Sub-Tabs, scrollbar. */
    private void renderSettingsContent(GuiGraphics graphics, int mouseX, int mouseY) {
        List<SettingsRow> rows = buildSettingsRows();
        int contentX = guiLeft + SETTINGS_CONTENT_X;
        int contentTop = guiTop + SETTINGS_CONTENT_Y;
        int maxScroll = settingsMaxScroll(rows);
        settingsScrollAmount = Math.max(0, Math.min(maxScroll, settingsScrollAmount));

        graphics.enableScissor(contentX, contentTop, contentX + SETTINGS_CONTENT_WIDTH, contentTop + SETTINGS_CONTENT_VISIBLE_HEIGHT);
        int rowY = contentTop - (int) Math.round(settingsScrollAmount);
        for (SettingsRow row : rows) {
            int rowH = settingsRowHeight(row);
            if (rowY + rowH >= contentTop && rowY <= contentTop + SETTINGS_CONTENT_VISIBLE_HEIGHT) {
                renderSettingsRow(graphics, contentX, rowY, row, mouseX, mouseY);
            }
            rowY += rowH;
        }
        graphics.disableScissor();

        int scrollbarX = contentX + SETTINGS_CONTENT_WIDTH + SETTINGS_SCROLLBAR_GAP;
        renderScrollbar(graphics, scrollbarX, SETTINGS_SCROLLBAR_WIDTH, contentTop, SETTINGS_CONTENT_VISIBLE_HEIGHT, settingsScrollAmount, maxScroll);
    }

    /** Eine Zeile: entweder eine Überschrift oder eine Options-Zeile (Label links, Toggle-/Cycle-Button rechtsbündig). */
    private void renderSettingsRow(GuiGraphics graphics, int contentX, int y, SettingsRow row, int mouseX, int mouseY) {
        if (row.type == SETTINGS_ROW_HEADING) {
            int headingColor = row.overrideColor != -1 ? row.overrideColor : SETTINGS_HEADING_COLOR;
            int headingLineY = y;
            for (String line : settingsHeadingLines(row)) {
                drawSmallLabel(graphics, line, contentX, headingLineY, SETTINGS_HEADING_TEXT_SCALE,
                    headingColor, SETTINGS_HEADING_BOLD, SETTINGS_HEADING_UNIFORM_FONT);
                headingLineY += SETTINGS_OPTION_LINE_H;
            }
            return;
        }

        int controlW = row.type == SETTINGS_ROW_CYCLE || row.type == SETTINGS_ROW_BUTTON ? SETTINGS_CYCLE_W : SETTINGS_TOGGLE_W;
        int controlX = contentX + SETTINGS_CONTENT_WIDTH - controlW;

        // Nutzer-Korrektur: Gold/Gelb gilt nur für den MODUS-NAMEN "Living Dex+" selbst (Dex-
        // Wahl-Zeile, PC-Status-Button, Modus-Label), NICHT für die einzelnen Kategorie-Zeilen
        // darunter - die sind normale (weiße) Optionen wie jede andere Einstellung auch.
        int labelColor = SETTINGS_OPTION_LABEL_COLOR;
        // Gerade gezogene Zeile leicht hervorheben (Drag&Drop-Feedback).
        if (ldpDragArmed && isLdpCategoryKey(row.key) && ldpCategoryIdForKey(row.key) == ldpDragCategoryId) {
            graphics.fill(contentX - 2, y - 1, controlX - 2, y + settingsRowHeight(row) - SETTINGS_OPTION_ROW_GAP, 0x40FFD700);
        }
        int lineY = y + SETTINGS_OPTION_LABEL_Y_OFFSET;
        for (String line : settingsLabelLines(row)) {
            drawSmallLabel(graphics, line, contentX, lineY, SETTINGS_OPTION_LABEL_SCALE,
                labelColor, SETTINGS_OPTION_LABEL_BOLD, SETTINGS_OPTION_LABEL_UNIFORM_FONT);
            lineY += SETTINGS_OPTION_LINE_H;
        }

        boolean hovered = isInRect(mouseX, mouseY, controlX, y, controlW, SETTINGS_TOGGLE_H);
        graphics.blit(TODO_EVOLVE_BUTTON, controlX, y, controlW, SETTINGS_TOGGLE_H,
            0f, hovered ? SETTINGS_BTN_NATIVE_H : 0f,
            SETTINGS_BTN_NATIVE_W, SETTINGS_BTN_NATIVE_H, SETTINGS_BTN_NATIVE_W, SETTINGS_BTN_NATIVE_H * 2);

        String text;
        int color;
        if (row.type == SETTINGS_ROW_CYCLE) {
            text = readCycleText(row.key);
            color = (row.key == SKEY_DEX_WAHL && ClientSettingsHelper.isPcSortModeLivingDexPlus())
                ? SETTINGS_LDP_GOLD_COLOR : SETTINGS_CYCLE_TEXT_COLOR;
        } else if (row.type == SETTINGS_ROW_BUTTON) {
            text = settingsButtonText(row.key);
            color = SETTINGS_CYCLE_TEXT_COLOR;
        } else {
            boolean value = readToggleValue(row.key);
            text = value ? tr("cobblecompanion.gui.confirm.yes") : tr("cobblecompanion.gui.confirm.no");
            color = value ? SETTINGS_TOGGLE_ON_COLOR : SETTINGS_TOGGLE_OFF_COLOR;
        }
        // OP-only-Optionen für Nicht-OPs abgedunkelt darstellen (nur lesbar, nicht änderbar).
        if (isSettingLocked(row.key)) color = 0xFF888888;

        // Auf/Ab-Pfeile jetzt INNERHALB des Ja/Nein-Buttons (Nutzer-Korrektur: kollidierten vorher
        // mit dem Label-Text links davon) - nur bei "Ja" (aktivierte Kategorie/Region, hat eine
        // Box-Position zum Verschieben). Drittelung: links=hoch, mitte=Ja/Nein-Text, rechts=runter.
        // Regionen sind seit dem Umbau "Regionalformen-Unterkategorien von den Oberkategorien
        // trennen" genauso Teil der Haupt-Kategorie-Reihenfolge wie jede Basis-Kategorie.
        boolean ldpEnabled = isLdpCategoryKey(row.key) && ClientSettingsHelper.isLivingDexPlusCategoryEnabled(ldpCategoryIdForKey(row.key));
        if (ldpEnabled) {
            java.util.List<Integer> order = ClientSettingsHelper.getLivingDexPlusCategoryOrder();
            int idx = order.indexOf(ldpCategoryIdForKey(row.key));
            int size = order.size();
            int third = controlW / 3;
            int upColor = idx > 0 ? 0xFFFFFFFF : 0xFF555555;
            int downColor = idx >= 0 && idx < size - 1 ? 0xFFFFFFFF : 0xFF555555;
            int arrowY = y + (SETTINGS_TOGGLE_H - 8) / 2;
            drawSmallLabel(graphics, "▲", controlX + (third - smallLabelWidth("▲", 1.0f, true, true)) / 2, arrowY, 1.0f, upColor, true, true);
            drawSmallLabel(graphics, "▼", controlX + controlW - third + (third - smallLabelWidth("▼", 1.0f, true, true)) / 2, arrowY, 1.0f, downColor, true, true);
        }

        int textWidth = smallLabelWidth(text, SETTINGS_CONTROL_TEXT_SCALE, SETTINGS_CONTROL_TEXT_BOLD, SETTINGS_CONTROL_TEXT_UNIFORM_FONT);
        int textX = controlX + (controlW - textWidth) / 2;
        int textY = y + (SETTINGS_TOGGLE_H - Math.round(9 * SETTINGS_CONTROL_TEXT_SCALE)) / 2;
        drawSmallLabel(graphics, text, textX, textY, SETTINGS_CONTROL_TEXT_SCALE, color, SETTINGS_CONTROL_TEXT_BOLD, SETTINGS_CONTROL_TEXT_UNIFORM_FONT);
    }

    /**
     * Baut die anzuzeigenden Zeilen für den aktiven Sub-Tab. Bei "All Options" werden alle
     * vier Kategorien nacheinander mit eigener Überschrift angehängt, sonst nur die eine.
     */
    private List<SettingsRow> buildSettingsRows() {
        List<SettingsRow> rows = new java.util.ArrayList<>();
        if (settingsSubTab == SETTINGS_TAB_ALL || settingsSubTab == SETTINGS_TAB_POKEDEX) {
            rows.add(new SettingsRow(SETTINGS_ROW_HEADING, tr("cobblecompanion.settings.head.pokedex"), -1));
            rows.add(new SettingsRow(SETTINGS_ROW_CYCLE, tr("cobblecompanion.settings.pokedex.dex_wahl"), SKEY_DEX_WAHL));

            if (ClientSettingsHelper.isPcSortModeLivingDexPlus()) {
                com.cobblecompanion.client.data.LivingDexPlusHelper.ensureRequested();
                rows.add(new SettingsRow(SETTINGS_ROW_HEADING, tr("cobblecompanion.settings.head.livingdexplus"), -1));
                rows.add(buildLdpSummaryRow());
                // Aktivierte Kategorien zuerst, in ihrer Box-Reihenfolge (Drag&Drop-Arbeitskopie
                // während eines laufenden Drags, sonst der gespeicherte Stand) - danach die
                // deaktivierten in fester Deklarationsreihenfolge (nicht ziehbar).
                java.util.List<Integer> order = (ldpDragCategoryId != -1 && ldpDragOrder != null)
                    ? ldpDragOrder : ClientSettingsHelper.getLivingDexPlusCategoryOrder();
                for (int id : order) rows.add(buildLdpCategoryRow(id));
                // Deaktivierte Kategorien/Regionen/Kosmetisch-Arten in der festen Nutzer-Vorgabe-
                // Reihenfolge: Pokédex/Living Dex/Regionen (CANONICAL_ORDER_BEFORE_COSMETIC), dann
                // die Kosmetisch-Art-Unterkategorien (Whitelist, dynamisch aus dem Katalog - siehe
                // LivingDexPlusRegistry.COSMETIC_SUBCATEGORY_WHITELIST), zuletzt die gemeinsame
                // Basis-Kategorie "Kosmetische Formen" für alle übrigen Arten.
                List<Integer> disabledFallbackOrder = new java.util.ArrayList<>(com.cobblecompanion.data.LivingDexPlusRegistry.CANONICAL_ORDER_BEFORE_COSMETIC);
                java.util.List<String> cosmeticSpecies = com.cobblecompanion.client.data.LivingDexPlusHelper.cosmeticSpeciesNames();
                for (int i = 0; i < cosmeticSpecies.size(); i++) {
                    disabledFallbackOrder.add(com.cobblecompanion.data.LivingDexPlusRegistry.cosmeticSyntheticId(i, false));
                }
                for (int i = 0; i < cosmeticSpecies.size(); i++) {
                    disabledFallbackOrder.add(com.cobblecompanion.data.LivingDexPlusRegistry.cosmeticSyntheticId(i, true));
                }
                disabledFallbackOrder.addAll(com.cobblecompanion.data.LivingDexPlusRegistry.CANONICAL_ORDER_COSMETIC_TAIL);
                for (int id : disabledFallbackOrder) {
                    if (order.contains(id)) continue;
                    rows.add(buildLdpCategoryRow(id));
                }
            }
        }
        if (settingsSubTab == SETTINGS_TAB_ALL || settingsSubTab == SETTINGS_TAB_TODO) {
            rows.add(new SettingsRow(SETTINGS_ROW_HEADING, tr("cobblecompanion.settings.head.todo"), -1));
            rows.add(new SettingsRow(SETTINGS_ROW_TOGGLE, tr("cobblecompanion.settings.todo.show_button"), SKEY_TODO_SHOW_BUTTON));
            rows.add(new SettingsRow(SETTINGS_ROW_TOGGLE, tr("cobblecompanion.settings.todo.confirm"), SKEY_TODO_CONFIRM));
            rows.add(new SettingsRow(SETTINGS_ROW_TOGGLE, tr("cobblecompanion.settings.todo.send_out"), SKEY_TODO_SEND_OUT));
            rows.add(new SettingsRow(SETTINGS_ROW_TOGGLE, tr("cobblecompanion.settings.todo.close"), SKEY_TODO_CLOSE));
        }
        if (settingsSubTab == SETTINGS_TAB_ALL || settingsSubTab == SETTINGS_TAB_WHONEEDS) {
            rows.add(new SettingsRow(SETTINGS_ROW_HEADING, tr("cobblecompanion.settings.head.whoneeds"), -1));
            rows.add(new SettingsRow(SETTINGS_ROW_TOGGLE, tr("cobblecompanion.settings.whoneeds.only_friends"), SKEY_WHONEEDS_ONLY_FRIENDS));
            rows.add(new SettingsRow(SETTINGS_ROW_TOGGLE, tr("cobblecompanion.settings.whoneeds.friends_first"), SKEY_WHONEEDS_FRIENDS_FIRST));
        }
        if (settingsSubTab == SETTINGS_TAB_ALL || settingsSubTab == SETTINGS_TAB_FRIENDS) {
            rows.add(new SettingsRow(SETTINGS_ROW_HEADING, tr("cobblecompanion.settings.head.friends"), -1));
            rows.add(new SettingsRow(SETTINGS_ROW_TOGGLE, tr("cobblecompanion.settings.friends.auto_accept"), SKEY_FRIENDS_AUTO_ACCEPT));
            rows.add(new SettingsRow(SETTINGS_ROW_TOGGLE, tr("cobblecompanion.settings.friends.show_offline"), SKEY_FRIENDS_SHOW_OFFLINE));
            // Platzhalter fürs spätere Teleport-Feature, noch nicht funktional.
            rows.add(new SettingsRow(SETTINGS_ROW_TOGGLE, tr("cobblecompanion.settings.friends.allow_tp_to_me"), SKEY_FRIENDS_ALLOW_TELEPORT_TO_ME));
        }
        if (settingsSubTab == SETTINGS_TAB_ALL || settingsSubTab == SETTINGS_TAB_PC) {
            rows.add(new SettingsRow(SETTINGS_ROW_HEADING, tr("cobblecompanion.settings.head.pc"), -1));
            rows.add(new SettingsRow(SETTINGS_ROW_TOGGLE, tr("cobblecompanion.settings.pc.sort_helper"), SKEY_PC_SORT_HELPER));
            rows.add(new SettingsRow(SETTINGS_ROW_CYCLE, tr("cobblecompanion.settings.pc.start_box"), SKEY_PC_SORT_START_BOX));
            rows.add(new SettingsRow(SETTINGS_ROW_BUTTON, tr("cobblecompanion.settings.pc.autoname"), SKEY_PC_AUTONAME_BUTTON));
            rows.add(new SettingsRow(SETTINGS_ROW_TOGGLE, tr("cobblecompanion.settings.pc.slot_check"), SKEY_PC_SLOT_CHECK));
        }
        // Server-Kategorie bewusst ans Ende verschoben (Nutzer-Vorgabe) - für Spieler ohne AdminOp
        // nur sichtbar, nicht klickbar (siehe isSettingLocked()).
        if (settingsSubTab == SETTINGS_TAB_ALL || settingsSubTab == SETTINGS_TAB_SERVER) {
            rows.add(new SettingsRow(SETTINGS_ROW_HEADING, tr("cobblecompanion.settings.head.server"), -1));
            // Nutzer-Vorgabe: rein client-lokales Setting (kein AdminOp-Lock, siehe
            // isSettingLocked() - jeder Spieler blendet seine EIGENEN Meldungen selbst aus).
            rows.add(new SettingsRow(SETTINGS_ROW_TOGGLE, tr("cobblecompanion.settings.server.hide_observer_messages"), SKEY_HIDE_OBSERVER_MESSAGES));
            rows.add(new SettingsRow(SETTINGS_ROW_TOGGLE, tr("cobblecompanion.settings.server.forbid_gifting"), SKEY_SERVER_FORBID_GIFTING));
            // Platzhalter fürs spätere Teleport-Feature, noch nicht funktional.
            rows.add(new SettingsRow(SETTINGS_ROW_TOGGLE, tr("cobblecompanion.settings.server.allow_tp"), SKEY_SERVER_ALLOW_TELEPORT_TO_FRIENDS));
            rows.add(new SettingsRow(SETTINGS_ROW_CYCLE, tr("cobblecompanion.settings.livingdexplus.box_count"), SKEY_PC_BOX_COUNT));
            rows.add(new SettingsRow(SETTINGS_ROW_HEADING, tr("cobblecompanion.settings.livingdexplus.box_count_hint"), -1, 0xFF888888));
            // CobbleDollars-Einstellungen nur sichtbar, wenn der Server die Integration überhaupt
            // unterstützt (Nutzer-Anfrage: Einnahmequellen abschaltbar + Online-Belohnung +
            // Creative-Preis hierher aus dem Wallet-Tab verschoben).
            if (ClientServerRulesHelper.isCobbleDollarsAvailable()) {
                rows.add(new SettingsRow(SETTINGS_ROW_TOGGLE, tr("cobblecompanion.settings.server.earn_from_npc"), SKEY_SERVER_EARN_FROM_NPC));
                rows.add(new SettingsRow(SETTINGS_ROW_TOGGLE, tr("cobblecompanion.settings.server.earn_from_wild"), SKEY_SERVER_EARN_FROM_WILD_POKEMON));
                rows.add(new SettingsRow(SETTINGS_ROW_CYCLE, tr("cobblecompanion.settings.server.income_multiplier"), SKEY_SERVER_INCOME_MULTIPLIER));
                rows.add(new SettingsRow(SETTINGS_ROW_TOGGLE, tr("cobblecompanion.settings.server.online_reward_enabled"), SKEY_SERVER_ONLINE_REWARD_ENABLED));
                rows.add(new SettingsRow(SETTINGS_ROW_CYCLE, tr("cobblecompanion.settings.server.online_reward_interval"), SKEY_SERVER_ONLINE_REWARD_INTERVAL));
                rows.add(new SettingsRow(SETTINGS_ROW_CYCLE, tr("cobblecompanion.settings.server.online_reward_amount"), SKEY_SERVER_ONLINE_REWARD_AMOUNT));
                // Nutzer-Vorgabe: Liste der pro-Spieler Online-Belohnung-Boni direkt unter dem
                // "Betrag"-Eintrag (rein lesend, Bearbeitung über /companion admin onlinebonus).
                List<String> bonusEntries = com.cobblecompanion.client.data.ClientOnlineBonusRulesHelper.getEntries();
                if (bonusEntries.isEmpty()) {
                    rows.add(new SettingsRow(SETTINGS_ROW_HEADING, tr("cobblecompanion.settings.server.online_reward_no_bonuses"), -1, 0xFF888888));
                } else {
                    for (String entry : bonusEntries) {
                        rows.add(new SettingsRow(SETTINGS_ROW_HEADING, entry, -1, 0xFFAAAAAA));
                    }
                }
            }
        }
        // Nutzer-Vorgabe: eigene Kategorie NUR für AdminOp, bewusst NICHT unter "All Options"
        // mitgelistet (siehe SETTINGS_TAB_GAMEMODES-Kommentar) - Creative-Preis von Server hierher
        // verschoben, dazu der neue Kauf-Ein/Aus-Schalter. Dimensionsregeln (siehe
        // DimensionGamemodeManager) sind hier als echter Listeneditor abgebildet (Nutzer-Fund:
        // eine reine Text-Zeile mit Hinweis auf den Command wirkte wie eine leere, kaputte Liste) -
        // bestehende Regeln als klickbare "Entfernen"-Zeilen, neue Regeln über zwei Cycle-Buttons
        // (Dimension aus der vom Server gesendeten Liste ALLER aktuell geladenen Dimensionen,
        // Gamemode aus den 4 Vanilla-Modi) + "Hinzufügen"-Button - bewusst KEIN freies Textfeld,
        // dadurch nie ein ungültiger Dimensionsname möglich. Der Command
        // (/companion admin gamemode set/remove/list) bleibt zusätzlich nutzbar.
        if (settingsSubTab == SETTINGS_TAB_GAMEMODES && isGamemodesTabVisible()) {
            rows.add(new SettingsRow(SETTINGS_ROW_HEADING, tr("cobblecompanion.settings.head.gamemodes"), -1));
            rows.add(new SettingsRow(SETTINGS_ROW_TOGGLE, tr("cobblecompanion.settings.gamemodes.creative_purchase_enabled"), SKEY_GAMEMODES_CREATIVE_PURCHASE_ENABLED));
            // Nutzer-Vorgabe: Ersatz für das externe InvSync-Datapack (siehe GamemodeInventorySyncManager) -
            // ein Ein/Aus-Schalter genau hier in der ohnehin AdminOp-only Gamemodes-Kategorie.
            rows.add(new SettingsRow(SETTINGS_ROW_TOGGLE, tr("cobblecompanion.settings.gamemodes.inventory_sync_enabled"), SKEY_GAMEMODES_INVENTORY_SYNC_ENABLED));
            rows.add(new SettingsRow(SETTINGS_ROW_CYCLE, tr("cobblecompanion.settings.server.creative_price_per_minute"), SKEY_SERVER_CREATIVE_PRICE_PER_MINUTE));

            rows.add(new SettingsRow(SETTINGS_ROW_HEADING, tr("cobblecompanion.settings.gamemodes.dimension_rules_heading"), -1));
            List<Map.Entry<String, String>> dimensionRules = com.cobblecompanion.client.data.ClientDimensionGamemodeHelper.getSortedRules();
            if (dimensionRules.isEmpty()) {
                rows.add(new SettingsRow(SETTINGS_ROW_HEADING, tr("cobblecompanion.settings.gamemodes.no_rules"), -1, 0xFF888888));
            } else {
                for (int i = 0; i < dimensionRules.size(); i++) {
                    Map.Entry<String, String> rule = dimensionRules.get(i);
                    rows.add(new SettingsRow(SETTINGS_ROW_BUTTON, rule.getKey() + " → " + rule.getValue(), GAMEMODES_REMOVE_KEY_BASE + i));
                }
            }

            rows.add(new SettingsRow(SETTINGS_ROW_HEADING, tr("cobblecompanion.settings.gamemodes.new_rule_heading"), -1));
            rows.add(new SettingsRow(SETTINGS_ROW_CYCLE, tr("cobblecompanion.settings.gamemodes.new_rule_dimension"), SKEY_GAMEMODES_NEW_DIMENSION));
            rows.add(new SettingsRow(SETTINGS_ROW_CYCLE, tr("cobblecompanion.settings.gamemodes.new_rule_mode"), SKEY_GAMEMODES_NEW_MODE));
            rows.add(new SettingsRow(SETTINGS_ROW_BUTTON, tr("cobblecompanion.settings.gamemodes.new_rule_add"), SKEY_GAMEMODES_ADD_BUTTON));

            // Nutzer-Vorgabe: Liste der pro-Spieler Creative-Kauf-Dimensionsregeln GANZ AM ENDE der
            // Kategorie (rein lesend, Bearbeitung über /companion admin creativedimensions).
            rows.add(new SettingsRow(SETTINGS_ROW_HEADING, tr("cobblecompanion.settings.gamemodes.creative_dimension_rules_heading"), -1));
            List<String> creativeDimensionRules = com.cobblecompanion.client.data.ClientCreativeDimensionRulesHelper.getEntries();
            if (creativeDimensionRules.isEmpty()) {
                rows.add(new SettingsRow(SETTINGS_ROW_HEADING, tr("cobblecompanion.settings.gamemodes.no_rules"), -1, 0xFF888888));
            } else {
                for (String entry : creativeDimensionRules) {
                    rows.add(new SettingsRow(SETTINGS_ROW_HEADING, entry, -1, 0xFFAAAAAA));
                }
            }
        }
        return rows;
    }

    /**
     * Ampel-Zusammenfassung über der Kategorie-Liste: benötigte vs. konfigurierte Box-Anzahl.
     * Grün = mehr konfiguriert als benötigt, Gelb = passt exakt, Rot = zu wenig konfiguriert.
     * Solange der Katalog noch nicht da ist (Server-Antwort unterwegs) ein neutraler Platzhalter.
     */
    private static SettingsRow buildLdpSummaryRow() {
        if (!com.cobblecompanion.client.data.LivingDexPlusHelper.isCatalogReady()) {
            return new SettingsRow(SETTINGS_ROW_HEADING, tr("cobblecompanion.settings.livingdexplus.loading"), -1, 0xFF888888);
        }
        java.util.List<Integer> enabled = ClientSettingsHelper.getLivingDexPlusCategoryOrder();
        int needed = com.cobblecompanion.client.data.LivingDexPlusHelper.totalBoxesNeeded(enabled);
        int configured = PCSortHelper.getKnownMaxBox();
        int color = configured > needed ? 0xFF55FF55 : configured == needed ? 0xFFFFFF55 : 0xFFFF5555;
        String text = tr("cobblecompanion.settings.livingdexplus.summary", needed, configured);
        return new SettingsRow(SETTINGS_ROW_HEADING, text, -1, color);
    }

    /**
     * Bildet mouseY auf einen Ziel-Index INNERHALB der aktivierten Living-Dex+-Kategorien ab
     * (0-basiert, geclampt) - läuft dieselbe rowY-Akkumulation wie render/handleSettingsClicks,
     * zählt dabei aber nur die AKTIVIERTEN Kategorie-Zeilen mit. -1, falls keine gefunden wurde.
     */
    private int ldpRowIndexAtY(double mouseY) {
        List<SettingsRow> rows = buildSettingsRows();
        int contentTop = guiTop + SETTINGS_CONTENT_Y;
        int rowY = contentTop - (int) Math.round(settingsScrollAmount);
        int index = -1;
        for (SettingsRow row : rows) {
            int rowH = settingsRowHeight(row);
            if (isLdpCategoryKey(row.key) && ldpDragOrder.contains(ldpCategoryIdForKey(row.key))) {
                index++;
                if (mouseY < rowY + rowH / 2.0) return index;
            }
            rowY += rowH;
        }
        return index; // unter der letzten Zeile losgelassen -> ans Ende
    }

    /**
     * "id" ist entweder eine echte Basis-Kategorie-ID (0/1/2/7, siehe VariantCategory), eine
     * synthetische Pro-Region-ID (100-103 normal / 200-203 Shiny, siehe
     * LivingDexPlusRegistry.regionSyntheticId()) oder eine synthetische Pro-Kosmetisch-Art-ID
     * (1000+/2000+, siehe LivingDexPlusRegistry.cosmeticSyntheticId()) - seit den jeweiligen
     * Umbauten "...-Unterkategorien von den Oberkategorien trennen" ist jede Region/Art ein
     * vollwertiges, genauso behandeltes Listenmitglied wie jede Basis-Kategorie (eigene Zeile,
     * eigener Toggle, frei einordenbar).
     */
    private static SettingsRow buildLdpCategoryRow(int id) {
        String label;
        int boxes;
        if (com.cobblecompanion.data.LivingDexPlusRegistry.isAnyRegionSyntheticId(id)) {
            boolean shiny = com.cobblecompanion.data.LivingDexPlusRegistry.isRegionShinySyntheticId(id);
            String region = com.cobblecompanion.data.LivingDexPlusRegistry.regionNameFromSyntheticId(id);
            String regionName = tr("cobblecompanion.livingdexplus.region." + region.toLowerCase());
            String labelKey = shiny ? "cobblecompanion.livingdexplus.region_label_shiny" : "cobblecompanion.livingdexplus.region_label";
            label = tr(labelKey, regionName);
            boxes = com.cobblecompanion.client.data.LivingDexPlusHelper.isCatalogReady()
                ? com.cobblecompanion.client.data.LivingDexPlusHelper.boxesNeeded(id) : -1;
        } else if (com.cobblecompanion.data.LivingDexPlusRegistry.isAnyCosmeticSyntheticId(id)) {
            // Nutzer-Vorgabe: Karpador/Garados bleiben in den Settings GETRENNTE Zeilen (nicht wie
            // bei der Box-Beschriftung kombiniert) - eigener Übersetzungsschlüssel pro Art statt
            // des rohen Katalog-Artnamens, damit deutsche Clients z.B. "Karpador" statt "Magikarp" sehen.
            boolean shiny = com.cobblecompanion.data.LivingDexPlusRegistry.isCosmeticShinySyntheticId(id);
            int idx = com.cobblecompanion.data.LivingDexPlusRegistry.cosmeticSpeciesIndexFromSyntheticId(id);
            java.util.List<String> species = com.cobblecompanion.client.data.LivingDexPlusHelper.cosmeticSpeciesNames();
            String speciesName = idx >= 0 && idx < species.size() ? species.get(idx) : "?";
            String speciesLabel = tr("cobblecompanion.livingdexplus.cosmetic_species." + speciesName.toLowerCase());
            String labelKey = shiny ? "cobblecompanion.livingdexplus.cosmetic_label_shiny" : "cobblecompanion.livingdexplus.cosmetic_label";
            label = tr(labelKey, speciesLabel);
            boxes = com.cobblecompanion.client.data.LivingDexPlusHelper.isCatalogReady()
                ? com.cobblecompanion.client.data.LivingDexPlusHelper.boxesNeeded(id) : -1;
        } else {
            com.cobblecompanion.client.data.VariantCategory cat = com.cobblecompanion.client.data.VariantCategory.byId(id);
            label = cat != null ? tr(cat.labelKey) : "?";
            boxes = com.cobblecompanion.client.data.LivingDexPlusHelper.isCatalogReady()
                ? com.cobblecompanion.client.data.LivingDexPlusHelper.boxesNeeded(id) : -1;
        }
        if (boxes >= 0) label = label + " (" + tr("cobblecompanion.settings.livingdexplus.boxes", boxes) + ")";
        return new SettingsRow(SETTINGS_ROW_TOGGLE, label, ldpKeyForCategoryId(id));
    }

    private static int ldpKeyForCategoryId(int id) { return SKEY_LDP_CAT_BASE_POKEDEX + id; }
    private static int ldpCategoryIdForKey(int key) { return key - SKEY_LDP_CAT_BASE_POKEDEX; }

    private static boolean isLdpCategoryKey(int key) {
        if (key < SKEY_LDP_CAT_BASE_POKEDEX) return false;
        int id = ldpCategoryIdForKey(key);
        return com.cobblecompanion.client.data.VariantCategory.byId(id) != null
            || com.cobblecompanion.data.LivingDexPlusRegistry.isAnyRegionSyntheticId(id)
            || com.cobblecompanion.data.LivingDexPlusRegistry.isAnyCosmeticSyntheticId(id);
    }

    private int settingsRowHeight(SettingsRow row) {
        // Nutzer-Fund: lange Zeilen (z.B. Creative-Dimensionsregeln-Liste "Spieler (WHITELIST):
        // dim1, dim2, ...") wurden bisher als einzeilige Überschrift gerendert und dadurch
        // abgeschnitten/überlappend statt umgebrochen - jetzt wie Options-Zeilen mehrzeilig.
        if (row.type == SETTINGS_ROW_HEADING) {
            int lines = settingsHeadingLines(row).size();
            return Math.max(SETTINGS_HEADING_H, lines * SETTINGS_OPTION_LINE_H + (SETTINGS_HEADING_H - SETTINGS_OPTION_LINE_H));
        }
        int lines = settingsLabelLines(row).size();
        int labelHeight = SETTINGS_OPTION_LABEL_Y_OFFSET + lines * SETTINGS_OPTION_LINE_H;
        return Math.max(SETTINGS_OPTION_ROW_H, labelHeight + SETTINGS_OPTION_ROW_GAP);
    }

    /** Umgebrochene Zeilen einer Überschrift-Zeile (siehe settingsLabelLines für Options-Zeilen) - nutzt die volle Content-Breite, keine Steuerelement-Spalte. */
    private List<String> settingsHeadingLines(SettingsRow row) {
        return wrapText(row.label, SETTINGS_HEADING_TEXT_SCALE, SETTINGS_HEADING_BOLD, SETTINGS_HEADING_UNIFORM_FONT,
            SETTINGS_CONTENT_WIDTH - SETTINGS_SCROLLBAR_GAP);
    }

    /** Umgebrochene Label-Zeilen einer Options-Zeile (statt Abschneiden - v.a. für längere deutsche Texte). */
    private List<String> settingsLabelLines(SettingsRow row) {
        int controlW = row.type == SETTINGS_ROW_CYCLE || row.type == SETTINGS_ROW_BUTTON ? SETTINGS_CYCLE_W : SETTINGS_TOGGLE_W;
        int labelMaxWidth = SETTINGS_CONTENT_WIDTH - controlW - SETTINGS_SCROLLBAR_GAP;
        return wrapText(row.label, SETTINGS_OPTION_LABEL_SCALE, SETTINGS_OPTION_LABEL_BOLD, SETTINGS_OPTION_LABEL_UNIFORM_FONT, labelMaxWidth);
    }

    /** Bricht Text wortweise um, sobald eine Zeile breiter als maxWidth würde - nie mitten im Wort. */
    List<String> wrapText(String text, float scale, boolean bold, boolean uniformFont, int maxWidth) {
        List<String> lines = new java.util.ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (!current.isEmpty() && smallLabelWidth(candidate, scale, bold, uniformFont) > maxWidth) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        if (!current.isEmpty()) lines.add(current.toString());
        if (lines.isEmpty()) lines.add("");
        return lines;
    }

    private int settingsContentHeight(List<SettingsRow> rows) {
        int total = 0;
        for (SettingsRow row : rows) total += settingsRowHeight(row);
        return total;
    }

    private int settingsMaxScroll(List<SettingsRow> rows) {
        return Math.max(0, settingsContentHeight(rows) - SETTINGS_CONTENT_VISIBLE_HEIGHT);
    }

    private static boolean readToggleValue(int key) {
        return switch (key) {
            case SKEY_TODO_SHOW_BUTTON -> ClientSettingsHelper.isTodoShowEvolveButton();
            case SKEY_TODO_CONFIRM -> ClientSettingsHelper.isTodoConfirmEvolution();
            case SKEY_TODO_SEND_OUT -> ClientSettingsHelper.isTodoSendOutBeforeEvolve();
            case SKEY_TODO_CLOSE -> ClientSettingsHelper.isTodoCloseOnEvolve();
            case SKEY_WHONEEDS_ONLY_FRIENDS -> ClientSettingsHelper.isWhoNeedsOnlyFriends();
            case SKEY_WHONEEDS_FRIENDS_FIRST -> ClientSettingsHelper.isWhoNeedsFriendsFirst();
            case SKEY_FRIENDS_AUTO_ACCEPT -> ClientSettingsHelper.isFriendsAutoAcceptRequests();
            case SKEY_FRIENDS_SHOW_OFFLINE -> ClientSettingsHelper.isFriendsShowOffline();
            case SKEY_FRIENDS_ALLOW_TELEPORT_TO_ME -> ClientSettingsHelper.isFriendsAllowTeleportToMe();
            // Server-Regeln kommen vom Server (nicht aus der lokalen Settings-Datei).
            case SKEY_SERVER_FORBID_GIFTING -> ClientServerRulesHelper.isForbidGifting();
            case SKEY_SERVER_ALLOW_TELEPORT_TO_FRIENDS -> ClientServerRulesHelper.isAllowTeleportToFriends();
            case SKEY_PC_SORT_HELPER -> ClientSettingsHelper.isPcSortHelperEnabled();
            case SKEY_PC_SLOT_CHECK -> ClientSettingsHelper.isPcSlotCheckEnabled();
            case SKEY_SERVER_EARN_FROM_NPC -> ClientServerRulesHelper.isEarnFromNPC();
            case SKEY_SERVER_EARN_FROM_WILD_POKEMON -> ClientServerRulesHelper.isEarnFromWildPokemon();
            case SKEY_SERVER_ONLINE_REWARD_ENABLED -> ClientServerRulesHelper.isOnlineRewardEnabled();
            case SKEY_GAMEMODES_CREATIVE_PURCHASE_ENABLED -> ClientCreativeTimeHelper.isPurchaseEnabled();
            case SKEY_GAMEMODES_INVENTORY_SYNC_ENABLED -> com.cobblecompanion.client.data.ClientGamemodeInventoryHelper.isEnabled();
            case SKEY_HIDE_OBSERVER_MESSAGES -> ClientSettingsHelper.isHideObserverMessages();
            default -> isLdpCategoryKey(key) && ClientSettingsHelper.isLivingDexPlusCategoryEnabled(ldpCategoryIdForKey(key));
        };
    }

    /** true, wenn die Option nur vom Server-Admin (OP) geändert werden darf und der Spieler das nicht ist. */
    private static boolean isSettingLocked(int key) {
        if (key == SKEY_SERVER_FORBID_GIFTING || key == SKEY_SERVER_ALLOW_TELEPORT_TO_FRIENDS) {
            return !ClientServerRulesHelper.canEdit();
        }
        if (key == SKEY_PC_BOX_COUNT) {
            return !com.cobblecompanion.client.data.ClientPcBoxCountHelper.canEdit();
        }
        if (key == SKEY_SERVER_EARN_FROM_NPC || key == SKEY_SERVER_EARN_FROM_WILD_POKEMON
            || key == SKEY_SERVER_INCOME_MULTIPLIER || key == SKEY_SERVER_ONLINE_REWARD_ENABLED
            || key == SKEY_SERVER_ONLINE_REWARD_INTERVAL || key == SKEY_SERVER_ONLINE_REWARD_AMOUNT) {
            return !ClientServerRulesHelper.canEdit();
        }
        if (key == SKEY_SERVER_CREATIVE_PRICE_PER_MINUTE || key == SKEY_GAMEMODES_CREATIVE_PURCHASE_ENABLED) {
            return !ClientCreativeTimeHelper.canEditPrice();
        }
        if (key == SKEY_GAMEMODES_INVENTORY_SYNC_ENABLED) {
            return !ClientAdminHelper.isAdminOp();
        }
        return false;
    }

    private static void applyToggle(int key) {
        switch (key) {
            case SKEY_TODO_SHOW_BUTTON -> ClientSettingsHelper.toggleTodoShowEvolveButton();
            case SKEY_TODO_CONFIRM -> ClientSettingsHelper.toggleTodoConfirmEvolution();
            case SKEY_TODO_SEND_OUT -> ClientSettingsHelper.toggleTodoSendOutBeforeEvolve();
            case SKEY_TODO_CLOSE -> ClientSettingsHelper.toggleTodoCloseOnEvolve();
            case SKEY_WHONEEDS_ONLY_FRIENDS -> ClientSettingsHelper.toggleWhoNeedsOnlyFriends();
            case SKEY_WHONEEDS_FRIENDS_FIRST -> ClientSettingsHelper.toggleWhoNeedsFriendsFirst();
            case SKEY_FRIENDS_AUTO_ACCEPT -> ClientSettingsHelper.toggleFriendsAutoAcceptRequests();
            case SKEY_FRIENDS_SHOW_OFFLINE -> ClientSettingsHelper.toggleFriendsShowOffline();
            case SKEY_FRIENDS_ALLOW_TELEPORT_TO_ME -> {
                ClientSettingsHelper.toggleFriendsAllowTeleportToMe();
                sendToServer(new TeleportPreferencePacket(ClientSettingsHelper.isFriendsAllowTeleportToMe()));
            }
            case SKEY_HIDE_OBSERVER_MESSAGES -> ClientSettingsHelper.toggleHideObserverMessages();
            // Server-Regeln werden nicht lokal umgeschaltet, sondern zum Server geschickt; der
            // Server ändert die Regel und broadcastet den neuen Stand (ServerRulesSyncPacket).
            case SKEY_SERVER_FORBID_GIFTING -> sendToServer(
                new ServerRuleChangePacket(ServerRulesManager.RULE_FORBID_GIFTING, !ClientServerRulesHelper.isForbidGifting()));
            case SKEY_SERVER_ALLOW_TELEPORT_TO_FRIENDS -> sendToServer(
                new ServerRuleChangePacket(ServerRulesManager.RULE_ALLOW_TELEPORT_TO_FRIENDS, !ClientServerRulesHelper.isAllowTeleportToFriends()));
            case SKEY_PC_SORT_HELPER -> ClientSettingsHelper.togglePcSortHelperEnabled();
            case SKEY_PC_SLOT_CHECK -> ClientSettingsHelper.togglePcSlotCheckEnabled();
            case SKEY_SERVER_EARN_FROM_NPC -> sendToServer(
                new ServerRuleChangePacket(ServerRulesManager.RULE_EARN_COBBLEDOLLARS_FROM_NPC, !ClientServerRulesHelper.isEarnFromNPC()));
            case SKEY_SERVER_EARN_FROM_WILD_POKEMON -> sendToServer(
                new ServerRuleChangePacket(ServerRulesManager.RULE_EARN_COBBLEDOLLARS_FROM_WILD_POKEMON, !ClientServerRulesHelper.isEarnFromWildPokemon()));
            case SKEY_SERVER_ONLINE_REWARD_ENABLED -> sendToServer(
                new com.cobblecompanion.network.OnlineRewardSettingsChangePacket(!ClientServerRulesHelper.isOnlineRewardEnabled(),
                    ClientServerRulesHelper.getOnlineRewardIntervalMinutes(), ClientServerRulesHelper.getOnlineRewardAmount()));
            case SKEY_GAMEMODES_CREATIVE_PURCHASE_ENABLED -> sendToServer(
                new com.cobblecompanion.network.CreativePurchaseEnabledChangePacket(!ClientCreativeTimeHelper.isPurchaseEnabled()));
            case SKEY_GAMEMODES_INVENTORY_SYNC_ENABLED -> sendToServer(
                new com.cobblecompanion.network.GamemodeInventorySyncTogglePacket(
                    !com.cobblecompanion.client.data.ClientGamemodeInventoryHelper.isEnabled()));
            default -> {
                if (isLdpCategoryKey(key)) {
                    ClientSettingsHelper.toggleLivingDexPlusCategory(ldpCategoryIdForKey(key));
                }
            }
        }
    }

    private static String readCycleText(int key) {
        if (key == SKEY_DEX_WAHL) {
            return switch (ClientSettingsHelper.getPcSortMode()) {
                case ClientSettingsHelper.PC_SORT_MODE_LIVING_DEX -> tr("cobblecompanion.settings.filter.livingdex");
                case ClientSettingsHelper.PC_SORT_MODE_LIVING_DEX_PLUS -> tr("cobblecompanion.settings.filter.livingdexplus");
                default -> tr("cobblecompanion.settings.filter.pokedex");
            };
        }
        if (key == SKEY_PC_SORT_START_BOX) {
            return tr("cobblecompanion.gui.todo.box", ClientSettingsHelper.getPcSortStartBox());
        }
        if (key == SKEY_PC_BOX_COUNT) {
            return String.valueOf(com.cobblecompanion.client.data.ClientPcBoxCountHelper.getBoxCount());
        }
        if (key == SKEY_SERVER_INCOME_MULTIPLIER) {
            return String.format(java.util.Locale.ROOT, "%.1fx", ClientServerRulesHelper.getIncomeMultiplier());
        }
        if (key == SKEY_SERVER_ONLINE_REWARD_INTERVAL) {
            return tr("cobblecompanion.settings.server.minutes", ClientServerRulesHelper.getOnlineRewardIntervalMinutes());
        }
        if (key == SKEY_SERVER_ONLINE_REWARD_AMOUNT) {
            return com.cobblecompanion.integrations.cobbledollars.CobbleDollarsScale.formatRaw(java.math.BigInteger.valueOf(ClientServerRulesHelper.getOnlineRewardAmount()));
        }
        if (key == SKEY_SERVER_CREATIVE_PRICE_PER_MINUTE) {
            return com.cobblecompanion.integrations.cobbledollars.CobbleDollarsScale.formatRaw(java.math.BigInteger.valueOf(ClientCreativeTimeHelper.getPricePerMinute()));
        }
        if (key == SKEY_GAMEMODES_NEW_DIMENSION) {
            List<String> dims = com.cobblecompanion.client.data.ClientDimensionGamemodeHelper.getAvailableDimensions();
            if (dims.isEmpty()) return "-";
            return dims.get(Math.floorMod(com.cobblecompanion.client.data.ClientDimensionGamemodeHelper.getNewRuleDimensionIndex(), dims.size()));
        }
        if (key == SKEY_GAMEMODES_NEW_MODE) {
            return GAMEMODES_CYCLE_TYPES[Math.floorMod(com.cobblecompanion.client.data.ClientDimensionGamemodeHelper.getNewRuleModeIndex(), GAMEMODES_CYCLE_TYPES.length)].getName();
        }
        return "";
    }

    private static void applyCycle(int key, int button) {
        if (key == SKEY_DEX_WAHL) ClientSettingsHelper.cyclePcSortMode(button != 1);
        if (key == SKEY_PC_SORT_START_BOX) {
            int maxBox = PCSortHelper.getKnownMaxBox();
            if (button == 1) { // Rechtsklick: vorherige Box
                int prev = ClientSettingsHelper.getPcSortStartBox() - 1;
                if (prev < 1) prev = maxBox;
                ClientSettingsHelper.setPcSortStartBox(prev);
            } else { // Linksklick: nächste Box
                int next = ClientSettingsHelper.getPcSortStartBox() + 1;
                if (next > maxBox) next = 1;
                ClientSettingsHelper.setPcSortStartBox(next);
            }
        }
        if (key == SKEY_PC_BOX_COUNT) {
            int current = com.cobblecompanion.client.data.ClientPcBoxCountHelper.getBoxCount();
            int next = Math.max(1, current + (button == 1 ? -1 : 1));
            sendToServer(new com.cobblecompanion.network.PcBoxCountChangePacket(next));
        }
        if (key == SKEY_SERVER_INCOME_MULTIPLIER) {
            double next = Math.max(0.0, ClientServerRulesHelper.getIncomeMultiplier() + (button == 1 ? -0.1 : 0.1));
            sendToServer(new com.cobblecompanion.network.CobbleDollarsIncomeMultiplierChangePacket(next));
        }
        if (key == SKEY_SERVER_ONLINE_REWARD_INTERVAL) {
            int next = Math.max(1, ClientServerRulesHelper.getOnlineRewardIntervalMinutes() + (button == 1 ? -5 : 5));
            sendToServer(new com.cobblecompanion.network.OnlineRewardSettingsChangePacket(
                ClientServerRulesHelper.isOnlineRewardEnabled(), next, ClientServerRulesHelper.getOnlineRewardAmount()));
        }
        if (key == SKEY_SERVER_ONLINE_REWARD_AMOUNT) {
            // Schrittweite ×10 (siehe CobbleDollarsScale) - entspricht weiterhin +/-100 ganzen Cobbledollars pro Klick.
            long next = Math.max(0, ClientServerRulesHelper.getOnlineRewardAmount() + (button == 1 ? -1000 : 1000));
            sendToServer(new com.cobblecompanion.network.OnlineRewardSettingsChangePacket(
                ClientServerRulesHelper.isOnlineRewardEnabled(), ClientServerRulesHelper.getOnlineRewardIntervalMinutes(), next));
        }
        if (key == SKEY_SERVER_CREATIVE_PRICE_PER_MINUTE) {
            // Schrittweite ×10 (siehe CobbleDollarsScale) - entspricht weiterhin +/-10 ganzen Cobbledollars pro Klick.
            long next = Math.max(0, ClientCreativeTimeHelper.getPricePerMinute() + (button == 1 ? -100 : 100));
            sendToServer(new com.cobblecompanion.network.CreativeTimePriceChangePacket(next));
        }
        if (key == SKEY_GAMEMODES_NEW_DIMENSION) {
            int size = Math.max(1, com.cobblecompanion.client.data.ClientDimensionGamemodeHelper.getAvailableDimensions().size());
            int next = Math.floorMod(com.cobblecompanion.client.data.ClientDimensionGamemodeHelper.getNewRuleDimensionIndex() + (button == 1 ? -1 : 1), size);
            com.cobblecompanion.client.data.ClientDimensionGamemodeHelper.setNewRuleDimensionIndex(next);
        }
        if (key == SKEY_GAMEMODES_NEW_MODE) {
            int next = Math.floorMod(com.cobblecompanion.client.data.ClientDimensionGamemodeHelper.getNewRuleModeIndex() + (button == 1 ? -1 : 1), GAMEMODES_CYCLE_TYPES.length);
            com.cobblecompanion.client.data.ClientDimensionGamemodeHelper.setNewRuleModeIndex(next);
        }
    }

    private static String settingsButtonText(int key) {
        if (key == SKEY_GAMEMODES_ADD_BUTTON) return tr("cobblecompanion.settings.gamemodes.new_rule_add");
        if (key >= GAMEMODES_REMOVE_KEY_BASE) return tr("cobblecompanion.settings.gamemodes.remove");
        return tr("cobblecompanion.settings.pc.autoname_run");
    }

    private static void applyButton(int key) {
        if (key == SKEY_PC_AUTONAME_BUTTON) PCSortHelper.autoNameBoxes();
        if (key == SKEY_GAMEMODES_ADD_BUTTON) {
            List<String> dims = com.cobblecompanion.client.data.ClientDimensionGamemodeHelper.getAvailableDimensions();
            if (!dims.isEmpty()) {
                String dimension = dims.get(Math.floorMod(com.cobblecompanion.client.data.ClientDimensionGamemodeHelper.getNewRuleDimensionIndex(), dims.size()));
                String mode = GAMEMODES_CYCLE_TYPES[Math.floorMod(com.cobblecompanion.client.data.ClientDimensionGamemodeHelper.getNewRuleModeIndex(), GAMEMODES_CYCLE_TYPES.length)].getName();
                sendToServer(new com.cobblecompanion.network.DimensionGamemodeSetPacket(dimension, mode));
            }
        }
        if (key >= GAMEMODES_REMOVE_KEY_BASE) {
            List<Map.Entry<String, String>> rules = com.cobblecompanion.client.data.ClientDimensionGamemodeHelper.getSortedRules();
            int idx = key - GAMEMODES_REMOVE_KEY_BASE;
            if (idx >= 0 && idx < rules.size()) {
                sendToServer(new com.cobblecompanion.network.DimensionGamemodeRemovePacket(rules.get(idx).getKey()));
            }
        }
    }

    /** Prüft Klicks auf Sub-Tab-Leiste, Scrollbar und die Options-Buttons der sichtbaren Zeilen. */
    private boolean handleSettingsClicks(double mouseX, double mouseY, int button) {
        int navX = guiLeft + SETTINGS_NAV_X;
        int navY = guiTop + SETTINGS_NAV_Y;
        for (int i = 0; i < SETTINGS_SUBTAB_NAMES.length; i++) {
            if (i == SETTINGS_TAB_GAMEMODES && !isGamemodesTabVisible()) continue;
            int btnY = navY + i * (SETTINGS_NAV_H + SETTINGS_NAV_GAP);
            if (isInRect(mouseX, mouseY, navX, btnY, SETTINGS_NAV_W, SETTINGS_NAV_H)) {
                settingsSubTab = i;
                settingsScrollAmount = 0;
                return true;
            }
        }

        List<SettingsRow> rows = buildSettingsRows();
        int contentX = guiLeft + SETTINGS_CONTENT_X;
        int contentTop = guiTop + SETTINGS_CONTENT_Y;
        int maxScroll = settingsMaxScroll(rows);

        int scrollbarX = contentX + SETTINGS_CONTENT_WIDTH + SETTINGS_SCROLLBAR_GAP;
        if (maxScroll > 0 && isMouseOverScrollbar(mouseX, mouseY, scrollbarX, SETTINGS_SCROLLBAR_WIDTH, contentTop, SETTINGS_CONTENT_VISIBLE_HEIGHT)) {
            settingsScrollbarDragging = true;
            settingsScrollAmount = scrollAmountFromMouseY(mouseY, contentTop, SETTINGS_CONTENT_VISIBLE_HEIGHT, maxScroll);
            return true;
        }

        int rowY = contentTop - (int) Math.round(settingsScrollAmount);
        for (SettingsRow row : rows) {
            int rowH = settingsRowHeight(row);
            if (row.type != SETTINGS_ROW_HEADING && rowY + rowH >= contentTop && rowY <= contentTop + SETTINGS_CONTENT_VISIBLE_HEIGHT) {
                int controlW = row.type == SETTINGS_ROW_CYCLE || row.type == SETTINGS_ROW_BUTTON ? SETTINGS_CYCLE_W : SETTINGS_TOGGLE_W;
                int controlX = contentX + SETTINGS_CONTENT_WIDTH - controlW;
                // Auf/Ab-Pfeile INNERHALB des Ja/Nein-Buttons (Nutzer-Korrektur: kollidierten
                // vorher mit dem Label-Text links davon) - Drittelung wie beim Rendern: links=hoch,
                // rechts=runter, mitte fällt zum normalen Toggle-Klick unten durch. Nur bei "Ja"
                // (aktivierte Kategorie) sichtbar/klickbar.
                if (isLdpCategoryKey(row.key) && ClientSettingsHelper.isLivingDexPlusCategoryEnabled(ldpCategoryIdForKey(row.key))
                    && isInRect(mouseX, mouseY, controlX, rowY, controlW, SETTINGS_TOGGLE_H)) {
                    int catId = ldpCategoryIdForKey(row.key);
                    int third = controlW / 3;
                    if (mouseX < controlX + third) {
                        java.util.List<Integer> order = new java.util.ArrayList<>(ClientSettingsHelper.getLivingDexPlusCategoryOrder());
                        int idx = order.indexOf(catId);
                        if (idx > 0) {
                            java.util.Collections.swap(order, idx, idx - 1);
                            ClientSettingsHelper.reorderLivingDexPlusCategories(order);
                            sendHomeSummaryRequest();
                        }
                        return true;
                    }
                    if (mouseX >= controlX + controlW - third) {
                        java.util.List<Integer> order = new java.util.ArrayList<>(ClientSettingsHelper.getLivingDexPlusCategoryOrder());
                        int idx = order.indexOf(catId);
                        if (idx >= 0 && idx < order.size() - 1) {
                            java.util.Collections.swap(order, idx, idx + 1);
                            ClientSettingsHelper.reorderLivingDexPlusCategories(order);
                            sendHomeSummaryRequest();
                        }
                        return true;
                    }
                    // mittleres Drittel -> fällt zum normalen Toggle-Klick unten durch.
                }
                // Drag-Start: Klick auf das LABEL (nicht den Toggle-Button) einer AKTIVIERTEN
                // Living-Dex+-Kategorie-Zeile beginnt einen möglichen Drag statt zu togglen.
                if (isLdpCategoryKey(row.key) && ClientSettingsHelper.isLivingDexPlusCategoryEnabled(ldpCategoryIdForKey(row.key))
                    && isInRect(mouseX, mouseY, contentX, rowY, controlX - contentX, rowH)) {
                    ldpDragCategoryId = ldpCategoryIdForKey(row.key);
                    ldpDragStartMouseY = mouseY;
                    ldpDragCurrentMouseY = mouseY;
                    ldpDragArmed = false;
                    ldpDragOrder = new java.util.ArrayList<>(ClientSettingsHelper.getLivingDexPlusCategoryOrder());
                    return true;
                }
                if (isInRect(mouseX, mouseY, controlX, rowY, controlW, SETTINGS_TOGGLE_H)) {
                    if (isSettingLocked(row.key)) return true; // OP-only: Klick abfangen, aber nicht ändern
                    if (row.type == SETTINGS_ROW_CYCLE) applyCycle(row.key, button);
                    else if (row.type == SETTINGS_ROW_BUTTON) applyButton(row.key);
                    else applyToggle(row.key);
                    // "Dex Wahl" sofort neu laden, statt erst beim nächsten ToDo-Tab-Wechsel - vermeidet
                    // die "Einstellung geändert, aber nichts passiert"-Verwirrung. Treibt jetzt sowohl
                    // ToDo/DexCompletion als auch PC-Sortierung/Home-Zähler an.
                    if (row.key == SKEY_DEX_WAHL) {
                        sendToServer(new com.cobblecompanion.network.DexCompletionRequestPacket(
                            ClientSettingsHelper.isModusPokedex(), dexCompletionLdpCategories()));
                        sendHomeSummaryRequest();
                    }
                    // Slot-Prüfung ein-/ausgeschaltet, Startbox geändert, oder eine Living-Dex+-
                    // Kategorie/Region getoggelt - Home-Zähler sofort neu anfordern (gleicher Grund wie oben).
                    if (row.key == SKEY_PC_SLOT_CHECK || row.key == SKEY_PC_SORT_START_BOX || isLdpCategoryKey(row.key)) {
                        sendHomeSummaryRequest();
                    }
                    return true;
                }
            }
            rowY += rowH;
        }
        return false;
    }

    // ===== Friends-Tab =====

    private void renderFriendsTab(GuiGraphics graphics, int mouseX, int mouseY) {
        renderFriendsDetailPanel(graphics, mouseX, mouseY);
        renderFriendsListPanel(graphics, mouseX, mouseY);
    }

    /** Linke Hälfte: Kopf, Name und Zähler des ausgewählten Freundes (bzw. Platzhaltertext). */
    private void renderFriendsDetailPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = guiLeft + FRIENDS_DETAIL_X;
        int y = guiTop + FRIENDS_DETAIL_Y;
        ClientFriendsHelper.FriendItem selected = ClientFriendsHelper.getSelected();
        if (selected == null) {
            drawScaledBoldText(graphics, tr("cobblecompanion.gui.friends.select"), x, y, DEFAULT_TEXT_SCALE, 0x808080);
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        net.minecraft.client.multiplayer.PlayerInfo info = (mc.getConnection() != null)
            ? mc.getConnection().getPlayerInfo(selected.uuid) : null;
        ResourceLocation skinTexture = (selected.online && info != null)
            ? info.getSkin().texture()
            : net.minecraft.client.resources.DefaultPlayerSkin.getDefaultTexture();

        if (!selected.online) RenderSystem.setShaderColor(0.5f, 0.5f, 0.5f, 1f);
        net.minecraft.client.gui.components.PlayerFaceRenderer.draw(graphics, skinTexture, x, y, FRIENDS_DETAIL_HEAD_SIZE);
        if (!selected.online) RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        drawScaledBoldText(graphics, selected.name, x, y + FRIENDS_DETAIL_NAME_OFFSET_Y, DEFAULT_TEXT_SCALE, 0xFFFFFF);

        int statY = y + FRIENDS_DETAIL_STAT_START_OFFSET_Y;
        drawScaledBoldText(graphics, tr("cobblecompanion.gui.friends.seen") + " " + selected.seenCount, x, statY, DEFAULT_TEXT_SCALE, 0xFFFFFF);
        statY += FRIENDS_DETAIL_STAT_ROW_H;
        drawScaledBoldText(graphics, tr("cobblecompanion.gui.friends.caught") + " " + selected.caughtCount, x, statY, DEFAULT_TEXT_SCALE, 0xFFFFFF);
        statY += FRIENDS_DETAIL_STAT_ROW_H;
        drawScaledBoldText(graphics, tr("cobblecompanion.gui.friends.living") + " " + selected.livingDexCount, x, statY, DEFAULT_TEXT_SCALE, 0xFFFFFF);

        // Schritt 5/6: Teleport-zu-Freund (nur wenn online + Freund erlaubt es) und
        // Pokemon-Verschenken (nur wenn online - beide Spieler müssen für den Transfer online sein).
        int[] btnYs = friendActionButtonYs(selected, statY);
        if (btnYs[0] >= 0) {
            renderFriendActionButton(graphics, mouseX, mouseY, x, btnYs[0], tr("cobblecompanion.gui.friends.teleport"));
        }
        if (btnYs[1] >= 0) {
            renderFriendActionButton(graphics, mouseX, mouseY, x, btnYs[1], tr("cobblecompanion.gui.friends.gift"));
        }
    }

    /**
     * Berechnet die Y-Positionen von Teleport-/Geschenk-Button unter der letzten Stat-Zeile
     * (statY) - gemeinsam von Render und Klick-Erkennung genutzt. -1 = Button wird nicht gezeigt
     * (Freund offline bzw. erlaubt kein Teleport).
     */
    private int[] friendActionButtonYs(ClientFriendsHelper.FriendItem f, int statY) {
        int nextY = statY + FRIENDS_DETAIL_STAT_ROW_H + FRIENDS_ACTION_BTN_GAP_Y;
        int teleportY = -1, giftY = -1;
        if (f.online && f.allowsTeleport) {
            teleportY = nextY;
            nextY += FRIENDS_ACTION_BTN_H + FRIENDS_ACTION_BTN_SPACING_Y;
        }
        if (f.online) {
            giftY = nextY;
        }
        return new int[]{teleportY, giftY};
    }

    private void renderFriendActionButton(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, String label) {
        boolean hovered = isInRect(mouseX, mouseY, x, y, FRIENDS_ACTION_BTN_W, FRIENDS_ACTION_BTN_H);
        graphics.blit(TODO_EVOLVE_BUTTON, x, y, FRIENDS_ACTION_BTN_W, FRIENDS_ACTION_BTN_H,
            0f, hovered ? SETTINGS_BTN_NATIVE_H : 0f,
            SETTINGS_BTN_NATIVE_W, SETTINGS_BTN_NATIVE_H, SETTINGS_BTN_NATIVE_W, SETTINGS_BTN_NATIVE_H * 2);
        int labelWidth = smallLabelWidth(label, FRIENDS_ACTION_BTN_TEXT_SCALE, true, true);
        drawSmallLabel(graphics, label, x + (FRIENDS_ACTION_BTN_W - labelWidth) / 2, y + (FRIENDS_ACTION_BTN_H - 8) / 2,
            FRIENDS_ACTION_BTN_TEXT_SCALE, 0xFFFFFF, true, true);
    }

    /** Rechte Hälfte: Suchfeld + Add-Button + offene Anfragen + gefilterte Freundesliste. */
    private void renderFriendsListPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        friendsSearchBox.render(graphics, mouseX, mouseY, 0f);

        int addBtnX = guiLeft + FRIENDS_PANEL_X + FRIENDS_SEARCH_W - FRIENDS_ADD_BTN_W;
        int addBtnY = guiTop + FRIENDS_ADD_BTN_Y;
        boolean addHovered = isInRect(mouseX, mouseY, addBtnX, addBtnY, FRIENDS_ADD_BTN_W, FRIENDS_ADD_BTN_H);
        graphics.blit(TODO_EVOLVE_BUTTON, addBtnX, addBtnY, FRIENDS_ADD_BTN_W, FRIENDS_ADD_BTN_H,
            0f, addHovered ? SETTINGS_BTN_NATIVE_H : 0f,
            SETTINGS_BTN_NATIVE_W, SETTINGS_BTN_NATIVE_H, SETTINGS_BTN_NATIVE_W, SETTINGS_BTN_NATIVE_H * 2);
        String addLabel = tr("cobblecompanion.gui.friends.add");
        int addLabelWidth = smallLabelWidth(addLabel, FRIENDS_ADD_BTN_TEXT_SCALE, true, true);
        drawSmallLabel(graphics, addLabel,
            addBtnX + (FRIENDS_ADD_BTN_W - addLabelWidth) / 2,
            addBtnY + (FRIENDS_ADD_BTN_H - Math.round(9 * FRIENDS_ADD_BTN_TEXT_SCALE)) / 2,
            FRIENDS_ADD_BTN_TEXT_SCALE, 0xFFFFFF, true, true);

        int rowX = guiLeft + FRIENDS_PANEL_X;
        int rowY = guiTop + FRIENDS_LIST_Y;

        // Offene Anfragen zuerst (mit kleiner Überschrift), sofern vorhanden.
        List<ClientFriendsHelper.RequestItem> requests = ClientFriendsHelper.getRequests();
        if (!requests.isEmpty()) {
            drawSmallLabel(graphics, tr("cobblecompanion.gui.friends.requests"), rowX, rowY, 1.0f, SETTINGS_HEADING_COLOR, true, true);
            rowY += FRIENDS_REQ_HEADING_H;
            for (ClientFriendsHelper.RequestItem req : requests) {
                renderRequestRow(graphics, rowX, rowY, req);
                rowY += FRIENDS_ROW_H;
            }
        }

        List<ClientFriendsHelper.FriendItem> friends = filteredFriends();
        if (friends.isEmpty()) {
            String msg = ClientFriendsHelper.getFriends().isEmpty()
                ? tr("cobblecompanion.gui.friends.none") : tr("cobblecompanion.gui.friends.nomatch");
            drawScaledBoldText(graphics, msg, rowX, rowY, DEFAULT_TEXT_SCALE, 0x808080);
        } else {
            for (ClientFriendsHelper.FriendItem friend : friends) {
                renderFriendRow(graphics, rowX, rowY, friend);
                rowY += FRIENDS_ROW_H;
            }
        }
    }

    /**
     * Spielernamen-Vorschläge während des Tippens im Suchfeld - rein clientseitig aus der
     * bereits vom Server bekannten Online-Spielerliste gefiltert (Tab-Liste), kein extra
     * Netzwerk-Roundtrip nötig. Eigener Name wird ausgeschlossen. Gerendert wird sie zentral in
     * renderActiveSearchSuggestions (oberste Ebene), nicht hier.
     */
    private List<String> friendSuggestions() {
        if (!friendsSearchBox.isFocused()) return java.util.List.of();
        String q = friendsSearchBox.getValue().trim().toLowerCase();
        if (q.isEmpty()) return java.util.List.of();
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return java.util.List.of();
        String selfName = mc.player != null ? mc.player.getName().getString() : "";

        List<String> result = new java.util.ArrayList<>();
        for (net.minecraft.client.multiplayer.PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            String name = info.getProfile().getName();
            if (name.equalsIgnoreCase(selfName) || name.equalsIgnoreCase(q)) continue;
            if (name.toLowerCase().startsWith(q)) result.add(name);
            if (result.size() >= SEARCH_SUGGEST_MAX) break;
        }
        return result;
    }

    // Wallet-Tab (Render/Klick/Drag/Scroll/Tastatur + Bestätigungs-Overlays): komplett ausgelagert
    // nach CobbleCompanion: CobbleDollars, siehe com.cobblecompanion.cobbledollars.client.WalletTabExtension.

    /** Y-Position, ab der die Freundesliste beginnt (nach der optionalen Anfragen-Sektion). */
    private int friendListStartY() {
        int y = guiTop + FRIENDS_LIST_Y;
        int reqCount = ClientFriendsHelper.getRequests().size();
        if (reqCount > 0) y += FRIENDS_REQ_HEADING_H + reqCount * FRIENDS_ROW_H;
        return y;
    }

    private List<ClientFriendsHelper.FriendItem> filteredFriends() {
        String query = friendsSearchBox.getValue().toLowerCase();
        boolean showOffline = ClientSettingsHelper.isFriendsShowOffline();
        List<ClientFriendsHelper.FriendItem> result = new java.util.ArrayList<>();
        for (ClientFriendsHelper.FriendItem f : ClientFriendsHelper.getFriends()) {
            if (!showOffline && !f.online) continue;
            if (query.isEmpty() || f.name.toLowerCase().contains(query)) result.add(f);
        }
        return result;
    }

    /** Zeichnet eine Anfrage-Zeile: Kopf + Name + Annehmen(✓, grün) / Ablehnen(✗, rot). */
    private void renderRequestRow(GuiGraphics graphics, int x, int y, ClientFriendsHelper.RequestItem req) {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.client.multiplayer.PlayerInfo info = (mc.getConnection() != null)
            ? mc.getConnection().getPlayerInfo(req.uuid) : null;
        ResourceLocation skinTexture = info != null ? info.getSkin().texture()
            : net.minecraft.client.resources.DefaultPlayerSkin.getDefaultTexture();
        net.minecraft.client.gui.components.PlayerFaceRenderer.draw(graphics, skinTexture, x, y, FRIENDS_HEAD_SIZE);

        drawScaledBoldText(graphics, req.name, x + FRIENDS_HEAD_SIZE + FRIENDS_NAME_OFFSET_X, y, BODY_TEXT_SCALE);
        drawScaledBoldText(graphics, "✓", reqAcceptBtnX(x), y, BODY_TEXT_SCALE, 0xFF55FF55);
        drawScaledBoldText(graphics, "✗", reqDeclineBtnX(x), y, BODY_TEXT_SCALE, 0xFFFF5555);
    }

    private int reqAcceptBtnX(int rowX) {
        return rowX + SEARCH_OVERLAY_W - FRIENDS_REQ_ACCEPT_OFFSET_X;
    }

    private int reqDeclineBtnX(int rowX) {
        return rowX + SEARCH_OVERLAY_W - FRIENDS_REQ_DECLINE_OFFSET_X;
    }

    /** Zeichnet eine Freundes-Zeile: Kopf+Badge+Name (wie renderPlayerNeedRow im Who-Needs-Tab) plus Entfernen-Button. */
    private void renderFriendRow(GuiGraphics graphics, int x, int y, ClientFriendsHelper.FriendItem friend) {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.client.multiplayer.PlayerInfo info = (mc.getConnection() != null)
            ? mc.getConnection().getPlayerInfo(friend.uuid) : null;
        ResourceLocation skinTexture = (friend.online && info != null)
            ? info.getSkin().texture()
            : net.minecraft.client.resources.DefaultPlayerSkin.getDefaultTexture();

        if (!friend.online) RenderSystem.setShaderColor(0.5f, 0.5f, 0.5f, 1f);
        net.minecraft.client.gui.components.PlayerFaceRenderer.draw(graphics, skinTexture, x, y, FRIENDS_HEAD_SIZE);
        if (!friend.online) RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        ResourceLocation badge = friend.online ? ICON_ONLINE : ICON_OFFLINE;
        graphics.blit(badge,
            x + FRIENDS_HEAD_SIZE - FRIENDS_BADGE_SIZE + FRIENDS_BADGE_OFFSET_X,
            y + FRIENDS_HEAD_SIZE - FRIENDS_BADGE_SIZE + FRIENDS_BADGE_OFFSET_Y,
            FRIENDS_BADGE_SIZE, FRIENDS_BADGE_SIZE,
            0f, 0f, 16, 16, 16, 16);

        drawScaledBoldText(graphics, friend.name, x + FRIENDS_HEAD_SIZE + FRIENDS_NAME_OFFSET_X, y, BODY_TEXT_SCALE);
        drawScaledBoldText(graphics, "✗", friendRemoveBtnX(x), y, BODY_TEXT_SCALE, 0xFFFF5555);
    }

    private int friendRemoveBtnX(int rowX) {
        return rowX + SEARCH_OVERLAY_W - FRIENDS_REMOVE_SIZE - FRIENDS_REMOVE_OFFSET_X;
    }

    /** Prüft Klicks auf Autocomplete, Add-Button, Anfrage-Buttons, Zeilen-Auswahl und Entfernen-Button. */
    private boolean handleFriendsClicks(double mouseX, double mouseY) {
        // Teleport-/Geschenk-Button im Detail-Panel (linke Hälfte) - vor allem anderen prüfen.
        ClientFriendsHelper.FriendItem selected = ClientFriendsHelper.getSelected();
        if (selected != null) {
            int detailX = guiLeft + FRIENDS_DETAIL_X;
            int statY = guiTop + FRIENDS_DETAIL_Y + FRIENDS_DETAIL_STAT_START_OFFSET_Y + 2 * FRIENDS_DETAIL_STAT_ROW_H;
            int[] btnYs = friendActionButtonYs(selected, statY);
            if (btnYs[0] >= 0 && isInRect(mouseX, mouseY, detailX, btnYs[0], FRIENDS_ACTION_BTN_W, FRIENDS_ACTION_BTN_H)) {
                sendToServer(new TeleportToFriendPacket(selected.uuid));
                return true;
            }
            if (btnYs[1] >= 0 && isInRect(mouseX, mouseY, detailX, btnYs[1], FRIENDS_ACTION_BTN_W, FRIENDS_ACTION_BTN_H)) {
                giftOverlayTargetUuid = selected.uuid;
                giftOverlayTargetName = selected.name;
                sendToServer(new MyPartyRequestPacket());
                return true;
            }
        }

        int rowX = guiLeft + FRIENDS_PANEL_X;

        // Autocomplete-Vorschläge liegen optisch über allem anderen -> zuerst prüfen.
        int sx = guiLeft + FRIENDS_PANEL_X;
        int sy = guiTop + FRIENDS_ADD_BTN_Y + FRIENDS_ADD_BTN_H + 2;
        if (handleSuggestionsClick(mouseX, mouseY, sx, sy, FRIENDS_SEARCH_W, friendSuggestions(), friendsSearchBox)) return true;

        // Add-Button: Freundschaftsanfrage per Suchfeld-Namen senden.
        int addBtnX = rowX + FRIENDS_SEARCH_W - FRIENDS_ADD_BTN_W;
        int addBtnY = guiTop + FRIENDS_ADD_BTN_Y;
        if (isInRect(mouseX, mouseY, addBtnX, addBtnY, FRIENDS_ADD_BTN_W, FRIENDS_ADD_BTN_H)) {
            String name = friendsSearchBox.getValue().trim();
            if (!name.isEmpty()) {
                sendToServer(FriendActionPacket.requestByName(name));
                friendsSearchBox.setValue("");
            }
            return true;
        }

        // Anfragen-Sektion: Annehmen/Ablehnen.
        List<ClientFriendsHelper.RequestItem> requests = ClientFriendsHelper.getRequests();
        int rowY = guiTop + FRIENDS_LIST_Y;
        if (!requests.isEmpty()) {
            rowY += FRIENDS_REQ_HEADING_H;
            for (ClientFriendsHelper.RequestItem req : requests) {
                if (isInRect(mouseX, mouseY, reqAcceptBtnX(rowX), rowY, FRIENDS_REQ_BTN_SIZE, FRIENDS_REQ_BTN_SIZE)) {
                    sendToServer(FriendActionPacket.forUuid(FriendActionPacket.ACCEPT, req.uuid));
                    return true;
                }
                if (isInRect(mouseX, mouseY, reqDeclineBtnX(rowX), rowY, FRIENDS_REQ_BTN_SIZE, FRIENDS_REQ_BTN_SIZE)) {
                    sendToServer(FriendActionPacket.forUuid(FriendActionPacket.DECLINE, req.uuid));
                    return true;
                }
                rowY += FRIENDS_ROW_H;
            }
        }

        // Freundesliste: Entfernen bzw. Auswahl.
        List<ClientFriendsHelper.FriendItem> friends = filteredFriends();
        rowY = friendListStartY();
        for (ClientFriendsHelper.FriendItem friend : friends) {
            if (isInRect(mouseX, mouseY, friendRemoveBtnX(rowX), rowY, FRIENDS_REMOVE_SIZE, FRIENDS_REMOVE_SIZE)) {
                // Nicht direkt entfernen - erst Sicherheitsabfrage-Overlay öffnen.
                pendingRemoveFriendUuid = friend.uuid;
                pendingRemoveFriendName = friend.name;
                return true;
            }
            if (isInRect(mouseX, mouseY, rowX, rowY, SEARCH_OVERLAY_W, FRIENDS_ROW_H)) {
                ClientFriendsHelper.setSelectedFriend(friend.uuid);
                // Frischen Sync anfordern, damit die Übersicht nicht veraltete Zähler zeigt,
                // falls der Freund seit dem letzten Sync (Tab-Öffnen/Login) etwas gefangen hat.
                sendToServer(new FriendsListRequestPacket());
                return true;
            }
            rowY += FRIENDS_ROW_H;
        }
        return false;
    }

    // ===== Professor-Tab (Op/AdminOp) =====
    // Anders als die meisten anderen Tabs liegt hier die Suche+Liste LINKS und die
    // Auswahl/Aktionen RECHTS - explizit vom Nutzer so gewünscht (siehe Roadmap-Memory).

    private void renderProfessorTab(GuiGraphics graphics, int mouseX, int mouseY) {
        checkProfessorSubScreenUpdate();
        if (professorSubScreen != null) {
            try {
                if (professorViewingLivingDex) {
                    // Seen/Caught/Living-Zähler bei jedem Frame neu berechnen (Region-gefiltert wie
                    // beim eigenen Living-Dex-Tab, siehe renderLivingDexTab()) - liest dabei die
                    // temporär getauschten Daten (Pokédex + Spezies-Menge des Zielspielers).
                    ClientLivingDexHelper.loadData(getSelectedRegion(professorSubScreen));
                }
                professorSubScreen.render(graphics, mouseX, mouseY, 0);
                // Cobblemons Summary-Fenster rendert das 3D-Modell-Widget mit aktiviertem
                // Depth-Test und lässt diesen GL-Zustand stehen (gleiches Muster wie beim
                // Living-Dex-Tab, siehe renderLivingDexTab()) - ohne Reset würde unser
                // "Zurück"-Text dahinter verschwinden.
                RenderSystem.disableDepthTest();
                if (professorViewingLivingDex) {
                    renderCaughtLivingCounter(graphics);
                    renderLivingIconsOverlay(graphics, professorSubScreen);
                }
            } catch (Exception e) {
                CobbleCompanion.LOGGER.error("[CC] Error rendering Professor Summary", e);
            }
            // PC-Ansicht (PCGUI) hat unten rechts Cobblemons eigenen Tür-Knopf, der jetzt auf
            // unsere Zurück-Navigation umgebogen ist (siehe buildProfessorPCScreen()) - für
            // Pokédex/Living-Dex bauen wir denselben Knopf (com.cobblemon...gui.ExitButton, exakt
            // dieselbe Klasse, die Cobblemon selbst für den PC-Tür-Knopf nutzt) hier manuell nach.
            if (!(professorSubScreen instanceof com.cobblemon.mod.common.client.gui.pc.PCGUI)) {
                renderProfessorBackExitButton(graphics, mouseX, mouseY);
            }
            // Eigenes Greifen/Ablegen-System (siehe mouseClicked()) hat keine eigene "Pokemon
            // schwebt am Cursor"-Darstellung wie Cobblemons natives Drag&Drop (das läuft hier ja
            // bewusst nicht) - stattdessen ein simpler Text-Hinweis direkt am Mauszeiger.
            if (professorGrabbedPokemon != null) {
                String label = "[" + speciesDisplayName(professorGrabbedPokemon.getSpecies()) + "]";
                drawSmallLabel(graphics, label, mouseX + 10, mouseY + 10, 1.0f, 0xFFFFFF00, true, true);
            }
            return;
        }
        if (professorViewingRct) {
            renderProfessorRctPanel(graphics, mouseX, mouseY);
            return;
        }
        renderProfessorListPanel(graphics, mouseX, mouseY);
        renderProfessorDetailPanel(graphics, mouseX, mouseY);
    }

    /** Eigene Vollbreiten-Liste der RCT-Trainerpfade des ausgewählten Spielers (kein Cobblemon-GUI). */
    private void renderProfessorRctPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = guiLeft + RCT_PANEL_X;
        int y = guiTop + RCT_PANEL_Y;
        ClientProfessorHelper.PlayerItem selected = ClientProfessorHelper.getSelected();
        String targetName = selected != null ? selected.name : "";

        // Kopf: Zurück-Button links, Titel mittig, "Alles zurücksetzen" rechts.
        boolean backHovered = isInRect(mouseX, mouseY, x, y, RCT_BACK_BTN_W, RCT_BACK_BTN_H);
        graphics.blit(TODO_EVOLVE_BUTTON, x, y, RCT_BACK_BTN_W, RCT_BACK_BTN_H,
            0f, backHovered ? SETTINGS_BTN_NATIVE_H : 0f,
            SETTINGS_BTN_NATIVE_W, SETTINGS_BTN_NATIVE_H, SETTINGS_BTN_NATIVE_W, SETTINGS_BTN_NATIVE_H * 2);
        String backLabel = tr("cobblecompanion.gui.professor.back");
        int backLabelW = smallLabelWidth(backLabel, 1.0f, true, true);
        drawSmallLabel(graphics, backLabel, x + (RCT_BACK_BTN_W - backLabelW) / 2, y + (RCT_BACK_BTN_H - 8) / 2, 1.0f, 0xFFFFFF, true, true);

        String title = tr("cobblecompanion.gui.professor.rct_title", targetName);
        int titleW = smallLabelWidth(title, 1.0f, true, true);
        drawSmallLabel(graphics, title, x + (RCT_PANEL_W - titleW) / 2, y + (RCT_BACK_BTN_H - 8) / 2, 1.0f, 0xFFFFFF, true, true);

        boolean online = ClientProfessorHelper.isRctTargetOnline();
        int resetAllX = x + RCT_PANEL_W - RCT_RESET_ALL_BTN_W;
        if (online) {
            renderConfirmButton(graphics, resetAllX, y, RCT_RESET_ALL_BTN_W, RCT_RESET_ALL_BTN_H,
                tr("cobblecompanion.gui.professor.rct_reset_all"), 0xFFFF5555, mouseX, mouseY);
        }

        if (!online) {
            String hint = tr("cobblecompanion.gui.professor.rct_offline");
            drawSmallLabel(graphics, hint, x, y + RCT_LIST_Y_OFFSET, 1.0f, 0x808080, true, true);
            return;
        }

        List<ClientProfessorHelper.RctSeriesItem> series = ClientProfessorHelper.getRctSeries();
        if (series.isEmpty()) {
            drawSmallLabel(graphics, tr("cobblecompanion.gui.professor.rct_empty"), x, y + RCT_LIST_Y_OFFSET, 1.0f, 0x808080, true, true);
            return;
        }

        int listTop = y + RCT_LIST_Y_OFFSET;
        int visibleHeight = (guiTop + GUI_HEIGHT - PROFESSOR_BOTTOM_MARGIN) - listTop;
        int maxScroll = Math.max(0, series.size() * (RCT_ROW_H + RCT_ROW_GAP) - visibleHeight);
        rctScrollAmount = Math.max(0, Math.min(maxScroll, rctScrollAmount));

        graphics.enableScissor(x, listTop, x + RCT_PANEL_W, listTop + visibleHeight);
        int rowY = listTop - (int) Math.round(rctScrollAmount);
        for (ClientProfessorHelper.RctSeriesItem entry : series) {
            if (rowY + RCT_ROW_H >= listTop && rowY <= listTop + visibleHeight) {
                boolean rowHovered = isInRect(mouseX, mouseY, x, rowY, RCT_PANEL_W - RCT_ROW_RESET_BTN_W - 6, RCT_ROW_H);
                if (rowHovered) graphics.fill(x, rowY, x + RCT_PANEL_W, rowY + RCT_ROW_H, 0x20FFFFFF);

                String status = entry.completed
                    ? tr("cobblecompanion.gui.professor.rct_completed")
                    : tr("cobblecompanion.gui.professor.rct_open");
                int statusColor = entry.completed ? 0xFF55FF55 : 0xFF808080;
                drawSmallLabel(graphics, entry.title, x + 2, rowY + (RCT_ROW_H - 8) / 2, 1.0f, 0xFFFFFF, true, true);
                int statusW = smallLabelWidth(status, 1.0f, true, true);
                int resetBtnX = x + RCT_PANEL_W - RCT_ROW_RESET_BTN_W;
                drawSmallLabel(graphics, status, resetBtnX - statusW - 6, rowY + (RCT_ROW_H - 8) / 2, 1.0f, statusColor, true, true);
                renderConfirmButton(graphics, resetBtnX, rowY + (RCT_ROW_H - RCT_ROW_RESET_BTN_H) / 2,
                    RCT_ROW_RESET_BTN_W, RCT_ROW_RESET_BTN_H, tr("cobblecompanion.gui.professor.rct_reset"), 0xFFFF5555, mouseX, mouseY);
            }
            rowY += RCT_ROW_H + RCT_ROW_GAP;
        }
        graphics.disableScissor();

        if (maxScroll > 0) {
            int scrollbarX = x + RCT_PANEL_W + RCT_SCROLLBAR_GAP;
            renderScrollbar(graphics, scrollbarX, RCT_SCROLLBAR_WIDTH, listTop, visibleHeight, rctScrollAmount, maxScroll);
        }
    }

    /** Klicks auf Zurück/Alles-zurücksetzen/Zeilen-Reset-Buttons + Scrollbar der RCT-Liste. */
    private boolean handleProfessorRctClicks(double mouseX, double mouseY) {
        int x = guiLeft + RCT_PANEL_X;
        int y = guiTop + RCT_PANEL_Y;

        if (isInRect(mouseX, mouseY, x, y, RCT_BACK_BTN_W, RCT_BACK_BTN_H)) {
            professorViewingRct = false;
            return true;
        }

        boolean online = ClientProfessorHelper.isRctTargetOnline();
        int resetAllX = x + RCT_PANEL_W - RCT_RESET_ALL_BTN_W;
        if (online && isInRect(mouseX, mouseY, resetAllX, y, RCT_RESET_ALL_BTN_W, RCT_RESET_ALL_BTN_H)) {
            pendingRctReset = com.cobblecompanion.network.AdminResetRctPacket.ALL_SERIES;
            return true;
        }
        if (!online) return true;

        List<ClientProfessorHelper.RctSeriesItem> series = ClientProfessorHelper.getRctSeries();
        int listTop = y + RCT_LIST_Y_OFFSET;
        int visibleHeight = (guiTop + GUI_HEIGHT - PROFESSOR_BOTTOM_MARGIN) - listTop;
        int maxScroll = Math.max(0, series.size() * (RCT_ROW_H + RCT_ROW_GAP) - visibleHeight);

        if (maxScroll > 0) {
            int scrollbarX = x + RCT_PANEL_W + RCT_SCROLLBAR_GAP;
            if (isMouseOverScrollbar(mouseX, mouseY, scrollbarX, RCT_SCROLLBAR_WIDTH, listTop, visibleHeight)) {
                rctScrollAmount = scrollAmountFromMouseY(mouseY, listTop, visibleHeight, maxScroll);
                return true;
            }
        }

        int rowY = listTop - (int) Math.round(rctScrollAmount);
        int resetBtnX = x + RCT_PANEL_W - RCT_ROW_RESET_BTN_W;
        for (ClientProfessorHelper.RctSeriesItem entry : series) {
            if (rowY >= listTop && rowY <= listTop + visibleHeight
                    && isInRect(mouseX, mouseY, resetBtnX, rowY + (RCT_ROW_H - RCT_ROW_RESET_BTN_H) / 2, RCT_ROW_RESET_BTN_W, RCT_ROW_RESET_BTN_H)) {
                pendingRctReset = entry.id;
                return true;
            }
            rowY += RCT_ROW_H + RCT_ROW_GAP;
        }
        return true;
    }

    /** Ja/Nein-Bestätigung für einen RCT-Reset (einzelner Pfad oder ALL_SERIES). */
    private void renderRctResetConfirmOverlay(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(0, 0, this.width, this.height, 0xC0000000);
        int boxX = guiLeft + (GUI_WIDTH - CONFIRM_BOX_W) / 2;
        int boxY = guiTop + (GUI_HEIGHT - CONFIRM_BOX_H) / 2;
        graphics.fill(boxX, boxY, boxX + CONFIRM_BOX_W, boxY + CONFIRM_BOX_H, 0xFF202020);
        graphics.fill(boxX, boxY, boxX + CONFIRM_BOX_W, boxY + 1, 0xFFFF5555);
        graphics.fill(boxX, boxY + CONFIRM_BOX_H - 1, boxX + CONFIRM_BOX_W, boxY + CONFIRM_BOX_H, 0xFFFF5555);

        boolean all = com.cobblecompanion.network.AdminResetRctPacket.ALL_SERIES.equals(pendingRctReset);
        String question = tr(all ? "cobblecompanion.gui.professor.rct_confirm_all" : "cobblecompanion.gui.professor.rct_confirm_series");
        int qWidth = smallLabelWidth(question, 1.0f, true, true);
        drawSmallLabel(graphics, question, boxX + (CONFIRM_BOX_W - qWidth) / 2, boxY + 10, 1.0f, 0xFFFFFF, true, true);

        int btnY = boxY + CONFIRM_BOX_H - CONFIRM_BTN_H - 8;
        int yesX = boxX + CONFIRM_BOX_W / 2 - CONFIRM_BTN_W - CONFIRM_BTN_GAP / 2;
        int noX = boxX + CONFIRM_BOX_W / 2 + CONFIRM_BTN_GAP / 2;
        renderConfirmButton(graphics, yesX, btnY, tr("cobblecompanion.gui.confirm.yes"), 0xFF55FF55, mouseX, mouseY);
        renderConfirmButton(graphics, noX, btnY, tr("cobblecompanion.gui.confirm.no"), 0xFFFF5555, mouseX, mouseY);
    }

    private boolean handleRctResetConfirmClick(double mouseX, double mouseY) {
        int boxX = guiLeft + (GUI_WIDTH - CONFIRM_BOX_W) / 2;
        int boxY = guiTop + (GUI_HEIGHT - CONFIRM_BOX_H) / 2;
        int btnY = boxY + CONFIRM_BOX_H - CONFIRM_BTN_H - 8;
        int yesX = boxX + CONFIRM_BOX_W / 2 - CONFIRM_BTN_W - CONFIRM_BTN_GAP / 2;
        int noX = boxX + CONFIRM_BOX_W / 2 + CONFIRM_BTN_GAP / 2;
        if (isInRect(mouseX, mouseY, yesX, btnY, CONFIRM_BTN_W, CONFIRM_BTN_H)) {
            ClientProfessorHelper.PlayerItem selected = ClientProfessorHelper.getSelected();
            if (selected != null && pendingRctReset != null) {
                sendToServer(new com.cobblecompanion.network.AdminResetRctPacket(selected.uuid, pendingRctReset));
            }
        }
        pendingRctReset = null;
        return true;
    }

    // Justierschrauben: Abstand des nachgebauten Tür-Knopfs (26x13, siehe ExitButton) von der
    // unteren rechten Ecke unseres eigenen Rahmens.
    private static final int PROFESSOR_EXIT_BTN_MARGIN_X = 0;
    private static final int PROFESSOR_EXIT_BTN_MARGIN_Y = 0;
    private static final int PROFESSOR_EXIT_BTN_W = 26;
    private static final int PROFESSOR_EXIT_BTN_H = 13;

    // Zuletzt gerenderte Instanz - mouseClicked() nutzt exakt dasselbe Objekt (gleiche Position),
    // daher reicht Cobblemons eigenes isHovered()/mouseClicked() für den Klick-Treffer.
    private com.cobblemon.mod.common.client.gui.ExitButton professorBackExitButton;

    private void renderProfessorBackExitButton(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = guiLeft + GUI_WIDTH - PROFESSOR_EXIT_BTN_W - PROFESSOR_EXIT_BTN_MARGIN_X;
        int y = guiTop + GUI_HEIGHT - PROFESSOR_EXIT_BTN_H - PROFESSOR_EXIT_BTN_MARGIN_Y;
        professorBackExitButton = new com.cobblemon.mod.common.client.gui.ExitButton(x, y, btn -> {
            restoreLocalPokedexIfNeeded();
            restoreLocalLivingDexIfNeeded();
            professorViewingLivingDex = false;
            professorSubScreen = null;
            professorGrabbedPokemon = null;
        });
        professorBackExitButton.render(graphics, mouseX, mouseY, 0);
    }

    /**
     * Baut Cobblemons PCGUI/PokedexGUI neu, sobald ClientProfessorHelper eine neue Antwort hat
     * (unabhängige Versionszähler, da PC/Pokédex/Living-Dex-Knopf getrennte Packets/Antworten
     * auslösen). Team wurde bewusst entfernt - die PC-Ansicht zeigt ohnehin immer beide Storages
     * (Box + Party) gleichzeitig, ein eigener Team-Knopf war redundant und sein eingebettetes
     * Summary-Fenster hatte kein Live-Rebind für unser Verschieben-System (siehe alte Team-
     * Implementierung in der Git-Historie).
     */
    private void checkProfessorSubScreenUpdate() {
        int pcVersion = ClientProfessorHelper.getPCDataVersion();
        if (pcVersion != professorPCDataVersionSeen) {
            professorPCDataVersionSeen = pcVersion;
            restoreLocalPokedexIfNeeded();
            restoreLocalLivingDexIfNeeded();
            professorViewingLivingDex = false;
            professorSubScreen = buildProfessorPCScreen();
            return;
        }
        int pokedexVersion = ClientProfessorHelper.getPokedexDataVersion();
        if (pokedexVersion != professorPokedexDataVersionSeen) {
            professorPokedexDataVersionSeen = pokedexVersion;
            restoreLocalLivingDexIfNeeded();
            professorViewingLivingDex = false;
            professorSubScreen = buildProfessorPokedexScreen();
            return;
        }
        int livingDexVersion = ClientProfessorHelper.getLivingDexDataVersion();
        if (livingDexVersion != professorLivingDexDataVersionSeen) {
            professorLivingDexDataVersionSeen = livingDexVersion;
            professorViewingLivingDex = true;
            professorSubScreen = buildProfessorLivingDexScreen();
        }
    }

    /**
     * Macht den temporären Tausch von CobblemonClient.INSTANCE.clientPokedexData rückgängig
     * (siehe buildProfessorPokedexScreen() UND ensureTypesPokedexOverride() - beide teilen sich
     * dieselben Swap-Felder, siehe dortiger Kommentar). Muss aufgerufen werden, bevor die
     * Professor-Ansicht verlassen wird, auf Team/PC gewechselt wird, oder der Types-Tab-Suche
     * verlassen wird - sonst zeigt der echte Pokédex/Living-Dex-Tab fälschlich fremde bzw.
     * gefälschte Daten.
     */
    private void restoreLocalPokedexIfNeeded() {
        typesPokedexOverrideSpeciesId = null;
        if (!professorPokedexSwapActive) return;
        com.cobblemon.mod.common.client.CobblemonClient.INSTANCE.setClientPokedexData(savedLocalPokedexManager);
        professorPokedexSwapActive = false;
        savedLocalPokedexManager = null;
    }

    /**
     * Macht den temporären Tausch von ClientLivingDexHelpers Spezies-Menge rückgängig (siehe
     * buildProfessorLivingDexScreen()) - gleiches Muster wie restoreLocalPokedexIfNeeded().
     */
    private void restoreLocalLivingDexIfNeeded() {
        if (!professorLivingDexSwapActive) return;
        ClientLivingDexHelper.setLivingDexSpecies(savedLocalLivingDexSpecies);
        professorLivingDexSwapActive = false;
        savedLocalLivingDexSpecies = null;
    }

    /**
     * Baut per Reflection Cobblemons echtes Pokédex-Fenster (gleiche Technik wie initPokedex()),
     * aber mit einem Trick: Cobblemons PokedexGUI nimmt (anders als PCGUI/Summary) keine Daten im
     * Konstruktor entgegen, sondern liest sie live aus dem Singleton
     * CobblemonClient.INSTANCE.clientPokedexData, das eigentlich an den LOKALEN Spieler gebunden
     * ist. Wir bauen aus den vom Server per PokedexManager.Companion.CODEC gesendeten NBT-Daten
     * (siehe ProfessorPokedexResponsePacket) einen echten PokedexManager, wandeln ihn per
     * toClientData() in einen ClientPokedexManager um und tauschen ihn temporär in dieses
     * Singleton ein - der eigene echte Stand wird vorher gesichert (siehe
     * restoreLocalPokedexIfNeeded()) und beim Verlassen der Ansicht wiederhergestellt.
     */
    private Screen buildProfessorPokedexScreen() {
        return buildPokedexGuiFromTag(ClientProfessorHelper.getPokedexTag(), "Pokedex");
    }

    /**
     * Living-Dex-Variante von buildProfessorPokedexScreen(): dieselbe eingebettete PokedexGUI,
     * zusätzlich wird ClientLivingDexHelpers Spezies-Menge (für die Blatt-Icon-Overlays, siehe
     * renderProfessorTab()) temporär auf die des Zielspielers umgeschaltet - Sicherung/
     * Wiederherstellung analog zum Pokédex-Singleton-Tausch, siehe restoreLocalLivingDexIfNeeded().
     */
    private Screen buildProfessorLivingDexScreen() {
        if (!professorLivingDexSwapActive) {
            savedLocalLivingDexSpecies = ClientLivingDexHelper.getLivingDexSpeciesSnapshot();
            professorLivingDexSwapActive = true;
        }
        ClientLivingDexHelper.setLivingDexSpecies(new java.util.HashSet<>(ClientProfessorHelper.getLivingDexSpecies()));
        return buildPokedexGuiFromTag(ClientProfessorHelper.getLivingDexPokedexTag(), "LivingDex");
    }

    /**
     * Gemeinsamer Aufbau von Cobblemons echtem Pokédex-Fenster (gleiche Technik wie initPokedex()),
     * aber mit einem Trick: Cobblemons PokedexGUI nimmt (anders als PCGUI/Summary) keine Daten im
     * Konstruktor entgegen, sondern liest sie live aus dem Singleton
     * CobblemonClient.INSTANCE.clientPokedexData, das eigentlich an den LOKALEN Spieler gebunden
     * ist. Wir bauen aus den vom Server per PokedexManager.Companion.CODEC gesendeten NBT-Daten
     * (siehe ProfessorPokedexResponsePacket/ProfessorLivingDexResponsePacket) einen echten
     * PokedexManager, wandeln ihn per toClientData() in einen ClientPokedexManager um und tauschen
     * ihn temporär in dieses Singleton ein - der eigene echte Stand wird vorher gesichert (siehe
     * restoreLocalPokedexIfNeeded()) und beim Verlassen der Ansicht wiederhergestellt. Wird sowohl
     * von der normalen Pokédex- als auch der Living-Dex-Ansicht genutzt (identische GUI-Klasse).
     */
    private Screen buildPokedexGuiFromTag(net.minecraft.nbt.CompoundTag tag, String logLabel) {
        if (tag == null || tag.isEmpty()) return null;
        try {
            net.minecraft.core.RegistryAccess registryAccess = Minecraft.getInstance().level.registryAccess();
            net.minecraft.resources.RegistryOps<net.minecraft.nbt.Tag> ops =
                net.minecraft.resources.RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE, registryAccess);
            com.mojang.serialization.DataResult<com.mojang.datafixers.util.Pair<com.cobblemon.mod.common.api.pokedex.PokedexManager, net.minecraft.nbt.Tag>> decoded =
                com.cobblemon.mod.common.api.pokedex.PokedexManager.Companion.getCODEC().decode(ops, tag);
            com.mojang.datafixers.util.Pair<com.cobblemon.mod.common.api.pokedex.PokedexManager, net.minecraft.nbt.Tag> pair =
                decoded.result().orElse(null);
            if (pair == null) return null;
            com.cobblemon.mod.common.api.storage.player.client.ClientPokedexManager clientMgr = pair.getFirst().toClientData();

            if (!professorPokedexSwapActive) {
                savedLocalPokedexManager = com.cobblemon.mod.common.client.CobblemonClient.INSTANCE.getClientPokedexData();
                professorPokedexSwapActive = true;
            }
            com.cobblemon.mod.common.client.CobblemonClient.INSTANCE.setClientPokedexData(clientMgr);

            Class<?> typeClass = Class.forName("com.cobblemon.mod.common.client.pokedex.PokedexType");
            String color = getPokedexColor().toUpperCase();
            Object pokedexType = null;
            for (Object enumConst : typeClass.getEnumConstants()) {
                if (enumConst.toString().equalsIgnoreCase(color)) {
                    pokedexType = enumConst;
                    break;
                }
            }
            if (pokedexType == null) pokedexType = typeClass.getEnumConstants()[0];

            Class<?> guiClass = Class.forName("com.cobblemon.mod.common.client.gui.pokedex.PokedexGUI");
            java.lang.reflect.Constructor<?> ctor = guiClass.getDeclaredConstructor(
                typeClass, net.minecraft.resources.ResourceLocation.class, net.minecraft.core.BlockPos.class);
            ctor.setAccessible(true);
            Screen pokedexScreen = (Screen) ctor.newInstance(pokedexType, null, null);
            pokedexScreen.init(Minecraft.getInstance(), this.width, this.height);
            return pokedexScreen;
        } catch (Exception e) {
            CobbleCompanion.LOGGER.error("[CC] Failed to build Professor " + logLabel + " screen", e);
            return null;
        }
    }

    /**
     * Baut Cobblemons echtes PCGUI (com.cobblemon...client.gui.pc.PCGUI) aus den zuletzt
     * empfangenen PC-Daten (siehe ProfessorPCResponsePacket). Anders als Summary hat PCGUI einen
     * PUBLIC Konstruktor, der ClientPC/ClientParty direkt entgegennimmt - keine Reflection nötig,
     * nur die eigens dafür gebaute "leere" ClientPC/ClientParty mit den Zieldaten befüllen.
     * canSelect liefert IMMER false: Cobblemons eigenes natives Drag&Drop muss vollständig
     * deaktiviert bleiben (KRITISCHER Bugfix, live gemeldet als "Pokemon verschwinden/verdoppeln/
     * ändern sich beim Bearbeiten eines fremden PC") - unser eigenes Greifen/Ablegen-System
     * (findProfessorPCSlotUnderCursor() + mouseClicked()) fängt Linksklicks SELBST ab und gibt bei
     * einem Treffer immer true zurück, ABER wenn unser Hit-Test einen Klick verfehlt (z.B. der
     * separat gemeldete Bug mit den untersten Party-Slots), fällt der Klick zu Cobblemons NATIVER
     * Klick-Weiterleitung durch. War canSelect dort true, konnte dieser durchgefallene Klick
     * Cobblemons eigenes StorageWidget-Greifen auslösen - und dessen native Move-Packete
     * (MovePCPokemonPacket & Co.) tragen laut früherer Bytecode-Analyse KEINE storeID und wirken
     * IMMER auf der ECHTEN Storage DES SENDERS (also des Admins selbst), nicht auf der hier
     * angezeigten (fremden) PC/Team-Ansicht - das erklärt exakt die gemeldeten Symptome (Löschen/
     * Verdoppeln/Vertauschen), weil ein verfehlter Klick unbemerkt in der EIGENEN Storage des
     * Admins herumpfuschen konnte, während der Bildschirm weiterhin die (unveränderten) Fake-Daten
     * des Zielspielers zeigte. Das AdminOp-Editor-Overlay (siehe adminEditOverlayOpen) öffnet über
     * einen Rechtsklick, den wir SELBST vor der Weiterleitung an dieses Fenster abfangen (siehe
     * mouseClicked()) - StorageSlot.mouseClicked reagiert laut Bytecode-Analyse ohnehin nur auf
     * Linksklick (Button.isValidClickButton akzeptiert nur button==0), Rechtsklick käme hier also
     * nie an. exitFunction ist Cobblemons eigener Tür-Knopf unten rechts - hier auf unsere
     * Zurück-Navigation umgebogen.
     */
    private Screen buildProfessorPCScreen() {
        List<String> boxNames = ClientProfessorHelper.getPCBoxNames();
        if (boxNames.isEmpty()) return null;
        try {
            net.minecraft.core.RegistryAccess registryAccess = Minecraft.getInstance().level.registryAccess();
            java.util.UUID targetUuid = ClientProfessorHelper.getSelected() != null
                ? ClientProfessorHelper.getSelected().uuid : java.util.UUID.randomUUID();

            com.cobblemon.mod.common.client.storage.ClientPC clientPC =
                new com.cobblemon.mod.common.client.storage.ClientPC(targetUuid, boxNames.size());
            for (int i = 0; i < boxNames.size(); i++) {
                String name = boxNames.get(i);
                if (!name.isBlank()) {
                    clientPC.getBoxes().get(i).setName(net.minecraft.network.chat.Component.literal(name));
                }
            }
            for (net.minecraft.nbt.CompoundTag tag : ClientProfessorHelper.getPCEntries()) {
                int box = tag.getInt("cc_box");
                int slot = tag.getInt("cc_slot");
                Pokemon pokemon = new Pokemon();
                pokemon.loadFromNBT(registryAccess, tag);
                clientPC.set(new com.cobblemon.mod.common.api.storage.pc.PCPosition(box, slot), pokemon);
            }

            // WICHTIG (Live-Test-Fund): der ECHTE Party-Slot steht im "cc_slot"-Feld des NBT-Tags
            // (siehe ProfessorPCRequestPacket.buildAndSend()), NICHT in der Listen-Position - eine
            // lückenhafte Party (z.B. nur Slot 2 belegt) würde sonst bei jedem Refresh fälschlich
            // auf Slot 0 "kollabieren", weil PartyStore.iterator() leere Slots stillschweigend
            // überspringt und die Liste dadurch nie die echten Lücken/Indizes zeigt.
            com.cobblemon.mod.common.client.storage.ClientParty clientParty =
                new com.cobblemon.mod.common.client.storage.ClientParty(targetUuid, 6);
            for (net.minecraft.nbt.CompoundTag tag : ClientProfessorHelper.getPCPartyEntries()) {
                int slot = tag.getInt("cc_slot");
                Pokemon pokemon = new Pokemon();
                pokemon.loadFromNBT(registryAccess, tag);
                clientParty.set(new com.cobblemon.mod.common.api.storage.party.PartyPosition(slot), pokemon);
            }

            kotlin.jvm.functions.Function1<com.cobblemon.mod.common.client.gui.pc.PCGUI, kotlin.Unit> exitFn =
                pcgui -> {
                    restoreLocalPokedexIfNeeded();
                    restoreLocalLivingDexIfNeeded();
                    professorViewingLivingDex = false;
                    professorSubScreen = null;
                    professorGrabbedPokemon = null;
                    return kotlin.Unit.INSTANCE;
                };
            // IMMER false - siehe Klassenkommentar. Unser eigenes Greifen/Ablegen-System deckt
            // alles ab, was Cobblemons natives Drag&Drop hier tun könnte, ohne dessen "wirkt immer
            // auf die Storage des Senders"-Gefahr.
            kotlin.jvm.functions.Function1<Pokemon, Boolean> canSelectFn = pokemon -> false;
            com.cobblemon.mod.common.client.gui.pc.PCGUIConfiguration config =
                new com.cobblemon.mod.common.client.gui.pc.PCGUIConfiguration(exitFn, null, true, canSelectFn);

            Screen pcgui = new com.cobblemon.mod.common.client.gui.pc.PCGUI(
                clientPC, clientParty, config, 0, java.util.Collections.emptySet());
            pcgui.init(Minecraft.getInstance(), this.width, this.height);
            return pcgui;
        } catch (Exception e) {
            CobbleCompanion.LOGGER.error("[CC] Failed to build Professor PC screen", e);
            return null;
        }
    }

    /** Ergebnis von findProfessorPCSlotUnderCursor() - eine konkrete Party- oder Box-Position, ggf. mit Pokemon. */
    private static class ProfessorSlotInfo {
        final Pokemon pokemon; // null = leerer Slot
        final boolean isParty;
        final com.cobblemon.mod.common.api.storage.pc.PCPosition pcPosition; // nur gültig wenn !isParty
        final com.cobblemon.mod.common.api.storage.party.PartyPosition partyPosition; // nur gültig wenn isParty

        ProfessorSlotInfo(Pokemon pokemon, boolean isParty,
                          com.cobblemon.mod.common.api.storage.pc.PCPosition pcPosition,
                          com.cobblemon.mod.common.api.storage.party.PartyPosition partyPosition) {
            this.pokemon = pokemon;
            this.isParty = isParty;
            this.pcPosition = pcPosition;
            this.partyPosition = partyPosition;
        }
    }

    /**
     * Findet Position (+ ggf. Pokemon) unter dem Mauszeiger in einer eingebetteten PCGUI - für
     * unseren Rechtsklick-Abfang (Edit-Overlay) UND unser eigenes Greifen/Ablegen-System für
     * Linksklick (siehe mouseClicked()). PCGUI.storageWidget sowie StorageWidget.boxSlots/
     * partySlots sind privat, deshalb per Reflection ausgelesen; StorageSlot.isHovered(int,int),
     * getPokemon() sowie Box/PartyStorageSlot.getPosition() sind dagegen public.
     */
    private ProfessorSlotInfo findProfessorPCSlotUnderCursor(com.cobblemon.mod.common.client.gui.pc.PCGUI pcgui, double mouseX, double mouseY) {
        try {
            java.lang.reflect.Field storageWidgetField = com.cobblemon.mod.common.client.gui.pc.PCGUI.class.getDeclaredField("storageWidget");
            storageWidgetField.setAccessible(true);
            Object storageWidget = storageWidgetField.get(pcgui);
            if (storageWidget == null) return null;
            java.lang.reflect.Field boxSlotsField = storageWidget.getClass().getDeclaredField("boxSlots");
            boxSlotsField.setAccessible(true);
            java.lang.reflect.Field partySlotsField = storageWidget.getClass().getDeclaredField("partySlots");
            partySlotsField.setAccessible(true);
            for (Object slotObj : (List<?>) boxSlotsField.get(storageWidget)) {
                com.cobblemon.mod.common.client.gui.pc.BoxStorageSlot slot =
                    (com.cobblemon.mod.common.client.gui.pc.BoxStorageSlot) slotObj;
                if (slot.isHovered((int) mouseX, (int) mouseY)) {
                    return new ProfessorSlotInfo(slot.getPokemon(), false, slot.getPosition(), null);
                }
            }
            // Party-Slots: NÄCHSTER-Mittelpunkt statt strikter isHovered()-Boxprüfung. Live-Test
            // (per Debug-Ausgabe verifiziert - die Erkennung war geometrisch korrekt, das Problem
            // liegt im Layout selbst) zeigte: Cobblemons Team-Icons stehen im Zickzack (abwechselnd
            // linke/rechte Spalte, leicht Y-versetzt) mit nur ~6px Abstand zwischen den Spalten bei
            // überlappendem Y-Bereich - ein leerer Slot (kein Icon zum Anvisieren) lässt sich damit
            // kaum pixelgenau treffen. Nächster-Mittelpunkt-Matching (mit Maximalradius, damit
            // Klicks weit außerhalb der Party-Reihe nicht versehentlich einen Party-Slot "gewinnen")
            // ist für die nur 6 festen Party-Positionen unproblematisch und deutlich verzeihender.
            List<?> partySlotsList = (List<?>) partySlotsField.get(storageWidget);
            com.cobblemon.mod.common.client.gui.pc.PartyStorageSlot nearest = null;
            double nearestDistSq = Double.MAX_VALUE;
            for (Object slotObj : partySlotsList) {
                com.cobblemon.mod.common.client.gui.pc.PartyStorageSlot slot =
                    (com.cobblemon.mod.common.client.gui.pc.PartyStorageSlot) slotObj;
                double centerX = slot.getX() + 12.5;
                double centerY = slot.getY() + 12.5;
                double dx = mouseX - centerX;
                double dy = mouseY - centerY;
                double distSq = dx * dx + dy * dy;
                if (distSq < nearestDistSq) {
                    nearestDistSq = distSq;
                    nearest = slot;
                }
            }
            // Radius 25px (ein Slot-Durchmesser) um versehentliches Treffen aus großer Entfernung
            // (z.B. Klicks im Box-Bereich) auszuschließen.
            if (nearest != null && nearestDistSq <= 25.0 * 25.0) {
                return new ProfessorSlotInfo(nearest.getPokemon(), true, null, nearest.getPosition());
            }
        } catch (Exception e) {
            CobbleCompanion.LOGGER.error("[CC] Failed to resolve PC slot under cursor", e);
        }
        return null;
    }

    // ===== AdminOp-Editor-Overlay =====
    private static final int ADMIN_EDIT_BOX_W = 210;
    private static final int ADMIN_EDIT_BOX_H = 268; // +20 gegenüber vorher, Platz für die neue Fähigkeit-Zeile
    private static final int ADMIN_EDIT_ROW_H = 20;
    private static final int ADMIN_EDIT_ROW_START_Y = 26;
    private static final int ADMIN_EDIT_STEP_BTN_W = 16;
    private static final int ADMIN_EDIT_STEP_BTN_H = 14;
    private static final int ADMIN_EDIT_ACTION_BTN_W = 60;
    private static final int ADMIN_EDIT_ACTION_BTN_H = 15;
    private static final int ADMIN_EDIT_GIFT_BTN_H = 15;
    private static final int ADMIN_EDIT_NICKNAME_MAX_LEN = 12;
    // Justierschraube: Entwickeln/Zurückentwickeln-Buttons unterhalb der Nickname-Zeile.
    private static final int ADMIN_EDIT_EVOLVE_ROW_GAP_Y = 6;
    private static final int ADMIN_EDIT_EVOLVE_BTN_H = 15;
    private static final int ADMIN_EDIT_EVOLVE_BTN_GAP = 4;

    /** Öffnet das Editor-Overlay für ein per selectOverride angeklicktes Pokemon (siehe buildProfessorPCScreen()). */
    private void openAdminEditOverlay(Pokemon pokemon) {
        adminEditPokemon = pokemon;
        adminEditLevel = pokemon.getLevel();
        adminEditShiny = pokemon.getShiny();
        adminEditNicknameFocused = false;
        adminGiftOverlayOpen = false;
        adminEvolveOverlayOpen = false;
        adminEditNickname = pokemon.getNickname() != null ? pokemon.getNickname().getString() : "";

        adminEditBallIndex = 0;
        try {
            String currentBallPath = pokemon.getCaughtBall() != null ? pokemon.getCaughtBall().getName().getPath() : "poke_ball";
            for (int i = 0; i < ADMIN_EDIT_BALLS.length; i++) {
                if (ADMIN_EDIT_BALLS[i].equals(currentBallPath)) {
                    adminEditBallIndex = i;
                    break;
                }
            }
        } catch (Exception ignored) {}

        adminEditNatureIndex = 0;
        try {
            String currentNature = pokemon.getNature().getName().getPath();
            for (int i = 0; i < ADMIN_EDIT_NATURES.length; i++) {
                if (ADMIN_EDIT_NATURES[i].equals(currentNature)) {
                    adminEditNatureIndex = i;
                    break;
                }
            }
        } catch (Exception ignored) {}

        adminEditGenderIndex = 0;
        try {
            for (int i = 0; i < ADMIN_EDIT_GENDERS.length; i++) {
                if (ADMIN_EDIT_GENDERS[i].equals(pokemon.getGender().name())) {
                    adminEditGenderIndex = i;
                    break;
                }
            }
        } catch (Exception ignored) {}

        adminEditAbilityOptions = new java.util.ArrayList<>();
        adminEditAbilityIndex = 0;
        try {
            String currentAbility = pokemon.getAbility() != null ? pokemon.getAbility().getName() : null;
            if (pokemon.getForm() != null) {
                for (com.cobblemon.mod.common.api.abilities.PotentialAbility pa : pokemon.getForm().getAbilities()) {
                    String abilityName = pa.getTemplate().getName();
                    if (!adminEditAbilityOptions.contains(abilityName)) adminEditAbilityOptions.add(abilityName);
                }
            }
            if (adminEditAbilityOptions.isEmpty() && currentAbility != null) adminEditAbilityOptions.add(currentAbility);
            if (currentAbility != null) {
                int idx = adminEditAbilityOptions.indexOf(currentAbility);
                if (idx >= 0) adminEditAbilityIndex = idx;
            }
        } catch (Exception ignored) {}

        adminEditOverlayOpen = true;
    }

    private static String formatBallName(String path) {
        String[] words = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    private void renderAdminEditOverlay(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(0, 0, this.width, this.height, 0xC0000000);
        int boxX = guiLeft + (GUI_WIDTH - ADMIN_EDIT_BOX_W) / 2;
        int boxY = guiTop + (GUI_HEIGHT - ADMIN_EDIT_BOX_H) / 2;
        graphics.fill(boxX, boxY, boxX + ADMIN_EDIT_BOX_W, boxY + ADMIN_EDIT_BOX_H, 0xFF202020);
        graphics.fill(boxX, boxY, boxX + ADMIN_EDIT_BOX_W, boxY + 1, 0xFF00A0C0);
        graphics.fill(boxX, boxY + ADMIN_EDIT_BOX_H - 1, boxX + ADMIN_EDIT_BOX_W, boxY + ADMIN_EDIT_BOX_H, 0xFF00A0C0);
        if (adminEditPokemon == null) return;

        String title = speciesDisplayName(adminEditPokemon.getSpecies());
        drawSmallLabel(graphics, title, boxX + 10, boxY + 8, 1.0f, 0xFFFFFF, true, true);

        int rowY = boxY + ADMIN_EDIT_ROW_START_Y;
        int minusX = boxX + ADMIN_EDIT_BOX_W - 2 * ADMIN_EDIT_STEP_BTN_W - 16;
        int plusX = boxX + ADMIN_EDIT_BOX_W - ADMIN_EDIT_STEP_BTN_W - 10;

        drawSmallLabel(graphics, tr("cobblecompanion.gui.admin.level") + ": " + adminEditLevel, boxX + 10, rowY + 3, 1.0f, 0xFFFFFF, false, true);
        renderStepButton(graphics, mouseX, mouseY, minusX, rowY, "-");
        renderStepButton(graphics, mouseX, mouseY, plusX, rowY, "+");
        rowY += ADMIN_EDIT_ROW_H;

        String shinyLabel = tr("cobblecompanion.gui.admin.shiny") + ": " + (adminEditShiny ? tr("cobblecompanion.gui.confirm.yes") : tr("cobblecompanion.gui.confirm.no"));
        drawSmallLabel(graphics, shinyLabel, boxX + 10, rowY + 3, 1.0f, 0xFFFFFF, false, true);
        renderStepButton(graphics, mouseX, mouseY, plusX, rowY, "~");
        rowY += ADMIN_EDIT_ROW_H;

        String ballLabel = tr("cobblecompanion.gui.admin.ball") + ": " + formatBallName(ADMIN_EDIT_BALLS[adminEditBallIndex]);
        drawSmallLabel(graphics, ballLabel, boxX + 10, rowY + 3, 1.0f, 0xFFFFFF, false, true);
        renderStepButton(graphics, mouseX, mouseY, minusX, rowY, "<");
        renderStepButton(graphics, mouseX, mouseY, plusX, rowY, ">");
        rowY += ADMIN_EDIT_ROW_H;

        String natureLabel = tr("cobblecompanion.gui.admin.nature") + ": " + formatBallName(ADMIN_EDIT_NATURES[adminEditNatureIndex]);
        drawSmallLabel(graphics, natureLabel, boxX + 10, rowY + 3, 1.0f, 0xFFFFFF, false, true);
        renderStepButton(graphics, mouseX, mouseY, minusX, rowY, "<");
        renderStepButton(graphics, mouseX, mouseY, plusX, rowY, ">");
        rowY += ADMIN_EDIT_ROW_H;

        String genderLabel = tr("cobblecompanion.gui.admin.gender") + ": " + formatBallName(ADMIN_EDIT_GENDERS[adminEditGenderIndex]);
        drawSmallLabel(graphics, genderLabel, boxX + 10, rowY + 3, 1.0f, 0xFFFFFF, false, true);
        renderStepButton(graphics, mouseX, mouseY, minusX, rowY, "<");
        renderStepButton(graphics, mouseX, mouseY, plusX, rowY, ">");
        rowY += ADMIN_EDIT_ROW_H;

        String abilityDisplay = adminEditAbilityOptions.isEmpty() ? "-" : formatBallName(adminEditAbilityOptions.get(adminEditAbilityIndex));
        String abilityLabel = tr("cobblecompanion.gui.admin.ability") + ": " + abilityDisplay;
        drawSmallLabel(graphics, abilityLabel, boxX + 10, rowY + 3, 1.0f, 0xFFFFFF, false, true);
        renderStepButton(graphics, mouseX, mouseY, minusX, rowY, "<");
        renderStepButton(graphics, mouseX, mouseY, plusX, rowY, ">");
        rowY += ADMIN_EDIT_ROW_H;

        String nicknameDisplay = adminEditNickname.isBlank() ? "-" : adminEditNickname;
        if (adminEditNicknameFocused) nicknameDisplay += "_";
        String nicknameLabel = tr("cobblecompanion.gui.admin.nickname") + ": " + nicknameDisplay;
        int nicknameColor = adminEditNicknameFocused ? 0xFFFFFF00 : 0xFFFFFFFF;
        drawSmallLabel(graphics, nicknameLabel, boxX + 10, rowY + 3, 1.0f, nicknameColor, false, true);

        int evolveBtnY = rowY + ADMIN_EDIT_ROW_H + ADMIN_EDIT_EVOLVE_ROW_GAP_Y;
        int evolveBtnW = (ADMIN_EDIT_BOX_W - 16 - ADMIN_EDIT_EVOLVE_BTN_GAP) / 2;
        // Kein clientseitiges "hat Vorentwicklung?"-Gating mehr (siehe openAdminEvolvePicker-
        // Kommentar: das rekonstruierte Pokemon liefert das unzuverlässig) - der Server entscheidet
        // beim Klick, ob es eine Vorentwicklung gibt, und tut sonst einfach nichts.
        renderConfirmButton(graphics, boxX + 8, evolveBtnY, evolveBtnW, ADMIN_EDIT_EVOLVE_BTN_H,
            tr("cobblecompanion.gui.admin.evolve"), 0xFF55FFFF, mouseX, mouseY);
        renderConfirmButton(graphics, boxX + 8 + evolveBtnW + ADMIN_EDIT_EVOLVE_BTN_GAP, evolveBtnY, evolveBtnW, ADMIN_EDIT_EVOLVE_BTN_H,
            tr("cobblecompanion.gui.admin.deevolve"), 0xFFFFAAFF, mouseX, mouseY);

        int giftBtnY = boxY + ADMIN_EDIT_BOX_H - ADMIN_EDIT_ACTION_BTN_H - ADMIN_EDIT_GIFT_BTN_H - 14;
        renderConfirmButton(graphics, boxX + (ADMIN_EDIT_BOX_W - CONFIRM_BTN_W) / 2, giftBtnY,
            tr("cobblecompanion.gui.admin.gift"), 0xFF55AAFF, mouseX, mouseY);

        int btnY = boxY + ADMIN_EDIT_BOX_H - ADMIN_EDIT_ACTION_BTN_H - 8;
        renderConfirmButton(graphics, boxX + 8, btnY, tr("cobblecompanion.gui.admin.save"), 0xFF55FF55, mouseX, mouseY);
        renderConfirmButton(graphics, boxX + (ADMIN_EDIT_BOX_W - ADMIN_EDIT_ACTION_BTN_W) / 2, btnY, tr("cobblecompanion.gui.admin.release"), 0xFFFFAA00, mouseX, mouseY);
        renderConfirmButton(graphics, boxX + ADMIN_EDIT_BOX_W - ADMIN_EDIT_ACTION_BTN_W - 8, btnY, tr("cobblecompanion.gui.admin.cancel"), 0xFFFF5555, mouseX, mouseY);
    }

    private void renderStepButton(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, String label) {
        boolean hovered = isInRect(mouseX, mouseY, x, y, ADMIN_EDIT_STEP_BTN_W, ADMIN_EDIT_STEP_BTN_H);
        graphics.blit(TODO_EVOLVE_BUTTON, x, y, ADMIN_EDIT_STEP_BTN_W, ADMIN_EDIT_STEP_BTN_H,
            0f, hovered ? TODO_EVOLVE_BTN_NATIVE_H : 0f,
            TODO_EVOLVE_BTN_NATIVE_W, TODO_EVOLVE_BTN_NATIVE_H, TODO_EVOLVE_BTN_NATIVE_W, TODO_EVOLVE_BTN_NATIVE_H * 2);
        int lw = smallLabelWidth(label, 1.0f, true, true);
        drawSmallLabel(graphics, label, x + (ADMIN_EDIT_STEP_BTN_W - lw) / 2, y + (ADMIN_EDIT_STEP_BTN_H - 8) / 2, 1.0f, 0xFFFFFF, true, true);
    }

    /** Verarbeitet Klicks im AdminOp-Editor-Overlay. Gibt immer true zurück (modal). */
    private boolean handleAdminEditOverlayClick(double mouseX, double mouseY) {
        int boxX = guiLeft + (GUI_WIDTH - ADMIN_EDIT_BOX_W) / 2;
        int boxY = guiTop + (GUI_HEIGHT - ADMIN_EDIT_BOX_H) / 2;
        int rowY = boxY + ADMIN_EDIT_ROW_START_Y;
        int minusX = boxX + ADMIN_EDIT_BOX_W - 2 * ADMIN_EDIT_STEP_BTN_W - 16;
        int plusX = boxX + ADMIN_EDIT_BOX_W - ADMIN_EDIT_STEP_BTN_W - 10;

        if (isInRect(mouseX, mouseY, minusX, rowY, ADMIN_EDIT_STEP_BTN_W, ADMIN_EDIT_STEP_BTN_H)) {
            adminEditLevel = Math.max(1, adminEditLevel - 1);
            return true;
        }
        if (isInRect(mouseX, mouseY, plusX, rowY, ADMIN_EDIT_STEP_BTN_W, ADMIN_EDIT_STEP_BTN_H)) {
            adminEditLevel = Math.min(100, adminEditLevel + 1);
            return true;
        }
        rowY += ADMIN_EDIT_ROW_H;
        if (isInRect(mouseX, mouseY, plusX, rowY, ADMIN_EDIT_STEP_BTN_W, ADMIN_EDIT_STEP_BTN_H)) {
            adminEditShiny = !adminEditShiny;
            return true;
        }
        rowY += ADMIN_EDIT_ROW_H;
        if (isInRect(mouseX, mouseY, minusX, rowY, ADMIN_EDIT_STEP_BTN_W, ADMIN_EDIT_STEP_BTN_H)) {
            adminEditBallIndex = (adminEditBallIndex - 1 + ADMIN_EDIT_BALLS.length) % ADMIN_EDIT_BALLS.length;
            return true;
        }
        if (isInRect(mouseX, mouseY, plusX, rowY, ADMIN_EDIT_STEP_BTN_W, ADMIN_EDIT_STEP_BTN_H)) {
            adminEditBallIndex = (adminEditBallIndex + 1) % ADMIN_EDIT_BALLS.length;
            return true;
        }
        rowY += ADMIN_EDIT_ROW_H;
        if (isInRect(mouseX, mouseY, minusX, rowY, ADMIN_EDIT_STEP_BTN_W, ADMIN_EDIT_STEP_BTN_H)) {
            adminEditNatureIndex = (adminEditNatureIndex - 1 + ADMIN_EDIT_NATURES.length) % ADMIN_EDIT_NATURES.length;
            return true;
        }
        if (isInRect(mouseX, mouseY, plusX, rowY, ADMIN_EDIT_STEP_BTN_W, ADMIN_EDIT_STEP_BTN_H)) {
            adminEditNatureIndex = (adminEditNatureIndex + 1) % ADMIN_EDIT_NATURES.length;
            return true;
        }
        rowY += ADMIN_EDIT_ROW_H;
        if (isInRect(mouseX, mouseY, minusX, rowY, ADMIN_EDIT_STEP_BTN_W, ADMIN_EDIT_STEP_BTN_H)) {
            adminEditGenderIndex = (adminEditGenderIndex - 1 + ADMIN_EDIT_GENDERS.length) % ADMIN_EDIT_GENDERS.length;
            return true;
        }
        if (isInRect(mouseX, mouseY, plusX, rowY, ADMIN_EDIT_STEP_BTN_W, ADMIN_EDIT_STEP_BTN_H)) {
            adminEditGenderIndex = (adminEditGenderIndex + 1) % ADMIN_EDIT_GENDERS.length;
            return true;
        }
        rowY += ADMIN_EDIT_ROW_H;
        if (!adminEditAbilityOptions.isEmpty()) {
            if (isInRect(mouseX, mouseY, minusX, rowY, ADMIN_EDIT_STEP_BTN_W, ADMIN_EDIT_STEP_BTN_H)) {
                adminEditAbilityIndex = (adminEditAbilityIndex - 1 + adminEditAbilityOptions.size()) % adminEditAbilityOptions.size();
                return true;
            }
            if (isInRect(mouseX, mouseY, plusX, rowY, ADMIN_EDIT_STEP_BTN_W, ADMIN_EDIT_STEP_BTN_H)) {
                adminEditAbilityIndex = (adminEditAbilityIndex + 1) % adminEditAbilityOptions.size();
                return true;
            }
        }
        rowY += ADMIN_EDIT_ROW_H;
        if (isInRect(mouseX, mouseY, boxX + 8, rowY, ADMIN_EDIT_BOX_W - 16, ADMIN_EDIT_ROW_H)) {
            adminEditNicknameFocused = !adminEditNicknameFocused;
            return true;
        }
        adminEditNicknameFocused = false;

        int evolveBtnY = rowY + ADMIN_EDIT_ROW_H + ADMIN_EDIT_EVOLVE_ROW_GAP_Y;
        int evolveBtnW = (ADMIN_EDIT_BOX_W - 16 - ADMIN_EDIT_EVOLVE_BTN_GAP) / 2;
        ClientProfessorHelper.PlayerItem selectedForEvolve = ClientProfessorHelper.getSelected();
        if (isInRect(mouseX, mouseY, boxX + 8, evolveBtnY, evolveBtnW, ADMIN_EDIT_EVOLVE_BTN_H)) {
            if (adminEditPokemon != null) openAdminEvolvePicker(adminEditPokemon);
            return true;
        }
        if (isInRect(mouseX, mouseY, boxX + 8 + evolveBtnW + ADMIN_EDIT_EVOLVE_BTN_GAP, evolveBtnY, evolveBtnW, ADMIN_EDIT_EVOLVE_BTN_H)) {
            // Zeigt jetzt genau wie "Entwickeln" erst ein Auswahlfenster (mit Sprite-Vorschau) zur
            // Bestätigung, statt sofort ohne Rückfrage die Vorentwicklung anzuwenden.
            if (adminEditPokemon != null) openAdminDeEvolvePicker(adminEditPokemon);
            return true;
        }

        int giftBtnY = boxY + ADMIN_EDIT_BOX_H - ADMIN_EDIT_ACTION_BTN_H - ADMIN_EDIT_GIFT_BTN_H - 14;
        if (isInRect(mouseX, mouseY, boxX + (ADMIN_EDIT_BOX_W - CONFIRM_BTN_W) / 2, giftBtnY, CONFIRM_BTN_W, ADMIN_EDIT_GIFT_BTN_H)) {
            adminGiftOverlayOpen = true;
            adminGiftScrollAmount = 0;
            return true;
        }

        int btnY = boxY + ADMIN_EDIT_BOX_H - ADMIN_EDIT_ACTION_BTN_H - 8;
        ClientProfessorHelper.PlayerItem selected = ClientProfessorHelper.getSelected();
        if (isInRect(mouseX, mouseY, boxX + 8, btnY, ADMIN_EDIT_ACTION_BTN_W, ADMIN_EDIT_ACTION_BTN_H)) {
            if (selected != null && adminEditPokemon != null) {
                String newAbility = adminEditAbilityOptions.isEmpty() ? "" : adminEditAbilityOptions.get(adminEditAbilityIndex);
                sendToServer(new AdminEditPokemonPacket(
                    selected.uuid, adminEditPokemon.getUuid(), adminEditLevel, adminEditShiny,
                    ADMIN_EDIT_BALLS[adminEditBallIndex], ADMIN_EDIT_NATURES[adminEditNatureIndex],
                    ADMIN_EDIT_GENDERS[adminEditGenderIndex], adminEditNickname, newAbility));
            }
            adminEditOverlayOpen = false;
            return true;
        }
        if (isInRect(mouseX, mouseY, boxX + (ADMIN_EDIT_BOX_W - ADMIN_EDIT_ACTION_BTN_W) / 2, btnY, ADMIN_EDIT_ACTION_BTN_W, ADMIN_EDIT_ACTION_BTN_H)) {
            if (selected != null && adminEditPokemon != null) {
                sendToServer(new AdminReleasePokemonPacket(selected.uuid, adminEditPokemon.getUuid()));
            }
            adminEditOverlayOpen = false;
            return true;
        }
        // Abbrechen-Button oder Klick irgendwo sonst -> ohne Änderung schließen.
        adminEditOverlayOpen = false;
        return true;
    }

    /** Spieler-Auswahl-Overlay (über dem Editor-Overlay) zum Verschenken des gerade bearbeiteten Pokemons. */
    private void renderAdminGiftOverlay(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(0, 0, this.width, this.height, 0xC0000000);
        int boxX = guiLeft + (GUI_WIDTH - ADMIN_GIFT_BOX_W) / 2;
        int boxY = guiTop + (GUI_HEIGHT - ADMIN_GIFT_BOX_H) / 2;
        graphics.fill(boxX, boxY, boxX + ADMIN_GIFT_BOX_W, boxY + ADMIN_GIFT_BOX_H, 0xFF202020);
        graphics.fill(boxX, boxY, boxX + ADMIN_GIFT_BOX_W, boxY + 1, 0xFF00A0C0);
        graphics.fill(boxX, boxY + ADMIN_GIFT_BOX_H - 1, boxX + ADMIN_GIFT_BOX_W, boxY + ADMIN_GIFT_BOX_H, 0xFF00A0C0);

        String title = tr("cobblecompanion.gui.admin.gift_pick");
        int tw = smallLabelWidth(title, 1.0f, true, true);
        drawSmallLabel(graphics, title, boxX + (ADMIN_GIFT_BOX_W - tw) / 2, boxY + 8, 1.0f, 0xFFFFFF, true, true);

        List<ClientProfessorHelper.PlayerItem> players = adminGiftCandidates();
        int listTop = boxY + ADMIN_GIFT_LIST_START_Y;
        int visibleHeight = ADMIN_GIFT_BOX_H - ADMIN_GIFT_LIST_START_Y - ADMIN_GIFT_LIST_BOTTOM_MARGIN;
        int maxScroll = Math.max(0, players.size() * ADMIN_GIFT_ROW_H - visibleHeight);
        adminGiftScrollAmount = Math.max(0, Math.min(maxScroll, adminGiftScrollAmount));

        int rowX = boxX + 10;
        int y = listTop - (int) Math.round(adminGiftScrollAmount);
        for (ClientProfessorHelper.PlayerItem p : players) {
            if (y + ADMIN_GIFT_ROW_H >= listTop && y <= listTop + visibleHeight) {
                boolean hovered = isInRect(mouseX, mouseY, rowX, y, ADMIN_GIFT_BOX_W - 20, ADMIN_GIFT_ROW_H);
                if (hovered) graphics.fill(rowX, y, rowX + ADMIN_GIFT_BOX_W - 20, y + ADMIN_GIFT_ROW_H, 0x40FFFFFF);
                int color = p.online ? 0xFFFFFFFF : 0xFF808080;
                drawSmallLabel(graphics, p.name, rowX, y + 3, 1.0f, color, false, true);
            }
            y += ADMIN_GIFT_ROW_H;
        }
        if (maxScroll > 0) {
            int scrollbarX = boxX + ADMIN_GIFT_BOX_W - ADMIN_GIFT_SCROLLBAR_WIDTH - 4;
            renderScrollbar(graphics, scrollbarX, ADMIN_GIFT_SCROLLBAR_WIDTH, listTop, visibleHeight, adminGiftScrollAmount, maxScroll);
        }

        String cancel = tr("cobblecompanion.gui.admin.cancel");
        int cw = smallLabelWidth(cancel, 1.0f, true, true);
        drawSmallLabel(graphics, cancel, boxX + (ADMIN_GIFT_BOX_W - cw) / 2, boxY + ADMIN_GIFT_BOX_H - 14, 1.0f, 0xFFFF5555, true, true);
    }

    /** Alle bekannten Spieler außer dem gerade inspizierten Zielspieler - Empfänger dürfen auch offline sein (AdminGiftPokemonPacket arbeitet UUID-basiert). */
    private List<ClientProfessorHelper.PlayerItem> adminGiftCandidates() {
        List<ClientProfessorHelper.PlayerItem> result = new java.util.ArrayList<>();
        ClientProfessorHelper.PlayerItem selected = ClientProfessorHelper.getSelected();
        for (ClientProfessorHelper.PlayerItem p : ClientProfessorHelper.getPlayers()) {
            if (selected != null && p.uuid.equals(selected.uuid)) continue;
            result.add(p);
        }
        return result;
    }

    private boolean handleAdminGiftOverlayClick(double mouseX, double mouseY) {
        int boxX = guiLeft + (GUI_WIDTH - ADMIN_GIFT_BOX_W) / 2;
        int boxY = guiTop + (GUI_HEIGHT - ADMIN_GIFT_BOX_H) / 2;
        List<ClientProfessorHelper.PlayerItem> players = adminGiftCandidates();
        int listTop = boxY + ADMIN_GIFT_LIST_START_Y;
        int visibleHeight = ADMIN_GIFT_BOX_H - ADMIN_GIFT_LIST_START_Y - ADMIN_GIFT_LIST_BOTTOM_MARGIN;
        int maxScroll = Math.max(0, players.size() * ADMIN_GIFT_ROW_H - visibleHeight);

        if (maxScroll > 0) {
            int scrollbarX = boxX + ADMIN_GIFT_BOX_W - ADMIN_GIFT_SCROLLBAR_WIDTH - 4;
            if (isMouseOverScrollbar(mouseX, mouseY, scrollbarX, ADMIN_GIFT_SCROLLBAR_WIDTH, listTop, visibleHeight)) {
                adminGiftScrollbarDragging = true;
                adminGiftScrollAmount = scrollAmountFromMouseY(mouseY, listTop, visibleHeight, maxScroll);
                return true;
            }
        }

        int rowX = boxX + 10;
        int y = listTop - (int) Math.round(adminGiftScrollAmount);
        ClientProfessorHelper.PlayerItem selected = ClientProfessorHelper.getSelected();
        for (ClientProfessorHelper.PlayerItem p : players) {
            if (y >= listTop && y <= listTop + visibleHeight
                && isInRect(mouseX, mouseY, rowX, y, ADMIN_GIFT_BOX_W - 20, ADMIN_GIFT_ROW_H)) {
                if (selected != null && adminEditPokemon != null) {
                    sendToServer(new AdminGiftPokemonPacket(selected.uuid, adminEditPokemon.getUuid(), p.uuid));
                }
                adminGiftOverlayOpen = false;
                adminEditOverlayOpen = false;
                return true;
            }
            y += ADMIN_GIFT_ROW_H;
        }
        // Abbrechen oder Klick irgendwo sonst -> nur das Spieler-Auswahl-Overlay schließen, Editor bleibt offen.
        adminGiftOverlayOpen = false;
        return true;
    }

    /**
     * Fragt beim Server die Liste ALLER potentiell möglichen Entwicklungen ab (Voraussetzungen
     * bewusst ignoriert - siehe AdminForceEvolvePokemonPacket) und öffnet das Auswahl-Overlay
     * sofort mit einer leeren Liste ("lädt..." bzw. "keine Entwicklung", bis die Antwort da ist).
     * WICHTIG: läuft bewusst NICHT über adminEditPokemon.getEvolutions() - das per loadFromNBT()
     * clientseitig rekonstruierte Pokemon liefert dort laut Live-Test unzuverlässig eine leere
     * Liste, selbst bei Arten mit echten Entwicklungen (z.B. Schiggy). Stattdessen fragt
     * AdminEvolveOptionsRequestPacket die ECHTE serverseitige Pokemon-Instanz.
     */
    private void openAdminEvolvePicker(Pokemon pokemon) {
        adminEvolveCandidates = new java.util.ArrayList<>();
        adminEvolveScrollAmount = 0;
        adminEvolveOverlayOpen = true;
        adminEvolveIsDeEvolve = false;
        ClientProfessorHelper.PlayerItem selected = ClientProfessorHelper.getSelected();
        if (selected != null) {
            adminEvolveOptionsVersionSeen = ClientProfessorHelper.getEvolveOptionsVersion();
            sendToServer(new com.cobblecompanion.network.AdminEvolveOptionsRequestPacket(selected.uuid, pokemon.getUuid()));
        }
    }

    /**
     * Wie openAdminEvolvePicker(), aber für "Zurückentwickeln" - fragt die Vorentwicklung ab und
     * zeigt sie im selben Auswahl-Overlay (maximal 1 Eintrag, da jedes Pokemon höchstens eine
     * direkte Vorentwicklung hat) statt sie sofort ohne Bestätigung anzuwenden.
     */
    private void openAdminDeEvolvePicker(Pokemon pokemon) {
        adminEvolveCandidates = new java.util.ArrayList<>();
        adminEvolveScrollAmount = 0;
        adminEvolveOverlayOpen = true;
        adminEvolveIsDeEvolve = true;
        ClientProfessorHelper.PlayerItem selected = ClientProfessorHelper.getSelected();
        if (selected != null) {
            adminDeEvolveOptionVersionSeen = ClientProfessorHelper.getDeEvolveOptionVersion();
            sendToServer(new com.cobblecompanion.network.AdminDeEvolveOptionsRequestPacket(selected.uuid, pokemon.getUuid()));
        }
    }

    /** Baut adminEvolveCandidates neu, sobald die passende Server-Antwort eingetroffen ist (Entwickeln ODER Zurückentwickeln). */
    private void checkAdminEvolveOptionsUpdate() {
        if (!adminEvolveOverlayOpen) return;
        if (adminEvolveIsDeEvolve) {
            int version = ClientProfessorHelper.getDeEvolveOptionVersion();
            if (version == adminDeEvolveOptionVersionSeen) return;
            adminDeEvolveOptionVersionSeen = version;
            adminEvolveCandidates = new java.util.ArrayList<>();
            String raw = ClientProfessorHelper.getDeEvolveOption();
            if (raw != null && !raw.isBlank()) {
                try {
                    ResourceLocation toId = ResourceLocation.parse(raw);
                    Species toSpecies = com.cobblemon.mod.common.api.pokemon.PokemonSpecies.INSTANCE.getByIdentifier(toId);
                    String label = toSpecies != null ? speciesDisplayName(toSpecies) : toId.getPath();
                    adminEvolveCandidates.add(new AdminEvolveCandidate(label, toId.toString(), ""));
                } catch (Exception ignored) {}
            }
            return;
        }
        int version = ClientProfessorHelper.getEvolveOptionsVersion();
        if (version == adminEvolveOptionsVersionSeen) return;
        adminEvolveOptionsVersionSeen = version;
        adminEvolveCandidates = new java.util.ArrayList<>();
        for (String raw : ClientProfessorHelper.getEvolveOptions()) {
            String[] parts = raw.split("\\|", 2);
            if (parts.length < 1) continue;
            ResourceLocation toId;
            try {
                toId = ResourceLocation.parse(parts[0]);
            } catch (Exception e) {
                continue;
            }
            String aspects = parts.length > 1 ? parts[1] : "";
            Species toSpecies = com.cobblemon.mod.common.api.pokemon.PokemonSpecies.INSTANCE.getByIdentifier(toId);
            String label = toSpecies != null ? speciesDisplayName(toSpecies) : toId.getPath();
            if (!aspects.isEmpty()) label += " (" + aspects.replace(",", ", ") + ")";
            adminEvolveCandidates.add(new AdminEvolveCandidate(label, toId.toString(), aspects));
        }
    }

    /** Set<String> aus dem kommagetrennten toAspects-Feld - für PokemonSlotRenderer. */
    private static java.util.Set<String> parseAspects(String commaSeparated) {
        if (commaSeparated == null || commaSeparated.isBlank()) return java.util.Collections.emptySet();
        return java.util.Set.of(commaSeparated.split(","));
    }

    /**
     * Wie renderAdminGiftOverlay, aber für die Entwickeln/Zurückentwickeln-Auswahl - mit einer
     * Pokemon-Sprite-Vorschau (PokemonSlotRenderer, dieselbe Technik wie im WhoNeeds-Tab) je
     * Zeile, da der Admin die ~1050 Artnamen nicht alle auswendig kennt. Eigene (höhere)
     * Zeilenhöhe ADMIN_EVOLVE_ROW_H statt ADMIN_GIFT_ROW_H, Box-Breite/-Position bleiben geteilt.
     */
    private void renderAdminEvolveOverlay(GuiGraphics graphics, int mouseX, int mouseY) {
        checkAdminEvolveOptionsUpdate();
        graphics.fill(0, 0, this.width, this.height, 0xC0000000);
        int boxX = guiLeft + (GUI_WIDTH - ADMIN_GIFT_BOX_W) / 2;
        int boxY = guiTop + (GUI_HEIGHT - ADMIN_GIFT_BOX_H) / 2;
        graphics.fill(boxX, boxY, boxX + ADMIN_GIFT_BOX_W, boxY + ADMIN_GIFT_BOX_H, 0xFF202020);
        graphics.fill(boxX, boxY, boxX + ADMIN_GIFT_BOX_W, boxY + 1, 0xFF00A0C0);
        graphics.fill(boxX, boxY + ADMIN_GIFT_BOX_H - 1, boxX + ADMIN_GIFT_BOX_W, boxY + ADMIN_GIFT_BOX_H, 0xFF00A0C0);

        String title = tr(adminEvolveIsDeEvolve ? "cobblecompanion.gui.admin.deevolve_pick" : "cobblecompanion.gui.admin.evolve_pick");
        int tw = smallLabelWidth(title, 1.0f, true, true);
        drawSmallLabel(graphics, title, boxX + (ADMIN_GIFT_BOX_W - tw) / 2, boxY + 8, 1.0f, 0xFFFFFF, true, true);

        int listTop = boxY + ADMIN_GIFT_LIST_START_Y;
        int visibleHeight = ADMIN_GIFT_BOX_H - ADMIN_GIFT_LIST_START_Y - ADMIN_GIFT_LIST_BOTTOM_MARGIN;
        int maxScroll = Math.max(0, adminEvolveCandidates.size() * ADMIN_EVOLVE_ROW_H - visibleHeight);
        adminEvolveScrollAmount = Math.max(0, Math.min(maxScroll, adminEvolveScrollAmount));

        int rowX = boxX + 10;
        int y = listTop - (int) Math.round(adminEvolveScrollAmount);
        for (AdminEvolveCandidate c : adminEvolveCandidates) {
            if (y + ADMIN_EVOLVE_ROW_H >= listTop && y <= listTop + visibleHeight) {
                boolean hovered = isInRect(mouseX, mouseY, rowX, y, ADMIN_GIFT_BOX_W - 20, ADMIN_EVOLVE_ROW_H);
                if (hovered) graphics.fill(rowX, y, rowX + ADMIN_GIFT_BOX_W - 20, y + ADMIN_EVOLVE_ROW_H, 0x40FFFFFF);
                try {
                    PokemonSlotRenderer.renderSlot(graphics, rowX, y + 1, ResourceLocation.parse(c.toSpeciesId), parseAspects(c.toAspects));
                } catch (Exception ignored) {}
                drawSmallLabel(graphics, c.label, rowX + PokemonSlotRenderer.SLOT_SIZE + 6, y + (ADMIN_EVOLVE_ROW_H - 8) / 2, 1.0f, 0xFFFFFFFF, false, true);
            }
            y += ADMIN_EVOLVE_ROW_H;
        }
        if (adminEvolveCandidates.isEmpty()) {
            String empty = tr("cobblecompanion.gui.admin.evolve_none");
            int ew = smallLabelWidth(empty, 1.0f, false, true);
            drawSmallLabel(graphics, empty, boxX + (ADMIN_GIFT_BOX_W - ew) / 2, listTop + 4, 1.0f, 0xFF808080, false, true);
        }
        if (maxScroll > 0) {
            int scrollbarX = boxX + ADMIN_GIFT_BOX_W - ADMIN_GIFT_SCROLLBAR_WIDTH - 4;
            renderScrollbar(graphics, scrollbarX, ADMIN_GIFT_SCROLLBAR_WIDTH, listTop, visibleHeight, adminEvolveScrollAmount, maxScroll);
        }

        String cancel = tr("cobblecompanion.gui.admin.cancel");
        int cw = smallLabelWidth(cancel, 1.0f, true, true);
        drawSmallLabel(graphics, cancel, boxX + (ADMIN_GIFT_BOX_W - cw) / 2, boxY + ADMIN_GIFT_BOX_H - 14, 1.0f, 0xFFFF5555, true, true);
    }

    private boolean handleAdminEvolveOverlayClick(double mouseX, double mouseY) {
        int boxX = guiLeft + (GUI_WIDTH - ADMIN_GIFT_BOX_W) / 2;
        int boxY = guiTop + (GUI_HEIGHT - ADMIN_GIFT_BOX_H) / 2;
        int listTop = boxY + ADMIN_GIFT_LIST_START_Y;
        int visibleHeight = ADMIN_GIFT_BOX_H - ADMIN_GIFT_LIST_START_Y - ADMIN_GIFT_LIST_BOTTOM_MARGIN;
        int maxScroll = Math.max(0, adminEvolveCandidates.size() * ADMIN_EVOLVE_ROW_H - visibleHeight);

        if (maxScroll > 0) {
            int scrollbarX = boxX + ADMIN_GIFT_BOX_W - ADMIN_GIFT_SCROLLBAR_WIDTH - 4;
            if (isMouseOverScrollbar(mouseX, mouseY, scrollbarX, ADMIN_GIFT_SCROLLBAR_WIDTH, listTop, visibleHeight)) {
                adminEvolveScrollbarDragging = true;
                adminEvolveScrollAmount = scrollAmountFromMouseY(mouseY, listTop, visibleHeight, maxScroll);
                return true;
            }
        }

        int rowX = boxX + 10;
        int y = listTop - (int) Math.round(adminEvolveScrollAmount);
        ClientProfessorHelper.PlayerItem selected = ClientProfessorHelper.getSelected();
        for (AdminEvolveCandidate c : adminEvolveCandidates) {
            if (y >= listTop && y <= listTop + visibleHeight
                && isInRect(mouseX, mouseY, rowX, y, ADMIN_GIFT_BOX_W - 20, ADMIN_EVOLVE_ROW_H)) {
                if (selected != null && adminEditPokemon != null) {
                    if (adminEvolveIsDeEvolve) {
                        sendToServer(new com.cobblecompanion.network.AdminDeEvolvePokemonPacket(
                            selected.uuid, adminEditPokemon.getUuid()));
                    } else {
                        sendToServer(new com.cobblecompanion.network.AdminForceEvolvePokemonPacket(
                            selected.uuid, adminEditPokemon.getUuid(), c.toSpeciesId, c.toAspects));
                    }
                }
                adminEvolveOverlayOpen = false;
                adminEditOverlayOpen = false;
                return true;
            }
            y += ADMIN_EVOLVE_ROW_H;
        }
        // Abbrechen oder Klick irgendwo sonst -> nur das Auswahl-Overlay schließen, Editor bleibt offen.
        adminEvolveOverlayOpen = false;
        return true;
    }

    private int professorVisibleHeight() {
        return (guiTop + GUI_HEIGHT - PROFESSOR_BOTTOM_MARGIN) - (guiTop + PROFESSOR_LIST_Y);
    }

    private int professorMaxScroll(int count) {
        return Math.max(0, count * PROFESSOR_ROW_H - professorVisibleHeight());
    }

    private List<ClientProfessorHelper.PlayerItem> filteredProfessorPlayers() {
        String query = professorSearchBox.getValue().toLowerCase();
        List<ClientProfessorHelper.PlayerItem> result = new java.util.ArrayList<>();
        for (ClientProfessorHelper.PlayerItem p : ClientProfessorHelper.getPlayers()) {
            if (query.isEmpty() || p.name.toLowerCase().contains(query)) result.add(p);
        }
        return result;
    }

    /** Linke Hälfte: Suchfeld + scrollbare Liste aller bekannten Spieler. */
    private void renderProfessorListPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        professorSearchBox.render(graphics, mouseX, mouseY, 0f);

        List<ClientProfessorHelper.PlayerItem> players = filteredProfessorPlayers();
        int listTop = guiTop + PROFESSOR_LIST_Y;
        int visibleHeight = professorVisibleHeight();
        int maxScroll = professorMaxScroll(players.size());
        professorScrollAmount = Math.max(0, Math.min(maxScroll, professorScrollAmount));

        if (players.isEmpty()) {
            drawScaledBoldText(graphics, tr("cobblecompanion.gui.professor.none"),
                guiLeft + PROFESSOR_LIST_X, listTop, DEFAULT_TEXT_SCALE, 0x808080);
            return;
        }

        int rowX = guiLeft + PROFESSOR_LIST_X;
        int y = listTop - (int) Math.round(professorScrollAmount);
        for (ClientProfessorHelper.PlayerItem player : players) {
            if (y + PROFESSOR_ROW_H >= listTop && y <= listTop + visibleHeight) {
                renderProfessorRow(graphics, rowX, y, player);
            }
            y += PROFESSOR_ROW_H;
        }

        if (maxScroll > 0) {
            int scrollbarX = rowX + PROFESSOR_SEARCH_W + PROFESSOR_SCROLLBAR_GAP;
            renderScrollbar(graphics, scrollbarX, PROFESSOR_SCROLLBAR_WIDTH, listTop, visibleHeight, professorScrollAmount, maxScroll);
        }
    }

    private void renderProfessorRow(GuiGraphics graphics, int x, int y, ClientProfessorHelper.PlayerItem player) {
        ClientProfessorHelper.PlayerItem selected = ClientProfessorHelper.getSelected();
        boolean isSelected = selected != null && selected.uuid.equals(player.uuid);
        if (isSelected) {
            graphics.fill(x, y, x + PROFESSOR_SEARCH_W, y + PROFESSOR_ROW_H, 0x40FFFFFF);
        }

        Minecraft mc = Minecraft.getInstance();
        net.minecraft.client.multiplayer.PlayerInfo info = (mc.getConnection() != null)
            ? mc.getConnection().getPlayerInfo(player.uuid) : null;
        ResourceLocation skinTexture = (player.online && info != null)
            ? info.getSkin().texture()
            : net.minecraft.client.resources.DefaultPlayerSkin.getDefaultTexture();

        if (!player.online) RenderSystem.setShaderColor(0.5f, 0.5f, 0.5f, 1f);
        net.minecraft.client.gui.components.PlayerFaceRenderer.draw(graphics, skinTexture, x, y, PROFESSOR_HEAD_SIZE);
        if (!player.online) RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        ResourceLocation badge = player.online ? ICON_ONLINE : ICON_OFFLINE;
        graphics.blit(badge,
            x + PROFESSOR_HEAD_SIZE - PROFESSOR_BADGE_SIZE + PROFESSOR_BADGE_OFFSET_X,
            y + PROFESSOR_HEAD_SIZE - PROFESSOR_BADGE_SIZE + PROFESSOR_BADGE_OFFSET_Y,
            PROFESSOR_BADGE_SIZE, PROFESSOR_BADGE_SIZE,
            0f, 0f, 16, 16, 16, 16);

        drawScaledBoldText(graphics, player.name, x + PROFESSOR_HEAD_SIZE + PROFESSOR_NAME_OFFSET_X, y, BODY_TEXT_SCALE);
    }

    /** Rechte Hälfte: "Bitte wähle einen Spieler" oder Kopf/Name + Buttons (PC/Pokédex/Living Dex). */
    private void renderProfessorDetailPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = guiLeft + PROFESSOR_DETAIL_X;
        int y = guiTop + PROFESSOR_DETAIL_Y;
        ClientProfessorHelper.PlayerItem selected = ClientProfessorHelper.getSelected();
        if (selected == null) {
            drawScaledBoldText(graphics, tr("cobblecompanion.gui.professor.select"), x, y, DEFAULT_TEXT_SCALE, 0x808080);
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        net.minecraft.client.multiplayer.PlayerInfo info = (mc.getConnection() != null)
            ? mc.getConnection().getPlayerInfo(selected.uuid) : null;
        ResourceLocation skinTexture = (selected.online && info != null)
            ? info.getSkin().texture()
            : net.minecraft.client.resources.DefaultPlayerSkin.getDefaultTexture();

        if (!selected.online) RenderSystem.setShaderColor(0.5f, 0.5f, 0.5f, 1f);
        net.minecraft.client.gui.components.PlayerFaceRenderer.draw(graphics, skinTexture, x, y, PROFESSOR_DETAIL_HEAD_SIZE);
        if (!selected.online) RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        drawScaledBoldText(graphics, selected.name, x, y + PROFESSOR_DETAIL_NAME_OFFSET_Y, DEFAULT_TEXT_SCALE, 0xFFFFFF);

        int btnY = y + PROFESSOR_DETAIL_NAME_OFFSET_Y + PROFESSOR_BTN_START_OFFSET_Y;
        String[] labels = {
            tr("cobblecompanion.gui.professor.pc"),
            tr("cobblecompanion.gui.professor.pokedex"),
            tr("cobblecompanion.gui.professor.livingdex")
        };
        for (String label : labels) {
            renderProfessorActionButton(graphics, mouseX, mouseY, x, btnY, label, 0xFFFFFF);
            btnY += PROFESSOR_BTN_H + PROFESSOR_BTN_GAP_Y;
        }
        if (ClientAdminHelper.isAdminOp() && ClientServerRulesHelper.isRctAvailable()) {
            renderProfessorActionButton(graphics, mouseX, mouseY, x, btnY, tr("cobblecompanion.gui.professor.rct"), 0xFFFFFF);
            btnY += PROFESSOR_BTN_H + PROFESSOR_BTN_GAP_Y;
        }
        if (ClientAdminHelper.isAdminOp()) {
            btnY += PROFESSOR_RESET_BTN_GAP_Y;
            renderProfessorActionButton(graphics, mouseX, mouseY, x, btnY,
                tr("cobblecompanion.gui.professor.reset_player"), 0xFF5555);
        }
    }

    private void renderProfessorActionButton(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, String label, int textColor) {
        boolean hovered = isInRect(mouseX, mouseY, x, y, PROFESSOR_BTN_W, PROFESSOR_BTN_H);
        graphics.blit(TODO_EVOLVE_BUTTON, x, y, PROFESSOR_BTN_W, PROFESSOR_BTN_H,
            0f, hovered ? SETTINGS_BTN_NATIVE_H : 0f,
            SETTINGS_BTN_NATIVE_W, SETTINGS_BTN_NATIVE_H, SETTINGS_BTN_NATIVE_W, SETTINGS_BTN_NATIVE_H * 2);
        int labelWidth = smallLabelWidth(label, 1.0f, true, true);
        drawSmallLabel(graphics, label, x + (PROFESSOR_BTN_W - labelWidth) / 2, y + (PROFESSOR_BTN_H - 8) / 2,
            1.0f, textColor, true, true);
    }

    /** Prüft Klicks auf Suchfeld, Zeilen-Auswahl und die Aktions-Buttons (PC/Pokédex/Living Dex). */
    private boolean handleProfessorClicks(double mouseX, double mouseY) {
        ClientProfessorHelper.PlayerItem selected = ClientProfessorHelper.getSelected();
        if (selected != null) {
            int detailX = guiLeft + PROFESSOR_DETAIL_X;
            int btnY = guiTop + PROFESSOR_DETAIL_Y + PROFESSOR_DETAIL_NAME_OFFSET_Y + PROFESSOR_BTN_START_OFFSET_Y;
            // Index 0 = PC, 1 = Pokédex, 2 = Living Dex (siehe renderProfessorDetailPanel). Team
            // wurde entfernt - die PC-Ansicht zeigt Box UND Party gleichzeitig und deckt damit
            // Team-Verschieben bereits mit ab (Cobblemons eigenes Move-System dort funktionierte,
            // das separate Summary-Fenster war redundant und hatte kein Live-Rebind für unser
            // Verschieben-System).
            for (int i = 0; i < 3; i++) {
                if (isInRect(mouseX, mouseY, detailX, btnY, PROFESSOR_BTN_W, PROFESSOR_BTN_H)) {
                    if (i == 0) {
                        professorPCRightClickEditEnabled = true;
                        sendToServer(new ProfessorPCRequestPacket(selected.uuid));
                    } else if (i == 1) {
                        sendToServer(new ProfessorPokedexRequestPacket(selected.uuid));
                    } else {
                        // "Living Dex": gleiche eingebettete PokedexGUI wie "Pokédex", zusätzlich
                        // Blatt-Icon-Overlays anhand der aktuell besessenen Spezies des Zielspielers.
                        sendToServer(new ProfessorLivingDexRequestPacket(selected.uuid));
                    }
                    return true;
                }
                btnY += PROFESSOR_BTN_H + PROFESSOR_BTN_GAP_Y;
            }
            if (ClientAdminHelper.isAdminOp() && ClientServerRulesHelper.isRctAvailable()) {
                if (isInRect(mouseX, mouseY, detailX, btnY, PROFESSOR_BTN_W, PROFESSOR_BTN_H)) {
                    professorViewingRct = true;
                    rctScrollAmount = 0;
                    sendToServer(new ProfessorRctListRequestPacket(selected.uuid));
                    return true;
                }
                btnY += PROFESSOR_BTN_H + PROFESSOR_BTN_GAP_Y;
            }
            if (ClientAdminHelper.isAdminOp()) {
                btnY += PROFESSOR_RESET_BTN_GAP_Y;
                if (isInRect(mouseX, mouseY, detailX, btnY, PROFESSOR_BTN_W, PROFESSOR_BTN_H)) {
                    resetPlayerConfirmStage = 1;
                    resetPlayerConfirmInput = "";
                    return true;
                }
            }
        }

        List<ClientProfessorHelper.PlayerItem> players = filteredProfessorPlayers();
        int rowX = guiLeft + PROFESSOR_LIST_X;
        int listTop = guiTop + PROFESSOR_LIST_Y;
        int visibleHeight = professorVisibleHeight();
        int maxScroll = professorMaxScroll(players.size());

        if (maxScroll > 0) {
            int scrollbarX = rowX + PROFESSOR_SEARCH_W + PROFESSOR_SCROLLBAR_GAP;
            if (isMouseOverScrollbar(mouseX, mouseY, scrollbarX, PROFESSOR_SCROLLBAR_WIDTH, listTop, visibleHeight)) {
                professorScrollbarDragging = true;
                professorScrollAmount = scrollAmountFromMouseY(mouseY, listTop, visibleHeight, maxScroll);
                return true;
            }
        }

        int y = listTop - (int) Math.round(professorScrollAmount);
        for (ClientProfessorHelper.PlayerItem player : players) {
            if (y >= listTop && y <= listTop + visibleHeight
                && isInRect(mouseX, mouseY, rowX, y, PROFESSOR_SEARCH_W, PROFESSOR_ROW_H)) {
                ClientProfessorHelper.select(player.uuid);
                return true;
            }
            y += PROFESSOR_ROW_H;
        }
        return false;
    }

    // ===== ToDo-Zeilen-Modell: große Party/PC-Überschrift + kleine PC-Box-Unterüberschrift =====
    private static final int TODO_ROWTYPE_ENTRY = 0;
    private static final int TODO_ROWTYPE_HEADING = 1;
    private static final int TODO_ROWTYPE_BOX_HEADING = 2;

    private static final int TODO_HEADING_H = 14;
    private static final float TODO_HEADING_TEXT_SCALE = 1.0f;
    private static final boolean TODO_HEADING_BOLD = true;
    private static final boolean TODO_HEADING_UNIFORM_FONT = true;
    private static final int TODO_HEADING_COLOR = 0xFFAA00;

    // Kleinere Unterüberschrift je PC-Box ("Box 3" bzw. "Box 3 - <Custom-Name>").
    private static final int TODO_BOX_HEADING_H = 13;
    private static final float TODO_BOX_HEADING_TEXT_SCALE = 1.0f;
    private static final boolean TODO_BOX_HEADING_BOLD = true;
    private static final boolean TODO_BOX_HEADING_UNIFORM_FONT = true;
    private static final int TODO_BOX_HEADING_COLOR = 0xFFFFFF;

    private static class TodoRow {
        final int type;
        final String headingKey; // bei HEADING: Sprachschlüssel; bei BOX_HEADING: fertiger (schon übersetzter) Text
        final ClientTodoHelper.TodoEntry entry;
        // true = dasselbe Ausgangs-Pokemon wie die Zeile direkt davor (mehrere gleichzeitig
        // mögliche Entwicklungsziele, z.B. Evoli mit zwei angewendeten Steinen) - FROM-Slot/Name
        // werden dann nicht wiederholt, stattdessen eine "L"-Linie zur ersten Zeile der Gruppe.
        final boolean groupContinuation;
        TodoRow(int type, String headingKey, ClientTodoHelper.TodoEntry entry, boolean groupContinuation) {
            this.type = type;
            this.headingKey = headingKey;
            this.entry = entry;
            this.groupContinuation = groupContinuation;
        }
        TodoRow(int type, String headingKey, ClientTodoHelper.TodoEntry entry) {
            this(type, headingKey, entry, false);
        }
    }

    // JUSTIERSCHRAUBE: deutlich kleinerer Abstand VOR einer Gruppen-Folgezeile (L-Linie, mehrere
    // gleichzeitig mögliche Entwicklungsziele desselben Pokemon) als der normale Zeilenabstand.
    private static final int TODO_ROW_GROUP_SPACING = 3;

    /** Höhe der Zeile bei index INKLUSIVE ihres Abstands nach unten - kleiner, wenn die nächste Zeile eine Gruppen-Folgezeile (L-Linie) ist. */
    private int todoRowHeight(List<TodoRow> rows, int index) {
        TodoRow row = rows.get(index);
        return switch (row.type) {
            case TODO_ROWTYPE_HEADING -> TODO_HEADING_H;
            case TODO_ROWTYPE_BOX_HEADING -> TODO_BOX_HEADING_H;
            default -> {
                boolean nextIsGroupSibling = index + 1 < rows.size() && rows.get(index + 1).groupContinuation;
                yield TODO_ROW_HEIGHT + (nextIsGroupSibling ? TODO_ROW_GROUP_SPACING : TODO_ROW_SPACING);
            }
        };
    }

    private int todoContentHeight(List<TodoRow> rows) {
        if (rows.isEmpty()) return 0;
        int total = 0;
        for (int i = 0; i < rows.size(); i++) total += todoRowHeight(rows, i);
        // Der letzte Eintrag braucht seinen Trailing-Spacing nicht als sichtbaren Leerraum.
        TodoRow last = rows.get(rows.size() - 1);
        if (last.type == TODO_ROWTYPE_ENTRY) total -= TODO_ROW_SPACING;
        return total;
    }

    private int todoMaxScroll(List<TodoRow> rows) {
        return Math.max(0, todoContentHeight(rows) - TODO_LIST_VISIBLE_HEIGHT);
    }

    /** ToDo-Einträge gefiltert nach "Modus" (Pokédex- bzw. Living-Dex-relevant, siehe ClientSettingsHelper). */
    private List<ClientTodoHelper.TodoEntry> getFilteredTodoEntries() {
        boolean pokedexModus = ClientSettingsHelper.isModusPokedex();
        List<ClientTodoHelper.TodoEntry> result = new java.util.ArrayList<>();
        for (ClientTodoHelper.TodoEntry e : ClientTodoHelper.getEntries()) {
            if (pokedexModus ? e.needsPokedex : e.needsLivingDex) result.add(e);
        }
        return result;
    }

    /**
     * Baut die anzuzeigenden Zeilen aus den gefilterten Einträgen: große Überschrift Party vs.
     * PC, PC zusätzlich nach Box sortiert mit kleiner Unterüberschrift ("Box N" bzw.
     * "Box N - <Custom-Name>") vor jeder Box-Gruppe (auch vor der ersten).
     */
    private List<TodoRow> buildTodoRows() {
        List<ClientTodoHelper.TodoEntry> filtered = getFilteredTodoEntries();
        List<TodoRow> rows = new java.util.ArrayList<>();

        List<ClientTodoHelper.TodoEntry> partyEntries = new java.util.ArrayList<>();
        List<ClientTodoHelper.TodoEntry> pcEntries = new java.util.ArrayList<>();
        for (ClientTodoHelper.TodoEntry e : filtered) {
            if (e.isParty) partyEntries.add(e); else pcEntries.add(e);
        }

        if (!partyEntries.isEmpty()) {
            rows.add(new TodoRow(TODO_ROWTYPE_HEADING, "cobblecompanion.gui.todo.party", null));
            java.util.UUID lastUuid = null;
            for (ClientTodoHelper.TodoEntry e : partyEntries) {
                rows.add(new TodoRow(TODO_ROWTYPE_ENTRY, null, e, e.pokemonUuid.equals(lastUuid)));
                lastUuid = e.pokemonUuid;
            }
        }
        if (!pcEntries.isEmpty()) {
            pcEntries.sort(java.util.Comparator.comparingInt(e -> e.pcBox));
            rows.add(new TodoRow(TODO_ROWTYPE_HEADING, "cobblecompanion.gui.todo.pc", null));
            int lastBox = Integer.MIN_VALUE;
            java.util.UUID lastUuid = null;
            boolean first = true;
            for (ClientTodoHelper.TodoEntry e : pcEntries) {
                if (first || e.pcBox != lastBox) {
                    // Cobblemons Boxen sind intern 0-indiziert, im PC-UI aber "Box 1", "Box 2", ... .
                    String label = tr("cobblecompanion.gui.todo.box", e.pcBox + 1);
                    if (!e.pcBoxName.isBlank()) label += " - " + e.pcBoxName;
                    rows.add(new TodoRow(TODO_ROWTYPE_BOX_HEADING, label, null));
                    lastUuid = null;
                }
                rows.add(new TodoRow(TODO_ROWTYPE_ENTRY, null, e, e.pokemonUuid.equals(lastUuid)));
                lastBox = e.pcBox;
                lastUuid = e.pokemonUuid;
                first = false;
            }
        }
        return rows;
    }

    // JUSTIERSCHRAUBE: Position, an der Cobblemons eigener Pokédex-Tab seinen "gesehen/gefangen"-
    // Zähler zeigt (per javap-Bytecode-Analyse von PokedexGUI grob verortet, ~x+252..290/y+14) -
    // ToDo- und WhoNeeds-Tab haben keinen eigenen Zähler dort, zeigen an dieser Stelle stattdessen
    // den aktuellen "Modus" (Pokédex/Living Dex) als reinen Infotext.
    private static final int MODUS_LABEL_X = 252;
    private static final int MODUS_LABEL_Y = 14;

    private void renderModusLabel(GuiGraphics graphics) {
        boolean livingDexPlus = ClientSettingsHelper.isPcSortModeLivingDexPlus();
        String modeName = switch (ClientSettingsHelper.getPcSortMode()) {
            case ClientSettingsHelper.PC_SORT_MODE_LIVING_DEX -> tr("cobblecompanion.settings.filter.livingdex");
            case ClientSettingsHelper.PC_SORT_MODE_LIVING_DEX_PLUS -> tr("cobblecompanion.settings.filter.livingdexplus");
            default -> tr("cobblecompanion.settings.filter.pokedex");
        };
        String modusText = tr("cobblecompanion.gui.modus_label", modeName);
        // Living Dex+ auch hier immer Gold/Gelb, wie in den Settings.
        drawSmallLabel(graphics, modusText, guiLeft + MODUS_LABEL_X, guiTop + MODUS_LABEL_Y, 1.0f,
            livingDexPlus ? SETTINGS_LDP_GOLD_COLOR : 0xFFFFFFFF, false, true);
    }

    private void renderTodoTab(GuiGraphics graphics, int mouseX, int mouseY) {
        renderModusLabel(graphics);
        renderDexHelpPanel(graphics, mouseX, mouseY);

        List<TodoRow> rows = buildTodoRows();
        boolean showEvolveButton = ClientSettingsHelper.isTodoShowEvolveButton();
        int rowX = guiLeft + TODO_ROW_X;
        int listTop = guiTop + TODO_ROW_Y;
        if (rows.isEmpty()) {
            drawScaledBoldText(graphics, tr("cobblecompanion.gui.todo.empty1"), rowX, listTop, DEFAULT_TEXT_SCALE, 0xFFFFFF);
            drawScaledBoldText(graphics, tr("cobblecompanion.gui.todo.empty2_line1"), rowX, listTop + 12, DEFAULT_TEXT_SCALE, 0x808080);
            drawScaledBoldText(graphics, tr("cobblecompanion.gui.todo.empty2_line2"), rowX, listTop + 22, DEFAULT_TEXT_SCALE, 0x808080);
            return;
        }

        int maxScroll = todoMaxScroll(rows);
        todoScrollAmount = Math.max(0, Math.min(maxScroll, todoScrollAmount));

        // Liste ist unbegrenzt und scrollbar - alle Zeilen werden gebaut, aber per Scissor auf
        // den sichtbaren Bereich beschnitten.
        graphics.enableScissor(rowX, listTop, rowX + TODO_ROW_WIDTH, listTop + TODO_LIST_VISIBLE_HEIGHT);
        boolean evolveButtonHovered = false;
        int rowY = listTop - (int) Math.round(todoScrollAmount);
        for (int i = 0; i < rows.size(); i++) {
            TodoRow row = rows.get(i);
            int rowH = todoRowHeight(rows, i);
            if (rowY + rowH >= listTop && rowY <= listTop + TODO_LIST_VISIBLE_HEIGHT) {
                switch (row.type) {
                    case TODO_ROWTYPE_HEADING -> drawSmallLabel(graphics, tr(row.headingKey), rowX, rowY,
                        TODO_HEADING_TEXT_SCALE, TODO_HEADING_COLOR, TODO_HEADING_BOLD, TODO_HEADING_UNIFORM_FONT);
                    case TODO_ROWTYPE_BOX_HEADING -> drawSmallLabel(graphics, row.headingKey, rowX, rowY,
                        TODO_BOX_HEADING_TEXT_SCALE, TODO_BOX_HEADING_COLOR, TODO_BOX_HEADING_BOLD, TODO_BOX_HEADING_UNIFORM_FONT);
                    default -> {
                        renderTodoRow(graphics, rowX, rowY, row.entry, mouseX, mouseY, row.groupContinuation);
                        if (showEvolveButton && row.entry.canEvolveNow
                                && isMouseOverTodoEvolveButton(rowX + PokemonSlotRenderer.SLOT_SIZE, rowY, mouseX, mouseY)) {
                            evolveButtonHovered = true;
                        }
                    }
                }
            }
            rowY += rowH;
        }
        graphics.disableScissor();

        int scrollbarX = rowX + TODO_ROW_WIDTH + TODO_SCROLLBAR_GAP;
        renderScrollbar(graphics, scrollbarX, TODO_SCROLLBAR_WIDTH, listTop, TODO_LIST_VISIBLE_HEIGHT, todoScrollAmount, maxScroll);

        if (evolveButtonHovered) {
            renderCobblemonTooltip(graphics, Component.translatable("cobblecompanion.gui.todo.evolve"), mouseX, mouseY, -14);
        }
    }

    /**
     * Zeichnet eine ToDo-Zeile: links FROM-Slot (mit Nummer+Level im Slot) + kurze obere
     * type_bar (Typ-Icon -> Name des aktuellen Pokemon). Rechts je nach Fall entweder TO-Slot
     * + kurze gespiegelte untere Bar (Name <- Typ-Icon, näher am Slot) plus Entwickeln-Button
     * in der Lücke dazwischen (Entwicklung sofort möglich), oder Item-Icon + Name statt des
     * TO-Slots (Entwicklung braucht ein Item, das der Spieler dem Pokemon noch geben muss).
     */
    private void renderTodoRow(GuiGraphics graphics, int rowX, int rowY, ClientTodoHelper.TodoEntry entry, int mouseX, int mouseY, boolean groupContinuation) {
        // Beide Bars überbrücken jetzt wieder dieselbe volle Lücke zwischen den Slots (nur an
        // unterschiedlicher Y-Position), daher ein gemeinsames barX für oben und unten.
        int barX = rowX + PokemonSlotRenderer.SLOT_SIZE;
        int toSlotX = rowX + TODO_ROW_WIDTH - PokemonSlotRenderer.SLOT_SIZE;
        int bottomBarY = rowY + TODO_ROW_HEIGHT - TODO_BAR_HEIGHT;

        // Mehrere gleichzeitig mögliche Entwicklungsziele desselben Pokemon (z.B. Evoli mit zwei
        // angewendeten Steinen): FROM-Slot/Name nur bei der ersten Zeile der Gruppe zeigen, bei
        // Folgezeilen stattdessen eine "L"-Linie zur ersten Zeile.
        if (!groupContinuation) {
            renderPokemonNumberedSlot(graphics, rowX, rowY, entry.fromSpeciesId, entry.fromAspects, entry.fromLevel);
            renderTodoCaughtBallIcon(graphics, rowX, rowY, entry.caughtBallId);
            graphics.blit(TODO_TYPE_BAR, barX, rowY, TODO_BAR_WIDTH, TODO_BAR_HEIGHT,
                0f, 0f, TODO_TYPE_BAR_NATIVE_W, TODO_TYPE_BAR_NATIVE_H, TODO_TYPE_BAR_NATIVE_W, TODO_TYPE_BAR_NATIVE_H);
            renderTodoBarLabel(graphics, barX, rowY, entry.fromSpeciesId, entry.fromAspects, false, entry.nickname);
        } else {
            renderTodoGroupConnector(graphics, rowX, rowY, barX);
        }

        if (entry.canEvolveNow) {
            renderPokemonSlotNumber(graphics, toSlotX, rowY, entry.toSpeciesId, entry.toAspects);
            blitTypeBarFlipped(graphics, barX, bottomBarY, TODO_BAR_WIDTH, TODO_BAR_HEIGHT);
            renderTodoBarLabel(graphics, barX, bottomBarY, entry.toSpeciesId, entry.toAspects, true, null);
            if (ClientSettingsHelper.isTodoShowEvolveButton()) {
                renderTodoEvolveButton(graphics, barX, rowY, mouseX, mouseY);
            }
        } else if (entry.itemId != null) {
            Item item = BuiltInRegistries.ITEM.get(entry.itemId);
            ItemStack stack = item.getDefaultInstance();
            int iconX = toSlotX + (PokemonSlotRenderer.SLOT_SIZE - TODO_ITEM_ICON_SIZE) / 2;
            int iconY = rowY + (PokemonSlotRenderer.SLOT_SIZE - TODO_ITEM_ICON_SIZE) / 2 - 3;
            graphics.renderItem(stack, iconX, iconY);
            blitTypeBarFlipped(graphics, barX, bottomBarY, TODO_BAR_WIDTH, TODO_BAR_HEIGHT);
            String itemName = truncateLabel(stack.getHoverName().getString(), TODO_TO_NAME_SCALE, TODO_TO_NAME_BOLD,
                TODO_TO_NAME_UNIFORM_FONT, TODO_BAR_WIDTH - TODO_TO_NAME_OFFSET_X * 2);
            int nameWidth = smallLabelWidth(itemName, TODO_TO_NAME_SCALE, TODO_TO_NAME_BOLD, TODO_TO_NAME_UNIFORM_FONT);
            drawSmallLabel(graphics, itemName,
                barX + TODO_BAR_WIDTH - TODO_TO_NAME_OFFSET_X - nameWidth,
                bottomBarY + TODO_TO_NAME_OFFSET_Y, TODO_TO_NAME_SCALE, 0xFFFFFF, TODO_TO_NAME_BOLD, TODO_TO_NAME_UNIFORM_FONT);
        }
    }

    // JUSTIERSCHRAUBE: "L"-Verbindungslinie für Folgezeilen derselben Gruppe (mehrere
    // gleichzeitig mögliche Entwicklungsziele) statt wiederholtem FROM-Slot/Name.
    private static final int TODO_GROUP_LINE_COLOR = 0xFFAAAAAA;
    private static final int TODO_GROUP_LINE_THICKNESS = 1;

    /** Zeichnet an Stelle des FROM-Slots eine "L"-Linie: hoch zur vorherigen Zeile, dann rechts zur Bar. */
    private void renderTodoGroupConnector(GuiGraphics graphics, int rowX, int rowY, int barX) {
        int lineX = rowX + PokemonSlotRenderer.SLOT_SIZE / 2;
        int midY = rowY + PokemonSlotRenderer.SLOT_SIZE / 2;
        graphics.fill(lineX, rowY - TODO_ROW_GROUP_SPACING, lineX + TODO_GROUP_LINE_THICKNESS, midY, TODO_GROUP_LINE_COLOR);
        graphics.fill(lineX, midY, barX, midY + TODO_GROUP_LINE_THICKNESS, TODO_GROUP_LINE_COLOR);
    }

    // JUSTIERSCHRAUBE: Fangball-Icon oben rechts am Ausgangs-Slot im ToDo-Tab (Stream-Wunsch).
    private static final int TODO_CAUGHT_BALL_ICON_SIZE = 8;
    private static final int TODO_CAUGHT_BALL_OFFSET_X = 1;
    private static final int TODO_CAUGHT_BALL_OFFSET_Y = 1;

    /** Zeichnet den Fangball des Ausgangs-Pokemon oben rechts am Slot, falls bekannt. */
    private void renderTodoCaughtBallIcon(GuiGraphics graphics, int slotX, int slotY, ResourceLocation caughtBallId) {
        if (caughtBallId == null) return;
        Item ballItem = BuiltInRegistries.ITEM.get(caughtBallId);
        if (ballItem == null) return;
        ItemStack ballStack = ballItem.getDefaultInstance();
        int x = slotX + PokemonSlotRenderer.SLOT_SIZE - TODO_CAUGHT_BALL_ICON_SIZE - TODO_CAUGHT_BALL_OFFSET_X;
        int y = slotY + TODO_CAUGHT_BALL_OFFSET_Y;
        float scale = TODO_CAUGHT_BALL_ICON_SIZE / 16f;

        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 300);
        graphics.pose().scale(scale, scale, 1f);
        graphics.renderItem(ballStack, 0, 0);
        graphics.pose().popPose();
    }

    /**
     * Zeichnet Typ-Icon + Name auf eine bereits gezeichnete type_bar. mirrored=false (obere
     * Bar): Icon links, Name rechts davon, normal ausgerichtet. mirrored=true (untere,
     * gedrehte Bar): Name links, Icon rechts davon direkt am angrenzenden Slot - spiegel-
     * verkehrte Reihenfolge auf der jeweils anderen Seite der Bar. Name wird bei Bedarf
     * abgeschnitten ("..."), damit er nie über den Bar-Rand hinaus in Slot/Nachbar-Bar läuft.
     * nickname (nur bei mirrored=false, also dem Ausgangs-Pokemon relevant) wird als
     * "Art - Spitzname" angehängt, falls vergeben.
     */
    private void renderTodoBarLabel(GuiGraphics graphics, int barX, int barY, ResourceLocation speciesId, Set<String> aspects, boolean mirrored, String nickname) {
        Species species = PokemonSpecies.INSTANCE.getByIdentifier(speciesId);
        if (species == null) return;
        FormData form = species.getForm(aspects);
        ElementalType primary = form.getPrimaryType();
        ElementalType secondary = form.getSecondaryType();

        // mirrored=false = obere Bar = Ausgangspokemon (FROM), mirrored=true = untere,
        // gespiegelte Bar = Zielpokemon (TO) - jeweils eigene Justierschrauben.
        float nameScale = mirrored ? TODO_TO_NAME_SCALE : TODO_FROM_NAME_SCALE;
        int nameOffsetX = mirrored ? TODO_TO_NAME_OFFSET_X : TODO_FROM_NAME_OFFSET_X;
        int nameOffsetY = mirrored ? TODO_TO_NAME_OFFSET_Y : TODO_FROM_NAME_OFFSET_Y;
        boolean nameBold = mirrored ? TODO_TO_NAME_BOLD : TODO_FROM_NAME_BOLD;
        boolean nameUniform = mirrored ? TODO_TO_NAME_UNIFORM_FONT : TODO_FROM_NAME_UNIFORM_FONT;
        int iconOffsetX = mirrored ? TODO_TO_ICON_OFFSET_X : TODO_FROM_ICON_OFFSET_X;
        int iconOffsetY = mirrored ? TODO_TO_ICON_OFFSET_Y : TODO_FROM_ICON_OFFSET_Y;

        String displayName = speciesDisplayName(species);
        if (nickname != null && !nickname.isBlank()) {
            displayName = displayName + " - " + nickname;
        }
        // JUSTIERSCHRAUBE: Textbox nutzt jetzt die volle Balkenbreite (nicht mehr um die
        // Typ-Icon-Breite verkleinert) - Name/Spitzname wurde vorher zu früh abgeschnitten.
        int maxTextWidth = TODO_BAR_WIDTH - (nameOffsetX * 2);
        String name = truncateLabel(displayName, nameScale, nameBold, nameUniform, maxTextWidth);
        int iconY = barY + iconOffsetY;

        if (mirrored) {
            // Gespiegelte (untere) Bar: Icon und Name jeweils von der RECHTEN Kante der Bar aus
            // gemessen (näher am angrenzenden Slot), unabhängig voneinander positionierbar.
            if (primary != null) {
                int iconX = barX + TODO_BAR_WIDTH - iconOffsetX - TODO_BAR_TYPE_ICON_SIZE;
                new TypeIcon(iconX, iconY, primary, secondary, false, true, TODO_BAR_TYPE_ICON_SECONDARY_OFFSET, 7.5F, 1F).render(graphics);
            }
            int textWidth = smallLabelWidth(name, nameScale, nameBold, nameUniform);
            int textRight = barX + TODO_BAR_WIDTH - nameOffsetX;
            drawSmallLabel(graphics, name, textRight - textWidth, barY + nameOffsetY, nameScale, 0xFFFFFF, nameBold, nameUniform);
        } else {
            // Obere Bar: Icon und Name jeweils von der LINKEN Kante der Bar aus gemessen,
            // unabhängig voneinander positionierbar.
            if (primary != null) {
                int iconX = barX + iconOffsetX;
                new TypeIcon(iconX, iconY, primary, secondary, false, true, TODO_BAR_TYPE_ICON_SECONDARY_OFFSET, 7.5F, 1F).render(graphics);
            }
            drawSmallLabel(graphics, name, barX + nameOffsetX, barY + nameOffsetY, nameScale, 0xFFFFFF, nameBold, nameUniform);
        }
    }

    /** Horizontale Position des Entwickeln-Buttons: mittig über der Nahtstelle der beiden Bars. */
    private int todoEvolveBtnX(int barX) {
        return barX + (TODO_BAR_WIDTH - TODO_EVOLVE_BTN_W) / 2;
    }

    private boolean isMouseOverTodoEvolveButton(int barX, int rowY, int mouseX, int mouseY) {
        int btnX = todoEvolveBtnX(barX);
        int btnY = rowY + (TODO_ROW_HEIGHT - TODO_EVOLVE_BTN_H) / 2;
        return mouseX >= btnX && mouseX < btnX + TODO_EVOLVE_BTN_W
            && mouseY >= btnY && mouseY < btnY + TODO_EVOLVE_BTN_H;
    }

    /**
     * Entwickeln-Button, mittig über der Nahtstelle der beiden Bars, aus Cobblemons eigenem
     * Party-Menü übernommen (summary_evolve_button.png). Button-Text wie Cobblemons eigener
     * SummaryButton (com.cobblemon.mod.common.client.gui.summary.SummaryButton.renderWidget()):
     * zentriert, uniform+fett. Hover-Zustand über die obere/untere Sprite-Hälfte wie bei den
     * anderen Icon-Buttons.
     */
    private void renderTodoEvolveButton(GuiGraphics graphics, int barX, int rowY, int mouseX, int mouseY) {
        boolean hovered = isMouseOverTodoEvolveButton(barX, rowY, mouseX, mouseY);
        int btnX = todoEvolveBtnX(barX);
        int btnY = rowY + (TODO_ROW_HEIGHT - TODO_EVOLVE_BTN_H) / 2;

        graphics.blit(TODO_EVOLVE_BUTTON, btnX, btnY, TODO_EVOLVE_BTN_W, TODO_EVOLVE_BTN_H,
            0f, hovered ? TODO_EVOLVE_BTN_NATIVE_H : 0f,
            TODO_EVOLVE_BTN_NATIVE_W, TODO_EVOLVE_BTN_NATIVE_H, TODO_EVOLVE_BTN_NATIVE_W, TODO_EVOLVE_BTN_NATIVE_H * 2);

        String label = tr("cobblecompanion.gui.todo.evolve");
        int textWidth = smallLabelWidth(label, TODO_EVOLVE_BTN_TEXT_SCALE, TODO_EVOLVE_BTN_TEXT_BOLD, TODO_EVOLVE_BTN_TEXT_UNIFORM_FONT);
        int textX = btnX + (TODO_EVOLVE_BTN_W - textWidth) / 2;
        int textY = btnY + (TODO_EVOLVE_BTN_H - Math.round(9 * TODO_EVOLVE_BTN_TEXT_SCALE)) / 2;
        drawSmallLabel(graphics, label, textX, textY, TODO_EVOLVE_BTN_TEXT_SCALE, 0xFFFFFF, TODO_EVOLVE_BTN_TEXT_BOLD, TODO_EVOLVE_BTN_TEXT_UNIFORM_FONT);
    }

    /** Zeichnet die type_bar.png-Textur um 180° gedreht (für die untere Bar der ToDo-Zeile). */
    private void blitTypeBarFlipped(GuiGraphics graphics, int x, int y, int width, int height) {
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x + width / 2.0, y + height / 2.0, 0);
        pose.mulPose(new Quaternionf().rotationZ((float) Math.PI));
        pose.translate(-width / 2.0, -height / 2.0, 0);
        graphics.blit(TODO_TYPE_BAR, 0, 0, width, height,
            0f, 0f, TODO_TYPE_BAR_NATIVE_W, TODO_TYPE_BAR_NATIVE_H, TODO_TYPE_BAR_NATIVE_W, TODO_TYPE_BAR_NATIVE_H);
        pose.popPose();
    }

    /** Prüft Klicks auf Scrollbar und Entwickeln-Buttons der sichtbaren ToDo-Zeilen. */
    private boolean handleTodoClicks(double mouseX, double mouseY) {
        List<TodoRow> rows = buildTodoRows();
        int rowX = guiLeft + TODO_ROW_X;
        int listTop = guiTop + TODO_ROW_Y;
        int maxScroll = todoMaxScroll(rows);

        int scrollbarX = rowX + TODO_ROW_WIDTH + TODO_SCROLLBAR_GAP;
        if (maxScroll > 0 && isMouseOverScrollbar(mouseX, mouseY, scrollbarX, TODO_SCROLLBAR_WIDTH, listTop, TODO_LIST_VISIBLE_HEIGHT)) {
            todoScrollbarDragging = true;
            todoScrollAmount = scrollAmountFromMouseY(mouseY, listTop, TODO_LIST_VISIBLE_HEIGHT, maxScroll);
            return true;
        }

        // Klick auf den Entwickeln-Button nur, wenn er auch angezeigt wird (Setting).
        if (!ClientSettingsHelper.isTodoShowEvolveButton()) return false;

        int rowY = listTop - (int) Math.round(todoScrollAmount);
        for (int i = 0; i < rows.size(); i++) {
            TodoRow row = rows.get(i);
            int rowH = todoRowHeight(rows, i);
            if (row.type == TODO_ROWTYPE_ENTRY && row.entry.canEvolveNow
                && rowY + rowH >= listTop && rowY <= listTop + TODO_LIST_VISIBLE_HEIGHT
                && isMouseOverTodoEvolveButton(rowX + PokemonSlotRenderer.SLOT_SIZE, rowY, (int) mouseX, (int) mouseY)) {
                if (ClientSettingsHelper.isTodoConfirmEvolution()) {
                    pendingEvolveEntry = row.entry; // Sicherheitsabfrage-Overlay öffnen
                } else {
                    performEvolve(row.entry);
                }
                return true;
            }
            rowY += rowH;
        }
        return false;
    }

    /** Sendet die Entwicklung an den Server (inkl. Send-out-Flag) und schließt ggf. den Screen. */
    private void performEvolve(ClientTodoHelper.TodoEntry entry) {
        String toAspects = String.join(",", entry.toAspects);
        sendToServer(new EvolvePokemonRequestPacket(
            entry.pokemonUuid, entry.toSpeciesId, toAspects, ClientSettingsHelper.isTodoSendOutBeforeEvolve()));
        pendingEvolveEntry = null;
        if (ClientSettingsHelper.isTodoCloseOnEvolve()) {
            this.onClose();
        }
    }

    // ===== Dex-Vervollständigungshilfe (ToDo-Tab, rechte Hälfte) =====

    private static final int DEXHELP_ROWTYPE_CATEGORY = 0;
    private static final int DEXHELP_ROWTYPE_SUBCATEGORY = 1;
    private static final int DEXHELP_ROWTYPE_CATCH = 2;
    private static final int DEXHELP_ROWTYPE_EVOLVE = 3;
    private static final int DEXHELP_ROWTYPE_NEED = 4;

    /** Eine Bedarfsliste-Zeile - fertiger Text + optionaler Item-Pfad (Icon) + Farbe (siehe buildDexHelpNeedsList()). */
    private static class DexHelpNeedLine {
        final String text;
        final String itemPath; // für Icon-Anzeige, null/leer = kein Icon
        final int color;
        DexHelpNeedLine(String text, String itemPath, int color) {
            this.text = text;
            this.itemPath = itemPath;
            this.color = color;
        }
    }

    private static class DexHelpRow {
        final int type;
        final String key; // Ausklapp-Schlüssel (nur Kategorie/Unterkategorie)
        final String label; // fertiger Anzeigetext (Kategorie/Unterkategorie)
        final ClientDexCompletionHelper.CatchEntry catchEntry;
        final ClientDexCompletionHelper.EvolveEntry evolveEntry;
        final DexHelpNeedLine needLine;

        private DexHelpRow(int type, String key, String label, ClientDexCompletionHelper.CatchEntry catchEntry,
                            ClientDexCompletionHelper.EvolveEntry evolveEntry, DexHelpNeedLine needLine) {
            this.type = type;
            this.key = key;
            this.label = label;
            this.catchEntry = catchEntry;
            this.evolveEntry = evolveEntry;
            this.needLine = needLine;
        }

        static DexHelpRow category(String key, String label) { return new DexHelpRow(DEXHELP_ROWTYPE_CATEGORY, key, label, null, null, null); }
        static DexHelpRow subcategory(String key, String label) { return new DexHelpRow(DEXHELP_ROWTYPE_SUBCATEGORY, key, label, null, null, null); }
        static DexHelpRow catchRow(ClientDexCompletionHelper.CatchEntry e) { return new DexHelpRow(DEXHELP_ROWTYPE_CATCH, null, null, e, null, null); }
        static DexHelpRow evolveRow(ClientDexCompletionHelper.EvolveEntry e) { return new DexHelpRow(DEXHELP_ROWTYPE_EVOLVE, null, null, null, e, null); }
        static DexHelpRow need(DexHelpNeedLine line) { return new DexHelpRow(DEXHELP_ROWTYPE_NEED, null, null, null, null, line); }
    }

    /**
     * Baut die (flache) Zeilenliste aus dem aktuellen ClientDexCompletionHelper-Stand + dem
     * Ausklapp-Zustand (dexHelpExpanded) - "Fangen [...]" und "Entwicklungen [Level [...], Stein
     * [...], Freundschaft [...], Tausch [...], Sonstige [...], Bedarfsliste [...]]" wie vom Nutzer
     * vorgegeben. Muss identisch von render und Klick-Erkennung genutzt werden (siehe
     * buildTodoRows()-Vorbild), sonst driften Anzeige und Treffer-Erkennung auseinander.
     */
    private List<DexHelpRow> buildDexHelpRows() {
        List<DexHelpRow> rows = new java.util.ArrayList<>();
        // Punkt 1 (Runde 2): nach Pokedex-Nummer sortiert statt in Registrierungsreihenfolge.
        List<ClientDexCompletionHelper.CatchEntry> catchEntries = new java.util.ArrayList<>(ClientDexCompletionHelper.getCatchEntries());
        catchEntries.sort(java.util.Comparator.comparingInt(e -> {
            Species s = PokemonSpecies.INSTANCE.getByIdentifier(e.speciesId);
            return s != null ? s.getNationalPokedexNumber() : Integer.MAX_VALUE;
        }));
        // BUGFIX: die Zahl in der Überschrift zeigte nur die Anzahl Wurzel-Arten, nicht die
        // tatsächlich benötigte Stückzahl (Living-Dex-Mengen-Konzept, z.B. 2x Bisasam gebraucht
        // zählte bisher nur als 1) - Summe aus (selfNeeded?1:0)+extraCopies je Eintrag.
        int catchTotal = 0;
        for (ClientDexCompletionHelper.CatchEntry e : catchEntries) catchTotal += (e.selfNeeded ? 1 : 0) + e.extraCopies;
        rows.add(DexHelpRow.category("CATCH", tr("cobblecompanion.gui.dexhelp.catch_category", catchTotal)));
        if (dexHelpExpanded.contains("CATCH")) {
            addDexHelpCatchSubcategory(rows, catchEntries, CatchSubcategory.NORMAL, "CATCH_NORMAL", "cobblecompanion.gui.dexhelp.catch_sub_normal");
            addDexHelpCatchSubcategory(rows, catchEntries, CatchSubcategory.SHINY, "CATCH_SHINY", "cobblecompanion.gui.dexhelp.catch_sub_shiny");
            addDexHelpCatchSubcategory(rows, catchEntries, CatchSubcategory.REGIONAL, "CATCH_REGIONAL", "cobblecompanion.gui.dexhelp.catch_sub_regional");
            addDexHelpCatchSubcategory(rows, catchEntries, CatchSubcategory.COSMETIC, "CATCH_COSMETIC", "cobblecompanion.gui.dexhelp.catch_sub_cosmetic");
        }

        List<ClientDexCompletionHelper.EvolveEntry> evolveEntries = ClientDexCompletionHelper.getEvolveEntries();
        rows.add(DexHelpRow.category("EVOLVE", tr("cobblecompanion.gui.dexhelp.evolve_category", evolveEntries.size())));
        if (dexHelpExpanded.contains("EVOLVE")) {
            addDexHelpEvolveSubcategory(rows, evolveEntries, ClientDexCompletionHelper.Category.LEVEL, "EVOLVE_LEVEL", "cobblecompanion.gui.dexhelp.sub_level");
            addDexHelpEvolveSubcategory(rows, evolveEntries, ClientDexCompletionHelper.Category.STONE, "EVOLVE_STONE", "cobblecompanion.gui.dexhelp.sub_stone");
            addDexHelpEvolveSubcategory(rows, evolveEntries, ClientDexCompletionHelper.Category.FRIENDSHIP, "EVOLVE_FRIENDSHIP", "cobblecompanion.gui.dexhelp.sub_friendship");
            addDexHelpEvolveSubcategory(rows, evolveEntries, ClientDexCompletionHelper.Category.TRADE, "EVOLVE_TRADE", "cobblecompanion.gui.dexhelp.sub_trade");
            addDexHelpEvolveSubcategory(rows, evolveEntries, ClientDexCompletionHelper.Category.OTHER, "EVOLVE_OTHER", "cobblecompanion.gui.dexhelp.sub_other");

            List<DexHelpNeedLine> needs = buildDexHelpNeedsList(evolveEntries);
            rows.add(DexHelpRow.subcategory("EVOLVE_NEEDS", tr("cobblecompanion.gui.dexhelp.sub_needs", needs.size())));
            if (dexHelpExpanded.contains("EVOLVE_NEEDS")) {
                for (DexHelpNeedLine line : needs) rows.add(DexHelpRow.need(line));
            }
        }
        return rows;
    }

    /**
     * Unterkategorien für "Fangen" (Nutzer-Vorgabe, analog zu "Entwicklungen"): Normal (Living
     * Dex, keine Form, nicht shiny) / Shiny (Shiny Living Dex, catId 2) / Regional (Formname ist
     * eine echte Region, siehe LivingDexPlusRegistry.REGIONS - unabhängig vom Shiny-Status) /
     * Kosmetisch (jede andere Form, ebenfalls unabhängig vom Shiny-Status). Jeder Eintrag landet
     * in GENAU einer Unterkategorie, wie bei den Entwicklungs-Unterkategorien.
     */
    private enum CatchSubcategory { NORMAL, SHINY, REGIONAL, COSMETIC }

    private static CatchSubcategory catchSubcategoryOf(ClientDexCompletionHelper.CatchEntry e) {
        if (e.formName != null && !e.formName.isBlank()) {
            for (String region : com.cobblecompanion.data.LivingDexPlusRegistry.REGIONS) {
                if (region.equalsIgnoreCase(e.formName)) return CatchSubcategory.REGIONAL;
            }
            return CatchSubcategory.COSMETIC;
        }
        return e.shiny ? CatchSubcategory.SHINY : CatchSubcategory.NORMAL;
    }

    private void addDexHelpCatchSubcategory(List<DexHelpRow> rows, List<ClientDexCompletionHelper.CatchEntry> all,
                                             CatchSubcategory subcategory, String key, String labelKey) {
        List<ClientDexCompletionHelper.CatchEntry> filtered = new java.util.ArrayList<>();
        for (ClientDexCompletionHelper.CatchEntry e : all) {
            if (catchSubcategoryOf(e) == subcategory) filtered.add(e);
        }
        if (filtered.isEmpty() && subcategory != CatchSubcategory.NORMAL) return; // leere Regional/Kosmetisch/Shiny-Unterkategorie gar nicht erst anzeigen
        int total = 0;
        for (ClientDexCompletionHelper.CatchEntry e : filtered) total += (e.selfNeeded ? 1 : 0) + e.extraCopies;
        rows.add(DexHelpRow.subcategory(key, tr(labelKey, total)));
        if (dexHelpExpanded.contains(key)) {
            for (ClientDexCompletionHelper.CatchEntry e : filtered) rows.add(DexHelpRow.catchRow(e));
        }
    }

    private void addDexHelpEvolveSubcategory(List<DexHelpRow> rows, List<ClientDexCompletionHelper.EvolveEntry> all,
                                              ClientDexCompletionHelper.Category category, String key, String labelKey) {
        List<ClientDexCompletionHelper.EvolveEntry> filtered = new java.util.ArrayList<>();
        for (ClientDexCompletionHelper.EvolveEntry e : all) {
            if (e.category == category) filtered.add(e);
        }
        rows.add(DexHelpRow.subcategory(key, tr(labelKey, filtered.size())));
        if (dexHelpExpanded.contains(key)) {
            for (ClientDexCompletionHelper.EvolveEntry e : filtered) rows.add(DexHelpRow.evolveRow(e));
        }
    }

    /**
     * "Bedarfsliste": Steine/Linkkabel/getragene Items/Sonderbonbons aufsummiert über ALLE
     * Entwicklungs-Einträge (nicht nur die gerade ausgeklappten Unterkategorien) - Sonderbonbons
     * nur für Einträge, deren "von"-Spezies tatsächlich besessen wird (ownedLevel &gt;= 0, siehe
     * DexCompletionHelper) - für Zwischenstufen, die der Spieler noch nicht besitzt, lässt sich
     * kein aktuelles Level ermitteln, die tauchen hier bewusst nicht in der Bonbon-Summe auf.
     */
    private List<DexHelpNeedLine> buildDexHelpNeedsList(List<ClientDexCompletionHelper.EvolveEntry> evolveEntries) {
        Map<String, Integer> stoneCounts = new java.util.LinkedHashMap<>();
        Map<String, Integer> heldItemCounts = new java.util.LinkedHashMap<>();
        int tradeCount = 0;
        int candyTotal = 0;
        for (ClientDexCompletionHelper.EvolveEntry e : evolveEntries) {
            switch (e.category) {
                case STONE -> { if (!e.itemPath.isBlank()) stoneCounts.merge(e.itemPath, 1, Integer::sum); }
                case TRADE -> {
                    tradeCount++;
                    if (!e.itemPath.isBlank()) heldItemCounts.merge(e.itemPath, 1, Integer::sum);
                }
                case OTHER -> { if (!e.itemPath.isBlank()) heldItemCounts.merge(e.itemPath, 1, Integer::sum); }
                case LEVEL -> {
                    if (e.ownedLevel >= 0 && e.requiredLevel > e.ownedLevel) candyTotal += (e.requiredLevel - e.ownedLevel);
                }
                default -> {}
            }
        }
        List<DexHelpNeedLine> lines = new java.util.ArrayList<>();
        for (Map.Entry<String, Integer> entry : stoneCounts.entrySet()) {
            String path = entry.getKey();
            int color = DEXHELP_STONE_TYPE_COLORS.getOrDefault(path, DEXHELP_LABEL_COLOR);
            lines.add(new DexHelpNeedLine(entry.getValue() + "x " + itemDisplayName(path), path, color));
        }
        if (tradeCount > 0) {
            lines.add(new DexHelpNeedLine(tradeCount + "x " + tr("cobblecompanion.gui.dexhelp.link_cable"),
                TodoHelper.LINK_CABLE_PATH, DEXHELP_LINK_CABLE_COLOR));
        }
        for (Map.Entry<String, Integer> entry : heldItemCounts.entrySet()) {
            String path = entry.getKey();
            lines.add(new DexHelpNeedLine(entry.getValue() + "x " + itemDisplayName(path), path, DEXHELP_LABEL_COLOR));
        }
        if (candyTotal > 0) {
            lines.add(new DexHelpNeedLine(candyTotal + "x " + tr("cobblecompanion.gui.dexhelp.rare_candy"),
                "rare_candy", DEXHELP_RARE_CANDY_COLOR));
        }
        return lines;
    }

    /** Übersetzter Item-Anzeigename (Cobblemon- dann Minecraft-Namensraum), sonst grob aus dem Pfad gebildet. */
    private static String itemDisplayName(String itemPath) {
        if (itemPath == null || itemPath.isBlank()) return "";
        String key = "item.cobblemon." + itemPath;
        String translated = tr(key);
        if (!translated.equals(key)) return translated;
        key = "item.minecraft." + itemPath;
        translated = tr(key);
        if (!translated.equals(key)) return translated;
        String[] words = itemPath.split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    private int dexHelpRowHeight(DexHelpRow row) {
        return switch (row.type) {
            case DEXHELP_ROWTYPE_CATEGORY -> DEXHELP_CATEGORY_H;
            case DEXHELP_ROWTYPE_SUBCATEGORY -> DEXHELP_SUBCATEGORY_H;
            case DEXHELP_ROWTYPE_NEED -> DEXHELP_NEED_ROW_H;
            default -> DEXHELP_ROW_H;
        };
    }

    private int dexHelpContentHeight(List<DexHelpRow> rows) {
        int total = 0;
        for (DexHelpRow row : rows) total += dexHelpRowHeight(row);
        return total;
    }

    private int dexHelpMaxScroll(List<DexHelpRow> rows) {
        return Math.max(0, dexHelpContentHeight(rows) - DEXHELP_LIST_VISIBLE_HEIGHT);
    }

    private int dexHelpListRight() {
        return guiLeft + GUI_WIDTH - DEXHELP_SCROLLBAR_WIDTH - DEXHELP_SCROLLBAR_GAP - DEXHELP_SCROLLBAR_MARGIN;
    }

    /**
     * Zeichnet die rechte ToDo-Tab-Hälfte: Suchfeld oben, darunter entweder die kategorisierte
     * Gesamtliste (ausklappbar, scrollbar) oder - falls eine Suche bestätigt wurde - die
     * Vorentwicklung + direkten Entwicklungsziele der gesuchten Spezies (siehe Nutzer-Vorgabe).
     */
    private void renderDexHelpPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        if (dexHelpSearchBox == null) return;
        // Leeres Suchfeld -> immer zurück zur kategorisierten Ansicht (kein Extra-Klick nötig).
        if (dexHelpSearchBox.getValue().isBlank()) dexHelpSearchActive = false;

        dexHelpSearchBox.render(graphics, mouseX, mouseY, 0f);

        if (dexHelpSearchActive) {
            renderDexHelpSearchResult(graphics, mouseX, mouseY);
            return;
        }

        List<DexHelpRow> rows = buildDexHelpRows();
        int listX = guiLeft + DEXHELP_X;
        int listTop = guiTop + DEXHELP_LIST_Y;
        int listRight = dexHelpListRight();
        int maxScroll = dexHelpMaxScroll(rows);
        dexHelpScrollAmount = Math.max(0, Math.min(maxScroll, dexHelpScrollAmount));

        graphics.enableScissor(listX, listTop, listRight, listTop + DEXHELP_LIST_VISIBLE_HEIGHT);
        int rowY = listTop - (int) Math.round(dexHelpScrollAmount);
        for (DexHelpRow row : rows) {
            int rowH = dexHelpRowHeight(row);
            if (rowY + rowH >= listTop && rowY <= listTop + DEXHELP_LIST_VISIBLE_HEIGHT) {
                renderDexHelpRow(graphics, listX, rowY, row);
            }
            rowY += rowH;
        }
        graphics.disableScissor();

        if (maxScroll > 0) {
            int scrollbarX = listRight + DEXHELP_SCROLLBAR_GAP - DEXHELP_SCROLLBAR_EXTRA_LEFT;
            renderScrollbar(graphics, scrollbarX, DEXHELP_SCROLLBAR_WIDTH, listTop, DEXHELP_LIST_VISIBLE_HEIGHT, dexHelpScrollAmount, maxScroll);
        }
    }

    private void renderDexHelpRow(GuiGraphics graphics, int x, int y, DexHelpRow row) {
        switch (row.type) {
            case DEXHELP_ROWTYPE_CATEGORY -> {
                String prefix = dexHelpExpanded.contains(row.key) ? "▼ " : "▶ ";
                drawSmallLabel(graphics, prefix + row.label, x, y, 1.0f, DEXHELP_CATEGORY_COLOR, true, true);
            }
            case DEXHELP_ROWTYPE_SUBCATEGORY -> {
                String prefix = dexHelpExpanded.contains(row.key) ? "▼ " : "▶ ";
                drawSmallLabel(graphics, prefix + row.label, x + DEXHELP_INDENT - 5, y, 1.0f, DEXHELP_SUBCATEGORY_COLOR, true, true);
            }
            case DEXHELP_ROWTYPE_NEED -> renderDexHelpNeedRow(graphics, x + DEXHELP_INDENT, y, row.needLine);
            default -> renderDexHelpEntryRow(graphics, x + DEXHELP_INDENT - 5, y, row);
        }
    }

    /** Bedarfsliste-Zeile: optionales Item-Icon (Punkt 8) + typ-/kategoriegefärbter Text (Punkte 6/7). */
    private void renderDexHelpNeedRow(GuiGraphics graphics, int x, int y, DexHelpNeedLine line) {
        int textX = x;
        int textY = y;
        if (line.itemPath != null && !line.itemPath.isBlank()) {
            ResourceLocation itemId = TodoHelper.resolveItemId(line.itemPath);
            if (itemId != null && net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(itemId)) {
                net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(itemId);
                net.minecraft.world.item.ItemStack stack = item.getDefaultInstance();
                graphics.pose().pushPose();
                graphics.pose().translate(x, y + DEXHELP_NEED_ICON_OFFSET_Y, 0);
                graphics.pose().scale(DEXHELP_NEED_ICON_SCALE, DEXHELP_NEED_ICON_SCALE, 1f);
                graphics.renderItem(stack, 0, 0);
                graphics.pose().popPose();
                textX = x + DEXHELP_NEED_TEXT_OFFSET_X;
                textY = y + DEXHELP_NEED_TEXT_OFFSET_Y;
            }
        }
        drawSmallLabel(graphics, line.text, textX, textY, 1.0f, line.color, false, true);
    }

    /**
     * Zeichnet einen Fang-/Entwicklungs-Eintrag - EIN Slot (wie die Types-Tab-Ergebnisliste, kein
     * FROM/TO-Zeilen-Paar wie im ToDo), Name in Standardweiß statt Tier-Farbe (Nutzer-Vorgabe),
     * darunter statt Attacke/Typ die benötigte Bedingung (bzw. "aus X" + Bedingung bei Entwicklungen).
     */
    private void renderDexHelpEntryRow(GuiGraphics graphics, int x, int y, DexHelpRow row) {
        ResourceLocation speciesId = row.type == DEXHELP_ROWTYPE_CATCH ? row.catchEntry.speciesId : row.evolveEntry.toSpeciesId;
        // Hokumil/Milcery & Co: ohne die Ergebnis-Aspekte zeigt der Slot immer die Standardform,
        // egal welches Item die konkrete Zeile eigentlich repräsentiert (Bug-Report). Bei Living-
        // Dex+-Varianten-Einträgen (Fangen-Liste) zumindest den Shiny-Aspekt korrekt zeigen -
        // Formen NICHT über Aspekte gerendert (Formname != Aspekt-Schlüsselwort, z.B. "Hisui" vs.
        // "hisuian" - unzuverlässig), stattdessen als Textzeile unten (siehe lines.add unten).
        java.util.Set<String> resultAspects = row.type == DEXHELP_ROWTYPE_EVOLVE
            ? parseAspects(row.evolveEntry.resultAspects)
            : (row.type == DEXHELP_ROWTYPE_CATCH && row.catchEntry.shiny ? java.util.Set.of("shiny") : java.util.Set.of());
        renderPokemonSlotNumber(graphics, x, y, speciesId, resultAspects);

        Species species = PokemonSpecies.INSTANCE.getByIdentifier(speciesId);
        String name = species != null ? speciesDisplayName(species) : speciesId.getPath();
        int slotRight = x + PokemonSlotRenderer.SLOT_SIZE;
        drawSmallLabel(graphics, name, slotRight + DEXHELP_NAME_OFFSET_X, y + DEXHELP_NAME_OFFSET_Y, 1.0f, 0xFFFFFF, true, true);

        List<String> lines = new java.util.ArrayList<>();
        if (row.type == DEXHELP_ROWTYPE_EVOLVE) {
            ClientDexCompletionHelper.EvolveEntry e = row.evolveEntry;
            Species fromSpecies = PokemonSpecies.INSTANCE.getByIdentifier(e.fromSpeciesId);
            String fromName = fromSpecies != null ? speciesDisplayName(fromSpecies) : e.fromSpeciesId.getPath();
            lines.add(tr("cobblecompanion.gui.dexhelp.evolves_from", fromName));
            lines.add(describeDexHelpCondition(e));
        } else {
            ClientDexCompletionHelper.CatchEntry c = row.catchEntry;
            if (c.selfNeeded && c.extraCopies > 0) {
                // R3 Punkt: Wurzel selbst noch nicht gefangen UND ihre Kette braucht zusätzliche
                // Exemplare (z.B. Raupy: 1x Raupy selbst + 2x für Safcon/Smettbo = 3x insgesamt).
                lines.add(tr("cobblecompanion.gui.dexhelp.needs_catch_total", c.extraCopies + 1));
            } else if (c.selfNeeded) {
                lines.add(tr("cobblecompanion.gui.dexhelp.needs_catch"));
            } else {
                // Wurzel bereits besessen, nur weitere Stufen ihrer Kette fehlen gleichzeitig im Bestand.
                lines.add(tr("cobblecompanion.gui.dexhelp.needs_more_copies", c.extraCopies));
            }
            // Living-Dex+-Variante (Shiny/Regionalform/Kosmetische Form) - siehe
            // DexCompletionHelper.addLdpVariantCatchEntries().
            if (c.shiny && !c.formName.isEmpty()) {
                lines.add(tr("cobblecompanion.gui.dexhelp.variant_shiny_form", c.formName));
            } else if (c.shiny) {
                lines.add(tr("cobblecompanion.gui.dexhelp.variant_shiny"));
            } else if (!c.formName.isEmpty()) {
                lines.add(tr("cobblecompanion.gui.dexhelp.variant_form", c.formName));
            }
            if (!c.coveredSpeciesIds.isEmpty()) {
                StringBuilder covered = new StringBuilder();
                for (ResourceLocation coveredId : c.coveredSpeciesIds) {
                    Species coveredSpecies = PokemonSpecies.INSTANCE.getByIdentifier(coveredId);
                    String coveredName = coveredSpecies != null ? speciesDisplayName(coveredSpecies) : coveredId.getPath();
                    if (covered.length() > 0) covered.append(", ");
                    covered.append(coveredName);
                }
                lines.add(tr("cobblecompanion.gui.dexhelp.needed_for", covered.toString()));
            }
        }
        int lineY = y + DEXHELP_LABEL_OFFSET_Y;
        for (String line : lines) {
            drawSmallLabel(graphics, line, slotRight + DEXHELP_LABEL_OFFSET_X, lineY, 1.0f, DEXHELP_LABEL_COLOR, false, true);
            lineY += DEXHELP_LABEL_LINE_SPACING;
        }
    }

    private String describeDexHelpCondition(ClientDexCompletionHelper.EvolveEntry e) {
        return switch (e.category) {
            case LEVEL -> tr("cobblecompanion.gui.dexhelp.needs_level", e.requiredLevel);
            case STONE -> tr("cobblecompanion.gui.dexhelp.needs_item", itemDisplayName(e.itemPath));
            case FRIENDSHIP -> tr("cobblecompanion.gui.dexhelp.needs_friendship");
            case TRADE -> e.itemPath.isBlank() ? tr("cobblecompanion.gui.dexhelp.needs_trade")
                : tr("cobblecompanion.gui.dexhelp.needs_trade_item", itemDisplayName(e.itemPath));
            case OTHER -> e.itemPath.isBlank() ? tr("cobblecompanion.gui.dexhelp.needs_other")
                : tr("cobblecompanion.gui.dexhelp.needs_item", itemDisplayName(e.itemPath));
        };
    }

    /** Suchmodus: Vorentwicklung (falls vorhanden) + alle direkten Entwicklungsziele der gesuchten Spezies, ohne Dex-Bezug. */
    // Klickbare Fläche des Vorentwicklungs-Namens im Suchmodus (Punkt 10) - null, solange keine
    // Vorentwicklung angezeigt wird. {x, y, w, h}.
    private int[] dexHelpPreEvoLinkRect;

    /**
     * Gesamthöhe der Suchmodus-Ausgabe (Vorentwicklung + Status-Block + alle Entwicklungs-
     * Optionen) - spiegelt exakt dieselben y-Inkremente wie renderDexHelpSearchResult(), damit
     * bei vielen Verzweigungen (z.B. Evoli mit 8 Optionen) eine Scrollbar berechnet werden kann.
     */
    private int dexHelpSearchContentHeight() {
        if (ClientDexCompletionHelper.getSearchSpeciesName().isBlank()) return 0;
        int height = 0;
        boolean hasPreviousSlot = false;

        if (ClientDexCompletionHelper.getSearchPreEvolutionId() != null) {
            height += DEXHELP_INFO_ROW_H;
            hasPreviousSlot = true;
        }
        if (ClientDexCompletionHelper.getSearchSpeciesId() != null) {
            if (hasPreviousSlot) height += DEXHELP_SEARCH_ARROW_GAP;
            height += DEXHELP_INFO_ROW_H;
            hasPreviousSlot = true;
        }
        List<ClientDexCompletionHelper.EvolveEntry> evolutions = ClientDexCompletionHelper.getSearchEvolutions();
        boolean firstEvolution = true;
        for (ClientDexCompletionHelper.EvolveEntry e : evolutions) {
            if (firstEvolution) {
                if (hasPreviousSlot) height += DEXHELP_SEARCH_ARROW_GAP;
            } else {
                height += DEXHELP_SEARCH_SIBLING_GAP;
            }
            height += DEXHELP_ROW_H;
            firstEvolution = false;
        }
        return height;
    }

    private int dexHelpSearchMaxScroll() {
        return Math.max(0, dexHelpSearchContentHeight() - DEXHELP_LIST_VISIBLE_HEIGHT);
    }

    private void renderDexHelpSearchResult(GuiGraphics graphics, int mouseX, int mouseY) {
        String name = ClientDexCompletionHelper.getSearchSpeciesName();
        dexHelpPreEvoLinkRect = null;
        if (name.isBlank()) return;

        int listX = guiLeft + DEXHELP_X;
        int listTop = guiTop + DEXHELP_LIST_Y;
        int listRight = dexHelpListRight();
        int maxScroll = dexHelpSearchMaxScroll();
        dexHelpScrollAmount = Math.max(0, Math.min(maxScroll, dexHelpScrollAmount));

        graphics.enableScissor(listX, listTop, listRight, listTop + DEXHELP_LIST_VISIBLE_HEIGHT);

        int x = listX;
        int y = listTop - (int) Math.round(dexHelpScrollAmount);
        int arrowCenterX = x + PokemonSlotRenderer.SLOT_SIZE / 2;
        boolean hasPreviousSlot = false;

        ResourceLocation preId = ClientDexCompletionHelper.getSearchPreEvolutionId();
        if (preId != null) {
            // R3 Punkt 4 (nochmal angepasst): jetzt wieder 3 Zeilen - 1. der eigene Name der
            // Vorentwicklung (weiß), 2. "Entwickelt sich zu <gesuchte Art>" (grau), 3. Bedingung
            // (grau). Klickbar bleibt der SLOT (springt in die Vorentwicklungs-Suche).
            boolean slotHovered = isInRect(mouseX, mouseY, x, y, PokemonSlotRenderer.SLOT_SIZE, PokemonSlotRenderer.SLOT_SIZE);
            renderPokemonSlotNumber(graphics, x, y, preId, java.util.Set.of());
            if (slotHovered) {
                int sx1 = x - 1, sy1 = y - 1, sx2 = x + PokemonSlotRenderer.SLOT_SIZE + 1, sy2 = y + PokemonSlotRenderer.SLOT_SIZE + 1;
                graphics.fill(sx1, sy1, sx2, y, 0xFFFFFF55);
                graphics.fill(sx1, sy2 - 1, sx2, sy2, 0xFFFFFF55);
                graphics.fill(sx1, y, x, sy2, 0xFFFFFF55);
                graphics.fill(sx2 - 1, y, sx2, sy2, 0xFFFFFF55);
            }
            dexHelpPreEvoLinkRect = new int[]{x, y, PokemonSlotRenderer.SLOT_SIZE, PokemonSlotRenderer.SLOT_SIZE};

            Species preSpecies = PokemonSpecies.INSTANCE.getByIdentifier(preId);
            String preName = preSpecies != null ? speciesDisplayName(preSpecies) : preId.getPath();
            int slotRight = x + PokemonSlotRenderer.SLOT_SIZE;
            drawSmallLabel(graphics, preName, slotRight + DEXHELP_NAME_OFFSET_X, y + DEXHELP_NAME_OFFSET_Y, 1.0f, 0xFFFFFF, true, true);

            ClientDexCompletionHelper.EvolveEntry preEdge = ClientDexCompletionHelper.getSearchPreEvolutionEdge();
            Species searchedSpecies = preEdge != null ? PokemonSpecies.INSTANCE.getByIdentifier(preEdge.toSpeciesId) : null;
            String searchedName = searchedSpecies != null ? speciesDisplayName(searchedSpecies) : name;
            int lineY = y + DEXHELP_LABEL_OFFSET_Y;
            drawSmallLabel(graphics, tr("cobblecompanion.gui.dexhelp.pre_evolution", searchedName),
                slotRight + DEXHELP_LABEL_OFFSET_X, lineY, 1.0f, DEXHELP_LABEL_COLOR, false, true);
            lineY += DEXHELP_LABEL_LINE_SPACING;

            // Punkt 9: Bedingung DIESER Entwicklung (Vorentwicklung -> gesuchte Art) mit anzeigen.
            if (preEdge != null) {
                drawSmallLabel(graphics, describeDexHelpCondition(preEdge),
                    slotRight + DEXHELP_LABEL_OFFSET_X, lineY, 1.0f, DEXHELP_LABEL_COLOR, false, true);
            }
            y += DEXHELP_INFO_ROW_H;
            hasPreviousSlot = true;
        }

        // R3 Punkt: neuer Statusblock zwischen Vorentwicklung und Weiterentwicklungen - die
        // gesuchte Art selbst mit Pokédex-/Living-Dex-Status des lokal anfragenden Spielers.
        ResourceLocation searchedId = ClientDexCompletionHelper.getSearchSpeciesId();
        if (searchedId != null) {
            if (hasPreviousSlot) {
                renderDexHelpArrow(graphics, arrowCenterX, y);
                y += DEXHELP_SEARCH_ARROW_GAP;
            }
            renderPokemonSlotNumber(graphics, x, y, searchedId, java.util.Set.of());
            Species selfSpecies = PokemonSpecies.INSTANCE.getByIdentifier(searchedId);
            String selfName = selfSpecies != null ? speciesDisplayName(selfSpecies) : name;
            int slotRight = x + PokemonSlotRenderer.SLOT_SIZE;
            drawSmallLabel(graphics, selfName, slotRight + DEXHELP_NAME_OFFSET_X, y + DEXHELP_NAME_OFFSET_Y, 1.0f, 0xFFFFFF, true, true);

            boolean caughtPokedex = ClientDexCompletionHelper.isSearchHasCaughtPokedex();
            boolean ownsLiving = ClientDexCompletionHelper.isSearchOwnsLivingDex();
            int lineY = y + DEXHELP_LABEL_OFFSET_Y;
            drawSmallLabel(graphics,
                tr("cobblecompanion.gui.dexhelp.status_pokedex", tr(caughtPokedex ? "cobblecompanion.gui.confirm.yes" : "cobblecompanion.gui.confirm.no")),
                slotRight + DEXHELP_LABEL_OFFSET_X, lineY, 1.0f, caughtPokedex ? DEXHELP_STATUS_YES_COLOR : DEXHELP_STATUS_NO_COLOR, false, true);
            lineY += DEXHELP_LABEL_LINE_SPACING;
            drawSmallLabel(graphics,
                tr("cobblecompanion.gui.dexhelp.status_living", tr(ownsLiving ? "cobblecompanion.gui.confirm.yes" : "cobblecompanion.gui.confirm.no")),
                slotRight + DEXHELP_LABEL_OFFSET_X, lineY, 1.0f, ownsLiving ? DEXHELP_STATUS_YES_COLOR : DEXHELP_STATUS_NO_COLOR, false, true);
            y += DEXHELP_INFO_ROW_H;
            hasPreviousSlot = true;
        }

        List<ClientDexCompletionHelper.EvolveEntry> evolutions = ClientDexCompletionHelper.getSearchEvolutions();
        if (evolutions.isEmpty() && preId == null) {
            drawSmallLabel(graphics, tr("cobblecompanion.gui.dexhelp.no_evolutions"), x, y, 1.0f, 0x808080, false, true);
            graphics.disableScissor();
            return;
        }
        // Alle Einträge in "evolutions" stammen aus DERSELBEN Quelle (der gesuchten Art selbst) -
        // der Pfeil kommt deshalb nur EINMAL, vor der ersten Option; alle weiteren Optionen werden
        // direkt darunter mit einem kleinen Abstand gestapelt (siehe DEXHELP_SEARCH_SIBLING_GAP).
        boolean firstEvolution = true;
        for (ClientDexCompletionHelper.EvolveEntry e : evolutions) {
            if (firstEvolution) {
                if (hasPreviousSlot) {
                    renderDexHelpArrow(graphics, arrowCenterX, y);
                    y += DEXHELP_SEARCH_ARROW_GAP;
                }
            } else {
                y += DEXHELP_SEARCH_SIBLING_GAP;
            }
            renderDexHelpEntryRow(graphics, x, y, DexHelpRow.evolveRow(e));
            y += DEXHELP_ROW_H;
            hasPreviousSlot = true;
            firstEvolution = false;
        }

        graphics.disableScissor();
        if (maxScroll > 0) {
            int scrollbarX = listRight + DEXHELP_SCROLLBAR_GAP - DEXHELP_SCROLLBAR_EXTRA_LEFT;
            renderScrollbar(graphics, scrollbarX, DEXHELP_SCROLLBAR_WIDTH, listTop, DEXHELP_LIST_VISIBLE_HEIGHT, dexHelpScrollAmount, maxScroll);
        }
    }

    /**
     * Zeigt einen kleinen, horizontal auf slotCenterX zentrierten Pfeil (Suchmodus, zwischen zwei
     * gestapelten Slots) - vertikal um DEXHELP_ARROW_EXTRA_LENGTH gestreckt (eigene Y-Skalierung,
     * Breite bleibt unverändert), damit die Länge pixelgenau justierbar ist.
     */
    private void renderDexHelpArrow(GuiGraphics graphics, int slotCenterX, int y) {
        String arrow = "↓";
        float baseGlyphHeight = this.font.lineHeight;
        float scaleY = 1.0f + (DEXHELP_ARROW_EXTRA_LENGTH / baseGlyphHeight);
        int w = smallLabelWidth(arrow, 1.0f, false, false);

        graphics.pose().pushPose();
        graphics.pose().translate(slotCenterX - w / 2f, y + DEXHELP_ARROW_OFFSET_Y, 0);
        graphics.pose().scale(1.0f, scaleY, 1f);
        graphics.drawString(this.font, Component.literal(arrow), 0, 0, 0xAAAAAA, true);
        graphics.pose().popPose();
    }

    /** Klicks auf Kategorie-/Unterkategorie-Zeilen (Ausklappen), die Scrollbar, und (Suchmodus) den Vorentwicklungs-Link. */
    private boolean handleDexHelpClicks(double mouseX, double mouseY) {
        if (dexHelpSearchActive) {
            int searchListTop = guiTop + DEXHELP_LIST_Y;
            int searchListRight = dexHelpListRight();
            int searchMaxScroll = dexHelpSearchMaxScroll();
            if (searchMaxScroll > 0) {
                int scrollbarX = searchListRight + DEXHELP_SCROLLBAR_GAP - DEXHELP_SCROLLBAR_EXTRA_LEFT;
                if (isMouseOverScrollbar(mouseX, mouseY, scrollbarX, DEXHELP_SCROLLBAR_WIDTH, searchListTop, DEXHELP_LIST_VISIBLE_HEIGHT)) {
                    dexHelpScrollbarDragging = true;
                    dexHelpScrollAmount = scrollAmountFromMouseY(mouseY, searchListTop, DEXHELP_LIST_VISIBLE_HEIGHT, searchMaxScroll);
                    return true;
                }
            }
            // Punkt 10: Klick auf den Vorentwicklungs-Namen -> springt direkt in dessen Suche.
            if (dexHelpPreEvoLinkRect != null && isInRect(mouseX, mouseY,
                    dexHelpPreEvoLinkRect[0], dexHelpPreEvoLinkRect[1], dexHelpPreEvoLinkRect[2], dexHelpPreEvoLinkRect[3])) {
                ResourceLocation preId = ClientDexCompletionHelper.getSearchPreEvolutionId();
                Species preSpecies = preId != null ? PokemonSpecies.INSTANCE.getByIdentifier(preId) : null;
                if (preSpecies != null) {
                    String preName = speciesDisplayName(preSpecies);
                    dexHelpSearchBox.setValue(preName);
                    dexHelpSearchBox.setCursorPosition(preName.length());
                    sendDexHelpSearch(preName);
                }
                return true;
            }
            return false;
        }
        List<DexHelpRow> rows = buildDexHelpRows();
        int listX = guiLeft + DEXHELP_X;
        int listTop = guiTop + DEXHELP_LIST_Y;
        int listRight = dexHelpListRight();
        int maxScroll = dexHelpMaxScroll(rows);

        if (maxScroll > 0) {
            int scrollbarX = listRight + DEXHELP_SCROLLBAR_GAP - DEXHELP_SCROLLBAR_EXTRA_LEFT;
            if (isMouseOverScrollbar(mouseX, mouseY, scrollbarX, DEXHELP_SCROLLBAR_WIDTH, listTop, DEXHELP_LIST_VISIBLE_HEIGHT)) {
                dexHelpScrollbarDragging = true;
                dexHelpScrollAmount = scrollAmountFromMouseY(mouseY, listTop, DEXHELP_LIST_VISIBLE_HEIGHT, maxScroll);
                return true;
            }
        }

        int rowY = listTop - (int) Math.round(dexHelpScrollAmount);
        for (DexHelpRow row : rows) {
            int rowH = dexHelpRowHeight(row);
            if ((row.type == DEXHELP_ROWTYPE_CATEGORY || row.type == DEXHELP_ROWTYPE_SUBCATEGORY)
                    && rowY >= listTop && rowY <= listTop + DEXHELP_LIST_VISIBLE_HEIGHT
                    && isInRect(mouseX, mouseY, listX, rowY, listRight - listX, rowH)) {
                if (!dexHelpExpanded.add(row.key)) dexHelpExpanded.remove(row.key);
                return true;
            }
            rowY += rowH;
        }
        return false;
    }

    /** Pokemon-Namen (übersetzt), die mit der Eingabe im Dex-Hilfe-Suchfeld beginnen. */
    private List<String> dexHelpSearchSuggestions() {
        if (!dexHelpSearchBox.isFocused()) return java.util.List.of();
        String q = dexHelpSearchBox.getValue().trim().toLowerCase();
        if (q.isEmpty()) return java.util.List.of();
        List<String> result = new java.util.ArrayList<>();
        for (Species s : PokemonSpecies.INSTANCE.getSpecies()) {
            String display = speciesDisplayName(s);
            if (display.toLowerCase().startsWith(q) && !display.equalsIgnoreCase(q)) result.add(display);
            if (result.size() >= SEARCH_SUGGEST_MAX) break;
        }
        return result;
    }

    private void sendDexHelpSearch(String query) {
        if (query == null || query.isBlank()) {
            dexHelpSearchActive = false;
            return;
        }
        dexHelpSearchActive = true;
        dexHelpScrollAmount = 0; // neue Suche -> nicht mit dem Scroll-Stand der vorherigen starten
        sendToServer(new com.cobblecompanion.network.DexCompletionSearchRequestPacket(resolveSearchQuery(query)));
    }

    private void renderTypesTab(GuiGraphics graphics, int mouseX, int mouseY) {
        // Der Pokedex-Hintergrund (renderPokedexBackdrop()) wird jetzt ganz am Anfang von
        // render() gezeichnet - VOR unserem eigenen Rahmen/Hintergrund - damit er wirklich die
        // unterste Ebene ist. Hier also nichts mehr davon.
        typeSearchBox.render(graphics, mouseX, mouseY, 0f);

        for (int i = 0; i < TYPE_ORDER.length; i++) {
            int col = i % TYPE_GRID_COLUMNS;
            int row = i / TYPE_GRID_COLUMNS;
            int cellX = guiLeft + TYPE_GRID_X + col * TYPE_GRID_COL_WIDTH;
            int cellY = guiTop + TYPE_GRID_Y + row * TYPE_GRID_ROW_H;

            if (mouseX >= cellX && mouseX < cellX + TYPE_GRID_COL_WIDTH &&
                mouseY >= cellY && mouseY < cellY + TYPE_GRID_ROW_H) {
                graphics.fill(cellX - 1, cellY - 1,
                    cellX + TYPE_GRID_COL_WIDTH - 1, cellY + TYPE_GRID_ROW_H - 1, 0x60FFFFFF);
            }

            graphics.blit(TYPE_ICONS_SHEET,
                cellX, cellY,
                TYPE_GRID_ICON_SIZE, TYPE_GRID_ICON_SIZE,
                (float) (i * TYPE_ICON_SRC_SIZE), 0f,
                TYPE_ICON_SRC_SIZE, TYPE_ICON_SRC_SIZE,
                TYPE_ICON_SHEET_W, TYPE_ICON_SHEET_H);

            drawScaledBoldText(graphics, tr("cobblemon.type." + TYPE_ORDER[i]),
                cellX + TYPE_GRID_TEXT_OFFSET_X, cellY + TYPE_GRID_TEXT_OFFSET_Y, TYPE_GRID_TEXT_SCALE, 0xFFFFFF);
        }

        boolean queryTooltip = renderTypeResults(graphics, mouseX, mouseY);

        if (isMouseOverSearchBox(typeSearchBox, mouseX, mouseY)) {
            renderCobblemonTooltip(graphics, Component.translatable(TYPE_SEARCH_TOOLTIP_KEY), mouseX, mouseY, -14);
        } else if (queryTooltip) {
            renderCobblemonTooltip(graphics, Component.translatable("cobblecompanion.gui.types.to_pokedex"), mouseX, mouseY, -14);
        }
    }

    /** Ein Icon aus dem Typ-Sprite-Sheet an beliebiger Bildschirmposition, unabhängig vom Grid links. */
    private void renderTypeIcon(GuiGraphics graphics, int x, int y, int size, String typeName) {
        int index = -1;
        for (int i = 0; i < TYPE_ORDER.length; i++) {
            if (TYPE_ORDER[i].equalsIgnoreCase(typeName)) { index = i; break; }
        }
        if (index < 0) return;
        graphics.blit(TYPE_ICONS_SHEET, x, y, size, size,
            (float) (index * TYPE_ICON_SRC_SIZE), 0f, TYPE_ICON_SRC_SIZE, TYPE_ICON_SRC_SIZE, TYPE_ICON_SHEET_W, TYPE_ICON_SHEET_H);
    }

    /** Breite des Ergebnisbereichs rechts vom Typ-Grid, ohne den reservierten Scrollbar-Bereich. */
    private int typeResultAreaWidth() {
        return (guiLeft + GUI_WIDTH - TYPE_SCROLLBAR_MARGIN - TYPE_SCROLLBAR_GAP) - (guiLeft + TYPE_RESULT_X);
    }

    /**
     * Zeichnet (graphics != null) oder misst nur (graphics == null) eine Liste von
     * Icon+Name-"Chips" (z.B. die effektiven Typen), die bei Bedarf in mehrere Zeilen umbricht,
     * damit sie nie über den rechten Rand hinausläuft. Gibt die Anzahl benötigter Zeilen zurück -
     * dieselbe Funktion wird für Layout-Berechnung (typeResultListTop()) UND fürs eigentliche
     * Zeichnen genutzt, damit beide garantiert denselben Umbruch berechnen.
     */
    private int renderOrMeasureChipList(GuiGraphics graphics, int startX, int startY, int maxWidth, List<ClientTypeHelper.StrongType> types) {
        int cursorX = startX;
        int lines = 1;
        for (int i = 0; i < types.size(); i++) {
            ClientTypeHelper.StrongType st = types.get(i);
            String label = tr("cobblemon.type." + st.typeName) + (st.multiplier >= 4 ? " (4x)" : "")
                + (i < types.size() - 1 ? ", " : "");
            int itemWidth = TYPE_INLINE_ICON_SIZE + TYPE_INLINE_ICON_GAP + smallLabelWidth(label, TYPE_EFFECTIVE_SCALE, TYPE_EFFECTIVE_BOLD, TYPE_EFFECTIVE_UNIFORM_FONT);
            if (cursorX + itemWidth > startX + maxWidth && cursorX > startX) {
                lines++;
                cursorX = startX;
            }
            if (graphics != null) {
                int lineY = startY + (lines - 1) * TYPE_EFFECTIVE_LINE_HEIGHT;
                renderTypeIcon(graphics, cursorX, lineY, TYPE_INLINE_ICON_SIZE, st.typeName);
                drawSmallLabel(graphics, label, cursorX + TYPE_INLINE_ICON_SIZE + TYPE_INLINE_ICON_GAP, lineY,
                    TYPE_EFFECTIVE_SCALE, TYPE_EFFECTIVE_COLOR, TYPE_EFFECTIVE_BOLD, TYPE_EFFECTIVE_UNIFORM_FONT);
            }
            cursorX += itemWidth;
        }
        return lines;
    }

    /**
     * Y-Position, an der der GESAMTE scrollbare Bereich beginnt (Panel-Loch, Titel, "Effective
     * types" und Ergebnisliste liegen jetzt alle zusammen im Scroll-Bereich, statt dass die
     * ersten drei fest darüber stehen - siehe renderTypeScrollArea()). Fix, hängt nicht mehr vom
     * Datenstand ab.
     */
    private int typeResultListTop() {
        return guiTop + TYPE_RESULT_Y;
    }

    private int typeResultVisibleHeight() {
        return (guiTop + GUI_HEIGHT - TYPE_RESULT_BOTTOM_MARGIN) - typeResultListTop();
    }

    /**
     * Höhe von Titelzeile + "Effective types"-Zeile(n) (OHNE das Pokedex-Loch - das ist ein
     * eigener, separater Block davor, siehe typeQueryHoleRect()). Muss exakt dieselbe Präfix-
     * Breite/Umbruch-Logik nutzen wie renderTypeScrollArea(), sonst driftet die berechnete Höhe
     * von der tatsächlich gezeichneten auseinander (z.B. beim Scrollbar-Maximum).
     */
    private int typeHeaderTextHeight() {
        int height = TYPE_TITLE_LINE_HEIGHT;
        List<ClientTypeHelper.StrongType> strongTypes = ClientTypeHelper.getStrongTypes();
        int effectiveLines;
        if (strongTypes.isEmpty()) {
            effectiveLines = 1;
        } else {
            String prefix = tr("cobblecompanion.gui.types.effective_prefix");
            int prefixWidth = smallLabelWidth(prefix, TYPE_EFFECTIVE_SCALE, TYPE_EFFECTIVE_BOLD, TYPE_EFFECTIVE_UNIFORM_FONT);
            effectiveLines = renderOrMeasureChipList(null, 0, 0, typeResultAreaWidth() - prefixWidth, strongTypes);
        }
        height += effectiveLines * TYPE_EFFECTIVE_LINE_HEIGHT;
        return height;
    }

    /**
     * Aktuelles Pokedex-Loch-Rechteck (Größe/Position EXAKT wie Cobblemons echtes Info-Widget,
     * siehe Kommentar bei TYPE_QUERY_PANEL_W/H oben), inkl. aktueller Scroll-Position - null wenn
     * gerade kein Pokemon gesucht ist. Ist der ERSTE Block im Scroll-Inhalt (vor Titel/Effective-
     * Types), scrollt also mit weg wie der Rest. Gemeinsam genutzt von der Backdrop-Begrenzung
     * (renderTypeQueryBackdrop, ganz unten in render()), der Hover-Erkennung (renderTypeScrollArea)
     * und der Klick-Erkennung (handleTypeResultClicks), damit alle drei garantiert dieselbe Fläche
     * meinen.
     */
    private int[] typeQueryHoleRect() {
        if (ClientTypeHelper.getQuerySpeciesId() == null) return null;
        int resultX = guiLeft + TYPE_RESULT_X;
        int listTop = typeResultListTop();
        int y = listTop - (int) Math.round(typeResultScrollAmount);
        return new int[]{resultX, y, TYPE_QUERY_PANEL_W, TYPE_QUERY_PANEL_H};
    }

    /** Höhe des "Kopf"-Teils: Pokedex-Loch (nur wenn ein Pokemon gesucht wurde) + Titel + Effective-Types + Abstand zur Ergebnisliste. */
    private int typeHeaderContentHeight() {
        int height = 0;
        if (ClientTypeHelper.getQuerySpeciesId() != null) {
            height += TYPE_QUERY_PANEL_H + TYPE_QUERY_SLOT_GAP;
        }
        return height + typeHeaderTextHeight() + TYPE_QUERY_SLOT_GAP;
    }

    /** Gesamthöhe des scrollbaren Inhalts: Kopf-Teil + alle Ergebniszeilen inkl. "Your team:"/"Your PC:"-Zwischenüberschriften. */
    private int typeResultContentHeight() {
        int height = typeHeaderContentHeight();
        Boolean lastWasParty = null;
        for (ClientTypeHelper.TypeResultEntry r : ClientTypeHelper.getResults()) {
            if (lastWasParty == null || r.party != lastWasParty) {
                height += TYPE_SECTION_LABEL_HEIGHT;
                lastWasParty = r.party;
            }
            height += TYPE_RESULT_ROW_H;
        }
        return height;
    }

    private int typeMaxScroll() {
        return Math.max(0, typeResultContentHeight() - typeResultVisibleHeight());
    }

    /**
     * Rendert den echten Cobblemon-Pokedex KOMPLETT UNBESCHNITTEN (keine Scissor-Begrenzung
     * mehr) als unterste Ebene - Test-Ansatz: die vorherige harte Scissor-Begrenzung auf das
     * Panel-Loch-Rechteck hat vermutlich verhindert, dass wichtige Elemente des Widgets
     * überhaupt gerendert wurden. Die Sichtbarkeit soll jetzt AUSSCHLIESSLICH dadurch entstehen,
     * dass unser eigenes GUI an der Panel-Loch-Stelle (siehe typeQueryHoleRect()) bewusst nichts
     * Eigenes zeichnet.
     */
    private void renderTypeQueryBackdrop(GuiGraphics graphics, int mouseX, int mouseY, ResourceLocation speciesId) {
        int[] rect = typeQueryHoleRect();
        if (rect == null) {
            restoreLocalPokedexIfNeeded(); // Suche geleert/kein Pokemon gesucht -> ggf. aktive Fälschung sofort zurücksetzen
            return;
        }
        int slotX = rect[0], slotY = rect[1], slotW = rect[2], slotH = rect[3];

        int listTop = typeResultListTop();
        int visibleHeight = typeResultVisibleHeight();
        int clipTop = Math.max(slotY, listTop);
        int clipBottom = Math.min(slotY + slotH, listTop + visibleHeight);
        if (clipBottom <= clipTop) return; // Loch aktuell komplett weggescrollt - nichts zu zeichnen

        // Unser eigener Rahmen (pokedexScreen/pokedexBase) ist EIN einziger undurchsichtiger
        // Blit über die komplette GUI-Fläche - der kennt kein "Loch". Deshalb wird der Backdrop
        // hier NICHT ganz am Anfang von render() gezeichnet (das würde nur wieder vom Rahmen
        // übermalt), sondern genau an dieser Stelle im normalen Zeichen-Ablauf des Types-Tabs -
        // also NACH dem Rahmen, aber VOR unserem eigenen Text (Titel/Effective-Types) - hart per
        // Scissor auf das Loch-Rechteck begrenzt, damit er dort auf den Rahmen "übergemalt" wird.
        graphics.enableScissor(slotX, clipTop, slotX + slotW, clipBottom);
        renderPokedexBackdrop(graphics, mouseX, mouseY, speciesId);
        graphics.disableScissor();
    }

    /**
     * Zeichnet den ECHTEN Cobblemon-Pokedex komplett unbeschnitten. Der Pokedex wird dabei
     * client-seitig auf die gesuchte Spezies
     * gesetzt (gleiche Such-Mechanik wie jumpToPokedexEntry(), aber ohne Tab-Wechsel), damit dort
     * das passende Pokemon zu sehen ist.
     */
    private void renderPokedexBackdrop(GuiGraphics graphics, int mouseX, int mouseY, ResourceLocation speciesId) {
        if (!pokedexInitialized) initPokedex();
        if (cobblemonPokedexInstance == null) return;
        try {
            ensureTypesPokedexOverride(speciesId);
            Species species = PokemonSpecies.INSTANCE.getByIdentifier(speciesId);
            if (species != null) {
                Class<?> guiClass = cobblemonPokedexInstance.getClass();
                java.lang.reflect.Field searchField = guiClass.getDeclaredField("searchWidget");
                searchField.setAccessible(true);
                Object searchWidget = searchField.get(cobblemonPokedexInstance);
                searchWidget.getClass().getMethod("setValue", String.class)
                    .invoke(searchWidget, species.getTranslatedName().getString());
                guiClass.getMethod("updateFilters", boolean.class).invoke(cobblemonPokedexInstance, false);
            }
            // KEINE Skalierung mehr (Testresultat: PoseStack-Scale macht Text/Icons unscharf -
            // "Pixelbrei"): der Pokedex wird in seiner echten, nativen Größe gerendert.
            //
            // Tiefentest hier bewusst AUS: unser eigenes GUI liegt (seit dem Z-Offset-Fix in
            // render(), siehe TYPES_TAB_FOREGROUND_Z) auf Ebene 2500 - vom VORHERIGEN Frame
            // steht dieser Tiefenwert noch im Depth-Buffer, wenn dieser Frame beginnt. Ohne
            // disableDepthTest() testet Cobblemons Modell-Rendering (Ebene 1000) gegen diesen
            // alten, "näheren" Wert und verwirft seine eigenen Draw-Calls komplett - das GUI
            // "verdeckt" den Pokedex dann genauso wie ein hartes Scissor, obwohl gar keins mehr
            // aktiv ist. Erzwingt das Rendern unabhängig vom (veralteten) Tiefenpuffer-Inhalt.
            RenderSystem.disableDepthTest();
            cobblemonPokedexInstance.render(graphics, mouseX, mouseY, 0);
            RenderSystem.enableDepthTest();
        } catch (Exception e) {
            CobbleCompanion.LOGGER.error("[CC] Error rendering Pokedex backdrop for Types query panel", e);
        }
    }

    // Welche Spezies der aktuell aktive Fälschungs-Swap (siehe ensureTypesPokedexOverride) betrifft
    // - null, solange keine Fälschung aktiv ist.
    private ResourceLocation typesPokedexOverrideSpeciesId = null;

    /**
     * BUGFIX (Live-Test): Sucht man im Types-Tab nach einem Pokemon, das der eigene ECHTE Pokédex
     * noch nicht kennt, zeigt Cobblemons eigene Pokedex-Suche (searchWidget.setValue()) trotzdem
     * das zuletzt gesuchte bzw. irgendein Pokemon statt des gewünschten - die native Suche lässt
     * grundsätzlich keine Treffer für noch nicht gesehene Arten zu. Fix (Variante 1 aus der
     * Nutzer-Rückfrage): der Hintergrund-Pokedex "kennt" die gesuchte Art für die Dauer der
     * Anzeige künstlich als gefangen - NICHT der komplette Pokédex auf einmal (unnötig teuer bei
     * ~1500 Arten), sondern nur die jeweils gerade gesuchte, in einer KOPIE der echten Daten
     * (ClientPokedexManager ist eine finale Kotlin-Klasse, kein Überschreiben von Methoden
     * möglich - deshalb echte Daten kopieren + einen synthetischen CAUGHT-Formeneintrag
     * hinzufügen, statt die Klasse zu subclassen). Der echte Fortschritt des Spielers bleibt
     * dadurch unangetastet; restoreLocalPokedexIfNeeded() (bereits als Sicherheitsnetz in
     * renderPokedexTab()/renderLivingDexTab() vorhanden) stellt beim nächsten Besuch des echten
     * Pokédex/Living-Dex-Tabs automatisch die echten Daten wieder her. Nutzt bewusst dieselben
     * professorPokedexSwapActive/savedLocalPokedexManager-Felder wie der Professor-Tab-Swap - beide
     * Ansichten können nie gleichzeitig aktiv sein (nur ein Tab ist je sichtbar), ein einziger
     * "gesichert echter Stand"-Platz reicht also für beide.
     */
    private void ensureTypesPokedexOverride(ResourceLocation speciesId) {
        if (speciesId == null) return;
        try {
            com.cobblemon.mod.common.api.storage.player.client.ClientPokedexManager active =
                com.cobblemon.mod.common.client.CobblemonClient.INSTANCE.getClientPokedexData();
            com.cobblemon.mod.common.api.storage.player.client.ClientPokedexManager trueManager =
                professorPokedexSwapActive ? savedLocalPokedexManager : active;
            if (trueManager == null) return;

            if (speciesId.equals(typesPokedexOverrideSpeciesId)) return; // Fälschung für genau diese Art schon aktiv

            if (trueManager.getKnowledgeForSpecies(speciesId) == com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress.CAUGHT) {
                // Wirklich schon gefangen - keine Fälschung nötig; eine ggf. vorherige (andere) Art
                // zurücksetzen, damit deren künstlicher Eintrag nicht unnötig aktiv bleibt.
                restoreLocalPokedexIfNeeded();
                typesPokedexOverrideSpeciesId = null;
                return;
            }

            java.util.Map<ResourceLocation, com.cobblemon.mod.common.api.pokedex.SpeciesDexRecord> copy =
                new java.util.HashMap<>(trueManager.getSpeciesRecords());
            com.cobblemon.mod.common.api.storage.player.client.ClientPokedexManager fakeManager =
                new com.cobblemon.mod.common.api.storage.player.client.ClientPokedexManager(copy);
            com.cobblemon.mod.common.api.pokedex.SpeciesDexRecord record = fakeManager.getOrCreateSpeciesRecord(speciesId);
            com.cobblemon.mod.common.api.pokedex.FormDexRecord form = record.getOrCreateFormRecord("normal");
            form.setKnowledgeProgress(com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress.CAUGHT);

            if (!professorPokedexSwapActive) {
                savedLocalPokedexManager = trueManager;
                professorPokedexSwapActive = true;
            }
            com.cobblemon.mod.common.client.CobblemonClient.INSTANCE.setClientPokedexData(fakeManager);
            typesPokedexOverrideSpeciesId = speciesId;
        } catch (Exception e) {
            CobbleCompanion.LOGGER.error("[CC] Failed to fake Pokedex knowledge for Types-tab search", e);
        }
    }

    /**
     * Zeichnet den Ergebnisbereich rechts vom Typ-Grid. Gibt zurück, ob der Pokedex-Link-Tooltip
     * gezeichnet werden soll (Hover über dem gesuchten-Pokemon-Loch).
     */
    private boolean renderTypeResults(GuiGraphics graphics, int mouseX, int mouseY) {
        int resultX = guiLeft + TYPE_RESULT_X;
        int y = guiTop + TYPE_RESULT_Y;

        String errorQuery = ClientTypeHelper.getErrorQuery();
        if (errorQuery != null) {
            drawSmallLabel(graphics, tr("cobblecompanion.gui.types.unknown", errorQuery), resultX, y,
                TYPE_TITLE_SCALE, 0xFF5555, TYPE_TITLE_BOLD, TYPE_TITLE_UNIFORM_FONT);
            drawSmallLabel(graphics, tr("cobblecompanion.gui.types.unknown_hint"), resultX, y + TYPE_TITLE_LINE_HEIGHT,
                TYPE_EFFECTIVE_SCALE, 0xAAAAAA, false, TYPE_EFFECTIVE_UNIFORM_FONT);
            return false;
        }

        List<String> defenderTypes = ClientTypeHelper.getDefenderTypes();
        ResourceLocation querySpecies = ClientTypeHelper.getQuerySpeciesId();
        if (defenderTypes.isEmpty() && querySpecies == null) return false;

        return renderTypeScrollArea(graphics, resultX, mouseX, mouseY, querySpecies, defenderTypes);
    }

    /**
     * Zeichnet den KOMPLETTEN scrollbaren Bereich rechts vom Typ-Grid: Panel-Loch (gesuchtes
     * Pokemon), Titelzeile, "Effective types"-Zeile(n) UND die Team/PC-Ergebnisliste - alles
     * zusammen in einem Scroll-Bereich (Scissor + Scrollbar). Bewusster Kompromiss: das
     * Panel-Loch/der Titel scrollen mit weg, dafür bleibt für die Ergebniszeilen selbst immer
     * genug Platz, auch wenn viele Treffer da sind.
     */
    private boolean renderTypeScrollArea(GuiGraphics graphics, int resultX, int mouseX, int mouseY,
                                          ResourceLocation querySpecies, List<String> defenderTypes) {
        int areaWidth = typeResultAreaWidth();
        int listTop = typeResultListTop();
        int visibleHeight = typeResultVisibleHeight();
        int maxScroll = typeMaxScroll();
        typeResultScrollAmount = Math.max(0, Math.min(maxScroll, typeResultScrollAmount));

        int y = listTop - (int) Math.round(typeResultScrollAmount);
        boolean queryHovered = false;

        if (querySpecies != null) {
            queryHovered = mouseX >= resultX && mouseX < resultX + TYPE_QUERY_PANEL_W
                && mouseY >= y && mouseY < y + TYPE_QUERY_PANEL_H
                && mouseY >= listTop && mouseY < listTop + visibleHeight;
            // Eigener, in sich geschlossener Scissor-Block (siehe renderTypeQueryBackdrop()) -
            // MUSS vor dem folgenden enableScissor() für den restlichen Scroll-Bereich passieren,
            // sonst würde dessen enableScissor/disableScissor-Paar durch das verschachtelte
            // disableScissor() hier drin (GuiGraphics kennt keinen Scissor-Stack) komplett
            // aufgehoben statt korrekt wiederhergestellt.
            renderTypeQueryBackdrop(graphics, mouseX, mouseY, querySpecies);
            y += TYPE_QUERY_PANEL_H + TYPE_QUERY_SLOT_GAP;
        }

        int scrollbarX = guiLeft + GUI_WIDTH - TYPE_SCROLLBAR_MARGIN;
        graphics.enableScissor(resultX, listTop, scrollbarX - TYPE_SCROLLBAR_GAP, listTop + visibleHeight);

        // Titelzeile client-seitig aus D|/Q| gebaut (übersetzter Spezies- bzw. Typname), damit
        // der Server keinen fertig formulierten Text mehr schicken muss.
        String subject;
        Species species = querySpecies != null ? PokemonSpecies.INSTANCE.getByIdentifier(querySpecies) : null;
        if (species != null) {
            subject = speciesDisplayName(species);
        } else if (!defenderTypes.isEmpty()) {
            subject = tr("cobblemon.type." + defenderTypes.get(0));
        } else {
            subject = "";
        }
        String title = tr("cobblecompanion.gui.types.title", subject);

        int titleX = resultX;
        // Typ-Icon(e) vor dem Titel nur bei reiner Typ-Suche (kein Panel vorhanden) - bei einer
        // Pokemon-Suche zeigt das Panel oben die Typ-Leiste bereits, doppelt gemoppelt.
        if (querySpecies == null) {
            for (String type : defenderTypes) {
                renderTypeIcon(graphics, titleX, y + TYPE_TITLE_OFFSET_Y, TYPE_INLINE_ICON_SIZE, type);
                titleX += TYPE_INLINE_ICON_SIZE + TYPE_INLINE_ICON_GAP;
            }
        }
        drawSmallLabel(graphics, title, titleX, y + TYPE_TITLE_OFFSET_Y, TYPE_TITLE_SCALE, TYPE_TITLE_COLOR, TYPE_TITLE_BOLD, TYPE_TITLE_UNIFORM_FONT);
        y += TYPE_TITLE_LINE_HEIGHT;

        // "Effective types: ..." - Präfix einmal, dann die Typ-Chips mit automatischem
        // Zeilenumbruch (renderOrMeasureChipList), damit sie nie über den rechten Rand
        // (bzw. in die reservierte Scrollbar-Spalte) hinauslaufen.
        List<ClientTypeHelper.StrongType> strongTypes = ClientTypeHelper.getStrongTypes();
        if (strongTypes.isEmpty()) {
            drawSmallLabel(graphics, tr("cobblecompanion.gui.types.none_effective"), resultX, y + TYPE_EFFECTIVE_OFFSET_Y,
                TYPE_EFFECTIVE_SCALE, TYPE_EFFECTIVE_COLOR, TYPE_EFFECTIVE_BOLD, TYPE_EFFECTIVE_UNIFORM_FONT);
            y += TYPE_EFFECTIVE_LINE_HEIGHT;
        } else {
            String prefix = tr("cobblecompanion.gui.types.effective_prefix");
            int prefixWidth = smallLabelWidth(prefix, TYPE_EFFECTIVE_SCALE, TYPE_EFFECTIVE_BOLD, TYPE_EFFECTIVE_UNIFORM_FONT);
            drawSmallLabel(graphics, prefix, resultX, y + TYPE_EFFECTIVE_OFFSET_Y, TYPE_EFFECTIVE_SCALE, TYPE_EFFECTIVE_COLOR, TYPE_EFFECTIVE_BOLD, TYPE_EFFECTIVE_UNIFORM_FONT);
            int chipStartX = resultX + prefixWidth;
            int chipMaxWidth = areaWidth - prefixWidth;
            int lines = renderOrMeasureChipList(graphics, chipStartX, y + TYPE_EFFECTIVE_OFFSET_Y, chipMaxWidth, strongTypes);
            y += lines * TYPE_EFFECTIVE_LINE_HEIGHT;
        }
        y += TYPE_QUERY_SLOT_GAP;

        // Ergebnisliste (Team/PC) direkt im Anschluss, gleicher Scroll-Cursor/Scissor.
        List<ClientTypeHelper.TypeResultEntry> results = ClientTypeHelper.getResults();
        boolean sectionShown = false;
        boolean lastWasParty = true;
        for (ClientTypeHelper.TypeResultEntry r : results) {
            if (!sectionShown || r.party != lastWasParty) {
                if (y + TYPE_SECTION_LABEL_HEIGHT >= listTop && y <= listTop + visibleHeight) {
                    drawScaledBoldText(graphics, tr(r.party ? "cobblecompanion.gui.types.your_team" : "cobblecompanion.gui.types.your_pc"),
                        resultX, y, BODY_TEXT_SCALE, 0xAAAAAA);
                }
                y += TYPE_SECTION_LABEL_HEIGHT;
                sectionShown = true;
                lastWasParty = r.party;
            }
            if (y + TYPE_RESULT_ROW_H >= listTop && y <= listTop + visibleHeight) {
                renderTypeResultRow(graphics, resultX, y, r);
            }
            y += TYPE_RESULT_ROW_H;
        }
        graphics.disableScissor();

        renderScrollbar(graphics, scrollbarX, TYPE_SCROLLBAR_WIDTH, listTop, visibleHeight, typeResultScrollAmount, maxScroll);
        return queryHovered;
    }

    private void renderTypeResultRow(GuiGraphics graphics, int x, int y, ClientTypeHelper.TypeResultEntry r) {
        renderPokemonNumberedSlot(graphics, x, y, r.speciesId, r.aspects, r.level);

        Species species = PokemonSpecies.INSTANCE.getByIdentifier(r.speciesId);
        String name = species != null ? speciesDisplayName(species) : r.speciesId.getPath();
        int slotRight = x + PokemonSlotRenderer.SLOT_SIZE;
        drawSmallLabel(graphics, name, slotRight + TYPE_RESULT_NAME_OFFSET_X, y + TYPE_RESULT_NAME_OFFSET_Y,
            TYPE_RESULT_NAME_SCALE, typeResultTierColor(r.colorCode), TYPE_RESULT_NAME_BOLD, TYPE_RESULT_NAME_UNIFORM_FONT);

        if (!r.keywords.isEmpty()) {
            // Rechte Grenze aus derselben scrollbar-bewussten Breite wie der Rest des Ergebnis-
            // bereichs (typeResultAreaWidth()) statt direkt vom GUI-Rand, sonst würde der Text
            // unter die Scrollbar laufen. TYPE_RESULT_LABEL_RIGHT_MARGIN ist nur noch ein
            // zusätzlicher kleiner Puffer obendrauf.
            int maxWidth = (guiLeft + TYPE_RESULT_X + typeResultAreaWidth() - TYPE_RESULT_LABEL_RIGHT_MARGIN) - (slotRight + TYPE_RESULT_LABEL_OFFSET_X);
            int lineY = y + TYPE_RESULT_LABEL_OFFSET_Y;
            List<String> translatedKeywords = new java.util.ArrayList<>();
            for (String kw : r.keywords) translatedKeywords.add(translateTypeKeyword(kw));
            for (String line : wrapKeywords(translatedKeywords, TYPE_RESULT_LABEL_SCALE, TYPE_RESULT_LABEL_BOLD, TYPE_RESULT_LABEL_UNIFORM_FONT, maxWidth)) {
                drawSmallLabel(graphics, line, slotRight + TYPE_RESULT_LABEL_OFFSET_X, lineY,
                    TYPE_RESULT_LABEL_SCALE, TYPE_RESULT_LABEL_COLOR, TYPE_RESULT_LABEL_BOLD, TYPE_RESULT_LABEL_UNIFORM_FONT);
                lineY += TYPE_RESULT_LABEL_LINE_SPACING;
            }
        }
    }

    /**
     * Übersetzt einen rohen Keyword-Marker vom Server ("M:<moveId>", "T2"/"T4", "TOP") in den
     * lokalisierten Anzeigetext. Der Server schickt bewusst keinen fertigen Text, damit das
     * GUI komplett über die lang-Dateien lokalisierbar bleibt.
     */
    private String translateTypeKeyword(String marker) {
        if (marker.startsWith("M:")) {
            String moveId = marker.substring(2);
            return tr("cobblecompanion.gui.types.move_prefix") + " " + tr("cobblemon.move." + moveId);
        }
        return switch (marker) {
            case "T2" -> tr("cobblecompanion.gui.types.type2x");
            case "T4" -> tr("cobblecompanion.gui.types.type4x");
            case "TOP" -> tr("cobblecompanion.gui.types.toplevel");
            default -> marker;
        };
    }

    /**
     * Bricht eine Liste von Stichworten (z.B. "Attacke:X", "Typ 2x", "Top Lvl") in mehrere
     * Zeilen um, sobald die mit " · " verbundene Zeile breiter als maxWidth würde - statt sie
     * über den rechten GUI-Rand hinauslaufen zu lassen. Bricht immer zwischen ganzen
     * Stichworten, nie mitten im Wort.
     */
    private List<String> wrapKeywords(List<String> keywords, float scale, boolean bold, boolean uniformFont, int maxWidth) {
        List<String> lines = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String keyword : keywords) {
            String candidate = current.isEmpty() ? keyword : current + " · " + keyword;
            if (!current.isEmpty() && smallLabelWidth(candidate, scale, bold, uniformFont) > maxWidth) {
                lines.add(current.toString());
                current = new StringBuilder(keyword);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        if (!current.isEmpty()) lines.add(current.toString());
        return lines;
    }

    /** Wandelt einen Minecraft-Formatierungscode (z.B. "§a") in die passende RGB-Farbe für drawScaledBoldText um. */
    private int typeResultTierColor(String colorCode) {
        return switch (colorCode) {
            case "§a" -> 0x55FF55;
            case "§e" -> 0xFFFF55;
            case "§c" -> 0xFF5555;
            default -> 0xAAAAAA;
        };
    }

    /** Prüft Klicks auf die Ergebnis-Scrollbar und den gesuchtes-Pokemon-Slot (Pokédex-Link). */
    private boolean handleTypeResultClicks(double mouseX, double mouseY) {
        int maxScroll = typeMaxScroll();
        if (maxScroll > 0) {
            int listTop = typeResultListTop();
            int visibleHeight = typeResultVisibleHeight();
            int scrollbarX = guiLeft + GUI_WIDTH - TYPE_SCROLLBAR_MARGIN;
            if (isMouseOverScrollbar(mouseX, mouseY, scrollbarX, TYPE_SCROLLBAR_WIDTH, listTop, visibleHeight)) {
                typeScrollbarDragging = true;
                typeResultScrollAmount = scrollAmountFromMouseY(mouseY, listTop, visibleHeight, maxScroll);
                return true;
            }
        }

        ResourceLocation querySpecies = ClientTypeHelper.getQuerySpeciesId();
        int[] rect = typeQueryHoleRect();
        if (querySpecies != null && rect != null) {
            int slotX = rect[0], slotY = rect[1], slotW = rect[2], slotH = rect[3];
            int listTop = typeResultListTop();
            int visibleHeight = typeResultVisibleHeight();
            if (mouseX >= slotX && mouseX < slotX + slotW
                && mouseY >= slotY && mouseY < slotY + slotH
                && mouseY >= listTop && mouseY < listTop + visibleHeight) {
                Species species = PokemonSpecies.INSTANCE.getByIdentifier(querySpecies);
                // Cobblemons eigene Pokedex-Suche filtert nach dem im Client aktiven Sprachpaket
                // (bei deutschem Client z.B. "Glumanda" statt "Charmander") - deshalb hier den
                // übersetzten Anzeigenamen schicken, nicht den internen (immer englischen) Namen.
                if (species != null) jumpToPokedexEntry(species.getTranslatedName().getString());
                return true;
            }
        }
        return false;
    }

    /** Prüft Klicks auf das Typ-Icon-Grid und löst ggf. eine TypeRequestPacket-Anfrage aus. */
    private boolean handleTypeGridClick(double mouseX, double mouseY) {
        for (int i = 0; i < TYPE_ORDER.length; i++) {
            int col = i % TYPE_GRID_COLUMNS;
            int row = i / TYPE_GRID_COLUMNS;
            int cellX = guiLeft + TYPE_GRID_X + col * TYPE_GRID_COL_WIDTH;
            int cellY = guiTop + TYPE_GRID_Y + row * TYPE_GRID_ROW_H;

            if (mouseX >= cellX && mouseX < cellX + TYPE_GRID_COL_WIDTH &&
                mouseY >= cellY && mouseY < cellY + TYPE_GRID_ROW_H) {
                String typeName = TYPE_ORDER[i];
                typeSearchBox.setValue(typeName);
                sendTypeRequest(typeName);
                return true;
            }
        }
        return false;
    }

    private void sendTypeRequest(String query) {
        if (query == null || query.isBlank()) return;
        ClientTypeHelper.setLastQuery(query);
        // Deutsche (bzw. lokalisierte) Eingabe clientseitig in den internen englischen Namen
        // übersetzen - der Server kennt die Client-Sprache nicht (siehe resolveSearchQuery).
        sendToServer(new TypeRequestPacket(resolveSearchQuery(query)));
    }

    /**
     * Übersetzt eine lokalisierte Sucheingabe (deutscher Pokémon- oder Typname) in den
     * internen englischen Namen, den der Server erwartet. Reihenfolge: exakter interner
     * Name -> übersetzter Typname -> übersetzter Spezies-Name -> unverändert durchreichen.
     */
    private String resolveSearchQuery(String input) {
        if (input == null || input.isBlank()) return input;
        String q = input.trim();

        // Typen: gegen internen Namen und übersetzten Typnamen prüfen.
        for (String type : TYPE_ORDER) {
            if (type.equalsIgnoreCase(q)) return type;
            if (tr("cobblemon.type." + type).equalsIgnoreCase(q)) return type;
        }

        // Spezies: internen Namen bevorzugen, sonst übersetzten Anzeigenamen matchen.
        for (Species s : PokemonSpecies.INSTANCE.getSpecies()) {
            if (s.getName().equalsIgnoreCase(q)) return s.getName();
        }
        for (Species s : PokemonSpecies.INSTANCE.getSpecies()) {
            try {
                if (s.getTranslatedName().getString().equalsIgnoreCase(q)) return s.getName();
            } catch (Exception ignored) {}
        }
        return input; // nichts gefunden -> Server versucht es selbst
    }

    /** Anzeigename einer Spezies im aktiven Sprachpaket (deutsch etc.), mit Fallback auf den internen Namen. */
    private static String speciesDisplayName(Species species) {
        try {
            String translated = species.getTranslatedName().getString();
            if (translated != null && !translated.isBlank()) return translated;
        } catch (Exception ignored) {}
        return species.getName();
    }

    private void renderWhoNeedsTab(GuiGraphics graphics, int mouseX, int mouseY) {
        renderModusLabel(graphics);
        whoNeedsSearchBox.render(graphics, mouseX, mouseY, 0f);

        // Umschalt-Button Pokédex-/Living-Dex-Modus: Icon-Button direkt am rechten Ende
        // der Suchleiste, wie Cobblemons eigener "Search By..."-Modus-Button neben dessen
        // Suchfeld. Species-Tab-Icon = Pokédex-Modus, Drops-Tab-Icon = Living-Dex-Modus.
        boolean livingDexMode = ClientWhoNeedsHelper.isLivingDexMode();
        int btnX = guiLeft + WHONEEDS_MODE_BTN_X;
        int btnY = guiTop + WHONEEDS_MODE_BTN_Y;
        boolean btnHovered = mouseX >= btnX && mouseX < btnX + WHONEEDS_MODE_BTN_W
            && mouseY >= btnY && mouseY < btnY + WHONEEDS_MODE_BTN_H;

        // Kein farbiger Hintergrund mehr - die Hover-Rückmeldung kommt stattdessen (wie bei
        // Cobblemons eigenem Search-By-Button/ScaledButton) aus dem Sprite-Sheet selbst:
        // obere Hälfte = normale Anzeige, untere Hälfte = Mouse-Over-Variante.
        int iconX = btnX + WHONEEDS_MODE_BTN_ICON_OFFSET_X;
        int iconY = btnY + WHONEEDS_MODE_BTN_ICON_OFFSET_Y;
        ResourceLocation modeIcon = livingDexMode ? WHONEEDS_MODE_ICON_LIVINGDEX : WHONEEDS_MODE_ICON_POKEDEX;
        graphics.blit(modeIcon,
            iconX, iconY,
            WHONEEDS_MODE_BTN_ICON_SIZE, WHONEEDS_MODE_BTN_ICON_SIZE,
            0f, btnHovered ? WHONEEDS_MODE_ICON_SRC_SIZE : 0f,
            WHONEEDS_MODE_ICON_SRC_SIZE, WHONEEDS_MODE_ICON_SRC_SIZE,
            WHONEEDS_MODE_ICON_SRC_SIZE, WHONEEDS_MODE_ICON_SRC_SIZE * 2);

        // Eigene abgebbare Duplikate (links, wo bei Cobblemon die Scroll-Liste sitzt): ein
        // pokedex_slot.png je Exemplar, Nationaldex-Nummer oben links wie bei Cobblemon,
        // Level oben rechts an der Stelle, an der Cobblemon sein Seen/Caught-Icon zeigt.
        // Per Scissor+Scrollbar begrenzt (lief vorher unbegrenzt nach unten aus dem GUI heraus).
        List<ClientWhoNeedsHelper.DuplicateItem> duplicates = ClientWhoNeedsHelper.getDuplicates();
        int gridVisibleHeight = whoNeedsGridVisibleHeight();
        int gridMaxScroll = whoNeedsMaxScroll(duplicates.size());
        whoNeedsScrollAmount = Math.max(0, Math.min(gridMaxScroll, whoNeedsScrollAmount));

        int gridTop = guiTop + WHONEEDS_SLOT_Y;
        int gridRight = guiLeft + WHONEEDS_PLAYERS_X - WHONEEDS_GRID_SCROLLBAR_GAP - WHONEEDS_GRID_SCROLLBAR_WIDTH;
        graphics.enableScissor(guiLeft, gridTop, gridRight, gridTop + gridVisibleHeight);
        for (int i = 0; i < duplicates.size(); i++) {
            ClientWhoNeedsHelper.DuplicateItem item = duplicates.get(i);
            int slotY = whoNeedsSlotPosY(i);
            if (slotY + PokemonSlotRenderer.SLOT_SIZE >= gridTop && slotY <= gridTop + gridVisibleHeight) {
                renderPokemonNumberedSlot(graphics, whoNeedsSlotPosX(i), slotY, item.speciesId, item.aspects, item.level);
            }
        }
        graphics.disableScissor();

        int gridScrollbarX = guiLeft + WHONEEDS_PLAYERS_X - WHONEEDS_GRID_SCROLLBAR_GAP - WHONEEDS_GRID_SCROLLBAR_WIDTH;
        renderScrollbar(graphics, gridScrollbarX, WHONEEDS_GRID_SCROLLBAR_WIDTH, gridTop, gridVisibleHeight, whoNeedsScrollAmount, gridMaxScroll);

        // Spielerliste rechts (wo bei Cobblemon das Info-Panel sitzt)
        List<ClientWhoNeedsHelper.PlayerNeedItem> players = ClientWhoNeedsHelper.getPlayerNeeds();
        int playerY = guiTop + WHONEEDS_PLAYERS_Y;
        for (ClientWhoNeedsHelper.PlayerNeedItem player : players) {
            renderPlayerNeedRow(graphics, guiLeft + WHONEEDS_PLAYERS_X, playerY, player);
            playerY += WHONEEDS_ROW_H;
        }

        // Tooltips zuletzt zeichnen, damit sie über allem anderen liegen (wie Cobblemons
        // eigener Search-By-Tooltip, der ebenfalls erst nach super.render() gezeichnet wird).
        if (btnHovered) {
            renderCobblemonTooltip(graphics,
                Component.translatable(livingDexMode ? "cobblecompanion.tooltip.mode_livingdex" : "cobblecompanion.tooltip.mode_pokedex"),
                mouseX, mouseY, -14);
        } else if (isMouseOverSearchBox(whoNeedsSearchBox, mouseX, mouseY)) {
            renderCobblemonTooltip(graphics, Component.translatable(WHONEEDS_SEARCH_TOOLTIP_KEY), mouseX, mouseY, -14);
        }
    }

    private int whoNeedsSlotPosX(int index) {
        int col = index % WHONEEDS_SLOT_COLUMNS;
        return guiLeft + WHONEEDS_SLOT_X + col * WHONEEDS_SLOT_SPACING;
    }

    private int whoNeedsSlotPosY(int index) {
        int row = index / WHONEEDS_SLOT_COLUMNS;
        return guiTop + WHONEEDS_SLOT_Y + row * WHONEEDS_SLOT_SPACING - (int) Math.round(whoNeedsScrollAmount);
    }

    private int whoNeedsGridVisibleHeight() {
        return (guiTop + GUI_HEIGHT - WHONEEDS_GRID_BOTTOM_MARGIN) - (guiTop + WHONEEDS_SLOT_Y);
    }

    private int whoNeedsMaxScroll(int itemCount) {
        int rows = (itemCount + WHONEEDS_SLOT_COLUMNS - 1) / WHONEEDS_SLOT_COLUMNS;
        int contentHeight = rows * WHONEEDS_SLOT_SPACING;
        return Math.max(0, contentHeight - whoNeedsGridVisibleHeight());
    }

    /**
     * Zeichnet einen pokedex_slot.png mit 3D-Modell, Nationaldex-Nummer oben links (wie
     * Cobblemons eigene Pokemon-Liste) und Level unten rechts. Nummer und Level sitzen bewusst
     * auf gegenüberliegenden Ecken (nicht beide oben nebeneinander) - bei nur 25px Slot-Breite
     * würden sie sich sonst schon bei gut lesbarer Schriftgröße überlagern. Gemeinsam genutzt
     * von Who-Needs- und Type-Tab.
     */
    private void renderPokemonNumberedSlot(GuiGraphics graphics, int x, int y, ResourceLocation speciesId, Set<String> aspects, int level) {
        renderPokemonSlotNumber(graphics, x, y, speciesId, aspects);

        String levelText = "Lv" + level;
        int levelWidth = smallLabelWidth(levelText, POKEMON_SLOT_LABEL_SCALE, POKEMON_SLOT_LABEL_BOLD, POKEMON_SLOT_LABEL_UNIFORM_FONT);
        int levelX = x + PokemonSlotRenderer.SLOT_SIZE - levelWidth - POKEMON_SLOT_LEVEL_OFFSET_X;
        int levelY = y + PokemonSlotRenderer.SLOT_SIZE - Math.round(9 * POKEMON_SLOT_LABEL_SCALE) - POKEMON_SLOT_LEVEL_OFFSET_Y;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);
        drawSmallLabel(graphics, levelText, levelX, levelY, POKEMON_SLOT_LABEL_SCALE, 0xFFFFFF, POKEMON_SLOT_LABEL_BOLD, POKEMON_SLOT_LABEL_UNIFORM_FONT);
        graphics.pose().popPose();
    }

    /** Wie renderPokemonNumberedSlot, aber ohne Level (für Vorschau-Slots ohne konkretes Exemplar, z.B. ToDo-Zielart). */
    private void renderPokemonSlotNumber(GuiGraphics graphics, int x, int y, ResourceLocation speciesId, Set<String> aspects) {
        PokemonSlotRenderer.renderSlot(graphics, x, y, speciesId, aspects);

        Species species = PokemonSpecies.INSTANCE.getByIdentifier(speciesId);
        String number = species != null ? String.format("%04d", species.getNationalPokedexNumber()) : "----";

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);
        drawSmallLabel(graphics, number, x + POKEMON_SLOT_NUMBER_OFFSET_X, y + POKEMON_SLOT_NUMBER_OFFSET_Y,
            POKEMON_SLOT_LABEL_SCALE, 0xFFFFFF, POKEMON_SLOT_LABEL_BOLD, POKEMON_SLOT_LABEL_UNIFORM_FONT);
        graphics.pose().popPose();
    }

    /**
     * Zeichnet einen Spieler-Eintrag: Kopf (online = echter Skin, offline = ausgegrauter
     * Steve) mit Online/Offline-Badge in der Ecke, Name, Bedarfs-Status.
     */
    private void renderPlayerNeedRow(GuiGraphics graphics, int x, int y, ClientWhoNeedsHelper.PlayerNeedItem player) {
        ResourceLocation skinTexture;
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.client.multiplayer.PlayerInfo info = (mc.getConnection() != null)
            ? mc.getConnection().getPlayerInfo(player.uuid) : null;

        if (player.online && info != null) {
            skinTexture = info.getSkin().texture();
        } else {
            skinTexture = net.minecraft.client.resources.DefaultPlayerSkin.getDefaultTexture();
        }

        if (!player.online) {
            RenderSystem.setShaderColor(0.5f, 0.5f, 0.5f, 1f);
        }
        net.minecraft.client.gui.components.PlayerFaceRenderer.draw(graphics, skinTexture, x, y, WHONEEDS_HEAD_SIZE);
        if (!player.online) {
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }

        ResourceLocation badge = player.online ? ICON_ONLINE : ICON_OFFLINE;
        graphics.blit(badge,
            x + WHONEEDS_HEAD_SIZE - WHONEEDS_BADGE_SIZE + WHONEEDS_BADGE_OFFSET_X,
            y + WHONEEDS_HEAD_SIZE - WHONEEDS_BADGE_SIZE + WHONEEDS_BADGE_OFFSET_Y,
            WHONEEDS_BADGE_SIZE, WHONEEDS_BADGE_SIZE,
            0f, 0f, 16, 16, 16, 16);

        drawScaledBoldText(graphics, player.name,
            x + WHONEEDS_HEAD_SIZE + 4, y, BODY_TEXT_SCALE);
        // KORREKTUR (per altem JAR-Vergleich vom 13.07. bestätigt): das Label spiegelt den
        // PRO-SPIELER-STATUS wider (needsPokedex-Flag), NICHT den Suchmodus - das war die
        // ursprüngliche, korrekte Bedeutung, die ein früherer Fix in dieser Sitzung fälschlich
        // auf "Modus-basiert" umgestellt hatte. "braucht für Pokédex" = hat die Art noch nie
        // gefangen (braucht sie zwangsläufig AUCH für Living Dex, das ist der grundlegendere
        // Bedarf). "braucht nur für Living Dex" = hat die Art schon mal gefangen (Pokédex-Eintrag
        // vorhanden), besitzt sie aber aktuell nicht - erscheint nur im Living-Dex-Suchmodus
        // (siehe addNeedEntry: needsPokedex=false-Einträge werden im Pokédex-Modus rausgefiltert).
        drawScaledBoldText(graphics,
            player.needsPokedex ? tr("cobblecompanion.gui.whoneeds.needs_pokedex") : tr("cobblecompanion.gui.whoneeds.needs_livingdex"),
            x + WHONEEDS_HEAD_SIZE + 4, y + 10, BODY_TEXT_SCALE);
    }

    /** Prüft Klicks auf Umschalt-Button und Duplikat-Liste im Who-Needs-Tab. */
    private boolean handleWhoNeedsClicks(double mouseX, double mouseY) {
        int toggleX = guiLeft + WHONEEDS_MODE_BTN_X;
        int toggleY = guiTop + WHONEEDS_MODE_BTN_Y;
        if (mouseX >= toggleX && mouseX < toggleX + WHONEEDS_MODE_BTN_W &&
            mouseY >= toggleY && mouseY < toggleY + WHONEEDS_MODE_BTN_H) {
            boolean newMode = !ClientWhoNeedsHelper.isLivingDexMode();
            ClientWhoNeedsHelper.setLivingDexMode(newMode);
            sendToServer(new MyDuplicatesRequestPacket(newMode));
            if (!ClientWhoNeedsHelper.getLastQuery().isBlank()) {
                sendToServer(new WhoNeedsQueryPacket(ClientWhoNeedsHelper.getLastQuery(), newMode,
                    ClientSettingsHelper.isWhoNeedsOnlyFriends(), ClientSettingsHelper.isWhoNeedsFriendsFirst()));
            }
            return true;
        }

        List<ClientWhoNeedsHelper.DuplicateItem> duplicates = ClientWhoNeedsHelper.getDuplicates();
        int gridMaxScroll = whoNeedsMaxScroll(duplicates.size());
        int gridTop = guiTop + WHONEEDS_SLOT_Y;
        int gridVisibleHeight = whoNeedsGridVisibleHeight();
        if (gridMaxScroll > 0) {
            int gridScrollbarX = guiLeft + WHONEEDS_PLAYERS_X - WHONEEDS_GRID_SCROLLBAR_GAP - WHONEEDS_GRID_SCROLLBAR_WIDTH;
            if (isMouseOverScrollbar(mouseX, mouseY, gridScrollbarX, WHONEEDS_GRID_SCROLLBAR_WIDTH, gridTop, gridVisibleHeight)) {
                whoNeedsScrollbarDragging = true;
                whoNeedsScrollAmount = scrollAmountFromMouseY(mouseY, gridTop, gridVisibleHeight, gridMaxScroll);
                return true;
            }
        }

        for (int i = 0; i < duplicates.size(); i++) {
            int slotX = whoNeedsSlotPosX(i);
            int slotY = whoNeedsSlotPosY(i);
            if (slotY + PokemonSlotRenderer.SLOT_SIZE < gridTop || slotY > gridTop + gridVisibleHeight) continue;
            if (mouseX >= slotX && mouseX < slotX + PokemonSlotRenderer.SLOT_SIZE &&
                mouseY >= slotY && mouseY < slotY + PokemonSlotRenderer.SLOT_SIZE) {
                Species species = PokemonSpecies.INSTANCE.getByIdentifier(duplicates.get(i).speciesId);
                if (species == null) return true;
                // Suchfeld zeigt den übersetzten Namen, die Abfrage nutzt intern den internen Namen.
                whoNeedsSearchBox.setValue(speciesDisplayName(species));
                sendWhoNeedsQuery(species.getName());
                return true;
            }
        }
        return false;
    }

    private void sendWhoNeedsQuery(String query) {
        if (query == null || query.isBlank()) return;
        ClientWhoNeedsHelper.setLastQuery(query);
        sendToServer(new WhoNeedsQueryPacket(resolveSearchQuery(query), ClientWhoNeedsHelper.isLivingDexMode(),
            ClientSettingsHelper.isWhoNeedsOnlyFriends(), ClientSettingsHelper.isWhoNeedsFriendsFirst()));
    }

    // ===== Gemeinsame Scrollbar (ToDo- und Type-Tab) =====
    // Nachgebaut aus Cobblemons eigener EntriesScrollingWidget.renderScrollbar() (gleiche
    // Farben: Hintergrund RGB(58,150,182), Balken RGB(252,252,252) - keine eigene Textur,
    // nur zwei fill()-Rechtecke).
    private static final int SCROLLBAR_TRACK_COLOR = 0xFF3A96B6;
    private static final int SCROLLBAR_THUMB_COLOR = 0xFFFCFCFC;
    private static final int SCROLLBAR_MIN_THUMB_HEIGHT = 20;

    void renderScrollbar(GuiGraphics graphics, int barX, int barWidth, int trackTop, int trackHeight, double scrollAmount, int maxScroll) {
        if (maxScroll <= 0) return;
        int contentHeight = trackHeight + maxScroll;
        int thumbHeight = Math.max(SCROLLBAR_MIN_THUMB_HEIGHT, (int) ((long) trackHeight * trackHeight / contentHeight));
        thumbHeight = Math.min(thumbHeight, trackHeight);
        int thumbY = trackTop + (int) Math.round((trackHeight - thumbHeight) * (scrollAmount / maxScroll));

        graphics.fill(barX, trackTop, barX + barWidth, trackTop + trackHeight, SCROLLBAR_TRACK_COLOR);
        graphics.fill(barX, thumbY, barX + barWidth, thumbY + thumbHeight, SCROLLBAR_THUMB_COLOR);
    }

    boolean isMouseOverScrollbar(double mouseX, double mouseY, int barX, int barWidth, int trackTop, int trackHeight) {
        return mouseX >= barX - 2 && mouseX < barX + barWidth + 2 && mouseY >= trackTop && mouseY < trackTop + trackHeight;
    }

    /** Setzt den Scroll-Betrag anhand einer Y-Mausposition innerhalb der Scrollbar-Spur (Klick = Sprung an diese Stelle). */
    double scrollAmountFromMouseY(double mouseY, int trackTop, int trackHeight, int maxScroll) {
        double fraction = (mouseY - trackTop) / (double) trackHeight;
        return Math.max(0, Math.min(maxScroll, fraction * maxScroll));
    }

    // Professor-Tab: Cobblemons eigene PCGUI/PokedexGUI per Reflection eingebettet, gleiche
    // Technik wie cobblemonPokedexInstance unten. professor*DataVersionSeen trackt pro Unteransicht,
    // ob ClientProfessorHelper schon eine neue Antwort hat (siehe checkProfessorSubScreenUpdate()).
    private Screen professorSubScreen = null;
    private int professorPCDataVersionSeen = -1;
    private int professorPokedexDataVersionSeen = -1;
    private int professorLivingDexDataVersionSeen = -1;
    // Pokédex-Ansicht: CobblemonClient.INSTANCE.clientPokedexData ist ein Singleton, das an den
    // LOKALEN Spieler gebunden ist - hier temporär gegen die Zieldaten getauscht, siehe
    // buildProfessorPokedexScreen()/restoreLocalPokedexIfNeeded(). Muss VOR jedem Wechsel zurück
    // zum echten Pokédex/Living-Dex-Tab (oder Verlassen der Professor-Ansicht) rückgängig gemacht
    // werden, sonst zeigt der eigene Pokédex-Tab fälschlich die Daten des Zielspielers an.
    private boolean professorPokedexSwapActive = false;
    private com.cobblemon.mod.common.api.storage.player.client.ClientPokedexManager savedLocalPokedexManager = null;
    // true = professorSubScreen zeigt gerade die Living-Dex-Ansicht (dieselbe PokedexGUI-Klasse
    // wie die normale Pokédex-Ansicht, aber mit zusätzlichen Blatt-Icon-Overlays) - steuert, ob
    // renderProfessorTab() diese Overlays zeichnet, siehe buildProfessorLivingDexScreen().
    private boolean professorViewingLivingDex = false;
    // ClientLivingDexHelpers Spezies-Menge ist wie clientPokedexData ein globales Singleton (an
    // den lokalen Spieler gebunden) - hier temporär gegen die Zieldaten getauscht, muss vor
    // Verlassen der Professor-Living-Dex-Ansicht wiederhergestellt werden (gleiches Muster wie
    // professorPokedexSwapActive oben).
    private boolean professorLivingDexSwapActive = false;
    private java.util.Set<String> savedLocalLivingDexSpecies = null;
    // true = über den "PC"-Knopf geöffnet (Rechtsklick öffnet AdminOp-Edit-Overlay),
    // false = über den "Team"-Knopf geöffnet (nur Verschieben, kein Rechtsklick-Edit) - siehe
    // handleProfessorClicks()/buildProfessorPCScreen()/mouseClicked() (Rechtsklick-Abfang).
    private boolean professorPCRightClickEditEnabled = true;
    // Eigenes Greifen/Ablegen-System für die PC-Ansicht eines anderen Spielers (siehe mouseClicked()
    // Linksklick-Block) - professorGrabbedPokemon != null heißt "gerade etwas in der Hand".
    private Pokemon professorGrabbedPokemon = null;
    private boolean professorGrabbedIsParty = false;
    private com.cobblemon.mod.common.api.storage.pc.PCPosition professorGrabbedPcPosition = null;
    private com.cobblemon.mod.common.api.storage.party.PartyPosition professorGrabbedPartyPosition = null;

    // RCT-Trainerpfad-Ansicht (AdminOp, siehe RCT_PANEL_*-Konstanten): eigene Vollbreiten-Liste
    // statt eingebettetem Cobblemon-GUI (RCT hat keins). professorViewingRct steuert, ob
    // renderProfessorTab() diese Liste statt Spielerliste+Detail zeigt; rctScrollAmount ist der
    // Scroll-Zustand der Serien-Liste; pendingRctReset != null öffnet die Ja/Nein-Bestätigung
    // (Wert = die betroffene seriesId, oder AdminResetRctPacket.ALL_SERIES für "alles").
    private boolean professorViewingRct = false;
    private double rctScrollAmount = 0;
    private String pendingRctReset = null;

    // "Spieler zurücksetzen" (AdminOp, Professor-Tab): 0 = geschlossen, 1 = erste Ja/Nein-Abfrage,
    // 2 = zweite Abfrage mit Tipp-Bestätigung ("BESTÄTIGT" muss exakt eingetippt werden).
    private int resetPlayerConfirmStage = 0;
    private String resetPlayerConfirmInput = "";

    // ===== AdminOp-Editor-Overlay (Level/Shiny/Pokéball/Freilassen) - nur AdminOp, nur PC-Ansicht,
    // ausgelöst über PCGUIConfiguration.selectOverride (siehe buildProfessorPCScreen()). =====
    private boolean adminEditOverlayOpen = false;
    private Pokemon adminEditPokemon = null;
    private int adminEditLevel = 1;
    private boolean adminEditShiny = false;
    private int adminEditBallIndex = 0;
    private int adminEditNatureIndex = 0;
    private int adminEditGenderIndex = 0;
    private String adminEditNickname = "";
    private boolean adminEditNicknameFocused = false;
    private static final String[] ADMIN_EDIT_BALLS = {
        "poke_ball", "great_ball", "ultra_ball", "master_ball",
        "premier_ball", "cherish_ball", "safari_ball", "fast_ball", "heavy_ball", "level_ball"
    };
    private static final String[] ADMIN_EDIT_NATURES = {
        "hardy", "lonely", "brave", "adamant", "naughty", "bold", "docile", "relaxed", "impish", "lax",
        "timid", "hasty", "serious", "jolly", "naive", "modest", "mild", "quiet", "bashful", "rash",
        "calm", "gentle", "sassy", "careful", "quirky"
    };
    private static final String[] ADMIN_EDIT_GENDERS = { "MALE", "FEMALE", "GENDERLESS" };
    // Fähigkeit: anders als Wesen/Geschlecht/Ball keine feste globale Liste - wird pro geöffnetem
    // Pokemon aus dessen eigenem AbilityPool (Form.getAbilities()) neu befüllt (openAdminEditOverlay()).
    private java.util.List<String> adminEditAbilityOptions = new java.util.ArrayList<>();
    private int adminEditAbilityIndex = 0;
    // Spieler-Auswahl-Overlay zum Verschenken (öffnet sich über dem Editor-Overlay).
    private boolean adminGiftOverlayOpen = false;
    private double adminGiftScrollAmount = 0;
    private boolean adminGiftScrollbarDragging = false;
    private static final int ADMIN_GIFT_BOX_W = 200;
    private static final int ADMIN_GIFT_BOX_H = 150;
    private static final int ADMIN_GIFT_ROW_H = 16;
    private static final int ADMIN_GIFT_LIST_START_Y = 24;
    private static final int ADMIN_GIFT_LIST_BOTTOM_MARGIN = 22;
    private static final int ADMIN_GIFT_SCROLLBAR_WIDTH = 3;

    // Entwickeln-Auswahl-Overlay (öffnet sich über dem Editor-Overlay, gleiches Layout wie das
    // Verschenken-Overlay) - listet ALLE potentiell möglichen Entwicklungen von adminEditPokemon,
    // unabhängig davon ob die Voraussetzungen (Level/Item/etc.) aktuell erfüllt sind.
    private static class AdminEvolveCandidate {
        final String label;
        final String toSpeciesId;
        final String toAspects;
        AdminEvolveCandidate(String label, String toSpeciesId, String toAspects) {
            this.label = label;
            this.toSpeciesId = toSpeciesId;
            this.toAspects = toAspects;
        }
    }
    private boolean adminEvolveOverlayOpen = false;
    private double adminEvolveScrollAmount = 0;
    private boolean adminEvolveScrollbarDragging = false;
    private List<AdminEvolveCandidate> adminEvolveCandidates = new java.util.ArrayList<>();
    private int adminEvolveOptionsVersionSeen = -1;
    // Eigene (höhere) Zeilenhöhe als ADMIN_GIFT_ROW_H, damit Zeilen der Sprite-Vorschau
    // (PokemonSlotRenderer.SLOT_SIZE=25) Platz haben - der Spieler-Picker (Verschenken) bleibt
    // bei der schmaleren ADMIN_GIFT_ROW_H, da dort nur Text ohne Sprite steht.
    private static final int ADMIN_EVOLVE_ROW_H = 27;
    private int adminDeEvolveOptionVersionSeen = -1;
    // true = das Auswahlfenster zeigt gerade die Zurückentwickeln-Option (max. 1 Eintrag),
    // false = die normalen Entwicklungs-Optionen - beide teilen sich dasselbe Overlay/Layout.
    private boolean adminEvolveIsDeEvolve = false;

    private Screen cobblemonPokedexInstance = null;
    private boolean pokedexInitialized = false;

private void initPokedex() {
    if (pokedexInitialized) return;
    try {
        // PokedexType enum laden
        Class<?> typeClass = Class.forName(
            "com.cobblemon.mod.common.client.pokedex.PokedexType");
        
        // Farbe des Pokedex als PokedexType
        String color = getPokedexColor().toUpperCase();
        Object pokedexType = null;
        for (Object enumConst : typeClass.getEnumConstants()) {
            if (enumConst.toString().equalsIgnoreCase(color)) {
                pokedexType = enumConst;
                break;
            }
        }
        if (pokedexType == null) {
            // Fallback: RED
            for (Object enumConst : typeClass.getEnumConstants()) {
                if (enumConst.toString().equals("RED")) {
                    pokedexType = enumConst;
                    break;
                }
            }
        }

        // PokedexGUI instanziieren mit (PokedexType, null, null)
        Class<?> guiClass = Class.forName(
            "com.cobblemon.mod.common.client.gui.pokedex.PokedexGUI");
        java.lang.reflect.Constructor<?> ctor = guiClass.getDeclaredConstructor(
            typeClass,
            net.minecraft.resources.ResourceLocation.class,
            net.minecraft.core.BlockPos.class);
        ctor.setAccessible(true);

        cobblemonPokedexInstance = (Screen) ctor.newInstance(
            pokedexType, null, null);

        // Init aufrufen damit Widgets erstellt werden
        cobblemonPokedexInstance.init(
            net.minecraft.client.Minecraft.getInstance(),
            this.width, this.height);

    // Prüfe wie viele Widgets der Pokedex registriert hat
try {
    java.lang.reflect.Field childrenField = net.minecraft.client.gui.screens.Screen.class
        .getDeclaredField("children");
    childrenField.setAccessible(true);
    java.util.List<?> children = (java.util.List<?>) childrenField.get(cobblemonPokedexInstance);
} catch (Exception e) {
    CobbleCompanion.LOGGER.error("[CC] Cannot read children", e);
}

        pokedexInitialized = true;

    } catch (Exception e) {
        CobbleCompanion.LOGGER.error("[CC] Failed to initialize PokedexGUI", e);
    }
}

private void renderPokedexTab(GuiGraphics graphics, int mouseX, int mouseY) {
    // Sicherheitsnetz: falls die Professor-Ansicht (Pokédex eines anderen Spielers) noch aktiv
    // getauscht war, hier IMMER zuerst auf die echten eigenen Daten zurückstellen - sonst würde
    // der eigene Pokédex-Tab fälschlich fremde Daten zeigen (siehe restoreLocalPokedexIfNeeded()).
    restoreLocalPokedexIfNeeded();
    restoreLocalLivingDexIfNeeded();
    if (!pokedexInitialized) {
        initPokedex();
    }
    if (cobblemonPokedexInstance != null) {
        try {
            cobblemonPokedexInstance.render(graphics, mouseX, mouseY, 0);
        } catch (Exception e) {
            CobbleCompanion.LOGGER.error("[CC] Error rendering PokedexGUI", e);
            drawScaledBoldText(graphics, "Error rendering Pokédex", guiLeft + 10, guiTop + 40, DEFAULT_TEXT_SCALE, 0xFF0000);
        }
    } else {
        drawScaledBoldText(graphics, "Pokédex - Failed to load", guiLeft + 10, guiTop + 40, DEFAULT_TEXT_SCALE, 0xFF5555);
    }
}

/**
 * Springt zu einer Spezies im Pokédex-Tab: wechselt den Tab und tippt den Namen in Cobblemons
 * eigenes Suchfeld (searchWidget) der PokedexGUI, dann ruft es deren eigene updateFilters()
 * auf - genau das, was Cobblemon selbst bei einer Sucheingabe tut (filtert + wählt automatisch
 * den ersten Treffer aus). Für den "Zum Pokédex Eintrag"-Link im Type-Tab.
 */
private void jumpToPokedexEntry(String speciesName) {
    currentTab = TAB_POKEDEX;
    if (!pokedexInitialized) initPokedex();
    if (cobblemonPokedexInstance == null) return;
    try {
        Class<?> guiClass = cobblemonPokedexInstance.getClass();
        java.lang.reflect.Field searchField = guiClass.getDeclaredField("searchWidget");
        searchField.setAccessible(true);
        Object searchWidget = searchField.get(cobblemonPokedexInstance);
        searchWidget.getClass().getMethod("setValue", String.class).invoke(searchWidget, speciesName);
        guiClass.getMethod("updateFilters", boolean.class).invoke(cobblemonPokedexInstance, false);
    } catch (Exception e) {
        CobbleCompanion.LOGGER.error("[CC] Error jumping to Pokedex entry", e);
    }
}

    // Neues Feld
private Screen cobblemonLivingDexInstance = null;
private boolean livingDexInitialized = false;

private void initLivingDex() {
    if (livingDexInitialized) return;
    try {
        // Gleiche Logik wie initPokedex()
        Class<?> typeClass = Class.forName(
            "com.cobblemon.mod.common.client.pokedex.PokedexType");
        String color = getPokedexColor().toUpperCase();
        Object pokedexType = null;
        for (Object enumConst : typeClass.getEnumConstants()) {
            if (enumConst.toString().equalsIgnoreCase(color)) {
                pokedexType = enumConst;
                break;
            }
        }
        if (pokedexType == null) {
            for (Object enumConst : typeClass.getEnumConstants()) {
                if (enumConst.toString().equals("RED")) {
                    pokedexType = enumConst;
                    break;
                }
            }
        }

        Class<?> guiClass = Class.forName(
            "com.cobblemon.mod.common.client.gui.pokedex.PokedexGUI");
        java.lang.reflect.Constructor<?> ctor = guiClass.getDeclaredConstructor(
            typeClass,
            net.minecraft.resources.ResourceLocation.class,
            net.minecraft.core.BlockPos.class);
        ctor.setAccessible(true);

        cobblemonLivingDexInstance = (Screen) ctor.newInstance(pokedexType, null, null);
        cobblemonLivingDexInstance.init(
            net.minecraft.client.Minecraft.getInstance(),
            this.width, this.height);

        livingDexInitialized = true;
        CobbleCompanion.LOGGER.info("[CC] Living Dex initialized!");

    } catch (Exception e) {
        CobbleCompanion.LOGGER.error("[CC] Failed to initialize Living Dex", e);
    }
}

private void renderLivingDexTab(GuiGraphics graphics, int mouseX, int mouseY) {
    // Gleiches Sicherheitsnetz wie renderPokedexTab() - Living Dex liest dieselben Pokédex-Daten.
    restoreLocalPokedexIfNeeded();
    restoreLocalLivingDexIfNeeded();
    if (!livingDexInitialized) initLivingDex();

    // Seen/Caught (Cobblemon Pokedex-Wissen) bei jedem Frame neu laden, damit
    // Fänge/Evolutionen sofort sichtbar sind, ohne den Tab neu zu öffnen. Auf den
    // aktuell gewählten Regional-Dex (Kanto, Johto, ...) beschränkt.
    ClientLivingDexHelper.loadData(getSelectedRegion(cobblemonLivingDexInstance));

    if (cobblemonLivingDexInstance != null) {
        try {
            // Pokédex rendern (zeichnet u.a. Cobblemon's eigenen Seen/Caught-Counter rechts oben)
            cobblemonLivingDexInstance.render(graphics, mouseX, mouseY, 0);

            // Cobblemon rendert die 3D-Pokémon-Modelle in der Liste mit aktiviertem Depth-Test
            // (z.B. Z+100-Translate fürs Caught-Icon über dem Modell) und lässt diesen GL-Zustand
            // stehen. Ohne dieses Reset würden unsere Overlays unten gegen den stehengebliebenen
            // Tiefenpuffer verlieren, obwohl sie im Code später gezeichnet werden.
            RenderSystem.disableDepthTest();

            renderCaughtLivingCounter(graphics);
            renderLivingIconsOverlay(graphics, cobblemonLivingDexInstance);

        } catch (Exception e) {
            CobbleCompanion.LOGGER.error("[CC] Error rendering Living Dex", e);
        }
    }
}

/**
 * Übermalt Cobblemon's eigenen Seen/Caught-Counter (oben rechts im Pokedex-Screen)
 * mit einem sauberen Ausschnitt des Hintergrunds und zeichnet stattdessen unseren
 * eigenen Caught/Living-Counter an exakt derselben Stelle, im selben Icon-Stil.
 */
private void renderCaughtLivingCounter(GuiGraphics graphics) {
    // Bereich von Cobblemon's Original-Counter (zwei Icons + Zahlen), rechte obere Ecke.
    graphics.blit(pokedexScreen,
        guiLeft + COUNTER_PATCH_U, guiTop + COUNTER_PATCH_V,
        COUNTER_PATCH_U, COUNTER_PATCH_V,
        COUNTER_PATCH_W, COUNTER_PATCH_H,
        GUI_WIDTH, GUI_HEIGHT);

    int caughtCount = ClientLivingDexHelper.getCaughtCount();
    int livingCount = ClientLivingDexHelper.getLivingDexCount();

    // Icon 1 (Caught): Cobblemon's Original-Pokeball, unverändert.
    graphics.blit(CAUGHT_SEEN_ICON,
        guiLeft + COUNTER_CAUGHT_ICON_X, guiTop + COUNTER_ICON_Y,
        COUNTER_ICON_SIZE, COUNTER_ICON_SIZE,
        0f, 14f,
        14, 14,
        14, 28);
    drawScaledBoldText(graphics, String.format("%04d", caughtCount),
        guiLeft + COUNTER_CAUGHT_TEXT_X, guiTop + COUNTER_CAUGHT_TEXT_Y, COUNTER_TEXT_SCALE);

    // Icon 2 (Living): dieselbe Original-Pokeball + unser Blatt-Icon darüber.
    graphics.blit(CAUGHT_SEEN_ICON,
        guiLeft + COUNTER_LIVING_ICON_X, guiTop + COUNTER_ICON_Y,
        COUNTER_ICON_SIZE, COUNTER_ICON_SIZE,
        0f, 14f,
        14, 14,
        14, 28);
    graphics.blit(LIVING_LEAF_ICON,
        guiLeft + COUNTER_LIVING_ICON_X + COUNTER_LEAF_OFFSET_X,
        guiTop + COUNTER_ICON_Y + COUNTER_LEAF_OFFSET_Y,
        COUNTER_LEAF_SIZE, COUNTER_LEAF_SIZE,
        0f, 0f,
        LEAF_SHEET_SIZE, LEAF_SHEET_SIZE,
        LEAF_SHEET_SIZE, LEAF_SHEET_SIZE);
    drawScaledBoldText(graphics, String.format("%04d", livingCount),
        guiLeft + COUNTER_LIVING_TEXT_X, guiTop + COUNTER_LIVING_TEXT_Y, COUNTER_TEXT_SCALE);
}

/**
 * Zeichnet Text skaliert (kleiner als Standard-Schriftgröße) in COUNTER_TEXT_COLOR.
 * Bequemlichkeits-Overload für den häufigsten Fall (Counter/Fließtexte).
 */
private void drawScaledBoldText(GuiGraphics graphics, String text, int x, int y, float scale) {
    drawScaledBoldText(graphics, text, x, y, scale, COUNTER_TEXT_COLOR);
}

/**
 * Zeichnet Text skaliert mit frei wählbarer Farbe. COUNTER_TEXT_BOLD steuert
 * Fett-Darstellung, COUNTER_TEXT_USE_UNIFORM_FONT die Schriftart (Cobblemon's eigene
 * "uniform"-Schrift statt Minecrafts Standardschrift) - gilt für alle GUI-Texte.
 */
void drawScaledBoldText(GuiGraphics graphics, String text, int x, int y, float scale, int color) {
    graphics.pose().pushPose();
    graphics.pose().translate(x, y, 0);
    graphics.pose().scale(scale, scale, 1f);

    net.minecraft.network.chat.MutableComponent component = Component.literal(text);
    // Kein Lambda/UnaryOperator hier (bewusst): Standard-HotSpot-Hot-Swap wird instabil,
    // sobald eine Klasse eine invokedynamic-Lambda-Bootstrap-Methode enthält - javac
    // nummeriert die synthetische lambda$...$0-Methode bei jeder Neukompilierung neu durch,
    // was die JVM als "Methode gelöscht" meldet, selbst bei Änderungen an anderer Stelle.
    if (COUNTER_TEXT_USE_UNIFORM_FONT) component = component.withStyle(net.minecraft.network.chat.Style.EMPTY.withFont(COUNTER_TEXT_FONT));
    if (COUNTER_TEXT_BOLD) component = component.withStyle(net.minecraft.ChatFormatting.BOLD);

    graphics.drawString(this.font, component, 0, 0, color, true);
    graphics.pose().popPose();
}

/**
 * Zeichnet kleine Beschriftungen (Slot-Nummer/Level, Ergebnis-Stichworte, Item-Namen) mit
 * Minecrafts eingebauter Standardschrift statt der "uniform"-Schrift. So macht es Cobblemon
 * selbst für die kleine Spezies-Nummer in seiner eigenen Pokemon-Liste (EntriesScrollingWidget
 * .PokemonScrollSlotRow.render(): drawScaledText(font=null, scale=0.5F, ...) - kein
 * font-Override, also Minecrafts Default statt uniform). Die Standardschrift ist bei sehr
 * kleiner Skalierung deutlich lesbarer als die blockige uniform-Schrift. Für Namen/Buttons in
 * normaler Größe weiterhin drawScaledBoldText (uniform+bold) nutzen, siehe dort.
 */
void drawSmallLabel(GuiGraphics graphics, String text, int x, int y, float scale, int color, boolean bold, boolean uniformFont) {
    graphics.pose().pushPose();
    graphics.pose().translate(x, y, 0);
    graphics.pose().scale(scale, scale, 1f);

    net.minecraft.network.chat.MutableComponent component = Component.literal(text);
    if (uniformFont) component = component.withStyle(net.minecraft.network.chat.Style.EMPTY.withFont(COUNTER_TEXT_FONT));
    if (bold) component = component.withStyle(net.minecraft.ChatFormatting.BOLD);

    graphics.drawString(this.font, component, 0, 0, color, true);
    graphics.pose().popPose();
}

/** Breite (in Bildschirmpixeln) die drawSmallLabel für diesen Text bei dieser Skalierung/Bold/Schriftart belegen würde. */
int smallLabelWidth(String text, float scale, boolean bold, boolean uniformFont) {
    net.minecraft.network.chat.MutableComponent component = Component.literal(text);
    if (uniformFont) component = component.withStyle(net.minecraft.network.chat.Style.EMPTY.withFont(COUNTER_TEXT_FONT));
    if (bold) component = component.withStyle(net.minecraft.ChatFormatting.BOLD);
    return Math.round(this.font.width(component) * scale);
}

/**
 * Übersetzt einen Sprachschlüssel in den aktuellen Anzeigetext (respektiert das im Client
 * aktive Sprachpaket). Zentral, damit alle GUI-Texte über die lang-Dateien laufen.
 */
private static String tr(String key) {
    return net.minecraft.client.resources.language.I18n.get(key);
}

/** Wie tr, aber mit Format-Argumenten (z.B. Zähler in Klammern). */
private static String tr(String key, Object... args) {
    return net.minecraft.client.resources.language.I18n.get(key, args);
}

/**
 * Kürzt text mit "..." am Ende, falls er bei dieser Skalierung/Bold/Schriftart breiter als
 * maxWidth wäre - für sehr enge Felder, wo lange Pokemon-/Item-Namen sonst über den Rand
 * hinaus in Slot oder Nachbar-Element laufen würden.
 */
private String truncateLabel(String text, float scale, boolean bold, boolean uniformFont, int maxWidth) {
    if (smallLabelWidth(text, scale, bold, uniformFont) <= maxWidth) return text;
    String result = text;
    while (result.length() > 1 && smallLabelWidth(result + "...", scale, bold, uniformFont) > maxWidth) {
        result = result.substring(0, result.length() - 1);
    }
    return result + "...";
}

/**
 * Setzt kleine Living-Icons in Cobblemon's eigener Pokémon-Liste (links) und im
 * Info-Panel des ausgewählten Pokémon (rechts), per Reflection auf Cobblemons
 * private Widget-Felder (scrollScreen / pokemonInfoWidget) von PokedexGUI.
 * Bricht sauber ab (nur Log-Eintrag), falls sich Cobblemons interne Struktur ändert.
 */
private void renderLivingIconsOverlay(GuiGraphics graphics, Screen dexInstance) {
    if (dexInstance == null) return;
    Class<?> guiClass = dexInstance.getClass();

    try {
        java.lang.reflect.Field scrollField = guiClass.getDeclaredField("scrollScreen");
        scrollField.setAccessible(true);
        Object scrollWidget = scrollField.get(dexInstance);
        if (scrollWidget != null) renderLivingIconsInList(graphics, scrollWidget);
    } catch (Exception e) {
        CobbleCompanion.LOGGER.error("[CC] Error rendering Living icons in Pokemon list", e);
    }

    try {
        java.lang.reflect.Field infoField = guiClass.getDeclaredField("pokemonInfoWidget");
        infoField.setAccessible(true);
        Object infoWidget = infoField.get(dexInstance);
        if (infoWidget != null) renderLivingIconInInfoPanel(graphics, infoWidget);
    } catch (Exception e) {
        CobbleCompanion.LOGGER.error("[CC] Error rendering Living icon in info panel", e);
    }
}

/**
 * Cobblemon's Pokémon-Liste rendert Zeilen zu je 5 Slots (27px Abstand, 25x25 groß).
 * Wir lesen pro Zeile Position + gezeigte Spezies + Wissensstand aus (öffentliche
 * Felder/Getter von PokemonScrollSlotRow) und legen unser Blatt-Icon exakt über
 * Cobblemon's eigenes Caught-Icon, wenn die Spezies zusätzlich im Living Dex ist.
 */
private void renderLivingIconsInList(GuiGraphics graphics, Object scrollWidget) throws Exception {
    List<?> rows = (List<?>) invokePublic(scrollWidget, "children");
    int listTop = (int) invokePublic(scrollWidget, "getY");
    int listBottom = (int) invokePublic(scrollWidget, "getBottom");
    int listLeft = (int) invokePublic(scrollWidget, "getRowLeft");
    int listRight = (int) invokePublic(scrollWidget, "getRowRight");

    graphics.enableScissor(listLeft, listTop, listRight, listBottom);
    try {
        for (Object row : rows) {
            int rowX = (int) invokePublic(row, "getX");
            int rowY = (int) invokePublic(row, "getY");
            if (rowY + 27 < listTop || rowY > listBottom) continue; // Zeile aktuell nicht sichtbar

            List<?> dexDataList = (List<?>) invokePublic(row, "getDexDataList");
            List<?> discoveryLevelList = (List<?>) invokePublic(row, "getDiscoveryLevelList");
            for (int i = 0; i < dexDataList.size(); i++) {
                if (!"CAUGHT".equals(String.valueOf(discoveryLevelList.get(i)))) continue;

                String speciesName = extractSpeciesName(dexDataList.get(i));
                if (speciesName == null || !ClientLivingDexHelper.isInLivingDex(speciesName)) continue;

                int startPosX = rowX + 27 * i;
                int startPosY = rowY + 3;
                // Cobblemon zeichnet sein Caught-Icon hier mit einem eigenen Z-Translate
                // über dem 3D-Modell; wir setzen unser Blatt zur Sicherheit noch höher.
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, LIST_LEAF_Z_OFFSET);
                graphics.blit(LIVING_LEAF_ICON,
                    (int) (startPosX + LIST_LEAF_OFFSET_X), (int) (startPosY + LIST_LEAF_OFFSET_Y),
                    LIST_LEAF_SIZE, LIST_LEAF_SIZE,
                    0f, 0f,
                    LEAF_SHEET_SIZE, LEAF_SHEET_SIZE,
                    LEAF_SHEET_SIZE, LEAF_SHEET_SIZE);
                graphics.pose().popPose();
            }
        }
    } finally {
        graphics.disableScissor();
    }
}

/**
 * Legt unser Blatt-Icon exakt über Cobblemon's eigenes "Caught"-Icon im Info-Panel
 * des aktuell ausgewählten Pokémon (rechte Detailansicht), wenn die Spezies
 * zusätzlich im Living Dex ist.
 */
private void renderLivingIconInInfoPanel(GuiGraphics graphics, Object infoWidget) throws Exception {
    Object currentEntry = invokePublic(infoWidget, "getCurrentEntry");
    if (currentEntry == null) return;

    String speciesName = extractSpeciesName(currentEntry);
    if (speciesName == null || !ClientLivingDexHelper.isInLivingDex(speciesName)) return;

    int pX = (int) invokePublic(infoWidget, "getPX");
    int pY = (int) invokePublic(infoWidget, "getPY");

    graphics.blit(LIVING_LEAF_ICON,
        pX + INFO_LEAF_OFFSET_X, pY + INFO_LEAF_OFFSET_Y,
        INFO_LEAF_SIZE, INFO_LEAF_SIZE,
        0f, 0f,
        LEAF_SHEET_SIZE, LEAF_SHEET_SIZE,
        LEAF_SHEET_SIZE, LEAF_SHEET_SIZE);
}

/**
 * Liest den aktuell im Pokedex-GUI gewählten Regional-Dex aus (private Felder
 * availableRegions/selectedRegionIndex von PokedexGUI), z.B. Kanto oder National.
 * Gibt null zurück, falls nicht ermittelbar (dann zählt loadData() ungefiltert).
 */
private ResourceLocation getSelectedRegion(Screen dexInstance) {
    if (dexInstance == null) return null;
    try {
        Class<?> guiClass = dexInstance.getClass();

        java.lang.reflect.Field regionsField = guiClass.getDeclaredField("availableRegions");
        regionsField.setAccessible(true);
        Object regionsObj = regionsField.get(dexInstance);

        java.lang.reflect.Field indexField = guiClass.getDeclaredField("selectedRegionIndex");
        indexField.setAccessible(true);
        int index = (int) indexField.get(dexInstance);

        if (regionsObj instanceof List<?> regions && index >= 0 && index < regions.size()) {
            Object region = regions.get(index);
            if (region instanceof ResourceLocation rl) return rl;
        }
    } catch (Exception e) {
        CobbleCompanion.LOGGER.error("[CC] Error reading selected Pokedex region", e);
    }
    return null;
}

/** Liest species-Namen (Resource-Path, z.B. "charmander") aus einem PokedexEntry aus. */
private String extractSpeciesName(Object pokedexEntry) {
    try {
        Object speciesId = invokePublic(pokedexEntry, "getSpeciesId");
        if (speciesId instanceof ResourceLocation rl) return rl.getPath();
    } catch (Exception ignored) {
        // Struktur unbekannt/anders -> einfach kein Icon für diesen Eintrag
    }
    return null;
}

/** Ruft eine öffentliche, parameterlose Methode (inkl. geerbter) per Reflection auf. */
private static Object invokePublic(Object target, String methodName) throws Exception {
    return target.getClass().getMethod(methodName).invoke(target);
}

    @Override
public boolean mouseClicked(double mouseX, double mouseY, int button) {
    // Modale Sicherheitsabfrage fängt alle Klicks ab, solange sie offen ist.
    if (adminEditOverlayOpen && adminGiftOverlayOpen) {
        return handleAdminGiftOverlayClick(mouseX, mouseY);
    }
    if (adminEditOverlayOpen && adminEvolveOverlayOpen) {
        return handleAdminEvolveOverlayClick(mouseX, mouseY);
    }
    if (adminEditOverlayOpen) {
        return handleAdminEditOverlayClick(mouseX, mouseY);
    }
    if (resetPlayerConfirmStage > 0) {
        return handleResetPlayerConfirmClick(mouseX, mouseY);
    }
    if (pendingRctReset != null) {
        return handleRctResetConfirmClick(mouseX, mouseY);
    }
    if (extensionHasBlockingOverlay()) {
        return CompanionExtensions.getTab(currentTab).blockingOverlayMouseClicked(mouseX, mouseY, tabContext());
    }
    if (pendingEvolveEntry != null) {
        return handleEvolveConfirmClick(mouseX, mouseY);
    }
    if (pendingRemoveFriendUuid != null) {
        return handleRemoveFriendConfirmClick(mouseX, mouseY);
    }
    if (giftOverlayTargetUuid != null) {
        return handleGiftPartyOverlayClick(mouseX, mouseY);
    }
    if (reclaimOverlayOpen) {
        return handleReclaimOverlayClick(mouseX, mouseY);
    }

    // Erst Tab-Icons prüfen (haben immer Priorität) - während einer Professor-Unteransicht
    // (Pokédex/Living Dex/PC eines anderen Spielers) sind sie ausgeblendet (siehe render()) und daher
    // hier auch nicht klickbar; Zurück-Navigation läuft dort über Cobblemons eigene Steuerung.
    boolean tabIconsHidden = currentTab == TAB_PROFESSOR && professorSubScreen != null;
    if (!tabIconsHidden) {
    for (TabDefinition tab : tabs) {
        int cx = guiLeft + tab.clickX;
        int cy = guiTop + tab.clickY;
        if (mouseX >= cx && mouseX <= cx + tab.clickW &&
            mouseY >= cy && mouseY <= cy + tab.clickH) {
            if (tab.tabIndex == TAB_LIVINGDEX && currentTab != TAB_LIVINGDEX) {
                sendToServer(new LivingDexRequestPacket());
            }
            if (tab.tabIndex == TAB_TODO && currentTab != TAB_TODO) {
                sendToServer(new TodoRequestPacket());
                sendToServer(new com.cobblecompanion.network.DexCompletionRequestPacket(
                    ClientSettingsHelper.isModusPokedex(), dexCompletionLdpCategories()));
            }
            if (tab.tabIndex == TAB_WHONEEDS && currentTab != TAB_WHONEEDS) {
                // BUGFIX: der Umschalt-Button im Who-Needs-Tab hatte einen eigenen, nie mit der
                // globalen "Dex Wahl"-Einstellung synchronisierten Zustand (Default false, nicht
                // persistiert) - beim Öffnen des Tabs wich er dadurch oft von der Nutzerwahl ab
                // (z.B. globale Wahl "Living Dex", Tab zeigte trotzdem Pokédex-Reserve-Logik).
                // Beim Betreten des Tabs jetzt an die globale Wahl angleichen; der Button bleibt
                // danach weiterhin manuell umschaltbar (bewusst KEIN Entfernen des eigenen Felds,
                // siehe "überschreiben statt entfernen"-Vorgabe).
                ClientWhoNeedsHelper.setLivingDexMode(!ClientSettingsHelper.isModusPokedex());
                sendToServer(new MyDuplicatesRequestPacket(ClientWhoNeedsHelper.isLivingDexMode()));
            }
            if (tab.tabIndex == TAB_HOME && currentTab != TAB_HOME) {
                sendHomeSummaryRequest();
            }
            if (tab.tabIndex == TAB_WALLET && currentTab != TAB_WALLET) {
                CompanionTabExtension walletExt = CompanionExtensions.getTab(TAB_WALLET);
                if (walletExt != null) walletExt.onTabOpened(tabContext());
            }
            if (tab.tabIndex == TAB_SETTINGS && currentTab != TAB_SETTINGS) {
                // Preis/Minute für den Creative-Preis-Editor (jetzt hier statt im Wallet-Tab).
                sendToServer(new com.cobblecompanion.network.CreativeTimeStatusRequestPacket());
            }
            if (tab.tabIndex == TAB_SEARCH && currentTab != TAB_SEARCH) {
                // Freundesliste muss aktuell sein, damit der Such-Tab Freund-Namen matchen kann,
                // auch wenn der Friends-Tab diese Sitzung noch nie geöffnet wurde.
                sendToServer(new FriendsListRequestPacket());
            }
            if (tab.tabIndex == TAB_FRIENDS && currentTab != TAB_FRIENDS) {
                sendToServer(new FriendsListRequestPacket());
                // Server kennt "Freunde dürfen zu mir teleportieren" sonst erst nach dem ersten
                // Umschalten dieses Settings - hier zusätzlich den aktuellen Client-Stand syncen,
                // damit ein frisch gestarteter Client den Wert auch ohne Toggle serverseitig hat.
                sendToServer(new TeleportPreferencePacket(ClientSettingsHelper.isFriendsAllowTeleportToMe()));
            }
            if (tab.tabIndex == TAB_PROFESSOR) {
                // Klick auf "Professor" bringt IMMER zur Spielerliste zurück, auch wenn von einem
                // anderen Tab aus geklickt und professorSubScreen noch von einem früheren Besuch
                // gesetzt war (vorheriger Bug: Reset passierte nur bei currentTab==TAB_PROFESSOR).
                if (currentTab != TAB_PROFESSOR) {
                    sendToServer(new ProfessorPlayerListRequestPacket());
                }
                if (professorSubScreen != null) {
                    restoreLocalPokedexIfNeeded();
                    restoreLocalLivingDexIfNeeded();
                    professorViewingLivingDex = false;
                    professorSubScreen = null;
                    professorGrabbedPokemon = null;
                }
                professorViewingRct = false;
            }
            currentTab = tab.tabIndex;
            return true;
        }
    }
    }

    // ToDo-Tab: Entwickeln-Buttons (links) + Dex-Vervollständigungshilfe (rechts)
    if (currentTab == TAB_TODO) {
        if (handleSearchSuggestionClick(mouseX, mouseY, dexHelpSearchBox, dexHelpSearchSuggestions(),
                () -> sendDexHelpSearch(dexHelpSearchBox.getValue()))) return true;
        if (handleDexHelpClicks(mouseX, mouseY)) return true;
        boolean dexHelpHit = dexHelpSearchBox.mouseClicked(mouseX, mouseY, button);
        dexHelpSearchBox.setFocused(dexHelpHit);
        if (dexHelpHit) return true;
        if (handleTodoClicks(mouseX, mouseY)) return true;
    }

    // Type-Tab: Ergebnis-Scrollbar/Pokedex-Link, Icon-Grid und Suchfeld
    if (currentTab == TAB_TYPES) {
        if (handleSearchSuggestionClick(mouseX, mouseY, typeSearchBox, typeSearchSuggestions(),
                () -> sendTypeRequest(typeSearchBox.getValue()))) return true;
        if (handleTypeResultClicks(mouseX, mouseY)) return true;
        if (handleTypeGridClick(mouseX, mouseY)) return true;
        boolean hit = typeSearchBox.mouseClicked(mouseX, mouseY, button);
        // Screen's eingebauter Fokus-Mechanismus wird hier umgangen (keine children()-
        // Delegation), also Fokus manuell setzen - sonst nimmt das EditBox trotz
        // weitergeleiteter keyPressed/charTyped keine Eingaben an.
        typeSearchBox.setFocused(hit);
        return true;
    }

    // Who-Needs-Tab: Umschalt-Button, Duplikat-Liste und Suchfeld
    if (currentTab == TAB_WHONEEDS) {
        if (handleSearchSuggestionClick(mouseX, mouseY, whoNeedsSearchBox, whoNeedsSearchSuggestions(),
                () -> sendWhoNeedsQuery(whoNeedsSearchBox.getValue()))) return true;
        if (handleWhoNeedsClicks(mouseX, mouseY)) return true;
        boolean hit = whoNeedsSearchBox.mouseClicked(mouseX, mouseY, button);
        whoNeedsSearchBox.setFocused(hit);
        return true;
    }

    // Home-Tab: Dashboard-Sprunglinks, dann Geschenk-Annehmen-Panel (Scrollbar + Akzeptieren-Buttons)
    if (currentTab == TAB_HOME) {
        if (handleHomeDashboardClicks(mouseX, mouseY)) return true;
        if (handleHomeGiftClicks(mouseX, mouseY)) return true;
        if (handleHomeReclaimBadgeClick(mouseX, mouseY)) return true;
        if (handleHomePendingCobbleDollarsBadgeClick(mouseX, mouseY)) return true;
    }

    // Such-Tab: Vorschlagszeilen/Scrollbar/Verlauf, dann das Suchfeld selbst
    if (currentTab == TAB_SEARCH) {
        if (handleSearchTabClicks(mouseX, mouseY)) return true;
        boolean hit = searchTabSearchBox.mouseClicked(mouseX, mouseY, button);
        searchTabSearchBox.setFocused(hit);
        return true;
    }

    // Team-Builder-Tab: erst Autovervollständigungs-Vorschläge, dann Modus-Buttons/Typ-Liste/Gitter.
    if (currentTab == TAB_TEAMBUILDER) {
        if (handleTeamBuilderSuggestionClick(mouseX, mouseY)) return true;
        handleTeamBuilderClicks(mouseX, mouseY);
        return true;
    }

    // Extension-Tabs (aktuell nur Wallet, siehe com.cobblecompanion.api) - eigenes Klick-Handling
    // inkl. eigener Suchfeld-Vorschlagsbox.
    if (CompanionExtensions.getTab(currentTab) != null) {
        return CompanionExtensions.getTab(currentTab).mouseClicked(mouseX, mouseY, button, tabContext());
    }

    // Friends-Tab: Zeilen-Auswahl, Entfernen-Button, Add-Button und Suchfeld
    if (currentTab == TAB_FRIENDS) {
        if (handleFriendsClicks(mouseX, mouseY)) return true;
        boolean hit = friendsSearchBox.mouseClicked(mouseX, mouseY, button);
        friendsSearchBox.setFocused(hit);
        return true;
    }

    // Professor-Tab: nachgebauter Cobblemon-Tür-Knopf (falls Pokédex/Living-Dex gerade offen ist)
    // oder normal Zeilen-Auswahl/Scrollbar/Suchfeld - bei offenem Sub-Screen übernimmt die
    // generische getActivePokedexScreen()-Weiterleitung weiter unten die restlichen Klicks.
    if (currentTab == TAB_PROFESSOR && professorSubScreen != null
            && !(professorSubScreen instanceof com.cobblemon.mod.common.client.gui.pc.PCGUI)
            && professorBackExitButton != null
            && professorBackExitButton.mouseClicked(mouseX, mouseY, button)) {
        return true;
    }
    // PC-Ansicht: Rechtsklick öffnet (nur beim "PC"-Knopf, nur AdminOp) unser Edit-Overlay statt
    // an Cobblemons PCGUI weitergeleitet zu werden - dort würde Rechtsklick ohnehin nichts tun
    // (StorageSlot.mouseClicked reagiert nur auf Linksklick, siehe buildProfessorPCScreen()).
    if (currentTab == TAB_PROFESSOR && button == 1 && professorPCRightClickEditEnabled
            && ClientAdminHelper.isAdminOp()
            && professorSubScreen instanceof com.cobblemon.mod.common.client.gui.pc.PCGUI pcgui) {
        ProfessorSlotInfo slot = findProfessorPCSlotUnderCursor(pcgui, mouseX, mouseY);
        if (slot != null && slot.pokemon != null) {
            openAdminEditOverlay(slot.pokemon);
        }
        return true;
    }
    // PC-Ansicht: Linksklick greift/legt Pokemon ab - EIGENES Greifen/Ablegen-System statt
    // Cobblemons nativem Drag&Drop, weil dessen Move-Packete (MovePCPokemonPacket & Co.) laut
    // Bytecode-Analyse KEINE storeID mitschicken und serverseitig immer auf der Storage DES
    // SENDERS (also des Admins) arbeiten - ein natives Verschieben in der eingebetteten Ansicht
    // eines FREMDEN Spielers würde also entweder nichts bewirken oder schlimmstenfalls versehentlich
    // in der eigenen PC/Party des Admins herumpfuschen. Deshalb: erster Linksklick auf ein
    // Pokemon "greift" es (visuell aus der lokalen ClientPC/ClientParty entfernt), zweiter
    // Linksklick auf einen Slot "legt" es dort ab (inkl. Tausch bei belegtem Ziel) und schickt
    // erst dann unser eigenes AdminMovePokemonPacket (UUID-basiert, wirkt auf die ECHTE Storage
    // des Zielspielers).
    if (currentTab == TAB_PROFESSOR && button == 0 && ClientAdminHelper.isAdminOp()
            && professorSubScreen instanceof com.cobblemon.mod.common.client.gui.pc.PCGUI pcgui) {
        ProfessorSlotInfo slot = findProfessorPCSlotUnderCursor(pcgui, mouseX, mouseY);
        if (slot != null) {
            if (professorGrabbedPokemon == null) {
                if (slot.pokemon != null) {
                    professorGrabbedPokemon = slot.pokemon;
                    professorGrabbedIsParty = slot.isParty;
                    professorGrabbedPcPosition = slot.pcPosition;
                    professorGrabbedPartyPosition = slot.partyPosition;
                    if (slot.isParty) pcgui.getParty().set(slot.partyPosition, null);
                    else pcgui.getPc().set(slot.pcPosition, null);
                }
            } else {
                Pokemon displaced = slot.pokemon;
                if (slot.isParty) pcgui.getParty().set(slot.partyPosition, professorGrabbedPokemon);
                else pcgui.getPc().set(slot.pcPosition, professorGrabbedPokemon);
                if (displaced != null) {
                    if (professorGrabbedIsParty) pcgui.getParty().set(professorGrabbedPartyPosition, displaced);
                    else pcgui.getPc().set(professorGrabbedPcPosition, displaced);
                }
                ClientProfessorHelper.PlayerItem selected = ClientProfessorHelper.getSelected();
                if (selected != null) {
                    int fromBox = professorGrabbedIsParty ? 0 : professorGrabbedPcPosition.getBox();
                    int fromSlot = professorGrabbedIsParty ? professorGrabbedPartyPosition.getSlot() : professorGrabbedPcPosition.getSlot();
                    int toBox = slot.isParty ? 0 : slot.pcPosition.getBox();
                    int toSlot = slot.isParty ? slot.partyPosition.getSlot() : slot.pcPosition.getSlot();
                    sendToServer(new com.cobblecompanion.network.AdminMovePokemonPacket(
                        selected.uuid, professorGrabbedPokemon.getUuid(),
                        professorGrabbedIsParty, fromBox, fromSlot,
                        slot.isParty, toBox, toSlot));
                }
                professorGrabbedPokemon = null;
            }
            return true;
        }
    }
    if (currentTab == TAB_PROFESSOR && professorSubScreen == null && professorViewingRct) {
        return handleProfessorRctClicks(mouseX, mouseY);
    }
    if (currentTab == TAB_PROFESSOR && professorSubScreen == null && !professorViewingRct) {
        if (handleProfessorClicks(mouseX, mouseY)) return true;
        boolean hit = professorSearchBox.mouseClicked(mouseX, mouseY, button);
        professorSearchBox.setFocused(hit);
        return true;
    }

    // Settings-Tab: Sub-Tab-Leiste und Options-Buttons
    if (currentTab == TAB_SETTINGS) {
        if (handleSettingsClicks(mouseX, mouseY, button)) return true;
    }

    // Pokédex oder Living Dex: Klicks weiterleiten
    Screen activeScreen = getActivePokedexScreen();
    if (activeScreen != null) {
        try {
            return activeScreen.mouseClicked(mouseX, mouseY, button);
        } catch (Exception e) {
            CobbleCompanion.LOGGER.error("[CC] Error in mouseClicked", e);
        }
    }

    return super.mouseClicked(mouseX, mouseY, button);
}

@Override
public boolean mouseReleased(double mouseX, double mouseY, int button) {
    if (ldpDragCategoryId != -1) {
        if (ldpDragArmed && ldpDragOrder != null) {
            ClientSettingsHelper.reorderLivingDexPlusCategories(ldpDragOrder);
            sendHomeSummaryRequest();
        }
        ldpDragCategoryId = -1;
        ldpDragArmed = false;
        ldpDragOrder = null;
        return true;
    }
    if (todoScrollbarDragging || typeScrollbarDragging || settingsScrollbarDragging || whoNeedsScrollbarDragging || homeGiftScrollbarDragging || professorScrollbarDragging || adminGiftScrollbarDragging || adminEvolveScrollbarDragging || dexHelpScrollbarDragging || searchTabScrollbarDragging || teamBuilderResultScrollbarDragging) {
        todoScrollbarDragging = false;
        typeScrollbarDragging = false;
        settingsScrollbarDragging = false;
        whoNeedsScrollbarDragging = false;
        homeGiftScrollbarDragging = false;
        professorScrollbarDragging = false;
        adminGiftScrollbarDragging = false;
        adminEvolveScrollbarDragging = false;
        dexHelpScrollbarDragging = false;
        searchTabScrollbarDragging = false;
        teamBuilderResultScrollbarDragging = false;
        return true;
    }
    CompanionTabExtension draggingExt = CompanionExtensions.getTab(currentTab);
    if (draggingExt != null && draggingExt.isDraggingScrollbar()) {
        draggingExt.mouseReleased(mouseX, mouseY, button, tabContext());
        return true;
    }
    Screen activeScreen = getActivePokedexScreen();
    if (activeScreen != null) {
        try {
            return activeScreen.mouseReleased(mouseX, mouseY, button);
        } catch (Exception e) {
            CobbleCompanion.LOGGER.error("[CC] Error in mouseReleased", e);
        }
    }
    return super.mouseReleased(mouseX, mouseY, button);
}

@Override
public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
    if (ldpDragCategoryId != -1) {
        ldpDragCurrentMouseY = mouseY;
        if (!ldpDragArmed && Math.abs(mouseY - ldpDragStartMouseY) > LDP_DRAG_THRESHOLD_PX) {
            ldpDragArmed = true;
        }
        if (ldpDragArmed) {
            int targetIndex = ldpRowIndexAtY(mouseY);
            if (targetIndex >= 0) {
                int currentIndex = ldpDragOrder.indexOf(ldpDragCategoryId);
                if (currentIndex >= 0 && currentIndex != targetIndex) {
                    ldpDragOrder.remove(currentIndex);
                    ldpDragOrder.add(Math.min(targetIndex, ldpDragOrder.size()), ldpDragCategoryId);
                }
            }
        }
        return true;
    }
    if (todoScrollbarDragging) {
        int maxScroll = todoMaxScroll(buildTodoRows());
        todoScrollAmount = scrollAmountFromMouseY(mouseY, guiTop + TODO_ROW_Y, TODO_LIST_VISIBLE_HEIGHT, maxScroll);
        return true;
    }
    if (dexHelpScrollbarDragging) {
        int maxScroll = dexHelpSearchActive ? dexHelpSearchMaxScroll() : dexHelpMaxScroll(buildDexHelpRows());
        dexHelpScrollAmount = scrollAmountFromMouseY(mouseY, guiTop + DEXHELP_LIST_Y, DEXHELP_LIST_VISIBLE_HEIGHT, maxScroll);
        return true;
    }
    if (typeScrollbarDragging) {
        int maxScroll = typeMaxScroll();
        typeResultScrollAmount = scrollAmountFromMouseY(mouseY, typeResultListTop(), typeResultVisibleHeight(), maxScroll);
        return true;
    }
    if (teamBuilderResultScrollbarDragging) {
        int maxScroll = teamBuilderResultMaxScroll(buildTeamBuilderRows());
        teamBuilderResultScroll = scrollAmountFromMouseY(mouseY, guiTop + TEAMBUILDER_RESULT_Y, teamBuilderResultVisibleHeight(), maxScroll);
        return true;
    }
    if (settingsScrollbarDragging) {
        int maxScroll = settingsMaxScroll(buildSettingsRows());
        settingsScrollAmount = scrollAmountFromMouseY(mouseY, guiTop + SETTINGS_CONTENT_Y, SETTINGS_CONTENT_VISIBLE_HEIGHT, maxScroll);
        return true;
    }
    {
        CompanionTabExtension draggingExt = CompanionExtensions.getTab(currentTab);
        if (draggingExt != null && draggingExt.isDraggingScrollbar()) {
            draggingExt.mouseDragged(mouseX, mouseY, tabContext());
            return true;
        }
    }
    if (whoNeedsScrollbarDragging) {
        int maxScroll = whoNeedsMaxScroll(ClientWhoNeedsHelper.getDuplicates().size());
        whoNeedsScrollAmount = scrollAmountFromMouseY(mouseY, guiTop + WHONEEDS_SLOT_Y, whoNeedsGridVisibleHeight(), maxScroll);
        return true;
    }
    if (homeGiftScrollbarDragging) {
        int maxScroll = homeGiftMaxScroll(ClientGiftHelper.getPendingForMe().size());
        homeGiftScrollAmount = scrollAmountFromMouseY(mouseY, guiTop + HOME_GIFT_Y, homeGiftVisibleHeight(), maxScroll);
        return true;
    }
    if (professorScrollbarDragging) {
        int maxScroll = professorMaxScroll(filteredProfessorPlayers().size());
        professorScrollAmount = scrollAmountFromMouseY(mouseY, guiTop + PROFESSOR_LIST_Y, professorVisibleHeight(), maxScroll);
        return true;
    }
    if (searchTabScrollbarDragging) {
        int maxScroll = searchTabMaxScroll(searchTabSuggestions().size());
        searchTabScrollAmount = scrollAmountFromMouseY(mouseY, guiTop + SEARCH_TAB_LIST_Y, searchTabVisibleHeight(), maxScroll);
        return true;
    }
    if (adminGiftScrollbarDragging) {
        int boxY = guiTop + (GUI_HEIGHT - ADMIN_GIFT_BOX_H) / 2;
        int listTop = boxY + ADMIN_GIFT_LIST_START_Y;
        int visibleHeight = ADMIN_GIFT_BOX_H - ADMIN_GIFT_LIST_START_Y - ADMIN_GIFT_LIST_BOTTOM_MARGIN;
        int maxScroll = Math.max(0, adminGiftCandidates().size() * ADMIN_GIFT_ROW_H - visibleHeight);
        adminGiftScrollAmount = scrollAmountFromMouseY(mouseY, listTop, visibleHeight, maxScroll);
        return true;
    }
    if (adminEvolveScrollbarDragging) {
        int boxY = guiTop + (GUI_HEIGHT - ADMIN_GIFT_BOX_H) / 2;
        int listTop = boxY + ADMIN_GIFT_LIST_START_Y;
        int visibleHeight = ADMIN_GIFT_BOX_H - ADMIN_GIFT_LIST_START_Y - ADMIN_GIFT_LIST_BOTTOM_MARGIN;
        int maxScroll = Math.max(0, adminEvolveCandidates.size() * ADMIN_EVOLVE_ROW_H - visibleHeight);
        adminEvolveScrollAmount = scrollAmountFromMouseY(mouseY, listTop, visibleHeight, maxScroll);
        return true;
    }
    Screen activeScreen = getActivePokedexScreen();
    if (activeScreen != null) {
        try {
            return activeScreen.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        } catch (Exception e) {
            CobbleCompanion.LOGGER.error("[CC] Error in mouseDragged", e);
        }
    }
    return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
}

@Override
public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
    if (currentTab == TAB_TODO) {
        if (mouseX >= guiLeft + DEXHELP_X && dexHelpSearchActive) {
            int maxScroll = dexHelpSearchMaxScroll();
            dexHelpScrollAmount = Math.max(0, Math.min(maxScroll, dexHelpScrollAmount - scrollY * DEXHELP_ROW_H));
        } else if (mouseX >= guiLeft + DEXHELP_X) {
            int maxScroll = dexHelpMaxScroll(buildDexHelpRows());
            dexHelpScrollAmount = Math.max(0, Math.min(maxScroll, dexHelpScrollAmount - scrollY * DEXHELP_ROW_H));
        } else {
            int maxScroll = todoMaxScroll(buildTodoRows());
            todoScrollAmount = Math.max(0, Math.min(maxScroll, todoScrollAmount - scrollY * (TODO_ROW_HEIGHT + TODO_ROW_SPACING)));
        }
        return true;
    }
    if (currentTab == TAB_TYPES) {
        int maxScroll = typeMaxScroll();
        typeResultScrollAmount = Math.max(0, Math.min(maxScroll, typeResultScrollAmount - scrollY * TYPE_RESULT_ROW_H));
        return true;
    }
    if (currentTab == TAB_TEAMBUILDER && ClientTeamBuilderHelper.hasResult()) {
        int maxScroll = teamBuilderResultMaxScroll(buildTeamBuilderRows());
        teamBuilderResultScroll = Math.max(0, Math.min(maxScroll, teamBuilderResultScroll - scrollY * TEAMBUILDER_RESULT_ROW_MIN_H));
        return true;
    }
    if (currentTab == TAB_SETTINGS) {
        int maxScroll = settingsMaxScroll(buildSettingsRows());
        settingsScrollAmount = Math.max(0, Math.min(maxScroll, settingsScrollAmount - scrollY * SETTINGS_OPTION_ROW_H));
        return true;
    }
    if (currentTab == TAB_WHONEEDS) {
        int maxScroll = whoNeedsMaxScroll(ClientWhoNeedsHelper.getDuplicates().size());
        whoNeedsScrollAmount = Math.max(0, Math.min(maxScroll, whoNeedsScrollAmount - scrollY * WHONEEDS_SLOT_SPACING));
        return true;
    }
    if (currentTab == TAB_HOME) {
        int maxScroll = homeGiftMaxScroll(ClientGiftHelper.getPendingForMe().size());
        homeGiftScrollAmount = Math.max(0, Math.min(maxScroll, homeGiftScrollAmount - scrollY * HOME_GIFT_ENTRY_H));
        return true;
    }
    if (CompanionExtensions.getTab(currentTab) != null) {
        return CompanionExtensions.getTab(currentTab).mouseScrolled(mouseX, mouseY, scrollY, tabContext());
    }
    if (adminGiftOverlayOpen) {
        int boxY = guiTop + (GUI_HEIGHT - ADMIN_GIFT_BOX_H) / 2;
        int visibleHeight = ADMIN_GIFT_BOX_H - ADMIN_GIFT_LIST_START_Y - ADMIN_GIFT_LIST_BOTTOM_MARGIN;
        int maxScroll = Math.max(0, adminGiftCandidates().size() * ADMIN_GIFT_ROW_H - visibleHeight);
        adminGiftScrollAmount = Math.max(0, Math.min(maxScroll, adminGiftScrollAmount - scrollY * ADMIN_GIFT_ROW_H));
        return true;
    }
    if (adminEvolveOverlayOpen) {
        int visibleHeight = ADMIN_GIFT_BOX_H - ADMIN_GIFT_LIST_START_Y - ADMIN_GIFT_LIST_BOTTOM_MARGIN;
        int maxScroll = Math.max(0, adminEvolveCandidates.size() * ADMIN_EVOLVE_ROW_H - visibleHeight);
        adminEvolveScrollAmount = Math.max(0, Math.min(maxScroll, adminEvolveScrollAmount - scrollY * ADMIN_EVOLVE_ROW_H));
        return true;
    }
    if (currentTab == TAB_PROFESSOR && professorSubScreen == null && professorViewingRct) {
        int listTop = guiTop + RCT_PANEL_Y + RCT_LIST_Y_OFFSET;
        int visibleHeight = (guiTop + GUI_HEIGHT - PROFESSOR_BOTTOM_MARGIN) - listTop;
        int maxScroll = Math.max(0, ClientProfessorHelper.getRctSeries().size() * (RCT_ROW_H + RCT_ROW_GAP) - visibleHeight);
        rctScrollAmount = Math.max(0, Math.min(maxScroll, rctScrollAmount - scrollY * RCT_ROW_H));
        return true;
    }
    if (currentTab == TAB_PROFESSOR && professorSubScreen == null && !professorViewingRct) {
        int maxScroll = professorMaxScroll(filteredProfessorPlayers().size());
        professorScrollAmount = Math.max(0, Math.min(maxScroll, professorScrollAmount - scrollY * PROFESSOR_ROW_H));
        return true;
    }
    if (currentTab == TAB_SEARCH) {
        int maxScroll = searchTabMaxScroll(searchTabSuggestions().size());
        searchTabScrollAmount = Math.max(0, Math.min(maxScroll, searchTabScrollAmount - scrollY * SEARCH_TAB_ROW_H));
        return true;
    }
    Screen activeScreen = getActivePokedexScreen();
    if (activeScreen != null) {
        try {
            return activeScreen.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        } catch (Exception e) {
            CobbleCompanion.LOGGER.error("[CC] Error in mouseScrolled", e);
        }
    }
    return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
}

@Override
public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
    if (keyCode == 256) { // ESC
        this.onClose();
        return true;
    }
    // "E" schließt den Companion wie Cobblemons eigenen Pokédex - dort (Pokédex/Living-Dex-Tab)
    // erledigt das schon Cobblemons eigener Screen über die Weiterleitung unten. Für alle
    // anderen Tabs holen wir das hier nach, aber nur solange kein Suchfeld gerade Text annimmt.
    if (keyCode == 69 && currentTab != TAB_POKEDEX && currentTab != TAB_LIVINGDEX && !isAnySearchBoxFocused()
            && !adminEditOverlayOpen && resetPlayerConfirmStage == 0 && tbFocusedField == -1) {
        this.onClose();
        return true;
    }
    if (currentTab == TAB_TEAMBUILDER && tbFocusedField != -1) {
        if (keyCode == 259) { // Backspace
            String[] arr = tbFocusedField % 2 == 0 ? tbOpponentName : tbOpponentLevel;
            int row = tbFocusedField / 2;
            if (!arr[row].isEmpty()) arr[row] = arr[row].substring(0, arr[row].length() - 1);
            return true;
        }
        if (keyCode == 257 || keyCode == 335) { // Enter / Numpad-Enter
            tbFocusedField = -1;
            return true;
        }
        return true;
    }
    if (resetPlayerConfirmStage == 2) {
        if (keyCode == 259 && !resetPlayerConfirmInput.isEmpty()) { // Backspace
            resetPlayerConfirmInput = resetPlayerConfirmInput.substring(0, resetPlayerConfirmInput.length() - 1);
        }
        return true;
    }
    if (resetPlayerConfirmStage == 1) {
        return true; // Stufe 1 ist reine Ja/Nein-Abfrage, keine Tastatureingabe nötig.
    }
    // AdminOp-Editor-Overlay ist modal - Tasten NIE an die darunterliegende, eingebettete PCGUI
    // weiterleiten (sonst könnten z.B. Cobblemons eigene Tastenkürzel dort ungewollt feuern).
    if (adminEditOverlayOpen) {
        if (adminEditNicknameFocused) {
            if (keyCode == 259 && !adminEditNickname.isEmpty()) { // Backspace
                adminEditNickname = adminEditNickname.substring(0, adminEditNickname.length() - 1);
                return true;
            }
            if (keyCode == 257 || keyCode == 335) { // Enter/Numpad-Enter bestätigt
                adminEditNicknameFocused = false;
                return true;
            }
        }
        return true;
    }
    if (currentTab == TAB_TODO) {
        if (keyCode == 257 || keyCode == 335) { // Enter / Numpad-Enter
            sendDexHelpSearch(dexHelpSearchBox.getValue());
            return true;
        }
        return dexHelpSearchBox.keyPressed(keyCode, scanCode, modifiers);
    }
    if (currentTab == TAB_TYPES) {
        if (keyCode == 257 || keyCode == 335) { // Enter / Numpad-Enter
            sendTypeRequest(typeSearchBox.getValue());
            return true;
        }
        return typeSearchBox.keyPressed(keyCode, scanCode, modifiers);
    }
    if (currentTab == TAB_WHONEEDS) {
        if (keyCode == 257 || keyCode == 335) { // Enter / Numpad-Enter
            sendWhoNeedsQuery(whoNeedsSearchBox.getValue());
            return true;
        }
        return whoNeedsSearchBox.keyPressed(keyCode, scanCode, modifiers);
    }
    if (currentTab == TAB_FRIENDS) {
        // Kein Enter-Sonderfall nötig - die Freundesliste wird live beim Tippen gefiltert.
        return friendsSearchBox.keyPressed(keyCode, scanCode, modifiers);
    }
    if (CompanionExtensions.getTab(currentTab) != null) {
        return CompanionExtensions.getTab(currentTab).keyPressed(keyCode, scanCode, modifiers, tabContext());
    }
    if (currentTab == TAB_PROFESSOR && professorSubScreen == null) {
        // Kein Enter-Sonderfall nötig - die Spielerliste wird live beim Tippen gefiltert.
        return professorSearchBox.keyPressed(keyCode, scanCode, modifiers);
    }
    if (currentTab == TAB_SEARCH) {
        if (keyCode == 257 || keyCode == 335) { // Enter / Numpad-Enter - nur im Verlauf merken, kein festes Sprungziel
            com.cobblecompanion.client.data.ClientSearchHistoryHelper.addSearch(searchTabSearchBox.getValue());
            return true;
        }
        return searchTabSearchBox.keyPressed(keyCode, scanCode, modifiers);
    }
    Screen activeScreen = getActivePokedexScreen();
    if (activeScreen != null) {
        try {
            return activeScreen.keyPressed(keyCode, scanCode, modifiers);
        } catch (Exception e) {
            CobbleCompanion.LOGGER.error("[CC] Error in keyPressed", e);
        }
    }
    return super.keyPressed(keyCode, scanCode, modifiers);
}

@Override
public boolean charTyped(char codePoint, int modifiers) {
    if (resetPlayerConfirmStage == 2) {
        if (resetPlayerConfirmInput.length() < RESET_PLAYER_CONFIRM_WORD.length() + 4 && !Character.isISOControl(codePoint)) {
            resetPlayerConfirmInput += Character.toUpperCase(codePoint);
        }
        return true;
    }
    if (resetPlayerConfirmStage == 1) {
        return true;
    }
    if (adminEditOverlayOpen) {
        if (adminEditNicknameFocused && adminEditNickname.length() < ADMIN_EDIT_NICKNAME_MAX_LEN
                && !Character.isISOControl(codePoint) && codePoint != '|') {
            adminEditNickname += codePoint;
        }
        return true;
    }
    if (CompanionExtensions.getTab(currentTab) != null) {
        return CompanionExtensions.getTab(currentTab).charTyped(codePoint, modifiers, tabContext());
    }
    if (currentTab == TAB_TEAMBUILDER && tbFocusedField != -1) {
        if (!Character.isISOControl(codePoint)) {
            int row = tbFocusedField / 2;
            if (tbFocusedField % 2 == 0) {
                if (tbOpponentName[row].length() < TEAMBUILDER_OPP_NAME_MAX_LEN && codePoint != '|') {
                    tbOpponentName[row] += codePoint;
                }
            } else if (tbOpponentLevel[row].length() < TEAMBUILDER_OPP_LEVEL_MAX_LEN && Character.isDigit(codePoint)) {
                tbOpponentLevel[row] += codePoint;
            }
        }
        return true;
    }
    if (currentTab == TAB_TODO) {
        return dexHelpSearchBox.charTyped(codePoint, modifiers);
    }
    if (currentTab == TAB_TYPES) {
        return typeSearchBox.charTyped(codePoint, modifiers);
    }
    if (currentTab == TAB_WHONEEDS) {
        return whoNeedsSearchBox.charTyped(codePoint, modifiers);
    }
    if (currentTab == TAB_FRIENDS) {
        return friendsSearchBox.charTyped(codePoint, modifiers);
    }
    if (currentTab == TAB_PROFESSOR && professorSubScreen == null) {
        return professorSearchBox.charTyped(codePoint, modifiers);
    }
    if (currentTab == TAB_SEARCH) {
        return searchTabSearchBox.charTyped(codePoint, modifiers);
    }
    Screen activeScreen = getActivePokedexScreen();
    if (activeScreen != null) {
        try {
            return activeScreen.charTyped(codePoint, modifiers);
        } catch (Exception e) {
            CobbleCompanion.LOGGER.error("[CC] Error in charTyped", e);
        }
    }
    return super.charTyped(codePoint, modifiers);
}

@Override
public void tick() {
    super.tick();
    Screen activeScreen = getActivePokedexScreen();
    if (activeScreen != null) {
        try {
            activeScreen.tick();
        } catch (Exception e) {
            CobbleCompanion.LOGGER.error("[CC] Error in tick", e);
        }
    }
}

/**
 * Gibt den aktiven Cobblemon-Screen zurück wenn Pokédex oder Living Dex Tab aktiv ist.
 */
private Screen getActivePokedexScreen() {
    if (currentTab == TAB_POKEDEX && cobblemonPokedexInstance != null)
        return cobblemonPokedexInstance;
    if (currentTab == TAB_LIVINGDEX && cobblemonLivingDexInstance != null)
        return cobblemonLivingDexInstance;
    // Professor-Tab: Cobblemons eingebettete PCGUI/PokedexGUI, siehe buildProfessorPCScreen()/
    // buildPokedexGuiFromTag().
    if (currentTab == TAB_PROFESSOR && professorSubScreen != null)
        return professorSubScreen;
    return null;
}

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    // ===== INNERE KLASSE: Tab Definition =====
    private static class TabDefinition {
        final int tabIndex;
        final ResourceLocation icon;
        final int iconX;
        final int iconY;
        final int iconW;
        final int iconH;
        final int clickX;
        final int clickY;
        final int clickW;
        final int clickH;

        TabDefinition(int tabIndex, String iconName,
                      int iconX, int iconY, int iconW, int iconH,
                      int clickX, int clickY, int clickW, int clickH) {
            this.tabIndex = tabIndex;
            this.icon = ResourceLocation.fromNamespaceAndPath(
                "cobblecompanion", "textures/gui/tabs/" + iconName + ".png");
            this.iconX = iconX;
            this.iconY = iconY;
            this.iconW = iconW;
            this.iconH = iconH;
            this.clickX = clickX;
            this.clickY = clickY;
            this.clickW = clickW;
            this.clickH = clickH;
        }
    }

    /** Prüft, ob die Maus über einem der beiden Cobblemon-Style-Suchfelder steht (für Tooltips). */
    private boolean isMouseOverSearchBox(CobblemonSearchBox box, int mouseX, int mouseY) {
        return mouseX >= box.getX() && mouseX < box.getX() + box.getWidth()
            && mouseY >= box.getY() && mouseY < box.getY() + box.getHeight();
    }

    /**
     * Nachbau von Cobblemons eigenem Pokedex-Tooltip (com.cobblemon.mod.common.client.gui
     * .pokedex.PokedexTooltip.kt): schmale, horizontal um posX zentrierte Box statt der
     * breiten Standard-Minecraft-Tooltip-Box. offsetY hebt die Box relativ zu posY an
     * (Cobblemon nutzt -14, damit sie über dem Mauszeiger statt darunter sitzt).
     */
    private void renderCobblemonTooltip(GuiGraphics graphics, Component text, int posX, int posY, int offsetY) {
        net.minecraft.network.chat.MutableComponent styled = text.copy();
        if (COUNTER_TEXT_USE_UNIFORM_FONT) styled = styled.withStyle(net.minecraft.network.chat.Style.EMPTY.withFont(COUNTER_TEXT_FONT));
        if (COUNTER_TEXT_BOLD) styled = styled.withStyle(net.minecraft.ChatFormatting.BOLD);

        int textWidth = this.font.width(styled);
        int tooltipWidth = textWidth + 6;
        int tooltipTop = posY + offsetY;
        int left = posX - tooltipWidth / 2;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);

        graphics.blit(TOOLTIP_EDGE, left - 1, tooltipTop, 1, TOOLTIP_HEIGHT, 0f, 0f, 1, TOOLTIP_HEIGHT, 1, TOOLTIP_HEIGHT);
        graphics.blit(TOOLTIP_BACKGROUND, left, tooltipTop, tooltipWidth, TOOLTIP_HEIGHT, 0f, 0f, 1, TOOLTIP_HEIGHT, 1, TOOLTIP_HEIGHT);
        graphics.blit(TOOLTIP_EDGE, left + tooltipWidth, tooltipTop, 1, TOOLTIP_HEIGHT, 0f, 0f, 1, TOOLTIP_HEIGHT, 1, TOOLTIP_HEIGHT);

        graphics.drawString(this.font, styled, posX - textWidth / 2, tooltipTop + 1, COUNTER_TEXT_COLOR, true);
        graphics.pose().popPose();
    }

}