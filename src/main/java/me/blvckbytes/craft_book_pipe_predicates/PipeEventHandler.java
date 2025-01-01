package me.blvckbytes.craft_book_pipe_predicates;

import com.sk89q.craftbook.mechanics.pipe.PipeFilterEvent;
import me.blvckbytes.bukkitevaluable.ConfigKeeper;
import me.blvckbytes.craft_book_pipe_predicates.config.MainSection;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class PipeEventHandler implements Listener {

  private final PredicateDataHandler dataHandler;
  private final ConfigKeeper<MainSection> config;
  private final List<SignChangeEvent> fakeEvents;

  public PipeEventHandler(PredicateDataHandler dataHandler, ConfigKeeper<MainSection> config) {
    this.dataHandler = dataHandler;
    this.config = config;
    this.fakeEvents = new ArrayList<>();
  }

  public boolean canEditSign(Player player, Sign sign) {
    var fakeChangeEvent = new SignChangeEvent(sign.getBlock(), player, sign.getLines());
    fakeEvents.add(fakeChangeEvent);
    Bukkit.getPluginManager().callEvent(fakeChangeEvent);
    fakeEvents.remove(fakeChangeEvent);
    return !fakeChangeEvent.isCancelled();
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void onSignChange(SignChangeEvent event) {
    if (!(event.getBlock().getState() instanceof Sign sign))
      return;

    if (!sign.getLine(1).equalsIgnoreCase(MarkerConstants.PIPE_MARKER))
      return;

    if (fakeEvents.contains(event))
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
    var pistonSign = BlockUtility.getPistonSign(pistonBlock);

    if (pistonSign == null)
      return;

    var predicateData = dataHandler.access(pistonSign);

    if (predicateData == null)
      return;

    var firstLineContent = pistonSign.getLine(0);

    if (predicateData.parsedPredicate() == null) {
      if (!firstLineContent.startsWith("§" + MarkerConstants.PREDICATE_ERROR_COLOR)) {
        pistonSign.setLine(0, "§" + MarkerConstants.PREDICATE_ERROR_COLOR + ChatColor.stripColor(firstLineContent));
        pistonSign.update(true, false);
      }

      return;
    }

    if (!firstLineContent.startsWith("§" + MarkerConstants.PREDICATE_OK_COLOR)) {
      pistonSign.setLine(0, "§" + MarkerConstants.PREDICATE_OK_COLOR + ChatColor.stripColor(firstLineContent));
      pistonSign.update(true, false);
    }

    var result = new ArrayList<ItemStack>();

    for (var item : event.getItems()) {
      if (predicateData.parsedPredicate().test(item))
        result.add(item);
    }

    event.setFilteredItems(result);
  }
}
