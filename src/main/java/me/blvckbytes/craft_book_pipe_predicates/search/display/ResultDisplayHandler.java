package me.blvckbytes.craft_book_pipe_predicates.search.display;

import at.blvckbytes.cm_mapper.ConfigKeeper;
import at.blvckbytes.component_markup.expression.interpreter.InterpretationEnvironment;
import com.sk89q.craftbook.mechanics.pipe.CompactId;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import me.blvckbytes.craft_book_pipe_predicates.config.MainSection;
import me.blvckbytes.craft_book_pipe_predicates.search.ItemAndSlot;
import me.blvckbytes.item_predicate_parser.PredicateHelper;
import me.blvckbytes.item_predicate_parser.TranslationLanguageRegistry;
import org.apache.commons.lang3.mutable.MutableInt;
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
import java.util.function.Consumer;
import java.util.logging.Level;

public class ResultDisplayHandler extends DisplayHandler<ResultDisplay, ResultDisplayData> {

  private static final BlockFace[] DIRECT_FACES = {
    BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST,
    BlockFace.UP, BlockFace.DOWN
  };

  private final Map<UUID, Long2ObjectMap<MutableInt>> viewCountByChunkHashByWorldId;

  private final PredicateHelper predicateHelper;
  private final TranslationLanguageRegistry languageRegistry;

  public ResultDisplayHandler(
    PredicateHelper predicateHelper,
    TranslationLanguageRegistry languageRegistry,
    ConfigKeeper<MainSection> config,
    Plugin plugin
  ) {
    super(config, plugin);

    this.predicateHelper = predicateHelper;
    this.languageRegistry = languageRegistry;

    this.viewCountByChunkHashByWorldId = new HashMap<>();
  }

  @Override
  public ResultDisplay instantiateDisplay(Player player, ResultDisplayData displayData) {
    return new ResultDisplay(config, plugin, player, displayData);
  }

  @Override
  protected void handleClick(Player player, ResultDisplay display, ClickType clickType, int slot) {
    var targetItem = display.getShopCorrespondingToSlot(slot);

    if (clickType == ClickType.LEFT) {
      if (config.rootSection.resultDisplay.items.previousPage.getDisplaySlots().contains(slot)) {
        display.previousPage();
        return;
      }

      if (config.rootSection.resultDisplay.items.nextPage.getDisplaySlots().contains(slot)) {
        display.nextPage();
        return;
      }

      if (targetItem != null) {
        player.closeInventory();
        teleportPlayerToContainer(player, targetItem.block());
        return;
      }

      return;
    }

    if (targetItem != null) {
      if (clickType == ClickType.DROP) {
        moveItemIntoInventory(display, player, targetItem);
        return;
      }

      if (clickType == ClickType.CONTROL_DROP) {
        openContainer(player, targetItem);
        return;
      }

      return;
    }

    if (clickType == ClickType.RIGHT) {
      if (config.rootSection.resultDisplay.items.previousPage.getDisplaySlots().contains(slot)) {
        display.firstPage();
        return;
      }

      if (config.rootSection.resultDisplay.items.nextPage.getDisplaySlots().contains(slot))
        display.lastPage();
    }
  }

  @EventHandler
  public void onInventoryClose(InventoryCloseEvent event) {
    // DisplayHandler - of utmost importance to call it as well!
    super.onInventoryClose(event);

    modifyInventoryViewCounter(event.getPlayer().getOpenInventory().getTopInventory(), false);
  }

  private void teleportPlayerToContainer(Player player, Block block) {
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

  private void moveItemIntoInventory(ResultDisplay resultDisplay, Player player, ItemAndSlot item) {
    tryAccessContainer(player, item, container -> {
      var environment = getBlockEnvironment(item.block());

      var containerInventory = container.getInventory();
      var blockContents = containerInventory.getStorageContents();

      if (item.slot() < 0 || item.slot() >= blockContents.length) {
        config.rootSection.playerMessages.commandPipePredicateSearchGetItemContainerSizeChanged.sendMessage(player, environment);
        return;
      }

      var targetItem = blockContents[item.slot()];

      resultDisplay.removeItem(item);

      var typeTranslation = languageRegistry
        .getTranslationRegistry(predicateHelper.getSelectedLanguage(player))
        .getTranslationBySingleton(item.item().getType());

      if (typeTranslation == null)
        typeTranslation = item.item().getType().name();

      environment
        .withVariable("item_slot", item.slot() + 1)
        .withVariable("item_amount", item.item().getAmount())
        .withVariable("item_type", typeTranslation);

      if (!item.item().equals(targetItem)) {
        config.rootSection.playerMessages.commandPipePredicateSearchGetItemMoved.sendMessage(player, environment);
        return;
      }

      blockContents[item.slot()] = null;
      containerInventory.setStorageContents(blockContents);

      config.rootSection.playerMessages.commandPipePredicateSearchGetItemSuccess.sendMessage(player, environment);

      var remainders = player.getInventory().addItem(targetItem).values();

      if (!remainders.isEmpty()) {

        for (var remainder : remainders) {
          player.dropItem(remainder);

          config.rootSection.playerMessages.commandPipePredicateSearchGetItemDropped.sendMessage(
            player,
            environment
              .withVariable("dropped_amount", remainder.getAmount())
          );
        }
      }
    });
  }

  private void openContainer(Player player, ItemAndSlot item) {
    tryAccessContainer(player, item, container -> {
      var containerInventory = container.getInventory();
      var environment = getBlockEnvironment(item.block());

      config.rootSection.playerMessages.commandPipePredicateSearchContainerOpened.sendMessage(player, environment);
      player.openInventory(containerInventory);

      modifyInventoryViewCounter(containerInventory, true);
    });
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

  private void tryAccessContainer(Player player, ItemAndSlot item, Consumer<Container> containerHandler) {
    var block = item.block();

    if (!(block.getState() instanceof Container container)) {
      var environment = getBlockEnvironment(block);
      config.rootSection.playerMessages.commandPipePredicateSearchGetItemContainerAbsent.sendMessage(player, environment);
      return;
    }

    containerHandler.accept(container);
  }

  private InterpretationEnvironment getBlockEnvironment(Block block) {
    return new InterpretationEnvironment()
      .withVariable("container_x", block.getX())
      .withVariable("container_y", block.getY())
      .withVariable("container_z", block.getZ());
  }
}
