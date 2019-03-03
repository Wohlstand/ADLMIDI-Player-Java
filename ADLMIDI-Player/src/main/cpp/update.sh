#!/bin/bash

rm -Rfv include
rm -Rfv src
rm -v CMakeLists.txt

cp -av /home/vitaly/_git_repos/libADLMIDI/include .
cp -av /home/vitaly/_git_repos/libADLMIDI/src .
cp -v /home/vitaly/_git_repos/libADLMIDI/CMakeLists.txt .

read -n 1

