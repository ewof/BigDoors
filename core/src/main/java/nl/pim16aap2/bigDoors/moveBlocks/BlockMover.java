package nl.pim16aap2.bigDoors.moveBlocks;

import com.cryptomorin.xseries.XMaterial;
import nl.pim16aap2.bigDoors.BigDoors;
import nl.pim16aap2.bigDoors.Door;
import nl.pim16aap2.bigDoors.NMS.CustomCraftFallingBlock;
import nl.pim16aap2.bigDoors.events.DoorEventAutoToggle;
import nl.pim16aap2.bigDoors.events.DoorEventToggleEnd;
import nl.pim16aap2.bigDoors.util.MyBlockData;
import nl.pim16aap2.bigDoors.util.MyBlockFace;
import nl.pim16aap2.bigDoors.util.Util;
import nl.pim16aap2.bigDoors.util.Vector3D;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.PistonMoveReaction;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.material.MaterialData;
import org.bukkit.scheduler.BukkitRunnable;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import static nl.pim16aap2.bigDoors.events.DoorEventToggle.ToggleType;

public abstract class BlockMover
{
    private final BigDoors plugin;
    private final @Nullable Door door;

    protected final AtomicBoolean blocksPlaced = new AtomicBoolean(false);
    protected final ArrayList<MyBlockData> savedBlocks = new ArrayList<>();
    private final List<MyBlockData> publicSavedBlocks = Collections.unmodifiableList(savedBlocks);
    protected final boolean instantOpen;

    protected BlockMover(BigDoors plugin, @Nullable Door door, boolean instantOpen)
    {
        this.plugin = plugin;
        this.door = door;
        this.instantOpen = instantOpen;
        if (door == null)
            return;

        plugin.getAutoCloseScheduler().cancelTimer(door.getDoorUID());
        preprocess();
    }

    private void preprocess()
    {
        if (this.door == null)
            return;

        final Location powerBlockLoc = door.getPowerBlockLoc();
        final Location min = door.getMinimum();
        final Location max = door.getMaximum();

        if (!Util.isPosInCuboid(powerBlockLoc, min.clone().add(-1, -1, -1), max.clone().add(1, 1, 1)))
            return;

        for (MyBlockFace blockFace : MyBlockFace.getValues())
        {
            final Vector3D vec = MyBlockFace.getDirection(blockFace);
            final Location newLocation = powerBlockLoc.clone().add(vec.getX(), vec.getY(), vec.getZ());

            if (!Util.isPosInCuboid(newLocation, min, max))
                continue;

            final Block block = newLocation.getBlock();
            BigDoors.getScheduler().runTask(block.getLocation(powerBlockLoc), new BukkitRunnable() {
                @Override
                public void run()
                {
                    if (block.getPistonMoveReaction() == PistonMoveReaction.BREAK)
                        BigDoors.getScheduler().runTaskLater(block::breakNaturally, 1L);

                    for (Entity entity : powerBlockLoc.getWorld().getNearbyEntities(powerBlockLoc, 1.1, 1.1, 1.1))
                    {
                        if (entity instanceof ItemFrame && Util.isPosInCuboid(entity.getLocation(), min, max))
                        {
                            final ItemFrame itemFrame = (ItemFrame) entity;
                            powerBlockLoc.getWorld().dropItemNaturally(itemFrame.getLocation(), itemFrame.getItem());
                            powerBlockLoc.getWorld().dropItemNaturally(itemFrame.getLocation(), XMaterial.ITEM_FRAME.parseItem());
                            itemFrame.remove();
                        }
                    }
                }
            });

        }
    }

    // Put blocks in their final position.
    // Use onDisable = false to make it safe to use during onDisable().
    public abstract void putBlocks(boolean onDisable);

    public abstract long getDoorUID();

    public abstract Door getDoor();

    public abstract void cancel(boolean onDisable);

    /**
     * Gets the number of ticks the door should to the delay to make sure the second
     * toggle of a button doesn't toggle the door again.
     *
     * @param endCount The number of ticks the animation took.
     * @return The number of ticks to wait before a button cannot toggle the door again.
     */
    public int buttonDelay(final int endCount)
    {
        return Math.max(0, 17 - endCount);
    }

    protected synchronized void putBlocks(
        boolean onDisable, double time, int endCount, LocationFinder locationUpdater, Runnable coordinateUpdater)
    {
        if (blocksPlaced.getAndSet(true))
            return;

        final World world = Objects.requireNonNull(door).getWorld();

        for (MyBlockData savedBlock : savedBlocks)
        {
            Material mat = savedBlock.getMat();
            byte matByte = savedBlock.getBlockByte();
            Location newPos = locationUpdater.apply(savedBlock.getRadius(), savedBlock.getStartX(),
                                                    savedBlock.getStartY(), savedBlock.getStartZ());

            if (!instantOpen) {
				CustomCraftFallingBlock fBlock = savedBlock.getFBlock();
                
                BigDoors.getScheduler().runTask(fBlock.getLocation(), () -> {
                    fBlock.remove();
                });
			}

            if (!savedBlock.getMat().equals(Material.AIR))
            {
                if (BigDoors.isOnFlattenedVersion())
                {
                    Location savedBlockLoc = savedBlock.getFBlock().getLocation();
                    BigDoors.getScheduler().runTask(savedBlockLoc, () -> {
                        savedBlock.getBlock().putBlock(newPos);
                        Block b = world.getBlockAt(newPos);
                        BlockState bs = b.getState();
                        bs.update();
                    });
                }
                else
                {
                    Block b = world.getBlockAt(newPos);
                    MaterialData matData = savedBlock.getMatData();
                    matData.setData(matByte);

                    b.setType(mat);
                    BlockState bs = b.getState();
                    bs.setData(matData);
                    bs.update();
                }
            }
        }

        coordinateUpdater.run();
        toggleOpen(door);

        if (onDisable)
            return;

        BukkitRunnable run = new BukkitRunnable()
        {
            @Override
            public void run()
            {
                plugin.getCommander().setDoorAvailable(door.getDoorUID());

                final ToggleType toggleType = door.isOpen() ? ToggleType.OPEN : ToggleType.CLOSE;
                Bukkit.getPluginManager().callEvent(new DoorEventToggleEnd(door, toggleType, instantOpen));

                if (canAutoToggle(door))
                    plugin.getAutoCloseScheduler().scheduleAutoClose(door, time, instantOpen);
            }
        };

        BigDoors.getScheduler().runTaskLater(run,getDelay(endCount));
    }

    private int getDelay(int endCount)
    {
        return buttonDelay(endCount) + Math.min(plugin.getMinimumDoorDelay(),
                                                plugin.getConfigLoader().coolDown() * 20);
    }

    private boolean canAutoToggle(Door door)
    {
        if (!door.isOpen() || door.getAutoClose() <= 0)
            return false;

        final DoorEventAutoToggle preparationEvent = new DoorEventAutoToggle(door);
        Bukkit.getPluginManager().callEvent(preparationEvent);
        return !preparationEvent.isCancelled();
    }

    protected void toggleOpen(Door door)
    {
        door.setOpenStatus(!door.isOpen());
    }

    /**
     * @return An unmodifiable list of the 'saved blocks' (i.e. the animated blocks).
     * This list will be empty until the animated blocks have been created.
     */
    @SuppressWarnings("unused")
    public final List<MyBlockData> getSavedBlocks()
    {
        return publicSavedBlocks;
    }

    @FunctionalInterface
    public interface LocationFinder
    {
        Location apply(double radius, double startX, double startY, double startZ);
    }
}
