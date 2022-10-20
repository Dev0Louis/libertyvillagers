package com.gitsh01.libertyvillagers.mixin;

import com.gitsh01.libertyvillagers.tasks.HealGolemTask;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.optics.Wander;
import com.mojang.datafixers.util.Pair;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.MemoryModuleState;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.task.*;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.poi.PointOfInterestTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.gitsh01.libertyvillagers.LibertyVillagersMod.CONFIG;

@Mixin(VillagerTaskListProvider.class)
public abstract class VillagerTaskListProviderMixin {

    private static final int SECONDARY_WORK_TASK_PRIORITY = 5; // Mojang default: 5.
    private static final int THIRD_WORK_TASK_PRIORITY = 7;

    // 20 ticks  = 1 second.
    private static final int WANDER_AROUND_MIN_TIME = 20 * 30;
    private static final int WANDER_AROUND_MAX_TIME = 20 * 60;

    public VillagerTaskListProviderMixin() {
    }

    @Invoker("createBusyFollowTask")
    public static Pair<Integer, Task<LivingEntity>> invokeCreateBusyFollowTask() {
        throw new AssertionError();
    }

    @Inject(method = "createWorkTasks", at = @At("Head"), cancellable = true)
    private static void replaceCreateWorkTasks(VillagerProfession profession, float speed, CallbackInfoReturnable cir) {
        VillagerWorkTask villagerWorkTask = new VillagerWorkTask(); // Plays working sounds at the job site.
        Task secondaryWorkTask = null;
        Task thirdWorkTask = null;
        switch (profession.toString()) {
            case "armorer":
                if (CONFIG.villagersProfessionConfig.armorerHealsGolems) {
                    secondaryWorkTask = new HealGolemTask();
                }
                break;
            case "farmer":
                villagerWorkTask = new FarmerWorkTask(); // Compost.
                secondaryWorkTask = new FarmerVillagerTask(); // Harvest / plant seeds.
                thirdWorkTask = new BoneMealTask(); // Apply bonemeal to crops.
                break;
        }

        ArrayList<Pair<Task<? super VillagerEntity>, Integer>> randomTasks = new ArrayList(
                ImmutableList.of(Pair.of(villagerWorkTask, 7),
                        Pair.of(new GoToIfNearbyTask(MemoryModuleType.JOB_SITE, 0.4f, 4), 2),
                        Pair.of(new GoToNearbyPositionTask(MemoryModuleType.JOB_SITE, 0.4f, 1, 10), 5),
                        Pair.of(new GoToSecondaryPositionTask(MemoryModuleType.SECONDARY_JOB_SITE, speed, 1, 6,
                                MemoryModuleType.JOB_SITE), 5)));

        if (secondaryWorkTask != null) {
            randomTasks.add(Pair.of(secondaryWorkTask, SECONDARY_WORK_TASK_PRIORITY));
        }

        if (thirdWorkTask != null) {
            randomTasks.add(Pair.of(thirdWorkTask, THIRD_WORK_TASK_PRIORITY));
        }

        RandomTask randomTask = new RandomTask(ImmutableList.copyOf(randomTasks));
        List<Pair<Integer, ? extends Task<? super VillagerEntity>>> tasks =
                List.of(VillagerTaskListProviderMixin.invokeCreateBusyFollowTask(), Pair.of(7, randomTask),
                        Pair.of(10, new HoldTradeOffersTask(400, 1600)),
                        Pair.of(10, new FindInteractionTargetTask(EntityType.PLAYER, 4)), Pair.of(2,
                                new VillagerWalkTowardsTask(MemoryModuleType.JOB_SITE, speed, 9,
                                        CONFIG.villagersGeneralConfig.pathfindingMaxRange, 1200)),
                        Pair.of(3, new GiveGiftsToHeroTask(100)), Pair.of(99, new ScheduleActivityTask()));
        cir.setReturnValue(ImmutableList.copyOf(tasks));
        cir.cancel();
    }
}
