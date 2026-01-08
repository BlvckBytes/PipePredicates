package me.blvckbytes.craft_book_pipe_predicates;

import at.blvckbytes.cm_mapper.ConfigKeeper;
import com.sk89q.craftbook.mechanics.pipe.*;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import me.blvckbytes.craft_book_pipe_predicates.config.MainSection;
import me.blvckbytes.item_predicate_parser.predicate.ItemPredicate;
import me.blvckbytes.item_predicate_parser.translation.PredicateSourcesReloadEvent;
import org.bukkit.Bukkit;
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

  private record CachedSign(ItemPredicate predicate, int x, int y, int z) {}

  private final PredicateDataHandler dataHandler;
  private final ConfigKeeper<MainSection> config;
  private final Logger logger;

  private final Map<UUID, Long2ObjectMap<CachedSign>> cachedSignByPistonIdByWorldId;

  public PipeEventHandler(
    PredicateDataHandler dataHandler,
    ConfigKeeper<MainSection> config,
    Logger logger
  ) {
    this.dataHandler = dataHandler;
    this.config = config;
    this.logger = logger;

    this.cachedSignByPistonIdByWorldId = new HashMap<>();
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
  public void onPredicateSourcesReload(PredicateSourcesReloadEvent event) {
    for (var worldBucketEntry : cachedSignByPistonIdByWorldId.entrySet()) {
      var signByPistonId = worldBucketEntry.getValue();
      var world = Bukkit.getWorld(worldBucketEntry.getKey());

      if (world == null) {
        signByPistonId.clear();
        continue;
      }

      var cachedSigns = signByPistonId.values();

      // Avoid 1) concurrent modification and 2) needless hash-lookups for invalidation
      var invalidateEvents = new ArrayList<InvalidateCachedBlockEvent>(cachedSigns.size());

      for (var cachedSign : cachedSigns) {
        var signBlock = world.getBlockAt(cachedSign.x, cachedSign.y, cachedSign.z);
        invalidateEvents.add(new InvalidateCachedBlockEvent(signBlock));
      }

      signByPistonId.clear();

      for (var invalidateEvent : invalidateEvents)
        Bukkit.getPluginManager().callEvent(invalidateEvent);
    }
  }

  @EventHandler
  public void onPipeSignCache(PipeSignCacheCreatedEvent event) {
    var sign = event.getPipeSign();
    var predicateData = dataHandler.access(sign);

    if (predicateData != null) {
      var worldId = event.getPistonBlock().getWorld().getUID();
      var predicateCache = cachedSignByPistonIdByWorldId.computeIfAbsent(worldId, k -> new Long2ObjectOpenHashMap<>());
      var compactId = CompactId.computeWorldlessBlockId(event.getPistonBlock());
      var cachedSign = new CachedSign(predicateData.parsedPredicate(), sign.getX(), sign.getY(), sign.getZ());
      predicateCache.put(compactId, cachedSign);
    }
  }

  @EventHandler
  public void onPipeSignCacheInvalidated(PipeSignCacheInvalidedEvent event) {
    var worldId = event.getPistonBlock().getWorld().getUID();
    var predicateCache = cachedSignByPistonIdByWorldId.get(worldId);

    if (predicateCache != null)
      predicateCache.remove(CompactId.computeWorldlessBlockId(event.getPistonBlock()));
  }

  @EventHandler
  public void onPipeFilter(PipeFilterEvent event) {
    var worldId = event.getBlock().getWorld().getUID();
    var signCache = cachedSignByPistonIdByWorldId.get(worldId);

    if (signCache == null)
      return;

    var cachedSign = signCache.get(CompactId.computeWorldlessBlockId(event.getBlock()));

    if (cachedSign == null)
      return;

    var result = new ArrayList<ItemStack>();

    for (var item : event.getItems()) {
      if (cachedSign.predicate.test(item))
        result.add(item);
    }

    event.setFilteredItems(result);
  }
}
