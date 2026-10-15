package pt.realmidentity.compat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import pt.realmidentity.domain.ProfileSnapshot;

/** Reads only the identity accessors of the original Chronicles records. */
public final class ChroniclesProfileReader {
    private static final String PROFILE = "pt.cronicasrp.domain.CharacterProfile";
    private static final String PROGRESS = "pt.cronicasrp.domain.RpProgress";
    private static final ClassValue<Api> APIS = new ClassValue<>() {
        @Override protected Api computeValue(Class<?> type) { return resolve(type); }
    };

    private ChroniclesProfileReader() {}

    public static ProfileSnapshot read(Object profile) {
        if (profile == null) throw unavailable(null);
        Api api = APIS.get(profile.getClass());
        if (!(boolean) invoke(api.created(), profile)) {
            throw new IllegalStateException("gui.realmidentity.profile_required");
        }
        Object progress = invoke(api.progress(), profile);
        if (progress == null || progress.getClass() != api.progressType()) throw unavailable(null);
        try {
            return new ProfileSnapshot(ProfileSnapshot.Source.CHRONICLES,
                    (String) invoke(api.name(), profile), (int) invoke(api.age(), profile),
                    (String) invoke(api.race(), profile), (String) invoke(api.occupation(), progress),
                    (String) invoke(api.signature(), profile), (String) invoke(api.signatureDrawing(), profile));
        } catch (IllegalArgumentException | NullPointerException failure) {
            throw unavailable(failure);
        }
    }

    private static Api resolve(Class<?> profile) {
        try {
            if (!profile.getName().equals(PROFILE) || !profile.isRecord()) throw unavailable(null);
            Class<?> progress = Class.forName(PROGRESS, false, profile.getClassLoader());
            if (!progress.isRecord()) throw unavailable(null);
            return new Api(getter(profile, "created", boolean.class), getter(profile, "name", String.class),
                    getter(profile, "age", int.class), getter(profile, "race", String.class),
                    getter(profile, "signature", String.class), getter(profile, "signatureDrawing", String.class),
                    getter(profile, "progress", progress), progress, getter(progress, "occupation", String.class));
        } catch (ReflectiveOperationException | LinkageError failure) {
            throw unavailable(failure);
        }
    }

    private static Method getter(Class<?> owner, String name, Class<?> result) throws NoSuchMethodException {
        Method method = owner.getMethod(name);
        if (method.getReturnType() != result || method.getParameterCount() != 0
                || Modifier.isStatic(method.getModifiers()) || method.getDeclaringClass() != owner
                || Arrays.stream(owner.getRecordComponents()).noneMatch(component ->
                        component.getName().equals(name) && component.getType() == result)) {
            throw unavailable(null);
        }
        return method;
    }

    private static Object invoke(Method method, Object owner) {
        try {
            return method.invoke(owner);
        } catch (InvocationTargetException failure) {
            throw unavailable(failure.getCause());
        } catch (ReflectiveOperationException | LinkageError failure) {
            throw unavailable(failure);
        }
    }

    private static IllegalStateException unavailable(Throwable cause) {
        return new IllegalStateException("gui.realmidentity.profile_unavailable", cause);
    }

    private record Api(Method created, Method name, Method age, Method race, Method signature,
                       Method signatureDrawing, Method progress, Class<?> progressType, Method occupation) {}
}
