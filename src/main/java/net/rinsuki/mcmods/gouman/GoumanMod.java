package net.rinsuki.mcmods.gouman;

import java.util.ArrayList;

import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BeetrootBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarrotBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkConstants;

@Mod("gouman")
@Mod.EventBusSubscriber(Dist.CLIENT)
public class GoumanMod {
    public GoumanMod() {
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class, () -> new IExtensionPoint.DisplayTest(() -> NetworkConstants.IGNORESERVERONLY, (a, b) -> true));
    }

    private static final Minecraft mc = Minecraft.getInstance();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean enabled = false;

    @SubscribeEvent
    public static void joined(WorldEvent.Load e) {
        enabled = false;
        LOGGER.info("hoge");
        mc.gui.getChat().addMessage(new TextComponent("[傲慢MOD] 読み込まれました！ //gouman on で有効にできます"));
    }

    @SubscribeEvent
    public static void chatEvent(ClientChatEvent e) {
        if (e.getOriginalMessage().startsWith("//gouman ")) {
            String arg = e.getOriginalMessage().substring("//gouman ".length());
            if (arg.equals("on")) {
                enabled = true;
                mc.gui.getChat().addMessage(new TextComponent("[傲慢MOD] 有効になりました！"));
            } else if (arg.equals("off")) {
                enabled = false;
                mc.gui.getChat().addMessage(new TextComponent("[傲慢MOD] 無効になりました！"));
            } else {
                mc.gui.getChat().addMessage(new TextComponent("[傲慢MOD] 無効な引数です！"));
            }
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void debugScreenAddInfo(RenderGameOverlayEvent.Text e) {
        if (enabled) {
            e.getLeft().add("");
            e.getLeft().add("傲慢MOD: Enabled");
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (!enabled) return;
        if (e.phase != TickEvent.Phase.END) return;
        if (mc.player == null) return;
        attackToZombie();
        seedUekae();
        seedUekae_beetroots();
        seedUekae_carrots();
    }

    private static void attackToZombie() {
        var entities = mc.player.level.getEntitiesOfClass(
            Monster.class,
            mc.player.getBoundingBox().inflate(2),
            (entity) -> {
                if (!(entity instanceof LivingEntity)) return false;
                if (!entity.isAlive()) return false;
                if (entity.is(mc.player)) return false;
                if (entity instanceof ZombifiedPiglin) return false;
                if (entity instanceof Zombie) return true;
                return false;
            }
        );
        for (Entity entity : entities) {
            if (mc.player.getAttackStrengthScale(0) < 1.0F) return;
            if (!entity.isAlive()) continue;
            if (!entity.isAttackable()) continue;
            mc.gameMode.attack(mc.player, entity);
            break;
        }
    }

    private static ArrayList<BlockPos> breakedWheels = new ArrayList<>();

    private static void seedUekae() {
        var mainHandItemStack = mc.player.getMainHandItem();
        if (mainHandItemStack == null) return;
        if (mainHandItemStack.getItem() != Items.WHEAT_SEEDS) return;
        
        var playerPos = mc.player.getOnPos();

        for (var pos : breakedWheels) {
            var blockState = mc.player.level.getBlockState(pos);
            if (blockState != null && blockState.getBlock() != Blocks.AIR) continue;
            var result = mc.gameMode.useItemOn(mc.player, (ClientLevel) mc.player.level, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos.atY(pos.getY() - 1), false));
            if (result == InteractionResult.PASS || result == InteractionResult.SUCCESS) {
                breakedWheels.remove(pos);
            }
            LOGGER.info("RESULT >> {}", result);
            return;
        }

        for (int x = playerPos.getX() - 1; x <= playerPos.getX() + 1; x++) {
            for (int y = playerPos.getY() - 2; y <= playerPos.getY() + 2; y++) {
                for (int z = playerPos.getZ() - 1; z <= playerPos.getZ() + 1; z++) {
                    var pos = new BlockPos(x, y, z);
                    var blockState = mc.player.level.getBlockState(pos);
                    if (blockState.getBlock() != Blocks.WHEAT) continue;
                    if (blockState.getValue(CropBlock.AGE) < 7) continue;
                    if (!mc.gameMode.startDestroyBlock(pos, Direction.UP)) return;
                    breakedWheels.add(pos);
                    break;
                }
            }
        }
    }

    private static void seedUekae_beetroots() {
        var mainHandItemStack = mc.player.getMainHandItem();
        if (mainHandItemStack == null) return;
        if (mainHandItemStack.getItem() != Items.BEETROOT_SEEDS) return;
        
        var playerPos = mc.player.getOnPos();

        for (var pos : breakedWheels) {
            var blockState = mc.player.level.getBlockState(pos);
            if (blockState != null && blockState.getBlock() != Blocks.AIR) continue;
            var result = mc.gameMode.useItemOn(mc.player, (ClientLevel) mc.player.level, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos.atY(pos.getY() - 1), false));
            if (result == InteractionResult.PASS || result == InteractionResult.SUCCESS) {
                breakedWheels.remove(pos);
            }
            LOGGER.info("RESULT >> {}", result);
            return;
        }

        for (int x = playerPos.getX() - 1; x <= playerPos.getX() + 1; x++) {
            for (int y = playerPos.getY() - 2; y <= playerPos.getY() + 2; y++) {
                for (int z = playerPos.getZ() - 1; z <= playerPos.getZ() + 1; z++) {
                    var pos = new BlockPos(x, y, z);
                    var blockState = mc.player.level.getBlockState(pos);
                    if (blockState.getBlock() != Blocks.BEETROOTS) continue;
                    if (blockState.getValue(BeetrootBlock.AGE) < 3) continue;
                    if (!mc.gameMode.startDestroyBlock(pos, Direction.UP)) return;
                    breakedWheels.add(pos);
                    break;
                }
            }
        }
    }

    private static void seedUekae_carrots() {
        var mainHandItemStack = mc.player.getMainHandItem();
        if (mainHandItemStack == null) return;
        if (mainHandItemStack.getItem() != Items.CARROT) return;
        
        var playerPos = mc.player.getOnPos();

        for (var pos : breakedWheels) {
            var blockState = mc.player.level.getBlockState(pos);
            if (blockState != null && blockState.getBlock() != Blocks.AIR) continue;
            var result = mc.gameMode.useItemOn(mc.player, (ClientLevel) mc.player.level, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos.atY(pos.getY() - 1), false));
            if (result == InteractionResult.PASS || result == InteractionResult.SUCCESS) {
                breakedWheels.remove(pos);
            }
            LOGGER.info("RESULT >> {}", result);
            return;
        }

        for (int x = playerPos.getX() - 1; x <= playerPos.getX() + 1; x++) {
            for (int y = playerPos.getY() - 2; y <= playerPos.getY() + 2; y++) {
                for (int z = playerPos.getZ() - 1; z <= playerPos.getZ() + 1; z++) {
                    var pos = new BlockPos(x, y, z);
                    var blockState = mc.player.level.getBlockState(pos);
                    if (blockState.getBlock() != Blocks.CARROTS) continue;
                    if (blockState.getValue(CarrotBlock.AGE) < CarrotBlock.MAX_AGE) continue;
                    if (!mc.gameMode.startDestroyBlock(pos, Direction.UP)) return;
                    breakedWheels.add(pos);
                    break;
                }
            }
        }
    }
}
