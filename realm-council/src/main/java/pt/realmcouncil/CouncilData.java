package pt.realmcouncil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import pt.realmcouncil.domain.CouncilState;
public final class CouncilData extends SavedData {
    public CouncilState state=new CouncilState();
    public static CouncilData get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(CouncilData::new,CouncilData::load),"realmcouncil_elections");}
    public static CouncilData load(CompoundTag tag,HolderLookup.Provider lookup){var d=new CouncilData();d.state=CouncilState.fromJson(tag.getTagType("council")==7?new String(tag.getByteArray("council"),java.nio.charset.StandardCharsets.UTF_8):tag.getString("council"));return d;}
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider lookup){tag.putByteArray("council",state.toJson().getBytes(java.nio.charset.StandardCharsets.UTF_8));return tag;}
}
