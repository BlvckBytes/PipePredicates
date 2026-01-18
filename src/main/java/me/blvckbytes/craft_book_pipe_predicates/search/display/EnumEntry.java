package me.blvckbytes.craft_book_pipe_predicates.search.display;

import at.blvckbytes.component_markup.markup.interpreter.DirectFieldAccess;

import java.util.Set;

public record EnumEntry(String name, boolean enabled) implements DirectFieldAccess {

  public EnumEntry(Enum<?> constant, boolean enabled) {
    this(constant.name(), enabled);
  }

  @Override
  public Object accessField(String rawIdentifier) {
    return switch (rawIdentifier) {
      case "name" -> name;
      case "enabled" -> enabled;
      default -> DirectFieldAccess.UNKNOWN_FIELD_SENTINEL;
    };
  }

  @Override
  public Set<String> getAvailableFields() {
    return Set.of("name", "enabled");
  }
}
