package com.cobblecompanion.cobbledollars.client;

import com.cobblecompanion.api.CompanionTabContext;
import com.cobblecompanion.api.CompanionTabExtension;
import com.cobblecompanion.client.data.ClientCreativeTimeHelper;
import com.cobblecompanion.client.gui.CobblemonSearchBox;
import com.cobblecompanion.cobbledollars.network.CobbleDollarsTransferPacket;
import com.cobblecompanion.cobbledollars.network.CreativeTimeGameModeSwitchPacket;
import com.cobblecompanion.cobbledollars.network.CreativeTimePurchasePacket;
import com.cobblecompanion.data.TransactionLogManager;
import com.cobblecompanion.integrations.cobbledollars.CobbleDollarsScale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * Wallet-Tab (Cobbledollars-Überweisung + Creative-Zeitkauf) - ursprünglich Teil der
 * CompanionScreen-Gott-Klasse in CobbleCompanion: Basis, hierher ausgelagert und als
 * {@link CompanionTabExtension} bei com.cobblecompanion.api.CompanionExtensions registriert (siehe
 * CobbleCompanionDollars-Konstruktor). Ist dieses Mod-Jar nicht installiert, bleibt der Wallet-Tab
 * in Basis leer/inaktiv (kein registrierter Handler für TAB_WALLET).
 *
 * 1:1-Verhaltensübernahme aus dem Original (gleiche Konstanten/Layout/Ablauf) - alle vormals
 * privaten CompanionScreen-Hilfsmethoden (Text/Buttons/Scrollbars/Netzwerkversand/Vorschlagsbox)
 * laufen jetzt über {@link CompanionTabContext}.
 */
public class WalletTabExtension implements CompanionTabExtension {

    private CobblemonSearchBox walletRecipientBox;
    private String walletAmountInput = "";
    private boolean walletAmountFocused = false;
    private String pendingWalletRecipient = null;
    private String pendingWalletAmount = null;
    private double walletLogScrollAmount = 0;
    private boolean walletLogScrollbarDragging = false;
    private String walletCreativeMinutesInput = "";
    private boolean walletCreativeMinutesFocused = false;
    private Integer pendingCreativeMinutes = null;

    private static final int WALLET_X = 26;
    private static final int WALLET_Y = 28;
    // Überweisen + Creative-Kauf sitzen rechts (gleiche X/Y wie Basis' Settings-Tab-Inhaltsbereich),
    // links bleiben Guthaben + Transaktions-Log (Nutzer-Vorgabe) - siehe SETTINGS_CONTENT_* in
    // CompanionScreen, hier als eigene Konstanten dupliziert (gleiches Muster wie im Original).
    private static final int WALLET_RIGHT_X = 180;
    private static final int WALLET_RIGHT_Y = 28;
    private static final int CONTENT_VISIBLE_HEIGHT = 163;
    private static final int WALLET_BALANCE_LINE2_GAP_Y = 11;
    private static final int WALLET_BALANCE_GAP_Y = 27;
    private static final int WALLET_LOG_WIDTH = 140;
    private static final int WALLET_LOG_LINE_H = 9;
    private static final int WALLET_LOG_ENTRY_GAP = 4;
    private static final int WALLET_LOG_SCROLLBAR_GAP = 4;
    private static final int WALLET_LOG_SCROLLBAR_WIDTH = 3;
    private static final int WALLET_SEARCH_W = 135;
    private static final int WALLET_SEARCH_H = 11;
    private static final int WALLET_LABEL_GAP_Y = 10;
    private static final int WALLET_AMOUNT_GAP_Y = 14;
    private static final int WALLET_AMOUNT_BOX_W = 90;
    private static final int WALLET_AMOUNT_BOX_H = 14;
    private static final int WALLET_SEND_BTN_GAP_Y = 12;
    private static final int WALLET_SEND_BTN_W = 90;
    private static final int WALLET_SEND_BTN_H = 16;
    private static final int WALLET_AMOUNT_MAX_LEN = 15;

    private static final int WALLET_CREATIVE_SECTION_GAP_Y = 14;
    private static final int WALLET_CREATIVE_STATUS_GAP_Y = 12;
    private static final int WALLET_CREATIVE_ROW_GAP_Y = 12;
    private static final int WALLET_CREATIVE_REMAINING_LINE_H = 10;
    private static final int WALLET_CREATIVE_MINUTES_BOX_W = 50;
    private static final int WALLET_CREATIVE_MINUTES_BOX_H = 14;
    private static final int WALLET_CREATIVE_BUY_BTN_GAP_X = 6;
    private static final int WALLET_CREATIVE_BUY_BTN_W = 75;
    private static final int WALLET_CREATIVE_BUY_BTN_H = 16;
    private static final int WALLET_CREATIVE_MINUTES_MAX_LEN = 4;
    private static final int WALLET_CREATIVE_GAMEMODE_GAP_Y = 10;
    private static final int WALLET_CREATIVE_GAMEMODE_BTN_W = 70;
    private static final int WALLET_CREATIVE_GAMEMODE_BTN_H = 14;

