package gg.mira.sellwands;

import com.mira.shop.MiraShopPlugin;
import com.mira.shop.model.ShopItem;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

final class MiraShopBridge implements ShopBridge {
    private final MiraShopPlugin shop;

    MiraShopBridge(JavaPlugin plugin) {
        var raw = Bukkit.getPluginManager().getPlugin("MiraShop");
        this.shop = raw instanceof MiraShopPlugin miraShop && raw.isEnabled() ? miraShop : null;
    }

    @Override
    public boolean available() {
        return shop != null;
    }

    @Override
    public Match match(ItemStack stack) {
        if (shop == null || stack == null || stack.getType().isAir()) return null;

        ShopItem generic = null;
        for (var section : shop.catalog().sections()) {
            for (ShopItem item : section.items()) {
                if (!item.canSell() || item.material() != stack.getType()) continue;
                if (item.customTemplate() && shop.catalog().matches(stack, item)) {
                    return new Match(item, shop.sales().sellPrice(item));
                }
                if (!item.customTemplate() && generic == null) generic = item;
            }
        }

        if (generic == null || !isPlainGenericStack(stack)) return null;
        return new Match(generic, shop.sales().sellPrice(generic));
    }

    @Override
    public void recordSell(Object token, int units, double money) {
        if (shop == null || !(token instanceof ShopItem item)) return;
        shop.stats().recordSell(item, units, money);
    }

    private boolean isPlainGenericStack(ItemStack stack) {
        ItemStack one = stack.clone();
        one.setAmount(1);
        return one.isSimilar(new ItemStack(stack.getType()));
    }
}
