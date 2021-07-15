package nl.pim16aap2.bigDoors.compatiblity;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.sk89q.worldguard.bukkit.WorldGuardPlugin;

import nl.pim16aap2.bigDoors.BigDoors;

/**
 * Compatibility hook for version 6 of WorldGuard.
 *
 * @see IProtectionCompat
 * @author Pim
 */
class WorldGuard6ProtectionCompat implements IProtectionCompat
{
    private final BigDoors plugin;
    private final WorldGuardPlugin worldGuard;
    private boolean success = false;
    private Method m;
    private static final ProtectionCompat compat = ProtectionCompat.WORLDGUARD;

    public WorldGuard6ProtectionCompat(BigDoors plugin)
    {
        this.plugin = plugin;

        Plugin wgPlugin = Bukkit.getServer().getPluginManager().getPlugin(ProtectionCompat.getName(compat));

        // WorldGuard may not be loaded
        if (plugin == null || !(wgPlugin instanceof WorldGuardPlugin))
        {
            worldGuard = null;
            return;
        }

        worldGuard = (WorldGuardPlugin) wgPlugin;

        try
        {
            m = worldGuard.getClass().getMethod("canBuild", Player.class, Location.class);
            success = true;
        }
        catch (NoSuchMethodException | SecurityException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean canBreakBlock(Player player, Location loc)
    {
        try
        {
            return (boolean) (m.invoke(worldGuard, player, loc));
        }
        catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e)
        {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean canBreakBlocksBetweenLocs(Player player, Location loc1, Location loc2)
    {
        int x1 = Math.min(loc1.getBlockX(), loc2.getBlockX());
        int y1 = Math.min(loc1.getBlockY(), loc2.getBlockY());
        int z1 = Math.min(loc1.getBlockZ(), loc2.getBlockZ());
        int x2 = Math.max(loc1.getBlockX(), loc2.getBlockX());
        int y2 = Math.max(loc1.getBlockY(), loc2.getBlockY());
        int z2 = Math.max(loc1.getBlockZ(), loc2.getBlockZ());

        for (int xPos = x1; xPos <= x2; ++xPos)
            for (int yPos = y1; yPos <= y2; ++yPos)
                for (int zPos = z1; zPos <= z2; ++zPos)
                    if (!canBreakBlock(player, new Location(loc1.getWorld(), xPos, yPos, zPos)))
                        return false;
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean success()
    {
        return success;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName()
    {
        return worldGuard.getName();
    }
}