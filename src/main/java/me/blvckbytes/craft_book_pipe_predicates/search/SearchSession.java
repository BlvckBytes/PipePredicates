package me.blvckbytes.craft_book_pipe_predicates.search;

import com.sk89q.craftbook.mechanics.pipe.*;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.type.Chest;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Consumer;

public class SearchSession {

  // TODO: This limit should be configurable
  public static final int MAX_RETRY_COUNT  = 20 * 60; // ~1m

  private final Block origin;
  private final World world;

  private final Pipes pipesMechanic;
  private final Plugin plugin;
  private final Consumer<SearchSession> resultHandler;

  private final List<InventoryAndBlock> snapshotInventories;
  private final LongSet visitedPistons;
  private final EnumSet<SearchResultFlag> flags;

  private int tubeCount;
  private int containerCount;

  private int chunksWaitingOn;
  private boolean completedPistonSearch;

  private boolean terminated;

  public SearchSession(Block origin, Pipes pipesMechanic, Plugin plugin, Consumer<SearchSession> resultHandler) {
    this.origin = origin;
    this.world = origin.getWorld();

    this.pipesMechanic = pipesMechanic;
    this.plugin = plugin;
    this.resultHandler = resultHandler;

    this.snapshotInventories = new ArrayList<>();
    this.visitedPistons = new LongOpenHashSet();
    this.flags = EnumSet.noneOf(SearchResultFlag.class);
  }


  public int getTubeCount() {
    return tubeCount;
  }

  public int getContainerCount() {
    return containerCount;
  }

  public int getPistonCount() {
    return visitedPistons.size();
  }

  public boolean hasFlag(SearchResultFlag flag) {
    return flags.contains(flag);
  }

  public List<InventoryAndBlock> getSnapshotInventories() {
    return snapshotInventories;
  }

  public boolean didEncounterPipeBlocks() {
    return tubeCount > 0 || containerCount > 0 || !visitedPistons.isEmpty();
  }

  public void terminate() {
    this.terminated = true;
  }

  public void start() {
    _enumerateAllPistons(1);
  }

  private void callIfDone() {
    if (completedPistonSearch && chunksWaitingOn == 0)
      resultHandler.accept(this);
  }

  private void _enumerateAllPistons(int retryCount) {
    if (terminated)
      return;

    if (retryCount >= MAX_RETRY_COUNT) {
      flags.add(SearchResultFlag.EXCEEDED_MAX_RETRY_COUNT);
      completedPistonSearch = true;
      callIfDone();
      return;
    }

    // Reset the tube-count, since we may need multiple retries to walk the whole pipe.
    // No need to keep a separate visited-set for tubes - would be a waste of resources.
    tubeCount = 0;

    var result = pipesMechanic.enumeratePipeBlocks(origin, null, EnumSet.of(EnumerationBehavior.IGNORE_CHECK_VALVES), (block, cachedBlock) -> {
      if (CachedBlock.isTube(cachedBlock)) {
        ++tubeCount;
        return EnumerationDecision.CONTINUE;
      }

      if (!CachedBlock.isMaterial(cachedBlock, Material.PISTON))
        return EnumerationDecision.CONTINUE;

      if (visitedPistons.add(CompactId.computeWorldlessBlockId(block)))
        handlePossibleContainer(block.getRelative(CachedBlock.getFacing(cachedBlock)), true);

      return EnumerationDecision.CONTINUE;
    });

    if (result == EnumerationResult.EXCEEDED_TUBE_COUNT_LIMIT) {
      flags.add(SearchResultFlag.EXCEEDED_MAX_TUBE_COUNT);
      completedPistonSearch = true;
      callIfDone();
      return;
    }

    if (result == EnumerationResult.EXCEEDED_PISTON_COUNT_LIMIT) {
      flags.add(SearchResultFlag.EXCEEDED_MAX_PISTON_COUNT);
      completedPistonSearch = true;
      callIfDone();
      return;
    }

    if (!terminated && result != EnumerationResult.COMPLETED) {
      Bukkit.getScheduler().runTaskLater(plugin, () -> _enumerateAllPistons(retryCount + 1), 1);
      return;
    }

    completedPistonSearch = true;
    callIfDone();
  }

  private void handlePossibleContainer(Block container, boolean checkForDoubleChests) {
    var chunkX = container.getX() >> 4;
    var chunkZ = container.getZ() >> 4;

    if (world.isChunkLoaded(chunkX, chunkZ)) {
      handleState(container, container.getState(false), checkForDoubleChests);
      return;
    }

    ++chunksWaitingOn;
    world.getChunkAtAsync(chunkX, chunkZ, true, chunk -> {
      if (terminated)
        return;

      --chunksWaitingOn;
      handleState(container, container.getState(false), checkForDoubleChests);
    });
  }

  private void handleState(Block block, BlockState state, boolean checkForDoubleChests) {
    if (terminated)
      return;

    if (state instanceof Container container) {
      int slotOffset = 0;

      if (state.getBlockData() instanceof Chest chest) {
        var type = chest.getType();

        if (type == Chest.Type.LEFT)
          slotOffset = 3 * 9;

        if (checkForDoubleChests && type != Chest.Type.SINGLE) {
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

          // Avoid calling completion if the piston-loop is already done and this block is within
          // the same chunk; simply don't allow; simply don't allow other-halves to call completion.
          ++chunksWaitingOn;
          handlePossibleContainer(block.getRelative(dx, 0, dz), false);
          --chunksWaitingOn;
        }
      }

      // Do not count individual double-chest halves; if we're not checking for double-chests,
      // that means we're coming from one (as to prevent recursion), so don't increment again.
      if (checkForDoubleChests)
        ++containerCount;

      snapshotInventories.add(new InventoryAndBlock(container.getSnapshotInventory(), block, slotOffset));
    }

    callIfDone();
  }
}
