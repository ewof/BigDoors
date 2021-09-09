package nl.pim16aap2.bigDoors.NMS;

import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BlockRotatable;
import net.minecraft.world.level.block.EnumBlockRotation;
import net.minecraft.world.level.block.state.BlockBase;
import net.minecraft.world.level.block.state.IBlockData;
import nl.pim16aap2.bigDoors.util.DoorDirection;
import nl.pim16aap2.bigDoors.util.RotateDirection;
import com.cryptomorin.xseries.XMaterial;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.craftbukkit.v1_17_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_17_R1.block.CraftBlock;
import org.bukkit.craftbukkit.v1_17_R1.block.data.CraftBlockData;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Set;

public class NMSBlock_V1_17_R1 extends BlockBase implements NMSBlock
{
    private IBlockData blockData;
    private final CraftBlockData craftBlockData;
    private final XMaterial xmat;
    private Location loc;

    public NMSBlock_V1_17_R1(World world, int x, int y, int z, Info blockInfo)
    {
        super(blockInfo);

        loc = new Location(world, x, y, z);

        // If the block is waterlogged (i.e. it has water inside), unwaterlog it.
        craftBlockData = (CraftBlockData) world.getBlockAt(x, y, z).getBlockData();
        if (craftBlockData instanceof Waterlogged)
            ((Waterlogged) craftBlockData).setWaterlogged(false);

        constructBlockDataFromBukkit();

        xmat = XMaterial.matchXMaterial(world.getBlockAt(x, y, z).getType().toString()).orElse(XMaterial.BEDROCK);
    }

    @Override
    public boolean canRotate()
    {
        return craftBlockData instanceof MultipleFacing;
    }

    @Override
    public void rotateBlock(RotateDirection rotDir)
    {
        EnumBlockRotation rot;
        switch (rotDir)
        {
        case CLOCKWISE:
            rot = EnumBlockRotation.b;
            break;
        case COUNTERCLOCKWISE:
            rot = EnumBlockRotation.d;
            break;
        default:
            rot = EnumBlockRotation.a;
        }
        blockData = blockData.a(rot);
    }

    private void constructBlockDataFromBukkit()
    {
        blockData = craftBlockData.getState();
    }

    @Override
    public void putBlock(Location loc)
    {
        this.loc = loc;

        if (craftBlockData instanceof MultipleFacing)
            updateCraftBlockDataMultipleFacing();

        ((CraftWorld) loc.getWorld()).getHandle()
            .setTypeAndData(new BlockPosition(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()), blockData, 1);
    }

    private void updateCraftBlockDataMultipleFacing()
    {
        Set<BlockFace> allowedFaces = ((MultipleFacing) craftBlockData).getAllowedFaces();
        allowedFaces.forEach((K) ->
        {
            org.bukkit.block.Block otherBlock = loc.clone().add(K.getModX(), K.getModY(), K.getModZ()).getBlock();
            CraftBlockData otherData = (CraftBlockData) ((CraftBlock) otherBlock).getBlockData();

            if (K.equals(BlockFace.UP))
                ((MultipleFacing) craftBlockData).setFace(K, true);
            else if (otherBlock.getType().isSolid())
            {
                ((MultipleFacing) craftBlockData).setFace(K, true);
                if (otherData instanceof MultipleFacing &&
                    (otherBlock.getType().equals(xmat.parseMaterial()) ||
                     (craftBlockData instanceof org.bukkit.block.data.type.Fence &&
                      otherData instanceof org.bukkit.block.data.type.Fence)))
                    if (((MultipleFacing) otherData).getAllowedFaces().contains(K.getOppositeFace()))
                    {
                        ((MultipleFacing) otherData).setFace(K.getOppositeFace(), true);
                        ((CraftBlock) otherBlock).setBlockData(otherData);
                    }
            }
            else
                ((MultipleFacing) craftBlockData).setFace(K, false);
        });
        constructBlockDataFromBukkit();
    }

    @Override
    public void rotateBlockUpDown(RotateDirection upDown, DoorDirection openDirection)
    {
        if (!(craftBlockData instanceof Directional))
            return; // Nothing we can do

        final Directional directional = (Directional) craftBlockData;
        final BlockFace currentBlockFace = directional.getFacing();
        final @Nullable BlockFace newBlockFace;

        final BlockFace openingDirFace = openDirection.getBlockFace();
        final BlockFace oppositeDirFace =
            Objects.requireNonNull(DoorDirection.getOpposite(openDirection)).getBlockFace();

        if (currentBlockFace == openingDirFace)
            newBlockFace = BlockFace.DOWN;
        else if (currentBlockFace == oppositeDirFace)
            newBlockFace = BlockFace.UP;
        else if (currentBlockFace == BlockFace.UP)
            newBlockFace = openingDirFace;
        else if (currentBlockFace == BlockFace.DOWN)
            newBlockFace = oppositeDirFace;
        else
            return; // Nothing to do

        if (directional.getFaces().contains(newBlockFace))
            directional.setFacing(newBlockFace);
        this.constructBlockDataFromBukkit();
    }

    @Override
    public void rotateBlockUpDown(boolean ns)
    {
        EnumDirection.EnumAxis axis = blockData.get(BlockRotatable.g);
        EnumDirection.EnumAxis newAxis = axis;
        switch (axis)
        {
        case a:
            newAxis = ns ? EnumDirection.EnumAxis.a : EnumDirection.EnumAxis.b;
            break;
        case b:
            newAxis = ns ? EnumDirection.EnumAxis.c : EnumDirection.EnumAxis.a;
            break;
        case c:
            newAxis = ns ? EnumDirection.EnumAxis.b : EnumDirection.EnumAxis.c;
            break;
        }
        blockData = blockData.set(BlockRotatable.g, newAxis);
    }

    @Override
    public void rotateCylindrical(RotateDirection rotDir)
    {
        if (rotDir.equals(RotateDirection.CLOCKWISE))
            blockData = blockData.a(EnumBlockRotation.b);
        else
            blockData = blockData.a(EnumBlockRotation.d);
    }

    /**
     * Gets the IBlockData (NMS) of this block.
     *
     * @return The IBlockData (NMS) of this block.
     */
    IBlockData getMyBlockData()
    {
        return blockData;
    }

    @Override
    public String toString()
    {
        return blockData.toString();
    }

    @Override
    public void deleteOriginalBlock()
    {
        loc.getWorld().getBlockAt(loc).setType(Material.AIR);
    }

    @Override
    public Item getItem()
    {
        return null;
    }

    @Override
    protected Block p()
    {
        return null;
    }
}
