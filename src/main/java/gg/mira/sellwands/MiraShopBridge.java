package gg.mira.sellwands;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.Collection;

final class MiraShopBridge implements ShopBridge {
    private Plugin shop;

    MiraShopBridge(JavaPlugin plugin) {
        refresh();
    }

    @Override
    public boolean available() {
        refresh();
        return shop != null && shop.isEnabled();
    }

    @Override
    public Match match(ItemStack stack) {
        if (!available() || stack == null || stack.getType().isAir()) return null;

        try {
            Object catalog = shop.getClass().getMethod("catalog").invoke(shop);
            Object sales = shop.getClass().getMethod("sales").invoke(shop);
            Collection<?> sections = (Collection<?>) catalog.getClass().getMethod("sections").invoke(catalog);

            Object generic = null;
            for (Object section : sections) {
                Collection<?> items = (Collection<?>) section.getClass().getMethod("items").invoke(section);
                for (Object item : items) {
                    boolean canSell = (boolean) item.getClass().getMethod("canSell").invoke(item);
                    Material material = (Material) item.getClass().getMethod("material").invoke(item);
                    if (!canSell || material != stack.getType()) continue;

                    boolean custom = (boolean) item.getClass().getMethod("customTemplate").invoke(item);
                    if (custom) {
                        boolean matches = (boolean) catalog.getClass()
                                .getMethod("matches", ItemStack.class, item.getClass())
                                .invoke(catalog, stack, item);
                        if (matches) return new Match(item, sellPrice(sales, item));
                    } else if (generic == null) {
                        generic = item;
                    }
                }
            }

            if (generic == null || !isPlainGenericStack(stack)) return null;
            return new Match(generic, sellPrice(sales, generic));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    @Override
    public void recordSell(Object token, int units, double money) {
        if (!available() || token == null) return;
        try {
            Object stats = shop.getClass().getMethod("stats").invoke(shop);
            Method record = stats.getClass().getMethod("recordSell", token.getClass(), int.class, double.class);
            record.invoke(stats, token, units, money);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private double sellPrice(Object sales, Object item) throws ReflectiveOperationException {
        return ((Number) sales.getClass().getMethod("sellPrice", item.getClass()).invoke(sales, item)).doubleValue();
    }

    private void refresh() {
        Plugin candidate = Bukkit.getPluginManager().getPlugin("MiraShop");
        shop = candidate != null && candidate.isEnabled() ? candidate : null;
    }

    private boolean isPlainGenericStack(ItemStack stack) {
        ItemStack one = stack.clone();
        one.setAmount(1);
        return one.isSimilar(new ItemStack(stack.getType()));
    }
}
