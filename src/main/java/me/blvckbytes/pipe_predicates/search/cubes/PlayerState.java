package me.blvckbytes.pipe_predicates.search.cubes;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedTeamParameters;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PlayerState {

  private static final int FIRST_ENTITY_ID = 2_000_000;

  private static final String[] teamNameByFormattingOrdinal;

  static {
    var chatFormats = EnumWrappers.ChatFormatting.values();

    teamNameByFormattingOrdinal = new String[chatFormats.length];

    for (var i = 0; i < chatFormats.length; ++i)
      teamNameByFormattingOrdinal[i] = "cube_render_" + chatFormats[i].toBukkit().getChar();
  }

  private final Player player;
  private final ProtocolManager protocolManager;
  private final Logger logger;

  private final WrappedChatComponent emptyText;
  private final boolean[] teamKnownStatusByFormattingOrdinal;
  private final WrappedDataWatcher.Serializer byteSerializer;

  private final List<EntityId> inUseEntityIds;
  private int nextEntityId = FIRST_ENTITY_ID;

  public PlayerState(Player player, ProtocolManager protocolManager, Logger logger) {
    this.protocolManager = protocolManager;
    this.logger = logger;
    this.player = player;

    this.emptyText = WrappedChatComponent.fromText("");
    this.teamKnownStatusByFormattingOrdinal = new boolean[teamNameByFormattingOrdinal.length];
    this.byteSerializer = WrappedDataWatcher.Registry.get((Type) Byte.class);

    this.inUseEntityIds = new ArrayList<>();
  }

  private EntityId getNextId() {
    var nextId = new EntityId(nextEntityId++, UUID.randomUUID());
    inUseEntityIds.add(nextId);
    return nextId;
  }

  public boolean removeAll() {
    if (inUseEntityIds.isEmpty())
      return false;

    removeEntities(inUseEntityIds);

    this.inUseEntityIds.clear();
    this.nextEntityId = FIRST_ENTITY_ID;

    return true;
  }

  public @Nullable EntityId spawnAndGetEntityId(double x, double y, double z) {
    var id = getNextId();

    try {
      var spawnPacket = protocolManager.createPacket(PacketType.Play.Server.SPAWN_ENTITY);

      spawnPacket.getIntegers()
        .write(0, id.numericId())
        // Data (for most mobs, 0 is fine)
        .write(1, 0);

      spawnPacket.getUUIDs().write(0, id.uuid());
      spawnPacket.getEntityTypeModifier().write(0, EntityType.SHULKER);

      // Position
      spawnPacket.getDoubles()
        .write(0, x)
        .write(1, y)
        .write(2, z);

      // Yaw, pitch and head-yaw
      spawnPacket.getBytes()
        .write(0, (byte) 0)
        .write(1, (byte) 0)
        .write(2, (byte) 0);

      // Velocity
      spawnPacket.getVectors()
        .write(0, new Vector(0, 0, 0));

      protocolManager.sendServerPacket(player, spawnPacket);

      return id;
    } catch (Throwable e) {
      logger.log(Level.SEVERE, "An error occurred while trying to spawn a cube-renderer fake-entity", e);
      return null;
    }
  }

  private void removeEntities(List<EntityId> ids) {
    try {
      var spawnPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_DESTROY);

      var numericIds = new ArrayList<Integer>(ids.size());

      for (var id : ids)
        numericIds.add(id.numericId());

      spawnPacket.getIntLists().write(0, numericIds);

      protocolManager.sendServerPacket(player, spawnPacket);
    } catch (Throwable e) {
      logger.log(Level.SEVERE, "An error occurred while trying to remove cube-renderer fake-entities", e);
    }
  }

  public boolean updateEntityMetadata(EntityId id) {
    try {
      var metadataPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_METADATA);

      metadataPacket.getIntegers().write(0, id.numericId());

      var watcher = new WrappedDataWatcher();

      // Invisible
      byte flags = 0x20;

      // Glowing
      flags |= 0x40;

      watcher.setObject(new WrappedDataWatcher.WrappedDataWatcherObject(0, byteSerializer), flags);

      metadataPacket.getDataValueCollectionModifier().write(0, watcher.toDataValueCollection());

      protocolManager.sendServerPacket(player, metadataPacket);
      return true;
    } catch (Throwable e) {
      logger.log(Level.SEVERE, "An error occurred while trying to update the metadata of a cube-renderer fake-entity", e);
      return false;
    }
  }

  private boolean removeTeam(EnumWrappers.ChatFormatting color) {
    try {
      var teamPacket = protocolManager.createPacket(PacketType.Play.Server.SCOREBOARD_TEAM);

      teamPacket.getStrings().write(0, teamNameByFormattingOrdinal[color.ordinal()]);

      // Mode 1 -> remove team
      teamPacket.getIntegers().write(0, 1);

      protocolManager.sendServerPacket(player, teamPacket);
      teamKnownStatusByFormattingOrdinal[color.ordinal()] = false;

      return true;
    } catch (Throwable e) {
      logger.log(Level.SEVERE, "An error occurred while trying to remove a team to be used with the cube-renderer", e);
      return false;
    }
  }

  public boolean createOrUpdateTeam(EnumWrappers.ChatFormatting color, List<String> entityId) {
    // As of now, I don't think that there's much of an advantage to actually update teams with the update-operation
    if (teamKnownStatusByFormattingOrdinal[color.ordinal()]) {
      if (!removeTeam(color)) {
        logger.severe("Cannot create team due to being unable to remove the old team");
        return false;
      }
    }

    try {
      var teamPacket = protocolManager.createPacket(PacketType.Play.Server.SCOREBOARD_TEAM);

      teamPacket.getStrings().write(0, teamNameByFormattingOrdinal[color.ordinal()]);

      // Mode 0 -> create team
      teamPacket.getIntegers().write(0, 0);

      teamPacket.getOptionalTeamParameters().write(
        0,
        Optional.of(
          WrappedTeamParameters.newBuilder()
            .color(color)
            .displayName(emptyText)
            .prefix(emptyText)
            .suffix(emptyText)
            .collisionRule(EnumWrappers.TeamCollisionRule.NEVER)
            .nametagVisibility(EnumWrappers.TeamVisibility.NEVER)
            .build()
        )
      );

      teamPacket.getSpecificModifier(Collection.class).write(0, entityId);

      protocolManager.sendServerPacket(player, teamPacket);
      teamKnownStatusByFormattingOrdinal[color.ordinal()] = true;

      return true;
    } catch (Throwable e) {
      logger.log(Level.SEVERE, "An error occurred while trying to create a team to be used with the cube-renderer", e);
      return false;
    }
  }
}
