package me.blvckbytes.craft_book_pipe_predicates.search;

import me.blvckbytes.craft_book_pipe_predicates.PredicateAndLanguage;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

public class PipeSearchHandler implements Listener {

  private final Plugin plugin;

  public PipeSearchHandler(Plugin plugin) {
    this.plugin = plugin;
  }

  public void handleSearch(Player player, Block pistonBlock, PredicateAndLanguage query) {
    player.sendMessage("§cComing soon! :^)");
  }
}
