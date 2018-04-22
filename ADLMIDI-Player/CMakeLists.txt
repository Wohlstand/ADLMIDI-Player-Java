# Sets the minimum version of CMake required to build the native
# library. You should either keep the default value or only pass a
# value of 3.4.0 or lower.

cmake_minimum_required(VERSION 3.4.1)

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds it for you.
# Gradle automatically packages shared libraries with your APK.

add_definitions(-DADLMIDI_DISABLE_CPP_EXTRAS)
add_definitions(-DADLMIDI_USE_DOSBOX_OPL)

set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} -Os -s -fdata-sections -ffunction-sections -Wl,--gc-sections -Wl,-s")
set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -Os -s -fdata-sections -ffunction-sections -Wl,--gc-sections -Wl,-s")
set(LINK_FLAGS_RELEASE "${LINK_FLAGS_RELEASE} -Wl,--gc-sections -Wl,-s")
set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} -fno-omit-frame-pointer")
set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -fno-omit-frame-pointer")

add_library( # Sets the name of the library.
             native-lib

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
             # Associated headers in the same location as their source
             # file are automatically included.
             src/main/cpp/src/adldata.cpp
             src/main/cpp/src/adlmidi.cpp
             src/main/cpp/src/adlmidi_load.cpp
             src/main/cpp/src/adlmidi_midiplay.cpp
             src/main/cpp/src/adlmidi_opl3.cpp
             src/main/cpp/src/adlmidi_private.cpp
             src/main/cpp/src/adlmidi_xmi2mid.c
             src/main/cpp/src/adlmidi_mus2mid.c
             src/main/cpp/src/wopl/wopl_file.c
             src/main/cpp/src/chips/opl_chip_base.cpp
             src/main/cpp/src/chips/nuked_opl3.cpp
             src/main/cpp/src/chips/nuked_opl3_v174.cpp
             src/main/cpp/src/chips/dosbox_opl3.cpp
             src/main/cpp/src/chips/dosbox/dbopl.cpp
             src/main/cpp/src/chips/nuked/nukedopl3.c
             src/main/cpp/src/chips/nuked/nukedopl3_174.c
             src/main/cpp/native-lib.cpp )

target_include_directories(native-lib PUBLIC src/main/cpp/include)

# Searches for a specified prebuilt library and stores the path as a
# variable. Because system libraries are included in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

find_library( # Sets the name of the path variable.
              log-lib
              # Specifies the name of the NDK library that
              # you want CMake to locate.
              log )

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in the
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
                       native-lib
                       # Links the target library to the log library
                       # included in the NDK.
                       ${log-lib}
                       OpenSLES )

# target_link_libraries( native-audio-jni android log OpenSLES)

