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
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import nl.pim16aap2.bigDoors.BigDoors;
import nl.pim16aap2.bigDoors.Door;
import nl.pim16aap2.bigDoors.NMS.CustomCraftFallingBlock_Vall;
import nl.pim16aap2.bigDoors.NMS.FallingBlockFactory_Vall;
import nl.pim16aap2.bigDoors.NMS.NMSBlock_Vall;
import nl.pim16aap2.bigDoors.moveBlocks.Cylindrical.getNewLocation.GetNewLocation;
import nl.pim16aap2.bigDoors.moveBlocks.Cylindrical.getNewLocation.GetNewLocationEast;
import nl.pim16aap2.bigDoors.moveBlocks.Cylindrical.getNewLocation.GetNewLocationNorth;
import nl.pim16aap2.bigDoors.moveBlocks.Cylindrical.getNewLocation.GetNewLocationSouth;
import nl.pim16aap2.bigDoors.moveBlocks.Cylindrical.getNewLocation.GetNewLocationWest;
import nl.pim16aap2.bigDoors.util.DoorDirection;
import nl.pim16aap2.bigDoors.util.MyBlockData;
import nl.pim16aap2.bigDoors.util.RotateDirection;
import nl.pim16aap2.bigDoors.util.Util;

public class CylindricalMover implements BlockMover
{
	private GetNewLocation             gnl;
	private FallingBlockFactory_Vall  fabf;
	private double                    time;
	private Door                      door;
	private World                    world;
	private int                     dx, dz;
	private BigDoors                plugin;
	private int                   tickRate;
	private double              endStepSum;
	private double              multiplier;
	private boolean            instantOpen;
	private boolean            isASEnabled;
	private double            startStepSum;
	private RotateDirection   rotDirection;
	private int             stepMultiplier;
	private int           xMin, xMax, yMin;
	private int           yMax, zMin, zMax;
	private DoorDirection currentDirection;
	private Location         pointOpposite;
	private Location          turningPoint;
	private List<MyBlockData> savedBlocks = new ArrayList<MyBlockData>();

