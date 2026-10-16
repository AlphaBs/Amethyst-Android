# Amethyst Android runtime library

This fork converts `app_pojavlauncher` to an Android library. The public host boundary is `net.kdt.pojavlaunch.runtime.AmethystRuntime`. Upstream Java/JNI implementation and LGPL licensing are retained.

A host application uses `PojavApplication`, calls `initialize`, signs in with `openAccount`, refreshes the session, supplies its game directory/version/renderer to `prepareGame`, then calls `launchGame`. The host owns server metadata and file synchronization. The game runs in its own `:game` process.

Build with JDK 17, Android SDK 35, NDK 27.3.13750724 and CMake 3.22.1. Default ABIs are arm64-v8a and x86_64; set `-PruntimeAbis=x86_64` for emulator artifacts. Local bundled AARs are resolved as external flat-directory dependencies and must be provided to the consuming build as well. This is not a self-contained fat AAR.

Library extraction and runtime integration are in progress. A successful build alone does not prove game startup or LGPL relinking compliance. Hosts must provide applicable notices, library source and a verified relinking mechanism/materials when distributing a combined application.

Hosts may explicitly create an offline profile for local singleplayer testing with `createOfflineAccount`. This does not produce a Microsoft session. The host must keep offline singleplayer arguments separate from authenticated multiplayer. `prepareGame` downloads public version dependencies for the selected profile without requiring the upstream launcher home screen.

## Microsoft Device Code sign-in

Call `startDeviceCodeLogin(Context, DeviceCodeListener)` to start authentication without opening an Activity. The listener receives the user code, verification URI, expiry, Minecraft authentication stage and completion/failure on the main thread. The library polls for authorization, exchanges tokens through Xbox Live/XSTS and Minecraft services, saves the account and selects it on success. Hosts own all Device Code UI, browser opening, clipboard actions and error presentation. Keep the returned `LoginRequest` and call `cancel()` when dismissing the UI or destroying its owner. Browser switching alone should not cancel authentication. Hosts continue to use `accountName` and `refreshAccount`; FISH/server policies are not involved. `openAccount` retains the upstream WebView flow for existing callers.

This uses the existing upstream Xbox OAuth client ID and scope with `login.live.com/oauth20_connect.srf` and `oauth20_token.srf`. Device grants and refresh grants use the same client/scope. No client secret is embedded. The existing client was checked against Microsoft's live device endpoint: code issuance succeeded. End-to-end account authorization/refresh still requires a user with Minecraft Java Edition.

The worker respects the server interval, adds five seconds after `slow_down`, backs off on connection timeout, and stops on expiry, denial or cancellation. Cancelling the request stops polling and suppresses subsequent callbacks. No Device Code Activity or UI is provided by this library. Codes/tokens and token request bodies are not logged. The displayed code is transient and is never stored in the account file. Unit tests use a local HTTP server and a virtual clock, without real accounts.

Protocol references: [Microsoft device authorization](https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-device-code), [RFC 8628](https://www.rfc-editor.org/rfc/rfc8628#section-3.5).
