package net.kdt.pojavlaunch.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

public final class GameNativeLibraries {
    private GameNativeLibraries() {
    }

    public static File directory(File privateDirectory, String abi) {
        return new File(privateDirectory, "game-natives/" + abi);
    }

    public static File prepare(
            File gameDirectory,
            File privateDirectory,
            String abi
    ) throws IOException {
        File sourceDirectory = new File(gameDirectory, "native/" + abi);
        File[] libraries;
        if (!sourceDirectory.exists()) {
            libraries = new File[0];
        } else {
            libraries = sourceDirectory.listFiles(file -> file.isFile()
                    && file.getName().endsWith(".so"));
            if (libraries == null) {
                throw new IOException("Cannot list game native libraries: " + sourceDirectory);
            }
        }
        Arrays.sort(libraries, Comparator.comparing(File::getName));

        // 공유 저장소의 파일을 직접 실행 매핑하지 않고 앱 내부 복사본을 사용한다.
        File destinationDirectory = directory(privateDirectory, abi);
        Files.createDirectories(destinationDirectory.toPath());
        Set<String> currentNames = new HashSet<>();
        for (File source : libraries) {
            copyLibrary(source, new File(destinationDirectory, source.getName()));
            currentNames.add(source.getName());
        }

        // 다른 모드팩이나 이전 실행에 있던 파일이 검색 경로에 남지 않게 한다.
        File[] previousLibraries = destinationDirectory.listFiles();
        if (previousLibraries == null) {
            throw new IOException("Cannot list staged native libraries: " + destinationDirectory);
        }
        for (File previous : previousLibraries) {
            if (previous.isFile()
                    && previous.getName().endsWith(".so")
                    && !currentNames.contains(previous.getName())) {
                Files.delete(previous.toPath());
            }
        }
        return destinationDirectory;
    }

    private static void copyLibrary(File source, File destination) throws IOException {
        File temporary = File.createTempFile("native-", ".so", destination.getParentFile());
        try {
            Files.copy(
                    source.toPath(),
                    temporary.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
            );
            // 이미 매핑된 파일을 덮어쓰지 않고 다음 실행에 사용할 파일을 교체한다.
            Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } finally {
            Files.deleteIfExists(temporary.toPath());
        }
    }
}
