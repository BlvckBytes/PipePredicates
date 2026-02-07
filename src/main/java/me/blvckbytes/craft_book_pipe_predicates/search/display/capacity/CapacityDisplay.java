package me.blvckbytes.craft_book_pipe_predicates.search.display.capacity;

import at.blvckbytes.cm_mapper.ConfigKeeper;
import at.blvckbytes.component_markup.expression.interpreter.InterpretationEnvironment;
import me.blvckbytes.craft_book_pipe_predicates.config.MainSection;
import me.blvckbytes.craft_book_pipe_predicates.search.display.AsyncTaskQueue;
import me.blvckbytes.craft_book_pipe_predicates.search.display.Display;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class CapacityDisplay extends Display<CapacityDisplayData> {

  private final AsyncTaskQueue asyncQueue;

  private final List<? extends CapacityDisplayRenderable> renderables;
  private final CapacityDisplayRenderable[] slotMap;
  private int numberOfPages;

  private int currentPage = 1;

  public CapacityDisplay(
    Player player,
    boolean isFloodgate,
    CapacityDisplayData displayData,
    ConfigKeeper<MainSection> config,
    Plugin plugin
  ) {
    super(player, isFloodgate, displayData, config, plugin);

    this.asyncQueue = new AsyncTaskQueue(plugin);
    this.slotMap = new CapacityDisplayRenderable[9 * 6];

    if (displayData.selectedCapacity != null)
      this.renderables = displayData.selectedCapacity.storageBlocks;
    else
      this.renderables = displayData.capacities;

    this.renderables.sort((a, b) -> -Double.compare(a.getUsagePercentage(), b.getUsagePercentage()));

    // Within async context already, see corresponding command
    show();
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

  @Override
  protected void renderItems() {
    var environment = makeEnvironment();

    var displaySlots = new ArrayList<>(config.rootSection.searchDisplay.getPaginationSlots());
    var itemsIndex = (currentPage - 1) * displaySlots.size();
    var numberOfItems = renderables.size();

    for (Integer slot : displaySlots) {
      var currentSlot = itemsIndex++;

      if (currentSlot >= numberOfItems) {
        slotMap[slot] = null;
        inventory.setItem(slot, null);
        continue;
      }

      var renderable = renderables.get(currentSlot);
      inventory.setItem(slot, renderable.render(config, environment));
      slotMap[slot] = renderable;
    }

    config.rootSection.capacityDisplay.items.filler.renderInto(inventory, environment);
    config.rootSection.capacityDisplay.items.previousPage.renderInto(inventory, environment);
    config.rootSection.capacityDisplay.items.nextPage.renderInto(inventory, environment);

    if (displayData.selectedCapacity != null)
      config.rootSection.capacityDisplay.items.backToPredicatesButton.renderInto(inventory, environment);

    if (displayData.predicateString != null)
      config.rootSection.capacityDisplay.items.searchDetails.renderInto(inventory, environment);
  }

  @Override
  protected Inventory makeInventory() {
    var numberOfDisplaySlots = config.rootSection.searchDisplay.getPaginationSlots().size();
    this.numberOfPages = Math.max(1, (int) Math.ceil(renderables.size() / (double) numberOfDisplaySlots));
    return config.rootSection.capacityDisplay.createInventory(makeEnvironment());
  }

  public @Nullable CapacityDisplayRenderable getRenderableCorrespondingToSlot(int slot) {
    return slotMap[slot];
  }

  @Override
  public void onConfigReload() {}

  private InterpretationEnvironment makeEnvironment() {
    return new InterpretationEnvironment()
      .withVariable("predicate", this.displayData.predicateString)
      .withVariable("current_page", this.currentPage)
      .withVariable("number_pages", this.numberOfPages)
      .withVariable("is_floodgate", isFloodgate);
  }
}
