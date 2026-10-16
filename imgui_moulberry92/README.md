# Android JNI for Axiom ImGui 1.92

Axiom 5.4.2 contains `imgui.moulberry92` from Moulberry/imgui-java tag
`v1.92-moulberry.2` (`4a2813430cc2bc64ba2106289d0c8606e656f32d`).
All 4,000 native method declarations match that tag. The existing Amethyst
`libimgui-java.so` is ImGui 1.86 and cannot substitute for this ABI.

The pinned upstream submodule and its pinned extensions provide the sources.
`gradle/imgui-native/generate-jni.gradle` invokes the original JNI generator without editing
upstream generated Java, applies the upstream extension patch, and copies
native sources with the upstream `imconfig.h`. Android CMake then builds
`libimgui-moulberry92.so` for the selected runtime ABIs. STB font rendering
is enabled; the optional FreeType backend is not included in this first build.
The native library requests 16KB ELF segment alignment.

The runtime passes `amethyst.imgui.moulberry92.path` to the game JVM.
MioLibPatcher redirects native load calls and desktop extraction in the ImGui
static initializer to that version-specific file, preserving subsequent JNI setup.
This takes precedence over the shared `imgui.library.name` setting.
Without that property the original extraction behavior remains intact.

Build the normal runtime with recursive submodules and JDK 17 (plus JDK 8 for
existing runtime tools). JNI generation uses the pinned upstream Gradle wrapper.
Android builds and device validation are required to confirm functionality;
matching JNI signatures alone does not prove rendering or input compatibility.

Upstream MIT notices remain in the source tree. The binding notice is included
in this AAR's assets. ImGui 1.86, this version, and Flashback's separate 1.90 library use distinct
file names and JNI packages. Hidden C++ visibility and local symbol binding
keep each native context independent when both Moulberry libraries are loaded.
