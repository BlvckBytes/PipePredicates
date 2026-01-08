package me.blvckbytes.craft_book_pipe_predicates.config;

import at.blvckbytes.component_markup.constructor.*;
import at.blvckbytes.component_markup.util.TriState;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentBuilder;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class AdventureComponentConstructor implements ComponentConstructor<ComponentBuilder<?, ?>, Component> {

  public static final AdventureComponentConstructor INSTANCE = new AdventureComponentConstructor();

  private AdventureComponentConstructor() {}

  @Override
  public boolean doesSupport(ConstructorFeature feature) {
    return true;
  }

  @Override
  public SlotContext getSlotContext(SlotType slot) {
    return SlotContext.getForSlot(slot);
  }

  @Override
  public ComponentBuilder<?, ?> createTextComponent(String text) {
    return Component.text().content(text);
  }

  @Override
  public ComponentBuilder<?, ?> createKeyComponent(String key) {
    return Component.keybind().keybind(key);
  }

  @Override
  public ComponentBuilder<?, ?> createTranslateComponent(String key, List<Component> with, @Nullable String fallback) {
    return Component.translatable().key(key).fallback(fallback).arguments(with);
  }

  @Override
  public void setClickChangePageAction(ComponentBuilder<?, ?> component, String value) {
    try {
      component.clickEvent(ClickEvent.changePage(Integer.parseInt(value)));
    } catch (NumberFormatException e) {
      ConstructorWarning.emit(ConstructorWarning.MALFORMED_PAGE_VALUE);
    }
  }

  @Override
  public void setClickCopyToClipboardAction(ComponentBuilder<?, ?> component, String value) {
    component.clickEvent(ClickEvent.copyToClipboard(value));
  }

  @Override
  public void setClickOpenFileAction(ComponentBuilder<?, ?> component, String value) {
    component.clickEvent(ClickEvent.openFile(value));
  }

  @Override
  public void setClickOpenUrlAction(ComponentBuilder<?, ?> component, String value) {
    component.clickEvent(ClickEvent.openUrl(value));
  }

  @Override
  public void setClickRunCommandAction(ComponentBuilder<?, ?> component, String value) {
    component.clickEvent(ClickEvent.runCommand(value));
  }

  @Override
  public void setClickSuggestCommandAction(ComponentBuilder<?, ?> component, String value) {
    component.clickEvent(ClickEvent.suggestCommand(value));
  }

  @Override
  public void setHoverItemAction(ComponentBuilder<?, ?> component, String material, @Nullable Integer count, @Nullable Component name, @Nullable List<Component> lore, boolean hideProperties) {
    Material bukkitMaterial;

    try {
      var materialKey = NamespacedKey.minecraft(material);
      bukkitMaterial = Material.matchMaterial(materialKey.getKey(), false);

      if (bukkitMaterial == null)
        throw new IllegalStateException();
    } catch (Throwable e) {
      ConstructorWarning.emit(ConstructorWarning.MALFORMED_MATERIAL);
      return;
    }

    var item = new ItemStack(bukkitMaterial, count == null ? 1 : count);
    var meta = item.getItemMeta();

    if (name != null)
      meta.displayName(name);

    if (lore != null)
      meta.lore(lore);

    if (hideProperties)
      meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

    item.setItemMeta(meta);

    component.hoverEvent(item.asHoverEvent());
  }

  @Override
  public void setHoverTextAction(ComponentBuilder<?, ?> component, Component text) {
    component.hoverEvent(HoverEvent.showText(text));
  }

  @Override
  public void setHoverEntityAction(ComponentBuilder<?, ?> component, String type, UUID id, @Nullable Component name) {
    Key key;

    try {
      key = Key.key(type, ':');
    } catch (Throwable e) {
      ConstructorWarning.emit(ConstructorWarning.MALFORMED_ENTITY_TYPE);
      return;
    }

    component.hoverEvent(HoverEvent.showEntity(key, id, name));
  }

  @Override
  public void setInsertAction(ComponentBuilder<?, ?> component, String value) {
    component.insertion(value);
  }

  @Override
  public void setColor(ComponentBuilder<?, ?> component, long packedColor) {
    component.color(TextColor.color((int) packedColor));
  }

  @Override
  public void setShadowColor(ComponentBuilder<?, ?> component, long packedColor) {
    component.shadowColor(ShadowColor.shadowColor((int) packedColor));
  }

  @Override
  public void setFont(ComponentBuilder<?, ?> component, String font) {
    try {
      component.font(Key.key(font, ':'));
    } catch (Throwable e) {
      ConstructorWarning.emit(ConstructorWarning.MALFORMED_FONT_NAME);
    }
  }

  @Override
  public void setObfuscatedFormat(ComponentBuilder<?, ?> component, TriState value) {
    component.decoration(TextDecoration.OBFUSCATED, mapTriState(value));
  }

  @Override
  public void setBoldFormat(ComponentBuilder<?, ?> component, TriState value) {
    component.decoration(TextDecoration.BOLD, mapTriState(value));
  }

  @Override
  public void setStrikethroughFormat(ComponentBuilder<?, ?> component, TriState value) {
    component.decoration(TextDecoration.STRIKETHROUGH, mapTriState(value));
  }

  @Override
  public void setUnderlinedFormat(ComponentBuilder<?, ?> component, TriState value) {
    component.decoration(TextDecoration.UNDERLINED, mapTriState(value));
  }

  @Override
  public void setItalicFormat(ComponentBuilder<?, ?> component, TriState value) {
    component.decoration(TextDecoration.ITALIC, mapTriState(value));
  }

  @Override
  public Component finalizeComponent(ComponentBuilder<?, ?> component) {
    return component.asComponent();
  }

  @Override
  public void addChildren(ComponentBuilder<?, ?> component, List<Component> children) {
    component.append(children);
  }

  private TextDecoration.State mapTriState(TriState triState) {
    return switch (triState) {
      case TRUE -> TextDecoration.State.TRUE;
      case FALSE -> TextDecoration.State.FALSE;
      case NULL -> TextDecoration.State.NOT_SET;
    };
  }
}
