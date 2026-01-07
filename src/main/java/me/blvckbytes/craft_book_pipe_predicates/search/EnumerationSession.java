package me.blvckbytes.craft_book_pipe_predicates.search;

import com.sk89q.craftbook.mechanics.pipe.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

import java.util.EnumSet;
import java.util.function.Consumer;

public abstract class EnumerationSession<T extends EnumerationSession<T>> {

  // TODO: This limit should be configurable
  public static final int MAX_RETRY_COUNT  = 20 * 60; // ~1m

  private final Block origin;

  private final Pipes pipesMechanic;
  private final Plugin plugin;
  private final Consumer<T> warmupHandler;
  private final Consumer<T> completionHandler;

  private final EnumSet<SearchResultFlag> flags;

  protected boolean terminated;
  private boolean completedEnumeration;

  private int pistonCount;
  private int tubeCount;

  protected EnumerationSession(
    Block origin, Pipes pipesMechanic, Plugin plugin,
    Consumer<T> warmupHandler,
    Consumer<T> completionHandler
  ) {
    this.origin = origin;

    this.pipesMechanic = pipesMechanic;
    this.plugin = plugin;
    this.warmupHandler = warmupHandler;
    this.completionHandler = completionHandler;

    this.flags = EnumSet.noneOf(SearchResultFlag.class);
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

    pistonCount = 0;
    tubeCount = 0;

    var result = pipesMechanic.enumeratePipeBlocks(origin, null, EnumSet.of(EnumerationBehavior.IGNORE_CHECK_VALVES), (block, cachedBlock) -> {
      if (CachedBlock.isTube(cachedBlock)) {
        ++tubeCount;
        return onTube(block, cachedBlock);
      }

      if (!CachedBlock.isMaterial(cachedBlock, Material.PISTON))
        return EnumerationDecision.CONTINUE;

      ++pistonCount;

      return onPiston(block, cachedBlock);
    });

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
}
