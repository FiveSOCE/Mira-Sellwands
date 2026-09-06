package gg.mira.sellwands;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;

final class CollectorBridge {
    private final Plugin owner;

    CollectorBridge(Plugin owner) {
        this.owner = owner;
    }

    boolean isCollector(Location location) {
        Object service = service();
        if (service == null || location == null) return false;
        try {
            Method method = service.getClass().getMethod("isCollector", Location.class);
            return (boolean) method.invoke(service, location);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    SaleResult sellAll(Player player, Location location, double multiplier) {
        Object service = service();
        if (service == null) return SaleResult.fail("MiraCollectors is currently unavailable.");
        try {
            Method method = service.getClass().getMethod("sellAll", Player.class, Location.class, double.class);
            Object result = method.invoke(service, player, location, multiplier);
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

    private Object service() {
        Plugin collectors = Bukkit.getPluginManager().getPlugin("MiraCollectors");
        if (collectors == null || !collectors.isEnabled()) return null;

        for (RegisteredServiceProvider<?> registration : Bukkit.getServicesManager().getRegistrations(collectors)) {
            if (registration == null || registration.getService() == null) continue;
            String name = registration.getService().getName();
            if (!name.endsWith("$CollectorsApi") && !name.endsWith(".CollectorsApi")) continue;
            Object provider = registration.getProvider();
            if (provider != null) return provider;
        }
        return null;
    }

    record SaleResult(boolean success, long units, double payout, String label, String message) {
        static SaleResult fail(String message) { return new SaleResult(false, 0L, 0D, "Items", message); }
    }
}
