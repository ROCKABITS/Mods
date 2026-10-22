package pt.seasoneight.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import pt.seasoneight.SeasonConfig;

public final class WindowBranding {
    public static boolean installed;
    public static void install() {
        if (!SeasonConfig.enabled(SeasonConfig.ICON)) return;
        int platform = GLFW.glfwGetPlatform();
        // GLFW does not support application icons on Wayland or Cocoa.
        if (platform != GLFW.GLFW_PLATFORM_WIN32 && platform != GLFW.GLFW_PLATFORM_X11) return;
        var allocations = new ArrayList<ByteBuffer>();
        try (var stack = MemoryStack.stackPush()) {
            var icons = GLFWImage.malloc(3, stack);
            int index = 0;
            for (int size : new int[]{16, 32, 48}) {
                try (var input = WindowBranding.class.getResourceAsStream("/assets/seasoneight/art/icon-" + size + ".png")) {
                    if (input == null) throw new java.io.IOException("Missing Season 8 icon");
                    try (var image = NativeImage.read(input)) {
                        var pixels = MemoryUtil.memAlloc(size * size * 4);
                        allocations.add(pixels);
                        pixels.asIntBuffer().put(image.getPixelsRGBA());
                        icons.position(index++).width(size).height(size).pixels(pixels);
                    }
                }
            }
            GLFW.glfwSetWindowIcon(Minecraft.getInstance().getWindow().getWindow(), icons.position(0));
            installed = true;
        } catch (Exception error) {
            LogUtils.getLogger().warn("Could not install Season 8 window icon", error);
        } finally { allocations.forEach(MemoryUtil::memFree); }
    }
    private WindowBranding() {}
}