	@SuppressWarnings("deprecation")
	public CylindricalMover(BigDoors plugin, World world, int qCircleLimit, RotateDirection rotDirection, double time,
			Location pointOpposite, DoorDirection currentDirection, Door door, boolean instantOpen)
	{
//	    plugin.getMyLogger().logMessage("\n\nCylindricalMover initiation!", false, false);
		this.pointOpposite    = pointOpposite;
		turningPoint          = door.getEngine();
		this.rotDirection     = rotDirection;
		this.currentDirection = currentDirection;
		this.plugin           = plugin;
		this.world            = world;
		this.door             = door;
		isASEnabled      = plugin.isASEnabled();
		fabf             = isASEnabled ? plugin.getFABF2() : plugin.getFABF();
		this.instantOpen = instantOpen;
		stepMultiplier   = rotDirection == RotateDirection.CLOCKWISE ? -1 : 1;

		// Debug string
//		String logStr = "CylindricalMover will move door " + door.getName() + "\n";

		xMin = turningPoint.getBlockX() < pointOpposite.getBlockX() ? turningPoint.getBlockX() : pointOpposite.getBlockX();
		yMin = turningPoint.getBlockY() < pointOpposite.getBlockY() ? turningPoint.getBlockY() : pointOpposite.getBlockY();
		zMin = turningPoint.getBlockZ() < pointOpposite.getBlockZ() ? turningPoint.getBlockZ() : pointOpposite.getBlockZ();
		xMax = turningPoint.getBlockX() > pointOpposite.getBlockX() ? turningPoint.getBlockX() : pointOpposite.getBlockX();
		yMax = turningPoint.getBlockY() > pointOpposite.getBlockY() ? turningPoint.getBlockY() : pointOpposite.getBlockY();
		zMax = turningPoint.getBlockZ() > pointOpposite.getBlockZ() ? turningPoint.getBlockZ() : pointOpposite.getBlockZ();
		int xLen      = Math.abs(door.getMaximum().getBlockX() - door.getMinimum().getBlockX());
		int zLen      = Math.abs(door.getMaximum().getBlockZ() - door.getMinimum().getBlockZ());
		int doorSize  = Math.max(xLen, zLen) + 1;
		double vars[] = Util.calculateTimeAndTickRate(doorSize, time, plugin.getConfigLoader().bdMultiplier(), 3.7);
		this.time     = vars[0];
		tickRate   = (int) vars[1];
		multiplier = vars[2];

        // Debug string
//		logStr += "xLen = " + xLen + "\nyLen = " + Math.abs(door.getMaximum().getBlockY() - door.getMinimum().getBlockY()) +
//		          "\nzLen = " + zLen + "\nDoorSize = " + doorSize + "\ntime = " + this.time +
//		          "\ntickRate = " + tickRate + "\nmultiplier = " + multiplier + "\nInstantOpen = " + instantOpen;

		dx   = pointOpposite.getBlockX() > turningPoint.getBlockX() ? 1 : -1;
		dz   = pointOpposite.getBlockZ() > turningPoint.getBlockZ() ? 1 : -1;

        // Debug string
//		logStr += "min (x;y;z) = (" + xMin + " ; " + yMin + " ; " + zMin + ")\n";
//		logStr += "max (x;y;z) = (" + xMax + " ; " + yMax + " ; " + zMax + ")\n";
//		logStr += "dx = " + dx + ", dz = " + dz + "\n";
//		logStr += "Adding blocks now:\n";

		int index = 0;
		double xAxis = turningPoint.getX();
		do
		{
			double zAxis = turningPoint.getZ();
			do
			{
				// Get the radius of this pillar.
				double radius = Math.abs(xAxis - turningPoint.getBlockX()) > Math.abs(zAxis - turningPoint.getBlockZ()) ?
				                Math.abs(xAxis - turningPoint.getBlockX()) : Math.abs(zAxis - turningPoint.getBlockZ());

				for (double yAxis = yMin; yAxis <= yMax; yAxis++)
				{
			        // Debug string
//				    logStr += "\n";
				    String canRot = "";
				    String block_NMS = "";
				    String block_NMS2 = "";
				    String isAllowed = "";
				    String vBlockStr = "";

					Location newFBlockLocation = new Location(world, xAxis + 0.5, yAxis - 0.020, zAxis + 0.5);
					// Move the lowest blocks up a little, so the client won't predict they're touching through the ground, which would make them slower than the rest.
					if (yAxis == yMin)
						newFBlockLocation.setY(newFBlockLocation.getY() + .010001);

					Block vBlock = world.getBlockAt((int) xAxis, (int) yAxis, (int) zAxis);
					vBlockStr += vBlock.toString();
					Material mat = vBlock.getType();

//					plugin.getMyLogger().logMessage("Adding mat = " + mat, false, false);
					if (!mat.equals(Material.AIR))
					{
						Byte matData  = vBlock.getData();
						BlockState bs = vBlock.getState();
						MaterialData materialData = bs.getData();

						NMSBlock_Vall block  = fabf.nmsBlockFactory(world, (int) xAxis, (int) yAxis, (int) zAxis);
						NMSBlock_Vall block2 = null;

						int canRotate        = 0;
						Byte matByte         = matData;
						// Certain blocks cannot be used the way normal blocks can (heads, (ender) chests etc).
						if (Util.isAllowedBlock(mat))
						{
						    isAllowed = "TRUE";
							canRotate        = Util.canRotate(mat);
							canRot = Integer.toString(canRotate);
//							plugin.getMyLogger().logMessage("canRotate: " + canRotate, false, false);
							// Rotate blocks here so they don't interrupt the rotation animation.
							if (canRotate != 0)
							{
								Location pos = new Location(world, (int) xAxis, (int) yAxis, (int) zAxis);
								if (canRotate == 1 || canRotate == 3)
									matByte  = rotateBlockDataLog(matData);
								else if (canRotate == 2)
									matByte  = rotateBlockDataStairs(matData);
								else if (canRotate == 4)
									matByte  = rotateBlockDataAnvil(matData);

								Block b      = world.getBlockAt(pos);
								materialData.setData(matByte);

								if (plugin.is1_13())
								{
									if (canRotate == 6)
									{
										block2 = fabf.nmsBlockFactory(world, (int) xAxis, (int) yAxis, (int) zAxis);
										block2.rotateCylindrical(this.rotDirection);;
									}
									else
									{
										b.setType(mat);
										BlockState bs2 = b.getState();
										bs2.setData(materialData);
										bs2.update();
										block2 = fabf.nmsBlockFactory(world, (int) xAxis, (int) yAxis, (int) zAxis);
									}
								}
							}
							vBlock.setType(Material.AIR);
						}
						else
						{
						    isAllowed = "FALSE";
//						    plugin.getMyLogger().logMessage("Adding Air!", false, false);
							mat     = Material.AIR;
							matByte = 0;
							matData = 0;
							block   = null;
							materialData = null;
						}

						CustomCraftFallingBlock_Vall fBlock = null;
						if (!instantOpen)
							 fBlock = fallingBlockFactory(newFBlockLocation, mat, matData, block);

						savedBlocks.add(index, new MyBlockData(mat, matByte, fBlock, radius, materialData, block2 == null ? block : block2, canRotate, (int) yAxis));

				        try
				        {
				            block_NMS  = block.toString();
				        }
				        catch (Exception unhandled)
				        {
				            block_NMS = "...";
				        }

				        try
				        {
				            block_NMS2 = block2.toString();
				        }
				        catch (Exception unhandled)
				        {
				            block_NMS2 = "...";
				        }
					}
					else
						savedBlocks.add(index, new MyBlockData(Material.AIR));


//                    logStr += "(" + index + ") " + "radius: " + radius + ", isAllowed = " + isAllowed + ", canRot = " + canRot;
//                    logStr += "\nvBlock = " + vBlockStr.toString() + "\n";
//                    logStr += "block_NMS = " + block_NMS + "\n";
//                    logStr += "block_NMS2 = " + block_NMS2 + "\n";
//                    logStr += "savedBlocks[idx] = \n" + savedBlocks.get(index).toString() + "\n";

					index++;
				}
				zAxis += dz;
			}
			while (zAxis >= pointOpposite.getBlockZ() && dz == -1 || zAxis <= pointOpposite.getBlockZ() && dz == 1);
			xAxis += dx;
		}
		while (xAxis >= pointOpposite.getBlockX() && dx == -1 || xAxis <= pointOpposite.getBlockX() && dx == 1);

		switch (currentDirection)
		{
		case NORTH:
			gnl          = new GetNewLocationNorth(world, xMin, xMax, zMin, zMax, rotDirection);
			startStepSum = Math.PI;
			endStepSum   = rotDirection == RotateDirection.CLOCKWISE ? Math.PI / 2 : 3 * Math.PI / 2;
			break;
		case EAST:
			gnl          = new GetNewLocationEast (world, xMin, xMax, zMin, zMax, rotDirection);
			startStepSum = Math.PI / 2;
			endStepSum   = rotDirection == RotateDirection.CLOCKWISE ? 0 : Math.PI;
			break;
		case SOUTH:
			gnl          = new GetNewLocationSouth(world, xMin, xMax, zMin, zMax, rotDirection);
			startStepSum = 0;
			endStepSum   = rotDirection == RotateDirection.CLOCKWISE ? 3 * Math.PI / 2 : Math.PI / 2;
			break;
		case WEST:
			gnl          = new GetNewLocationWest (world, xMin, xMax, zMin, zMax, rotDirection);
			startStepSum = 3 * Math.PI / 2;
			endStepSum   = rotDirection == RotateDirection.CLOCKWISE ? Math.PI : 0;
			break;
		}

//		// Stuff used for debugging purposes:
//		plugin.getMyLogger().logMessage("cDir: " + currentDirection + ", startSum = " + startStepSum + ", endSum = " + endStepSum, false, false);
//		logStr += "cDir: " + currentDirection + ", startSum = " + startStepSum + ", endSum = " + endStepSum + "\n";
//		String output = "";
//		for (MyBlockData sb : savedBlocks)
//		    output += (sb.getMat() + " " + sb.getFBlock() + "\n");
//		plugin.getMyLogger().logMessage("savedBlocks size = " + savedBlocks.size() + ":\n" + output + "InstantOpen: " + instantOpen, false, false);
//        logStr += "savedBlocks size = " + savedBlocks.size() + ":\n" + output + "InstantOpen: " + instantOpen + "\n";
//        plugin.getMyLogger().logMessage(logStr, false, false);

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
//	    plugin.getMyLogger().logMessage("Putting blocks. onDisable = " + onDisable, false, false);
		int index = 0;
		double xAxis = turningPoint.getX();
		do
		{
			double zAxis = turningPoint.getZ();
			do
			{
				for (double yAxis = yMin; yAxis <= yMax; yAxis++)
				{
					/*
					 * 0-3: Vertical oak, spruce, birch, then jungle 4-7: East/west oak, spruce,
					 * birch, jungle 8-11: North/south oak, spruce, birch, jungle 12-15: Uses oak,
					 * spruce, birch, jungle bark texture on all six faces
					 */

					Material mat    = savedBlocks.get(index).getMat();

					if (!mat.equals(Material.AIR))
					{
						Byte matByte;
						matByte         = savedBlocks.get(index).getBlockByte();
						Location newPos = gnl.getNewLocation(savedBlocks, xAxis, yAxis, zAxis, index);
						if (!instantOpen)
							savedBlocks.get(index).getFBlock().remove();
//						plugin.getMyLogger().logMessage("Material = " + savedBlocks.get(index).getMat(), false, false);
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
					}
					index++;
				}
				zAxis += dz;
			}
			while (zAxis >= pointOpposite.getBlockZ() && dz == -1 || zAxis <= pointOpposite.getBlockZ() && dz == 1);
			xAxis += dx;
		}
		while (xAxis >= pointOpposite.getBlockX() && dx == -1 || xAxis <= pointOpposite.getBlockX() && dx == 1);
		savedBlocks.clear();
//		plugin.getMyLogger().logMessage("Going to update coords now!", false, false);
		// Tell the door object it has been opened and what its new coordinates are.
		updateCoords(door, currentDirection, rotDirection, -1);
		toggleOpen  (door);
		if (!onDisable)
			plugin.removeBlockMover(this);

		// Change door availability to true, so it can be opened again.
		// Wait for a bit if instantOpen is enabled.
		int timer = onDisable   ?  0 :
			        instantOpen ? 40 : plugin.getConfigLoader().coolDown() * 20;

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

		if (!onDisable)
			goAgain();
	}

