package me.blvckbytes.craft_book_pipe_predicates;

import com.sk89q.craftbook.mechanics.pipe.*;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Piston;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.inventory.DoubleChestInventory;
import org.jetbrains.annotations.Nullable;

public class BlockUtility {

  private static final BlockFace[] POSSIBLE_CONTAINER_PISTON_FACES = {
    BlockFace.UP, BlockFace.DOWN,
    BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
  };

  private static final BlockFace[] PISTON_SIGN_FACES = {
    BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
  };

  public static @Nullable Sign getPistonSign(Block pistonBlock, boolean allowBlank) {
    BlockFace pistonFacing = pistonBlock.getBlockData() instanceof Directional directional ? directional.getFacing() : BlockFace.SELF;

    for (BlockFace face : PISTON_SIGN_FACES) {
      if (face == pistonFacing)
        continue;

      Block faceBlock = pistonBlock.getRelative(face);
      Material faceType = faceBlock.getType();

      if (Tag.STANDING_SIGNS.isTagged(faceType)) {
        // Standing-signs may only be on or under the piston
        if (face != BlockFace.UP && face != BlockFace.DOWN)
          continue;
      } else if (Tag.WALL_SIGNS.isTagged(faceType)) {
        // Wall-signs may only be attached N/E/S/W on the piston
        if (face == BlockFace.UP || face == BlockFace.DOWN)
          continue;

        if (!(faceBlock.getBlockData() instanceof Directional directional))
          continue;

        if (directional.getFacing() != face)
          continue;
      } else {
        // Not a sign at all, do not needlessly try to get its state
        continue;
      }

      if (!(faceBlock.getState() instanceof Sign sign))
        continue;

      if (sign.getLine(1).equalsIgnoreCase(MarkerConstants.PIPE_MARKER))
        return sign;

      if (allowBlank && isSignBlank(sign))
        return sign;
    }

    return null;
  }

  private static boolean isSignBlank(Sign sign) {
    for (var line : sign.getLines()) {
      if (!line.isBlank())
        return false;
    }

    return true;
  }

  public static void initializeBlankSignIfApplicable(Sign sign) {
    if (!isSignBlank(sign))
      return;

    sign.setLine(0, "");
    sign.setLine(1, MarkerConstants.PIPE_MARKER);
    sign.setLine(2, "");
    sign.setLine(3, "");
    sign.update(true, false);
  }

  private static boolean isPiston(Material material) {
    return material == Material.PISTON || material == Material.STICKY_PISTON;
  }

  public static @Nullable Block resolvePistonBlock(Block block) {
    if (isPiston(block.getType()))
      return block;

    var blockData = block.getBlockData();

    if (blockData instanceof WallSign wallSign) {
      var mountingBlock = block.getRelative(wallSign.getFacing().getOppositeFace());

      if (isPiston(mountingBlock.getType()))
        return mountingBlock;

      if (mountingBlock.getState() instanceof Container container)
        return getContainerAttachedPiston(container);
    }

    if (blockData instanceof org.bukkit.block.data.type.Sign) {
      var mountingBlock = block.getRelative(BlockFace.DOWN);

      if (isPiston(mountingBlock.getType()))
        return mountingBlock;

      mountingBlock = block.getRelative(BlockFace.UP);

      return isPiston(mountingBlock.getType()) ? mountingBlock : null;
    }

    if (block.getState() instanceof Container container)
      return getContainerAttachedPiston(container);

    return null;
  }

  private static @Nullable Block getContainerAttachedPiston(Container container) {
    var containerInventory = container.getInventory();

    Location location;

    if (containerInventory instanceof DoubleChestInventory doubleChestInventory) {
      Block locationBlock;

      if ((location = doubleChestInventory.getLeftSide().getLocation()) != null) {
        if ((locationBlock = getBlockAttachedPiston(location.getBlock())) != null)
          return locationBlock;
      }

      if ((location = doubleChestInventory.getRightSide().getLocation()) != null) {
        if ((locationBlock = getBlockAttachedPiston(location.getBlock())) != null)
          return locationBlock;
      }

      return null;
    }

    if ((location = container.getInventory().getLocation()) != null)
      return getBlockAttachedPiston(location.getBlock());

    return null;
  }

  public static @Nullable Block getBlockAttachedPiston(Block containerBlock) {
    for (var currentFace : POSSIBLE_CONTAINER_PISTON_FACES) {
      var currentBlock = containerBlock.getRelative(currentFace);

      if (!isPiston(currentBlock.getType()))
        continue;

      var pistonFacing = ((Piston) currentBlock.getBlockData()).getFacing();

      if (pistonFacing != currentFace.getOppositeFace())
        continue;

      return currentBlock;
    }

    return null;
  }
}
