#!/bin/bash

rm -Rfv include
rm -Rfv src
rm -Rfv cmake
rm -v CMakeLists.txt
rm -v libADLMIDIConfig.cmake.in

cp -av ../../../../../libADLMIDI/include .
cp -av ../../../../../libADLMIDI/src .
cp -v ../../../../../libADLMIDI/CMakeLists.txt .
cp -v ../../../../../libADLMIDI/libADLMIDIConfig.cmake.in .

mkdir -p ./cmake
cp -av ../../../../../libADLMIDI/cmake/checks ./cmake/

echo "Press any key..."
read -n 1
