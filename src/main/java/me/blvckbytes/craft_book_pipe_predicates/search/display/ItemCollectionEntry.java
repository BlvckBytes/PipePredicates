package me.blvckbytes.craft_book_pipe_predicates.search.display;

import at.blvckbytes.cm_mapper.ConfigKeeper;
import at.blvckbytes.component_markup.expression.interpreter.InterpretationEnvironment;
import me.blvckbytes.craft_book_pipe_predicates.config.MainSection;
import me.blvckbytes.craft_book_pipe_predicates.search.ItemAndSlot;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ItemCollectionEntry implements ResultDisplayEntry {

  private final List<ItemAndSlot> members;

  private final ItemStack type;
  private final int stackSize;

  private @Nullable ItemAndSlot lastNextMember;

  private ItemCollectionEntry(List<ItemAndSlot> members, ItemStack type) {
    this.members = members;

    // Let's create an independent copy for the representative at this point, as the first member may be
    // taken out during the screen-session by somebody else and thereby become unusable for further rendering.
    this.type = new ItemStack(type);
    this.type.setAmount(1);

    this.stackSize = this.type.getMaxStackSize() > 0 ? this.type.getMaxStackSize() : 1;
  }

  public int getStackSize() {
    return stackSize;
  }

  public Material getMaterial() {
    return this.type.getType();
  }

  public boolean isEmpty() {
    return members.isEmpty();
  }

  public @Nullable ItemAndSlot getNextMember() {
    if (members.isEmpty())
      return null;

    // Return the last result until it has been used up completely, as to avoid
    // taking only a little bit from all the full stacks when handing out in the loop.
    if (lastNextMember != null) {
      if (lastNextMember.item().getAmount() > 0)
        return lastNextMember;

      lastNextMember = null;
    }

    for (var member : members) {
      if (member.item().getAmount() >= stackSize)
        return (lastNextMember = member);
    }

    return (lastNextMember = members.get(0));
  }

  public void removeMember(ItemAndSlot member) {
    if (member == lastNextMember)
      lastNextMember = null;

    members.remove(member);
  }

  public final List<ItemStackEntry> getMembersAsEntries() {
    var result = new ArrayList<ItemStackEntry>(members.size());

    for (var member : members)
      result.add(new ItemStackEntry(member));

    return result;
  }

  @Override
  public ItemStack makeRepresentative(ConfigKeeper<MainSection> config) {
    var representativeItem = new ItemStack(type);

    var totalAmount = 0;

    for (var memberIterator = members.iterator(); memberIterator.hasNext();) {
      var member = memberIterator.next();
      var amount = member.item().getAmount();

      if (amount <= 0) {
        memberIterator.remove();

        if (member == lastNextMember)
          lastNextMember = null;
      }

      totalAmount += amount;
    }

    var numberStacks = totalAmount / stackSize;
    var singleItems = totalAmount % stackSize;
    var numberDoubleChests = (double) numberStacks / (6 * 9);

    config.rootSection.resultDisplay.items.collectionRepresentativePatch.patch(
      representativeItem,
      new InterpretationEnvironment()
        .withVariable("number_stacks", numberStacks)
        .withVariable("number_double_chests", numberDoubleChests)
        .withVariable("single_items", singleItems)
        .withVariable("stack_size", stackSize)
    );

    return representativeItem;
  }

  public static List<ItemCollectionEntry> collectEntries(List<ItemAndSlot> items) {
    var results = new ArrayList<ItemCollectionEntry>();

    for (var item : items) {
      var bucket = findBucketFor(item.item(), results);

      if (bucket == null) {
        bucket = new ItemCollectionEntry(new ArrayList<>(), item.item());
        results.add(bucket);
      }

      bucket.members.add(item);
    }

    return results;
  }

  private static @Nullable ItemCollectionEntry findBucketFor(ItemStack item, List<ItemCollectionEntry> existingCollections) {
    for (var collection : existingCollections) {
      if (item.isSimilar(collection.type))
        return collection;
    }

    return null;
  }
}
