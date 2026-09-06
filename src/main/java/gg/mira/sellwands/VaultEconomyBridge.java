package gg.mira.sellwands;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

final class VaultEconomyBridge implements EconomyBridge {
    private final Economy economy;

    VaultEconomyBridge(JavaPlugin plugin) {
        var registration = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        this.economy = registration == null ? null : registration.getProvider();
    }

    @Override
    public boolean available() {
        return economy != null;
    }

    @Override
    public boolean deposit(Player player, double amount) {
        if (economy == null || player == null) return false;
        EconomyResponse response = economy.depositPlayer(player, amount);
        return response != null && response.transactionSuccess();
    }

    @Override
    public boolean withdraw(Player player, double amount) {
        if (economy == null || player == null) return false;
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        return response != null && response.transactionSuccess();
    }
}
