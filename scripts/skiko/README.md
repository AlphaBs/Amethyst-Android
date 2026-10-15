# Android Skiko 0.9.22.2

HyperLib 1.2.34 embeds the Skiko AWT 0.9.22.2 JVM classes but only ships a Windows native library. Its 924 Skia native declarations match the Android bindings of the same version.

The official Android 0.9.22.2 arm64 and x64 libraries fail Android dynamic linking with unresolved `sksg` symbols. The pinned source's Android linker repeats `libskia.a` but omits the late `libsksg.a` pass used by its Linux configuration. `patch-linker.py` adds the late Skia libraries and rejects unresolved symbols at link time. It also requests 16KB ELF segment alignment.

The `Android Skiko native libraries` workflow builds both ABIs in CI from upstream commit `2f621572270c28052549ff4678b762c3fea09fa5`, using its original Skia package `m132-a00c390e98-1`. The native-only build skips the historical internal publishing plugin and its unavailable JetBrains Space repository. Upstream generated Java and existing native function bodies are unchanged. A small LGPL-3.0 Android compatibility JNI supplies the AWT system theme getter from the launcher-provided `amethyst.android.systemTheme` property (light/dark/unknown). The artifact includes its source and license. The theme is captured at game startup. Preserve upstream Apache-2.0 and Skia notices with distribution.

Artifacts contain Android binaries at:

```text
native/arm64-v8a/libskiko-linux-arm64.so
native/x86_64/libskiko-linux-x64.so
```

The `linux` filename matches the name expected by the bundled AWT loader running on Android OpenJDK. The binary itself is compiled for Android, not glibc Linux. The runtime automatically sets `skiko.library.path` to its staged `game-natives/<ABI>` directory when the matching file exists. Do not disable the affected UI mod.

A successful build and JNI declaration match alone do not establish rendering compatibility. Validate Android loading, GL context creation and actual mod UI rendering on a device.
