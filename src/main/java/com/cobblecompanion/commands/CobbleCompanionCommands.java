package com.cobblecompanion.commands;

import com.cobblecompanion.data.AdminPermissionManager;
import com.cobblecompanion.data.CreativeTimeManager;
import com.cobblecompanion.data.DimensionGamemodeManager;
import com.cobblecompanion.data.FriendsManager;
import com.cobblecompanion.data.GiftManager;
import com.cobblecompanion.data.PlayerDataHelper;
import com.cobblecompanion.data.ServerDataHelper;
import com.cobblecompanion.data.TodoHelper;
import com.cobblecompanion.data.TypeHelper;
import net.minecraft.world.level.GameType;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.GameModeArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CobbleCompanionCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("companion")
                .executes(ctx -> { sendHelp(ctx.getSource()); return 1; })
                .then(Commands.literal("help")
                    .executes(ctx -> { sendHelp(ctx.getSource()); return 1; }))
                .then(Commands.literal("party")
                    .executes(ctx -> { sendParty(ctx.getSource()); return 1; }))
                .then(Commands.literal("dex")
                    .then(Commands.argument("pokemon", StringArgumentType.word())
                        .executes(ctx -> {
                            sendDex(ctx.getSource(), StringArgumentType.getString(ctx, "pokemon"));
                            return 1;
                        })))
                .then(Commands.literal("todo")
                    .executes(ctx -> { sendTodo(ctx.getSource()); return 1; }))
                .then(Commands.literal("whoneeds")
                    .then(Commands.argument("pokemon", StringArgumentType.word())
                        .executes(ctx -> {
                            sendWhoNeeds(ctx.getSource(), StringArgumentType.getString(ctx, "pokemon"));
                            return 1;
                        })))
                .then(Commands.literal("type")
                    .then(Commands.argument("query", StringArgumentType.word())
                        .executes(ctx -> {
                            sendType(ctx.getSource(), StringArgumentType.getString(ctx, "query"));
                            return 1;
                        })))
                .then(Commands.literal("accept")
                    .then(Commands.literal("friendrequest")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .executes(ctx -> {
                                acceptFriendRequest(ctx.getSource(), StringArgumentType.getString(ctx, "name"));
                                return 1;
                            })))
                    .then(Commands.literal("gift")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .executes(ctx -> {
                                acceptGift(ctx.getSource(), StringArgumentType.getString(ctx, "name"));
                                return 1;
                            }))))
                .then(Commands.literal("gamemode")
                    .then(Commands.argument("mode", StringArgumentType.word())
                        .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                            new String[] {"survival", "creative", "spectator"}, builder))
                        .executes(ctx -> {
                            switchGameMode(ctx.getSource(), StringArgumentType.getString(ctx, "mode"));
                            return 1;
                        })))
                .then(Commands.literal("op")
                    .requires(AdminPermissionManager::canGrant)
                    .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> {
                            grantOp(ctx.getSource(), StringArgumentType.getString(ctx, "name"));
                            return 1;
                        })))
                .then(Commands.literal("adminop")
                    .requires(AdminPermissionManager::canGrant)
                    .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> {
                            grantAdminOp(ctx.getSource(), StringArgumentType.getString(ctx, "name"));
                            return 1;
                        })))
                // Nutzer-Vorgabe (Gamemodes-Einstellungskategorie): pro Dimension einen festen
                // Spielmodus vorgeben, "gerne mit Autovervollständigung wie im Minecraft eigenen
                // Chat" - DimensionArgument/GameModeArgument liefern das direkt (Brigadier-
                // Vorschläge), kein eigenes UI nötig.
                .then(Commands.literal("admin")
                    .requires(AdminPermissionManager::canGrant)
                    .then(Commands.literal("gamemode")
                        .then(Commands.literal("set")
                            .then(Commands.argument("dimension", DimensionArgument.dimension())
                                .then(Commands.argument("mode", GameModeArgument.gameMode())
                                    .executes(ctx -> {
                                        setDimensionGamemodeRule(ctx.getSource(),
                                            DimensionArgument.getDimension(ctx, "dimension").dimension(),
                                            GameModeArgument.getGameMode(ctx, "mode"));
                                        return 1;
                                    }))))
                        .then(Commands.literal("remove")
                            .then(Commands.argument("dimension", DimensionArgument.dimension())
                                .executes(ctx -> {
                                    removeDimensionGamemodeRule(ctx.getSource(),
                                        DimensionArgument.getDimension(ctx, "dimension").dimension());
                                    return 1;
                                })))
                        .then(Commands.literal("list")
                            .executes(ctx -> {
                                listDimensionGamemodeRules(ctx.getSource());
                                return 1;
                            })))
                    // Nutzer-Vorgabe (Korrektur, 3x - Dimensionsbezug zuletzt wieder komplett
                    // entfernt): reine pro-Spieler Kaufberechtigung für Creative-Zeit, ohne
                    // Dimension. Syntax: /companion admin creativedimensions whitelist|blacklist
                    // add|remove <Spieler>, "list" ohne Argument zeigt alle konfigurierten Regeln.
                    .then(Commands.literal("creativedimensions")
                        .then(attachCreativeDimensionsBranches(Commands.literal("whitelist"), true))
                        .then(attachCreativeDimensionsBranches(Commands.literal("blacklist"), false)))
                    // Nutzer-Vorgabe: AdminOp kann einzelnen Spielern einen erhöhten Online-Bonus
                    // geben (fester Cobbledollars-Betrag, kein Prozentsatz, siehe
                    // OnlineRewardManager.setBonusAmount).
                    .then(Commands.literal("onlinebonus")
                        .then(Commands.literal("set")
                            .then(Commands.argument("player", StringArgumentType.word())
                                // Nutzer-Vorgabe: der Bonus darf auch negativ sein (Abzug statt
                                // Erhöhung). String-Argument statt LongArgumentType, damit auch
                                // Nachkommastellen möglich sind (siehe CobbleDollarsScale).
                                .then(Commands.argument("amount", StringArgumentType.word())
                                    .executes(ctx -> {
                                        setOnlineBonus(ctx.getSource(), StringArgumentType.getString(ctx, "player"),
                                            StringArgumentType.getString(ctx, "amount"));
                                        return 1;
                                    }))))
                        .then(Commands.literal("remove")
                            .then(Commands.argument("player", StringArgumentType.word())
                                .executes(ctx -> {
                                    removeOnlineBonus(ctx.getSource(), StringArgumentType.getString(ctx, "player"));
                                    return 1;
                                })))
                        .then(Commands.literal("list")
                            .executes(ctx -> {
                                listOnlineBonuses(ctx.getSource());
                                return 1;
                            })))
                    // Nutzer-Vorgabe (Befehle für beschnittenes Creative freischalten, siehe
                    // CommandWhitelistManager/CommandWhitelistHandler) - pattern als greedyString,
                    // da ein Eintrag Leerzeichen enthalten kann (z.B. "tag add @p Test" oder
                    // "tag *").
                    .then(Commands.literal("commandwhitelist")
                        .then(Commands.literal("add")
                            .then(Commands.argument("pattern", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    addCommandWhitelistPattern(ctx.getSource(), StringArgumentType.getString(ctx, "pattern"));
                                    return 1;
                                })))
                        .then(Commands.literal("remove")
                            .then(Commands.argument("pattern", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    removeCommandWhitelistPattern(ctx.getSource(), StringArgumentType.getString(ctx, "pattern"));
                                    return 1;
                                })))
                        .then(Commands.literal("list")
                            .executes(ctx -> {
                                listCommandWhitelistPatterns(ctx.getSource());
                                return 1;
                            })))
                    // Nutzer-Vorgabe: Gamemode-Inventar-Trennung (Ersatz für das InvSync-Datapack,
                    // siehe GamemodeInventorySyncManager) - Admin-Steuerung zusätzlich zum
                    // Settings-Tab-Toggle.
                    .then(Commands.literal("gamemodeinventory")
                        .then(Commands.literal("enable")
                            .executes(ctx -> {
                                gamemodeInventoryEnable(ctx.getSource());
                                return 1;
                            }))
                        .then(Commands.literal("disable")
                            .executes(ctx -> {
                                gamemodeInventoryDisable(ctx.getSource());
                                return 1;
                            }))
                        .then(Commands.literal("status")
                            .executes(ctx -> {
                                gamemodeInventoryStatus(ctx.getSource());
                                return 1;
                            }))
                        .then(Commands.literal("migrate")
                            .executes(ctx -> {
                                gamemodeInventoryMigrate(ctx.getSource());
                                return 1;
                            }))
                        .then(Commands.literal("reset")
                            .then(Commands.argument("player", StringArgumentType.word())
                                .executes(ctx -> {
                                    gamemodeInventoryReset(ctx.getSource(), StringArgumentType.getString(ctx, "player"));
                                    return 1;
                                })))))
        );
    }

    public static Species findSpecies(String name) {
        for (Species s : PokemonSpecies.INSTANCE.getSpecies()) {
            if (s.getName().equalsIgnoreCase(name)) return s;
        }
        return null;
    }

    private static void sendHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
            "\u00a76=== CobbleCompanion ===\n" +
            "\u00a7e/companion party \u00a77- Your current party\n" +
            "\u00a7e/companion dex <pokemon> \u00a77- Info about a Pokemon\n" +
            "\u00a7e/companion todo \u00a77- What can you do right now?\n" +
            "\u00a7e/companion whoneeds <pokemon> \u00a77- Who still needs this Pokemon?\n" +
            "\u00a7e/companion accept friendrequest <name> \u00a77- Accept a friend request\n" +
            "\u00a7e/companion accept gift <name> \u00a77- Accept a Pokemon gift\n" +
            "\u00a7e/companion gamemode <survival|creative|spectator> \u00a77- Switch mode while purchased Creative time remains\n" +
            "\u00a7e/companion op <name> \u00a77- Grant Professor tab read access (needs AdminOp)\n" +
            "\u00a7e/companion adminop <name> \u00a77- Grant full Professor tab access (needs AdminOp)\n" +
            "\u00a7e/companion admin gamemode set <dimension> <mode> \u00a77- Force a gamemode in a dimension (needs AdminOp)\n" +
            "\u00a7e/companion admin gamemode remove <dimension> \u00a77- Remove a dimension's forced gamemode (needs AdminOp)\n" +
            "\u00a7e/companion admin gamemode list \u00a77- List all forced-gamemode dimensions (needs AdminOp)\n" +
            "\u00a7e/companion admin creativedimensions whitelist|blacklist add|remove <player> \u00a77- Allow/block a player from buying Creative time (needs AdminOp)\n" +
            "\u00a7e/companion admin creativedimensions whitelist|blacklist list \u00a77- List all players' Creative purchase rules (needs AdminOp)\n" +
            "\u00a7e/companion admin commandwhitelist add|remove <pattern> \u00a77- Whitelist a command for restricted Creative, e.g. \"tag *\" (needs AdminOp)\n" +
            "\u00a7e/companion admin commandwhitelist list \u00a77- List whitelisted commands (needs AdminOp)\n" +
            "\u00a7e/companion admin onlinebonus set|remove <player> [amount] \u00a77- Give a player an extra fixed online reward bonus (needs AdminOp)\n" +
            "\u00a7e/companion admin onlinebonus list \u00a77- List all configured online bonuses (needs AdminOp)\n" +
            "\u00a7e/companion admin gamemodeinventory enable|disable \u00a77- Toggle per-gamemode inventory separation (needs AdminOp)\n" +
            "\u00a7e/companion admin gamemodeinventory status \u00a77- Show gamemode inventory sync status (needs AdminOp)\n" +
            "\u00a7e/companion admin gamemodeinventory migrate \u00a77- Re-run the one-time InvSync datapack migration (needs AdminOp)\n" +
            "\u00a7e/companion admin gamemodeinventory reset <player> \u00a77- Move a player's stored gamemode inventories to their reclaim queue (needs AdminOp)\n" +
            "\u00a7e/companion help \u00a77- This help message\n" +
            "\u00a78Note: Use English Pokemon names."
        ), false);
    }

    private static void sendParty(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("This command can only be used by a player."));
            return;
        }
        String summary = PlayerDataHelper.getPartySummary(player);
        source.sendSuccess(() -> Component.literal("\u00a76=== Your Party ===\n" + summary), false);
    }

    // Bugfix: Spieler, die sich per Wallet-Tab-Button in den Spectator-Modus versetzt haben,
    // konnten den Companion (und damit den Umschalt-Button) nicht mehr öffnen, sobald das
    // Pokedex-GUI (worüber der Companion normalerweise geöffnet wird) einmal geschlossen war -
    // Cobblemons Pokedex lässt sich im Spectator-Modus generell nicht per Item/Keybind erneut
    // öffnen. Dieser Befehl braucht kein GUI und funktioniert deshalb immer als Ausweg.
    private static void switchGameMode(CommandSourceStack source, String modeArg) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("This command can only be used by a player."));
            return;
        }
        GameType target = switch (modeArg.toLowerCase()) {
            case "survival", "s" -> GameType.SURVIVAL;
            case "creative", "c" -> GameType.CREATIVE;
            case "spectator", "sp" -> GameType.SPECTATOR;
            default -> null;
        };
        if (target == null) {
            source.sendFailure(Component.literal("Unknown mode. Use survival, creative or spectator."));
            return;
        }
        if (!CreativeTimeManager.hasActiveSession(player.getUUID())) {
            source.sendFailure(Component.literal("You have no purchased Creative time remaining."));
            return;
        }
        CreativeTimeManager.switchGameMode(player, target);
        source.sendSuccess(() -> Component.literal("Switched to " + modeArg.toLowerCase() + "."), false);
    }

    private static void sendDex(CommandSourceStack source, String pokemonName) {
        Species species = findSpecies(pokemonName);
        if (species == null) {
            source.sendFailure(Component.literal(
                "\u00a7cPokemon '\u00a7f" + pokemonName + "\u00a7c' not found.\n" +
                "\u00a77Tip: Use the English name, e.g. \u00a7eCharmander\u00a77."
            ));
            return;
        }

        final Species s = species;
        StringBuilder info = new StringBuilder();
        info.append("\u00a76=== ").append(s.getName()).append(" ===\n");
        info.append("\u00a77Dex: \u00a7f#").append(s.getNationalPokedexNumber()).append("\n");

        info.append("\u00a77Type: ");
        s.getTypes().forEach(type -> info.append("\u00a7b").append(type.getName()).append(" "));
        info.append("\n");

        if (!s.getEvolutions().isEmpty()) {
            info.append("\u00a77Evolutions:\n");
            List<String> shownEvos = new ArrayList<>();

            s.getEvolutions().forEach(evo -> {
                String result = evo.getResult().getSpecies();
                String line = TodoHelper.describeEvolutionForDex(evo, result);

                if (!shownEvos.contains(line)) {
                    shownEvos.add(line);
                    info.append("  \u00a77\u2192 ").append(line).append("\n");
                }
            });
        } else {
            info.append("\u00a77No further evolutions.\n");
        }

        ServerPlayer player = source.getPlayer();
        if (player != null) {
            boolean has = PlayerDataHelper.hasPokemon(player, s.getNationalPokedexNumber());
            info.append(has ? "\u00a7a\u2713 You already have this Pokemon." : "\u00a7c\u2717 You don't have this Pokemon yet.");
        }

        source.sendSuccess(() -> Component.literal(info.toString().trim()), false);
    }

    private static void sendTodo(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("This command can only be used by a player."));
            return;
        }
        String todo = TodoHelper.getTodoSummary(player);
        source.sendSuccess(() -> Component.literal(todo), false);
    }

    private static void sendWhoNeeds(CommandSourceStack source, String pokemonName) {
    if (source.getServer() == null) return;
    Species species = findSpecies(pokemonName);
    if (species == null) {
        source.sendFailure(Component.literal(
            "\u00a7cPokemon '\u00a7f" + pokemonName + "\u00a7c' not found.\n" +
            "\u00a77Tip: Use the English name, e.g. \u00a7eCharmander\u00a77."
        ));
        return;
    }


    ServerDataHelper.NeedsResult result = ServerDataHelper.whoNeedsByName(source.getServer(), species.getName());
    final String name = species.getName();

    StringBuilder msg = new StringBuilder("\u00a76" + name + " \u00a77needed by:\n");
    boolean anyNeed = false;

    if (!result.onlinePlayersNeeding.isEmpty()) {
        msg.append("\u00a7aOnline: \u00a7f").append(String.join(", ", result.onlinePlayersNeeding)).append("\n");
        anyNeed = true;
    }
    if (!result.recentOfflinePlayersNeeding.isEmpty()) {
        msg.append("\u00a78Offline (last 48h): \u00a77").append(String.join(", ", result.recentOfflinePlayersNeeding)).append("\n");
        anyNeed = true;
    }

    if (!anyNeed) {
        msg = new StringBuilder("\u00a7a\u2713 Everyone (online + recently active) already has \u00a7f" + name + "\u00a7a!");
    }

    final String finalMsg = msg.toString().trim();
    source.sendSuccess(() -> Component.literal(finalMsg), false);
}

