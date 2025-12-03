package me.blvckbytes.craft_book_pipe_predicates.search.display;

import me.blvckbytes.bukkitevaluable.ConfigKeeper;
import me.blvckbytes.bukkitevaluable.ItemBuilder;
import me.blvckbytes.craft_book_pipe_predicates.config.MainSection;
import me.blvckbytes.craft_book_pipe_predicates.search.ItemAndSlot;
import me.blvckbytes.gpeee.interpreter.EvaluationEnvironmentBuilder;
import me.blvckbytes.gpeee.interpreter.IEvaluationEnvironment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

public class ResultDisplay extends Display<ResultDisplayData> {

  private final AsyncTaskQueue asyncQueue;

  private final ItemAndSlot[] slotMap;
  private int numberOfPages;

  private IEvaluationEnvironment pageEnvironment;

  private int currentPage = 1;

  public ResultDisplay(
    ConfigKeeper<MainSection> config,
    Plugin plugin,
    Player player,
    ResultDisplayData displayData
  ) {
    super(player, displayData, config, plugin);

    this.asyncQueue = new AsyncTaskQueue(plugin);
    this.slotMap = new ItemAndSlot[9 * 6];

    setupEnvironments();

    // Within async context already, see corresponding command
    show();
  }

  private void setupEnvironments() {
    this.pageEnvironment = new EvaluationEnvironmentBuilder()
      .withLiveVariable("current_page", () -> this.currentPage)
      .withLiveVariable("number_pages", () -> this.numberOfPages)
      .build(config.rootSection.resultDisplay.inventoryEnvironment);
  }

  @Override
  public void onConfigReload() {
    setupEnvironments();
    show();
  }

  public @Nullable ItemAndSlot getShopCorrespondingToSlot(int slot) {
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

  public void removeItem(ItemAndSlot itemAndSlot) {
    displayData.items().remove(itemAndSlot);
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

  @Override
  public void show() {
    var numberOfDisplaySlots = config.rootSection.resultDisplay.getPaginationSlots().size();

    this.numberOfPages = Math.max(1, (int) Math.ceil(displayData.items().size() / (double) numberOfDisplaySlots));

    // Since we're removing items when handing them out, automatically page back if it was the last on the current page
    if (currentPage > numberOfPages)
      currentPage = numberOfPages;

    clearSlotMap();

    super.show();
  }

  @Override
  protected void renderItems() {
    var displaySlots = config.rootSection.resultDisplay.getPaginationSlots();
    var itemsIndex = (currentPage - 1) * displaySlots.size();
    var numberOfItems = displayData.items().size();

    for (var slot : displaySlots) {
      var currentSlot = itemsIndex++;

      if (currentSlot >= numberOfItems) {
        slotMap[slot] = null;
        inventory.setItem(slot, null);
        continue;
      }

      var itemAndSlot = displayData.items().get(currentSlot);

      var representativeItem = new ItemBuilder(itemAndSlot.item(), itemAndSlot.item().getAmount())
        .patch(config.rootSection.resultDisplay.items.representativePatch)
        .build(
          new EvaluationEnvironmentBuilder()
            .withStaticVariable("container_x", itemAndSlot.block().getX())
            .withStaticVariable("container_y", itemAndSlot.block().getY())
            .withStaticVariable("container_z", itemAndSlot.block().getZ())
            .build(pageEnvironment)
        );

      inventory.setItem(slot, representativeItem);

      slotMap[slot] = itemAndSlot;
    }

    // Render filler first, such that it may be overridden by conditionally displayed items
    config.rootSection.resultDisplay.items.filler.renderInto(inventory, pageEnvironment);

    config.rootSection.resultDisplay.items.previousPage.renderInto(inventory, pageEnvironment);
    config.rootSection.resultDisplay.items.nextPage.renderInto(inventory, pageEnvironment);
  }

  @Override
  protected Inventory makeInventory() {
    return config.rootSection.resultDisplay.createInventory(pageEnvironment);
  }
}
