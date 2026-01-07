package me.blvckbytes.craft_book_pipe_predicates.search.cubes;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.wrappers.EnumWrappers;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.logging.Logger;

public class CubeRenderer implements Listener {

  private final Logger logger;
  private final ProtocolManager protocolManager;
  private final Map<UUID, PlayerState> playerStateByPlayerId;

  public CubeRenderer(Logger logger) {
    this.logger = logger;
    this.protocolManager = ProtocolLibrary.getProtocolManager();
    this.playerStateByPlayerId = new HashMap<>();
  }

  public boolean renderColoredCubes(Player player, Map<EnumWrappers.ChatFormatting, List<Vector>> cubePositionsByColor) {
    var playerState = playerStateByPlayerId.computeIfAbsent(player.getUniqueId(), k -> new PlayerState(player, protocolManager, logger));

    for (var colorEntry : cubePositionsByColor.entrySet()) {
      var color = colorEntry.getKey();

      var entityPositions = colorEntry.getValue();

      var entityIds = new ArrayList<EntityId>(entityPositions.size());
      var memberList = new ArrayList<String>(entityPositions.size());

      for (var entityPosition : entityPositions) {
        var entityId = playerState.spawnAndGetEntityId(entityPosition.getX(), entityPosition.getY(), entityPosition.getZ());

        if (entityId == null)
          return false;

        entityIds.add(entityId);
        memberList.add(entityId.uuid().toString());
      }

      if (!playerState.createOrUpdateTeam(color, memberList))
        return false;

      for (var entityId : entityIds) {
        if (!playerState.updateEntityMetadata(entityId))
          return false;
      }
    }

    return true;
  }

  public boolean removeAll(Player player) {
    var playerState = playerStateByPlayerId.get(player.getUniqueId());

    if (playerState == null)
      return false;

    return playerState.removeAll();
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    playerStateByPlayerId.remove(event.getPlayer().getUniqueId());
  }
}
