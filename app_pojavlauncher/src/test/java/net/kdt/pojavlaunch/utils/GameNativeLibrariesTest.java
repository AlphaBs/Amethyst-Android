package net.kdt.pojavlaunch.utils;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

import java.io.File;
import java.nio.file.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class GameNativeLibrariesTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void externalReplacementDoesNotModifyPreviouslyOpenedFile() throws Exception {
        File game = temporaryFolder.newFolder("game");
        File privateDirectory = temporaryFolder.newFolder("private");
        File source = new File(game, "native/arm64-v8a/libimgui-moulberry92.so");
        Files.createDirectories(source.getParentFile().toPath());
        Files.write(source.toPath(), new byte[] {1, 2, 3});

        File directory = GameNativeLibraries.prepare(game, privateDirectory, "arm64-v8a");
        File loaded = new File(directory, source.getName());
        assertNotEquals(source, loaded);
        assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(loaded.toPath()));

        try (java.io.InputStream previous = Files.newInputStream(loaded.toPath())) {
            Files.write(source.toPath(), new byte[] {4, 5, 6});
            File replacementDirectory = GameNativeLibraries.prepare(
                    game,
                    privateDirectory,
                    "arm64-v8a"
            );
            File replacement = new File(replacementDirectory, source.getName());
            assertArrayEquals(new byte[] {4, 5, 6}, Files.readAllBytes(replacement.toPath()));
            assertEquals(1, previous.read());
        }
    }

    @Test
    public void missingLibraryDoesNotReusePreviouslyStagedCopy() throws Exception {
        File game = temporaryFolder.newFolder("game");
        File privateDirectory = temporaryFolder.newFolder("private");
        File source = new File(game, "native/arm64-v8a/libimgui-moulberry92.so");
        Files.createDirectories(source.getParentFile().toPath());
        Files.write(source.toPath(), new byte[] {1});
        GameNativeLibraries.prepare(game, privateDirectory, "arm64-v8a");
        Files.delete(source.toPath());

        File directory = GameNativeLibraries.prepare(game, privateDirectory, "arm64-v8a");
        File resolved = new File(directory, source.getName());
        assertFalse(resolved.exists());
    }

    @Test
    public void copiesAllSharedLibrariesWithoutRecursion() throws Exception {
        File game = temporaryFolder.newFolder("game");
        File privateDirectory = temporaryFolder.newFolder("private");
        File directory = new File(game, "native/x86_64");
        Files.createDirectories(directory.toPath());
        Files.write(new File(directory, "libfirst.so").toPath(), new byte[] {1});
        Files.write(new File(directory, "libsecond.so").toPath(), new byte[] {2});
        Files.write(new File(directory, "notice.txt").toPath(), new byte[] {3});
        File nested = new File(directory, "nested/libnested.so");
        Files.createDirectories(nested.getParentFile().toPath());
        Files.write(nested.toPath(), new byte[] {4});

        File resolved = GameNativeLibraries.prepare(game, privateDirectory, "x86_64");
        assertEquals(2, resolved.list().length);
        assertArrayEquals(new byte[] {1}, Files.readAllBytes(new File(resolved, "libfirst.so").toPath()));
        assertArrayEquals(new byte[] {2}, Files.readAllBytes(new File(resolved, "libsecond.so").toPath()));
    }

    @Test
    public void missingSourceDirectoryClearsPreviousGameLibraries() throws Exception {
        File firstGame = temporaryFolder.newFolder("first-game");
        File secondGame = temporaryFolder.newFolder("second-game");
        File privateDirectory = temporaryFolder.newFolder("private");
        File source = new File(firstGame, "native/arm64-v8a/libexample.so");
        Files.createDirectories(source.getParentFile().toPath());
        Files.write(source.toPath(), new byte[] {1});
        GameNativeLibraries.prepare(firstGame, privateDirectory, "arm64-v8a");

        File resolved = GameNativeLibraries.prepare(secondGame, privateDirectory, "arm64-v8a");
        assertEquals(0, resolved.list().length);
    }

    @Test
    public void otherAbiDoesNotProvideMissingLibrary() throws Exception {
        File game = temporaryFolder.newFolder("game");
        File privateDirectory = temporaryFolder.newFolder("private");
        File other = new File(game, "native/x86_64/libimgui-moulberry90.so");
        Files.createDirectories(other.getParentFile().toPath());
        Files.write(other.toPath(), new byte[] {1});

        File resolved = GameNativeLibraries.prepare(game, privateDirectory, "arm64-v8a");
        assertEquals(0, resolved.list().length);
    }
}
