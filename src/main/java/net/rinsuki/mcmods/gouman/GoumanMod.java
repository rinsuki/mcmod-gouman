package net.rinsuki.mcmods.gouman;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.GuardianEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.mob.SilverfishEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.entity.mob.WitchEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.mob.ZombieVillagerEntity;
import net.minecraft.entity.mob.ZombifiedPiglinEntity;
import net.minecraft.entity.passive.ArmadilloEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class GoumanMod implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("gouman");
    private static boolean enabled = false;
    private static KeyBinding keyToggle;
    private static long lastArmadilloFeedTick = 0;
    private static final long ARMADILLO_RETRY_TICKS = 6000; // 5 minutes
    private static final Map<Integer, Long> armadilloTriedAt = new HashMap<>(); // entityId -> worldTime

    @Override
    public void onInitializeClient() {
        enabled = false;
        LOGGER.info("Gouman MOD is active.");
        keyToggle = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.gouman.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.gouman.main"
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> this.onTick(client));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> this.onJoin(client));
    }

    private void onJoin(MinecraftClient client) {
        enabled = false;
        client.player.sendMessage(Text.of("傲慢: 読み込まれました！ Gキーで有効/無効を切り替えます。"), false);
    }

    private void onTick(MinecraftClient client) {
        if (client.world == null) return;
        if (client.player == null) return;
        boolean enabledWasChanged = false;
        while (keyToggle.wasPressed()) {
            enabled = !enabled;
            enabledWasChanged = true;
        }
        if (enabledWasChanged) {
            client.player.sendMessage(Text.of(String.format("傲慢: %s", enabled ? "有効" : "無効")), false);
        }
        if (!enabled) return;
        autoAttack(client);
        feedArmadilloIfPossible(client);
        var haveEmptySlot = false;
        var mainStack = client.player.getInventory().getMainStacks();
        for (var i=0; i<mainStack.size(); i++) {
            var itemStack = mainStack.get(i);
            if (itemStack.getItem() == Items.AIR) {
                haveEmptySlot = true;
                break;
            }
        }
        if (haveEmptySlot) {
            seedUekae(client, Blocks.WHEAT, Items.WHEAT_SEEDS);
            seedUekae(client, Blocks.BEETROOTS, Items.BEETROOT_SEEDS);
            seedUekae(client, Blocks.CARROTS, Items.CARROT);
            seedUekae(client, Blocks.POTATOES, Items.POTATO);
        }
    }

    private static void autoAttack(MinecraftClient client) {
        if (client.player.getAttackCooldownProgress(0f) < 1.0f) return;
        if (client.player.isUsingItem()) return;

        var entities = client.player.getWorld().getEntitiesByClass(
            HostileEntity.class,
            client.player.getBoundingBox().expand(2),
            (entity) -> {
                if (!(entity instanceof LivingEntity)) return false;
                if (!entity.isAlive()) return false;
                if (!entity.isAttackable()) return false;
                // 名札が付いている、またはボート/トロッコに乗っている場合は対象外
                if (entity.hasCustomName()) return false;
                var vehicle = entity.getVehicle();
                if (vehicle instanceof BoatEntity) return false;
                if (vehicle instanceof AbstractMinecartEntity) return false;
                if (entity instanceof ZombifiedPiglinEntity) return false; // 敵対されないように
                if (entity instanceof ZombieVillagerEntity) return false;

                if (entity instanceof ZombieEntity) return true;
                if (entity instanceof SkeletonEntity) return true;
                if (entity instanceof SpiderEntity) return true;
                if (entity instanceof WitchEntity) return true;
                if (entity instanceof GuardianEntity) return true;
                if (entity instanceof SilverfishEntity) return true;
                if (entity instanceof PillagerEntity) return true;
                return false;
            }
        );

        HostileEntity mostNearestEntity = null;
        var mostNearestEntityDistance = Double.MAX_VALUE;
        for (var entity : entities) {
            var distance = client.player.distanceTo(entity);
            if (distance < mostNearestEntityDistance) {
                mostNearestEntity = entity;
                mostNearestEntityDistance = distance;
            }
        }
        if (mostNearestEntity == null) return;
        client.interactionManager.attackEntity(client.player, mostNearestEntity);
        client.player.attack(mostNearestEntity);
    }

    private static void feedArmadilloIfPossible(MinecraftClient client) {
        // 手に蜘蛛の目を持っていない場合は何もしない
        var hand = findSpecifiedItemFromBothHand(client, Items.SPIDER_EYE);
        if (hand == null) return;

        // 連打しすぎ防止のため、一定間隔でのみ試行
        long now = client.world.getTime();
        if (now - lastArmadilloFeedTick < 10) return; // 約0.5秒間隔

        // 古い記録のクリーンアップ
        if (!armadilloTriedAt.isEmpty()) {
            Iterator<Map.Entry<Integer, Long>> it = armadilloTriedAt.entrySet().iterator();
            while (it.hasNext()) {
                var e = it.next();
                if (now - e.getValue() >= ARMADILLO_RETRY_TICKS) {
                    it.remove();
                }
            }
        }

        var entities = client.player.getWorld().getEntitiesByClass(
            ArmadilloEntity.class,
            client.player.getBoundingBox().expand(2.5),
            (entity) -> {
                if (!entity.isAlive()) return false;
                // 成体のみ対象
                if (entity.isBaby()) return false;
                return true;
            }
        );

        ArmadilloEntity nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (var e : entities) {
            // 5分リトライ禁止（同一エンティティID）
            int id = e.getId();
            Long triedAt = armadilloTriedAt.get(id);
            if (triedAt != null && now - triedAt < ARMADILLO_RETRY_TICKS) continue;

            double d = client.player.distanceTo(e);
            if (d < nearestDist) {
                nearest = e;
                nearestDist = d;
            }
        }
        if (nearest == null) return;

        // 近くの成体アルマジロに蜘蛛の目を与える
        client.interactionManager.interactEntity(client.player, nearest, hand);
        lastArmadilloFeedTick = now;
        armadilloTriedAt.put(nearest.getId(), now);
    }

    private static @Nullable Hand findSpecifiedItemFromBothHand(MinecraftClient client, Item item) {
        var mainHand = client.player.getMainHandStack();
        if (mainHand != null && mainHand.getItem() == item) return Hand.MAIN_HAND;

        var offHand = client.player.getOffHandStack();
        if (offHand != null && offHand.getItem() == item) return Hand.OFF_HAND;
        
        return null;
    }

    private static void seedUekae(MinecraftClient client, Block destBlock, Item seedItem) {
        var hand = findSpecifiedItemFromBothHand(client, seedItem);
        if (hand == null) return;
        var currentPosition = client.player.getBlockPos();

        var stream = BlockPos.stream(currentPosition.add(-1, 1, -1), currentPosition.add(1, -1, 1));
        for (BlockPos pos : (Iterable<BlockPos>) stream::iterator) {
            var state = client.world.getBlockState(pos);
            var block = state.getBlock();
            if (
                block instanceof CropBlock cropBlock
                && cropBlock == destBlock
                && cropBlock.isMature(state)
            ) {
                client.interactionManager.attackBlock(pos, Direction.UP);
                client.interactionManager.interactBlock(client.player, hand, new BlockHitResult(
                    Vec3d.ofCenter(pos), Direction.UP,
                    pos.add(0, -1, 0), false
                ));
                break;
            }
        }
    }

    // public static void onMainHandToolWasBroken(ClientPlayerEntity player) {
    //     var screenHandler = player.currentScreenHandler;
    //     var slots = screenHandler.slots;
    //     var currentSlot = -1;
    //     // 1. まずは現在のスロットを探す
    //     for (var i = 0; i < slots.size(); i++) {
    //         var slot = slots.get(i);
    //         if (slot.getStack() == player.getMainHandStack()) {
    //             currentSlot = i;
    //             break;
    //         }
    //     }
    //     var currentItemStack = slots.get(currentSlot).getStack();

    //     if (currentSlot < 0) {
    //         LOGGER.info("failed to find current slot");
    //         return;
    //     }

    //     // 2. 同じアイテムのスロットを探す
    //     for (var slotId=0; slotId < slots.size(); slotId++) {
    //         var slot = slots.get(slotId);
    //         var slotItemStack = slot.getStack();
    //         if (slotItemStack == currentItemStack) continue;
    //         if (currentItemStack.getItem() != slotItemStack.getItem()) continue;
    //         if (currentItemStack.getDamage() >= currentItemStack.getMaxDamage()) continue;
    //         if (slotItemStack.hasEnchantments()) continue;
    //         if (slotItemStack.get(DataComponentTypes.CUSTOM_NAME) != null) continue;
    //         LOGGER.info("Swap with slot {}", slotId);
    //         player.sendMessage(Text.of("傲慢: ツールが壊れたので同等のものと交換しました"), false);
    //         clickSlot(player, slotId);
    //         clickSlot(player, currentSlot);
    //         break;
    //     }
    // }

    // public static void clickSlot(ClientPlayerEntity player, int slotId) {
    //     var screenHandler = player.currentScreenHandler;
    //     var slots = screenHandler.slots;
    //     ArrayList<ItemStack> list = Lists.newArrayListWithCapacity(slotId);
    //     for (Slot s : slots) {
    //         list.add(s.getStack().copy());
    //     }
    //     screenHandler.onSlotClick(slotId, 0, SlotActionType.PICKUP, player);
    //     var int2ObjectMap = new Int2ObjectOpenHashMap<ItemStack>();
    //     for (int i = 0; i < slots.size(); i++) {
    //         if (ItemStack.areEqual(slots.get(i).getStack(), list.get(i))) continue;
    //         int2ObjectMap.put(i, list.get(i));
    //     }
    //     player.networkHandler.sendPacket(new ClickSlotC2SPacket(
    //         screenHandler.syncId, screenHandler.getRevision(),
    //         slotId, 0, SlotActionType.PICKUP, slots.get(slotId).getStack().copy(), int2ObjectMap
    //     ));
    // }
}
