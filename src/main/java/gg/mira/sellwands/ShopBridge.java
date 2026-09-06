package gg.mira.sellwands;

import org.bukkit.inventory.ItemStack;

interface ShopBridge {
    boolean available();
    Match match(ItemStack stack);
    void recordSell(Object token, int units, double money);

    record Match(Object token, double unitPrice) { }
}
