package teamhollow.direbats.entity.mob;

import net.minecraft.block.BlockState;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.entity.ai.attributes.AttributeModifierMap;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.controller.FlyingMovementController;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.HurtByTargetGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.NearestAttackableTargetGoal;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.pathfinding.FlyingPathNavigator;
import net.minecraft.pathfinding.PathNavigator;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.IWorld;
import net.minecraft.world.IWorldReader;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.time.temporal.ChronoField;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

public class DirebatEntity extends CreatureEntity {
    private static final DataParameter<Byte> BAT_FLAGS = EntityDataManager.createKey(DirebatEntity.class, DataSerializers.BYTE);
    private static final EntityPredicate CLOSE_PLAYER_PREDICATE = (new EntityPredicate()).setDistance(4.0D).allowFriendlyFire();

    private static final Predicate<ItemEntity> PICKABLE_DROP_FILTER = new Predicate<ItemEntity>() {
        public boolean test(@Nullable ItemEntity p_test_1_) {
            return p_test_1_ != null && !p_test_1_.cannotPickup();
        }
    };

    public DirebatEntity(EntityType<? extends DirebatEntity> entityType, World world) {
        super(entityType, world);
        this.moveController = new FlyingMovementController(this, 20, true);
        this.setPathPriority(PathNodeType.DANGER_FIRE, -1.0F);
        this.setPathPriority(PathNodeType.WATER, -1.0F);
        this.setPathPriority(PathNodeType.WATER_BORDER, 16.0F);
        this.setPathPriority(PathNodeType.STICKY_HONEY, -1.0F);
        this.setPathPriority(PathNodeType.COCOA, -1.0F);
        this.setPathPriority(PathNodeType.FENCE, -1.0F);

        this.setCanPickUpLoot(true);
        this.experienceValue = 5;
    }

