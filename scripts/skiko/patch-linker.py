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

# 네이티브 빌드에 필요 없는 과거 사내 배포 플러그인은 현재 접근되지 않는 저장소를 참조한다.
settings = source / "skiko/settings.gradle.kts"
text = settings.read_text()
text = text.replace('            maven("https://maven.pkg.jetbrains.space/public/p/compose/internal")\n', '')
text = text.replace('            maven("https://maven.pkg.jetbrains.space/public/p/space/maven")\n', '')
text = text.replace('            classpath("org.jetbrains.compose.internal.build-helpers:publishing:0.1.18")\n', '')
text = text.replace('include("ci")\n', '')
settings.write_text(text)

build = source / "skiko/build.gradle.kts"
text = build.read_text()
text = text.replace('import org.jetbrains.compose.internal.publishing.MavenCentralProperties\n', '')
start = text.index('val mavenCentral = MavenCentralProperties(project)')
end = text.index('tasks.withType<AbstractTestTask>', start)
text = text[:start] + text[end:]
build.write_text(text)

# AWT 바인딩의 시스템 테마 조회를 Android 런처가 제공한 값에 연결한다.
compatibility = pathlib.Path(__file__).with_name("AndroidSystemTheme.cc")
destination = source / "skiko/src/awtMain/cpp/android/AndroidSystemTheme.cc"
destination.parent.mkdir(parents=True, exist_ok=True)
destination.write_bytes(compatibility.read_bytes())
