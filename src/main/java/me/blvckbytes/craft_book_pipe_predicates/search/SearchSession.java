package me.blvckbytes.craft_book_pipe_predicates.search;

import com.sk89q.craftbook.mechanics.pipe.*;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import me.blvckbytes.craft_book_pipe_predicates.PistonPredicateRegistry;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Chest;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class SearchSession extends EnumerationSession<SearchSession> {

  private enum HandleFlag {
    IGNORE_OTHER_CHEST_HALF,
    CHECK_ONLY_FOR_HOPPERS,
  }

  static class MutableInt { int value; }

  private final PistonPredicateRegistry predicateRegistry;
  private final EnumSet<EnumerationBehavior> behaviorFlags;
  private final World world;

  private final List<SearchedInventory> searchedInventories;
  private final LongSet visitedBlocks;

  private int tubeCount;
  private int pistonCount;

  private int chunksWaitingOn;

  private final EnumMap<Material, MutableInt> containerCountByType;

  private @Nullable PredicateAndLabels lastPredicate;
  private final Stack<@Nullable PredicateAndLabels> lastPredicateStack;

  public SearchSession(
    Block origin, Pipes pipesMechanic, Plugin plugin,
    PistonPredicateRegistry predicateRegistry,
    EnumSet<EnumerationBehavior> behaviorFlags,
    Consumer<SearchSession> warmupHandler,
    Consumer<SearchSession> completionHandler
  ) {
    super(origin, pipesMechanic, plugin, warmupHandler, completionHandler);

    this.predicateRegistry = predicateRegistry;
    this.behaviorFlags = behaviorFlags;
    this.world = origin.getWorld();
    this.searchedInventories = new ArrayList<>();
    this.visitedBlocks = new LongOpenHashSet();
    this.containerCountByType = new EnumMap<>(Material.class);
    this.lastPredicateStack = new Stack<>();
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
    return pistonCount;
  }

  public List<SearchedInventory> getSearchedInventories() {
    return searchedInventories;
  }

  @Override
  protected EnumerationDecision onTube(Block block, int cachedBlock) {
    ++tubeCount;
    return EnumerationDecision.CONTINUE;
  }

  @Override
  protected EnumerationDecision onPiston(Block block, int cachedBlock) {
    // TODO: This does not check for whether we are walking in the opposite direction of a check-valve, which
    //       then completely changes the algorithm; in that case, we need to keep a list of inventories without
    //       a predicate and set it once we encounter the first check-valve that points in our direction while walking.
    lastPredicate = PredicateAndLabels.of(predicateRegistry.getPredicateForPiston(block));

    if (handleBlock(block.getRelative(CachedBlock.getFacing(cachedBlock)), EnumSet.noneOf(HandleFlag.class)))
      ++pistonCount;

    return EnumerationDecision.CONTINUE;
  }

  @Override
  protected void beforeCompletion() {}

  @Override
  protected void beforeSubPipe() {
    lastPredicateStack.push(lastPredicate);
  }

  @Override
  protected void afterSubPipe() {
    lastPredicateStack.pop();
  }

  @Override
  protected EnumSet<EnumerationBehavior> getEnumerationBehavior() {
    return EnumSet.copyOf(behaviorFlags);
  }

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

  private boolean handleBlock(Block block, EnumSet<HandleFlag> flags) {
    if (!visitedBlocks.add(CompactId.computeWorldlessBlockId(block)))
      return false;

    ensureChunkIsLoaded(block, () -> {
      if (terminated)
        return;

      var blockData = block.getBlockData();

      if (blockData.getMaterial() == Material.HOPPER) {
        var hopperFacing = ((Directional) blockData).getFacing();
        handleBlock(block.getRelative(hopperFacing), EnumSet.noneOf(HandleFlag.class));
      }

      else if (flags.contains(HandleFlag.CHECK_ONLY_FOR_HOPPERS)) {
        callIfDone();
        return;
      }

      if (block.getState(false) instanceof Container container)
        handleContainer(block, blockData, container, flags);

      callIfDone();
    });

    return true;
  }

  private void handleContainer(Block block, BlockData blockData, Container container, EnumSet<HandleFlag> flags) {
    int slotOffset = 0;

    var ignoreOtherChestHalf = flags.contains(HandleFlag.IGNORE_OTHER_CHEST_HALF);

    Block otherChestBlock = null;

    if (blockData instanceof Chest chest) {
      var type = chest.getType();

      if (type == Chest.Type.LEFT)
        slotOffset = 3 * 9;

      if (type != Chest.Type.SINGLE) {
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

        otherChestBlock = block.getRelative(dx, 0, dz);

        if (!ignoreOtherChestHalf) {
          // Avoid calling completion if the piston-loop is already done and this block is within
          // the same chunk; simply don't allow; simply don't allow other-halves to call completion.
          ++chunksWaitingOn;
          handleBlock(otherChestBlock, EnumSet.of(HandleFlag.IGNORE_OTHER_CHEST_HALF));
          --chunksWaitingOn;
        }
      }
    }

    var blockType = block.getType();

    // Do not count individual double-chest halves; if we're not checking for double-chests,
    // that means we're coming from one (as to prevent recursion), so don't increment again.
    if (!ignoreOtherChestHalf)
      containerCountByType.computeIfAbsent(blockType, k -> new MutableInt()).value++;

    searchedInventories.add(new SearchedInventory(container.getSnapshotInventory(), block, otherChestBlock, blockType, slotOffset, getCurrentlyActivePredicates()));

    // Hoppers are only funneling out of containers if they sit right below them, which makes
    // them become part of the chain items may travel down, so they are also walked into.
    handleBlock(block.getRelative(BlockFace.DOWN), EnumSet.of(HandleFlag.CHECK_ONLY_FOR_HOPPERS));
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

  private List<PredicateAndLabels> getCurrentlyActivePredicates() {
    if (lastPredicate == null && lastPredicateStack.isEmpty())
      return Collections.emptyList();

    var result = new ArrayList<PredicateAndLabels>(lastPredicateStack.size());

    for (var predicate : lastPredicateStack) {
      if (predicate != null)
        result.add(predicate);
    }

    if (lastPredicate != null)
      result.add(lastPredicate);

    return result;
  }
}
