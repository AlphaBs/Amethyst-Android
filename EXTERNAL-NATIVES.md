# External game natives

Moulberry ImGui 90/92 and Android Skiko native builds are maintained in the public [namu-android-prebuilt-libraries repository](https://github.com/NamuLauncher/namu-android-prebuilt-libraries). See its BUILD.md for pinned sources, rebuilding and licenses.

Place the game folder in app-internal storage and the required files in `<game>/native/<ABI>/*.so`. The launcher loads these files directly and adds this directory to JVM and Android linker search paths. It does not copy, stage or clean up native files. Nested folders and other games are not searched. The host must finish file synchronization before starting the game.

Supported ABI names are `arm64-v8a` and `x86_64`. Axiom uses `libimgui-moulberry92.so`; Flashback uses `libimgui-moulberry90.so`. Both can be present. These versioned libraries are not bundled in the launcher AAR or APK. The existing upstream `libimgui-java.so` is a separate dependency and remains unchanged.

The external native loader and the versioned JVM properties remain part of this runtime. Mod bytecode patches remain in MioLibPatcher. Server-specific downloading and synchronization belong to the host application.

This source relocation does not fix the known ImGui 92 font-buffer lifetime issue.

Skiko build scripts, compatibility JNI source and CI also live in that prebuilt repository. Skiko binaries are external game files named `libskiko-linux-arm64.so` or `libskiko-linux-x64.so`; the binaries target Android despite the loader-compatible filenames. This runtime exposes the game-specific native directory through `gameNativeDirectory(File gameDirectory)` and uses it directly. The host selects Skiko files and supplies `skiko.library.path` and `amethyst.android.systemTheme` through the generic JVM-argument parameter of `prepareGame`. Skiko is not bundled in the launcher AAR or APK.
