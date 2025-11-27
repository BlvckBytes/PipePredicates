package me.blvckbytes.craft_book_pipe_predicates;

import com.sk89q.craftbook.mechanics.pipe.PipeFilterEvent;
import com.sk89q.craftbook.mechanics.pipe.PipeSignCacheEvent;
import me.blvckbytes.bukkitevaluable.ConfigKeeper;
import me.blvckbytes.craft_book_pipe_predicates.config.MainSection;
import me.blvckbytes.item_predicate_parser.predicate.ItemPredicate;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PipeEventHandler implements Listener {

  private final Plugin plugin;
  private final PredicateDataHandler dataHandler;
  private final ConfigKeeper<MainSection> config;
  private final Logger logger;

  public PipeEventHandler(
    Plugin plugin,
    PredicateDataHandler dataHandler,
    ConfigKeeper<MainSection> config,
    Logger logger
  ) {
    this.plugin = plugin;
    this.dataHandler = dataHandler;
    this.config = config;
    this.logger = logger;
  }

  private void callFakeEvent(SignChangeEvent event) {
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

  public boolean canEditSign(Player player, Sign sign) {
    var fakeChangeEvent = new SignChangeEvent(sign.getBlock(), player, sign.getLines());
    callFakeEvent(fakeChangeEvent);
    return !fakeChangeEvent.isCancelled();
  }

  @EventHandler
  public void onPipeSignCache(PipeSignCacheEvent event) {
    var predicateData = dataHandler.access(event.sign);

    if (predicateData != null)
      event.pipeSign.setPluginData(plugin, predicateData.parsedPredicate());
  }

  @EventHandler
  public void onPipeFilter(PipeFilterEvent event) {
    var sign = event.getSign();

    if (sign == null)
      return;

    var predicate = (ItemPredicate) sign.getPluginData(plugin);

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
