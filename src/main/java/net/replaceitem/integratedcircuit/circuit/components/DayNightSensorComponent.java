package net.replaceitem.integratedcircuit.circuit.components;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.replaceitem.integratedcircuit.circuit.Circuit;
import net.replaceitem.integratedcircuit.circuit.Component;
import net.replaceitem.integratedcircuit.circuit.state.ComponentState;
import net.replaceitem.integratedcircuit.circuit.state.property.BooleanComponentProperty;
import net.replaceitem.integratedcircuit.client.IntegratedCircuitScreen;
import net.replaceitem.integratedcircuit.util.ComponentPos;
import net.replaceitem.integratedcircuit.util.FlatDirection;
import net.replaceitem.integratedcircuit.util.IntegratedCircuitIdentifier;
import net.replaceitem.integratedcircuit.circuit.ServerCircuit;
import net.minecraft.util.math.random.Random;

public class DayNightSensorComponent extends Component {

    // Property to track sensor mode (true = Night Mode, false = Day Mode)
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
    public void onBlockAdded(ComponentState state, Circuit circuit, ComponentPos pos, ComponentState oldState) {
        if (!circuit.isClient) {
            circuit.scheduleTick(pos, this, 1); //  Ensures ticking starts on placement
        }
    }

    @Override
    public void onUse(ComponentState state, Circuit circuit, ComponentPos pos, PlayerEntity player) {
        if (circuit.isClient) return; //  Only process on the server

        // Toggle mode (Day ↔ Night)
        ComponentState newState = state.cycle(NIGHT_MODE);

        // Apply only if the state actually changed
        if (!newState.equals(state)) {
            circuit.setComponentState(pos, newState, Component.NOTIFY_ALL);
            circuit.updateNeighborsAlways(pos, this);

            // Play a toggle sound
            circuit.playSound(null, SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, 0.3f, newState.get(NIGHT_MODE) ? 0.6f : 0.5f);
        }
    }


    @Override
    public void onStateReplaced(ComponentState state, Circuit circuit, ComponentPos pos, ComponentState newState) {
        if (state.isOf(newState.getComponent())) return;
        circuit.updateNeighborsAlways(pos, this);
    }

    @Override
    public int getWeakRedstonePower(ComponentState state, Circuit circuit, ComponentPos pos, FlatDirection direction) {
        // Ensure we have access to the world instance
        World world = circuit.getLevel(); // Uses circuit's world context
        if (world == null) return 0; // Prevents crashes

        // Get the current time in ticks (0-23999) where 0 = sunrise, 12000 = sunset, 18000 = midnight
        long timeOfDay = world.getTimeOfDay() % 24000;

        boolean isNight = timeOfDay >= 13000; // Night starts at tick 13000
        boolean isActive = (state.get(NIGHT_MODE) && isNight) || (!state.get(NIGHT_MODE) && !isNight);

        return isActive ? 15 : 0;
    }

    @Override
    public boolean emitsRedstonePower(ComponentState state) {
        return true;
    }

    @Override
    public boolean isSolidBlock(Circuit circuit, ComponentPos pos) {
        return false;
    }

    @Override
    public void appendProperties(ComponentState.PropertyBuilder builder) {
        builder.append(NIGHT_MODE);
    }

    @Override
    public void scheduledTick(ComponentState state, ServerCircuit circuit, ComponentPos pos, Random random) {
        World world = circuit.getLevel();
        if (world == null) return;

        // Get time of day (0-23999), check if it's night
        long timeOfDay = world.getTimeOfDay() % 24000;
        boolean isNight = timeOfDay >= 13000;

        boolean currentMode = state.get(NIGHT_MODE);
        boolean shouldEmit = (currentMode && isNight) || (!currentMode && !isNight);

        // Check the current redstone power output
        int currentPower = state.getWeakRedstonePower(circuit, pos, FlatDirection.NORTH);
        int newPower = shouldEmit ? 15 : 0;

        if (currentPower != newPower) {
            //  Update component state
            ComponentState newState = state.with(NIGHT_MODE, shouldEmit);
            circuit.setComponentState(pos, newState, Component.NOTIFY_ALL);

            //  Explicitly update redstone power in the circuit
            circuit.updateNeighborsAlways(pos, this);
        }

        //  Ensure the game knows redstone power changed and update comparator as well
        circuit.updateComparators(pos, this);  // Force comparator updates if used
        circuit.updateNeighborsAlways(pos, this); // Force all neighbors

        // Schedule next tick to keep checking the time
        circuit.scheduleBlockTick(pos, this, 20);
    }




}
