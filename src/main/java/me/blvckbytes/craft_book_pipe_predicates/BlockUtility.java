package me.blvckbytes.craft_book_pipe_predicates;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
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
    BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
  };

  public static @Nullable Sign getPistonSignAndPossiblyInitialize(Block pistonBlock, boolean allowInitialization) {
    for (var face : PISTON_SIGN_FACES) {
      var faceBlock = pistonBlock.getRelative(face);

      if (!(faceBlock.getState() instanceof Sign sign))
        continue;

      if (sign.getLine(1).equalsIgnoreCase(MarkerConstants.PIPE_MARKER))
        return sign;

      if (!allowInitialization)
        continue;

      if (tryInitializeBlankSign(sign))
        return sign;
    }

    return null;
  }

  private static boolean tryInitializeBlankSign(Sign sign) {
    if (!(sign.getLine(0).isBlank() && sign.getLine(1).isBlank() && sign.getLine(2).isBlank() && sign.getLine(3).isBlank()))
      return false;

    sign.setLine(0, "");
    sign.setLine(1, MarkerConstants.PIPE_MARKER);
    sign.setLine(2, "");
    sign.setLine(3, "");
    sign.update(true, false);

    return true;
  }

  public static @Nullable Block resolvePistonBlock(Block block) {
    if (block.getType() == Material.PISTON)
      return block;

    var blockData = block.getBlockData();

    if (blockData instanceof WallSign wallSign) {
      var mountingBlock = block.getRelative(wallSign.getFacing().getOppositeFace());

      if (mountingBlock.getType() == Material.PISTON)
        return mountingBlock;

      if (mountingBlock.getState() instanceof Container container)
        return getContainerAttachedPiston(container);
    }

    if (blockData instanceof org.bukkit.block.data.type.Sign) {
      var mountingBlock = block.getRelative(BlockFace.DOWN);
      return mountingBlock.getType() == Material.PISTON ? mountingBlock : null;
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

      if (currentBlock.getType() != Material.PISTON)
        continue;

      var pistonFacing = ((Piston) currentBlock.getBlockData()).getFacing();

      if (pistonFacing != currentFace.getOppositeFace())
        continue;

      return currentBlock;
    }

    return null;
  }
}
