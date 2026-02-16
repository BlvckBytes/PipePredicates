package me.blvckbytes.craft_book_pipe_predicates.search.display.search;

import at.blvckbytes.cm_mapper.ConfigKeeper;
import at.blvckbytes.component_markup.expression.interpreter.InterpretationEnvironment;
import com.sk89q.craftbook.mechanics.pipe.CompactId;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import me.blvckbytes.craft_book_pipe_predicates.FloodgateIntegration;
import me.blvckbytes.craft_book_pipe_predicates.config.MainSection;
import me.blvckbytes.craft_book_pipe_predicates.search.ItemAndSlot;
import me.blvckbytes.craft_book_pipe_predicates.search.display.DisplayHandler;
import org.apache.commons.lang3.mutable.MutableInt;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SearchDisplayHandler extends DisplayHandler<SearchDisplay, SearchDisplayData> {

  private static final BlockFace[] DIRECT_FACES = {
    BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST,
    BlockFace.UP, BlockFace.DOWN
  };

  private final Map<UUID, Long2ObjectMap<MutableInt>> viewCountByChunkHashByWorldId;
  private final Logger logger;

  public SearchDisplayHandler(
    ConfigKeeper<MainSection> config,
    Plugin plugin,
    FloodgateIntegration floodgateIntegration
  ) {
    super(config, plugin, floodgateIntegration);

    this.logger = plugin.getLogger();
    this.viewCountByChunkHashByWorldId = new HashMap<>();
  }

  @Override
  public SearchDisplay instantiateDisplay(Player player, SearchDisplayData displayData) {
    return new SearchDisplay(player, floodgateIntegration.isFloodgatePlayer(player), displayData, config, plugin);
  }

  private void handleStackAction(Player player, SearchDisplay display, StackAction stackAction, ItemStackEntry itemEntry) {
    if (stackAction == StackAction.TELEPORT_TO_CONTAINER) {
      player.closeInventory();
      teleportPlayerToContainer(player, itemEntry.itemAndSlot.block(), config);
      return;
    }

    if (stackAction == StackAction.MOVE_TO_INVENTORY) {
      var amountBefore = itemEntry.itemAndSlot.item().getAmount();
      var moveResult = moveItemIntoInventory(player, itemEntry.itemAndSlot);

      if (moveResult == MoveResult.NO_SPACE) {
        config.rootSection.playerMessages.commandPipePredicateSearchGetNoSpace.sendMessage(player);
        return;
      }

      int movedAmount = 0;

      if (moveResult == MoveResult.DID_MOVE || moveResult == MoveResult.INVALID_ITEM)
        display.removeEntry(itemEntry);

      // Only remove the entry if it has been moved wholly, e.g. not just decremented
      if (moveResult == MoveResult.DID_MOVE)
        movedAmount = amountBefore;
      else if (moveResult == MoveResult.DID_DECREMENT) {
        display.renderItems();
        movedAmount = amountBefore - itemEntry.itemAndSlot.item().getAmount();
      }

      if (movedAmount > 0)
        sendHandOutMessage(player, movedAmount, itemEntry.itemAndSlot.item().getMaxStackSize(), itemEntry.itemAndSlot.item().getType());

      return;
    }

    if (stackAction == StackAction.OPEN_CONTAINER)
      openContainer(player, itemEntry.itemAndSlot);
  }

  private void handleStackClick(Player player, SearchDisplay display, ClickType clickType, ItemStackEntry itemEntry) {
    if (display.isFloodgate) {
      if (clickType == ClickType.LEFT) {
        handleStackAction(player, display, display.getStackAction(), itemEntry);
        return;
      }

      if (clickType == ClickType.DROP || clickType == ClickType.CONTROL_DROP) {
        display.nextStackAction();
        return;
      }

      return;
    }

    if (clickType == ClickType.LEFT) {
      handleStackAction(player, display, StackAction.TELEPORT_TO_CONTAINER, itemEntry);
      return;
    }

    if (clickType == ClickType.DROP) {
      handleStackAction(player, display, StackAction.MOVE_TO_INVENTORY, itemEntry);
      return;
    }

    if (clickType == ClickType.CONTROL_DROP)
      handleStackAction(player, display, StackAction.OPEN_CONTAINER, itemEntry);
  }

  private void handleCollectionClick(Player player, SearchDisplay display, ClickType clickType, ItemCollectionEntry collectionEntry) {
    if (display.isFloodgate) {
      if (clickType == ClickType.LEFT) {
        handleCollectionAction(player, display, display.getCollectionAction(), collectionEntry);
        return;
      }

      if (clickType == ClickType.DROP || clickType == ClickType.CONTROL_DROP) {
        display.nextCollectionAction();
        return;
      }

      return;
    }

    if (clickType == ClickType.LEFT) {
      handleCollectionAction(player, display, CollectionAction.SHOW_STACKS, collectionEntry);
      return;
    }

    if (clickType == ClickType.DROP) {
      handleCollectionAction(player, display, CollectionAction.GET_ONE_STACK, collectionEntry);
      return;
    }

    if (clickType == ClickType.CONTROL_DROP) {
      handleCollectionAction(player, display, CollectionAction.FILL_INVENTORY, collectionEntry);
      return;
    }

    if (clickType == ClickType.RIGHT)
      handleCollectionAction(player, display, CollectionAction.GET_FOUR_STACKS, collectionEntry);
  }

  private void handleCollectionAction(Player player, SearchDisplay display, CollectionAction action, ItemCollectionEntry collectionEntry) {
    if (action == CollectionAction.SHOW_STACKS) {
      show(player, new SearchDisplayData(display.displayData.predicate(), collectionEntry.getMembersAsEntries(), display));
      return;
    }

    if (action == CollectionAction.GET_ONE_STACK) {
      handleMovingItems(player, display, collectionEntry, 1);
      return;
    }

    if (action == CollectionAction.FILL_INVENTORY) {
      handleMovingItems(player, display, collectionEntry, Integer.MAX_VALUE);
      return;
    }

    if (action == CollectionAction.GET_FOUR_STACKS)
      handleMovingItems(player, display, collectionEntry, 4);
  }

  private void sendHandOutMessage(Player player, int totalHandOutAmount, int stackSize, Material type) {
    var numberStacks = totalHandOutAmount / stackSize;
    var singleItems = totalHandOutAmount % stackSize;
    var numberDoubleChests = (double) numberStacks / (6 * 9);

    config.rootSection.playerMessages.commandPipePredicateSearchGetItemSuccess.sendMessage(
      player,
      new InterpretationEnvironment()
        .withVariable("number_stacks", numberStacks)
        .withVariable("number_double_chests", numberDoubleChests)
        .withVariable("stack_size", stackSize)
        .withVariable("single_items", singleItems)
        .withVariable("item_type_key", type.translationKey())
    );
  }

  // TODO: Move four stacks will get four partial stacks => in the worst case less than a full stack
  private void handleMovingItems(Player player, SearchDisplay display, ItemCollectionEntry collectionEntry, int maxMoveCount) {
    var totalHandOutAmount = 0;
    var ranOutOfSpace = false;

    ItemAndSlot nextMember;

    while ((nextMember = collectionEntry.getNextMember()) != null) {
      var amountBefore = nextMember.item().getAmount();
      var moveResult = moveItemIntoInventory(player, nextMember);

      if (moveResult == MoveResult.NO_SPACE) {
        ranOutOfSpace = true;
        break;
      }

      if (moveResult == MoveResult.DID_MOVE)
        totalHandOutAmount += amountBefore;
      else if (moveResult == MoveResult.DID_DECREMENT) {
        var amountAfter = nextMember.item().getAmount();
        totalHandOutAmount += amountBefore - amountAfter;
      }

      if (moveResult == MoveResult.INVALID_ITEM || moveResult == MoveResult.DID_MOVE)
        collectionEntry.removeMember(nextMember);

      if (--maxMoveCount <= 0)
        break;
    }

    if (totalHandOutAmount > 0)
      sendHandOutMessage(player, totalHandOutAmount, collectionEntry.getStackSize(), collectionEntry.getMaterial());

    else if (ranOutOfSpace)
      config.rootSection.playerMessages.commandPipePredicateSearchGetNoSpace.sendMessage(player);

    // Exhausted the collection - make it vanish altogether
    if (collectionEntry.isEmpty()) {
      display.removeEntry(collectionEntry);
      display.show();
      return;
    }

    // Synchronize the displayed counts
    if (totalHandOutAmount > 0)
      display.renderItems();
  }

  @Override
  protected void handleClick(Player player, SearchDisplay display, ClickType clickType, int slot) {
    var targetEntry = display.getEntryCorrespondingToSlot(slot);

    if (targetEntry != null) {
      if (targetEntry instanceof ItemStackEntry itemStackEntry) {
        handleStackClick(player, display, clickType, itemStackEntry);
        return;
      }

      if (targetEntry instanceof ItemCollectionEntry collectionEntry) {
        handleCollectionClick(player, display, clickType, collectionEntry);
        return;
      }

      logger.warning("Encountered unaccounted-for result-display entry-type: " + targetEntry.getClass());
      return;
    }

    if (clickType == ClickType.LEFT) {
      if (config.rootSection.searchDisplay.items.previousPage.getDisplaySlots().contains(slot)) {
        display.previousPage();
        return;
      }

      if (config.rootSection.searchDisplay.items.nextPage.getDisplaySlots().contains(slot)) {
        display.nextPage();
        return;
      }

      if (display.displayData.backToDisplay() != null && config.rootSection.searchDisplay.items.backToCollectionsButton.getDisplaySlots().contains(slot)) {
        reopen(display.displayData.backToDisplay());
        return;
      }
    }

    if (clickType == ClickType.RIGHT) {
      if (config.rootSection.searchDisplay.items.previousPage.getDisplaySlots().contains(slot)) {
        display.firstPage();
        return;
      }

      if (config.rootSection.searchDisplay.items.nextPage.getDisplaySlots().contains(slot))
        display.lastPage();
    }
  }

  @EventHandler
  public void onInventoryClose(InventoryCloseEvent event) {
    // DisplayHandler - of utmost importance to call it as well!
    super.onInventoryClose(event);

    modifyInventoryViewCounter(event.getPlayer().getOpenInventory().getTopInventory(), false);
  }

  public static void teleportPlayerToContainer(Player player, Block block, ConfigKeeper<MainSection> config) {
    var destinationBlock = block;
    var targetContainer = block;

    if (block.getBlockData() instanceof Directional directional) {
      var facing = directional.getFacing();
      destinationBlock = block.getRelative(facing.getModX(), facing.getModY(), facing.getModZ());

      // [1] Move one more away in this direction if possible, to allow for some breathing-space.
      if (destinationBlock.isPassable())
        destinationBlock = destinationBlock.getRelative(facing.getModX(), facing.getModY(), facing.getModZ());
    }

    if (!destinationBlock.isPassable() && block.getState() instanceof Container container) {
      var blocks = new Block[]{ block, null };

      if (container.getInventory() instanceof DoubleChestInventory doubleInventory) {
        if (doubleInventory.getRightSide().getHolder() instanceof Container rightContainer)
          blocks[0] = rightContainer.getBlock();

        if (doubleInventory.getLeftSide().getHolder() instanceof Container leftContainer)
          blocks[1] = leftContainer.getBlock();
      }

      blockLoop: for (var currentBlock : blocks) {
        if (currentBlock == null)
          continue;

        for (var nextFacing : DIRECT_FACES) {
          var nextDestinationBlock = currentBlock.getRelative(nextFacing.getModX(), nextFacing.getModY(), nextFacing.getModZ());

          if (!nextDestinationBlock.isPassable())
            continue;

          // Ensure that the player looks at the closest container to them, in case of a double-chest
          // with the short-side pointing outwards to where they'll be teleported.
          targetContainer = currentBlock;

          // Same as [1]
          var oneMoreApart = nextDestinationBlock.getRelative(nextFacing.getModX(), nextFacing.getModY(), nextFacing.getModZ());

          if (oneMoreApart.isPassable())
            destinationBlock = oneMoreApart;
          else
            destinationBlock = nextDestinationBlock;

          break blockLoop;
        }
      }
    }

    var environment = getBlockEnvironment(block);

    if (!destinationBlock.isPassable()) {
      config.rootSection.playerMessages.commandPipePredicateSearchContainerTeleportObstructed.sendMessage(player, environment);
      return;
    }

    var lookedAtCenter = targetContainer.getLocation().add(.5, .5, .5);

    // Center up on the destination-block; otherwise, the player will be partially
    // stuck in a block in tight spaces.
    var footLocation = destinationBlock.getLocation().add(.5, 0, .5);

    var eyeLocation = footLocation.clone().add(0, 1.6, 0);
    var direction = lookedAtCenter.toVector().subtract(eyeLocation.toVector()).normalize();
    footLocation.setDirection(direction);

    player.teleport(footLocation);

    config.rootSection.playerMessages.commandPipePredicateSearchContainerTeleported.sendMessage(player, environment);
  }

  private MoveResult moveItemIntoInventory(Player player, ItemAndSlot item) {
    var block = item.block();

    if (!(block.getState() instanceof Container container)) {
      var environment = getBlockEnvironment(block);
      config.rootSection.playerMessages.commandPipePredicateSearchGetItemContainerAbsent.sendMessage(player, environment);
      return MoveResult.INVALID_ITEM;
    }

    var environment = getBlockEnvironment(item.block());

    var containerInventory = container.getInventory();

    if (item.slot() < 0 || item.slot() >= containerInventory.getSize()) {
      config.rootSection.playerMessages.commandPipePredicateSearchGetItemContainerSizeChanged.sendMessage(player, environment);
      return MoveResult.INVALID_ITEM;
    }

    var targetItem = containerInventory.getItem(item.slot());

    environment
      .withVariable("item_slot", item.slot() + 1)
      .withVariable("item_amount", item.item().getAmount())
      .withVariable("item_type_key", item.type().translationKey());

    if (!item.item().equals(targetItem)) {
      config.rootSection.playerMessages.commandPipePredicateSearchGetItemMoved.sendMessage(player, environment);
      return MoveResult.INVALID_ITEM;
    }

    var playerInventory = player.getInventory();
    var didDecrement = false;

    for (var slot = 0; slot < 9 * 4; ++slot) {
      var playerItem = playerInventory.getItem(slot);

      if (playerItem == null || playerItem.getType().isAir()) {
        playerInventory.setItem(slot, targetItem);
        containerInventory.setItem(item.slot(), null);
        return MoveResult.DID_MOVE;
      }

      if (!targetItem.isSimilar(playerItem))
        continue;

      var remainingSpace = playerItem.getMaxStackSize() - playerItem.getAmount();
      var amountToAdd = Math.min(remainingSpace, targetItem.getAmount());

      if (amountToAdd <= 0)
        continue;

      playerItem.setAmount(playerItem.getAmount() + amountToAdd);
      targetItem.setAmount(targetItem.getAmount() - amountToAdd);

      didDecrement = true;

      if (targetItem.getAmount() <= 0) {
        containerInventory.setItem(item.slot(), null);
        return MoveResult.DID_MOVE;
      }
    }

    return didDecrement ? MoveResult.DID_DECREMENT : MoveResult.NO_SPACE;
  }

  private void openContainer(Player player, ItemAndSlot item) {
    var block = item.block();

    if (!(block.getState() instanceof Container container)) {
      var environment = getBlockEnvironment(block);
      config.rootSection.playerMessages.commandPipePredicateSearchGetItemContainerAbsent.sendMessage(player, environment);
      return;
    }

    var containerInventory = container.getInventory();
    var environment = getBlockEnvironment(item.block());

    config.rootSection.playerMessages.commandPipePredicateSearchContainerOpened.sendMessage(player, environment);
    player.openInventory(containerInventory);

    modifyInventoryViewCounter(containerInventory, true);
  }

  private void modifyInventoryViewCounter(Inventory inventory, boolean increment) {
    if (inventory instanceof DoubleChestInventory doubleInventory) {
      if (doubleInventory.getRightSide().getHolder() instanceof Container rightContainer)
        modifyBlockViewCounter(rightContainer.getBlock(), increment);

      if (doubleInventory.getLeftSide().getHolder() instanceof Container leftContainer)
        modifyBlockViewCounter(leftContainer.getBlock(), increment);

      return;
    }

    if (inventory.getHolder() instanceof Container container)
      modifyBlockViewCounter(container.getBlock(), increment);
  }

  private void modifyBlockViewCounter(Block block, boolean increment) {
    var location = block.getLocation();
    var world = location.getWorld();

    if (world == null) {
      plugin.getLogger().log(Level.WARNING, "Could not get world of block within #modifyBlockViewCounter");
      return;
    }

    var worldBucket = viewCountByChunkHashByWorldId.computeIfAbsent(world.getUID(), k -> new Long2ObjectOpenHashMap<>());
    var chunkId = CompactId.computeWorldlessChunkId(location.getBlockX() >> 4, location.getBlockZ() >> 4);

    var viewCount = worldBucket.get(chunkId);

    if (viewCount == null) {
      if (increment) {
        worldBucket.put(chunkId, new MutableInt(1));

        if (!block.getChunk().addPluginChunkTicket(plugin))
          plugin.getLogger().log(Level.WARNING, "Could not add chunk-ticket for block at " + block.getLocation());
      }

      return;
    }

    if (increment) {
      viewCount.increment();
      return;
    }

    if (viewCount.decrementAndGet() != 0)
      return;

    worldBucket.remove(chunkId);

    if (!block.getChunk().removePluginChunkTicket(plugin))
      plugin.getLogger().log(Level.WARNING, "Could not remove chunk-ticket for block at " + block.getLocation());
  }

  public static InterpretationEnvironment getBlockEnvironment(Block block) {
    return new InterpretationEnvironment()
      .withVariable("container_x", block.getX())
      .withVariable("container_y", block.getY())
      .withVariable("container_z", block.getZ());
  }
}
