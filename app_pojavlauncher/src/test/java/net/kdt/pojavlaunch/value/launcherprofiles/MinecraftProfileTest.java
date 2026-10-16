package net.kdt.pojavlaunch.value.launcherprofiles;

import static org.junit.Assert.assertArrayEquals;

import com.google.gson.Gson;

import org.junit.Test;

public class MinecraftProfileTest {
    @Test
    public void hostJvmArgumentsSurviveProfilePersistenceWithoutSplittingPaths() {
        MinecraftProfile profile = new MinecraftProfile();
        profile.extraJvmArguments = new String[] {
                "-Dskiko.library.path=/private files/game-natives/arm64-v8a",
                "-Damethyst.android.systemTheme=dark"
        };
        Gson gson = new Gson();
        MinecraftProfile restored = gson.fromJson(gson.toJson(profile), MinecraftProfile.class);
        assertArrayEquals(profile.extraJvmArguments, restored.extraJvmArguments);
    }

    @Test
    public void copiedProfilesDoNotShareMutableHostJvmArguments() {
        MinecraftProfile profile = new MinecraftProfile();
        profile.extraJvmArguments = new String[] {"-Dexample=original"};
        MinecraftProfile copied = new MinecraftProfile(profile);
        copied.extraJvmArguments[0] = "-Dexample=changed";
        assertArrayEquals(new String[] {"-Dexample=original"}, profile.extraJvmArguments);
    }
}
