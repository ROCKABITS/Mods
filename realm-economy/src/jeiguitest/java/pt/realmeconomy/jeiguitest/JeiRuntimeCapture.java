package pt.realmeconomy.jeiguitest;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

/** Test-only runtime access. No JEI classes or plugin are included in the production JAR. */
@JeiPlugin
public final class JeiRuntimeCapture implements IModPlugin {
    public static volatile IJeiRuntime runtime;
    @Override public ResourceLocation getPluginUid() { return ResourceLocation.fromNamespaceAndPath("realmeconomy", "gui_regression_test"); }
    @Override public void onRuntimeAvailable(IJeiRuntime value) { runtime = value; }
    @Override public void onRuntimeUnavailable() { runtime = null; }
}
