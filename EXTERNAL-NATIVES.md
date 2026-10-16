# External game natives

Moulberry ImGui 90/92 sources and native builds are maintained in the public [namu-android-prebuilt-libraries repository](https://github.com/NamuLauncher/namu-android-prebuilt-libraries). See its BUILD.md for pinned sources, rebuilding and licenses.

Place the required files in `<game>/native/<ABI>/*.so`. The launcher copies files from the current ABI directory to private `game-natives/<ABI>/` storage and loads that copy. It removes staged files that are absent from the current game folder. Nested folders are not scanned.

Supported ABI names are `arm64-v8a` and `x86_64`. Axiom uses `libimgui-moulberry92.so`; Flashback uses `libimgui-moulberry90.so`. Both can be present. These versioned libraries are not bundled in the launcher AAR or APK. The existing upstream `libimgui-java.so` is a separate dependency and remains unchanged.

The external native loader and the versioned JVM properties remain part of this runtime. Mod bytecode patches remain in MioLibPatcher. Server-specific downloading and synchronization belong to the host application.

This source relocation does not fix the known ImGui 92 font-buffer lifetime issue.
