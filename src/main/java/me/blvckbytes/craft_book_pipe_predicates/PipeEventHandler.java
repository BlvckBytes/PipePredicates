package me.blvckbytes.craft_book_pipe_predicates;

import com.sk89q.craftbook.mechanics.pipe.PipeFilterEvent;
import me.blvckbytes.bukkitevaluable.ConfigKeeper;
import me.blvckbytes.craft_book_pipe_predicates.config.MainSection;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PipeEventHandler implements Listener {

  private final PredicateDataHandler dataHandler;
  private final ConfigKeeper<MainSection> config;
  private final Logger logger;

  public PipeEventHandler(PredicateDataHandler dataHandler, ConfigKeeper<MainSection> config, Logger logger) {
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

  @EventHandler(priority = EventPriority.LOWEST)
  public void onSignChange(SignChangeEvent event) {
    if (!(event.getBlock().getState() instanceof Sign sign))
      return;

    if (!sign.getLine(1).equalsIgnoreCase(MarkerConstants.PIPE_MARKER))
      return;

    var predicateData = dataHandler.access(sign);

    if (predicateData == null)
      return;

    event.setCancelled(true);
    config.rootSection.playerMessages.manualEditWhileInPredicateMode.sendMessage(event.getPlayer(), config.rootSection.builtBaseEnvironment);
  }

  @EventHandler
  public void onPipeFilter(PipeFilterEvent event) {
    var pistonBlock = event.getBlock();
    var pistonSign = BlockUtility.getPistonSign(pistonBlock, false);

    if (pistonSign == null)
      return;

    var predicateData = dataHandler.access(pistonSign);

    if (predicateData == null)
      return;

    if (predicateData.parsedPredicate() == null)
      return;

    var result = new ArrayList<ItemStack>();

    for (var item : event.getItems()) {
      if (predicateData.parsedPredicate().test(item))
        result.add(item);
    }

    event.setFilteredItems(result);
  }
}
