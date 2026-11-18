cmake_minimum_required(VERSION 3.22)

project(lame_native C)

add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../../../../lib/lame ${CMAKE_CURRENT_BINARY_DIR}/lame)

add_library(lame_native SHARED lame_native.c)

target_compile_options(lame_native PRIVATE -O3 -Wall -Wextra)
target_link_libraries(lame_native PRIVATE mp3lame)
target_link_options(lame_native PRIVATE -Wl,--gc-sections)
