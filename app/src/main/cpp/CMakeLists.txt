cmake_minimum_required(VERSION 3.22.1)

project("astro_native")

add_library(
    astro_native
    SHARED
    star_detector.cpp
    homography_solver.cpp
    native_lib.cpp
)

find_library(
    log-lib
    log
)

find_library(
    jnigraphics-lib
    jnigraphics
)

target_include_directories(astro_native PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

target_link_libraries(
    astro_native
    ${log-lib}
    ${jnigraphics-lib}
    m
)
