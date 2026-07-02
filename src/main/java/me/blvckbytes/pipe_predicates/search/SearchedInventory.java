package me.blvckbytes.pipe_predicates.search;

import me.blvckbytes.pipe_predicates.CaseInsensitiveSet;
import me.blvckbytes.item_predicate_parser.predicate.ComparisonFlag;
import me.blvckbytes.item_predicate_parser.predicate.LabelPredicate;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.Nullable;

import java.util.*;

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

  private final List<PredicateAndLabels> activePredicatesInOrder;
  private @Nullable List<LabelPredicate> allLabels;
  private @Nullable CaseInsensitiveSet allLabelValues;

  private @Nullable String nearestActivePredicateString;

  public SearchedInventory(Inventory inventory, Block block, @Nullable Block otherChestBlock, Material material, int slotOffset, List<PredicateAndLabels> activePredicatesInOrder) {
    this.inventory = inventory;
    this.block = block;
    this.otherChestBlock = otherChestBlock;
    this.material = material;
    this.slotOffset = slotOffset;
    this.activePredicatesInOrder = activePredicatesInOrder;

    for (var activePredicate : activePredicatesInOrder) {
      if (allLabels == null)
        allLabels = new ArrayList<>();

      if (allLabelValues == null)
        allLabelValues = new CaseInsensitiveSet();

      allLabels.addAll(activePredicate.labels);

      for (var label : activePredicate.labels)
        allLabelValues.add(label.token.value());
    }

    for (var index = activePredicatesInOrder.size() - 1; index >= 0; --index) {
      var stringification = activePredicatesInOrder.get(index).getStringification();

      if (stringification != null) {
        nearestActivePredicateString = stringification;
        break;
      }
    }
  }

  public @Nullable String getNearestActivePredicateString() {
    return nearestActivePredicateString;
  }

  public Collection<String> getLabelValues() {
    if (allLabels == null)
      return Collections.emptyList();

    return allLabelValues;
  }

  public boolean isLabelled(PredicateAndLabels predicateAndLabels) {
    if (predicateAndLabels.labels.isEmpty())
      return true;

    if (allLabels == null)
      return false;

    for (var predicateLabel : predicateAndLabels.labels) {
      for (var currentLabel : allLabels) {
        if (currentLabel.containsOrEqualsPredicate(predicateLabel, EnumSet.of(ComparisonFlag.LABEL_PREDICATE__USE_MATCHER)))
          return true;
      }
    }

    return false;
  }

  public boolean containsOrEqualsPredicate(PredicateAndLabels predicateAndLabels) {
    if (predicateAndLabels.itemPredicate == null)
      return true;

    for (var activePredicate : activePredicatesInOrder) {
      if (activePredicate.itemPredicate == null)
        continue;

      if (activePredicate.itemPredicate.containsOrEqualsPredicate(predicateAndLabels.itemPredicate, EnumSet.of(ComparisonFlag.MATERIAL_PREDICATE__INTERSECTION_SUFFICES)))
        return true;
    }

    return false;
  }
}
