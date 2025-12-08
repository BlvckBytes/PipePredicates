package me.blvckbytes.craft_book_pipe_predicates;

import com.sk89q.craftbook.mechanics.pipe.CompactId;
import com.sk89q.craftbook.mechanics.pipe.PipeFilterEvent;
import com.sk89q.craftbook.mechanics.pipe.PipeSignCacheCreatedEvent;
import com.sk89q.craftbook.mechanics.pipe.PipeSignCacheInvalidedEvent;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import me.blvckbytes.bukkitevaluable.ConfigKeeper;
import me.blvckbytes.craft_book_pipe_predicates.config.MainSection;
import me.blvckbytes.item_predicate_parser.predicate.ItemPredicate;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PipeEventHandler implements Listener {

  private final PredicateDataHandler dataHandler;
  private final ConfigKeeper<MainSection> config;
  private final Logger logger;

  private final Long2ObjectMap<ItemPredicate> pipePredicateByPistonBlockCompactId;

  public PipeEventHandler(
    PredicateDataHandler dataHandler,
    ConfigKeeper<MainSection> config,
    Logger logger
  ) {
    this.dataHandler = dataHandler;
    this.config = config;
    this.logger = logger;

    this.pipePredicateByPistonBlockCompactId = new Long2ObjectOpenHashMap<>();
  }

  private void callFakeEvent(Event event) {
    for(var listener : event.getHandlers().getRegisteredListeners()) {
      if(!listener.getPlugin().isEnabled())
        continue;

      if (listener.getListener().equals(this))
        continue;

      var listenerClass = listener.getListener().getClass();

      if (config.rootSection.fakeEventSkippedListeners.contains(listenerClass.getName()))
        continue;

      try {
        listener.callEvent(event);
      } catch (Exception e) {
        logger.log(Level.SEVERE, "Could not pass event " + event.getEventName() + " to " + listener.getPlugin().getDescription().getFullName(), e);
      }
    }
  }

  public boolean canBuildAt(Player player, Block block) {
    var fakePlaceEvent = new BlockPlaceEvent(block, block.getState(), block, new ItemStack(Material.DIRT), player, false, EquipmentSlot.HAND);
    callFakeEvent(fakePlaceEvent);
    return !fakePlaceEvent.isCancelled();
  }

  public boolean canEditSign(Player player, Sign sign) {
    //noinspection removal
    var fakeChangeEvent = new SignChangeEvent(sign.getBlock(), player, sign.getLines());
    callFakeEvent(fakeChangeEvent);
    return !fakeChangeEvent.isCancelled();
  }

  @EventHandler
  public void onPipeSignCache(PipeSignCacheCreatedEvent event) {
    var predicateData = dataHandler.access(event.getPipeSign());

    if (predicateData != null) {
      var compactId = CompactId.computeWorldfulBlockId(event.getPistonBlock());
      pipePredicateByPistonBlockCompactId.put(compactId, predicateData.parsedPredicate());
    }
  }

  @EventHandler
  public void onPipeSignCacheInvalidated(PipeSignCacheInvalidedEvent event) {
    pipePredicateByPistonBlockCompactId.remove(CompactId.computeWorldfulBlockId(event.getPistonBlock()));
  }

  @EventHandler
  public void onPipeFilter(PipeFilterEvent event) {
    var predicate = pipePredicateByPistonBlockCompactId.get(CompactId.computeWorldfulBlockId(event.getBlock()));

    if (predicate == null)
      return;

    var result = new ArrayList<ItemStack>();

    for (var item : event.getItems()) {
      if (predicate.test(item))
        result.add(item);
    }

    event.setFilteredItems(result);
  }
}
