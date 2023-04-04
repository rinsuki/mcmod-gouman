package net.rinsuki.mcmods.gouman;

import java.util.ArrayList;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
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
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class GoumanMod implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("gouman");
    private static boolean enabled = false;
    private static KeyBinding keyToggle;

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
        client.player.sendMessage(Text.of("傲慢: 読み込まれました！ Gキーで有効/無効を切り替えます。"));
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
            client.player.sendMessage(Text.of(String.format("傲慢: %s", enabled ? "有効" : "無効")));
        }
        if (!enabled) return;
        autoAttack(client);
        seedUekae(client, Blocks.WHEAT, Items.WHEAT_SEEDS);
        seedUekae(client, Blocks.BEETROOTS, Items.BEETROOT_SEEDS);
        seedUekae(client, Blocks.CARROTS, Items.CARROT);
        seedUekae(client, Blocks.POTATOES, Items.POTATO);
    }

    private static void autoAttack(MinecraftClient client) {
        if (client.player.getAttackCooldownProgress(0f) < 1.0f) return;
        if (client.player.isUsingItem()) return;

        var entities = client.player.world.getEntitiesByClass(
            HostileEntity.class,
            client.player.getBoundingBox().expand(2),
            (entity) -> {
                if (!(entity instanceof LivingEntity)) return false;
                if (!entity.isAlive()) return false;
                if (!entity.isAttackable()) return false;
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

        @Nullable BlockPos targetedBlockPosition = null;

        if (client.crosshairTarget instanceof BlockHitResult blockHitResult) {
            targetedBlockPosition = blockHitResult.getBlockPos();
        }

        if (targetedBlockPosition == null) return;

        BlockState blockState = client.world.getBlockState(targetedBlockPosition);
        if (blockState.getBlock() != destBlock) return;
        
        var stream = BlockPos.stream(targetedBlockPosition.add(-1, 0, -1), targetedBlockPosition.add(1, 0, 1));
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

    public static void onMainHandToolWasBroken(ClientPlayerEntity player) {
        var screenHandler = player.currentScreenHandler;
        var slots = screenHandler.slots;
        var currentSlot = -1;
        // 1. まずは現在のスロットを探す
        for (var i = 0; i < slots.size(); i++) {
            var slot = slots.get(i);
            if (slot.getStack() == player.getMainHandStack()) {
                currentSlot = i;
                break;
            }
        }
        var currentItemStack = slots.get(currentSlot).getStack();

        if (currentSlot < 0) {
            LOGGER.info("failed to find current slot");
            return;
        }

        // 2. 同じアイテムのスロットを探す
        for (var slotId=0; slotId < slots.size(); slotId++) {
            var slot = slots.get(slotId);
            var slotItemStack = slot.getStack();
            if (slotItemStack == currentItemStack) continue;
            if (currentItemStack.getItem() != slotItemStack.getItem()) continue;
            if (currentItemStack.getDamage() >= currentItemStack.getMaxDamage()) continue;
            if (slotItemStack.hasEnchantments()) continue;
            if (slotItemStack.hasCustomName()) continue;
            LOGGER.info("Swap with slot {}", slotId);
            player.sendMessage(Text.of("傲慢: ツールが壊れたので同等のものと交換しました"));
            clickSlot(player, slotId);
            clickSlot(player, currentSlot);
            break;
        }
    }

    public static void clickSlot(ClientPlayerEntity player, int slotId) {
        var screenHandler = player.currentScreenHandler;
        var slots = screenHandler.slots;
        ArrayList<ItemStack> list = Lists.newArrayListWithCapacity(slotId);
        for (Slot s : slots) {
            list.add(s.getStack().copy());
        }
        screenHandler.onSlotClick(slotId, 0, SlotActionType.PICKUP, player);
        var int2ObjectMap = new Int2ObjectOpenHashMap<ItemStack>();
        for (int i = 0; i < slots.size(); i++) {
            if (ItemStack.areEqual(slots.get(i).getStack(), list.get(i))) continue;
            int2ObjectMap.put(i, list.get(i));
        }
        player.networkHandler.sendPacket(new ClickSlotC2SPacket(
            screenHandler.syncId, screenHandler.getRevision(),
            slotId, 0, SlotActionType.PICKUP, slots.get(slotId).getStack().copy(), int2ObjectMap
        ));
    }
}
