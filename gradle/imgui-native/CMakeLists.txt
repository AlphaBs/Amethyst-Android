cmake_minimum_required(VERSION 3.22.1)
project(imgui_moulberry LANGUAGES CXX)

if(NOT EXISTS "${IMGUI_JNI_DIR}/imgui.cpp")
    message(FATAL_ERROR "Generate the pinned Moulberry JNI sources before configuring CMake")
endif()

file(GLOB IMGUI_SOURCES CONFIGURE_DEPENDS "${IMGUI_JNI_DIR}/*.cpp")
add_library(${IMGUI_NATIVE_NAME} SHARED ${IMGUI_SOURCES})
target_include_directories(${IMGUI_NATIVE_NAME} PRIVATE "${IMGUI_JNI_DIR}")
target_compile_features(${IMGUI_NATIVE_NAME} PRIVATE cxx_std_17)
# 서로 다른 ImGui 버전의 전역 상태와 C++ 심볼이 섞이지 않게 한다.
# JNIEXPORT 함수만 외부에 공개하고 내부 참조는 각 라이브러리 안에서 해결한다.
set_target_properties(${IMGUI_NATIVE_NAME} PROPERTIES
    CXX_VISIBILITY_PRESET hidden
    VISIBILITY_INLINES_HIDDEN YES
)
target_link_options(${IMGUI_NATIVE_NAME} PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,--exclude-libs,ALL"
    "-Wl,-Bsymbolic"
    "-Wl,--no-undefined"
)

# 이 JNI는 Android ART가 아닌 게임의 OpenJDK 21에 로딩된다.
# NDK jni.h에 없는 표준 JNI 1.8 버전 상수를 GetEnv 요청에 제공한다.
target_compile_definitions(${IMGUI_NATIVE_NAME} PRIVATE JNI_VERSION_1_8=0x00010008)
