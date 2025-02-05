package net.replaceitem.integratedcircuit.circuit;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.replaceitem.integratedcircuit.circuit.components.DayNightSensorComponent;
import net.replaceitem.integratedcircuit.circuit.components.PortComponent;
import net.replaceitem.integratedcircuit.circuit.context.BlockEntityServerCircuitContext;
import net.replaceitem.integratedcircuit.circuit.context.ServerCircuitContext;
import net.replaceitem.integratedcircuit.circuit.state.ComponentState;
import net.replaceitem.integratedcircuit.util.ComponentPos;
import net.replaceitem.integratedcircuit.util.FlatDirection;

// ✅ Fix for @Nullable annotations
import org.jetbrains.annotations.Nullable;

public class ServerCircuit extends Circuit {

    private final ServerCircuitContext context;
    protected final CircuitTickScheduler circuitTickScheduler = new CircuitTickScheduler();
    private long lastCheckedTime = -1; // Track last daylight check time

    private int lastSignalStrength = -1; // Store last daylight signal strength

    public int getLastSignalStrength() {
        return lastSignalStrength;
    }


    public ServerCircuit(ServerCircuitContext context) {
        super(false); // Server circuits are never client-side
        this.context = context;
    }

    @Override
    public World getLevel() {
        return (context instanceof BlockEntityServerCircuitContext) ?
                ((BlockEntityServerCircuitContext) context).getWorld() : null;
    }

    @Override
    public CircuitTickScheduler getCircuitTickScheduler() {
        return this.circuitTickScheduler;
    }

    public ServerCircuitContext getContext() {
        return context;
    }

    @Override
    public long getTime() {
        return context.getTime();
    }

    public void tick() {
        this.circuitTickScheduler.tick(this.getTime(), 65536, this::tickBlock);

        // Update Day/Night Sensors every 20 ticks (1 second)
        if (this.getTime() % 20 == 0) {
            updateDayNightSensors();
        }

        context.markDirty();
    }

    private void tickBlock(ComponentPos pos, Component block) {
        ComponentState blockState = this.getComponentState(pos);
        if (blockState.isOf(block)) {
            blockState.scheduledTick(this, pos, this.context.getRandom());
        }
    }

    public void onExternalPowerChanged(FlatDirection direction, int power) {
        ComponentPos pos = PORT_POSITIONS.get(direction);
        ComponentState state = getComponentState(pos);
        boolean isOutput = state.get(PortComponent.IS_OUTPUT);
        if (!isOutput && state.get(PortComponent.POWER) != power) {
            setComponentState(pos, state.with(PortComponent.POWER, power), Component.NOTIFY_ALL);
        }
    }

    public int getPortOutputStrength(FlatDirection direction) {
        ComponentPos pos = PORT_POSITIONS.get(direction);
        ComponentState state = getComponentState(pos);
        if (!state.get(PortComponent.IS_OUTPUT)) return 0;
        return state.get(PortComponent.POWER);
    }

    public static ServerCircuit fromNbt(NbtCompound nbt, ServerCircuitContext context) {
        if (nbt == null) return null;
        ServerCircuit circuit = new ServerCircuit(context);
        circuit.readNbt(nbt);
        return circuit;
    }

