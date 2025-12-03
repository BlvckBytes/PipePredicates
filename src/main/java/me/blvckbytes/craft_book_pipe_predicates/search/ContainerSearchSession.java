package me.blvckbytes.craft_book_pipe_predicates.search;

import com.sk89q.craftbook.mechanics.pipe.Pipes;
import org.bukkit.World;
import org.bukkit.block.*;
import org.bukkit.block.data.type.Chest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ContainerSearchSession implements SearchSession {

  private final List<Block> pistons;
  private final World world;
  private final Consumer<List<InventoryAndBlock>> completion;
  private final List<InventoryAndBlock> containerInventories;

  private int chunksWaitingOn;
  private boolean completedPistonLoop;

  private boolean terminated;

  public ContainerSearchSession(List<Block> pistons, Consumer<List<InventoryAndBlock>> completion) {
    if (pistons.isEmpty())
      throw new IllegalStateException("Cannot search for containers in an empty list!");

    this.pistons = pistons;
    this.world = pistons.getFirst().getWorld();
    this.completion = completion;
    this.containerInventories = new ArrayList<>();
  }

  public void start() {
    var lastIndex = pistons.size() - 1;

    for (var i = 0; i <= lastIndex; ++i) {
      var piston = pistons.get(i);

      if (i == lastIndex)
        completedPistonLoop = true;

      var chunkX = piston.getX() >> 4;
      var chunkZ = piston.getZ() >> 4;

      if (world.isChunkLoaded(chunkX, chunkZ)) {
        var cachedPiston = Pipes.pipeBlockCache.getCachedBlock(piston);
        handlePossibleContainer(piston.getRelative(cachedPiston.getFacing(piston)), true);
        continue;
      }

      ++chunksWaitingOn;
      world.getChunkAtAsync(chunkX, chunkZ, true, chunk -> {
        if (terminated)
          return;

        --chunksWaitingOn;

        var cachedPiston = Pipes.pipeBlockCache.getCachedBlock(piston);
        handlePossibleContainer(piston.getRelative(cachedPiston.getFacing(piston)), true);
      });
    }
  }

  @Override
  public void terminate() {
    this.terminated = true;
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
      if (checkForDoubleChests && state.getBlockData() instanceof Chest chest) {
        var type = chest.getType();

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

          // Avoid calling completion if the piston-loop is already done and this block is within
          // the same chunk; simply don't allow; simply don't allow other-halves to call completion.
          ++chunksWaitingOn;
          handlePossibleContainer(block.getRelative(dx, 0, dz), false);
          --chunksWaitingOn;
        }
      }

      containerInventories.add(new InventoryAndBlock(container.getSnapshotInventory(), block));
    }

    if (completedPistonLoop && chunksWaitingOn == 0)
      this.completion.accept(containerInventories);
  }
}
