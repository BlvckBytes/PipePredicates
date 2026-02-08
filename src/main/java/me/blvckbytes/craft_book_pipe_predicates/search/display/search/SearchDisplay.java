package me.blvckbytes.craft_book_pipe_predicates.search.display.search;

import at.blvckbytes.cm_mapper.ConfigKeeper;
import at.blvckbytes.component_markup.expression.interpreter.InterpretationEnvironment;
import me.blvckbytes.craft_book_pipe_predicates.config.MainSection;
import me.blvckbytes.craft_book_pipe_predicates.search.display.AsyncTaskQueue;
import me.blvckbytes.craft_book_pipe_predicates.search.display.Display;
import me.blvckbytes.craft_book_pipe_predicates.search.display.EnumEntry;
import me.blvckbytes.item_predicate_parser.predicate.stringify.PlainStringifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class SearchDisplay extends Display<SearchDisplayData> {

  private final AsyncTaskQueue asyncQueue;

  private final SearchDisplayEntry[] slotMap;
  private int numberOfPages;

  private int currentPage = 1;

  private CollectionAction collectionAction = CollectionAction.first();
  private StackAction stackAction = StackAction.first();

  public SearchDisplay(
    Player player,
    boolean isFloodgate,
    SearchDisplayData displayData,
    ConfigKeeper<MainSection> config,
    Plugin plugin
  ) {
    super(player, isFloodgate, displayData, config, plugin);

    this.asyncQueue = new AsyncTaskQueue(plugin);
    this.slotMap = new SearchDisplayEntry[9 * 6];

    // Within async context already, see corresponding command
    show();
  }

  @Override
  public void onConfigReload() {
    show();
  }

  public @Nullable SearchDisplayEntry getEntryCorrespondingToSlot(int slot) {
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

  public void removeEntry(SearchDisplayEntry entry) {
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

  public void nextCollectionAction() {
    asyncQueue.enqueue(() -> {
      this.collectionAction = this.collectionAction.nextAction();
      renderItems();
    });
  }

  public void nextStackAction() {
    asyncQueue.enqueue(() -> {
      this.stackAction = this.stackAction.nextAction();
      renderItems();
    });
  }

  public CollectionAction getCollectionAction() {
    return this.collectionAction;
  }

  public StackAction getStackAction() {
    return this.stackAction;
  }

  private void updateNumberOfPages() {
    var numberOfDisplaySlots = config.rootSection.searchDisplay.getPaginationSlots().size();
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
    var displaySlots = new ArrayList<>(config.rootSection.searchDisplay.getPaginationSlots());
    var itemsIndex = (currentPage - 1) * displaySlots.size();
    var numberOfItems = displayData.entries().size();

    var environment = makeEnvironment();

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
        representativeItem = entry.makeRepresentative(environment, config);
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

    config.rootSection.searchDisplay.items.filler.renderInto(inventory, environment);
    config.rootSection.searchDisplay.items.previousPage.renderInto(inventory, environment);
    config.rootSection.searchDisplay.items.nextPage.renderInto(inventory, environment);

    if (displayData.backToDisplay() != null)
      config.rootSection.searchDisplay.items.backToCollectionsButton.renderInto(inventory, environment);

    if (displayData.predicate() != null)
      config.rootSection.searchDisplay.items.searchDetails.renderInto(inventory, environment);
  }

  @Override
  protected Inventory makeInventory() {
    return config.rootSection.searchDisplay.createInventory(makeEnvironment());
  }

  private InterpretationEnvironment makeEnvironment() {
    var environment = config.rootSection.searchDisplay.inventoryEnvironment.copy()
      .withVariable("predicate", this.displayData.predicate() == null ? null : PlainStringifier.stringify(this.displayData.predicate(), true))
      .withVariable("current_page", this.currentPage)
      .withVariable("number_pages", this.numberOfPages)
      .withVariable("is_floodgate", isFloodgate);

    if (isFloodgate) {
      environment.withVariable(
        "collection_actions",
        CollectionAction.values.stream().map(action -> new EnumEntry(action, action == collectionAction)).toList()
      );

      environment.withVariable(
        "stack_actions",
        StackAction.values.stream().map(action -> new EnumEntry(action, action == stackAction)).toList()
      );
    }

    return environment;
  }
}
