package gg.mira.sellwands.api.event;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class SellWandSaleEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String serial;
    private final int units;
    private final double baseMoney;
    private final double multiplier;
    private final double payout;
    private final Location containerLocation;

    public SellWandSaleEvent(Player player, String serial, int units, double baseMoney,
                             double multiplier, double payout, Location containerLocation) {
        this.player = player;
        this.serial = serial;
        this.units = units;
        this.baseMoney = baseMoney;
        this.multiplier = multiplier;
        this.payout = payout;
        this.containerLocation = containerLocation.clone();
    }

    public Player player() { return player; }
    public String serial() { return serial; }
    public int units() { return units; }
    public double baseMoney() { return baseMoney; }
    public double multiplier() { return multiplier; }
    public double payout() { return payout; }
    public Location containerLocation() { return containerLocation.clone(); }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
