package net.replaceitem.integratedcircuit.circuit.components;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.replaceitem.integratedcircuit.circuit.Circuit;
import net.replaceitem.integratedcircuit.circuit.Component;
import net.replaceitem.integratedcircuit.circuit.state.ComponentState;
import net.replaceitem.integratedcircuit.circuit.state.property.BooleanComponentProperty;
import net.replaceitem.integratedcircuit.util.ComponentPos;
import net.replaceitem.integratedcircuit.util.IntegratedCircuitIdentifier;
import org.jetbrains.annotations.Nullable;

public class CopperBulbComponent extends Component {

    // ✅ Converted to `BooleanComponentProperty` with proper encoding
    public static final BooleanComponentProperty LIT = new BooleanComponentProperty("lit", 0);
    public static final BooleanComponentProperty POWERED = new BooleanComponentProperty("powered", 1);

    // 🖼️ Fixed Identifier Paths
    private static final Identifier TEXTURE = new IntegratedCircuitIdentifier("textures/integrated_circuit/copper_bulb.png");
    private static final Identifier TEXTURE_LIT = new IntegratedCircuitIdentifier("textures/integrated_circuit/copper_bulb_lit.png");
    private static final Identifier TEXTURE_POWERED = new IntegratedCircuitIdentifier("textures/integrated_circuit/copper_bulb_powered.png");
    private static final Identifier TEXTURE_LIT_POWERED = new IntegratedCircuitIdentifier("textures/integrated_circuit/copper_bulb_lit_powered.png");

    public CopperBulbComponent(int id, Settings settings) {
        super(id, settings);
        // ✅ Use correct `setDefaultState` for 1.20.x
        this.setDefaultState(this.getDefaultState().with(LIT, false).with(POWERED, false));
    }

    @Override
    public @Nullable Identifier getItemTexture() {
        return TEXTURE;
    }

    private Identifier getTexture(boolean lit, boolean powered) {
        return lit ? (powered ? TEXTURE_LIT_POWERED : TEXTURE_LIT) : (powered ? TEXTURE_POWERED : TEXTURE);
    }

    @Override
    public void render(DrawContext drawContext, int x, int y, float a, ComponentState state) {
        // Reset the shader color to avoid red overlays
        drawContext.setShaderColor(1.0f, 1.0f, 1.0f, a);

        // Fetch the correct texture based on state
        Identifier texture = getTexture(state.get(LIT), state.get(POWERED));

        // Render the texture
        drawContext.drawTexture(texture, x, y, 0, 0, 16, 16, 16, 16);
    }

    @Override
    public void onBlockAdded(ComponentState state, Circuit circuit, ComponentPos pos, ComponentState oldState) {
        if (oldState.getComponent() != state.getComponent()) {
            update(state, circuit, pos);
        }
    }

    @Override
    public void neighborUpdate(ComponentState state, Circuit circuit, ComponentPos pos, Component sourceBlock, ComponentPos sourcePos, boolean notify) {
        update(state, circuit, pos);
    }

    public void update(ComponentState state, Circuit circuit, ComponentPos pos) {
        boolean receivingPower = circuit.isReceivingRedstonePower(pos);

        if (receivingPower != state.get(POWERED)) {
            ComponentState newState = state;

            if (receivingPower) {
                newState = newState.cycle(LIT);

                // ✅ 1.20.x Fix: Use alternative sounds
                boolean isLit = newState.get(LIT);
                circuit.playSound(null, isLit ? SoundEvents.BLOCK_REDSTONE_TORCH_BURNOUT : SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, 1, 1);
            }

            circuit.setComponentState(pos, newState.with(POWERED, receivingPower), Component.NOTIFY_ALL);
        }
    }

    @Override
    public boolean isSolidBlock(Circuit circuit, ComponentPos pos) {
        return true;
    }

    @Override
    public boolean hasComparatorOutput(ComponentState componentState) {
        return true;
    }

    @Override
    public int getComparatorOutput(ComponentState state, Circuit circuit, ComponentPos pos) {
        return state.get(LIT) ? 15 : 0;
    }

    @Override
    public void appendProperties(ComponentState.PropertyBuilder builder) {
        super.appendProperties(builder);
        builder.append(LIT, POWERED);
    }
}