	private void goAgain()
	{
//	    plugin.getMyLogger().logMessage("Going again!", false, false);
		int autoCloseTimer = door.getAutoClose();
		if (autoCloseTimer < 0 || !door.isOpen())
			return;

		new BukkitRunnable()
		{
			@Override
			public void run()
			{
				plugin.getCommander().setDoorAvailable(door.getDoorUID());
				plugin.getDoorOpener(door.getType()).openDoor(plugin.getCommander().getDoor(door.getDoorUID()), time, instantOpen, false);
			}
		}.runTaskLater(plugin, autoCloseTimer * 20);
	}

	// Method that takes care of the rotation aspect.
	private void rotateEntities()
	{
//	    plugin.getMyLogger().logMessage("Rotation intiation!", false, false);
		new BukkitRunnable()
		{
			int indexMid      = savedBlocks.size() / 2;
			Location center   = new Location(world, turningPoint.getBlockX() + 0.5, yMin, turningPoint.getBlockZ() + 0.5);
			boolean replace   = false;
			int counter       = 0;
			int endCount      = (int) (20 / tickRate * time);
			double step       = (Math.PI / 2) / endCount * stepMultiplier;
			double stepSum    = startStepSum;
			int totalTicks    = (int) (endCount * multiplier);
			int replaceCount  = (int) (endCount / 2);

			@Override
			public void run()
			{
//			    plugin.getMyLogger().logMessage("Running", false, false);
				if (counter == 0 || (counter < endCount - 27 / tickRate && counter % (5 * tickRate / 4) == 0))
					Util.playSound(door.getEngine(), "bd.dragging2", 0.5f, 0.6f);

				if (!plugin.getCommander().isPaused())
					++counter;
				if (counter < endCount - 1)
					stepSum = startStepSum + step * counter;
				else
					stepSum = endStepSum;

				replace = false;
				if (counter == replaceCount)
					replace = true;

				if (!plugin.getCommander().canGo() || !door.canGo() || counter > totalTicks)
				{
//				    plugin.getMyLogger().logMessage("plugin.canGo = " + plugin.getCommander().canGo() +
//				                                    ", door.canGo = " + door.canGo() +
//				                                    ", counter = " + counter + ", totalTicks = " + totalTicks +
//				                                    ", step = " + step + ", tickrate = " + tickRate + ", time = " + time, false, false);
					Util.playSound(door.getEngine(), "bd.closing-vault-door", 0.2f, 1f);
					for (int idx = 0; idx < savedBlocks.size(); ++idx)
						if (!savedBlocks.get(idx).getMat().equals(Material.AIR))
							savedBlocks.get(idx).getFBlock().setVelocity(new Vector(0D, 0D, 0D));
					putBlocks(false);
					cancel();
				}
				else
				{
					for (MyBlockData block : savedBlocks)
					{
						if (!block.getMat().equals(Material.AIR))
						{
							double radius = block.getRadius();
							int yPos      = block.getStartY();
							// It is not pssible to edit falling block blockdata (client won't update it), so delete the current fBlock and replace it by one that's been rotated.
							if (replace)
							{
								if (block.canRot() != 0 && block.canRot() != 5)
								{
									Material mat = block.getMat();
									Location loc = block.getFBlock().getLocation();
									Byte matData = block.getBlockByte();
									Vector veloc = block.getFBlock().getVelocity();
									// For some reason respawning fblocks puts them higher than they were, which has to be counteracted.
									if (yPos != yMin)
										loc.setY(loc.getY() - .010001);
									CustomCraftFallingBlock_Vall fBlock;
									// Because the block in savedBlocks is already rotated where applicable, just use that block now.
									fBlock = fallingBlockFactory(loc, mat, (byte) matData, block.getBlock());

									block.getFBlock().remove();
									block.setFBlock(fBlock);
									block.getFBlock().setVelocity(veloc);
								}
							}

							if (isASEnabled)
							{
								Location vec;
								if (radius != 0)
									vec = (new Location(center.getWorld(), center.getX(), yPos, center.getZ())).subtract(block.getFBlock().getLocation());
								else
								{
									Location midLoc = savedBlocks.get(indexMid).getFBlock().getLocation();
									vec = (new Location(center.getWorld(), midLoc.getX(), yPos, midLoc.getZ())).subtract(block.getFBlock().getLocation());
								}
								block.getFBlock().setHeadPose(directionToEuler(vec));
							}

							if (radius != 0)
							{
								Location loc;
								double addX = radius * Math.sin(stepSum);
								double addZ = radius * Math.cos(stepSum);

								loc = new Location(null, center.getX() + addX, yPos, center.getZ() + addZ);

								Vector vec = loc.toVector().subtract(block.getFBlock().getLocation().toVector());
								vec.multiply(0.101);
								block.getFBlock().setVelocity(vec);
							}
						}
					}
				}
			}
		}.runTaskTimer(plugin, 14, 4);
	}

