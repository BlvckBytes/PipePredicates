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
        if (!CachedBlock.isMaterial(cachedBlock, Material.PISTON))
          return EnumerationHandleResult.CONTINUE;

        pistonBlocks.add(block);

        return EnumerationHandleResult.CONTINUE;
      });
    } catch (LoadingChunkException e) {
      result = EnumerationResult.STILL_WARMING_UP;
    }

    if (result == EnumerationResult.EXCEEDED_TUBE_COUNT_LIMIT) {
      flags.add(PistonSearchFlag.EXCEEDED_MAX_TUBE_COUNT);
      return;
    }

    if (result == EnumerationResult.EXCEEDED_PISTON_COUNT_LIMIT) {
      flags.add(PistonSearchFlag.EXCEEDED_MAX_PISTON_COUNT);
      return;
    }

    if (!terminated && result != EnumerationResult.COMPLETED) {
      Bukkit.getScheduler().runTaskLater(plugin, () -> _enumerateAllPistons(retryCount + 1), 1);
      return;
    }

    resultHandler.handle(pistonBlocks, flags);
  }
}
