#!/bin/bash

rm -Rfv include
rm -Rfv src
rm -v CMakeLists.txt
rm -v libADLMIDIConfig.cmake.in

cp -av /home/vitaly/_git_repos/libADLMIDI/include .
cp -av /home/vitaly/_git_repos/libADLMIDI/src .
cp -v /home/vitaly/_git_repos/libADLMIDI/CMakeLists.txt .
cp -v /home/vitaly/_git_repos/libADLMIDI/libADLMIDIConfig.cmake.in .

read -n 1
