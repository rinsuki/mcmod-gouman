package net.rinsuki.mcmods.gouman.mixin;

import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.client.network.ClientPlayerEntity;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {
    // @Inject(method="handleStatus", at = @At("HEAD"))
    // private void onHandleStatus(byte status, CallbackInfo info) {
    //     if (status == EntityStatuses.BREAK_MAINHAND) {
    //         GoumanMod.onMainHandToolWasBroken((ClientPlayerEntity)(Object)this);
    //     }
    // }
}
