cmake_minimum_required(VERSION 3.22.1)
project(tdpl_llama)

# llama.cpp is a git submodule checked out here:
#   app/src/main/cpp/llama.cpp
# Init with: git submodule add https://github.com/ggerganov/llama.cpp app/src/main/cpp/llama.cpp
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)

add_subdirectory(llama.cpp)

add_library(tdpl_llama SHARED
    llama-bridge.cpp
)

target_include_directories(tdpl_llama PRIVATE
    llama.cpp/include
    llama.cpp/ggml/include
)

find_library(log-lib log)

target_link_libraries(tdpl_llama
    llama
    ggml
    ${log-lib}
)
