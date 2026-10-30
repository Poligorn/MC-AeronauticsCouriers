package dev.poligorn.aerocouriers.license;

import dev.poligorn.aerocouriers.registry.ModComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * {@code aerocouriers:pilot_license}: worn in the Curios {@code license} slot, rendered as epaulettes.
 * Curios 9 picks up {@link ICurioItem} implemented on the item automatically.
 */
public class PilotLicenseItem extends Item implements ICurioItem {
    private static final int SYNC_INTERVAL_TICKS = 20;

    public PilotLicenseItem(Properties properties) {
        super(properties);
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        if (slotContext.entity() instanceof ServerPlayer player
                && Licenses.CURIOS_SLOT.equals(slotContext.identifier())
                && player.tickCount % SYNC_INTERVAL_TICKS == 0) {
            Licenses.tickWorn(player, stack);
        }
    }

    @Override
    public ICurio.DropRule getDropRule(SlotContext slotContext, DamageSource source, boolean recentlyHit,
                                       ItemStack stack) {
        return ICurio.DropRule.ALWAYS_KEEP;
    }

    @Override
    @SuppressWarnings("deprecation")
    public ICurio.DropRule getDropRule(SlotContext slotContext, DamageSource source, int lootingLevel,
                                       boolean recentlyHit, ItemStack stack) {
        return ICurio.DropRule.ALWAYS_KEEP;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        LicenseOwner owner = stack.get(ModComponents.LICENSE_OWNER.get());
        if (owner == null) {
            tooltip.add(Component.translatable("item.aerocouriers.pilot_license.unbound").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("item.aerocouriers.pilot_license.owner", owner.name())
                    .withStyle(ChatFormatting.GRAY));
            if (owner.number() > 0) {
                tooltip.add(Component.translatable("item.aerocouriers.pilot_license.number", owner.number())
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        LicenseRank rank = stack.getOrDefault(ModComponents.LICENSE_RANK.get(), LicenseRank.RANK1);
        tooltip.add(Component.translatable("item.aerocouriers.pilot_license.rank", rank.displayName())
                .withStyle(ChatFormatting.GOLD));
    }
}
