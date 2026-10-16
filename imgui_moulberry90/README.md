# Android JNI for Flashback ImGui 1.90

Flashback 0.39.9 uses `imgui.moulberry90`, relocated from Moulberry/imgui-java
`v1.90.0` (`c80552861d5de1c929dbe3210cba8b72792e4471`).
Its 3,883 native declarations match that source after package relocation,
including the Windows-only GLFW helper moved into `moulberry90.glfw`.

The shared `gradle/imgui-native` scripts run the original JNI generator,
relocate generated native names and JNI class descriptors, and build
`libimgui-moulberry90.so` for arm64-v8a and x86_64. Generated upstream Java
is never edited. ImGui 1.90 and 1.92 have separate JNI packages and hidden
C++ symbols, with local symbol binding to isolate their native state.

The game JVM reads `amethyst.imgui.moulberry90.path` through MioLibPatcher.
Only desktop extraction is replaced; the original System.load and JNI
initialization remain. Place this library in `<game>/native/<ABI>/` alongside
`libimgui-moulberry92.so`. The runtime stages both files into private storage.
STB font rendering is enabled; FreeType is not included in this build.

Original binding and extension MIT notices are included in the AAR assets.
ABI matching and successful linking do not replace real device rendering tests.
