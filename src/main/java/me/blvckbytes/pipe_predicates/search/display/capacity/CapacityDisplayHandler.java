package me.blvckbytes.pipe_predicates.search.display.capacity;

import at.blvckbytes.cm_mapper.ConfigKeeper;
import me.blvckbytes.pipe_predicates.FloodgateIntegration;
import me.blvckbytes.pipe_predicates.config.MainSection;
import me.blvckbytes.pipe_predicates.search.StorageCapacity;
import me.blvckbytes.pipe_predicates.search.display.DisplayHandler;
import me.blvckbytes.pipe_predicates.search.display.StorageBlock;
import me.blvckbytes.pipe_predicates.search.display.search.SearchDisplayHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

public class CapacityDisplayHandler extends DisplayHandler<CapacityDisplay, CapacityDisplayData> {

  private final Logger logger;
  private final SelectionStateStore selectionStateStore;

  public CapacityDisplayHandler(
    ConfigKeeper<MainSection> config,
    Plugin plugin,
    FloodgateIntegration floodgateIntegration
  ) throws Exception {
    super(config, plugin, floodgateIntegration);

    this.logger = plugin.getLogger();
    this.selectionStateStore = new SelectionStateStore(plugin, logger);
  }

  @Override
  public void onShutdown() {
    super.onShutdown();

    selectionStateStore.onShutdown();
  }

  @Override
  public CapacityDisplay instantiateDisplay(Player player, CapacityDisplayData displayData) {
    return new CapacityDisplay(player, floodgateIntegration.isFloodgatePlayer(player), displayData, selectionStateStore.loadState(player), config, plugin);
  }

  @Override
  protected void handleClick(Player player, CapacityDisplay display, ClickType clickType, int slot) {
    var targetRenderable = display.getRenderableCorrespondingToSlot(slot);

    if (targetRenderable != null) {
      if (targetRenderable instanceof StorageBlock storageBlock) {
        SearchDisplayHandler.teleportPlayerToContainer(player, storageBlock.searchedInventory.block, config);
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

      if (config.rootSection.capacityDisplay.items.sorting.getDisplaySlots().contains(slot)) {
        display.nextSortingOrder();
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

      if (config.rootSection.capacityDisplay.items.nextPage.getDisplaySlots().contains(slot)) {
        display.lastPage();
        return;
      }

      if (config.rootSection.capacityDisplay.items.sorting.getDisplaySlots().contains(slot))
        display.resetSortingState();

      return;
    }

    if (clickType == ClickType.DROP) {
      if (config.rootSection.capacityDisplay.items.sorting.getDisplaySlots().contains(slot))
        display.nextSortingSelection();

      return;
    }

    if (clickType == ClickType.CONTROL_DROP) {
      if (config.rootSection.capacityDisplay.items.sorting.getDisplaySlots().contains(slot))
        display.moveSortingSelectionDown();
    }
  }
}
