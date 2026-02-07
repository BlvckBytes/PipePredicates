package me.blvckbytes.craft_book_pipe_predicates;

import at.blvckbytes.cm_mapper.ConfigHandler;
import at.blvckbytes.cm_mapper.ConfigKeeper;
import at.blvckbytes.cm_mapper.section.command.CommandUpdater;
import com.cryptomorin.xseries.XMaterial;
import me.blvckbytes.craft_book_pipe_predicates.config.MainSection;
import me.blvckbytes.craft_book_pipe_predicates.config.PipePredicateCommandSection;
import me.blvckbytes.craft_book_pipe_predicates.config.PipeSearchCommandSection;
import me.blvckbytes.craft_book_pipe_predicates.search.cubes.CubeRenderer;
import me.blvckbytes.craft_book_pipe_predicates.search.PipeSearchHandler;
import me.blvckbytes.craft_book_pipe_predicates.search.display.capacity.CapacityDisplayHandler;
import me.blvckbytes.craft_book_pipe_predicates.search.display.search.SearchDisplayHandler;
import me.blvckbytes.item_predicate_parser.ItemPredicateParserPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.geysermc.floodgate.api.FloodgateApi;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.logging.Level;

public class CraftBookPipePredicatesPlugin extends JavaPlugin implements Listener {

  private int sessionTickerTaskId = -1;
  private @Nullable SearchDisplayHandler searchDisplayHandler;
  private @Nullable CapacityDisplayHandler capacityDisplayHandler;

  @Override
  public void onEnable() {
    var logger = getLogger();

    try {
      ensureCompatibleCraftBookVersion();

      // First invocation is quite heavy - warm up cache
      XMaterial.matchXMaterial(Material.AIR);

      var configHandler = new ConfigHandler(this, "config");
      var config = new ConfigKeeper<>(configHandler, "config.yml", MainSection.class);

      var parserPlugin = ItemPredicateParserPlugin.getInstance();

      if (parserPlugin == null)
        throw new IllegalStateException("Depending on ItemPredicateParser to be successfully loaded");

      var predicateHelper = parserPlugin.getPredicateHelper();

      FloodgateIntegration floodgateIntegration = player -> false;

      if (Bukkit.getPluginManager().isPluginEnabled("floodgate")) {
        var floodgate = FloodgateApi.getInstance();
        floodgateIntegration = player -> floodgate.isFloodgatePlayer(player.getUniqueId());
        logger.info("Integrated with floodgate as to detect Bedrock-players!");
      }

      var dataHandler = new PredicateDataHandler(this, predicateHelper, config);

      var pipeEventHandler = new PipeEventHandler(dataHandler, config, logger);
      Bukkit.getServer().getPluginManager().registerEvents(pipeEventHandler, this);

      searchDisplayHandler = new SearchDisplayHandler(config, this, floodgateIntegration);
      Bukkit.getServer().getPluginManager().registerEvents(searchDisplayHandler, this);

      capacityDisplayHandler = new CapacityDisplayHandler(config, this, floodgateIntegration);
      Bukkit.getServer().getPluginManager().registerEvents(capacityDisplayHandler, this);

      var cubeRenderer = new CubeRenderer(logger);

      Bukkit.getServer().getPluginManager().registerEvents(cubeRenderer, this);

      var pipeSearchHandler = new PipeSearchHandler(pipeEventHandler, searchDisplayHandler, capacityDisplayHandler, cubeRenderer, config, this);

      Bukkit.getServer().getPluginManager().registerEvents(pipeSearchHandler, this);

      var pipePredicateCommandExecutor = new PipePredicateCommand(dataHandler, pipeEventHandler, pipeSearchHandler, predicateHelper, cubeRenderer, config, this);
      getServer().getPluginManager().registerEvents(pipePredicateCommandExecutor, this);

      sessionTickerTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, pipePredicateCommandExecutor::tickSessions, 0L, 5L);

      var pipePredicateCommand = Objects.requireNonNull(getCommand(PipePredicateCommandSection.INITIAL_NAME));
      pipePredicateCommand.setExecutor(pipePredicateCommandExecutor);

      var pipeSearchCommandHandler = new PipeSearchCommand(pipePredicateCommandExecutor, pipePredicateCommand);
      var pipeSearchCommand = Objects.requireNonNull(getCommand(PipeSearchCommandSection.INITIAL_NAME));
      pipeSearchCommand.setExecutor(pipeSearchCommandHandler);

      var commandUpdater = new CommandUpdater(this);

      Runnable updateCommands = () -> {
        config.rootSection.commands.pipePredicate.apply(pipePredicateCommand, commandUpdater);
        config.rootSection.commands.pipeSearch.apply(pipeSearchCommand, commandUpdater);

        commandUpdater.trySyncCommands();
      };

      updateCommands.run();
      config.registerReloadListener(updateCommands);

      Bukkit.getServer().getPluginManager().registerEvents(new CommandSendListener(this, config), this);
    } catch (Exception e) {
      logger.log(Level.SEVERE, "Could not initialize plugin", e);
      Bukkit.getPluginManager().disablePlugin(this);
    }
  }

  @Override
  public void onDisable() {
    if (sessionTickerTaskId >= 0)
      Bukkit.getScheduler().cancelTask(sessionTickerTaskId);

    if (searchDisplayHandler != null) {
      searchDisplayHandler.onShutdown();
      searchDisplayHandler = null;
    }

    if (capacityDisplayHandler != null) {
      capacityDisplayHandler.onShutdown();
      capacityDisplayHandler = null;
    }
  }

  private static void ensureCompatibleCraftBookVersion() {
    var craftBookPlugin = Bukkit.getPluginManager().getPlugin("CraftBook");

    if (craftBookPlugin == null || !craftBookPlugin.isEnabled())
      throw new IllegalStateException("Expected there to be a loaded instance of CraftBook present");

    try {
      Class.forName("com.sk89q.craftbook.mechanics.pipe.PipeFilterEvent");
    } catch (Throwable e) {
      throw new IllegalStateException("Expected the PipeFilterEvent to be implemented; your version of CraftBook is likely too outdated.");
    }
  }
}