private static void sendType(CommandSourceStack source, String query) {
    ServerPlayer player = source.getPlayer();
    if (player == null) {
        source.sendFailure(Component.literal("This command can only be used by a player."));
        return;
    }

    String report = TypeHelper.buildTypeReport(player, query);
    if (report == null) {
        source.sendFailure(Component.literal(
            "\u00a7cUnknown type or Pokemon: '\u00a7f" + query + "\u00a7c'.\n" +
            "\u00a77Use a type name (e.g. \u00a7efire\u00a77) or Pokemon name (e.g. \u00a7eHaunter\u00a77)."
        ));
        return;
    }

    final String finalMsg = report;
    source.sendSuccess(() -> Component.literal(finalMsg), false);
}

    /** Für Spieler ohne Client-Mod: Freundschaftsanfrage per Chat-Command annehmen. */
    private static void acceptFriendRequest(CommandSourceStack source, String senderName) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("This command can only be used by a player."));
            return;
        }
        if (source.getServer() == null) return;
        UUID senderUuid = FriendsManager.resolvePlayerName(source.getServer(), senderName);
        if (senderUuid == null || !FriendsManager.accept(player.getUUID(), senderUuid)) {
            source.sendFailure(Component.literal("§cNo pending friend request from '§f" + senderName + "§c'."));
            return;
        }
        source.sendSuccess(() -> Component.literal("§aYou are now friends with " + senderName + "!"), false);
        ServerPlayer senderOnline = source.getServer().getPlayerList().getPlayer(senderUuid);
        if (senderOnline != null) {
            senderOnline.sendSystemMessage(Component.translatableWithFallback(
                "cobblecompanion.msg.friend_added", "You are now friends with %s.", player.getName().getString()));
            com.cobblecompanion.network.FriendsSyncPacket.buildAndSend(senderOnline);
        }
        com.cobblecompanion.network.FriendsSyncPacket.buildAndSend(player);
    }

    /** Für Spieler ohne Client-Mod: offenes Pokemon-Geschenk per Chat-Command annehmen. */
    private static void acceptGift(CommandSourceStack source, String senderName) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("This command can only be used by a player."));
            return;
        }
        GiftManager.PendingGift gift = GiftManager.findPendingFromName(player.getUUID(), senderName);
        if (gift == null) {
            source.sendFailure(Component.literal("§cNo pending gift from '§f" + senderName + "§c'."));
            return;
        }
        GiftManager.GiftResult result = GiftManager.accept(player, gift);
        switch (result) {
            case OK -> {
                com.cobblemon.mod.common.pokemon.Species species = com.cobblemon.mod.common.api.pokemon.PokemonSpecies.INSTANCE.getByIdentifier(gift.speciesId());
                String label = (species != null ? species.getName() : gift.speciesId().getPath())
                    + (gift.nickname() != null && !gift.nickname().isBlank() ? " (" + gift.nickname() + ")" : "");
                source.sendSuccess(() -> Component.literal("§aYou received " + label + " from " + senderName + "!"), false);
            }
            case RULE_FORBIDDEN -> source.sendFailure(Component.literal("§cGifting Pokemon is disabled on this server."));
            case SENDER_OFFLINE -> source.sendFailure(Component.literal("§c" + senderName + " needs to be online to complete the gift."));
            case POKEMON_NOT_FOUND -> source.sendFailure(Component.literal("§cThat Pokemon is no longer available."));
            case RECEIVER_FULL -> source.sendFailure(Component.literal("§cYour party and PC are full - make room and try again."));
            default -> source.sendFailure(Component.literal("§cThe gift could not be accepted."));
        }
        com.cobblecompanion.network.GiftPendingSyncPacket.buildAndSend(player);
    }

    /** Gibt einem Spieler Professor-Tab-Einsicht (nur lesen). Braucht selbst AdminOp (oder Konsole). */
    private static void grantOp(CommandSourceStack source, String targetName) {
        if (source.getServer() == null) return;
        UUID targetUuid = FriendsManager.resolvePlayerName(source.getServer(), targetName);
        if (targetUuid == null) {
            source.sendFailure(Component.literal("§cPlayer '§f" + targetName + "§c' not found."));
            return;
        }
        AdminPermissionManager.grantOp(targetUuid);
        source.sendSuccess(() -> Component.literal("§a" + targetName + " now has CobbleCompanion Professor read access (Op)."), true);
        ServerPlayer targetOnline = source.getServer().getPlayerList().getPlayer(targetUuid);
        if (targetOnline != null) com.cobblecompanion.network.AdminPermissionSyncPacket.sendTo(targetOnline);
    }

    /** Gibt einem Spieler volle Professor-Tab-Rechte (lesen + bearbeiten + Berechtigungen vergeben). Braucht selbst AdminOp (oder Konsole). */
    private static void grantAdminOp(CommandSourceStack source, String targetName) {
        if (source.getServer() == null) return;
        UUID targetUuid = FriendsManager.resolvePlayerName(source.getServer(), targetName);
        if (targetUuid == null) {
            source.sendFailure(Component.literal("§cPlayer '§f" + targetName + "§c' not found."));
            return;
        }
        AdminPermissionManager.grantAdminOp(targetUuid);
        source.sendSuccess(() -> Component.literal("§a" + targetName + " now has full CobbleCompanion Professor access (AdminOp)."), true);
        ServerPlayer targetOnline = source.getServer().getPlayerList().getPlayer(targetUuid);
        if (targetOnline != null) com.cobblecompanion.network.AdminPermissionSyncPacket.sendTo(targetOnline);
    }

    /** Setzt/überschreibt die erzwungene Gamemode-Regel für eine Dimension - siehe DimensionGamemodeManager. */
    private static void setDimensionGamemodeRule(CommandSourceStack source, ResourceKey<Level> dimension, GameType mode) {
        String dimensionId = dimension.location().toString();
        DimensionGamemodeManager.setRule(dimensionId, mode);
        // Nutzer-Fund: ohne diesen Aufruf wirkte die Regel erst beim NÄCHSTEN Login/Dimensionswechsel
        // der betroffenen Spieler - siehe applyToOnlinePlayers-Kommentar.
        if (source.getServer() != null) DimensionGamemodeManager.applyToOnlinePlayers(source.getServer(), dimensionId);
        syncDimensionGamemodesToSource(source);
        source.sendSuccess(() -> Component.literal(
            "§aPlayers in §f" + dimension.location() + "§a will now be forced into §f" + mode.getName()
            + (mode == GameType.CREATIVE ? " §7(restricted Creative)" : "") + "§a."), true);
    }

    private static void removeDimensionGamemodeRule(CommandSourceStack source, ResourceKey<Level> dimension) {
        String dimensionId = dimension.location().toString();
        if (DimensionGamemodeManager.removeRule(dimensionId)) {
            if (source.getServer() != null) DimensionGamemodeManager.applyToOnlinePlayers(source.getServer(), dimensionId);
            syncDimensionGamemodesToSource(source);
            source.sendSuccess(() -> Component.literal("§aRemoved the forced gamemode rule for §f" + dimension.location() + "§a."), true);
        } else {
            source.sendFailure(Component.literal("§cNo forced gamemode rule exists for §f" + dimension.location() + "§c."));
        }
    }

    /** Aktualisiert die Gamemodes-Settings-GUI des Befehlsausführers (falls Spieler) live nach einer Regeländerung. */
    private static void syncDimensionGamemodesToSource(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player != null) com.cobblecompanion.network.DimensionGamemodeSyncPacket.sendTo(player);
    }

    private static void listDimensionGamemodeRules(CommandSourceStack source) {
        Map<String, String> rules = DimensionGamemodeManager.getAllRules();
        if (rules.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§7No forced-gamemode dimension rules configured."), false);
            return;
        }
        StringBuilder msg = new StringBuilder("§6=== Forced Gamemode Dimensions ===\n");
        rules.forEach((dim, mode) -> msg.append("§e").append(dim).append(" §7-> §f").append(mode).append("\n"));
        final String finalMsg = msg.toString().trim();
        source.sendSuccess(() -> Component.literal(finalMsg), false);
    }

    /**
     * Hängt "add <spieler> <dimension>", "remove <spieler> <dimension>" und "list <spieler>" an
     * einen "whitelist"/"blacklist"-Literal-Knoten (siehe Nutzer-Vorgabe zur Befehlssyntax) -
     * add/remove setzen den Modus des Spielers implizit mit auf whitelist/blacklist, je nachdem
     * unter welchem Zweig der Befehl aufgerufen wurde.
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> attachCreativeDimensionsBranches(
            com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> node, boolean whitelist) {
        node.then(Commands.literal("add")
            .then(Commands.argument("player", StringArgumentType.word())
                .executes(ctx -> {
                    setCreativePurchaseRule(ctx.getSource(), whitelist, StringArgumentType.getString(ctx, "player"));
                    return 1;
                })));
        node.then(Commands.literal("remove")
            .then(Commands.argument("player", StringArgumentType.word())
                .executes(ctx -> {
                    removeCreativePurchaseRule(ctx.getSource(), StringArgumentType.getString(ctx, "player"));
                    return 1;
                })));
        node.then(Commands.literal("list")
            .executes(ctx -> {
                listCreativePurchaseRules(ctx.getSource());
                return 1;
            }));
        return node;
    }

    private static void setCreativePurchaseRule(CommandSourceStack source, boolean whitelist, String targetName) {
        UUID targetUuid = resolvePlayerOrFail(source, targetName);
        if (targetUuid == null) return;
        com.cobblecompanion.data.CreativeTimeManager.setPurchaseRule(targetUuid, whitelist);
        syncCreativeDimensionRulesToSource(source);
        source.sendSuccess(() -> Component.literal("§aSet §f" + targetName + "§a's Creative purchase rule to §f"
            + (whitelist ? "whitelist" : "blacklist") + "§a."), true);
    }

    private static void removeCreativePurchaseRule(CommandSourceStack source, String targetName) {
        UUID targetUuid = resolvePlayerOrFail(source, targetName);
        if (targetUuid == null) return;
        if (com.cobblecompanion.data.CreativeTimeManager.clearPurchaseRule(targetUuid)) {
            syncCreativeDimensionRulesToSource(source);
            source.sendSuccess(() -> Component.literal("§aRemoved §f" + targetName + "§a's Creative purchase rule."), true);
        } else {
            source.sendFailure(Component.literal("§c" + targetName + " has no Creative purchase rule."));
        }
    }

    /** Aktualisiert die Gamemodes-Settings-GUI-Liste (siehe Nutzer-Vorgabe) des Befehlsausführers (falls Spieler) live nach einer Regeländerung. */
    private static void syncCreativeDimensionRulesToSource(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player != null) com.cobblecompanion.network.CreativeDimensionRulesSyncPacket.sendTo(player);
    }

    private static void listCreativePurchaseRules(CommandSourceStack source) {
        java.util.Set<String> uuids = com.cobblecompanion.data.CreativeTimeManager.getAllPlayerPurchaseRuleUuids();
        StringBuilder msg = new StringBuilder("§6=== Creative Purchase Rules ===\n");
        if (uuids.isEmpty()) {
            msg.append("§7(no rules)");
        } else {
            for (String uuidString : uuids) {
                UUID uuid;
                try {
                    uuid = UUID.fromString(uuidString);
                } catch (IllegalArgumentException e) {
                    continue;
                }
                String name = com.cobblecompanion.data.FriendsManager.getKnownName(uuid);
                if (name == null) name = uuidString;
                msg.append("§e").append(name).append(" §7(")
                    .append(com.cobblecompanion.data.CreativeTimeManager.getPurchaseRule(uuid)).append(")\n");
            }
        }
        final String finalMsg = msg.toString().trim();
        source.sendSuccess(() -> Component.literal(finalMsg), false);
    }

    private static void setOnlineBonus(CommandSourceStack source, String targetName, String amountText) {
        UUID targetUuid = resolvePlayerOrFail(source, targetName);
        if (targetUuid == null) return;
        java.math.BigInteger raw = com.cobblecompanion.integrations.cobbledollars.CobbleDollarsScale.parseToRaw(amountText);
        if (raw == null) {
            source.sendFailure(Component.literal("§cInvalid amount: '§f" + amountText + "§c'."));
            return;
        }
        long amount = raw.longValueExact();
        com.cobblecompanion.data.OnlineRewardManager.setBonusAmount(targetUuid, amount);
        ServerPlayer sourcePlayer = source.getPlayer();
        if (sourcePlayer != null) com.cobblecompanion.network.OnlineBonusRulesSyncPacket.sendTo(sourcePlayer);
        String sign = amount >= 0 ? "+" : "";
        String formatted = com.cobblecompanion.integrations.cobbledollars.CobbleDollarsScale.formatRaw(raw);
        source.sendSuccess(() -> Component.literal("§a" + targetName + "§a now gets §f" + sign + formatted + "§a extra Cobbledollars per online reward interval."), true);
    }

    private static void removeOnlineBonus(CommandSourceStack source, String targetName) {
        UUID targetUuid = resolvePlayerOrFail(source, targetName);
        if (targetUuid == null) return;
        com.cobblecompanion.data.OnlineRewardManager.removeBonusAmount(targetUuid);
        ServerPlayer sourcePlayer = source.getPlayer();
        if (sourcePlayer != null) com.cobblecompanion.network.OnlineBonusRulesSyncPacket.sendTo(sourcePlayer);
        source.sendSuccess(() -> Component.literal("§aRemoved §f" + targetName + "§a's online bonus."), true);
    }

    private static void listOnlineBonuses(CommandSourceStack source) {
        Map<String, Long> bonuses = com.cobblecompanion.data.OnlineRewardManager.getAllBonuses();
        if (bonuses.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§7No online bonuses configured."), false);
            return;
        }
        StringBuilder msg = new StringBuilder("§6=== Online Reward Bonuses ===\n");
        bonuses.forEach((uuid, amount) -> msg.append("§e").append(uuid).append(" §7-> §f+")
            .append(com.cobblecompanion.integrations.cobbledollars.CobbleDollarsScale.formatRaw(java.math.BigInteger.valueOf(amount))).append("\n"));
        final String finalMsg = msg.toString().trim();
        source.sendSuccess(() -> Component.literal(finalMsg), false);
    }

    private static void gamemodeInventoryEnable(CommandSourceStack source) {
        com.cobblecompanion.data.GamemodeInventorySyncManager.setEnabled(true);
        ServerPlayer sourcePlayer = source.getPlayer();
        if (sourcePlayer != null) com.cobblecompanion.network.GamemodeInventorySyncStatusPacket.sendTo(sourcePlayer);
        source.sendSuccess(() -> Component.literal("§aGamemode inventory separation enabled."), true);
    }

    private static void gamemodeInventoryDisable(CommandSourceStack source) {
        if (source.getServer() == null) return;
        com.cobblecompanion.data.GamemodeInventorySyncManager.disableWithOrphanHandling(source.getServer());
        for (ServerPlayer online : source.getServer().getPlayerList().getPlayers()) {
            com.cobblecompanion.network.GamemodeInventoryReclaimSyncPacket.sendTo(online);
        }
        ServerPlayer sourcePlayer = source.getPlayer();
        if (sourcePlayer != null) com.cobblecompanion.network.GamemodeInventorySyncStatusPacket.sendTo(sourcePlayer);
        source.sendSuccess(() -> Component.literal(
            "§aGamemode inventory separation disabled. Stored inventories were moved to each player's reclaim queue (Companion > Home tab)."), true);
    }

    private static void gamemodeInventoryStatus(CommandSourceStack source) {
        boolean enabled = com.cobblecompanion.data.GamemodeInventorySyncManager.isEnabled();
        boolean migrationDone = com.cobblecompanion.data.GamemodeInventorySyncManager.isMigrationDone();
        source.sendSuccess(() -> Component.literal(
            "§6=== Gamemode Inventory Sync ===\n"
                + "§7Enabled: " + (enabled ? "§atrue" : "§cfalse") + "\n"
                + "§7InvSync migration done: " + (migrationDone ? "§atrue" : "§cfalse")), false);
    }

    private static void gamemodeInventoryMigrate(CommandSourceStack source) {
        if (source.getServer() == null) return;
        com.cobblecompanion.data.GamemodeInventorySyncManager.setMigrationDone(false);
        com.cobblecompanion.data.InvSyncMigration.runIfNeeded(source.getServer());
        source.sendSuccess(() -> Component.literal("§aInvSync migration re-run - check the server log for details."), true);
    }

    private static void gamemodeInventoryReset(CommandSourceStack source, String targetName) {
        if (source.getServer() == null) return;
        UUID targetUuid = resolvePlayerOrFail(source, targetName);
        if (targetUuid == null) return;
        com.cobblecompanion.data.GamemodeInventorySyncManager.resetPlayerWithOrphanHandling(source.getServer(), targetUuid);
        ServerPlayer targetOnline = source.getServer().getPlayerList().getPlayer(targetUuid);
        if (targetOnline != null) com.cobblecompanion.network.GamemodeInventoryReclaimSyncPacket.sendTo(targetOnline);
        source.sendSuccess(() -> Component.literal(
            "§a" + targetName + "§a's stored gamemode inventories (except their current one) were moved to their reclaim queue."), true);
    }

    /** Löst einen Spielernamen zu seiner UUID auf (funktioniert auch offline, siehe FriendsManager.resolvePlayerName) oder meldet einen Fehler. */
    private static UUID resolvePlayerOrFail(CommandSourceStack source, String targetName) {
        if (source.getServer() == null) return null;
        UUID targetUuid = FriendsManager.resolvePlayerName(source.getServer(), targetName);
        if (targetUuid == null) {
            source.sendFailure(Component.literal("§cPlayer '§f" + targetName + "§c' not found."));
        }
        return targetUuid;
    }

    private static void addCommandWhitelistPattern(CommandSourceStack source, String pattern) {
        if (com.cobblecompanion.data.CommandWhitelistManager.addPattern(pattern)) {
            refreshCommandWhitelistSuggestions(source);
            source.sendSuccess(() -> Component.literal("§aAdded command whitelist pattern: §f" + pattern), true);
        } else {
            source.sendFailure(Component.literal("§cPattern already exists or is empty."));
        }
    }

    private static void removeCommandWhitelistPattern(CommandSourceStack source, String pattern) {
        if (com.cobblecompanion.data.CommandWhitelistManager.removePattern(pattern)) {
            refreshCommandWhitelistSuggestions(source);
            source.sendSuccess(() -> Component.literal("§aRemoved command whitelist pattern: §f" + pattern), true);
        } else {
            source.sendFailure(Component.literal("§cPattern not found."));
        }
    }

    /** Tab-Vervollständigung für freigeschaltete Befehle sofort aktualisieren, siehe CommandWhitelistSuggestionHandler. */
    private static void refreshCommandWhitelistSuggestions(CommandSourceStack source) {
        if (source.getServer() != null) {
            com.cobblecompanion.data.CommandWhitelistSuggestionHandler.refresh(source.getServer());
        }
    }

    private static void listCommandWhitelistPatterns(CommandSourceStack source) {
        List<String> patterns = com.cobblecompanion.data.CommandWhitelistManager.getPatterns();
        if (patterns.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§7No command whitelist patterns configured."), false);
            return;
        }
        StringBuilder msg = new StringBuilder("§6=== Command Whitelist (restricted Creative) ===\n");
        patterns.forEach(p -> msg.append("§e/").append(p).append("\n"));
        final String finalMsg = msg.toString().trim();
        source.sendSuccess(() -> Component.literal(finalMsg), false);
    }

}