package dev.xkmc.l2weaponry.content.item.legendary;

import dev.xkmc.l2library.init.events.attack.AttackCache;
import dev.xkmc.l2library.init.events.attack.CreateSourceEvent;
import dev.xkmc.l2library.init.events.attack.DamageModifier;
import dev.xkmc.l2library.init.events.damage.DefaultDamageState;
import dev.xkmc.l2library.init.materials.generic.ExtraToolConfig;
import dev.xkmc.l2weaponry.content.item.types.ThrowingAxeItem;
import dev.xkmc.l2weaponry.init.data.LangData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class BlackAxe extends ThrowingAxeItem implements LegendaryWeapon {

	public BlackAxe(Tier tier, int damage, float speed, Properties prop, ExtraToolConfig config) {
		super(tier, damage, speed, prop, config);
	}

	@Override
	public void modifySource(LivingEntity attacker, CreateSourceEvent event, ItemStack item, @Nullable Entity target) {
		event.enable(DefaultDamageState.BYPASS_ARMOR);
	}

	@Override
	public void onHurt(AttackCache event, LivingEntity le, ItemStack stack) {
		if (event.getCriticalHitEvent() != null && event.getStrength() < 0.9f) return;
		event.addHurtModifier(DamageModifier.addPost((float) event.getAttackTarget().getAttributeValue(Attributes.ARMOR)));
	}

	@Override
	public void appendHoverText(ItemStack pStack, @Nullable Level pLevel, List<Component> list, TooltipFlag pIsAdvanced) {
		list.add(LangData.BLACK_AXE.get());
	}

}
