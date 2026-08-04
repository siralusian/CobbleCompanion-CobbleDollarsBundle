package com.cobblecompanion.cobbledollarscustomnpcs.mixin;

import com.cobblecompanion.cobbledollarscustomnpcs.data.CustomNpcMerchantShopManager;
import fr.harmex.cobbledollars.common.utils.extensions.PlayerExtensionKt;
import fr.harmex.cobbledollars.common.world.item.trading.CobbleDollarsShopHolder;
import fr.harmex.cobbledollars.common.world.item.trading.shop.Shop;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Nutzer-Vorgabe: ein CustomNPC soll wahlweise die Funktion/das Kauf-GUI eines echten
 * CobbleMerchant bekommen, sein CustomNPCs-Aussehen aber behalten. Macht EntityNPCInterface per
 * Mixin direkt zu einem CobbleDollarsShopHolder (die 3 Getter/Setter-Paare, die das Interface
 * verlangt) - dadurch kann PlayerExtensionKt.openShop() (dieselbe Methode, die ein echter
 * CobbleMerchant in seinem eigenen mobInteract() aufruft, siehe javap-Analyse) den NPC 1:1 wie
 * einen echten Merchant behandeln, INKLUSIVE der eingebauten Angebots-Bearbeitung - kein eigenes
 * Options-Fenster nötig.
 *
 * Injiziert NICHT an HEAD von mobInteract(), sondern erst am Aufruf von RoleInterface.getType()
 * (per javap verifiziert: der einzige Aufruf dieser Methode in mobInteract(), unmittelbar vor
 * "if (role.getType()==0) role.interact(player) else say(...)") - an diesem Punkt hat CustomNPCs
 * bereits alle eigenen Vorrang-Prüfungen durchlaufen (Werkzeug in der Hand: Klon-/Wand-/Reit-/
 * Scripter-Tool -> eigener Editor bleibt UNANGETASTET, da dessen Rückgabe VOR diesem Punkt erfolgt;
 * außerdem Fraktion/Angriff/aktiver Dialog/Quest).
 *
 * Nutzer-Korrektur: NICHT canceln - solange man dem NPC keine eigene GUI-Rolle (Trader o.ä.) gibt,
 * hat er beim normalen Anklicken ohnehin kein konkurrierendes GUI, nur den eingestellten
 * Interaktionstext (DataAdvanced.getInteractLine(), "say()" im else-Zweig). Ein cancel() hätte
 * genau diesen Text unterdrückt - der Shop öffnet sich deshalb als reiner Zusatz-Effekt NEBEN dem
 * normalen CustomNPCs-Verhalten, nicht als dessen Ersatz.
 */
@Mixin(EntityNPCInterface.class)
public abstract class EntityNPCInterfaceMerchantMixin implements CobbleDollarsShopHolder {

    @Unique
    private Shop cobblecompanion$shop;
    @Unique
    private UUID cobblecompanion$merchantUuid;
    @Unique
    private final Set<Player> cobblecompanion$tradingPlayers = new HashSet<>();

    @Override
    public Shop getShop() {
        EntityNPCInterface self = (EntityNPCInterface) (Object) this;
        if (cobblecompanion$shop == null) {
            cobblecompanion$shop = CustomNpcMerchantShopManager.getShop(self.getUUID(), self.registryAccess());
        }
        return cobblecompanion$shop;
    }

    @Override
    public void setShop(Shop shop) {
        EntityNPCInterface self = (EntityNPCInterface) (Object) this;
        cobblecompanion$shop = shop;
        CustomNpcMerchantShopManager.setShop(self.getUUID(), shop, self.registryAccess());
    }

    @Override
    public UUID getMerchantUUID() {
        if (cobblecompanion$merchantUuid == null) {
            cobblecompanion$merchantUuid = ((EntityNPCInterface) (Object) this).getUUID();
        }
        return cobblecompanion$merchantUuid;
    }

    @Override
    public void setMerchantUUID(UUID uuid) {
        cobblecompanion$merchantUuid = uuid;
    }

    @Override
    public Set<Player> getTradingPlayers() {
        return cobblecompanion$tradingPlayers;
    }

    @Override
    public void setTradingPlayers(Set<Player> players) {
        cobblecompanion$tradingPlayers.clear();
        cobblecompanion$tradingPlayers.addAll(players);
    }

    @Inject(
        method = "mobInteract",
        at = @At(value = "INVOKE", target = "Lnoppes/npcs/roles/RoleInterface;getType()I"))
    private void cobblecompanion$openMerchantShop(Player player, net.minecraft.world.InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        EntityNPCInterface self = (EntityNPCInterface) (Object) this;
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (!CustomNpcMerchantShopManager.isEnabled(self.getUUID())) return;

        PlayerExtensionKt.openShop(serverPlayer, this);
        cobblecompanion$tradingPlayers.add(player);
    }
}
