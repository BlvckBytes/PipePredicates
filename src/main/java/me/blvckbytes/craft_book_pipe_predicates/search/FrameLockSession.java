package me.blvckbytes.craft_book_pipe_predicates.search;

import com.sk89q.craftbook.mechanics.pipe.*;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.ItemFrame;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.function.Consumer;

public class FrameLockSession extends EnumerationSession<FrameLockSession> {

  private static final BlockFace[] FRAME_MOUNT_FACES = {
    BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
  };

  private final World world;

  private final LongSet visitedBlocks;
  private final boolean lockFrames;
  private final NamespacedKey lockedFrameKey;

  private int chunksWaitingOn;
  private int lockChangeCount;

  public FrameLockSession(
    Block origin, Pipes pipesMechanic, Plugin plugin,
    Consumer<FrameLockSession> warmupHandler,
    Consumer<FrameLockSession> completionHandler,
    boolean lockFrames,
    NamespacedKey lockedFrameKey
  ) {
    super(origin, pipesMechanic, plugin, warmupHandler, completionHandler);

    this.world = origin.getWorld();
    this.visitedBlocks = new LongOpenHashSet();
    this.lockFrames = lockFrames;
    this.lockedFrameKey = lockedFrameKey;
  }

  @Override
  protected EnumerationDecision onTube(Block block, int cachedBlock) {
    return EnumerationDecision.CONTINUE;
  }

  @Override
  protected EnumerationDecision onPiston(Block block, int cachedBlock) {
    handleOutputBlock(block.getRelative(CachedBlock.getFacing(cachedBlock)));
    return EnumerationDecision.CONTINUE;
  }

  @Override
  protected void beforeCompletion() {}

  @Override
  protected void beforeSubPipe() {}

  @Override
  protected void afterSubPipe() {}

  @Override
  protected EnumSet<EnumerationBehavior> getEnumerationBehavior() {
    return EnumSet.of(EnumerationBehavior.IGNORE_CHECK_VALVES);
  }

  @Override
  protected void beforeRetry() {}

  @Override
  protected boolean isDone() {
    return chunksWaitingOn == 0;
  }

  public int getLockChangeCount() {
    return lockChangeCount;
  }

  public boolean isLockFrames() {
    return lockFrames;
  }

  private void handleOutputBlock(Block block) {
    if (!visitedBlocks.add(CompactId.computeWorldlessBlockId(block)))
      return;

    ensureChunkIsLoaded(block, () -> {
      if (terminated)
        return;

      handleFramesOnBlock(block);

      Chest.Type type;

      if (block.getBlockData() instanceof Chest chest && (type = chest.getType()) != Chest.Type.SINGLE) {
        int dx = 0, dz = 0;

        // Left and right are relative to the chest itself, i.e. opposite to what
        // a player placing the appropriate block would see.

        switch (chest.getFacing()) {
          case NORTH: // -z
            dx = (type == Chest.Type.LEFT) ? 1 : -1;
            break;
          case SOUTH: // +z
            dx = (type == Chest.Type.LEFT) ? -1 : 1;
            break;
          case EAST: // +x
            dz = (type == Chest.Type.LEFT) ? 1 : -1;
            break;
          case WEST: // -x
            dz = (type == Chest.Type.LEFT) ? -1 : 1;
            break;
        }

        var otherChestBlock = block.getRelative(dx, 0, dz);

        if (visitedBlocks.add(CompactId.computeWorldlessBlockId(otherChestBlock)))
          handleFramesOnBlock(otherChestBlock);
      }

      callIfDone();
    });
  }

  private void handleFramesOnBlock(Block block) {
    for (var mountFace : FRAME_MOUNT_FACES) {
      var possibleFrameBlock = block.getRelative(mountFace);

      ensureChunkIsLoaded(possibleFrameBlock, () -> {
        for (var chunkEntity : possibleFrameBlock.getChunk().getEntities()) {
          if (!(chunkEntity instanceof ItemFrame itemFrame))
            continue;

          var frameFace = itemFrame.getAttachedFace();

          if (itemFrame.getLocation().getBlockX() + frameFace.getModX() != possibleFrameBlock.getX())
            continue;

          if (itemFrame.getLocation().getBlockY() + frameFace.getModY() != possibleFrameBlock.getY())
            continue;

          if (itemFrame.getLocation().getBlockZ() + frameFace.getModZ() != possibleFrameBlock.getZ())
            continue;

          handleItemFrame(itemFrame);
        }
      });
    }
  }

  private void handleItemFrame(ItemFrame frame) {
    var pdc = frame.getPersistentDataContainer();
    var lockFlag = pdc.get(lockedFrameKey, PersistentDataType.BOOLEAN);

    if (lockFlag != null && lockFlag) {
      if (!lockFrames) {
        pdc.set(lockedFrameKey, PersistentDataType.BOOLEAN, false);
        ++lockChangeCount;
      }

      return;
    }

    if (lockFrames) {
      pdc.set(lockedFrameKey, PersistentDataType.BOOLEAN, true);
      ++lockChangeCount;
    }
  }

  private void ensureChunkIsLoaded(Block block, Runnable handler) {
    var chunkX = block.getX() >> 4;
    var chunkZ = block.getZ() >> 4;

    if (world.isChunkLoaded(chunkX, chunkZ)) {
      handler.run();
      return;
    }

    ++chunksWaitingOn;
    world.getChunkAtAsync(chunkX, chunkZ, true, chunk -> {
      if (terminated)
        return;

      --chunksWaitingOn;
      handler.run();
    });
  }
}
