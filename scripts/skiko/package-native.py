import hashlib
import json
import os
import pathlib
import shutil
import subprocess
import sys

source = pathlib.Path(sys.argv[1])
arch = os.environ["NATIVE_ARCH"]
abi = os.environ["NATIVE_ABI"]
library = source / f"skiko/build/out/link/Release-android-jvm-{arch}/libskiko-android-{arch}.so"
if not library.is_file():
    raise SystemExit(f"Native output missing: {library}")
undefined = subprocess.check_output(["nm", "-D", "--undefined-only", library], text=True)
if "_ZN4sksg" in undefined or "_ZTVN4sksg" in undefined:
    raise SystemExit("Android binary still has unresolved Skia Scene Graph symbols")
symbols = subprocess.check_output(["nm", "-D", "--defined-only", library], text=True)
for symbol in (
    "Java_org_jetbrains_skia_DirectContextKt__1nMakeGL",
    "Java_org_jetbrains_skia_ImageFilterKt__1nMakeBlur",
    "Java_org_jetbrains_skiko_SystemThemeHelper_getCurrentSystemTheme",
):
    if symbol not in symbols:
        raise SystemExit(f"Required JNI symbol missing: {symbol}")

output = pathlib.Path("skiko-native")
destination = output / "native" / abi / f"libskiko-linux-{arch}.so"
destination.parent.mkdir(parents=True, exist_ok=True)
# 데스크톱 바인딩이 기대하는 파일명으로 제공하되 내용은 Android용 JNI다.
shutil.copyfile(library, destination)
for name in ("LICENSE", "NOTICE"):
    shutil.copyfile(source / name, output / name)
# Skia 패키지가 제공하는 원본 및 서드파티 라이선스 고지를 함께 보관한다.
for path in (source / "skiko/dependencies/skia").rglob("*"):
    if path.is_file() and any(word in path.name.lower() for word in ("license", "notice")):
        target = output / "licenses" / path.relative_to(source)
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(path, target)
metadata = {
    "skikoVersion": "0.9.22.2",
    "sourceCommit": "2f621572270c28052549ff4678b762c3fea09fa5",
    "skiaVersion": "m132-a00c390e98-1",
    "ndkVersion": "27.3.13750724",
    "abi": abi,
    "sha256": hashlib.sha256(destination.read_bytes()).hexdigest(),
    "androidBinary": True,
}
(output / "build-info.json").write_text(json.dumps(metadata, indent=2) + "\n")
print("Verified Android Skiko:", destination)

compatibility = pathlib.Path(__file__).parent
rebuild = output / "compatibility-source"
rebuild.mkdir(exist_ok=True)
for name in ("AndroidSystemTheme.cc", "patch-linker.py", "package-native.py", "README.md"):
    shutil.copyfile(compatibility / name, rebuild / name)
shutil.copyfile(compatibility.parent.parent / "LICENSE", rebuild / "LICENSE-LGPL-3.0")
