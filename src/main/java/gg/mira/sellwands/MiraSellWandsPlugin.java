package gg.mira.sellwands;

import net.milkbowl.vault.economy.Economy;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Container;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.*;

public final class MiraSellWandsPlugin extends JavaPlugin implements Listener {
    private static final String PREFIX = "&5&lMira &8>> &r";
    private NamespacedKey wandKey, usesKey, multiplierKey, serialKey;
    private Economy economy;

    @Override public void onEnable() {
        wandKey = new NamespacedKey(this, "sell_wand");
        usesKey = new NamespacedKey(this, "uses");
        multiplierKey = new NamespacedKey(this, "multiplier");
        serialKey = new NamespacedKey(this, "serial");
        var reg = getServer().getServicesManager().getRegistration(Economy.class);
        economy = reg == null ? null : reg.getProvider();
        getServer().getPluginManager().registerEvents(this, this);
        if (Bukkit.getPluginManager().getPlugin("MiraShop") == null) getLogger().warning("MiraShop was not found. Sell wands cannot price container contents until MiraShop is installed.");
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 3 || !args[0].equalsIgnoreCase("give")) { msg(sender, "&cUsage: /sellwand give <player> <uses|-1> [multiplier]"); return true; }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { msg(sender, "&cPlayer not online."); return true; }
        int uses;
        double multiplier = 1D;
        try { uses = Integer.parseInt(args[2]); if (args.length >= 4) multiplier = Double.parseDouble(args[3]); }
        catch (NumberFormatException ex) { msg(sender, "&cInvalid uses or multiplier."); return true; }
        target.getInventory().addItem(createWand(uses, Math.max(0D, multiplier)));
        msg(sender, "&aSell wand given to " + target.getName() + ".");
        return true;
    }

    private ItemStack createWand(int uses, double multiplier) {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();
        meta.customName(Component.text("Sell Wand"));
        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte)1);
        meta.getPersistentDataContainer().set(usesKey, PersistentDataType.INTEGER, uses);
        meta.getPersistentDataContainer().set(multiplierKey, PersistentDataType.DOUBLE, multiplier);
        meta.getPersistentDataContainer().set(serialKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        meta.lore(List.of(Component.text("Right-click a container to sell its sellable contents."), Component.text("Uses: " + (uses < 0 ? "Unlimited" : uses)), Component.text("Multiplier: " + multiplier + "x")));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        ItemStack wand = event.getItem();
        if (!isWand(wand) || !event.getPlayer().hasPermission("mirasellwands.use")) return;
        if (!(event.getClickedBlock().getState() instanceof Container container)) return;
        event.setCancelled(true);
        if (economy == null) { msg(event.getPlayer(), "&cNo economy provider is available."); return; }
        Plugin shop = Bukkit.getPluginManager().getPlugin("MiraShop");
        if (shop == null || !shop.isEnabled()) { msg(event.getPlayer(), "&cMiraShop is required for sell wand pricing."); return; }

        SellResult result;
        try { result = sellContainer(shop, container.getInventory()); }
        catch (ReflectiveOperationException ex) { getLogger().warning("MiraShop integration failed: " + ex.getMessage()); msg(event.getPlayer(), "&cCould not read MiraShop pricing."); return; }
        if (result.units <= 0) { msg(event.getPlayer(), "&eThat container has no sellable MiraShop items."); return; }

        ItemMeta meta = wand.getItemMeta();
        double multiplier = meta.getPersistentDataContainer().getOrDefault(multiplierKey, PersistentDataType.DOUBLE, 1D);
        double payout = result.money * multiplier;
        economy.depositPlayer(event.getPlayer(), payout);
        msg(event.getPlayer(), "&aSold &f" + result.units + " &aitems for &f$" + String.format(Locale.US, "%.2f", payout) + "&a.");
        decrement(wand);
    }

    private SellResult sellContainer(Plugin shop, Inventory inventory) throws ReflectiveOperationException {
        Object catalog = shop.getClass().getMethod("catalog").invoke(shop);
        Object sales = shop.getClass().getMethod("sales").invoke(shop);
        Object stats = shop.getClass().getMethod("stats").invoke(shop);
        Collection<?> sections = (Collection<?>) catalog.getClass().getMethod("sections").invoke(catalog);
        List<Object> items = new ArrayList<>();
        for (Object section : sections) items.addAll((Collection<?>) section.getClass().getMethod("items").invoke(section));
        int units = 0; double total = 0D;
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot]; if (stack == null || stack.getType().isAir()) continue;
            Object match = findMatch(items, stack); if (match == null) continue;
            boolean canSell = (boolean) match.getClass().getMethod("canSell").invoke(match); if (!canSell) continue;
            double unit = ((Number) sales.getClass().getMethod("sellPrice", match.getClass()).invoke(sales, match)).doubleValue();
            if (unit < 0D) continue;
            int amount = stack.getAmount(); double money = unit * amount;
            contents[slot] = null; units += amount; total += money;
            stats.getClass().getMethod("recordSell", match.getClass(), int.class, double.class).invoke(stats, match, amount, money);
        }
        inventory.setContents(contents);
        return new SellResult(units, total);
    }

    private Object findMatch(List<Object> items, ItemStack stack) throws ReflectiveOperationException {
        Object generic = null;
        for (Object item : items) {
            Material material = (Material) item.getClass().getMethod("material").invoke(item);
            if (material != stack.getType()) continue;
            boolean custom = (boolean) item.getClass().getMethod("customTemplate").invoke(item);
            if (custom) {
                ItemStack template = (ItemStack) item.getClass().getMethod("template").invoke(item);
                if (stack.isSimilar(template)) return item;
            } else if (generic == null) generic = item;
        }
        return generic;
    }

    private boolean isWand(ItemStack item) { return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE); }

    private void decrement(ItemStack wand) {
        ItemMeta meta = wand.getItemMeta();
        int uses = meta.getPersistentDataContainer().getOrDefault(usesKey, PersistentDataType.INTEGER, 1);
        if (uses < 0) return;
        uses--;
        if (uses <= 0) { wand.setAmount(0); return; }
        meta.getPersistentDataContainer().set(usesKey, PersistentDataType.INTEGER, uses);
        double multiplier = meta.getPersistentDataContainer().getOrDefault(multiplierKey, PersistentDataType.DOUBLE, 1D);
        meta.lore(List.of(Component.text("Right-click a container to sell its sellable contents."), Component.text("Uses: " + uses), Component.text("Multiplier: " + multiplier + "x")));
        wand.setItemMeta(meta);
    }

    private void msg(CommandSender sender, String raw) { sender.sendMessage(ChatColor.translateAlternateColorCodes('&', PREFIX + raw)); }
    private record SellResult(int units, double money) {}
}
