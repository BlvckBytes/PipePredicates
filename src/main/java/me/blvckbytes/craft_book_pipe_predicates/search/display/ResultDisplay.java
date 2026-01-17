package me.blvckbytes.craft_book_pipe_predicates.search.display;

import at.blvckbytes.cm_mapper.ConfigKeeper;
import at.blvckbytes.component_markup.expression.interpreter.InterpretationEnvironment;
import me.blvckbytes.craft_book_pipe_predicates.config.MainSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class ResultDisplay extends Display<ResultDisplayData> {

  private final AsyncTaskQueue asyncQueue;

  private final ResultDisplayEntry[] slotMap;
  private int numberOfPages;

  private int currentPage = 1;

  public ResultDisplay(
    ConfigKeeper<MainSection> config,
    Plugin plugin,
    Player player,
    ResultDisplayData displayData
  ) {
    super(player, displayData, config, plugin);

    this.asyncQueue = new AsyncTaskQueue(plugin);
    this.slotMap = new ResultDisplayEntry[9 * 6];

    setupEnvironments();

    // Within async context already, see corresponding command
    show();
  }

  private void setupEnvironments() {
  }

  @Override
  public void onConfigReload() {
    setupEnvironments();
    show();
  }

  public @Nullable ResultDisplayEntry getEntryCorrespondingToSlot(int slot) {
    return slotMap[slot];
  }

  @Override
  public void onInventoryClose() {
    clearSlotMap();
    super.onInventoryClose();
  }

  @Override
  public void onShutdown() {
    clearSlotMap();
    super.onShutdown();
  }

  public void removeEntry(ResultDisplayEntry entry) {
    displayData.entries().remove(entry);

    var priorNumberOfPages = numberOfPages;

    updateNumberOfPages();

    // Avoid reopening the inventory if the title did not change
    if (priorNumberOfPages == numberOfPages) {
      renderItems();
      return;
    }

    show();
  }

  private void clearSlotMap() {
    for (var i = 0; i < slotMap.length; ++i)
      this.slotMap[i] = null;
  }

  public void nextPage() {
    asyncQueue.enqueue(() -> {
      if (currentPage == numberOfPages)
        return;

      ++currentPage;
      show();
    });
  }

  public void previousPage() {
    asyncQueue.enqueue(() -> {
      if (currentPage == 1)
        return;

      --currentPage;
      show();
    });
  }

  public void firstPage() {
    asyncQueue.enqueue(() -> {
      if (currentPage == 1)
        return;

      currentPage = 1;
      show();
    });
  }

  public void lastPage() {
    asyncQueue.enqueue(() -> {
      if (currentPage == numberOfPages)
        return;

      currentPage = numberOfPages;
      show();
    });
  }

  private void updateNumberOfPages() {
    var numberOfDisplaySlots = config.rootSection.resultDisplay.getPaginationSlots().size();
    this.numberOfPages = Math.max(1, (int) Math.ceil(displayData.entries().size() / (double) numberOfDisplaySlots));
  }

  @Override
  public void show() {
    updateNumberOfPages();

    // Since we're removing items when handing them out, automatically page back if it was the last on the current page
    if (currentPage > numberOfPages)
      currentPage = numberOfPages;

    clearSlotMap();

    super.show();
  }

  @Override
  protected void renderItems() {
    var displaySlots = new ArrayList<>(config.rootSection.resultDisplay.getPaginationSlots());
    var itemsIndex = (currentPage - 1) * displaySlots.size();
    var numberOfItems = displayData.entries().size();

    for (var paginationSlotIndex = 0; paginationSlotIndex < displaySlots.size(); ++paginationSlotIndex) {
      var slot = displaySlots.get(paginationSlotIndex);
      var currentSlot = itemsIndex++;

      if (currentSlot >= numberOfItems) {
        slotMap[slot] = null;
        inventory.setItem(slot, null);
        continue;
      }

      var entry = displayData.entries().get(currentSlot);

      ItemStack representativeItem;

      try {
        representativeItem = entry.makeRepresentative(config);
      }
      // java.lang.IllegalStateException: Could not get meta of item
      // The above occurs if the item has been moved; simply remove such items from the UI as well.
      catch (IllegalStateException ignored) {
        // Try again with the next item at the same slot
        displayData.entries().remove(entry);
        --itemsIndex;
        --paginationSlotIndex;
        --numberOfItems;
        continue;
      }

      inventory.setItem(slot, representativeItem);

      slotMap[slot] = entry;
    }

    var pageEnvironment = makePageEnvironment();

    // Render filler first, such that it may be overridden by conditionally displayed items
    config.rootSection.resultDisplay.items.filler.renderInto(inventory, pageEnvironment);

    config.rootSection.resultDisplay.items.previousPage.renderInto(inventory, pageEnvironment);
    config.rootSection.resultDisplay.items.nextPage.renderInto(inventory, pageEnvironment);

    if (displayData.backToDisplay() != null)
      config.rootSection.resultDisplay.items.backToCollectionsButton.renderInto(inventory, pageEnvironment);

    else if (displayData.predicateString() != null)
      config.rootSection.resultDisplay.items.searchDetails.renderInto(inventory, pageEnvironment);
  }

  @Override
  protected Inventory makeInventory() {
    return config.rootSection.resultDisplay.createInventory(makePageEnvironment());
  }

  private InterpretationEnvironment makePageEnvironment() {
    return config.rootSection.resultDisplay.inventoryEnvironment.copy()
      .withVariable("predicate", this.displayData.predicateString())
      .withVariable("current_page", this.currentPage)
      .withVariable("number_pages", this.numberOfPages);
  }
}
