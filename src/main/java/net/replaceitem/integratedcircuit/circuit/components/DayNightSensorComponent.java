package net.replaceitem.integratedcircuit.circuit.components;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.replaceitem.integratedcircuit.circuit.Circuit;
import net.replaceitem.integratedcircuit.circuit.Component;
import net.replaceitem.integratedcircuit.circuit.state.ComponentState;
import net.replaceitem.integratedcircuit.circuit.state.property.BooleanComponentProperty;
import net.replaceitem.integratedcircuit.client.IntegratedCircuitScreen;
import net.replaceitem.integratedcircuit.util.ComponentPos;
import net.replaceitem.integratedcircuit.util.FlatDirection;
import net.replaceitem.integratedcircuit.util.IntegratedCircuitIdentifier;
import net.replaceitem.integratedcircuit.circuit.ServerCircuit;

public class DayNightSensorComponent extends Component {

    private static final BooleanComponentProperty NIGHT_MODE = new BooleanComponentProperty("night_mode", 1);

    public static BooleanComponentProperty getNightModeProperty() {
        return NIGHT_MODE;
    }

    private static final Identifier ITEM_TEXTURE = new IntegratedCircuitIdentifier("textures/integrated_circuit/day_night_sensor.png");
    private static final Identifier TEXTURE_DAY = new IntegratedCircuitIdentifier("textures/integrated_circuit/day_sensor.png");
    private static final Identifier TEXTURE_NIGHT = new IntegratedCircuitIdentifier("textures/integrated_circuit/night_sensor.png");

    public DayNightSensorComponent(int id, Settings settings) {
        super(id, settings);
    }

    @Override
    public Identifier getItemTexture() {
        return ITEM_TEXTURE;
    }

    @Override
    public Text getHoverInfoText(ComponentState state) {
        return state.get(NIGHT_MODE) ? Text.literal("Night Mode (Power at Night)") : Text.literal("Day Mode (Power at Day)");
    }

    @Override
    public void render(DrawContext drawContext, int x, int y, float a, ComponentState state) {
        Identifier texture = state.get(NIGHT_MODE) ? TEXTURE_NIGHT : TEXTURE_DAY;
        IntegratedCircuitScreen.renderComponentTexture(drawContext, texture, x, y, 0, 1, 1, 1, a);
    }

    @Override
    public void onUse(ComponentState state, Circuit circuit, ComponentPos pos, PlayerEntity player) {
        if (circuit.isClient) return; // Only process on the server

        // Toggle day/night mode
        ComponentState newState = state.cycle(NIGHT_MODE);
        circuit.setComponentState(pos, newState, Component.NOTIFY_ALL);
        circuit.updateNeighborsAlways(pos, this);

        // Play toggle sound
        circuit.playSound(null, SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, 0.3f, newState.get(NIGHT_MODE) ? 0.6f : 0.5f);
    }

    @Override
    public int getWeakRedstonePower(ComponentState state, Circuit circuit, ComponentPos pos, FlatDirection direction) {
        if (!(circuit instanceof ServerCircuit)) return 0;

        // Get daylight signal from the circuit
        int daylightStrength = ((ServerCircuit) circuit).getLastSignalStrength();

        // Determine if the sensor should emit power
        boolean isNightMode = state.get(NIGHT_MODE);

        // 🔹 Instead of checking only for `== 0`, use a THRESHOLD for night detection
        boolean shouldEmitPower = isNightMode ? (daylightStrength <= 4) : (daylightStrength > 4);

        return shouldEmitPower ? 15 : 0; // 15 = full power, 0 = no power
    }


    @Override
    public boolean emitsRedstonePower(ComponentState state) {
        return true; // Always capable of emitting redstone
    }

    @Override
    public boolean isSolidBlock(Circuit circuit, ComponentPos pos) {
        return false;
    }

    @Override
    public void appendProperties(ComponentState.PropertyBuilder builder) {
        builder.append(NIGHT_MODE);
    }
}
