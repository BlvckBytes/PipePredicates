package me.blvckbytes.pipe_predicates.search;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import me.blvckbytes.bbtweaks.pipes.*;
import me.blvckbytes.bbtweaks.util.CompactId;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

import java.util.EnumSet;
import java.util.function.Consumer;

public abstract class EnumerationSession<T extends EnumerationSession<T>> {

  // TODO: This limit should be configurable
  public static final int MAX_RETRY_COUNT  = 20 * 60; // ~1m

  public final Block origin;

  private final PipesApi pipesApi;
  private final Plugin plugin;
  private final Consumer<T> warmupHandler;
  private final Consumer<T> completionHandler;

  private final EnumSet<SearchResultFlag> flags;
  private final LongSet visitedBlocks;

  protected boolean terminated;
  private boolean completedEnumeration;

  private int pistonCount;
  private int tubeCount;

  protected EnumerationSession(
    Block origin, PipesApi pipesApi, Plugin plugin,
    Consumer<T> warmupHandler,
    Consumer<T> completionHandler
  ) {
    this.origin = origin;

    this.pipesApi = pipesApi;
    this.plugin = plugin;
    this.warmupHandler = warmupHandler;
    this.completionHandler = completionHandler;

    this.flags = EnumSet.noneOf(SearchResultFlag.class);
    this.visitedBlocks = new LongOpenHashSet();
  }

  public boolean hasFlag(SearchResultFlag flag) {
    return flags.contains(flag);
  }

  public void terminate() {
    this.terminated = true;
  }

  public void start() {
    _enumerateAllPistons(1);
  }

  public int getPistonCount() {
    return pistonCount;
  }

  public int getTubeCount() {
    return tubeCount;
  }

  public boolean didEncounterPipeBlocks() {
    return tubeCount > 0 || pistonCount > 0;
  }

  protected abstract boolean isDone();

  protected abstract void beforeRetry();

  protected abstract EnumerationDecision onTube(Block block, int cachedBlock);

  protected abstract EnumerationDecision onPiston(Block block, int cachedBlock);

  protected abstract void beforeCompletion();

  protected abstract void beforeSubPipe();

  protected abstract void afterSubPipe();

  protected abstract EnumSet<EnumerationBehavior> getEnumerationBehavior();

  protected void callIfDone() {
    if (completedEnumeration && isDone()) {
      beforeCompletion();

      //noinspection unchecked
      completionHandler.accept((T) this);
    }
  }

  private void _enumerateAllPistons(int retryCount) {
    if (terminated)
      return;

    if (retryCount >= MAX_RETRY_COUNT) {
      flags.add(SearchResultFlag.EXCEEDED_MAX_RETRY_COUNT);
      completedEnumeration = true;
      callIfDone();
      return;
    }

    beforeRetry();

    visitedBlocks.clear();

    pistonCount = 0;
    tubeCount = 0;

    var result = pipesApi.enumeratePipeBlocks(origin, visitedBlocks, getEnumerationBehavior(), this::handlePipeEnumeration);

    if (result == EnumerationResult.EXCEEDED_TUBE_COUNT_LIMIT) {
      flags.add(SearchResultFlag.EXCEEDED_MAX_TUBE_COUNT);
      completedEnumeration = true;
      callIfDone();
      return;
    }

    if (result == EnumerationResult.EXCEEDED_PISTON_COUNT_LIMIT) {
      flags.add(SearchResultFlag.EXCEEDED_MAX_PISTON_COUNT);
      completedEnumeration = true;
      callIfDone();
      return;
    }

    if (!terminated && result != EnumerationResult.COMPLETED) {
      //noinspection unchecked
      warmupHandler.accept((T) this);
      Bukkit.getScheduler().runTaskLater(plugin, () -> _enumerateAllPistons(retryCount + 1), 1);
      return;
    }

    completedEnumeration = true;
    callIfDone();
  }

  private EnumerationDecision handlePipeEnumeration(Block pipeBlock, int cachedPipeBlock, CachedBlockResolver cache) throws LoadingChunkException {
    if (CachedBlock.isTube(cachedPipeBlock)) {
      ++tubeCount;
      return onTube(pipeBlock, cachedPipeBlock);
    }

    if (!CachedBlock.isMaterial(cachedPipeBlock, Material.PISTON))
      return EnumerationDecision.CONTINUE;

    ++pistonCount;

    if (onPiston(pipeBlock, cachedPipeBlock) != EnumerationDecision.CONTINUE)
      return EnumerationDecision.STOP;

    var putBlock = pipeBlock.getRelative(CachedBlock.getFacing(cachedPipeBlock));
    int cachedPutBlock = cache.getCachedBlock(putBlock);
    boolean isSubPipe = CachedBlock.isTube(cachedPutBlock) && !CachedBlock.isPane(cachedPutBlock);

    // Recurse down into the sub-pipe first, as for the piston/tube encounter-order to end up congruent
    // with the real-world pipe - this is crucial for stepwise visualization later on.
    if (isSubPipe) {
      var behaviorFlags = getEnumerationBehavior();
      behaviorFlags.add(EnumerationBehavior.DO_NOT_RESET_CACHE_AND_MAX_COUNTERS);

      visitedBlocks.add(CompactId.computeWorldlessBlockId(putBlock));

      beforeSubPipe();
      pipesApi.enumeratePipeBlocks(putBlock, visitedBlocks, behaviorFlags, this::handlePipeEnumeration);
      afterSubPipe();
    }

    return EnumerationDecision.CONTINUE;
  }
}
