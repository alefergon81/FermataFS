cmake_minimum_required(VERSION 4.1.2)
project(whisper_jni LANGUAGES C CXX)
include(FetchContent)

if (NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif ()

if (${CMAKE_BUILD_TYPE} STREQUAL "Debug")
    set(IS_DEBUG TRUE)
    set(IS_RELEASE FALSE)
else ()
    set(IS_DEBUG FALSE)
    set(IS_RELEASE TRUE)
endif ()

set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(BUILD_SHARED_LIBS ${IS_DEBUG} CACHE BOOL "" FORCE)

set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_STATIC ${IS_RELEASE} CACHE BOOL "" FORCE)
set(GGML_LTO ${IS_RELEASE} CACHE BOOL "" FORCE)

if (CMAKE_ANDROID_ARCH_ABI MATCHES "arm64")
    set(GGML_VULKAN ON CACHE BOOL "Enable Vulkan" FORCE)
    FetchContent_Declare(vk_hpp GIT_REPOSITORY https://github.com/KhronosGroup/Vulkan-Hpp
            GIT_TAG v1.3.275
    )
    FetchContent_Declare(SPIRV-Headers GIT_REPOSITORY https://github.com/KhronosGroup/SPIRV-Headers
            GIT_TAG main
            OVERRIDE_FIND_PACKAGE
    )
    FetchContent_MakeAvailable(vk_hpp SPIRV-Headers)
    include_directories(${vk_hpp_SOURCE_DIR})
    include_directories(${SPIRV-Headers_SOURCE_DIR}/include)
    set(Vulkan_INCLUDE_DIR "${CMAKE_SYSROOT}/usr/include")
    set(Vulkan_GLSLC_EXECUTABLE "${ANDROID_NDK}/shader-tools/${ANDROID_HOST_TAG}/glslc${CMAKE_HOST_EXECUTABLE_SUFFIX}" CACHE FILEPATH "" FORCE)
    set(Vulkan_GLSLANG_VALIDATOR_EXECUTABLE "${Vulkan_GLSLC_EXECUTABLE}" CACHE FILEPATH "" FORCE)
    set(Vulkan_glslangValidator_FOUND TRUE CACHE BOOL "" FORCE)
    find_library(Vulkan_LIBRARY vulkan)

    # vulkan-shaders-gen is built for the host. On Windows ggml picks the NDK clang,
    # which targets MinGW, so use MSVC instead.
    if (CMAKE_HOST_WIN32)
        file(TO_CMAKE_PATH "${CMAKE_MAKE_PROGRAM}" HOST_MAKE_PROGRAM)
        configure_file(${CMAKE_CURRENT_SOURCE_DIR}/host-msvc.cmake.in
                ${CMAKE_BINARY_DIR}/host-msvc.cmake @ONLY)
        set(GGML_VULKAN_SHADERS_GEN_TOOLCHAIN ${CMAKE_BINARY_DIR}/host-msvc.cmake
                CACHE FILEPATH "" FORCE)
        # The default ExternalProject dir, nested under _deps/whispercpp-build/..., exceeds MAX_PATH.
        include(ExternalProject)
        set_property(DIRECTORY PROPERTY EP_BASE ${CMAKE_BINARY_DIR}/ep)
    endif ()
endif ()

find_library(LOG_LIB log)

FetchContent_Declare(
        whispercpp
        GIT_REPOSITORY https://github.com/ggml-org/whisper.cpp.git
        GIT_TAG v1.9.4
)
FetchContent_MakeAvailable(whispercpp)

add_library(whisper_jni SHARED whisper_jni.cpp)
target_link_libraries(whisper_jni PRIVATE whisper ${LOG_LIB})
target_include_directories(whisper_jni PRIVATE ${whispercpp_SOURCE_DIR})

if (CMAKE_ANDROID_ARCH_ABI MATCHES "arm64")
    target_compile_options(whisper_jni PRIVATE -march=armv8.2-a+fp16+simd+dotprod)
    target_link_libraries(whisper_jni PRIVATE ${VULKAN_LIB})
elseif (CMAKE_ANDROID_ARCH_ABI STREQUAL "x86")
    set(GGML_AVX2 OFF CACHE BOOL "" FORCE)
    set(GGML_BMI2 OFF CACHE BOOL "" FORCE)
endif ()

if (IS_DEBUG)
    target_compile_options(whisper_jni PRIVATE -g -O0 -fno-omit-frame-pointer)
else ()
    target_compile_options(whisper_jni PRIVATE -O3 -ffast-math -flto)
    target_compile_options(whisper_jni PRIVATE -fvisibility=hidden -fvisibility-inlines-hidden)
    target_compile_options(whisper_jni PRIVATE -ffunction-sections -fdata-sections)

    target_link_options(whisper_jni PRIVATE -flto)
    target_link_options(whisper_jni PRIVATE -Wl,--gc-sections)
    target_link_options(whisper_jni PRIVATE -Wl,--exclude-libs,ALL)
endif ()
