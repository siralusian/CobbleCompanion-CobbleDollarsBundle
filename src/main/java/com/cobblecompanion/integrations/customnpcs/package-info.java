/**
 * Server-seitige Integration für CustomNPCs-Unofficial-NeoForge (Trader-Rolle) - eigenes
 * Unterpaket, weil diese Klassen EntityNPCInterface/RoleTrader direkt importieren und deshalb NUR
 * geladen werden dürfen, wenn CustomNPCs installiert ist (siehe CobbleCompanion.onServerStarting,
 * ModAvailability.isCustomNpcsAvailable()). Der Mixin-Teil (integrations.customnpcs.mixin)
 * importiert zusätzlich Create-Typen (Lagerticker-Anbindung) und wird deshalb über eine eigene,
 * separat geladene Mixin-Config mit requiredMods=["create", "customnpcs"] geschützt (siehe
 * cobblecompanion_customnpcs.mixins.json), analog zu integrations.create.mixin.mobilepackages.
 */
package com.cobblecompanion.integrations.customnpcs;
