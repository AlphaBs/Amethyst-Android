# Amethyst Android runtime library

This fork converts `app_pojavlauncher` to an Android library. The public host boundary is `net.kdt.pojavlaunch.runtime.AmethystRuntime`. Upstream Java/JNI implementation and LGPL licensing are retained.

A host application uses `PojavApplication`, calls `initialize`, signs in with `openAccount`, refreshes the session, supplies its game directory/version/renderer to `prepareGame`, then calls `launchGame`. The host owns server metadata and file synchronization. The game runs in its own `:game` process.

Build with JDK 17, Android SDK 35, NDK 27.3.13750724 and CMake 3.22.1. Default ABIs are arm64-v8a and x86_64; set `-PruntimeAbis=x86_64` for emulator artifacts. Local bundled AARs are resolved as external flat-directory dependencies and must be provided to the consuming build as well. This is not a self-contained fat AAR.

Library extraction and runtime integration are in progress. A successful build alone does not prove game startup or LGPL relinking compliance. Hosts must provide applicable notices, library source and a verified relinking mechanism/materials when distributing a combined application.

Hosts may explicitly create an offline profile for local singleplayer testing with `createOfflineAccount`. This does not produce a Microsoft session. The host must keep offline singleplayer arguments separate from authenticated multiplayer. `prepareGame` downloads public version dependencies for the selected profile without requiring the upstream launcher home screen.
