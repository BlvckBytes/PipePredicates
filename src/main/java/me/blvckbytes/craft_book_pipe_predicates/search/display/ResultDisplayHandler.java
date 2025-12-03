package me.blvckbytes.craft_book_pipe_predicates.search.display;

import me.blvckbytes.bukkitevaluable.ConfigKeeper;
import me.blvckbytes.craft_book_pipe_predicates.config.MainSection;
import me.blvckbytes.craft_book_pipe_predicates.search.ItemAndSlot;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.plugin.Plugin;

public class ResultDisplayHandler extends DisplayHandler<ResultDisplay, ResultDisplayData> {

  public ResultDisplayHandler(ConfigKeeper<MainSection> config, Plugin plugin) {
    super(config, plugin);
  }

  @Override
  public ResultDisplay instantiateDisplay(Player player, ResultDisplayData displayData) {
    return new ResultDisplay(config, plugin, player, displayData);
  }

  @Override
  protected void handleClick(Player player, ResultDisplay display, ClickType clickType, int slot) {
    var targetItem = display.getShopCorrespondingToSlot(slot);

    if (clickType == ClickType.LEFT) {
      if (config.rootSection.resultDisplay.items.previousPage.getDisplaySlots().contains(slot)) {
        display.previousPage();
        return;
      }

      if (config.rootSection.resultDisplay.items.nextPage.getDisplaySlots().contains(slot)) {
        display.nextPage();
        return;
      }

      if (targetItem != null) {
        player.closeInventory();
        teleportPlayerToContainer(player, targetItem.block());
        return;
      }

      return;
    }

    if (clickType == ClickType.DROP) {
      if (targetItem != null) {
        moveItemIntoInventory(display, player, targetItem);
        return;
      }

      return;
    }

    if (clickType == ClickType.RIGHT) {
      if (config.rootSection.resultDisplay.items.previousPage.getDisplaySlots().contains(slot)) {
        display.firstPage();
        return;
      }

      if (config.rootSection.resultDisplay.items.nextPage.getDisplaySlots().contains(slot))
        display.lastPage();
    }
  }

  private void teleportPlayerToContainer(Player player, Block block) {
    var blockCenter = block.getLocation().add(.5, .5, .5);

    if (block.getBlockData() instanceof Directional directional) {
      var facing = directional.getFacing();
      block = block.getRelative(facing.getModX() * 2, facing.getModY() * 2, facing.getModZ() * 2);
    }

    var footLocation = block.getLocation();

    var eyeLocation = footLocation.clone().add(0, 1.6, 0);
    var direction = blockCenter.toVector().subtract(eyeLocation.toVector()).normalize();
    footLocation.setDirection(direction);

    player.teleport(footLocation);
  }

  private void moveItemIntoInventory(ResultDisplay resultDisplay, Player player, ItemAndSlot item) {
    var containerBlock = item.block();
    var world = containerBlock.getWorld();

    var chunkX = containerBlock.getX() >> 4;
    var chunkZ = containerBlock.getZ() >> 4;

    if (world.isChunkLoaded(chunkX, chunkZ)) {
      _moveItemIntoInventory(resultDisplay, player, item);
      return;
    }

    world.getChunkAtAsync(chunkX, chunkZ, true, chunk -> _moveItemIntoInventory(resultDisplay, player, item));
  }

  // TODO: These messages should be configurable

  private void _moveItemIntoInventory(ResultDisplay resultDisplay, Player player, ItemAndSlot item) {
    var blockState = item.block().getState();

    if (!(blockState instanceof Container container)) {
      player.sendMessage("§cThe container no longer exists; cancelling!");
      return;
    }

    var containerInventory = container.getInventory();
    var blockContents = containerInventory.getStorageContents();

    if (item.slot() < 0 || item.slot() >= blockContents.length) {
      player.sendMessage("§cThe container-size changed in the mean-time; cancelling!");
      return;
    }

    var targetItem = blockContents[item.slot()];

    resultDisplay.removeItem(item);

    if (!item.item().equals(targetItem)) {
      player.sendMessage("§cThe item changed in the mean-time; cancelling!");
      return;
    }

    blockContents[item.slot()] = null;
    containerInventory.setStorageContents(blockContents);

    player.sendMessage("§aHanded out the item!");

    var remainders = player.getInventory().addItem(targetItem).values();

    if (!remainders.isEmpty()) {
      player.sendMessage("§cSome item(s) didn't fit into your inventory; dropping!");

      for (var remainder : remainders)
        player.dropItem(remainder);
    }
  }
}
