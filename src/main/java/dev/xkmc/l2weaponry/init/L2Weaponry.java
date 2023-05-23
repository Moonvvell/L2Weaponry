package dev.xkmc.l2weaponry.init;

import com.tterrag.registrate.providers.ProviderType;
import dev.xkmc.l2library.base.L2Registrate;
import dev.xkmc.l2library.base.tabs.contents.AttributeEntry;
import dev.xkmc.l2library.init.events.attack.AttackEventHandler;
import dev.xkmc.l2weaponry.compat.GolemCompat;
import dev.xkmc.l2weaponry.content.capability.LWPlayerData;
import dev.xkmc.l2weaponry.events.LWAttackEventListener;
import dev.xkmc.l2weaponry.init.data.*;
import dev.xkmc.l2weaponry.init.registrate.LWEntities;
import dev.xkmc.l2weaponry.init.registrate.LWItems;
import dev.xkmc.l2weaponry.network.NetworkManager;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(L2Weaponry.MODID)
@Mod.EventBusSubscriber(modid = L2Weaponry.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class L2Weaponry {

	public static final String MODID = "l2weaponry";
	public static final Logger LOGGER = LogManager.getLogger();
	public static final L2Registrate REGISTRATE = new L2Registrate(MODID);

	private static void registerRegistrates(IEventBus bus) {
		LWItems.register();
		LWEntities.register();
		LWDamageTypeGen.register();
		NetworkManager.register();
		LWConfig.init();
		LWPlayerData.register();
		if (ModList.get().isLoaded("modulargolems")) GolemCompat.register(bus);
		REGISTRATE.addDataGenerator(ProviderType.LANG, LangData::addTranslations);
		REGISTRATE.addDataGenerator(ProviderType.RECIPE, RecipeGen::genRecipe);
		REGISTRATE.addDataGenerator(ProviderType.BLOCK_TAGS, TagGen::onBlockTagGen);
		REGISTRATE.addDataGenerator(ProviderType.ITEM_TAGS, TagGen::onItemTagGen);
		REGISTRATE.addDataGenerator(ProviderType.ENTITY_TAGS, TagGen::onEntityTagGen);
	}

	public L2Weaponry() {
		FMLJavaModLoadingContext ctx = FMLJavaModLoadingContext.get();
		IEventBus bus = ctx.getModEventBus();
		registerRegistrates(bus);
	}

	@SubscribeEvent
	public static void setup(final FMLCommonSetupEvent event) {
		event.enqueueWork(() -> {
			AttackEventHandler.register(4000, new LWAttackEventListener());
			AttributeEntry.add(LWItems.SHIELD_DEFENSE, false, 14000);
		});
	}

	@SubscribeEvent
	public static void modifyAttributes(EntityAttributeModificationEvent event) {
		event.add(EntityType.PLAYER, LWItems.SHIELD_DEFENSE.get());
		event.add(EntityType.PLAYER, LWItems.REFLECT_TIME.get());
	}

	@SubscribeEvent
	public static void gatherData(GatherDataEvent event) {
		boolean gen = event.includeServer();
		var output = event.getGenerator().getPackOutput();
		var lookup = event.getLookupProvider();
		var helper = event.getExistingFileHelper();
		new LWDamageTypeGen(output, lookup, helper).generate(gen, event.getGenerator());
	}

}
