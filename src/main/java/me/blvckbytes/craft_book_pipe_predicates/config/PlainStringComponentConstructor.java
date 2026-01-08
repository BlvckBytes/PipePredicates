package me.blvckbytes.craft_book_pipe_predicates.config;

import at.blvckbytes.component_markup.constructor.ComponentConstructor;
import at.blvckbytes.component_markup.constructor.ConstructorFeature;
import at.blvckbytes.component_markup.constructor.SlotContext;
import at.blvckbytes.component_markup.constructor.SlotType;
import at.blvckbytes.component_markup.util.TriState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class PlainStringComponentConstructor implements ComponentConstructor<StringBuilder, String> {

  public static final PlainStringComponentConstructor INSTANCE = new PlainStringComponentConstructor();

  @Override
  public boolean doesSupport(ConstructorFeature feature) {
    return true;
  }

  @Override
  public SlotContext getSlotContext(SlotType slot) {
    return SlotContext.getForSlot(slot);
  }

  @Override
  public StringBuilder createTextComponent(String text) {
    return new StringBuilder(text);
  }

  @Override
  public StringBuilder createKeyComponent(String key) {
    return new StringBuilder(key);
  }

  @Override
  public StringBuilder createTranslateComponent(String key, List<String> with, @Nullable String fallback) {
    return new StringBuilder(key);
  }

  @Override
  public void setClickChangePageAction(StringBuilder component, String value) {}

  @Override
  public void setClickCopyToClipboardAction(StringBuilder component, String value) {}

  @Override
  public void setClickOpenFileAction(StringBuilder component, String value) {}

  @Override
  public void setClickOpenUrlAction(StringBuilder component, String value) {}

  @Override
  public void setClickRunCommandAction(StringBuilder component, String value) {}

  @Override
  public void setClickSuggestCommandAction(StringBuilder component, String value) {}

  @Override
  public void setHoverItemAction(StringBuilder component, String material, @Nullable Integer count, @Nullable String name, @Nullable List<String> lore, boolean hideProperties) {}

  @Override
  public void setHoverTextAction(StringBuilder component, String text) {}

  @Override
  public void setHoverEntityAction(StringBuilder component, String type, UUID id, @Nullable String name) {}

  @Override
  public void setInsertAction(StringBuilder component, String value) {}

  @Override
  public void setColor(StringBuilder component, long packedColor) {}

  @Override
  public void setShadowColor(StringBuilder component, long packedColor) {}

  @Override
  public void setFont(StringBuilder component, String font) {}

  @Override
  public void setObfuscatedFormat(StringBuilder component, TriState value) {}

  @Override
  public void setBoldFormat(StringBuilder component, TriState value) {}

  @Override
  public void setStrikethroughFormat(StringBuilder component, TriState value) {}

  @Override
  public void setUnderlinedFormat(StringBuilder component, TriState value) {}

  @Override
  public void setItalicFormat(StringBuilder component, TriState value) {}

  @Override
  public String finalizeComponent(StringBuilder component) {
    return component.toString();
  }

  @Override
  public void addChildren(StringBuilder component, List<String> children) {
    children.forEach(component::append);
  }
}
