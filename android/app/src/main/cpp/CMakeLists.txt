cmake_minimum_required(VERSION 3.22)
project(mnn_jni)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Paths supplied by the CI build script via -D flags
set(MNN_INCLUDE_DIR     "" CACHE PATH "MNN core include directory (contains MNN/MNNDefine.h)")
set(MNN_LLM_INCLUDE_DIR "" CACHE PATH "MNN-LLM include directory (contains llm.hpp)")
set(MNN_LIB_DIR         "" CACHE PATH "Directory containing pre-built libMNN.so / libmnn_llm.so")

add_library(mnn_jni SHARED mnn_jni.cpp)

target_include_directories(mnn_jni PRIVATE
    ${MNN_INCLUDE_DIR}
    ${MNN_LLM_INCLUDE_DIR}
)

# Import pre-built MNN core shared library (always required)
add_library(MNN SHARED IMPORTED)
set_target_properties(MNN PROPERTIES
    IMPORTED_LOCATION "${MNN_LIB_DIR}/libMNN.so"
)

target_link_libraries(mnn_jni
    MNN
    android
    log
)

# libmnn_llm.so is optional: in some MNN builds the LLM symbols are
# embedded directly into libMNN.so rather than shipped as a separate file.
if(EXISTS "${MNN_LIB_DIR}/libmnn_llm.so")
    add_library(mnn_llm SHARED IMPORTED)
    set_target_properties(mnn_llm PROPERTIES
        IMPORTED_LOCATION "${MNN_LIB_DIR}/libmnn_llm.so"
    )
    target_link_libraries(mnn_jni mnn_llm)
    message(STATUS "mnn_llm: linking against ${MNN_LIB_DIR}/libmnn_llm.so")
else()
    message(STATUS "mnn_llm: not found as separate .so – assuming symbols are in libMNN.so")
endif()
