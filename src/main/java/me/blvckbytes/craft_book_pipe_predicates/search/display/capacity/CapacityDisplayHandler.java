package me.blvckbytes.craft_book_pipe_predicates.search.display.capacity;

import at.blvckbytes.cm_mapper.ConfigKeeper;
import me.blvckbytes.craft_book_pipe_predicates.FloodgateIntegration;
import me.blvckbytes.craft_book_pipe_predicates.config.MainSection;
import me.blvckbytes.craft_book_pipe_predicates.search.StorageCapacity;
import me.blvckbytes.craft_book_pipe_predicates.search.display.DisplayHandler;
import me.blvckbytes.craft_book_pipe_predicates.search.display.StorageBlock;
import me.blvckbytes.craft_book_pipe_predicates.search.display.search.SearchDisplayHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

public class CapacityDisplayHandler extends DisplayHandler<CapacityDisplay, CapacityDisplayData> {

  private final Logger logger;

  public CapacityDisplayHandler(
    ConfigKeeper<MainSection> config,
    Plugin plugin,
    FloodgateIntegration floodgateIntegration
  ) {
    super(config, plugin, floodgateIntegration);

    logger = plugin.getLogger();
  }

  @Override
  public CapacityDisplay instantiateDisplay(Player player, CapacityDisplayData displayData) {
    return new CapacityDisplay(player, floodgateIntegration.isFloodgatePlayer(player), displayData, config, plugin);
  }

  @Override
  protected void handleClick(Player player, CapacityDisplay display, ClickType clickType, int slot) {
    var targetRenderable = display.getRenderableCorrespondingToSlot(slot);

    if (targetRenderable != null) {
      if (targetRenderable instanceof StorageBlock storageBlock) {
        SearchDisplayHandler.teleportPlayerToContainer(player, storageBlock.searchedInventory().block(), config);
        return;
      }

      if (targetRenderable instanceof StorageCapacity storageCapacity) {
        show(player, new CapacityDisplayData(display.displayData, storageCapacity, display));
        return;
      }

      logger.warning("Encountered unaccounted-for capacity-display renderable-type: " + targetRenderable.getClass());
      return;
    }

    if (clickType == ClickType.LEFT) {
      if (config.rootSection.capacityDisplay.items.previousPage.getDisplaySlots().contains(slot)) {
        display.previousPage();
        return;
      }

      if (config.rootSection.capacityDisplay.items.nextPage.getDisplaySlots().contains(slot)) {
        display.nextPage();
        return;
      }

      if (display.displayData.selectedCapacity != null && config.rootSection.capacityDisplay.items.backToPredicatesButton.getDisplaySlots().contains(slot)) {
        if (display.displayData.previousDisplay != null)
          reopen(display.displayData.previousDisplay);
        return;
      }
    }

    if (clickType == ClickType.RIGHT) {
      if (config.rootSection.capacityDisplay.items.previousPage.getDisplaySlots().contains(slot)) {
        display.firstPage();
        return;
      }

      if (config.rootSection.capacityDisplay.items.nextPage.getDisplaySlots().contains(slot))
        display.lastPage();
    }
  }
}
