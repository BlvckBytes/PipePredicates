package me.blvckbytes.craft_book_pipe_predicates.search;

import com.sk89q.craftbook.mechanics.pipe.*;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.type.Chest;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class SearchSession extends EnumerationSession<SearchSession> {

  static class MutableInt { int value; }

  private final World world;

  private final List<InventoryAndBlock> snapshotInventories;
  private final LongSet visitedPistons;

  private int tubeCount;

  private int chunksWaitingOn;

  private final EnumMap<Material, MutableInt> containerCountByType;

  public SearchSession(
    Block origin, Pipes pipesMechanic, Plugin plugin,
    Consumer<SearchSession> warmupHandler,
    Consumer<SearchSession> completionHandler
  ) {
    super(origin, pipesMechanic, plugin, warmupHandler, completionHandler);
    this.world = origin.getWorld();

    this.snapshotInventories = new ArrayList<>();
    this.visitedPistons = new LongOpenHashSet();
    this.containerCountByType = new EnumMap<>(Material.class);
  }

  public int getTubeCount() {
    return tubeCount;
  }

  public int forEachContainerCountAndGetSum(BiConsumer<Material, Integer> handler) {
    var materials = new ArrayList<>(containerCountByType.keySet());
    var sum = 0;

    materials.sort(Comparator.comparingInt(Enum::ordinal));

    for (var material : materials) {
      var materialCount = containerCountByType.get(material).value;
      sum += materialCount;
      handler.accept(material, materialCount);
    }

    return sum;
  }

  public int getPistonCount() {
    return visitedPistons.size();
  }

  public List<InventoryAndBlock> getSnapshotInventories() {
    return snapshotInventories;
  }

  @Override
  protected EnumerationDecision onTube(Block block, int cachedBlock) {
    ++tubeCount;
    return EnumerationDecision.CONTINUE;
  }

  @Override
  protected EnumerationDecision onPiston(Block block, int cachedBlock) {
    if (visitedPistons.add(CompactId.computeWorldlessBlockId(block)))
      handlePossibleContainer(block.getRelative(CachedBlock.getFacing(cachedBlock)), true);

    return EnumerationDecision.CONTINUE;
  }

  @Override
  protected void beforeCompletion() {}

  @Override
  protected void beforeRetry() {
    // Reset the tube-count, since we may need multiple retries to walk the whole pipe.
    // No need to keep a separate visited-set for tubes - would be a waste of resources.
    tubeCount = 0;
  }

  @Override
  protected boolean isDone() {
    return chunksWaitingOn == 0;
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
        containerCountByType.computeIfAbsent(block.getType(), k -> new MutableInt()).value++;

      snapshotInventories.add(new InventoryAndBlock(container.getSnapshotInventory(), block, slotOffset));
    }

    callIfDone();
  }
}
