package me.blvckbytes.craft_book_pipe_predicates.config.display_common;

import at.blvckbytes.component_markup.constructor.SlotType;
import at.blvckbytes.component_markup.expression.interpreter.InterpretationEnvironment;
import com.cryptomorin.xseries.XMaterial;
import com.destroystokyo.paper.profile.ProfileProperty;
import me.blvckbytes.bbconfigmapper.sections.AConfigSection;
import me.blvckbytes.craft_book_pipe_predicates.config.CMValue;
import me.blvckbytes.craft_book_pipe_predicates.config.ExpressionValue;
import me.blvckbytes.gpeee.interpreter.EvaluationEnvironmentBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class ItemStackSection extends AConfigSection {

  private @Nullable CMValue type;
  private @Nullable CMValue name;
  private @Nullable CMValue lore;
  private @Nullable ExpressionValue amount;
  private @Nullable CMValue textures;

  public ItemStackSection(EvaluationEnvironmentBuilder baseEnvironment) {
    super(baseEnvironment);
  }

  public void patch(ItemStack item, InterpretationEnvironment environment) {
    var meta = item.getItemMeta();

    if (amount != null) {
      var amountValue = amount.interpret(environment);

      if (amountValue != null)
        item.setAmount((int) environment.getValueInterpreter().asLong(amountValue));
    }

    if (name != null)
      meta.displayName(name.interpret(SlotType.ITEM_NAME, environment).get(0));

    if (lore != null) {
      var finalLore = meta.lore();
      var additionalLore = lore.interpret(SlotType.ITEM_LORE, environment);

      if (finalLore == null)
        finalLore = additionalLore;
      else
        finalLore.addAll(additionalLore);

      meta.lore(finalLore);
    }

    if (textures != null) {
      var texturesValue = textures.asPlainString(environment);

      if (!texturesValue.isBlank() && meta instanceof SkullMeta skullMeta) {
        var profile = Bukkit.createProfile(UUID.randomUUID(), null);
        profile.setProperty(new ProfileProperty("textures", texturesValue));
        skullMeta.setPlayerProfile(profile);
      }
    }

    item.setItemMeta(meta);
  }

  public ItemStack build(InterpretationEnvironment environment) {
    var result = new ItemStack(getMaterial(environment));

    patch(result, environment);

    return result;
  }

  private Material getMaterial(InterpretationEnvironment environment) {
    if (type != null) {
      var typeName = type.asPlainString(environment);

      if (typeName.isBlank())
        return Material.BARRIER;

      var xMaterial = XMaterial.matchXMaterial(typeName);

      if (xMaterial.isPresent())
        return xMaterial.get().get();

      type.log("Could not locate an XMeterial called \"" + typeName + "\"", null);
    }

    return Material.BARRIER;
  }
}
