package me.blvckbytes.craft_book_pipe_predicates.search;

import com.sk89q.craftbook.mechanics.pipe.CompactBlockId;
import com.sk89q.craftbook.mechanics.pipe.PipeWalkResult;
import com.sk89q.craftbook.mechanics.pipe.Pipes;
import com.sk89q.craftbook.util.ItemUtil;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
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

  private final LongSet seenPistons;
  private final LongSet seenPipes;
  private final List<Block> pistonBlocks;
  private final EnumSet<PistonSearchFlag> flags;

  private boolean terminated;

  public PistonSearchSession(Block origin, Pipes pipesMechanic, Plugin plugin, PistonSearchResultHandler resultHandler) {
    this.origin = origin;
    this.pipesMechanic = pipesMechanic;
    this.plugin = plugin;
    this.resultHandler = resultHandler;

    this.seenPistons = new LongOpenHashSet();
    this.seenPipes = new LongOpenHashSet();
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

  private boolean isPipeBlock(Material material) {
    if (material == Material.GLASS || material == Material.GLASS_PANE)
      return true;

    return ItemUtil.isStainedGlass(material) || ItemUtil.isStainedGlassPane(material);
  }

  private void _enumerateAllPistons(int retryCount) {
    if (retryCount >= MAX_RETRY_COUNT) {
      flags.add(PistonSearchFlag.EXCEEDED_MAX_RETRY_COUNT);
      resultHandler.handle(pistonBlocks, flags);
      return;
    }

    var result = pipesMechanic.enumeratePipeBlocks(origin, new LongOpenHashSet(), (block, cachedBlock) -> {
      var compactId = CompactBlockId.computeWorldlessId(block);

      if (isPipeBlock(cachedBlock.type)) {
        seenPipes.add(compactId);

        if (seenPipes.size() >= MAX_PIPE_COUNT) {
          flags.add(PistonSearchFlag.EXCEEDED_MAX_PIPE_COUNT);
          return PipeWalkResult.DONE;
        }

        return PipeWalkResult.CONTINUE;
      }

      if (cachedBlock.type != Material.PISTON)
        return PipeWalkResult.CONTINUE;

      if (seenPistons.add(compactId)) {
        pistonBlocks.add(block);

        if (pistonBlocks.size() >= MAX_PISTON_COUNT) {
          flags.add(PistonSearchFlag.EXCEEDED_MAX_PISTON_COUNT);
          return PipeWalkResult.DONE;
        }
      }

      return PipeWalkResult.CONTINUE;
    });

    if (!terminated && (result == PipeWalkResult.EXCEEDED_CACHE_MISSES || result == PipeWalkResult.NEEDS_CHUNK_LOADING)) {
      Bukkit.getScheduler().runTaskLater(plugin, () -> _enumerateAllPistons(retryCount + 1), 1);
      return;
    }

    resultHandler.handle(pistonBlocks, flags);
  }
}
