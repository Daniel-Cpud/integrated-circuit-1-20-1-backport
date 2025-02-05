package net.replaceitem.integratedcircuit.circuit.components;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import net.replaceitem.integratedcircuit.circuit.Circuit;
import net.replaceitem.integratedcircuit.circuit.ServerCircuit;
import net.replaceitem.integratedcircuit.circuit.Component;
import net.replaceitem.integratedcircuit.circuit.state.ComponentState;
import net.replaceitem.integratedcircuit.circuit.state.property.IntComponentProperty;
import net.replaceitem.integratedcircuit.client.IntegratedCircuitScreen;
import net.replaceitem.integratedcircuit.util.ComponentPos;
import net.replaceitem.integratedcircuit.util.FlatDirection;
import net.replaceitem.integratedcircuit.util.IntegratedCircuitIdentifier;
import net.minecraft.world.World;

public class SoundEmitterComponent extends Component {

    // Property to store the selected sound index (0-4)
    private static final IntComponentProperty SOUND_INDEX = new IntComponentProperty("sound_index", 0, 4);

    // Predefined sounds
    private static final SoundEvent[] SOUND_LIST = {
            SoundEvents.BLOCK_ANVIL_LAND,
            SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
            SoundEvents.BLOCK_PISTON_EXTEND,
            SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK,
            SoundEvents.BLOCK_BAMBOO_BREAK
    };

    private static final Identifier ITEM_TEXTURE = new IntegratedCircuitIdentifier("textures/integrated_circuit/sound_emitter.png");

    public SoundEmitterComponent(int id, Settings settings) {
        super(id, settings);
    }

    @Override
    public Identifier getItemTexture() {
        return ITEM_TEXTURE;
    }

    @Override
    public Text getHoverInfoText(ComponentState state) {
        int soundIndex = state.get(SOUND_INDEX);
        return Text.literal("Sound: " + SOUND_LIST[soundIndex].getId().getPath());
    }

    @Override
    public void render(DrawContext drawContext, int x, int y, float a, ComponentState state) {
        IntegratedCircuitScreen.renderComponentTexture(drawContext, ITEM_TEXTURE, x, y, 0, 1, 1, 1, a);
    }

    @Override
    public void onUse(ComponentState state, Circuit circuit, ComponentPos pos, PlayerEntity player) {
        if (circuit.isClient) return;

        // Cycle through the sound list (increment index and loop back at 4)
        int newSoundIndex = (state.get(SOUND_INDEX) + 1) % SOUND_LIST.length;

        // Update the block state
        circuit.setComponentState(pos, state.with(SOUND_INDEX, newSoundIndex), Component.NOTIFY_ALL);

        // Play the new sound as a preview
        circuit.playSound(null, SOUND_LIST[newSoundIndex], SoundCategory.BLOCKS, 1.0f, 1.0f);
    }

    @Override
    public void onStateReplaced(ComponentState state, Circuit circuit, ComponentPos pos, ComponentState newState) {
        if (state.isOf(newState.getComponent())) return;
        circuit.updateNeighborsAlways(pos, this);
    }

    @Override
    public void neighborUpdate(ComponentState state, Circuit circuit, ComponentPos pos, Component sourceBlock, ComponentPos sourcePos, boolean notify) {
        if (!circuit.isClient && circuit.isReceivingRedstonePower(pos)) {
            circuit.scheduleBlockTick(pos, this, 1); // Schedule a tick when redstone power is detected
        }
    }

    @Override
    public void scheduledTick(ComponentState state, ServerCircuit circuit, ComponentPos pos, Random random) {
        if (circuit.isReceivingRedstonePower(pos)) {
            int soundIndex = state.get(SOUND_INDEX);
            SoundEvent sound = SOUND_LIST[soundIndex];

            // Call playSoundExternal with the translated position
            circuit.playSoundExternal(null, sound, SoundCategory.BLOCKS, 1.0f, 1.0f, pos);
        }
    }

    private void playSound(ServerCircuit circuit, ComponentState state, ComponentPos pos) {
        int soundIndex = state.get(SOUND_INDEX);
        SoundEvent sound = SOUND_LIST[soundIndex];

        // Play the sound globally at the translated position
        circuit.playSoundExternal(null, sound, SoundCategory.BLOCKS, 1.0f, 1.0f, pos);
    }

    private void playGlobalSound(ComponentState state, ServerCircuit circuit, ComponentPos pos) {
        World world = circuit.getLevel();
        if (world == null) return; // Prevent crashes

        int soundIndex = state.get(SOUND_INDEX);
        SoundEvent sound = SOUND_LIST[soundIndex];

        // ✅ TODO: Play the sound globally at the block position
        world.playSound(null, pos.getX(), pos.getY(), pos.getZ(), sound, SoundCategory.BLOCKS, 1.0f, 1.0f);
    }

    @Override
    public boolean isSolidBlock(Circuit circuit, ComponentPos pos) {
        return false;
    }

    @Override
    public void appendProperties(ComponentState.PropertyBuilder builder) {
        builder.append(SOUND_INDEX);
    }
}
