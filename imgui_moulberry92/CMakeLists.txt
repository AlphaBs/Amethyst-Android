cmake_minimum_required(VERSION 3.22.1)
project(imgui_moulberry92 LANGUAGES CXX)

if(NOT EXISTS "${IMGUI_JNI_DIR}/imgui.cpp")
    message(FATAL_ERROR "Generate the pinned Moulberry JNI sources before configuring CMake")
endif()

file(GLOB IMGUI_SOURCES CONFIGURE_DEPENDS "${IMGUI_JNI_DIR}/*.cpp")
add_library(imgui-moulberry92 SHARED ${IMGUI_SOURCES})
target_include_directories(imgui-moulberry92 PRIVATE "${IMGUI_JNI_DIR}")
target_compile_features(imgui-moulberry92 PRIVATE cxx_std_17)
target_link_options(imgui-moulberry92 PRIVATE "-Wl,-z,max-page-size=16384")

# 이 JNI는 Android ART가 아닌 게임의 OpenJDK 21에 로딩된다.
# NDK jni.h에 없는 표준 JNI 1.8 버전 상수를 GetEnv 요청에 제공한다.
target_compile_definitions(imgui-moulberry92 PRIVATE JNI_VERSION_1_8=0x00010008)
