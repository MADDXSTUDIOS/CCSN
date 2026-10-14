package uk.maddx.ccsn.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import uk.maddx.ccsn.CCSN;
import uk.maddx.ccsn.drive.DriveItem;
import uk.maddx.ccsn.nas.NasBlockEntity;
import uk.maddx.ccsn.nas.NasMenu;

/** NAS bay GUI: four bays stacked like the front of the NAS, with what's in each one. */
public final class NasScreen extends AbstractContainerScreen<NasMenu> {
    private static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath(CCSN.MOD_ID, "textures/gui/nas.png");
    private static final int[] TIER_COLOURS = { 0xFF404040, 0xFF5A5E66, 0xFFB07A10, 0xFF1C8C8C };

    public NasScreen(NasMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 186;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        for (int bay = 0; bay < NasBlockEntity.BAYS; bay++) {
            int y = NasMenu.BAY_Y + bay * 18 + 5;
            graphics.drawString(font, String.valueOf(bay + 1), NasMenu.BAY_X - 10, y, 0xFF9AA0AA, false);

            ItemStack stack = menu.getDrive(bay);
            Component text;
            int colour;
            if (stack.getItem() instanceof DriveItem drive) {
                String label = DriveItem.getLabel(stack);
                String size = DriveItem.formatBytes(drive.capacity());
                text = Component.literal("T" + drive.tier() + "  " + size + (label != null ? "  " + label : ""));
                colour = TIER_COLOURS[drive.tier()];
            } else {
                text = Component.translatable("gui.ccsn.nas.empty_bay");
                colour = 0xFF8B8B8B;
            }
            int x = NasMenu.BAY_X + 22;
            String fitted = font.plainSubstrByWidth(text.getString(), imageWidth - x - 7);
            graphics.drawString(font, fitted, x, y, colour, false);
        }
    }
}
