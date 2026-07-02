package me.blvckbytes.pipe_predicates.search;

import me.blvckbytes.bbtweaks.pipes.mechanic.*;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

public class VisualizeSession extends EnumerationSession<VisualizeSession> {

  // TODO: This should be configurable
  public static final int CUBE_COUNT_LIMIT = 1000;

  private static final TubeColor[] TUBE_COLORS = TubeColor.values();

  public final List<BlockAndColor> tubeBlocks;

  private boolean ranIntoLimit;

  private @Nullable TubeColor lastColor;
  private final List<Block> pendingPistons;
  private final Stack<@Nullable TubeColor> lastColorStack;

  public VisualizeSession(
    Block origin, PipesApi pipesApi, Plugin plugin,
    Consumer<VisualizeSession> warmupHandler,
    Consumer<VisualizeSession> completionHandler
  ) {
    super(origin, pipesApi, plugin, warmupHandler, completionHandler);

    this.tubeBlocks = new ArrayList<>();
    this.pendingPistons = new ArrayList<>();
    this.lastColorStack = new Stack<>();
  }

  public boolean didRunIntoLimit() {
    return ranIntoLimit;
  }

  @Override
  protected boolean isDone() {
    return true;
  }

  @Override
  protected void beforeRetry() {
    this.tubeBlocks.clear();
  }

  @Override
  protected void beforeCompletion() {
    completePendingPistons();
  }

  @Override
  protected void beforeSubPipe() {
    lastColorStack.push(lastColor);
  }

  @Override
  protected void afterSubPipe() {
    lastColor = lastColorStack.pop();
  }

  @Override
  protected EnumSet<EnumerationBehavior> getEnumerationBehavior() {
    // Do not ignore check-valves when visualizing
    return EnumSet.noneOf(EnumerationBehavior.class);
  }

  @Override
  protected EnumerationDecision onTube(Block block, int cachedBlock) {
    lastColor = TUBE_COLORS[CachedBlock.getTubeColorOrdinal(cachedBlock)];

    completePendingPistons();

    tubeBlocks.add(new BlockAndColor(block, lastColor));

    return increaseCountAndCheckLimit();
  }

  @Override
  protected EnumerationDecision onPiston(Block block, int cachedBlock) {
    if (lastColor == null)
      pendingPistons.add(block);
    else
      tubeBlocks.add(new BlockAndColor(block, lastColor));

    return increaseCountAndCheckLimit();
  }

  private EnumerationDecision increaseCountAndCheckLimit() {
    if (tubeBlocks.size() >= CUBE_COUNT_LIMIT) {
      ranIntoLimit = true;
      return EnumerationDecision.STOP;
    }

    return EnumerationDecision.CONTINUE;
  }

  private void completePendingPistons() {
    if (pendingPistons.isEmpty())
      return;

    var color = lastColor;

    if (color == null)
      color = TubeColor.WHITE;

    for (var pendingPiston : pendingPistons)
      tubeBlocks.add(new BlockAndColor(pendingPiston, color));

    pendingPistons.clear();
  }
}
