package dev.xkmc.l2weaponry.content.entity;

import com.google.common.collect.Lists;
import dev.xkmc.l2weaponry.content.item.base.IThrowableCallback;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;

public class BaseThrownWeaponEntity<T extends BaseThrownWeaponEntity<T>> extends AbstractArrow implements IEntityAdditionalSpawnData {

	private static final int LOWEST_HEIGHT = -32, MAX_DIST = 400, MAX_HOR_DIST = 100;

	private static final EntityDataAccessor<Byte> ID_LOYALTY = SynchedEntityData.defineId(BaseThrownWeaponEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Boolean> ID_FOIL = SynchedEntityData.defineId(BaseThrownWeaponEntity.class, EntityDataSerializers.BOOLEAN);
	private ItemStack item;
	public int remainingHit = 1;
	public int clientSideReturnTridentTickCount;
	public int slot;

	public float waterInertia = 0.6f;

	@Nullable
	private Vec3 origin;

	public BaseThrownWeaponEntity(EntityType<T> type, Level pLevel) {
		super(type, pLevel);
		item = new ItemStack(Items.TRIDENT);
	}

	public BaseThrownWeaponEntity(EntityType<T> type, Level pLevel, LivingEntity pShooter, ItemStack pStack, int slot) {
		super(type, pShooter, pLevel);
		this.item = pStack.copy();
		this.slot = slot;
		this.entityData.set(ID_LOYALTY, (byte) EnchantmentHelper.getLoyalty(pStack));
		this.entityData.set(ID_FOIL, pStack.hasFoil());
	}

	// ------ base weapon code

	public ItemStack getItem() {
		return item;
	}

	@Override
	public void setPierceLevel(byte lv) {
		super.setPierceLevel(lv);
		this.remainingHit = lv + 1;
	}

	private void tickEarlyReturn() {
		Entity entity = this.getOwner();
		int loyal = this.entityData.get(ID_LOYALTY);
		if (this.isNoGravity() && this.getDeltaMovement().length() < 1e-2) {
			remainingHit = 0;
			this.setNoGravity(false);
		}
		if (entity != null && loyal > 0 && remainingHit > 0) {
			if (origin == null) {
				origin = position();
			} else {
				if (position().y < level.getMinBuildHeight() + LOWEST_HEIGHT) {
					remainingHit = 0;
				} else {
					Vec3 diff = position().subtract(origin);
					if (diff.horizontalDistance() > MAX_HOR_DIST || diff.length() > MAX_DIST) {
						remainingHit = 0;
					}
				}
			}
		}
	}

	// ------ default trident code

	protected void defineSynchedData() {
		super.defineSynchedData();
		this.entityData.define(ID_LOYALTY, (byte) 0);
		this.entityData.define(ID_FOIL, false);
	}

	public void tick() {
		if (this.inGroundTime > 4) {
			this.remainingHit = 0;
		}
		tickEarlyReturn();
		Entity entity = this.getOwner();
		int loyal = this.entityData.get(ID_LOYALTY);
		if (loyal > 0 && (this.remainingHit == 0 || this.isNoPhysics()) && entity != null) {
			if (!this.isAcceptibleReturnOwner()) {
				if (!this.level.isClientSide && this.pickup == AbstractArrow.Pickup.ALLOWED) {
					this.spawnAtLocation(this.getPickupItem(), 0.1F);
				}
				this.discard();
			} else {
				this.setNoPhysics(true);
				Vec3 vec3 = entity.getEyePosition().subtract(this.position());
				this.setPosRaw(this.getX(), this.getY() + vec3.y * 0.015D * (double) loyal, this.getZ());
				if (this.level.isClientSide) {
					this.yOld = this.getY();
				}
				double d0 = 0.05D * (double) loyal;
				this.setDeltaMovement(this.getDeltaMovement().scale(0.95D).add(vec3.normalize().scale(d0)));
				if (this.clientSideReturnTridentTickCount == 0) {
					this.playSound(SoundEvents.TRIDENT_RETURN, 10.0F, 1.0F);
				}
				++this.clientSideReturnTridentTickCount;
			}
		}

		super.tick();
	}

	private boolean isAcceptibleReturnOwner() {
		Entity entity = this.getOwner();
		if (entity != null && entity.isAlive()) {
			return !(entity instanceof ServerPlayer) || !entity.isSpectator();
		} else {
			return false;
		}
	}

	protected ItemStack getPickupItem() {
		return this.item.copy();
	}

	public boolean isFoil() {
		return this.entityData.get(ID_FOIL);
	}

	@Nullable
	protected EntityHitResult findHitEntity(Vec3 pStartVec, Vec3 pEndVec) {
		return this.remainingHit == 0 ? null : super.findHitEntity(pStartVec, pEndVec);
	}

	protected void onHitEntity(EntityHitResult pResult) {
		Entity entity = pResult.getEntity();
		float damage = (float) getBaseDamage();
		if (entity instanceof LivingEntity livingentity) {
			damage += EnchantmentHelper.getDamageBonus(this.item, livingentity.getMobType());
		}
		Entity owner = this.getOwner();
		DamageSource damagesource = DamageSource.trident(this, owner == null ? this : owner);
		if (this.remainingHit > 0) {
			this.remainingHit--;
			if (this.getPierceLevel() > 0) {
				if (this.piercingIgnoreEntityIds == null) {
					this.piercingIgnoreEntityIds = new IntOpenHashSet(getPierceLevel() + 1);
				}
				if (this.piercedAndKilledEntities == null) {
					this.piercedAndKilledEntities = Lists.newArrayListWithCapacity(5);
				}

				this.piercingIgnoreEntityIds.add(entity.getId());
			}
		}
		SoundEvent soundevent = SoundEvents.TRIDENT_HIT;
		if (entity.hurt(damagesource, damage)) {
			if (entity.getType() == EntityType.ENDERMAN) {
				return;
			}
			if (entity instanceof LivingEntity le) {
				if (owner instanceof LivingEntity) {
					EnchantmentHelper.doPostHurtEffects(le, owner);
					EnchantmentHelper.doPostDamageEffects((LivingEntity) owner, le);
				}
				this.doPostHurtEffects(le);
				if (!entity.isAlive() && this.piercedAndKilledEntities != null) {
					this.piercedAndKilledEntities.add(entity);
				}
				if (item.getItem() instanceof IThrowableCallback cb) {
					cb.onHitEntity(this, item, le);
				}
			}
		}
		if (this.remainingHit == 0)
			this.setDeltaMovement(this.getDeltaMovement().multiply(-0.01D, -0.1D, -0.01D));
		float f1 = 1.0F;
		this.playSound(soundevent, f1, 1.0F);
	}

	@Override
	protected void onHitBlock(BlockHitResult pResult) {
		super.onHitBlock(pResult);
		if (item.getItem() instanceof IThrowableCallback cb) {
			cb.onHitBlock(this, item);
		}
	}

	protected boolean tryPickup(Player player) {
		if (pickup == Pickup.CREATIVE_ONLY) {
			return player.getAbilities().instabuild;
		}
		if (pickup == Pickup.ALLOWED || isNoPhysics() && ownedBy(player)) {
			return addToPlayer(player, getPickupItem());
		}
		return false;
	}

	protected boolean addToPlayer(Player player, ItemStack stack) {
		if (slot == 40) {
			if (player.getOffhandItem().isEmpty()) {
				player.setItemInHand(InteractionHand.OFF_HAND, stack.copy());
				stack.setCount(0);
				return true;
			}
		} else if (player.getInventory().getItem(slot).isEmpty()) {
			if (player.getInventory().add(slot, stack))
				return true;
		}
		return player.getInventory().add(stack);
	}

	protected SoundEvent getDefaultHitGroundSoundEvent() {
		return SoundEvents.TRIDENT_HIT_GROUND;
	}

	public void playerTouch(Player pEntity) {
		if (this.ownedBy(pEntity) || this.getOwner() == null) {
			super.playerTouch(pEntity);
		}
	}

	public void readAdditionalSaveData(CompoundTag pCompound) {
		super.readAdditionalSaveData(pCompound);
		if (pCompound.contains("Item", 10)) {
			this.item = ItemStack.of(pCompound.getCompound("Item"));
		}
		this.remainingHit = pCompound.getInt("RemainingHit");
		this.slot = pCompound.getInt("playerSlot");
		this.entityData.set(ID_LOYALTY, (byte) EnchantmentHelper.getLoyalty(this.item));
	}

	public void addAdditionalSaveData(CompoundTag pCompound) {
		super.addAdditionalSaveData(pCompound);
		pCompound.put("Item", this.item.save(new CompoundTag()));
		pCompound.putInt("RemainingHit", this.remainingHit);
		pCompound.putInt("playerSlot", slot);
	}

	public void tickDespawn() {
		int i = this.entityData.get(ID_LOYALTY);
		if (this.pickup != AbstractArrow.Pickup.ALLOWED || i <= 0) {
			super.tickDespawn();
		}
	}

	public boolean shouldRender(double pX, double pY, double pZ) {
		return true;
	}

	@Override
	public final Packet<?> getAddEntityPacket() {
		return NetworkHooks.getEntitySpawningPacket(this);
	}

	@Override
	public void writeSpawnData(FriendlyByteBuf buffer) {
		buffer.writeItem(item);
	}

	@Override
	public void readSpawnData(FriendlyByteBuf buffer) {
		item = buffer.readItem();
	}

	protected float getWaterInertia() {
		return waterInertia;
	}

}