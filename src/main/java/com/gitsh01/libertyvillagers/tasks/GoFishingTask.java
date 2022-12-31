package com.gitsh01.libertyvillagers.tasks;

import com.gitsh01.libertyvillagers.mixin.FishingBobberEntityAccessorMixin;
import com.google.common.collect.ImmutableMap;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ai.brain.BlockPosLookTarget;
import net.minecraft.entity.ai.brain.MemoryModuleState;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.task.MultiTickTask;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.gitsh01.libertyvillagers.LibertyVillagersMod.CONFIG;

public class GoFishingTask extends MultiTickTask<VillagerEntity> {

    private static final int MAX_RUN_TIME = 40 * 20;
    private static final int TURN_TIME = 3 * 20;

    // Give the villager time to look at the water, so they aren't throwing the bobber behind their heads.
    private long bobberCountdown;

    private boolean hasThrownBobber = false;

    @Nullable
    private BlockPos targetPosition;

    @Nullable
    private FishingBobberEntity bobber = null;

    public GoFishingTask() {
        super(ImmutableMap.of(MemoryModuleType.LOOK_TARGET, MemoryModuleState.VALUE_ABSENT,
                MemoryModuleType.WALK_TARGET, MemoryModuleState.VALUE_ABSENT, MemoryModuleType.JOB_SITE,
                MemoryModuleState.VALUE_PRESENT), MAX_RUN_TIME);
    }

    protected boolean shouldRun(ServerWorld serverWorld, VillagerEntity villagerEntity) {
        List<BlockPos> targetPositions = new ArrayList<>();

        // Look for water nearby.
        BlockPos.Mutable mutable = villagerEntity.getBlockPos().mutableCopy();

        for (int i = -1 * CONFIG.villagersProfessionConfig.fishermanFishingWaterRange;
             i <= CONFIG.villagersProfessionConfig.fishermanFishingWaterRange; ++i) {
            for (int j = -3; j <= 3; ++j) {
                for (int k = -CONFIG.villagersProfessionConfig.fishermanFishingWaterRange;
                     k <= CONFIG.villagersProfessionConfig.fishermanFishingWaterRange; ++k) {
                    if (Math.abs(i) < 1 || Math.abs(k) < 1) {
                        continue;
                    }
                    mutable.set(villagerEntity.getX() + (double) i, villagerEntity.getY() + (double) j,
                            villagerEntity.getZ() + (double) k);
                    if (serverWorld.getBlockState(mutable.toImmutable()).isOf(Blocks.WATER) &&
                            serverWorld.getBlockState(mutable.up()).isOf(Blocks.AIR)) {
                        targetPositions.add(mutable.toImmutable());
                    }
                }
            }
        }

        if (targetPositions.size() < 1) {
            return false;
        }

        targetPosition = targetPositions.get(serverWorld.getRandom().nextInt(targetPositions.size()));
        return true;
    }

    protected void run(ServerWorld serverWorld, VillagerEntity villagerEntity, long time) {
        villagerEntity.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.FISHING_ROD));
        villagerEntity.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.0f);
        villagerEntity.getBrain().remember(MemoryModuleType.LOOK_TARGET, new BlockPosLookTarget(targetPosition.up()));
        bobberCountdown = TURN_TIME + time;
    }

    protected boolean shouldKeepRunning(ServerWorld world, VillagerEntity entity, long time) {
        // Villager dropped the fishing rod for some reason.
        if (!entity.getMainHandStack().isOf(Items.FISHING_ROD)) {
            return false;
        }

        if (!this.hasThrownBobber) {
            return true;
        }

        if (bobber == null || bobber.isRemoved() || bobber.isOnGround()) {
            return false;
        }

        // Still initilizing...
        if (bobber.getDataTracker() == null) {
            return true;
        }

        boolean caughtFish = bobber.getDataTracker().get(FishingBobberEntityAccessorMixin.getCaughtFish());
        return !caughtFish;
    }

    protected void keepRunning(ServerWorld serverWorld, VillagerEntity villagerEntity, long time) {
        if (!hasThrownBobber && time > bobberCountdown) {
                throwBobber(villagerEntity, serverWorld);
        }
    }

    protected void finishRunning(ServerWorld serverWorld, VillagerEntity villagerEntity, long l) {
        villagerEntity.getBrain().forget(MemoryModuleType.LOOK_TARGET);
        villagerEntity.getBrain().forget(MemoryModuleType.WALK_TARGET);
        if (bobber != null) {
            bobber.use(ItemStack.EMPTY);
            bobber = null;
        }
        // Remove fishing pole.
        villagerEntity.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        this.hasThrownBobber = false;
        this.bobberCountdown = 0;
    }

    void throwBobber(VillagerEntity thrower, ServerWorld serverWorld) {
        bobber = new FishingBobberEntity(EntityType.FISHING_BOBBER, serverWorld);
        bobber.setOwner(thrower);

        double d = targetPosition.getX() + 0.5 - thrower.getX();
        double e = targetPosition.getY() + 0.5 - thrower.getEyeY();
        double f = targetPosition.getZ() + 0.5 - thrower.getZ();
        double g = 0.1;

        double x = thrower.getX() + (d * 0.3);
        double y = thrower.getEyeY();
        double z = thrower.getZ() + (f * 0.3);

        bobber.refreshPositionAndAngles(x, y, z, thrower.getYaw(), thrower.getPitch());

        Vec3d vec3d = new Vec3d(d * g, e * g, f * g);

        bobber.setVelocity(vec3d);
        bobber.setYaw((float) (MathHelper.atan2(vec3d.x, vec3d.z) * 57.2957763671875));
        bobber.setPitch((float) (MathHelper.atan2(vec3d.y, vec3d.horizontalLength()) * 57.2957763671875));
        bobber.prevYaw = bobber.getYaw();
        bobber.prevPitch = bobber.getPitch();

        serverWorld.spawnEntity(bobber);
        serverWorld.playSound(null, thrower.getX(), thrower.getY(), thrower.getZ(),
                SoundEvents.ENTITY_FISHING_BOBBER_THROW, SoundCategory.NEUTRAL, 0.5f,
                0.4f / (serverWorld.getRandom().nextFloat() * 0.4f + 0.8f));
        hasThrownBobber = true;
    }
}