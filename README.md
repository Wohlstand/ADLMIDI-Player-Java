# ADLMIDI-Player-Java
Implementation of ADLMIDI based MIDI-player for Android

It's a MIDI-player based on emulator of a Frequency Modulation chip Yamaha OPL3. This small MIDI-player made with using of [libADLMIDI](https://github.com/Wohlstand/libADLMIDI/) library - a fork of origina ADLMIDI a console program implementation.

# Key features

* OPL3 emulation with four-operator mode support
* FM patches from a number of known PC games, copied from files typical to AIL = Miles Sound System / DMX / HMI = Human Machine Interfaces / Creative IBK.
* Stereo sound
* Number of simulated soundcards can be specified as 1-100 (maximum channels 1800!)
* Pan (binary panning, i.e. left/right side on/off)
* Pitch-bender with adjustable range
* Vibrato that responds to RPN/NRPN parameters
* Sustain enable/disable
* MIDI, RMI and Wolfinstein 3D IMF files support
* loopStart / loopEnd tag support (Final Fantasy VII)
* Use automatic arpeggio with chords to relieve channel pressure
* Support for multiple concurrent MIDI synthesizers (per-track device/port select FF 09 message), can be used to overcome 16 channel limit

# Download latest binary

https://github.com/Wohlstand/ADLMIDI-Player-Java/releases

# System requirements

* Recommended to have powerful CPU: Emulation of OPL3 chip is a sound generation by formulas which requires requires operations. For example, application works almost fine on Samsung GT-I9300 which has quad-core Exynos 4212 CPU with 1400 Mhz. On slow CPUs it may lag, for example, on LG-E612 with MSM7225A Cortex A5 with 800 Mhz.
* 4 MB free space

# How to install

* Put APK to your Android device
* Check in settings that custom applications installations are allowed
* Open APK via any file manager and confirm installation
* Allow external storage reading (needed to open MIDI-files from phone memory and SD Card)

# How to use

* Use "Open" button to open file dialog and select any MIDI, RMI, KAR or IMF (Wolfinstein 3D) file
* Setup any preferences (bank, tremolo, vibrato, number of emulated chips, etc.)
* Press "Play/Pause" to start playing or press again to pause or resume
* Press "Restart" to begin playing of music from begin (needed to apply changes of some settings)
* Use "Open" again to select any other music file
* You can switch another application or lock screen, music playing will work in background.

# Tips

* This application audio playback may lag on various devices, therefore you can reduce number of emulated chips
* If you are using "4-operators" banks, you can increase number of 4-op channels into number of chips multiped to 6 (each OPL3 chip supports maximum 6 four-operator channels)
* Set number of four-operator channels into zero if you are using 2-operator banks (most of banks are 4-operators. Pseudo-four-operator banks are 2-operator banks with double-voice support)

More detailed about playing MIDI with this application you also can find on [libADLMIDI library repo](git@github.com:Wohlstand/libADLMIDI.git)

