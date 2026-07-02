package me.blvckbytes.pipe_predicates.search.display.capacity;

import at.blvckbytes.cm_mapper.ConfigKeeper;
import at.blvckbytes.component_markup.expression.interpreter.InterpretationEnvironment;
import me.blvckbytes.pipe_predicates.config.MainSection;
import me.blvckbytes.pipe_predicates.search.display.AsyncTaskQueue;
import me.blvckbytes.pipe_predicates.search.display.Display;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class CapacityDisplay extends Display<CapacityDisplayData> {

  private final AsyncTaskQueue asyncQueue;
  private final SelectionState selectionState;

  private final List<? extends CapacityDisplayRenderable> renderables;
  private List<? extends CapacityDisplayRenderable> sortedRenderables;

  private final CapacityDisplayRenderable[] slotMap;
  private int numberOfPages;

  private int currentPage = 1;

  public CapacityDisplay(
    Player player,
    boolean isFloodgate,
    CapacityDisplayData displayData,
    SelectionState selectionState,
    ConfigKeeper<MainSection> config,
    Plugin plugin
  ) {
    super(player, isFloodgate, displayData, config, plugin);

    this.selectionState = selectionState;
    this.asyncQueue = new AsyncTaskQueue(plugin);
    this.slotMap = new CapacityDisplayRenderable[9 * 6];

    if (displayData.selectedCapacity != null)
      this.renderables = displayData.selectedCapacity.getCombinedStorageBlocks();
    else
      this.renderables = displayData.capacityInfo.capacities();

    applySorting();

    // Within async context already, see corresponding command
    show();
  }

  private void renderSortingItem(InterpretationEnvironment environment) {
    config.rootSection.capacityDisplay.items.sorting.renderInto(inventory, environment);
  }

  public void applySorting() {
    sortedRenderables = new ArrayList<>(renderables);
    selectionState.applySort(sortedRenderables);
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

  public void nextSortingSelection() {
    asyncQueue.enqueue(() -> {
      this.selectionState.nextSortingSelection();
      renderSortingItem(makeEnvironment());
    });
  }

  public void nextSortingOrder() {
    asyncQueue.enqueue(() -> {
      this.selectionState.nextSortingOrder();
      applySorting();
      renderItems();
    });
  }

  public void moveSortingSelectionDown() {
    asyncQueue.enqueue(() -> {
      this.selectionState.moveSortingSelectionDown();
      applySorting();
      renderItems();
    });
  }

  public void resetSortingState() {
    asyncQueue.enqueue(() -> {
      this.selectionState.resetSorting();
      applySorting();
      renderItems();
    });
  }

  @Override
  protected void renderItems() {
    var environment = makeEnvironment();

    var displaySlots = new ArrayList<>(config.rootSection.searchDisplay.getPaginationSlots());
    var itemsIndex = (currentPage - 1) * displaySlots.size();
    var numberOfItems = sortedRenderables.size();

    for (Integer slot : displaySlots) {
      var currentSlot = itemsIndex++;

      if (currentSlot >= numberOfItems) {
        slotMap[slot] = null;
        inventory.setItem(slot, null);
        continue;
      }

      var renderable = sortedRenderables.get(currentSlot);
      inventory.setItem(slot, renderable.render(config, environment));
      slotMap[slot] = renderable;
    }

    config.rootSection.capacityDisplay.items.filler.renderInto(inventory, environment);
    config.rootSection.capacityDisplay.items.previousPage.renderInto(inventory, environment);
    config.rootSection.capacityDisplay.items.nextPage.renderInto(inventory, environment);

    renderSortingItem(environment);

    if (displayData.selectedCapacity != null)
      config.rootSection.capacityDisplay.items.backToPredicatesButton.renderInto(inventory, environment);
    else
      config.rootSection.capacityDisplay.items.searchDetails.renderInto(inventory, environment);
  }

  @Override
  protected Inventory makeInventory() {
    var numberOfDisplaySlots = config.rootSection.searchDisplay.getPaginationSlots().size();
    this.numberOfPages = Math.max(1, (int) Math.ceil(sortedRenderables.size() / (double) numberOfDisplaySlots));
    return config.rootSection.capacityDisplay.createInventory(makeEnvironment());
  }

  public @Nullable CapacityDisplayRenderable getRenderableCorrespondingToSlot(int slot) {
    return slotMap[slot];
  }

  @Override
  public void onConfigReload() {}

  private InterpretationEnvironment makeEnvironment() {
    var environment = new InterpretationEnvironment()
      .withVariable("predicate", this.displayData.capacityInfo.containedPredicate() == null ? null : this.displayData.capacityInfo.containedPredicate().getStringification())
      .withVariable("predicate_labels", this.displayData.capacityInfo.containedPredicate() == null ? null : displayData.capacityInfo.containedPredicate().getLabelValues())
      .withVariable("encountered_labels", this.displayData.capacityInfo.encounteredLabelValues())
      .withVariable("current_page", this.currentPage)
      .withVariable("number_pages", this.numberOfPages)
      .withVariable("is_floodgate", isFloodgate);

    selectionState.extendSortingEnvironment(environment);

    return environment;
  }
}
