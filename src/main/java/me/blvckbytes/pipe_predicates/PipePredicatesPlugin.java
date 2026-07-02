package me.blvckbytes.pipe_predicates;

import at.blvckbytes.cm_mapper.ConfigHandler;
import at.blvckbytes.cm_mapper.ConfigKeeper;
import at.blvckbytes.cm_mapper.ConfigKeeperReloadEvent;
import at.blvckbytes.cm_mapper.section.command.CommandUpdater;
import com.cryptomorin.xseries.XMaterial;
import me.blvckbytes.pipe_predicates.config.MainSection;
import me.blvckbytes.pipe_predicates.config.PipePredicateCommandSection;
import me.blvckbytes.pipe_predicates.config.PipeSearchCommandSection;
import me.blvckbytes.pipe_predicates.search.cubes.CubeRenderer;
import me.blvckbytes.pipe_predicates.search.PipeSearchHandler;
import me.blvckbytes.pipe_predicates.search.display.capacity.CapacityDisplayHandler;
import me.blvckbytes.pipe_predicates.search.display.search.SearchDisplayHandler;
import me.blvckbytes.item_predicate_parser.ItemPredicateParserPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.geysermc.floodgate.api.FloodgateApi;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.logging.Level;

public class PipePredicatesPlugin extends JavaPlugin implements Listener {

  private @Nullable SearchDisplayHandler searchDisplayHandler;
  private @Nullable CapacityDisplayHandler capacityDisplayHandler;
  private @Nullable ConfigKeeper<MainSection> config;
  private @Nullable Runnable updateCommands;

  @Override
  public void onEnable() {
    var logger = getLogger();

    try {
      // First invocation is quite heavy - warm up cache
      XMaterial.matchXMaterial(Material.AIR);

      var configHandler = new ConfigHandler(this, "config");
      config = new ConfigKeeper<>(configHandler, "config.yml", MainSection.class);

      var ipp = ItemPredicateParserPlugin.getInstance();

      if (ipp == null)
        throw new IllegalStateException("Depending on ItemPredicateParser to be successfully loaded");

      var predicateHelper = ipp.getPredicateHelper();

      FloodgateIntegration floodgateIntegration = player -> false;

      if (Bukkit.getPluginManager().isPluginEnabled("floodgate")) {
        var floodgate = FloodgateApi.getInstance();
        floodgateIntegration = player -> floodgate.isFloodgatePlayer(player.getUniqueId());
        logger.info("Integrated with floodgate as to detect Bedrock-players!");
      }

      var dataHandler = new PredicateDataHandler(this, predicateHelper, config);

      var pipeEventHandler = new PipeEventHandler(dataHandler, logger);
      Bukkit.getServer().getPluginManager().registerEvents(pipeEventHandler, this);

      searchDisplayHandler = new SearchDisplayHandler(config, this, floodgateIntegration);
      Bukkit.getServer().getPluginManager().registerEvents(searchDisplayHandler, this);

      capacityDisplayHandler = new CapacityDisplayHandler(config, this, floodgateIntegration);
      Bukkit.getServer().getPluginManager().registerEvents(capacityDisplayHandler, this);

      var cubeRenderer = new CubeRenderer(logger);

      Bukkit.getServer().getPluginManager().registerEvents(cubeRenderer, this);

      var pipeTeleportCommandHandler = new PipeTeleportCommand(config);
      Bukkit.getServer().getPluginManager().registerEvents(pipeTeleportCommandHandler, this);
      var pipeTeleportCommand = Objects.requireNonNull(getCommand("pipeteleport"));
      pipeTeleportCommand.setExecutor(pipeTeleportCommandHandler);

      var pipeSearchHandler = new PipeSearchHandler(pipeTeleportCommandHandler, pipeEventHandler, searchDisplayHandler, capacityDisplayHandler, cubeRenderer, config, this);

      Bukkit.getServer().getPluginManager().registerEvents(pipeSearchHandler, this);

      var pipePredicateCommandExecutor = new PipePredicateCommand(dataHandler, pipeEventHandler, pipeSearchHandler, ipp, cubeRenderer, config, this);
      getServer().getPluginManager().registerEvents(pipePredicateCommandExecutor, this);

      var pipePredicateCommand = Objects.requireNonNull(getCommand(PipePredicateCommandSection.INITIAL_NAME));
      pipePredicateCommand.setExecutor(pipePredicateCommandExecutor);

      var pipeSearchCommandHandler = new PipeSearchCommand(pipePredicateCommandExecutor, pipePredicateCommand);
      var pipeSearchCommand = Objects.requireNonNull(getCommand(PipeSearchCommandSection.INITIAL_NAME));
      pipeSearchCommand.setExecutor(pipeSearchCommandHandler);

      var commandUpdater = new CommandUpdater(this);

      updateCommands = () -> {
        config.rootSection.commands.pipePredicate.apply(pipePredicateCommand, commandUpdater);
        config.rootSection.commands.pipeSearch.apply(pipeSearchCommand, commandUpdater);

        commandUpdater.trySyncCommands();
      };

      updateCommands.run();

      Bukkit.getServer().getPluginManager().registerEvents(new CommandSendListener(this, config), this);

      Bukkit.getServer().getPluginManager().registerEvents(this, this);
    } catch (Exception e) {
      logger.log(Level.SEVERE, "Could not initialize plugin", e);
      Bukkit.getPluginManager().disablePlugin(this);
    }
  }

  @EventHandler
  public void onConfigReload(ConfigKeeperReloadEvent event) {
    if (event.configKeeper == config && updateCommands != null)
      updateCommands.run();
  }

  @Override
  public void onDisable() {
    if (searchDisplayHandler != null) {
      searchDisplayHandler.onShutdown();
      searchDisplayHandler = null;
    }

    if (capacityDisplayHandler != null) {
      capacityDisplayHandler.onShutdown();
      capacityDisplayHandler = null;
    }
  }
}
