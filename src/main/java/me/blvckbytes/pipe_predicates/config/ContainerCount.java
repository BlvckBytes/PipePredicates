package me.blvckbytes.pipe_predicates.config;

import at.blvckbytes.component_markup.markup.interpreter.DirectFieldAccess;
import org.bukkit.Material;

import java.util.Set;

public record ContainerCount(Material type, int count) implements DirectFieldAccess {

  @Override
  public Object accessField(String rawIdentifier) {
    return switch (rawIdentifier) {
      case "type" -> type.name();
      case "translation_key" -> type.getBlockTranslationKey();
      case "count" -> count;
      default -> DirectFieldAccess.UNKNOWN_FIELD_SENTINEL;
    };
  }

  @Override
  public Set<String> getAvailableFields() {
    return Set.of("type", "count", "translation_key");
  }
}
