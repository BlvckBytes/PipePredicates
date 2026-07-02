package me.blvckbytes.pipe_predicates.search.cubes;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.wrappers.EnumWrappers;
import me.blvckbytes.bbtweaks.pipes.mechanic.TubeColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.logging.Logger;

public class CubeRenderer implements Listener {

  private static final EnumWrappers.ChatFormatting[] chatFormattingByTubeColorOrdinal;

  static {
    chatFormattingByTubeColorOrdinal = new EnumWrappers.ChatFormatting[TubeColor.values().length];
    Arrays.fill(chatFormattingByTubeColorOrdinal, EnumWrappers.ChatFormatting.WHITE);

    chatFormattingByTubeColorOrdinal[TubeColor.ORANGE.ordinal()] = EnumWrappers.ChatFormatting.GOLD;
    chatFormattingByTubeColorOrdinal[TubeColor.MAGENTA.ordinal()] = EnumWrappers.ChatFormatting.LIGHT_PURPLE;
    chatFormattingByTubeColorOrdinal[TubeColor.LIGHT_BLUE.ordinal()] = EnumWrappers.ChatFormatting.BLUE;
    chatFormattingByTubeColorOrdinal[TubeColor.YELLOW.ordinal()] = EnumWrappers.ChatFormatting.YELLOW;
    chatFormattingByTubeColorOrdinal[TubeColor.LIME.ordinal()] = EnumWrappers.ChatFormatting.GREEN;
    chatFormattingByTubeColorOrdinal[TubeColor.PINK.ordinal()] = EnumWrappers.ChatFormatting.LIGHT_PURPLE;
    chatFormattingByTubeColorOrdinal[TubeColor.GRAY.ordinal()] = EnumWrappers.ChatFormatting.DARK_GRAY;
    chatFormattingByTubeColorOrdinal[TubeColor.LIGHT_GRAY.ordinal()] = EnumWrappers.ChatFormatting.GRAY;
    chatFormattingByTubeColorOrdinal[TubeColor.CYAN.ordinal()] = EnumWrappers.ChatFormatting.AQUA;
    chatFormattingByTubeColorOrdinal[TubeColor.PURPLE.ordinal()] = EnumWrappers.ChatFormatting.DARK_PURPLE;
    chatFormattingByTubeColorOrdinal[TubeColor.BLUE.ordinal()] = EnumWrappers.ChatFormatting.DARK_BLUE;
    chatFormattingByTubeColorOrdinal[TubeColor.GREEN.ordinal()] = EnumWrappers.ChatFormatting.DARK_GREEN;
    chatFormattingByTubeColorOrdinal[TubeColor.RED.ordinal()] = EnumWrappers.ChatFormatting.RED;
    chatFormattingByTubeColorOrdinal[TubeColor.BLACK.ordinal()] = EnumWrappers.ChatFormatting.BLACK;
  }

  private final Logger logger;
  private final ProtocolManager protocolManager;
  private final Map<UUID, PlayerState> playerStateByPlayerId;

  public CubeRenderer(Logger logger) {
    this.logger = logger;
    this.protocolManager = ProtocolLibrary.getProtocolManager();
    this.playerStateByPlayerId = new HashMap<>();
  }

  public boolean renderColoredCubes(Player player, Map<TubeColor, List<Vector>> cubePositionsByColor) {
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

      if (!playerState.createOrUpdateTeam(chatFormattingByTubeColorOrdinal[color.ordinal()], memberList))
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
