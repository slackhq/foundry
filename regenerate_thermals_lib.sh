#!/bin/env sh

cd platforms/gradle/foundry-gradle-plugin || exit 1
swiftc thermal_state.swift -emit-library
mv -f libthermal_state.dylib src/main/resources/libthermal_state.dylib
cd ..