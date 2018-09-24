package nl.pim16aap2.bigDoors.NMS.v1_13_R2;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_13_R2.CraftWorld;

import net.minecraft.server.v1_13_R2.BlockPosition;
import net.minecraft.server.v1_13_R2.EnumBlockRotation;
import net.minecraft.server.v1_13_R2.IBlockData;
import nl.pim16aap2.bigDoors.NMS.NMSBlock_Vall;
import nl.pim16aap2.bigDoors.util.DoorDirection;
import nl.pim16aap2.bigDoors.util.RotateDirection;
import nl.pim16aap2.bigDoors.util.Util;
import nl.pim16aap2.bigDoors.util.XMaterial;

public class NMSBlock_V1_13_R2 extends net.minecraft.server.v1_13_R2.Block implements NMSBlock_Vall
{
	private IBlockData blockData;
	private XMaterial  xmat;
	
	public NMSBlock_V1_13_R2(World world, int x, int y, int z)
	{
		super(net.minecraft.server.v1_13_R2.Block.Info.a(((CraftWorld) world).getHandle().getType(new BlockPosition(x, y, z)).getBlock()));
		this.blockData = ((CraftWorld) world).getHandle().getType(new BlockPosition(x, y, z));
		this.xmat      = XMaterial.fromString(world.getBlockAt(new Location(world, x, y, z)).getType().toString());
		super.v(blockData);
	}
	
	public void rotateBlock(RotateDirection rotDir)
	{
		EnumBlockRotation rot;
		switch(rotDir)
		{
		case CLOCKWISE:
			rot = EnumBlockRotation.CLOCKWISE_90;
			break;
		case COUNTERCLOCKWISE:
			rot = EnumBlockRotation.COUNTERCLOCKWISE_90;
			break;
		default:
			rot = EnumBlockRotation.NONE;
		}
		this.blockData = blockData.a(rot);
	}

	@Override
	public void putBlock(Location loc)
	{
		((CraftWorld) loc.getWorld()).getHandle().setTypeAndData(
				new BlockPosition(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()), this.blockData, 1);
		if (Util.needsRefresh(this.xmat))
		{
			loc.getWorld().getBlockAt(loc).setType(XMaterial.AIR.parseMaterial());
			loc.getWorld().getBlockAt(loc).setType(xmat.parseMaterial());
		}
	}

	@Override
	public void rotateBlockUpDown(DoorDirection openDirection, RotateDirection upDown)
	{
		
	}
}