	private EulerAngle directionToEuler(Location dir)
	{
	    double yaw      = -Math.atan2(dir.getX(), dir.getZ()) + Math.PI / 2;
	    return new EulerAngle(0, yaw, 0);
	}

	// Rotate logs by modifying its material data.
	private byte rotateBlockDataLog(Byte matData)
	{
		if (matData >= 4 && matData <= 7)
			matData = (byte) (matData + 4);
		else if (matData >= 7 && matData <= 11)
			matData = (byte) (matData - 4);
		return matData;
	}

	private byte rotateBlockDataAnvil(Byte matData)
	{
		if (rotDirection == RotateDirection.CLOCKWISE)
		{
			if (     matData == 0 || matData == 4 || matData ==  8)
				matData = (byte) (matData + 1);
			else if (matData == 1 || matData == 5 || matData ==  9)
				matData = (byte) (matData + 1);
			else if (matData == 2 || matData == 6 || matData == 10)
				matData = (byte) (matData + 1);
			else if (matData == 3 || matData == 7 || matData == 11)
				matData = (byte) (matData - 3);
		}
		else
		{
			if (     matData == 0 || matData == 4 || matData ==  8)
				matData = (byte) (matData + 3);
			else if (matData == 1 || matData == 5 || matData ==  9)
				matData = (byte) (matData - 1);
			else if (matData == 2 || matData == 6 || matData == 10)
				matData = (byte) (matData - 1);
			else if (matData == 3 || matData == 7 || matData == 11)
				matData = (byte) (matData - 1);
		}
		return matData;
	}

