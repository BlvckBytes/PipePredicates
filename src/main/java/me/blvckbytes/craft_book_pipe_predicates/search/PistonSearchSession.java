package me.blvckbytes.craft_book_pipe_predicates.search;

import com.sk89q.craftbook.mechanics.pipe.*;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class PistonSearchSession implements SearchSession {

  // TODO: These limits should be configurable
  public static final int MAX_RETRY_COUNT  = 20 * 60; // ~1m
  public static final int MAX_PISTON_COUNT = 2500;
  public static final int MAX_PIPE_COUNT   = 10_000;

  private final Block origin;

  private final Pipes pipesMechanic;
  private final Plugin plugin;
  private final PistonSearchResultHandler resultHandler;

  private final List<Block> pistonBlocks;
  private final EnumSet<PistonSearchFlag> flags;

  int pipeCounter;
  int pistonCounter;

  private boolean terminated;

  public PistonSearchSession(Block origin, Pipes pipesMechanic, Plugin plugin, PistonSearchResultHandler resultHandler) {
    this.origin = origin;

    this.pipesMechanic = pipesMechanic;
    this.plugin = plugin;
    this.resultHandler = resultHandler;

    this.pistonBlocks = new ArrayList<>();
    this.flags = EnumSet.noneOf(PistonSearchFlag.class);
  }

  @Override
  public void terminate() {
    this.terminated = true;
  }

  public void start() {
    _enumerateAllPistons(1);
  }

  private void _enumerateAllPistons(int retryCount) {
    if (retryCount >= MAX_RETRY_COUNT) {
      flags.add(PistonSearchFlag.EXCEEDED_MAX_RETRY_COUNT);
      resultHandler.handle(pistonBlocks, flags);
      return;
    }

    EnumerationResult result;

    try {
      pistonCounter = pipeCounter = 0;

      result = pipesMechanic.enumeratePipeBlocks(origin, new LongOpenHashSet(), (block, cachedBlock) -> {
        if (CachedBlock.isTube(cachedBlock)) {
          if (++pipeCounter >= MAX_PIPE_COUNT) {
            flags.add(PistonSearchFlag.EXCEEDED_MAX_PIPE_COUNT);
            return EnumerationHandleResult.DONE;
          }

          return EnumerationHandleResult.CONTINUE;
        }

        if (!CachedBlock.isMaterial(cachedBlock, Material.PISTON))
          return EnumerationHandleResult.CONTINUE;

        if (++pistonCounter >= MAX_PISTON_COUNT) {
          flags.add(PistonSearchFlag.EXCEEDED_MAX_PISTON_COUNT);
          return EnumerationHandleResult.DONE;
        }

        pistonBlocks.add(block);

        return EnumerationHandleResult.CONTINUE;
      });
    } catch (LoadingChunkException e) {
      result = EnumerationResult.STOPPED_EARLY;
    }

    if (!terminated && result != EnumerationResult.COMPLETED) {
      Bukkit.getScheduler().runTaskLater(plugin, () -> _enumerateAllPistons(retryCount + 1), 1);
      return;
    }

    resultHandler.handle(pistonBlocks, flags);
  }
}
