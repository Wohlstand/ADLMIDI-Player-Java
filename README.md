# ADLMIDI-Player-Java
Implementation of ADLMIFI based MIDI-player for Android

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
