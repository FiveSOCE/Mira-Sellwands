package gg.mira.sellwands;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;

final class CollectorBridge {
    private final Plugin owner;

    CollectorBridge(Plugin owner) {
        this.owner = owner;
    }

    boolean isCollector(Location location) {
        if (location == null || location.getWorld() == null) return false;

        // Fast, classloader-independent identity check. MiraCollectors stores this
        // PDC key on every placed collector barrel.
        if (location.getBlock().getState() instanceof TileState tile) {
            NamespacedKey key = new NamespacedKey("miracollectors", "collector_id");
            if (tile.getPersistentDataContainer().has(key, PersistentDataType.STRING)) return true;
        }

        ServiceHandle handle = service();
        if (handle == null) return false;
        try {
            Method method = handle.serviceClass().getMethod("isCollector", Location.class);
            return (boolean) method.invoke(handle.provider(), location);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            owner.getLogger().warning("MiraCollectors identity bridge failed: "
                    + exception.getClass().getSimpleName()
                    + (exception.getMessage() == null ? "" : " - " + exception.getMessage()));
            return false;
        }
    }

    SaleResult sellAll(Player player, Location location, double multiplier) {
        ServiceHandle handle = service();
        if (handle == null) return SaleResult.fail("MiraCollectors is currently unavailable.");
        try {
            Method method = handle.serviceClass().getMethod("sellAll", Player.class, Location.class, double.class);
            Object result = method.invoke(handle.provider(), player, location, multiplier);
            if (result == null) return SaleResult.fail("Collector sale returned no result.");

            boolean success = (boolean) result.getClass().getMethod("success").invoke(result);
            long units = ((Number) result.getClass().getMethod("units").invoke(result)).longValue();
            double payout = ((Number) result.getClass().getMethod("payout").invoke(result)).doubleValue();
            String label = String.valueOf(result.getClass().getMethod("label").invoke(result));
            String message = String.valueOf(result.getClass().getMethod("message").invoke(result));
            return new SaleResult(success, units, payout, label == null ? "Items" : label, message == null ? "" : message);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            owner.getLogger().warning("MiraCollectors bridge failed: " + exception.getClass().getSimpleName()
                    + (exception.getMessage() == null ? "" : " - " + exception.getMessage()));
            return SaleResult.fail("Collector sale integration failed safely.");
        }
    }

    private ServiceHandle service() {
        Plugin collectors = Bukkit.getPluginManager().getPlugin("MiraCollectors");
        if (collectors == null || !collectors.isEnabled()) return null;

        for (RegisteredServiceProvider<?> registration : Bukkit.getServicesManager().getRegistrations(collectors)) {
            if (registration == null || registration.getService() == null) continue;
            Class<?> serviceClass = registration.getService();
            String name = serviceClass.getName();
            if (!name.endsWith("$CollectorsApi") && !name.endsWith(".CollectorsApi")) continue;
            Object provider = registration.getProvider();
            if (provider != null) return new ServiceHandle(provider, serviceClass);
        }
        return null;
    }

    private record ServiceHandle(Object provider, Class<?> serviceClass) { }

    record SaleResult(boolean success, long units, double payout, String label, String message) {
        static SaleResult fail(String message) { return new SaleResult(false, 0L, 0D, "Items", message); }
    }
}
