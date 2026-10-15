import pathlib
import sys

source = pathlib.Path(sys.argv[1])
configuration = source / "skiko/buildSrc/src/main/kotlin/tasks/configuration/JvmTasksConfiguration.kt"
text = configuration.read_text()
original = '''                // Hack to fix problem with linker not always finding certain declarations.
                "$skiaBinDir/libskia.a",
            )
            linker.set(project.androidClangFor(targetArch))'''
replacement = '''                // Android에서도 skottie가 참조하는 정적 라이브러리를 다시 링크한다.
                "$skiaBinDir/libsksg.a",
                "$skiaBinDir/libskia.a",
                "$skiaBinDir/libskunicode_core.a",
                "$skiaBinDir/libskunicode_icu.a",
                "$skiaBinDir/libskshaper.a",
                "-Wl,--no-undefined",
                "-Wl,-z,max-page-size=16384",
            )
            linker.set(project.androidClangFor(targetArch))'''
if text.count(original) != 1:
    raise SystemExit("Pinned Skiko Android linker configuration does not match")
configuration.write_text(text.replace(original, replacement))