	// Rotate stairs by modifying its material data.
	private byte rotateBlockDataStairs(Byte matData)
	{
		if (rotDirection == RotateDirection.CLOCKWISE)
		{
			if (matData == 0 || matData == 4)
				matData = (byte) (matData + 2);
			else if (matData == 1 || matData == 5)
				matData = (byte) (matData + 2);
			else if (matData == 2 || matData == 6)
				matData = (byte) (matData - 1);
			else if (matData == 3 || matData == 7)
				matData = (byte) (matData - 3);
		}
		else
		{
			if (matData == 0 || matData == 4)
				matData = (byte) (matData + 3);
			else if (matData == 1 || matData == 5)
				matData = (byte) (matData + 1);
			else if (matData == 2 || matData == 6)
				matData = (byte) (matData - 2);
			else if (matData == 3 || matData == 7)
				matData = (byte) (matData - 2);
		}
		return matData;
	}

	// Toggle the open status of a drawbridge.
	private void toggleOpen(Door door)
	{
		door.setOpenStatus(!door.isOpen());
	}

	// Update the coordinates of a door based on its location, direction it's pointing in and rotation direction.
	private void updateCoords(Door door, DoorDirection currentDirection, RotateDirection rotDirection, int moved)
	{
		int xMin = door.getMinimum().getBlockX();
		int yMin = door.getMinimum().getBlockY();
		int zMin = door.getMinimum().getBlockZ();
		int xMax = door.getMaximum().getBlockX();
		int yMax = door.getMaximum().getBlockY();
		int zMax = door.getMaximum().getBlockZ();
		int xLen = xMax - xMin;
		int zLen = zMax - zMin;
		Location newMax = null;
		Location newMin = null;

		switch (currentDirection)
		{
		case NORTH:
			if (rotDirection == RotateDirection.CLOCKWISE)
			{
				newMin = new Location(door.getWorld(), xMin,          yMin, zMax);
				newMax = new Location(door.getWorld(), (xMin + zLen), yMax, zMax);
			}
			else
			{
				newMin = new Location(door.getWorld(), (xMin - zLen), yMin, zMax);
				newMax = new Location(door.getWorld(), xMax,          yMax, zMax);
			}
			break;


		case EAST:
			if (rotDirection == RotateDirection.CLOCKWISE)
			{
				newMin = new Location(door.getWorld(), xMin, yMin,          zMin);
				newMax = new Location(door.getWorld(), xMin, yMax, (zMax + xLen));
			}
			else
			{
				newMin = new Location(door.getWorld(), xMin, yMin, (zMin - xLen));
				newMax = new Location(door.getWorld(), xMin, yMax,          zMin);
			}
			break;


		case SOUTH:
			if (rotDirection == RotateDirection.CLOCKWISE)
			{
				newMin = new Location(door.getWorld(), (xMin - zLen), yMin, zMin);
				newMax = new Location(door.getWorld(), xMax,          yMax, zMin);
			}
			else
			{
				newMin = new Location(door.getWorld(), xMin,          yMin, zMin);
				newMax = new Location(door.getWorld(), (xMin + zLen), yMax, zMin);
			}
			break;


		case WEST:
			if (rotDirection == RotateDirection.CLOCKWISE)
			{
				newMin = new Location(door.getWorld(), xMax, yMin, (zMin - xLen));
				newMax = new Location(door.getWorld(), xMax, yMax,          zMax);
			}
			else
			{
				newMin = new Location(door.getWorld(), xMax, yMin,          zMin);
				newMax = new Location(door.getWorld(), xMax, yMax, (zMax + xLen));
			}
			break;
		}
		door.setMaximum(newMax);
		door.setMinimum(newMin);

		plugin.getCommander().updateDoorCoords(door.getDoorUID(), !door.isOpen(), newMin.getBlockX(), newMin.getBlockY(), newMin.getBlockZ(), newMax.getBlockX(), newMax.getBlockY(), newMax.getBlockZ());
	}

	private CustomCraftFallingBlock_Vall fallingBlockFactory(Location loc, Material mat, byte matData, NMSBlock_Vall block)
	{
		CustomCraftFallingBlock_Vall entity = fabf.fallingBlockFactory(plugin, loc, block, matData, mat);
		Entity bukkitEntity = (Entity) entity;
		bukkitEntity.setCustomName("BigDoorsEntity");
		bukkitEntity.setCustomNameVisible(false);
		return entity;
	}

	@Override
	public long getDoorUID()
	{
		return door.getDoorUID();
	}


	@Override
	public Door getDoor()
	{
		return door;
	}
}