    private NbtList tickSchedulerNbtBuffer;

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        NbtList tickSchedulerNbt = nbt.getList("tickScheduler", NbtElement.COMPOUND_TYPE);
        if (this.context.isReady()) {
            loadTickScheduler(tickSchedulerNbt);
        } else {
            this.tickSchedulerNbtBuffer = tickSchedulerNbt;
        }
    }

    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.put("tickScheduler", this.circuitTickScheduler.toNbt(this.getTime()));
    }

    public void onWorldIsPresent() {
        if (this.tickSchedulerNbtBuffer != null) {
            loadTickScheduler(this.tickSchedulerNbtBuffer);
            this.tickSchedulerNbtBuffer = null;
        }
    }

    private void loadTickScheduler(NbtList list) {
        this.circuitTickScheduler.loadFromNbt(list, this.getTime());
    }

    @Override
    public void placeComponentState(ComponentPos pos, Component component, FlatDirection placementRotation) {
        ComponentState placementState = component.getPlacementState(this, pos, placementRotation);
        if (placementState == null) placementState = Components.AIR_DEFAULT_STATE;

        ComponentState beforeState = this.getComponentState(pos);
        if (beforeState.isAir() && placementState.isAir()) return;

        this.setComponentState(pos, placementState, Component.NOTIFY_ALL);
        placementState.getComponent().onPlaced(this, pos, placementState);
    }

    @Override
    public void playSoundInternal(@Nullable PlayerEntity except, SoundEvent sound, SoundCategory category, float volume, float pitch) {
        this.context.playSound(except, sound, category, volume, pitch);
    }

    public void playSoundExternal(@Nullable PlayerEntity except, SoundEvent sound, SoundCategory category, float volume, float pitch, ComponentPos pos) {
        World world = this.getLevel();
        if (world == null) return;

        BlockPos blockPos = (this.context instanceof BlockEntityServerCircuitContext) ?
                ((BlockEntityServerCircuitContext) this.context).getPos() : null;

        if (blockPos == null) return;

        world.playSound(null, blockPos, sound, category, volume, pitch);
    }

    @Override
    protected void updateListeners(ComponentPos pos, ComponentState oldState, ComponentState state, int flags) {
        this.context.onComponentUpdate(pos, state);
    }

    @Override
    public void updateNeighbors(ComponentPos pos, Component component) {
        this.updateNeighborsAlways(pos, component);
    }

    @Override
    public void updateNeighborsAlways(ComponentPos pos, Component sourceComponent) {
        this.neighborUpdater.updateNeighbors(pos, sourceComponent, null);
    }

    public void updateNeighborsExcept(ComponentPos pos, Component sourceComponent, FlatDirection direction) {
        this.neighborUpdater.updateNeighbors(pos, sourceComponent, direction);
    }

    public void updateNeighbor(ComponentPos pos, Component sourceComponent, ComponentPos sourcePos) {
        this.neighborUpdater.updateNeighbor(pos, sourceComponent, sourcePos);
    }

    public void updateNeighbor(ComponentState state, ComponentPos pos, Component sourceComponent, ComponentPos sourcePos, boolean notify) {
        this.neighborUpdater.updateNeighbor(state, pos, sourceComponent, sourcePos, notify);
    }

    public void updateDayNightSensors() {
        if (isClient) return; // Ensure we only run on the server

        World world = getLevel();
        if (world == null) return;

        // Calculate new daylight signal strength
        int newSignalStrength = calculateDaylightSignalStrength(world);

        // Skip updates if the signal strength hasn't changed
        if (newSignalStrength == lastSignalStrength) return;
        lastSignalStrength = newSignalStrength;

        // Iterate through all components in the circuit
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                ComponentPos pos = new ComponentPos(x, y);
                ComponentState state = getComponentState(pos);

                // If it's a Day/Night Sensor, notify the circuit that redstone should be updated
                if (state.getComponent() instanceof DayNightSensorComponent) {
                    updateNeighborsAlways(pos, state.getComponent());
                    updateComparators(pos, state.getComponent());
                }
            }
        }
    }

    /**
     * Ensures all circuits get notified once for redstone updates instead of per-component updates.
     */
    private void updateNeighborsAndComparators() {
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                ComponentPos pos = new ComponentPos(x, y);
                ComponentState state = getComponentState(pos);

                if (state.getComponent() instanceof DayNightSensorComponent) {
                    updateNeighborsAlways(pos, state.getComponent());
                    updateComparators(pos, state.getComponent());
                }
            }
        }
    }

    // Helper function for daylight strength
    private int calculateDaylightSignalStrength(World world) {
        BlockPos circuitPos = (context instanceof BlockEntityServerCircuitContext) ?
                ((BlockEntityServerCircuitContext) context).getPos() : BlockPos.ORIGIN;

        int skyLightLevel = world.getLightLevel(circuitPos.up()); // Check sky light above circuit

        long timeOfDay = world.getTimeOfDay() % 24000;
        boolean isNight = (timeOfDay >= 13000);

        // ✅ Adjusted to return correct strength
        return isNight ? Math.max(1, skyLightLevel / 2) : Math.max(0, 15 - (skyLightLevel / 2));
    }


}
