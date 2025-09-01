#!/bin/bash

rm -Rfv include
rm -Rfv src
rm -Rfv cmake
rm -v CMakeLists.txt
rm -v libADLMIDIConfig.cmake.in

cp -av /home/vitaly/_git_repos/libADLMIDI/include .
cp -av /home/vitaly/_git_repos/libADLMIDI/src .
cp -v /home/vitaly/_git_repos/libADLMIDI/CMakeLists.txt .
cp -v /home/vitaly/_git_repos/libADLMIDI/libADLMIDIConfig.cmake.in .

mkdir -p ./cmake
cp -av /home/vitaly/_git_repos/libADLMIDI/cmake/checks ./checks

echo "Press any key..."
read -n 1
