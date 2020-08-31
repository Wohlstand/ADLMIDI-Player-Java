/*
 * libADLMIDI is a free Software MIDI synthesizer library with OPL3 emulation
 *
 * Original ADLMIDI code: Copyright (c) 2010-2014 Joel Yliluoma <bisqwit@iki.fi>
 * ADLMIDI Library API:   Copyright (c) 2015-2020 Vitaly Novichkov <admin@wohlnet.ru>
 *
 * Library is based on the ADLMIDI, a MIDI player for Linux and Windows with OPL3 emulation:
 * http://iki.fi/bisqwit/source/adlmidi.html
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

#ifndef ADLMIDI_HPP
#define ADLMIDI_HPP

#include "adlmidi.h"

struct ADL_MIDIPlayer;

class ADLMIDI_DECLSPEC AdlInstrumentTester
{
    struct Impl;
    Impl *p;

public:
    explicit AdlInstrumentTester(ADL_MIDIPlayer *device);
    virtual ~AdlInstrumentTester();

    void start();

    // Find list of adlib instruments that supposedly implement this GM
    void FindAdlList();
    void DoNote(int note);
    void DoNoteOff();
    void NextGM(int offset);
    void NextAdl(int offset);
    bool HandleInputChar(char ch);

private:
    AdlInstrumentTester(const AdlInstrumentTester &);
    AdlInstrumentTester &operator=(const AdlInstrumentTester &);
};

#endif //ADLMIDI_HPP

