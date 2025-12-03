package me.blvckbytes.craft_book_pipe_predicates;

import com.sk89q.craftbook.mechanics.pipe.PipeSignUpdateEvent;
import org.bukkit.Bukkit;
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

  public static @Nullable Sign getPistonSign(Block pistonBlock, boolean allowBlank) {
    for (var face : PISTON_SIGN_FACES) {
      var faceBlock = pistonBlock.getRelative(face);

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

  public static void updateSign(Sign sign) {
    sign.update(true, false);
    Bukkit.getServer().getPluginManager().callEvent(new PipeSignUpdateEvent(sign.getBlock()));
  }

  public static void initializeBlankSignIfApplicable(Sign sign) {
    if (!isSignBlank(sign))
      return;

    sign.setLine(0, "");
    sign.setLine(1, MarkerConstants.PIPE_MARKER);
    sign.setLine(2, "");
    sign.setLine(3, "");
    updateSign(sign);
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
