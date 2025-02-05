package net.replaceitem.integratedcircuit.circuit.components;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.replaceitem.integratedcircuit.circuit.Circuit;
import net.replaceitem.integratedcircuit.circuit.Component;
import net.replaceitem.integratedcircuit.circuit.state.ComponentState;
import net.replaceitem.integratedcircuit.circuit.state.property.IntComponentProperty;
import net.replaceitem.integratedcircuit.util.ComponentPos;
import net.replaceitem.integratedcircuit.util.IntegratedCircuitIdentifier;
import org.jetbrains.annotations.Nullable;

public class LecternComponent extends Component {

    // ✅ Corrected Property: Uses full 4-bit range (0-15)
    public static final IntComponentProperty PAGE = new IntComponentProperty("page", 0, 4);

    // 🖼️ Texture identifier
    private static final Identifier TEXTURE = new IntegratedCircuitIdentifier("textures/integrated_circuit/lectern.png");

    public LecternComponent(int id, Settings settings) {
        super(id, settings);
        this.setDefaultState(this.getDefaultState().with(PAGE, 1));
    }

    @Override
    public @Nullable Identifier getItemTexture() {
        return TEXTURE;
    }

    @Override
    public void render(DrawContext drawContext, int x, int y, float a, ComponentState state) {
        drawContext.drawTexture(TEXTURE, x, y, 0, 0, 16, 16, 16, 16);
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        String text = String.valueOf(state.get(PAGE));

        drawContext.getMatrices().push();
        drawContext.getMatrices().translate(x + 8, y + 8, 0);
        drawContext.getMatrices().scale(0.8f, 0.8f, 1);
        drawContext.getMatrices().translate(
                (float) textRenderer.getWidth(text) / -2,
                (float) textRenderer.fontHeight / -2 + 1,
                0);
        drawContext.drawText(textRenderer, text, 0, 0, 0, false);
        drawContext.getMatrices().pop();
    }

    @Override
    public Text getHoverInfoText(ComponentState state) {
        return Text.literal("Page " + state.get(PAGE));
    }

    @Override
    public void onUse(ComponentState state, Circuit circuit, ComponentPos pos, PlayerEntity player) {
        if (circuit.isClient) {
            return;
        }

        // 🔄 Cycle correctly: 1 → 15, then reset to 1
        int newPage = (state.get(PAGE) >= 15) ? 1 : state.get(PAGE) + 1;

        // 🚀 Create a new state with the updated page
        ComponentState newState = state.with(PAGE, newPage);


        // ✅ Ensure circuit updates the state
        boolean updated = circuit.setComponentState(pos, newState, Component.NOTIFY_ALL);

        // 🔴 Update redstone signal for comparators
        circuit.updateNeighborsAlways(pos, this);
        circuit.updateComparators(pos, this);

        // 📖 Play the page-turning sound
        circuit.playSound(null, SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.BLOCKS, 1, 1);
    }

    @Override
    public void onStateReplaced(ComponentState state, Circuit circuit, ComponentPos pos, ComponentState newState) {
        super.onStateReplaced(state, circuit, pos, newState);
        if (!newState.isOf(this) || !newState.get(PAGE).equals(state.get(PAGE))) {
            circuit.updateComparators(pos, this);
        }
    }

    @Override
    public boolean hasComparatorOutput(ComponentState state) {
        return true;
    }

    @Override
    public int getComparatorOutput(ComponentState state, Circuit circuit, ComponentPos pos) {
        return state.get(PAGE);
    }

    @Override
    public boolean isSolidBlock(Circuit circuit, ComponentPos pos) {
        return false;
    }

    @Override
    public void appendProperties(ComponentState.PropertyBuilder builder) {
        super.appendProperties(builder);
        builder.append(PAGE);
    }
}
