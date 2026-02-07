package me.blvckbytes.craft_book_pipe_predicates.search;

import me.blvckbytes.item_predicate_parser.predicate.ComparisonFlag;
import me.blvckbytes.item_predicate_parser.predicate.ItemPredicate;
import me.blvckbytes.item_predicate_parser.predicate.stringify.PlainStringifier;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

public class SearchedInventory {

  public final Inventory inventory;
  public final Block block;
  public final @Nullable Block otherChestBlock;
  public final Material material;
  // Double-chests are represented by two individual containers, with each side
  // having its own snapshot-inventory. When accessing the double-chest in a live
  // manner, we'll get a view called a DoubleChestInventory - thus, the slots of the
  // other half need to be offset by the number if slots in a single chest.
  public final int slotOffset;

  public final List<ItemPredicate> activePredicatesInOrder;
  public final @Nullable String nearestActivePredicateString;

  public SearchedInventory(Inventory inventory, Block block, @Nullable Block otherChestBlock, Material material, int slotOffset, List<ItemPredicate> activePredicatesInOrder) {
    this.inventory = inventory;
    this.block = block;
    this.otherChestBlock = otherChestBlock;
    this.material = material;
    this.slotOffset = slotOffset;
    this.activePredicatesInOrder = activePredicatesInOrder;

    this.nearestActivePredicateString = activePredicatesInOrder.isEmpty()
      ? null
      : PlainStringifier.stringify(activePredicatesInOrder.getLast(), false);
  }

  public boolean matchesPredicate(ItemPredicate predicate) {
    for (var activePredicate : activePredicatesInOrder) {
      if (activePredicate.containsOrEqualsPredicate(predicate, EnumSet.of(ComparisonFlag.MATERIAL_PREDICATE__INTERSECTION_SUFFICES)))
        return true;
    }

    return false;
  }
}
