package me.blvckbytes.craft_book_pipe_predicates.search.display;

import me.blvckbytes.bukkitevaluable.ConfigKeeper;
import me.blvckbytes.craft_book_pipe_predicates.config.MainSection;
import me.blvckbytes.craft_book_pipe_predicates.search.ItemAndSlot;
import me.blvckbytes.gpeee.interpreter.EvaluationEnvironmentBuilder;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.plugin.Plugin;

import java.util.function.Consumer;

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

    if (targetItem != null) {
      if (clickType == ClickType.DROP) {
        moveItemIntoInventory(display, player, targetItem);
        return;
      }

      if (clickType == ClickType.CONTROL_DROP) {
        openContainer(player, targetItem);
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

    config.rootSection.playerMessages.commandPipePredicateSearchContainerTeleported.sendMessage(player, getBlockEnvironment(block).build());
  }

  private void moveItemIntoInventory(ResultDisplay resultDisplay, Player player, ItemAndSlot item) {
    tryAccessContainer(player, item, container -> {
      var environment = getBlockEnvironment(item.block());

      var containerInventory = container.getInventory();
      var blockContents = containerInventory.getStorageContents();

      if (item.slot() < 0 || item.slot() >= blockContents.length) {
        config.rootSection.playerMessages.commandPipePredicateSearchGetItemContainerSizeChanged.sendMessage(player, environment.build());
        return;
      }

      var targetItem = blockContents[item.slot()];

      resultDisplay.removeItem(item);

      environment
        .withStaticVariable("item_slot", item.slot() + 1)
        .withStaticVariable("item_amount", item.item().getAmount())
        .withStaticVariable("item_type", item.item().getType().name());

      if (!item.item().equals(targetItem)) {
        config.rootSection.playerMessages.commandPipePredicateSearchGetItemMoved.sendMessage(player, environment.build());
        return;
      }

      blockContents[item.slot()] = null;
      containerInventory.setStorageContents(blockContents);

      config.rootSection.playerMessages.commandPipePredicateSearchGetItemSuccess.sendMessage(player, environment.build());

      var remainders = player.getInventory().addItem(targetItem).values();

      if (!remainders.isEmpty()) {

        for (var remainder : remainders) {
          player.dropItem(remainder);

          config.rootSection.playerMessages.commandPipePredicateSearchGetItemDropped.sendMessage(
            player,
            environment
              .withStaticVariable("dropped_amount", remainder.getAmount())
              .build()
          );
        }
      }
    });
  }

  private void openContainer(Player player, ItemAndSlot item) {
    tryAccessContainer(player, item, container -> {
      config.rootSection.playerMessages.commandPipePredicateSearchContainerOpened.sendMessage(player, getBlockEnvironment(item.block()).build());
      player.openInventory(container.getInventory());
    });
  }

  private void tryAccessContainer(Player player, ItemAndSlot item, Consumer<Container> containerHandler) {
    var block = item.block();

    if (!(block.getState() instanceof Container container)) {
      config.rootSection.playerMessages.commandPipePredicateSearchGetItemContainerAbsent.sendMessage(
        player, getBlockEnvironment(block).build()
      );
      return;
    }

    containerHandler.accept(container);
  }

  private EvaluationEnvironmentBuilder getBlockEnvironment(Block block) {
    return config.rootSection.getBaseEnvironment()
      .withStaticVariable("container_x", block.getX())
      .withStaticVariable("container_y", block.getY())
      .withStaticVariable("container_z", block.getZ());
  }
}
