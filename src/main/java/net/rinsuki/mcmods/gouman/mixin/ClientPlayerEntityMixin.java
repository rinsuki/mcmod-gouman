package net.rinsuki.mcmods.gouman.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EntityStatuses;
import net.rinsuki.mcmods.gouman.GoumanMod;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {
    @Inject(method="handleStatus", at = @At("HEAD"))
    private void onHandleStatus(byte status, CallbackInfo info) {
        if (status == EntityStatuses.BREAK_MAINHAND) {
            GoumanMod.onMainHandToolWasBroken((ClientPlayerEntity)(Object)this);
        }
    }
}
