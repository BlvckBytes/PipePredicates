package me.blvckbytes.craft_book_pipe_predicates.search;

import at.blvckbytes.cm_mapper.ConfigKeeper;
import at.blvckbytes.component_markup.expression.interpreter.InterpretationEnvironment;
import com.sk89q.craftbook.mechanics.pipe.CompactId;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import me.blvckbytes.craft_book_pipe_predicates.CaseInsensitiveSet;
import me.blvckbytes.craft_book_pipe_predicates.config.MainSection;
import me.blvckbytes.craft_book_pipe_predicates.search.display.StorageBlock;
import me.blvckbytes.craft_book_pipe_predicates.search.display.capacity.CapacityDisplayRenderable;
import me.blvckbytes.craft_book_pipe_predicates.search.display.capacity.UsageLevel;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class StorageCapacity implements CapacityDisplayRenderable {

  private final CaseInsensitiveSet allLabelValues;
  private final Long2ObjectMap<StorageBlock> storageBlockByCompactId;
  private @Nullable List<StorageBlock> combinedStorageBlocks;

  public int vacantSlotCount;
  public int occupiedSlotCount;

  private final String predicateString;

  private double usagePercentage = -1;
  private UsageLevel usageLevel = null;

  public StorageCapacity(String predicateString) {
    this.allLabelValues = new CaseInsensitiveSet();
    this.predicateString = predicateString;
    this.storageBlockByCompactId = new Long2ObjectOpenHashMap<>();
  }

  public void combineStorageBlocks() {
    if (combinedStorageBlocks != null)
      throw new IllegalStateException("Called the combine-method twice");

    var seenIds = new LongOpenHashSet();

    combinedStorageBlocks = new ArrayList<>();

    for (var entry : storageBlockByCompactId.long2ObjectEntrySet()) {
      var currentId = entry.getLongKey();

      if (!seenIds.add(currentId))
        continue;

      var storageBlock = entry.getValue();

      var otherChestBlock = storageBlock.searchedInventory.otherChestBlock;

      if (otherChestBlock != null) {
        var otherId = CompactId.computeWorldlessBlockId(otherChestBlock);

        if (seenIds.add(otherId)) {
          var otherStorage = storageBlockByCompactId.get(otherId);

          if (otherStorage != null) {
            storageBlock = new StorageBlock(
              storageBlock.searchedInventory,
              storageBlock.occupiedSlotCount + otherStorage.occupiedSlotCount,
              storageBlock.inventorySize + otherStorage.inventorySize
            );
          }
        }
      }

      combinedStorageBlocks.add(storageBlock);
    }
  }

  public List<StorageBlock> getCombinedStorageBlocks() {
    if (combinedStorageBlocks == null)
      throw new IllegalStateException("Did not call the combine-method first");

    return combinedStorageBlocks;
  }

  public void addEntry(SearchedInventory searchedInventory, int occupiedSlotCount, int inventorySize) {
    allLabelValues.addAll(searchedInventory.getLabelValues());

    var storageBlock = new StorageBlock(searchedInventory, occupiedSlotCount, inventorySize);
    storageBlockByCompactId.put(CompactId.computeWorldlessBlockId(searchedInventory.block), storageBlock);
  }

  @Override
  public double getUsagePercentage() {
    if (usagePercentage < 0)
      usagePercentage = (occupiedSlotCount / (double) (occupiedSlotCount + vacantSlotCount)) * 100;

    return usagePercentage;
  }

  @Override
  public UsageLevel getUsageLevel() {
    if (usageLevel == null)
      usageLevel = UsageLevel.fromUsagePercentage(getUsagePercentage());

    return usageLevel;
  }

  @Override
  public ItemStack render(ConfigKeeper<MainSection> config, InterpretationEnvironment environment) {
    return config.rootSection.capacityDisplay.items.predicateRepresentative.build(
      environment.copy()
        .withVariable("vacant_slot_count", vacantSlotCount)
        .withVariable("occupied_slot_count", occupiedSlotCount)
        .withVariable("total_slot_count", occupiedSlotCount + vacantSlotCount)
        .withVariable("usage_percentage", getUsagePercentage())
        .withVariable("usage_level", getUsageLevel().name())
        .withVariable("predicate", predicateString)
        .withVariable("labels", allLabelValues)
        .withVariable("container_count", getCombinedStorageBlocks().size())
    );
  }

  @Override
  public int getTotalCapacity() {
    return occupiedSlotCount + vacantSlotCount;
  }
}
