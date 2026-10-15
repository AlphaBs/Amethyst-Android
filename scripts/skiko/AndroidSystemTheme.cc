// SPDX-License-Identifier: LGPL-3.0-only
#include <jni.h>
#include <cstring>

extern "C" JNIEXPORT jint JNICALL
Java_org_jetbrains_skiko_SystemThemeHelper_getCurrentSystemTheme(JNIEnv* env, jobject) {
    jclass system = env->FindClass("java/lang/System");
    if (system == nullptr) {
        return -1;
    }
    jmethodID getProperty = env->GetStaticMethodID(
        system,
        "getProperty",
        "(Ljava/lang/String;)Ljava/lang/String;"
    );
    if (getProperty == nullptr) {
        env->DeleteLocalRef(system);
        return -1;
    }
    jstring key = env->NewStringUTF("amethyst.android.systemTheme");
    jstring value = static_cast<jstring>(env->CallStaticObjectMethod(system, getProperty, key));
    env->DeleteLocalRef(key);
    env->DeleteLocalRef(system);
    if (value == nullptr) {
        return -1;
    }

    const char* theme = env->GetStringUTFChars(value, nullptr);
    jint result = -1;
    if (theme != nullptr) {
        // Skiko의 원래 AWT 계약: 밝게 0, 어둡게 1, 알 수 없음 -1.
        if (std::strcmp(theme, "light") == 0) {
            result = 0;
        } else if (std::strcmp(theme, "dark") == 0) {
            result = 1;
        }
        env->ReleaseStringUTFChars(value, theme);
    }
    env->DeleteLocalRef(value);
    return result;
}