    private static final float TEXT_SCALE = 1f;
    private static final int SEARCH_SUGGEST_MAX = 5;

    private static final int CONFIRM_BOX_W = 180;
    private static final int CONFIRM_BOX_H = 60;
    private static final int CONFIRM_BTN_W = 56;
    private static final int CONFIRM_BTN_H = 15;
    private static final int CONFIRM_BTN_GAP = 12;

    @Override
    public boolean isAvailable(CompanionTabContext ctx) {
        return true;
    }

    @Override
    public boolean isCapturingTextInput() {
        return (walletRecipientBox != null && walletRecipientBox.isFocused()) || walletAmountFocused || walletCreativeMinutesFocused;
    }

    @Override
    public void onTabOpened(CompanionTabContext ctx) {
        if (walletRecipientBox == null) {
            walletRecipientBox = new CobblemonSearchBox(
                ctx.guiLeft() + WALLET_RIGHT_X, ctx.guiTop() + WALLET_RIGHT_Y,
                WALLET_SEARCH_W, WALLET_SEARCH_H, ctx.tr("cobblecompanion.gui.search.wallet_hint"));
        }
        ctx.sendToServer(new com.cobblecompanion.cobbledollars.network.CobbleDollarsBalanceRequestPacket());
        ctx.sendToServer(new com.cobblecompanion.network.CreativeTimeStatusRequestPacket());
        ctx.sendToServer(new com.cobblecompanion.cobbledollars.network.TransactionLogRequestPacket());
    }

