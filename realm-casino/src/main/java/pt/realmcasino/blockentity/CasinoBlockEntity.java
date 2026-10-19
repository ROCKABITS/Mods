package pt.realmcasino.blockentity;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import pt.realmcasino.block.CasinoMachineBlock;

/** Public animation display only: never a bank, random source or card shoe. */
public final class CasinoBlockEntity extends BlockEntity {
    private UUID animationId = new UUID(0, 0);
    private long startedAt;
    private int[] result = new int[0];
    private long payout;
    private boolean active;

    public CasinoBlockEntity(BlockPos pos, BlockState state) { super(CasinoBlockEntities.MACHINE.get(), pos, state); }

    public UUID animationId() { return animationId; }
    public String game() { return getBlockState().getBlock() instanceof CasinoMachineBlock block ? block.game() : "slots"; }
    public long startedAt() { return startedAt; }
    public int[] result() { return result.clone(); }
    public long payout() { return payout; }
    public boolean active() { return active; }

    /** Called by the authoritative service only after the round is committed. */
    public void show(UUID round, String game, long startedAt, int[] result, long payout, boolean active) {
        if (getLevel() == null || getLevel().isClientSide || !getLevel().getServer().isSameThread()) {
            throw new IllegalStateException("Casino displays are updated on the server thread");
        }
        if (round == null || !game().equals(game) || startedAt < 0 || payout < 0 || !validResults(game, result)) {
            throw new IllegalArgumentException("Invalid public casino display");
        }
        this.animationId = round;
        this.startedAt = startedAt;
        this.result = result.clone();
        this.payout = payout;
        this.active = active;
        setChanged();
        getLevel().sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    private static boolean validResults(String game, int[] values) {
        if (values == null) return false;
        return switch (game) {
            case "slots" -> values.length == 3 && validRange(values, 0, 7);
            case "roulette" -> values.length == 1 && validRange(values, 0, 36);
            case "blackjack" -> values.length <= 24 && validRange(values, -1, 51);
            case "plinko" -> values.length == 12 && validRange(values,0,1);
            case "wheel" -> values.length == 1 && validRange(values,0,19);
            case "chicken" -> values.length <= 10 && validRange(values,0,1);
            default -> false;
        };
    }

    private static boolean validRange(int[] values, int min, int max) {
        for (int value : values) if (value < min || value > max) return false;
        return true;
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putUUID("Animation", animationId);
        tag.putLong("StartedAt", startedAt);
        tag.putIntArray("PublicResult", result);
        tag.putLong("Payout", payout);
        tag.putBoolean("Active", active);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        int[] values = tag.getIntArray("PublicResult");
        if (tag.hasUUID("Animation") && tag.getLong("StartedAt") >= 0 && tag.getLong("Payout") >= 0
                && validResults(game(), values)) {
            animationId = tag.getUUID("Animation");
            startedAt = tag.getLong("StartedAt");
            payout = tag.getLong("Payout");
            result = values;
            active = tag.getBoolean("Active");
        } else {
            animationId = new UUID(0, 0);
            startedAt = 0;
            result = new int[0];
            payout = 0;
            active = false;
        }
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    // Slot animation belongs to the lower block but extends into the upper cell.
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).expandTowards(0, getBlockState().getBlock() instanceof CasinoMachineBlock block && block.isTall() ? 1 : 0, 0);
    }
}
