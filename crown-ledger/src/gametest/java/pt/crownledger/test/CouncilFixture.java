package pt.crownledger.test;

import java.lang.reflect.Field;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.fml.ModList;

/** Mutates the real optional Realm Council state, then restores its original object. */
final class CouncilFixture implements AutoCloseable {
    private final Object data;
    private final Field stateField;
    private final Object originalState;
    private final Object state;
    private Object mandate;

    static boolean loaded() { return ModList.get().isLoaded("realmcouncil"); }

    CouncilFixture(MinecraftServer server) {
        try {
            var dataClass = Class.forName("pt.realmcouncil.CouncilData");
            data = dataClass.getMethod("get", MinecraftServer.class).invoke(null, server);
            stateField = dataClass.getField("state");
            originalState = stateField.get(data);
            state = Class.forName("pt.realmcouncil.domain.CouncilState").getConstructor().newInstance();
            stateField.set(data, state);
            ((SavedData) data).setDirty();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot prepare the actual Realm Council integration", exception);
        }
    }

    void mandate(UUID player, long beginsAt, long endsAt, long claimedAt) {
        try {
            var type = Class.forName("pt.realmcouncil.domain.CouncilState$Mandate");
            mandate = type.getConstructor().newInstance();
            type.getField("electionId").set(mandate, UUID.randomUUID());
            type.getField("playerId").set(mandate, player);
            type.getField("title").set(mandate, "Mandato de teste");
            type.getField("name").set(mandate, "Governante QA");
            type.getField("beginsAt").setLong(mandate, beginsAt);
            type.getField("endsAt").setLong(mandate, endsAt);
            type.getField("claimedAt").setLong(mandate, claimedAt);
            state.getClass().getField("mandate").set(state, mandate);
            ((SavedData) data).setDirty();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot seed a real Realm Council mandate", exception);
        }
    }

    void claim(long time) { setTime("claimedAt", time); }
    void expire(long time) { setTime("endsAt", time); }

    private void setTime(String field, long time) {
        try {
            mandate.getClass().getField(field).setLong(mandate, time);
            ((SavedData) data).setDirty();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot change the real mandate's " + field, exception);
        }
    }

    @Override public void close() {
        try {
            stateField.set(data, originalState);
            ((SavedData) data).setDirty();
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot restore the original Realm Council state", exception);
        }
    }
}
