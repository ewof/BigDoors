package nl.pim16aap2.bigDoors.moveBlocks;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.material.MaterialData;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import nl.pim16aap2.bigDoors.BigDoors;
import nl.pim16aap2.bigDoors.Door;
import nl.pim16aap2.bigDoors.NMS.CustomCraftFallingBlock_Vall;
import nl.pim16aap2.bigDoors.NMS.FallingBlockFactory_Vall;
import nl.pim16aap2.bigDoors.NMS.NMSBlock_Vall;
import nl.pim16aap2.bigDoors.util.DoorDirection;
import nl.pim16aap2.bigDoors.util.MyBlockData;
import nl.pim16aap2.bigDoors.util.RotateDirection;
import nl.pim16aap2.bigDoors.util.Util;

public class VerticalMover implements BlockMover
{
	private FallingBlockFactory_Vall  fabf;
	private Door                      door;
	private double                    time;
	private World                    world;
	private BigDoors                plugin;
	private int                   tickRate;
	private boolean            instantOpen;
	private int               blocksToMove;
	private int           xMin, xMax, yMin;
	private int           yMax, zMin, zMax;
	private List<MyBlockData> savedBlocks = new ArrayList<MyBlockData>();
	
	@SuppressWarnings("deprecation")
	public VerticalMover(BigDoors plugin, World world, double time, Door door, boolean instantOpen, int blocksToMove)
	{
		this.plugin       = plugin;
		this.world        = world;
		this.door         = door;
		this.fabf         = plugin.getFABF();
		this.instantOpen  = instantOpen;
		this.blocksToMove = blocksToMove;
		
		this.xMin = door.getMinimum().getBlockX();
		this.yMin = door.getMinimum().getBlockY();
		this.zMin = door.getMinimum().getBlockZ();
		this.xMax = door.getMaximum().getBlockX();
		this.yMax = door.getMaximum().getBlockY();
		this.zMax = door.getMaximum().getBlockZ();
		
		double speed  = 1;
		double pcMult = plugin.getConfigLoader().getDouble("pcMultiplier");
		pcMult = pcMult == 0.0 ? 1.0 : pcMult;
		int maxSpeed  = 6;
		// If the time isn't default, calculate speed.
		if (time != 0.0)
		{
			speed     = Math.abs(blocksToMove) / time * pcMult;
			this.time = time;
		};
		
		// If the non-default exceeds the max-speed or isn't set, calculate default speed.
		if (time == 0.0 || Math.abs(blocksToMove) / time > maxSpeed)
		{
			speed     = blocksToMove < 0 ? 1.7 : 0.8 * pcMult;
			speed     = speed > maxSpeed ? maxSpeed : speed;
			this.time = Math.abs(blocksToMove) / speed;
		}
		
		this.tickRate = Util.tickRateFromSpeed(speed);
		
		int index = 0;
		int yAxis = yMin;
		do
		{
			int zAxis = zMin;
			do
			{	
				for (int xAxis = xMin; xAxis <= xMax; xAxis++)
				{
					Location newFBlockLocation = new Location(world, xAxis + 0.5, yAxis - 0.020, zAxis + 0.5);
					// Move the lowest blocks up a little, so the client won't predict they're touching through the ground, which would make them slower than the rest.
					if (yAxis == yMin)
						newFBlockLocation.setY(newFBlockLocation.getY() + .010001);
					
					Material mat  = world.getBlockAt((int) xAxis, (int) yAxis, (int) zAxis).getType();
					Byte matData  = world.getBlockAt((int) xAxis, (int) yAxis, (int) zAxis).getData();
					BlockState bs = world.getBlockAt((int) xAxis, (int) yAxis, (int) zAxis).getState();
					MaterialData materialData = bs.getData();
					NMSBlock_Vall block  = this.fabf.nmsBlockFactory(world, (int) xAxis, (int) yAxis, (int) zAxis);
					
					// Certain blocks cannot be used the way normal blocks can (heads, (ender) chests etc).
					if (Util.isAllowedBlock(mat))
						world.getBlockAt((int) xAxis, (int) yAxis, (int) zAxis).setType(Material.AIR);
					else
					{
						mat     = Material.AIR;
						matData = 0;
					}
					
					CustomCraftFallingBlock_Vall fBlock = null;
					if (!instantOpen)
						 fBlock = fallingBlockFactory(newFBlockLocation, mat, matData, block);
					savedBlocks.add(index, new MyBlockData(mat, matData, fBlock, 0, materialData, block, 0, (int) yAxis));
					
					++index;
				}
				++zAxis;
			}
			while (zAxis <= zMax);
			++yAxis;
		}
		while (yAxis <= yMax);
		
		if (!instantOpen)
			rotateEntities();
		else
			putBlocks(false);
	}
	
