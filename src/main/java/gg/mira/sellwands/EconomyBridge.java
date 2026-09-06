package gg.mira.sellwands;

import org.bukkit.entity.Player;

interface EconomyBridge {
    boolean available();
    boolean deposit(Player player, double amount);
    boolean withdraw(Player player, double amount);
}