    @Override
    protected void registerGoals() {

        this.goalSelector.addGoal(1, new AttackGoal(this));
        this.goalSelector.addGoal(2, new DirebatEntity.PickupItemGoal());
        this.goalSelector.addGoal(3, new WanderGoal());
        this.targetSelector.addGoal(0, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(1, new TargetGoal<>(this, PlayerEntity.class));
    }

    @Override
    protected void registerData() {
        super.registerData();
        this.dataManager.register(BAT_FLAGS, (byte) 0);
    }

    public static AttributeModifierMap.MutableAttribute getAttributeMap() {
        return MobEntity.func_233666_p_().createMutableAttribute(Attributes.MAX_HEALTH, 8.0D)
                .createMutableAttribute(Attributes.FLYING_SPEED, 0.22D) // previously (double) 0.22F
                .createMutableAttribute(Attributes.MOVEMENT_SPEED, 0.22D) // previously (double) 0.22F
                .createMutableAttribute(Attributes.ATTACK_DAMAGE, 2.0D);
    }

    @Override
    protected PathNavigator createNavigator(World worldIn) {
        FlyingPathNavigator flyingpathnavigator = new FlyingPathNavigator(this, worldIn) {
            public boolean canEntityStandOnPos(BlockPos pos) {
//                IDEA: Alternatives for replacing the deprecated function. Ignore if you don't know what this is.
//                System.out.println("this.world.getBlockState(pos).isAir(worldIn, pos.down()) = " + this.world.getBlockState(pos).isAir(worldIn, pos.down()));
//                System.out.println("this.world.getBlockState(pos.down()).isAir(worldIn, pos.down()) = " + this.world.getBlockState(pos.down()).isAir(worldIn, pos.down()));
//                System.out.println("this.world.getBlockState(pos.down()).isAir() = " + this.world.getBlockState(pos.down()).isAir());
                return !this.world.getBlockState(pos.down()).isAir();
            }
        };
        flyingpathnavigator.setCanOpenDoors(false);
        flyingpathnavigator.setCanSwim(false);
        flyingpathnavigator.setCanEnterDoors(true);
        return flyingpathnavigator;
    }

    @Override
    public Vector3d func_233633_a_(Vector3d p_233633_1_, float p_233633_2_) {
        this.moveRelative(this.getRelevantMoveFactor(p_233633_2_), p_233633_1_);
        //this.setMotion(this.handleOnClimbable(this.getMotion()));
        this.move(MoverType.SELF, this.getMotion());
        Vector3d motionVector = this.getMotion();
        if ((this.collidedHorizontally || this.isJumping) && this.isOnLadder()) {
            motionVector = new Vector3d(motionVector.x, 0.2D, motionVector.z);
        }

        return motionVector;
    }

    private float getRelevantMoveFactor(float slipperiness) {
        return this.getAIMoveSpeed() * (0.21600002F / (slipperiness * slipperiness * slipperiness));
    }

    @Override
    public float getBlockPathWeight(BlockPos pos, IWorldReader worldIn) {
        return worldIn.getBlockState(pos).isAir() ? 10.0F * (1F / worldIn.getBrightness(pos)) : 0.0F;
    }

    @Override
    protected float getSoundVolume() {
        return 0.15F;
    }

    @Override
    protected float getSoundPitch() {
        return super.getSoundPitch() * 0.95F;
    }

    @Override
    public SoundEvent getAmbientSound() {
        return this.isHanging() && this.rand.nextInt(4) != 0 ? null : SoundEvents.ENTITY_BAT_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_BAT_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_BAT_DEATH;
    }

    @Override
    public boolean canBePushed() {
        return true;
    }

    @Override
    public boolean canBeLeashedTo(PlayerEntity player) {
        return false;
    }

    @Override
    protected void collideWithEntity(Entity entityIn) {
    }

    @Override
    protected void collideWithNearbyEntities() {
    }

    @Override
    public boolean onLivingFall(float distance, float damageMultiplier) {
        return false;
    }

    @Override
    protected void updateFallState(double y, boolean onGroundIn, BlockState state, BlockPos pos) {
    }

    public boolean isHanging() {
        return ((Byte) this.dataManager.get(BAT_FLAGS) & 1) != 0;
    }

    public void setHanging(boolean roosting) {
        byte batFlag = (Byte) this.dataManager.get(BAT_FLAGS);
        if (roosting) {
            this.dataManager.set(BAT_FLAGS, (byte) (batFlag | 1));
        } else {
            this.dataManager.set(BAT_FLAGS, (byte) (batFlag & -2));
        }

    }

    @Override
    public void tick() {
        super.tick();
        if (this.isHanging()) {
            this.setMotion(Vector3d.ZERO);
            this.setPosition(this.getPosX(), (double) MathHelper.floor(this.getPosY()) + 1.0D - (double) this.getHeight(), this.getPosZ());
        } else {
            this.setMotion(this.getMotion().mul(1.0D, 0.6D, 1.0D));
        }

    }

    @Override
    public void updateAITasks() {
        super.updateAITasks();
        BlockPos blockPos = this.getPosition();
        BlockPos blockPosUp = blockPos.up();
        if (this.isHanging()) {
            boolean isSilent = this.isSilent();

            if (this.getAttackTarget() != null) {
                this.setHanging(false);
            }

            if (this.world.getBlockState(blockPosUp).isNormalCube(this.world, blockPos)) {
                if (this.rand.nextInt(200) == 0) {
                    this.rotationYawHead = (float) this.rand.nextInt(360);
                }

                if (this.world.getClosestPlayer(CLOSE_PLAYER_PREDICATE, this) != null) {
                    this.setHanging(false);
                    if (!isSilent) {
                        this.world.playEvent((PlayerEntity) null, 1025, blockPos, 0);
                    }
                }
            } else {
                this.setHanging(false);
                if (!isSilent) {
                    this.world.playEvent((PlayerEntity) null, 1025, blockPos, 0);
                }
            }

            this.setMotion(0, 0, 0);
            this.getNavigator().clearPath();
        } else {
            if (this.rand.nextInt(100) == 0 && this.world.getBlockState(blockPosUp).isNormalCube(this.world, blockPosUp)) {
                this.setHanging(true);
            }
        }
    }

    @Override
    public void setAttackTarget(LivingEntity entitylivingbaseIn) {
        super.setAttackTarget(entitylivingbaseIn);

        this.dropInventory();
    }

    @Override
    public boolean attackEntityAsMob(Entity entityIn) {
        if (super.attackEntityAsMob(entityIn)) {
            if (entityIn instanceof LivingEntity) {
                int seconds = 5;

                if (this.world.getDifficulty() == Difficulty.HARD) {
                    seconds = 10;
                }

                ((LivingEntity) entityIn).addPotionEffect(new EffectInstance(Effects.BLINDNESS, seconds * 20, 0));
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) {
            return false;
        } else {
            if (!this.world.isRemote && this.isHanging()) {
                this.setHanging(false);
            }

            this.dropInventory();

            return super.attackEntityFrom(source, amount);
        }
    }

    @Override
    protected void updateEquipmentIfNeeded(ItemEntity itemEntity) {
        ItemStack newItem = itemEntity.getItem();

        if (PICKABLE_DROP_FILTER.test(itemEntity)) {
            ItemStack currentItem = this.getItemStackFromSlot(EquipmentSlotType.MAINHAND);

            if (!currentItem.isEmpty()) {
                this.entityDropItem(currentItem);
            }

            this.func_233657_b_(EquipmentSlotType.MAINHAND, newItem); // insert new item into main hand
            this.triggerItemPickupTrigger(itemEntity);
            this.onItemPickup(itemEntity, newItem.getCount());
            itemEntity.remove();
        }
    }

    @Override
    protected void dropInventory() {
        super.dropInventory();
        if (!this.getHeldItem(Hand.MAIN_HAND).isEmpty()) {
            this.entityDropItem(this.getHeldItemMainhand());
        }
    }

    @Override
    public void readAdditional(CompoundNBT compound) {
        super.readAdditional(compound);
        this.dataManager.set(BAT_FLAGS, compound.getByte("BatFlags"));
    }

    @Override
    public void writeAdditional(CompoundNBT compound) {
        super.writeAdditional(compound);
        compound.putByte("BatFlags", (Byte) this.dataManager.get(BAT_FLAGS));
    }

    public static boolean canSpawn(EntityType<DirebatEntity> type, IWorld world, SpawnReason spawnReason, BlockPos pos, Random random) {
        if (pos.getY() >= world.getSeaLevel() && world.getMoonFactor() < 1.0F) {
            return false;
        } else {
            int worldLight = world.getLight(pos);
            int maximumLight = 4;
            if (isTodayAroundHalloween()) {
                maximumLight = 7;
            } else if (random.nextBoolean()) {
                return false;
            }

            return worldLight > random.nextInt(maximumLight)
                    ? false : canSpawnOn(type, world, spawnReason, pos, random);
        }
    }

    private static boolean isTodayAroundHalloween() {
        LocalDate localDate = LocalDate.now();
        int day = localDate.get(ChronoField.DAY_OF_MONTH);
        int month = localDate.get(ChronoField.MONTH_OF_YEAR);
        return month == 10 && day >= 20 || month == 11 && day <= 3;
    }

    @Override
    protected float getStandingEyeHeight(Pose poseIn, EntitySize sizeIn) {
        return sizeIn.height / 2;
    }

    private boolean isWithinDistance(BlockPos pos, int distance) {
        return pos.withinDistance(this.getPosition(), (double) distance);
    }

    @Override
    protected boolean isDespawnPeaceful() {
        return true;
    }

    static class AttackGoal extends MeleeAttackGoal {
        public AttackGoal(DirebatEntity entity) {
            super(entity, 1.0D, true);
        }

        /**
         * Returns whether execution should begin. You can also read and cache any state necessary for execution in this
         * method as well.
         */
        public boolean shouldExecute() {
            return super.shouldExecute();
        }

        /**
         * Returns whether an in-progress EntityAIBase should continue executing
         */
        public boolean shouldContinueExecuting() {
            float attackerBrightness = this.attacker.getBrightness();
            if (attackerBrightness <= 0.5F && this.attacker.getRNG().nextInt(100) == 0) {
                this.attacker.setAttackTarget((LivingEntity) null);
                return false;
            } else {
                return super.shouldContinueExecuting();
            }
        }
    }

    class WanderGoal extends Goal {
        WanderGoal() {
            this.setMutexFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        /**
         * Returns whether execution should begin. You can also read and cache any state necessary for execution in this
         * method as well.
         */
        public boolean shouldExecute() {
            return DirebatEntity.this.navigator.noPath() && !DirebatEntity.this.isHanging() && DirebatEntity.this.rand.nextInt(5) == 0;
        }

        /**
         * Returns whether an in-progress EntityAIBase should continue executing
         */
        public boolean shouldContinueExecuting() {
            return DirebatEntity.this.navigator.hasPath();
        }

        /**
         * Execute a one shot task or start executing a continuous task
         */
        public void startExecuting() {
            Vector3d randomLocation = this.getRandomLocation();
            if (randomLocation != null) {
                DirebatEntity.this.navigator.setPath(DirebatEntity.this.navigator.getPathToPos(new BlockPos(randomLocation), 1), 1.0D);
            }

        }

        // TODO: PLEASE RENAME THESE VARIABLE NAMES TO MAKE SENSE!!!
        @Nullable
        private Vector3d getRandomLocation() {
            Vector3d vector3d;
            if (DirebatEntity.this.detachHome() && !DirebatEntity.this.isWithinDistance(DirebatEntity.this.getHomePosition(), 22)) {
                Vector3d vector3d1 = Vector3d.copyCentered(DirebatEntity.this.getHomePosition());
                vector3d = vector3d1.subtract(DirebatEntity.this.getPositionVec()).normalize();
            } else {
                vector3d = DirebatEntity.this.getLook(0.0F);
            }

            Vector3d vector3d2 = RandomPositionGenerator.findAirTarget(DirebatEntity.this, 8, 7, vector3d, ((float) Math.PI / 2F), 2, 1);
            return vector3d2 != null ? vector3d2 : RandomPositionGenerator.findGroundTarget(DirebatEntity.this, 8, 4, -2, vector3d, (double) ((float) Math.PI / 2F));
        }
    }

    class PickupItemGoal extends Goal {
        public PickupItemGoal() {
            this.setMutexFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean shouldExecute() {
            if (!DirebatEntity.this.getHeldItem(Hand.MAIN_HAND).isEmpty()) {
                return false;
            } else if (DirebatEntity.this.getAttackTarget() == null && DirebatEntity.this.getAttackingEntity() == null) {
                List<ItemEntity> list = DirebatEntity.this.world.getEntitiesWithinAABB(ItemEntity.class, DirebatEntity.this.getBoundingBox().expand(8.0D, 8.0D, 8.0D), DirebatEntity.PICKABLE_DROP_FILTER);
                return !list.isEmpty() && DirebatEntity.this.getHeldItem(Hand.MAIN_HAND).isEmpty();
            } else {
                return false;
            }
        }

        public boolean shouldContinueExecuting() {
            return DirebatEntity.this.navigator.hasPath();
        }

        public void tick() {
            List<ItemEntity> list = DirebatEntity.this.world.getEntitiesWithinAABB(ItemEntity.class, DirebatEntity.this.getBoundingBox().expand(8.0D, 8.0D, 8.0D), DirebatEntity.PICKABLE_DROP_FILTER);
            ItemStack itemInHand = DirebatEntity.this.getHeldItem(Hand.MAIN_HAND);
            if (itemInHand.isEmpty() && !list.isEmpty()) {
                DirebatEntity.this.getNavigator().tryMoveToEntityLiving((Entity) list.get(0), 1.2000000476837158D);
            }
        }

        @Override
        public void startExecuting() {
            List<ItemEntity> list = DirebatEntity.this.world.getEntitiesWithinAABB(ItemEntity.class, DirebatEntity.this.getBoundingBox().expand(8.0D, 8.0D, 8.0D), DirebatEntity.PICKABLE_DROP_FILTER);
            if (!list.isEmpty()) {
                DirebatEntity.this.getNavigator().tryMoveToEntityLiving((Entity) list.get(0), 1.2000000476837158D);
            }

            DirebatEntity.this.setHanging(false);
        }
    }

    static class TargetGoal<T extends LivingEntity> extends NearestAttackableTargetGoal<T> {
        private final DirebatEntity entity;

        public TargetGoal(DirebatEntity entity, Class<T> classTarget) {
            super(entity, classTarget, true);
            this.entity = entity;
        }

        /**
         * Returns whether execution should begin. You can also read and cache any state necessary for execution in this
         * method as well.
         */
        public boolean shouldExecute() {
            float goalOwnerBrightness = this.goalOwner.getBrightness();
            return goalOwnerBrightness <= 0.5F
                    ? false : super.shouldExecute();
        }
    }
}