	// Put the door blocks back, but change their state now.
	@SuppressWarnings("deprecation")
	@Override
	public void putBlocks(boolean onDisable)
	{
		int index = 0;
		double yAxis = this.yMin;
		do
		{
			double zAxis = this.zMin;
			do
			{
				for (int xAxis = xMin; xAxis <= xMax; ++xAxis)
				{
					Material mat    = savedBlocks.get(index).getMat();
					Byte matByte    = savedBlocks.get(index).getBlockByte();
					Location newPos = this.getNewLocation(xAxis, yAxis, zAxis);
					
					if (!instantOpen)
						savedBlocks.get(index).getFBlock().remove();

					if (!savedBlocks.get(index).getMat().equals(Material.AIR))
						if (plugin.is1_13())
						{
							savedBlocks.get(index).getBlock().putBlock(newPos);
							Block b = world.getBlockAt(newPos);
							BlockState bs = b.getState();
							bs.update();
						}
						else
						{
							Block b = world.getBlockAt(newPos);					
							MaterialData matData = savedBlocks.get(index).getMatData();
							matData.setData(matByte);
							
							b.setType(mat);
							BlockState bs = b.getState();
							bs.setData(matData);
							bs.update();
						}
					++index;
				}
				++zAxis;
			}
			while (zAxis <= this.zMax);
			++yAxis;
		}
		while (yAxis <= this.yMax);
		savedBlocks.clear();

		// Tell the door object it has been opened and what its new coordinates are.
		updateCoords(this.door, null, blocksToMove > 0 ? RotateDirection.UP : RotateDirection.DOWN, this.blocksToMove);
		toggleOpen  (door);
		if (!onDisable)
			plugin.removeBlockMover(this);
		
		// Change door availability to true, so it can be opened again.
		// Wait for a bit if instantOpen is enabled.
		int timer = onDisable   ?  0 : 
			        instantOpen ? 40 : plugin.getConfigLoader().getInt("coolDown") * 20;
		
		if (timer > 0)
		{
			new BukkitRunnable()
			{
				@Override
				public void run()
				{
					plugin.getCommander().setDoorAvailable(door.getDoorUID());
				}
			}.runTaskLater(plugin, timer);
		}
		else
			plugin.getCommander().setDoorAvailable(door.getDoorUID());
	}
	
	private Location getNewLocation(double xAxis, double yAxis, double zAxis)
	{
		return new Location(this.world, xAxis, yAxis + this.blocksToMove, zAxis);
	}
	
	// Method that takes care of the rotation aspect.
	public void rotateEntities()
	{
		new BukkitRunnable()
		{
			int counter       = 0;
			int endCount      = (int) (20 / tickRate * time);
			double step       = ((double) blocksToMove) / ((double) endCount);
			double stepSum    = 0;
			int totalTicks    = (int) (endCount * 1.1);
			
			@Override
			public void run()
			{
				if (counter == 0 || (counter < endCount - 27 && counter % 4 == 0))
					Util.playSound(door.getEngine(), "bd.dragging2", 0.8f, 0.6f);
				int index = 0;
				
				if (!plugin.getCommander().isPaused())
					++counter;
				if (counter < endCount - 1)
					stepSum = step * counter;
				else 
					stepSum = blocksToMove;
								
				if (!plugin.getCommander().canGo() || !door.canGo() || counter > totalTicks)
				{					
					Util.playSound(door.getEngine(), "bd.thud", 2f, 0.15f);
					for (int idx = 0; idx < savedBlocks.size(); ++idx)
						savedBlocks.get(idx).getFBlock().setVelocity(new Vector(0D, 0D, 0D));
					putBlocks(false);
					this.cancel();
				}
				else
				{
					int yAxis = yMin;
					do
					{
						int zAxis = zMin;
						do
						{
							for (int xAxis = xMin; xAxis <= xMax; xAxis++)
							{
								Location loc = new Location(null, xAxis + 0.5, yAxis + stepSum, zAxis + 0.5);
								Vector vec = loc.toVector().subtract(savedBlocks.get(index).getFBlock().getLocation().toVector());
								vec.multiply(0.101);
								savedBlocks.get(index).getFBlock().setVelocity(vec);
								++index;
							}
							++zAxis;
						}
						while (zAxis <= zMax);
						++yAxis;
					}
					while (yAxis <= yMax);
				}
			}
		}.runTaskTimer(plugin, 14, tickRate);
	}
	
	// Toggle the open status of a drawbridge.
	public void toggleOpen(Door door)
	{
		door.setOpenStatus(!door.isOpen());
	}

	// Update the coordinates of a door based on its location, direction it's pointing in and rotation direction.	
	public void updateCoords(Door door, DoorDirection currentDirection, RotateDirection rotDirection, int moved)
	{
		int xMin = door.getMinimum().getBlockX();
		int yMin = door.getMinimum().getBlockY();
		int zMin = door.getMinimum().getBlockZ();
		int xMax = door.getMaximum().getBlockX();
		int yMax = door.getMaximum().getBlockY();
		int zMax = door.getMaximum().getBlockZ();
				
		Location newMax = new Location(door.getWorld(), xMax, yMax + moved, zMax);
		Location newMin = new Location(door.getWorld(), xMin, yMin + moved, zMin);
		
		door.setMaximum(newMax);
		door.setMinimum(newMin);

		plugin.getCommander().updateDoorCoords(door.getDoorUID(), !door.isOpen(), newMin.getBlockX(), newMin.getBlockY(), newMin.getBlockZ(), newMax.getBlockX(), newMax.getBlockY(), newMax.getBlockZ());
	}
	
	public CustomCraftFallingBlock_Vall fallingBlockFactory(Location loc, Material mat, byte matData, NMSBlock_Vall block)
	{		
		CustomCraftFallingBlock_Vall entity = this.fabf.fallingBlockFactory(loc, block, matData, mat);
		Entity bukkitEntity = (Entity) entity;
		bukkitEntity.setCustomName("BigDoorsEntity");
		bukkitEntity.setCustomNameVisible(false);
		return entity;
	}
	
	@Override
	public long getDoorUID()
	{
		return this.door.getDoorUID();
	}

	@Override
	public Door getDoor()
	{
		return this.door;
	}
}
