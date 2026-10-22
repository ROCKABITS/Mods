# Validation — Season 8 0.1.0

## Executed locally

- Production compilation on Java 21 against Minecraft 1.21.1 and NeoForge 21.1.256.
- Two JUnit regression tests. The layout test checks all nine controls for bounds and intersections at **3,700 GUI sizes**, from 320×180 through the sampled widths/heights up to 1280×720.
- Two actual Minecraft client executions on Linux/X11 with Mesa llvmpipe: standalone, and with the **13 published ROCKABITS gameplay mods**, Origins 0.3.2, Jupiter 2.3.7 and Origins: Classes 0.3.
- Both clients completed the automated flow (`step: 18`, `success: true`), rendering and capturing the initial NeoForge resource-loading overlay and a real subsequent Minecraft resource reload. Resource completion is driven by the original loaders.
- Real mouse clicks reached world selection/creation, mod list, options, language, accessibility, credits and the account-aware multiplayer entry path. All nine original actions are present. Realms and Quit retain their original actions but are not invoked by the automated flow, to avoid remote account operations and premature shutdown.
- English and Portuguese menus, 1600×900, 854×480, 640×360, disabled background motion, and switching back to the original vanilla menu.
- Native desktop title `Season 8` checked on X11. `_NET_WM_ICON` contained the expected 16×16, 32×32 and 48×48 images. This supplements the client harness's successful GLFW icon installation.
- Seven real screenshots cover the two loading implementations, menus and small-window resource reload. An additional framebuffer check rejects old menu text showing through a loading overlay. PNG dimensions and the Season 8 border pixel are checked again during packaging to reject missing or unrelated frames.

QA failures are reported in `result.json`; a Gradle `BUILD SUCCESSFUL` alone is not accepted as a client test pass.

## Scope and limits

These runs verify presentation and navigation, and that the released companion mods can load alongside the presentation. They do not repeat each companion's gameplay/economy tests, authenticate with a Microsoft account, play on an external multiplayer server, or establish exhaustive resource-pack, shader, GPU or third-party menu compatibility.

The bootstrap window before Minecraft's renderer starts is still NeoForge's. World-generation, server-connection and in-game menus are not reskinned. Icons use GLFW's supported Windows/X11 operation; Windows was not executed here, and unsupported Wayland/macOS operations are skipped safely.

The rendering injections run after the original loading methods; they do not replace reload instances, completion/error callbacks, progress meters, framebuffers or removal of the loading overlay. The game does not wait for a decorative animation. Separate config switches allow another mod to own the menu or loading screen.

The release workflow reruns the unit tests and both actual-client modes, verifies pinned companion hashes, checks screenshots, excludes the QA source set from the production JAR, and publishes the production artifact with source provenance and hashes. CI results are recorded in the published `mod-pronto/season-eight-0.1.0.build.json` after that run; this document does not assert an unexecuted CI outcome.
