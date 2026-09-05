package gg.mira.sellwands;

import com.mira.core.api.MiraCore;
import com.mira.core.api.MiraCoreProvider;
import com.mira.core.api.ModuleHealth;
import com.mira.shop.MiraShopPlugin;
import com.mira.shop.model.ShopItem;
import gg.mira.sellwands.api.event.SellWandSaleEvent;
import net.kyori.adventure.text.Component;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class MiraSellWandsPlugin extends JavaPlugin implements Listener {
    private NamespacedKey wandKey;
    private NamespacedKey usesKey;
    private NamespacedKey multiplierKey;
    private NamespacedKey serialKey;
    private NamespacedKey tierKey;

    private MiraCore core;
    private MiraShopPlugin shop;
    private Economy economy;
    private SellWandsApi api;

    private final Map<UUID, Long> lastUse = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        core = MiraCoreProvider.require();
        wandKey = new NamespacedKey(this, "sell_wand");
        usesKey = new NamespacedKey(this, "uses");
        multiplierKey = new NamespacedKey(this, "multiplier");
        serialKey = new NamespacedKey(this, "serial");
        tierKey = new NamespacedKey(this, "tier");

        var shopPlugin = Bukkit.getPluginManager().getPlugin("MiraShop");
        if (!(shopPlugin instanceof MiraShopPlugin miraShop)) {
            throw new IllegalStateException("MiraShop is required but was not available.");
        }
        shop = miraShop;

        var economyRegistration = getServer().getServicesManager().getRegistration(Economy.class);
        economy = economyRegistration == null ? null : economyRegistration.getProvider();

        api = new SellWandsApiImpl();
        getServer().getServicesManager().register(SellWandsApi.class, api, this, ServicePriority.Normal);
        core.services().register(SellWandsApi.class, api);
        core.modules().register(this, "MiraSellWands");
        core.modules().setHealth(this,
                economy == null ? ModuleHealth.DEGRADED : ModuleHealth.HEALTHY,
                economy == null
                        ? "Vault is present but no economy provider is currently registered"
                        : "Transactional container selling, MiraShop pricing and audited wand identity ready");

        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("MiraSellWands v" + getPluginMeta().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        if (core != null) {
            if (api != null) core.services().unregister(SellWandsApi.class, api);
            core.modules().unregister(this);
        }
        lastUse.clear();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length < 3 || !args[0].equalsIgnoreCase("give")) {
            msg(sender, "&eUsage: /sellwand give <player> <uses|-1> [multiplier]");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            msg(sender, "&cPlayer not online.");
            return true;
        }

        int uses;
        double multiplier = 1D;
        String tier = "CUSTOM";

        WandTier configured = tier(args[2]).orElse(null);
        if (configured != null) {
            uses = configured.uses();
            multiplier = configured.multiplier();
            tier = configured.id();
            if (args.length >= 4) {
                msg(sender, "&cTiered wands use their configured multiplier. Omit the multiplier argument.");
                return true;
            }
        } else {
            try {
                uses = Integer.parseInt(args[2]);
                if (args.length >= 4) multiplier = Double.parseDouble(args[3]);
            } catch (NumberFormatException exception) {
                msg(sender, "&cUnknown tier or invalid uses/multiplier.");
                return true;
            }
        }

        if (uses != -1 && uses <= 0) {
            msg(sender, "&cUses must be -1 for unlimited or a positive number.");
            return true;
        }

        double maxMultiplier = Math.max(1D, getConfig().getDouble("wand.max-multiplier", 100D));
        if (!Double.isFinite(multiplier) || multiplier <= 0D || multiplier > maxMultiplier) {
            msg(sender, "&cMultiplier must be finite, above 0 and at most " + maxMultiplier + "x.");
            return true;
        }

        ItemStack wand = createWand(uses, multiplier, tier);
        String serial = serial(wand);
        Map<Integer, ItemStack> leftovers = target.getInventory().addItem(wand);
        leftovers.values().forEach(item -> target.getWorld().dropItemNaturally(target.getLocation(), item));

        core.audit().record("MiraSellWands", "WAND_GRANTED",
                sender instanceof Player player ? player.getUniqueId() : null,
                sender.getName(), serial, "Sell wand granted",
                Map.of(
                        "target", target.getUniqueId().toString(),
                        "targetName", target.getName(),
                        "uses", Integer.toString(uses),
                        "multiplier", Double.toString(multiplier),
                        "tier", tier
                ));

        msg(sender, "&aSell wand given to &f" + target.getName() + "&a. Serial &f" + shortSerial(serial) + "&a.");
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return complete(args[0], List.of("give"));
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return complete(args[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            List<String> values = new ArrayList<>(tierIds());
            values.addAll(List.of("1", "5", "10", "25", "50", "-1"));
            return complete(args[2], values);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            return complete(args[3], List.of("1", "1.25", "1.5", "2", "3"));
        }
        return List.of();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        ItemStack wand = event.getItem();
        if (!isWand(wand) || !event.getPlayer().hasPermission("mirasellwands.use")) return;
        if (!(event.getClickedBlock().getState() instanceof Container container)) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        if (economy == null) {
            msg(player, "&cNo Vault economy provider is currently available.");
            return;
        }

        long now = System.currentTimeMillis();
        long cooldown = Math.max(0L, getConfig().getLong("wand.use-cooldown-millis", 250L));
        long previous = lastUse.getOrDefault(player.getUniqueId(), 0L);
        if (cooldown > 0L && now - previous < cooldown) return;
        lastUse.put(player.getUniqueId(), now);

        ItemMeta meta = wand.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        int uses = pdc.getOrDefault(usesKey, PersistentDataType.INTEGER, 1);
        double multiplier = pdc.getOrDefault(multiplierKey, PersistentDataType.DOUBLE, 1D);

        if (uses != -1 && uses <= 0) {
            msg(player, "&cThat sell wand has no uses remaining.");
            return;
        }

        double maxMultiplier = Math.max(1D, getConfig().getDouble("wand.max-multiplier", 100D));
        if (!Double.isFinite(multiplier) || multiplier <= 0D || multiplier > maxMultiplier) {
            msg(player, "&cThat sell wand has invalid multiplier data.");
            return;
        }

        String serial = ensureSerial(wand);
        SalePlan plan = planSale(container.getInventory());
        if (plan.units() <= 0) {
            msg(player, "&eThat container has no safely sellable MiraShop items.");
            return;
        }

        double payout = safeMultiply(plan.baseMoney(), multiplier);
        if (payout < 0D) {
            msg(player, "&cThe calculated payout was invalid. Nothing was changed.");
            return;
        }

        EconomyResponse deposit = economy.depositPlayer(player, payout);
        if (deposit == null || !deposit.transactionSuccess()) {
            msg(player, "&cThe economy rejected the payout. Nothing was removed from the container.");
            return;
        }

        try {
            container.getInventory().setContents(plan.resultContents());
        } catch (RuntimeException exception) {
            EconomyResponse rollback = economy.withdrawPlayer(player, payout);
            if (rollback == null || !rollback.transactionSuccess()) {
                getLogger().severe("CRITICAL: Could not roll back $" + payout + " after a failed container mutation for "
                        + player.getUniqueId() + " wand " + serial);
            }
            getLogger().warning("Container mutation failed after payout attempt for " + player.getName() + ": "
                    + exception.getMessage());
            msg(player, "&cThe container changed unexpectedly. The sale was cancelled.");
            return;
        }

        for (Map.Entry<ShopItem, SaleLine> entry : plan.lines().entrySet()) {
            shop.stats().recordSell(entry.getKey(), entry.getValue().units(),
                    entry.getValue().baseMoney() * multiplier);
        }

        decrement(wand);

        Location saleLocation = event.getClickedBlock().getLocation();
        Bukkit.getPluginManager().callEvent(new SellWandSaleEvent(
                player, serial, plan.units(), plan.baseMoney(), multiplier, payout, saleLocation));
        CosmeticsBridge.play(player, "sellwand_sale", saleLocation);

        if (getConfig().getBoolean("audit.successful-sales", true)) {
            core.audit().record("MiraSellWands", "WAND_SALE",
                    player.getUniqueId(), player.getName(), serial, "Container sold with sell wand",
                    Map.of(
                            "units", Integer.toString(plan.units()),
                            "baseMoney", Double.toString(plan.baseMoney()),
                            "multiplier", Double.toString(multiplier),
                            "payout", Double.toString(payout),
                            "world", event.getClickedBlock().getWorld().getName(),
                            "x", Integer.toString(event.getClickedBlock().getX()),
                            "y", Integer.toString(event.getClickedBlock().getY()),
                            "z", Integer.toString(event.getClickedBlock().getZ())
                    ));
        }

        msg(player, "&aSold &f" + plan.units() + " &aitems for &f$"
                + String.format(Locale.US, "%.2f", payout)
                + "&a using wand &f" + shortSerial(serial) + "&a.");
    }

    private SalePlan planSale(Inventory inventory) {
        ItemStack[] original = inventory.getContents();
        ItemStack[] result = cloneContents(original);
        int units = 0;
        double total = 0D;
        Map<ShopItem, SaleLine> lines = new LinkedHashMap<>();

        for (int slot = 0; slot < original.length; slot++) {
            ItemStack stack = original[slot];
            if (stack == null || stack.getType().isAir()) continue;

            ShopItem item = findSafeSellMatch(stack);
            if (item == null || !item.canSell()) continue;

            double unitPrice = shop.sales().sellPrice(item);
            double money = safeMultiply(unitPrice, stack.getAmount());
            if (money < 0D) continue;

            int amount = stack.getAmount();
            units += amount;
            total += money;
            if (!Double.isFinite(total) || total < 0D) return SalePlan.empty(original.length);

            SaleLine previous = lines.get(item);
            lines.put(item, previous == null
                    ? new SaleLine(amount, money)
                    : new SaleLine(previous.units() + amount, previous.baseMoney() + money));
            result[slot] = null;
        }

        return new SalePlan(units, total, result, Map.copyOf(lines));
    }

    private ShopItem findSafeSellMatch(ItemStack stack) {
        ShopItem generic = null;

        for (var section : shop.catalog().sections()) {
            for (ShopItem item : section.items()) {
                if (!item.canSell() || item.material() != stack.getType()) continue;
                if (item.customTemplate() && shop.catalog().matches(stack, item)) return item;
                if (!item.customTemplate() && generic == null) generic = item;
            }
        }

        return generic != null && isPlainGenericStack(stack) ? generic : null;
    }

    private boolean isPlainGenericStack(ItemStack stack) {
        ItemStack one = stack.clone();
        one.setAmount(1);
        return one.isSimilar(new ItemStack(stack.getType()));
    }

    private ItemStack createWand(int uses, double multiplier) {
        return createWand(uses, multiplier, "CUSTOM");
    }

    private ItemStack createWand(int uses, double multiplier, String tier) {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();
        String serial = UUID.randomUUID().toString();

        meta.customName(Component.text("Sell Wand"));
        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(usesKey, PersistentDataType.INTEGER, uses);
        meta.getPersistentDataContainer().set(multiplierKey, PersistentDataType.DOUBLE, multiplier);
        meta.getPersistentDataContainer().set(serialKey, PersistentDataType.STRING, serial);
        meta.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING, tier == null ? "CUSTOM" : tier);
        applyLore(meta, uses, multiplier, tier == null ? "CUSTOM" : tier);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isWand(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        Byte value = item.getItemMeta().getPersistentDataContainer().get(wandKey, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    private String ensureSerial(ItemStack wand) {
        ItemMeta meta = wand.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String serial = pdc.get(serialKey, PersistentDataType.STRING);
        if (serial != null && !serial.isBlank()) return serial;

        serial = UUID.randomUUID().toString();
        pdc.set(serialKey, PersistentDataType.STRING, serial);
        wand.setItemMeta(meta);
        return serial;
    }

    private String serial(ItemStack wand) {
        if (!isWand(wand)) return "";
        return wand.getItemMeta().getPersistentDataContainer()
                .getOrDefault(serialKey, PersistentDataType.STRING, "");
    }

    private void decrement(ItemStack wand) {
        ItemMeta meta = wand.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        int uses = pdc.getOrDefault(usesKey, PersistentDataType.INTEGER, 1);
        if (uses == -1) return;

        uses--;
        if (uses <= 0) {
            wand.setAmount(0);
            return;
        }

        pdc.set(usesKey, PersistentDataType.INTEGER, uses);
        double multiplier = pdc.getOrDefault(multiplierKey, PersistentDataType.DOUBLE, 1D);
        String tier = pdc.getOrDefault(tierKey, PersistentDataType.STRING, "CUSTOM");
        applyLore(meta, uses, multiplier, tier);
        wand.setItemMeta(meta);
    }

    private void applyLore(ItemMeta meta, int uses, double multiplier, String tier) {
        meta.lore(List.of(
                Component.text("Right-click a container to sell its safe MiraShop contents."),
                Component.text("Tier: " + prettyTier(tier)),
                Component.text("Uses: " + (uses == -1 ? "Unlimited" : uses)),
                Component.text("Multiplier: " + String.format(Locale.US, "%.2f", multiplier) + "x")
        ));
    }

    private Optional<WandTier> tier(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        String id = raw.trim().toUpperCase(Locale.ROOT);
        String base = "wand.tiers." + id.toLowerCase(Locale.ROOT);
        if (!getConfig().isConfigurationSection(base)) return Optional.empty();
        int uses = getConfig().getInt(base + ".uses", 50);
        double multiplier = getConfig().getDouble(base + ".multiplier", 1D);
        if (uses != -1 && uses <= 0) return Optional.empty();
        double max = Math.max(1D, getConfig().getDouble("wand.max-multiplier", 100D));
        if (!Double.isFinite(multiplier) || multiplier <= 0D || multiplier > max) return Optional.empty();
        return Optional.of(new WandTier(id, uses, multiplier));
    }

    private List<String> tierIds() {
        var section = getConfig().getConfigurationSection("wand.tiers");
        if (section == null) return List.of();
        return section.getKeys(false).stream().map(String::toUpperCase).sorted().toList();
    }

    private static String prettyTier(String raw) {
        if (raw == null || raw.isBlank()) return "Custom";
        String lower = raw.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static ItemStack[] cloneContents(ItemStack[] source) {
        ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) copy[i] = source[i] == null ? null : source[i].clone();
        return copy;
    }

    private static double safeMultiply(double price, double amount) {
        if (!Double.isFinite(price) || price < 0D || !Double.isFinite(amount) || amount < 0D) return -1D;
        double total = price * amount;
        return Double.isFinite(total) && total >= 0D ? total : -1D;
    }

    private static String shortSerial(String serial) {
        if (serial == null || serial.isBlank()) return "unknown";
        return serial.substring(0, Math.min(8, serial.length()));
    }

    private static List<String> complete(String prefix, Collection<String> values) {
        String lower = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower))
                .distinct().sorted().toList();
    }

    private void msg(CommandSender sender, String raw) {
        core.messages().send(sender, raw);
    }

    public interface SellWandsApi {
        ItemStack create(int uses, double multiplier);
        ItemStack createTier(String tier);
        boolean isSellWand(ItemStack item);
        int uses(ItemStack item);
        double multiplier(ItemStack item);
        Optional<String> serial(ItemStack item);
    }

    private final class SellWandsApiImpl implements SellWandsApi {
        @Override
        public ItemStack create(int uses, double multiplier) {
            if (uses != -1 && uses <= 0) throw new IllegalArgumentException("uses must be -1 or positive");
            double maxMultiplier = Math.max(1D, getConfig().getDouble("wand.max-multiplier", 100D));
            if (!Double.isFinite(multiplier) || multiplier <= 0D || multiplier > maxMultiplier) {
                throw new IllegalArgumentException("invalid multiplier");
            }
            return createWand(uses, multiplier);
        }

        @Override
        public ItemStack createTier(String tier) {
            WandTier configured = MiraSellWandsPlugin.this.tier(tier)
                    .orElseThrow(() -> new IllegalArgumentException("unknown sell wand tier"));
            return createWand(configured.uses(), configured.multiplier(), configured.id());
        }

        @Override public boolean isSellWand(ItemStack item) { return isWand(item); }

        @Override
        public int uses(ItemStack item) {
            if (!isWand(item)) return 0;
            return item.getItemMeta().getPersistentDataContainer()
                    .getOrDefault(usesKey, PersistentDataType.INTEGER, 1);
        }

        @Override
        public double multiplier(ItemStack item) {
            if (!isWand(item)) return 0D;
            return item.getItemMeta().getPersistentDataContainer()
                    .getOrDefault(multiplierKey, PersistentDataType.DOUBLE, 1D);
        }

        @Override
        public Optional<String> serial(ItemStack item) {
            if (!isWand(item)) return Optional.empty();
            String value = MiraSellWandsPlugin.this.serial(item);
            return value.isBlank() ? Optional.empty() : Optional.of(value);
        }
    }

    private record WandTier(String id, int uses, double multiplier) { }

    private record SaleLine(int units, double baseMoney) { }

    private record SalePlan(int units, double baseMoney, ItemStack[] resultContents,
                            Map<ShopItem, SaleLine> lines) {
        static SalePlan empty(int size) {
            return new SalePlan(0, 0D, new ItemStack[size], Map.of());
        }
    }
}
