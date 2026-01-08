package me.blvckbytes.craft_book_pipe_predicates.search;

import com.comphenix.protocol.wrappers.EnumWrappers;
import com.sk89q.craftbook.mechanics.pipe.CachedBlock;
import com.sk89q.craftbook.mechanics.pipe.EnumerationDecision;
import com.sk89q.craftbook.mechanics.pipe.Pipes;
import com.sk89q.craftbook.mechanics.pipe.TubeColor;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

public class VisualizeSession extends EnumerationSession<VisualizeSession> {

  // TODO: This should be configurable
  public static final int TUBE_COUNT_LIMIT = 1000;

  private static final EnumWrappers.ChatFormatting[] chatFormattingByTubeColorOrdinal;

  static {
    chatFormattingByTubeColorOrdinal = new EnumWrappers.ChatFormatting[TubeColor.values().length];
    Arrays.fill(chatFormattingByTubeColorOrdinal, EnumWrappers.ChatFormatting.WHITE);

    chatFormattingByTubeColorOrdinal[TubeColor.ORANGE.ordinal()] = EnumWrappers.ChatFormatting.GOLD;
    chatFormattingByTubeColorOrdinal[TubeColor.MAGENTA.ordinal()] = EnumWrappers.ChatFormatting.LIGHT_PURPLE;
    chatFormattingByTubeColorOrdinal[TubeColor.LIGHT_BLUE.ordinal()] = EnumWrappers.ChatFormatting.BLUE;
    chatFormattingByTubeColorOrdinal[TubeColor.YELLOW.ordinal()] = EnumWrappers.ChatFormatting.YELLOW;
    chatFormattingByTubeColorOrdinal[TubeColor.LIME.ordinal()] = EnumWrappers.ChatFormatting.GREEN;
    chatFormattingByTubeColorOrdinal[TubeColor.PINK.ordinal()] = EnumWrappers.ChatFormatting.LIGHT_PURPLE;
    chatFormattingByTubeColorOrdinal[TubeColor.GRAY.ordinal()] = EnumWrappers.ChatFormatting.DARK_GRAY;
    chatFormattingByTubeColorOrdinal[TubeColor.LIGHT_GRAY.ordinal()] = EnumWrappers.ChatFormatting.GRAY;
    chatFormattingByTubeColorOrdinal[TubeColor.CYAN.ordinal()] = EnumWrappers.ChatFormatting.AQUA;
    chatFormattingByTubeColorOrdinal[TubeColor.PURPLE.ordinal()] = EnumWrappers.ChatFormatting.DARK_PURPLE;
    chatFormattingByTubeColorOrdinal[TubeColor.BLUE.ordinal()] = EnumWrappers.ChatFormatting.DARK_BLUE;
    chatFormattingByTubeColorOrdinal[TubeColor.GREEN.ordinal()] = EnumWrappers.ChatFormatting.DARK_GREEN;
    chatFormattingByTubeColorOrdinal[TubeColor.RED.ordinal()] = EnumWrappers.ChatFormatting.RED;
    chatFormattingByTubeColorOrdinal[TubeColor.BLACK.ordinal()] = EnumWrappers.ChatFormatting.BLACK;
  }

  public final Map<EnumWrappers.ChatFormatting, List<Vector>> cubePositionsByColor;

  private int cubeCount;

  private boolean ranIntoLimit;

  private @Nullable EnumWrappers.ChatFormatting lastColor;
  private final List<Block> pendingPistons;

  public VisualizeSession(
    Block origin, Pipes pipesMechanic, Plugin plugin,
    Consumer<VisualizeSession> warmupHandler,
    Consumer<VisualizeSession> completionHandler
  ) {
    super(origin, pipesMechanic, plugin, warmupHandler, completionHandler);

    this.cubePositionsByColor = new HashMap<>();
    this.pendingPistons = new ArrayList<>();
  }

  public boolean didRunIntoLimit() {
    return ranIntoLimit;
  }

  public int getCubeCount() {
    return cubeCount;
  }

  @Override
  protected boolean isDone() {
    return true;
  }

  @Override
  protected void beforeRetry() {
    this.cubePositionsByColor.clear();
    cubeCount = 0;
  }

  @Override
  protected void beforeCompletion() {
    completePendingPistons();
  }

  @Override
  protected EnumerationDecision onTube(Block block, int cachedBlock) {
    lastColor = chatFormattingByTubeColorOrdinal[CachedBlock.getTubeColorOrdinal(cachedBlock)];

    completePendingPistons();

    makeCubeEntry(lastColor, block);

    return increaseCountAndCheckLimit();
  }

  @Override
  protected EnumerationDecision onPiston(Block block, int cachedBlock) {
    if (lastColor == null)
      pendingPistons.add(block);
    else
      makeCubeEntry(lastColor, block);

    // TODO: Keep set of visited chunks and for each entity in there, add it to a list if it
    //       has the glowing effect enabled. Disable it while the visualization is active
    //       and restore it later on again.

    return increaseCountAndCheckLimit();
  }

  private EnumerationDecision increaseCountAndCheckLimit() {
    if (++cubeCount >= TUBE_COUNT_LIMIT) {
      ranIntoLimit = true;
      return EnumerationDecision.STOP;
    }

    return EnumerationDecision.CONTINUE;
  }

  private void makeCubeEntry(EnumWrappers.ChatFormatting color, Block block) {
    var colorBucket = cubePositionsByColor.computeIfAbsent(color, k -> new ArrayList<>());
    colorBucket.add(new Vector(block.getX(), block.getY(), block.getZ()));
  }

  private void completePendingPistons() {
    if (pendingPistons.isEmpty())
      return;

    var color = lastColor;

    if (color == null)
      color = EnumWrappers.ChatFormatting.WHITE;

    for (var pendingPiston : pendingPistons)
      makeCubeEntry(color, pendingPiston);

    pendingPistons.clear();
  }
}