    // ===== Render =====

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CompanionTabContext ctx) {
        if (walletRecipientBox == null) onTabOpened(ctx);
        renderWalletBalancePanel(graphics, mouseX, mouseY, ctx);

        int x = ctx.guiLeft() + WALLET_RIGHT_X;

        walletRecipientBox.render(graphics, mouseX, mouseY, 0);

        int amountLabelY = walletRecipientBox.getY() + WALLET_SEARCH_H + WALLET_AMOUNT_GAP_Y;
        ctx.drawSmallLabel(graphics, ctx.tr("cobblecompanion.gui.wallet.amount"), x, amountLabelY, 1.0f, 0xFFFFFF, true, true);

        int amountBoxY = amountLabelY + WALLET_LABEL_GAP_Y;
        boolean amountMatches = isValidPositiveAmount(walletAmountInput);
        graphics.fill(x - 1, amountBoxY - 1, x + WALLET_AMOUNT_BOX_W + 1, amountBoxY + WALLET_AMOUNT_BOX_H + 1,
            amountMatches ? 0xFF55FF55 : 0xFF808080);
        graphics.fill(x, amountBoxY, x + WALLET_AMOUNT_BOX_W, amountBoxY + WALLET_AMOUNT_BOX_H, 0xFF000000);
        String amountDisplay = walletAmountInput + (walletAmountFocused ? "_" : "");
        ctx.drawSmallLabel(graphics, amountDisplay, x + 4, amountBoxY + 3, 1.0f, 0xFFFFFF, true, true);

        int sendBtnY = amountBoxY + WALLET_AMOUNT_BOX_H + WALLET_SEND_BTN_GAP_Y;
        ctx.renderConfirmButton(graphics, x, sendBtnY, WALLET_SEND_BTN_W, WALLET_SEND_BTN_H,
            ctx.tr("cobblecompanion.gui.wallet.send"), 0xFFFFFFFF, mouseX, mouseY);

        renderWalletCreativeSection(graphics, mouseX, mouseY, x, sendBtnY + WALLET_SEND_BTN_H, ctx);
    }

    private void renderWalletBalancePanel(GuiGraphics graphics, int mouseX, int mouseY, CompanionTabContext ctx) {
        int x = ctx.guiLeft() + WALLET_X;
        int y = ctx.guiTop() + WALLET_Y;

        String balanceLabel = ctx.tr("cobblecompanion.gui.wallet.balance");
        ctx.drawScaledBoldText(graphics, balanceLabel, x, y, TEXT_SCALE, 0xFFD700);
        String balanceAmount = ctx.tr("cobblecompanion.gui.wallet.balance_amount", ClientCobbleDollarsHelper.getFormattedBalance());
        ctx.drawScaledBoldText(graphics, balanceAmount, x, y + WALLET_BALANCE_LINE2_GAP_Y, TEXT_SCALE, 0xFFD700);

        int logTop = y + WALLET_BALANCE_GAP_Y;
        int logBottom = ctx.guiTop() + WALLET_RIGHT_Y + CONTENT_VISIBLE_HEIGHT;
        int visibleHeight = logBottom - logTop;

        List<String[]> lines = walletLogLines(ctx);
        int totalHeight = walletLogLinesHeight(lines);
        int maxScroll = Math.max(0, totalHeight - visibleHeight);
        walletLogScrollAmount = Math.max(0, Math.min(maxScroll, walletLogScrollAmount));

        graphics.enableScissor(x, logTop, x + WALLET_LOG_WIDTH, logBottom);
        int lineY = logTop - (int) Math.round(walletLogScrollAmount);
        boolean firstEntry = true;
        for (String[] line : lines) {
            if (line[2].equals("1")) {
                if (!firstEntry) lineY += WALLET_LOG_ENTRY_GAP;
                firstEntry = false;
            }
            if (lineY + WALLET_LOG_LINE_H >= logTop && lineY <= logBottom) {
                ctx.drawSmallLabel(graphics, line[0], x, lineY, 1.0f, Integer.parseInt(line[1], 16), false, true);
            }
            lineY += WALLET_LOG_LINE_H;
        }
        graphics.disableScissor();

        if (maxScroll > 0) {
            int scrollbarX = x + WALLET_LOG_WIDTH + WALLET_LOG_SCROLLBAR_GAP;
            ctx.renderScrollbar(graphics, scrollbarX, WALLET_LOG_SCROLLBAR_WIDTH, logTop, visibleHeight, walletLogScrollAmount, maxScroll);
        }
    }

    private List<String[]> walletLogLines(CompanionTabContext ctx) {
        List<ClientTransactionLogHelper.Entry> entries = ClientTransactionLogHelper.getEntries();
        List<String[]> lines = new ArrayList<>();
        if (entries.isEmpty()) {
            lines.add(new String[]{ctx.tr("cobblecompanion.gui.wallet.log_empty"), "808080", "1"});
            return lines;
        }
        for (ClientTransactionLogHelper.Entry entry : entries) {
            String text;
            String color;
            // Rohwert (siehe TransactionLogManager.addEntry - immer BigInteger.toString()) auf die
            // Ein-Nachkommastellen-Anzeige umrechnen, robust gegen (theoretisch) kaputte alte Einträge.
            String formattedAmount;
            try {
                formattedAmount = CobbleDollarsScale.formatRaw(new java.math.BigInteger(entry.amount()));
            } catch (NumberFormatException e) {
                formattedAmount = entry.amount();
            }
            switch (entry.type()) {
                case TransactionLogManager.TRANSFER_SENT -> {
                    text = ctx.tr("cobblecompanion.gui.wallet.log_transfer_sent", entry.counterpart(), formattedAmount);
                    color = "ff5555";
                }
                case TransactionLogManager.TRANSFER_RECEIVED -> {
                    text = ctx.tr("cobblecompanion.gui.wallet.log_transfer_received", entry.counterpart(), formattedAmount);
                    color = "55ff55";
                }
                case TransactionLogManager.MERCHANT_SOLD -> {
                    text = ctx.tr("cobblecompanion.gui.wallet.log_merchant_sold", formattedAmount);
                    color = "55ff55";
                }
                case TransactionLogManager.TICKER_PAID -> {
                    text = ctx.tr("cobblecompanion.gui.wallet.log_ticker_paid", formattedAmount);
                    color = "ff5555";
                }
                case TransactionLogManager.MERCHANT_BOUGHT -> {
                    text = ctx.tr("cobblecompanion.gui.wallet.log_merchant_bought", formattedAmount);
                    color = "ff5555";
                }
                case TransactionLogManager.SALE_DETAIL -> {
                    String[] parts = entry.counterpart() != null
                        ? entry.counterpart().split(TransactionLogManager.SALE_DETAIL_DELIMITER, -1)
                        : new String[0];
                    if (parts.length >= 3) {
                        text = ctx.tr("cobblecompanion.gui.wallet.log_sale_detail", parts[0], parts[2], parts[1], formattedAmount);
                    } else {
                        text = ctx.tr("cobblecompanion.gui.wallet.log_ticker_paid", formattedAmount);
                    }
                    color = "55ff55";
                }
                case TransactionLogManager.CREATIVE_PAID -> {
                    text = ctx.tr("cobblecompanion.gui.wallet.log_creative_paid", formattedAmount);
                    color = "ff5555";
                }
                case TransactionLogManager.OBSERVER_REWARD -> {
                    text = ctx.tr("cobblecompanion.gui.wallet.log_observer_reward", formattedAmount);
                    color = "55ff55";
                }
                case TransactionLogManager.OBSERVER_CHARGE -> {
                    text = ctx.tr("cobblecompanion.gui.wallet.log_observer_charge", formattedAmount);
                    color = "ff5555";
                }
                case TransactionLogManager.ONLINE_REWARD -> {
                    text = ctx.tr("cobblecompanion.gui.wallet.log_online_reward", formattedAmount);
                    color = "55ff55";
                }
                default -> {
                    text = formattedAmount;
                    color = "ffffff";
                }
            }
            boolean first = true;
            for (String wrapped : ctx.wrapText(text, 1.0f, false, true, WALLET_LOG_WIDTH)) {
                lines.add(new String[]{wrapped, color, first ? "1" : "0"});
                first = false;
            }
        }
        return lines;
    }

    private int walletLogLinesHeight(List<String[]> lines) {
        int height = 0;
        boolean firstEntry = true;
        for (String[] line : lines) {
            if (line[2].equals("1") && !firstEntry) height += WALLET_LOG_ENTRY_GAP;
            firstEntry = false;
            height += WALLET_LOG_LINE_H;
        }
        return height;
    }

    private boolean handleWalletLogScrollClick(double mouseX, double mouseY, CompanionTabContext ctx) {
        int x = ctx.guiLeft() + WALLET_X;
        int logTop = ctx.guiTop() + WALLET_Y + WALLET_BALANCE_GAP_Y;
        int logBottom = ctx.guiTop() + WALLET_RIGHT_Y + CONTENT_VISIBLE_HEIGHT;
        int visibleHeight = logBottom - logTop;
        int maxScroll = Math.max(0, walletLogLinesHeight(walletLogLines(ctx)) - visibleHeight);
        if (maxScroll <= 0) return false;

        int scrollbarX = x + WALLET_LOG_WIDTH + WALLET_LOG_SCROLLBAR_GAP;
        if (ctx.isMouseOverScrollbar(mouseX, mouseY, scrollbarX, WALLET_LOG_SCROLLBAR_WIDTH, logTop, visibleHeight)) {
            walletLogScrollbarDragging = true;
            walletLogScrollAmount = ctx.scrollAmountFromMouseY(mouseY, logTop, visibleHeight, maxScroll);
            return true;
        }
        return false;
    }

    private void renderWalletCreativeSection(GuiGraphics graphics, int mouseX, int mouseY, int x, int afterSendBtnY, CompanionTabContext ctx) {
        int titleY = afterSendBtnY + WALLET_CREATIVE_SECTION_GAP_Y;
        ctx.drawScaledBoldText(graphics, ctx.tr("cobblecompanion.gui.wallet.creative_title"), x, titleY, TEXT_SCALE, 0xFFD700);

        int statusY = titleY + WALLET_CREATIVE_STATUS_GAP_Y;
        String priceLine = ctx.tr("cobblecompanion.gui.wallet.creative_price", CobbleDollarsScale.formatRaw(java.math.BigInteger.valueOf(ClientCreativeTimeHelper.getPricePerMinute())));
        ctx.drawSmallLabel(graphics, priceLine, x, statusY, 1.0f, 0xFFFFFF, true, true);
        long remaining = ClientCreativeTimeHelper.getRemainingSeconds();
        int rowY = statusY + WALLET_CREATIVE_ROW_GAP_Y;
        if (remaining > 0) {
            String remainingLine = ctx.tr("cobblecompanion.gui.wallet.creative_remaining", formatDuration(remaining));
            ctx.drawSmallLabel(graphics, remainingLine, x, statusY + WALLET_CREATIVE_REMAINING_LINE_H, 1.0f, 0xFF55FF55, true, true);
            rowY += WALLET_CREATIVE_REMAINING_LINE_H;
        }
        boolean minutesValid = isValidCreativeMinutes(walletCreativeMinutesInput);
        graphics.fill(x - 1, rowY - 1, x + WALLET_CREATIVE_MINUTES_BOX_W + 1, rowY + WALLET_CREATIVE_MINUTES_BOX_H + 1,
            minutesValid ? 0xFF55FF55 : 0xFF808080);
        graphics.fill(x, rowY, x + WALLET_CREATIVE_MINUTES_BOX_W, rowY + WALLET_CREATIVE_MINUTES_BOX_H, 0xFF000000);
        String minutesDisplay = walletCreativeMinutesInput + (walletCreativeMinutesFocused ? "_" : "");
        ctx.drawSmallLabel(graphics, minutesDisplay, x + 4, rowY + 3, 1.0f, 0xFFFFFF, true, true);

        int buyBtnX = x + WALLET_CREATIVE_MINUTES_BOX_W + WALLET_CREATIVE_BUY_BTN_GAP_X;
        ctx.renderConfirmButton(graphics, buyBtnX, rowY - 1, WALLET_CREATIVE_BUY_BTN_W, WALLET_CREATIVE_BUY_BTN_H,
            ctx.tr("cobblecompanion.gui.wallet.creative_buy"), 0xFFFFFFFF, mouseX, mouseY);

        if (remaining > 0) {
            int gamemodeBtnY = rowY + WALLET_CREATIVE_MINUTES_BOX_H + WALLET_CREATIVE_GAMEMODE_GAP_Y;
            String label = ctx.tr(gamemodeLabelKey(nextGameModeIndex()));
            ctx.renderConfirmButton(graphics, x, gamemodeBtnY, WALLET_CREATIVE_GAMEMODE_BTN_W, WALLET_CREATIVE_GAMEMODE_BTN_H,
                label, 0xFFFFFFFF, mouseX, mouseY);
        }
    }

    private static int currentGameModeIndex() {
        var player = Minecraft.getInstance().player;
        if (player == null) return 0;
        if (player.isSpectator()) return 2;
        if (player.isCreative()) return 1;
        return 0;
    }

    private static int nextGameModeIndex() {
        return (currentGameModeIndex() + 1) % 3;
    }

    private static String gamemodeLabelKey(int index) {
        return switch (index) {
            case 1 -> "cobblecompanion.gui.wallet.gamemode_creative";
            case 2 -> "cobblecompanion.gui.wallet.gamemode_spectator";
            default -> "cobblecompanion.gui.wallet.gamemode_survival";
        };
    }

    private boolean isValidCreativeMinutes(String value) {
        if (value.isEmpty()) return false;
        try {
            int minutes = Integer.parseInt(value);
            return minutes >= CreativeTimePurchasePacket.MIN_MINUTES && minutes <= CreativeTimePurchasePacket.MAX_MINUTES;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String formatDuration(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) return hours + ":" + String.format("%02d", minutes) + ":" + String.format("%02d", seconds);
        return minutes + ":" + String.format("%02d", seconds);
    }

    private boolean isValidPositiveAmount(String value) {
        if (value.isEmpty()) return false;
        java.math.BigInteger raw = CobbleDollarsScale.parseToRaw(value);
        return raw != null && raw.signum() > 0;
    }

    // ===== Klicks =====

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, CompanionTabContext ctx) {
        if (ctx.handleSearchSuggestionClick(mouseX, mouseY, walletRecipientBox, walletRecipientSuggestions())) return true;
        return handleWalletClicks(mouseX, mouseY, ctx);
    }

    private boolean handleWalletClicks(double mouseX, double mouseY, CompanionTabContext ctx) {
        if (handleWalletLogScrollClick(mouseX, mouseY, ctx)) return true;

        boolean recipientHit = walletRecipientBox.mouseClicked(mouseX, mouseY, 0);
        walletRecipientBox.setFocused(recipientHit);
        if (recipientHit) {
            walletAmountFocused = false;
            return true;
        }

        int x = ctx.guiLeft() + WALLET_RIGHT_X;
        int amountLabelY = walletRecipientBox.getY() + WALLET_SEARCH_H + WALLET_AMOUNT_GAP_Y;
        int amountBoxY = amountLabelY + WALLET_LABEL_GAP_Y;
        if (ctx.isInRect(mouseX, mouseY, x, amountBoxY, WALLET_AMOUNT_BOX_W, WALLET_AMOUNT_BOX_H)) {
            walletAmountFocused = true;
            return true;
        }

        int sendBtnY = amountBoxY + WALLET_AMOUNT_BOX_H + WALLET_SEND_BTN_GAP_Y;
        if (ctx.isInRect(mouseX, mouseY, x, sendBtnY, WALLET_SEND_BTN_W, WALLET_SEND_BTN_H)) {
            String recipient = walletRecipientBox.getValue().trim();
            if (!recipient.isEmpty() && isValidPositiveAmount(walletAmountInput)) {
                pendingWalletRecipient = recipient;
                pendingWalletAmount = walletAmountInput;
            }
            return true;
        }

        return handleWalletCreativeClicks(mouseX, mouseY, x, sendBtnY + WALLET_SEND_BTN_H, ctx);
    }

    private boolean handleWalletCreativeClicks(double mouseX, double mouseY, int x, int afterSendBtnY, CompanionTabContext ctx) {
        int titleY = afterSendBtnY + WALLET_CREATIVE_SECTION_GAP_Y;
        int statusY = titleY + WALLET_CREATIVE_STATUS_GAP_Y;
        int rowY = statusY + WALLET_CREATIVE_ROW_GAP_Y;
        if (ClientCreativeTimeHelper.getRemainingSeconds() > 0) rowY += WALLET_CREATIVE_REMAINING_LINE_H;

        if (ctx.isInRect(mouseX, mouseY, x, rowY, WALLET_CREATIVE_MINUTES_BOX_W, WALLET_CREATIVE_MINUTES_BOX_H)) {
            walletCreativeMinutesFocused = true;
            walletAmountFocused = false;
            return true;
        }

        int buyBtnX = x + WALLET_CREATIVE_MINUTES_BOX_W + WALLET_CREATIVE_BUY_BTN_GAP_X;
        if (ctx.isInRect(mouseX, mouseY, buyBtnX, rowY - 1, WALLET_CREATIVE_BUY_BTN_W, WALLET_CREATIVE_BUY_BTN_H)) {
            if (isValidCreativeMinutes(walletCreativeMinutesInput)) {
                pendingCreativeMinutes = Integer.parseInt(walletCreativeMinutesInput);
            }
            return true;
        }

        if (ClientCreativeTimeHelper.getRemainingSeconds() > 0) {
            int gamemodeBtnY = rowY + WALLET_CREATIVE_MINUTES_BOX_H + WALLET_CREATIVE_GAMEMODE_GAP_Y;
            if (ctx.isInRect(mouseX, mouseY, x, gamemodeBtnY, WALLET_CREATIVE_GAMEMODE_BTN_W, WALLET_CREATIVE_GAMEMODE_BTN_H)) {
                ctx.sendToServer(new CreativeTimeGameModeSwitchPacket(nextGameModeIndex()));
                return true;
            }
        }
        return false;
    }

    private List<String> walletRecipientSuggestions() {
        if (!walletRecipientBox.isFocused()) return List.of();
        String q = walletRecipientBox.getValue().trim().toLowerCase();
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return List.of();
        String selfName = mc.player != null ? mc.player.getName().getString() : "";

        List<String> result = new ArrayList<>();
        for (net.minecraft.client.multiplayer.PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            String name = info.getProfile().getName();
            if (name.equalsIgnoreCase(selfName) || name.equalsIgnoreCase(q)) continue;
            if (q.isEmpty() || name.toLowerCase().startsWith(q)) result.add(name);
            if (result.size() >= SEARCH_SUGGEST_MAX) break;
        }
        return result;
    }

    @Override
    public void renderTopLayerContent(GuiGraphics graphics, int mouseX, int mouseY, CompanionTabContext ctx) {
        if (walletRecipientBox != null) {
            ctx.renderSearchSuggestions(graphics, mouseX, mouseY, walletRecipientBox, walletRecipientSuggestions());
        }
    }

    // ===== Scrollbar-Drag =====

    @Override
    public boolean isDraggingScrollbar() {
        return walletLogScrollbarDragging;
    }

    @Override
    public void mouseDragged(double mouseX, double mouseY, CompanionTabContext ctx) {
        int logTop = ctx.guiTop() + WALLET_Y + WALLET_BALANCE_GAP_Y;
        int visibleHeight = ctx.guiTop() + WALLET_RIGHT_Y + CONTENT_VISIBLE_HEIGHT - logTop;
        int maxScroll = Math.max(0, walletLogLinesHeight(walletLogLines(ctx)) - visibleHeight);
        walletLogScrollAmount = ctx.scrollAmountFromMouseY(mouseY, logTop, visibleHeight, maxScroll);
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button, CompanionTabContext ctx) {
        walletLogScrollbarDragging = false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY, CompanionTabContext ctx) {
        int logTop = ctx.guiTop() + WALLET_Y + WALLET_BALANCE_GAP_Y;
        int visibleHeight = ctx.guiTop() + WALLET_RIGHT_Y + CONTENT_VISIBLE_HEIGHT - logTop;
        int maxScroll = Math.max(0, walletLogLinesHeight(walletLogLines(ctx)) - visibleHeight);
        walletLogScrollAmount = Math.max(0, Math.min(maxScroll, walletLogScrollAmount - scrollY * WALLET_LOG_LINE_H * 3));
        return true;
    }

    // ===== Tastatur =====

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers, CompanionTabContext ctx) {
        if (walletAmountFocused) {
            if (keyCode == 259 && !walletAmountInput.isEmpty()) { // Backspace
                walletAmountInput = walletAmountInput.substring(0, walletAmountInput.length() - 1);
            }
            return true;
        }
        if (walletCreativeMinutesFocused) {
            if (keyCode == 259 && !walletCreativeMinutesInput.isEmpty()) { // Backspace
                walletCreativeMinutesInput = walletCreativeMinutesInput.substring(0, walletCreativeMinutesInput.length() - 1);
            }
            return true;
        }
        return walletRecipientBox != null && walletRecipientBox.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers, CompanionTabContext ctx) {
        if (walletAmountFocused) {
            // Nutzer-Vorgabe: deutsches Zahlenformat - Komma als Dezimaltrennzeichen (Punkt wird
            // beim Tippen als Komma-Alias akzeptiert, Tippgewohnheit), nur einmal und nicht als
            // allererstes Zeichen (führt sonst zu z.B. ",5" statt "0,5").
            if ((chr == ',' || chr == '.') && !walletAmountInput.isEmpty() && !walletAmountInput.contains(",")
                    && walletAmountInput.length() < WALLET_AMOUNT_MAX_LEN) {
                walletAmountInput += ",";
            } else if (Character.isDigit(chr) && walletAmountInput.length() < WALLET_AMOUNT_MAX_LEN) {
                int comma = walletAmountInput.indexOf(',');
                if (comma >= 0 && walletAmountInput.length() - comma > 1) {
                    // Bereits eine Nachkommastelle nach dem Komma vorhanden - weitere Ziffern ignorieren.
                } else if (!(walletAmountInput.equals("0"))) {
                    walletAmountInput += chr;
                } else if (chr != '0') {
                    walletAmountInput = String.valueOf(chr);
                }
            }
            return true;
        }
        if (walletCreativeMinutesFocused) {
            if (Character.isDigit(chr) && walletCreativeMinutesInput.length() < WALLET_CREATIVE_MINUTES_MAX_LEN) {
                if (!(walletCreativeMinutesInput.equals("0"))) {
                    walletCreativeMinutesInput += chr;
                } else if (chr != '0') {
                    walletCreativeMinutesInput = String.valueOf(chr);
                }
            }
            return true;
        }
        if (walletRecipientBox != null && walletRecipientBox.isFocused()) {
            return walletRecipientBox.charTyped(chr, modifiers);
        }
        return false;
    }

    // ===== Blockierende Ja/Nein-Bestätigung (Überweisung ODER Creative-Kauf) =====

    @Override
    public boolean hasBlockingOverlay() {
        return pendingWalletRecipient != null || pendingCreativeMinutes != null;
    }

    @Override
    public void renderBlockingOverlay(GuiGraphics graphics, int mouseX, int mouseY, CompanionTabContext ctx) {
        if (pendingWalletRecipient != null) {
            renderWalletConfirmOverlay(graphics, mouseX, mouseY, ctx);
        } else if (pendingCreativeMinutes != null) {
            renderCreativeConfirmOverlay(graphics, mouseX, mouseY, ctx);
        }
    }

    @Override
    public boolean blockingOverlayMouseClicked(double mouseX, double mouseY, CompanionTabContext ctx) {
        if (pendingWalletRecipient != null) return handleWalletConfirmClick(mouseX, mouseY, ctx);
        if (pendingCreativeMinutes != null) return handleCreativeConfirmClick(mouseX, mouseY, ctx);
        return false;
    }

    private void renderWalletConfirmOverlay(GuiGraphics graphics, int mouseX, int mouseY, CompanionTabContext ctx) {
        graphics.fill(0, 0, ctx.screenWidth(), ctx.screenHeight(), 0xC0000000);
        int boxX = ctx.guiLeft() + (ctx.guiWidth() - CONFIRM_BOX_W) / 2;
        int boxY = ctx.guiTop() + (ctx.guiHeight() - CONFIRM_BOX_H) / 2;
        graphics.fill(boxX, boxY, boxX + CONFIRM_BOX_W, boxY + CONFIRM_BOX_H, 0xFF202020);
        graphics.fill(boxX, boxY, boxX + CONFIRM_BOX_W, boxY + 1, 0xFF00A0C0);
        graphics.fill(boxX, boxY + CONFIRM_BOX_H - 1, boxX + CONFIRM_BOX_W, boxY + CONFIRM_BOX_H, 0xFF00A0C0);

        String question = ctx.tr("cobblecompanion.gui.wallet.confirm", pendingWalletAmount, pendingWalletRecipient);
        List<String> lines = ctx.wrapText(question, 1.0f, true, true, CONFIRM_BOX_W - 16);
        int lineY = boxY + 10;
        for (String line : lines) {
            int lw = ctx.smallLabelWidth(line, 1.0f, true, true);
            ctx.drawSmallLabel(graphics, line, boxX + (CONFIRM_BOX_W - lw) / 2, lineY, 1.0f, 0xFFFFFF, true, true);
            lineY += 9;
        }

        int btnY = boxY + CONFIRM_BOX_H - CONFIRM_BTN_H - 8;
        int yesX = boxX + CONFIRM_BOX_W / 2 - CONFIRM_BTN_W - CONFIRM_BTN_GAP / 2;
        int noX = boxX + CONFIRM_BOX_W / 2 + CONFIRM_BTN_GAP / 2;
        ctx.renderConfirmButton(graphics, yesX, btnY, CONFIRM_BTN_W, CONFIRM_BTN_H, ctx.tr("cobblecompanion.gui.confirm.yes"), 0xFF55FF55, mouseX, mouseY);
        ctx.renderConfirmButton(graphics, noX, btnY, CONFIRM_BTN_W, CONFIRM_BTN_H, ctx.tr("cobblecompanion.gui.confirm.no"), 0xFFFF5555, mouseX, mouseY);
    }

    private boolean handleWalletConfirmClick(double mouseX, double mouseY, CompanionTabContext ctx) {
        int boxX = ctx.guiLeft() + (ctx.guiWidth() - CONFIRM_BOX_W) / 2;
        int boxY = ctx.guiTop() + (ctx.guiHeight() - CONFIRM_BOX_H) / 2;
        int btnY = boxY + CONFIRM_BOX_H - CONFIRM_BTN_H - 8;
        int yesX = boxX + CONFIRM_BOX_W / 2 - CONFIRM_BTN_W - CONFIRM_BTN_GAP / 2;
        int noX = boxX + CONFIRM_BOX_W / 2 + CONFIRM_BTN_GAP / 2;
        if (ctx.isInRect(mouseX, mouseY, yesX, btnY, CONFIRM_BTN_W, CONFIRM_BTN_H)) {
            ctx.sendToServer(new CobbleDollarsTransferPacket(pendingWalletRecipient, pendingWalletAmount));
            walletRecipientBox.setValue("");
            walletAmountInput = "";
        }
        pendingWalletRecipient = null;
        pendingWalletAmount = null;
        return true;
    }

    private void renderCreativeConfirmOverlay(GuiGraphics graphics, int mouseX, int mouseY, CompanionTabContext ctx) {
        graphics.fill(0, 0, ctx.screenWidth(), ctx.screenHeight(), 0xC0000000);
        int boxX = ctx.guiLeft() + (ctx.guiWidth() - CONFIRM_BOX_W) / 2;
        int boxY = ctx.guiTop() + (ctx.guiHeight() - CONFIRM_BOX_H) / 2;
        graphics.fill(boxX, boxY, boxX + CONFIRM_BOX_W, boxY + CONFIRM_BOX_H, 0xFF202020);
        graphics.fill(boxX, boxY, boxX + CONFIRM_BOX_W, boxY + 1, 0xFF00A0C0);
        graphics.fill(boxX, boxY + CONFIRM_BOX_H - 1, boxX + CONFIRM_BOX_W, boxY + CONFIRM_BOX_H, 0xFF00A0C0);

        long cost = ClientCreativeTimeHelper.getPricePerMinute() * pendingCreativeMinutes;
        String question = ctx.tr("cobblecompanion.gui.wallet.creative_confirm",
            String.valueOf(pendingCreativeMinutes), CobbleDollarsScale.formatRaw(java.math.BigInteger.valueOf(cost)));
        List<String> lines = ctx.wrapText(question, 1.0f, true, true, CONFIRM_BOX_W - 16);
        int lineY = boxY + 10;
        for (String line : lines) {
            int lw = ctx.smallLabelWidth(line, 1.0f, true, true);
            ctx.drawSmallLabel(graphics, line, boxX + (CONFIRM_BOX_W - lw) / 2, lineY, 1.0f, 0xFFFFFF, true, true);
            lineY += 9;
        }

        int btnY = boxY + CONFIRM_BOX_H - CONFIRM_BTN_H - 8;
        int yesX = boxX + CONFIRM_BOX_W / 2 - CONFIRM_BTN_W - CONFIRM_BTN_GAP / 2;
        int noX = boxX + CONFIRM_BOX_W / 2 + CONFIRM_BTN_GAP / 2;
        ctx.renderConfirmButton(graphics, yesX, btnY, CONFIRM_BTN_W, CONFIRM_BTN_H, ctx.tr("cobblecompanion.gui.confirm.yes"), 0xFF55FF55, mouseX, mouseY);
        ctx.renderConfirmButton(graphics, noX, btnY, CONFIRM_BTN_W, CONFIRM_BTN_H, ctx.tr("cobblecompanion.gui.confirm.no"), 0xFFFF5555, mouseX, mouseY);
    }

    private boolean handleCreativeConfirmClick(double mouseX, double mouseY, CompanionTabContext ctx) {
        int boxX = ctx.guiLeft() + (ctx.guiWidth() - CONFIRM_BOX_W) / 2;
        int boxY = ctx.guiTop() + (ctx.guiHeight() - CONFIRM_BOX_H) / 2;
        int btnY = boxY + CONFIRM_BOX_H - CONFIRM_BTN_H - 8;
        int yesX = boxX + CONFIRM_BOX_W / 2 - CONFIRM_BTN_W - CONFIRM_BTN_GAP / 2;
        int noX = boxX + CONFIRM_BOX_W / 2 + CONFIRM_BTN_GAP / 2;
        if (ctx.isInRect(mouseX, mouseY, yesX, btnY, CONFIRM_BTN_W, CONFIRM_BTN_H)) {
            ctx.sendToServer(new CreativeTimePurchasePacket(pendingCreativeMinutes));
            walletCreativeMinutesInput = "";
        }
        pendingCreativeMinutes = null;
        return true;
    }
}
