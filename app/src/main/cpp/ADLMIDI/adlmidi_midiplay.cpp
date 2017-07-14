/*
 * libADLMIDI is a free MIDI to WAV conversion library with OPL3 emulation
 *
 * Original ADLMIDI code: Copyright (c) 2010-2014 Joel Yliluoma <bisqwit@iki.fi>
 * ADLMIDI Library API:   Copyright (c) 2017 Vitaly Novichkov <admin@wohlnet.ru>
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

#include "adlmidi_private.hpp"


// Mapping from MIDI volume level to OPL level value.

static const uint32_t DMX_volume_mapping_table[] =
{
    0,  1,  3,  5,  6,  8,  10, 11,
    13, 14, 16, 17, 19, 20, 22, 23,
    25, 26, 27, 29, 30, 32, 33, 34,
    36, 37, 39, 41, 43, 45, 47, 49,
    50, 52, 54, 55, 57, 59, 60, 61,
    63, 64, 66, 67, 68, 69, 71, 72,
    73, 74, 75, 76, 77, 79, 80, 81,
    82, 83, 84, 84, 85, 86, 87, 88,
    89, 90, 91, 92, 92, 93, 94, 95,
    96, 96, 97, 98, 99, 99, 100, 101,
    101, 102, 103, 103, 104, 105, 105, 106,
    107, 107, 108, 109, 109, 110, 110, 111,
    112, 112, 113, 113, 114, 114, 115, 115,
    116, 117, 117, 118, 118, 119, 119, 120,
    120, 121, 121, 122, 122, 123, 123, 123,
    124, 124, 125, 125, 126, 126, 127, 127,
    //Protection entries to avoid crash if value more than 127
    127, 127, 127, 127, 127, 127, 127, 127,
    127, 127, 127, 127, 127, 127, 127, 127,
    127, 127, 127, 127, 127, 127, 127, 127,
    127, 127, 127, 127, 127, 127, 127, 127,
    127, 127, 127, 127, 127, 127, 127, 127,
    127, 127, 127, 127, 127, 127, 127, 127,
    127, 127, 127, 127, 127, 127, 127, 127,
    127, 127, 127, 127, 127, 127, 127, 127,
    127, 127, 127, 127, 127, 127, 127, 127,
    127, 127, 127, 127, 127, 127, 127, 127,
    127, 127, 127, 127, 127, 127, 127, 127,
    127, 127, 127, 127, 127, 127, 127, 127,
    127, 127, 127, 127, 127, 127, 127, 127,
    127, 127, 127, 127, 127, 127, 127, 127,
    127, 127, 127, 127, 127, 127, 127, 127,
    127, 127, 127, 127, 127, 127, 127, 127,
};

static const uint8_t W9X_volume_mapping_table[32] =
{
    63, 63, 40, 36, 32, 28, 23, 21,
    19, 17, 15, 14, 13, 12, 11, 10,
    9,  8,  7,  6,  5,  5,  4,  4,
    3,  3,  2,  2,  1,  1,  0,  0
};


//static const char MIDIsymbols[256+1] =
//"PPPPPPhcckmvmxbd"  // Ins  0-15
//"oooooahoGGGGGGGG"  // Ins 16-31
//"BBBBBBBBVVVVVHHM"  // Ins 32-47
//"SSSSOOOcTTTTTTTT"  // Ins 48-63
//"XXXXTTTFFFFFFFFF"  // Ins 64-79
//"LLLLLLLLpppppppp"  // Ins 80-95
//"XXXXXXXXGGGGGTSS"  // Ins 96-111
//"bbbbMMMcGXXXXXXX"  // Ins 112-127
//"????????????????"  // Prc 0-15
//"????????????????"  // Prc 16-31
//"???DDshMhhhCCCbM"  // Prc 32-47
//"CBDMMDDDMMDDDDDD"  // Prc 48-63
//"DDDDDDDDDDDDDDDD"  // Prc 64-79
//"DD??????????????"  // Prc 80-95
//"????????????????"  // Prc 96-111
//"????????????????"; // Prc 112-127

static const uint8_t PercussionMap[256] =
    "\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0"//GM
    "\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0" // 3 = bass drum
    "\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0" // 4 = snare
    "\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0" // 5 = tom
    "\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0" // 6 = cymbal
    "\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0" // 7 = hihat
    "\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0"
    "\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0"
    "\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0"//GP0
    "\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0"//GP16
    //2 3 4 5 6 7 8 940 1 2 3 4 5 6 7
    "\0\0\0\3\3\7\4\7\4\5\7\5\7\5\7\5"//GP32
    //8 950 1 2 3 4 5 6 7 8 960 1 2 3
    "\5\6\5\6\6\0\5\6\0\6\0\6\5\5\5\5"//GP48
    //4 5 6 7 8 970 1 2 3 4 5 6 7 8 9
    "\5\0\0\0\0\0\7\0\0\0\0\0\0\0\0\0"//GP64
    "\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0"
    "\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0"
    "\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0";

void MIDIplay::AdlChannel::AddAge(int64_t ms)
{
    if(users.empty())
        koff_time_until_neglible =
            std::max(koff_time_until_neglible - ms, static_cast<int64_t>(-0x1FFFFFFFl));
    else
    {
        koff_time_until_neglible = 0;

        for(users_t::iterator i = users.begin(); i != users.end(); ++i)
        {
            i->second.kon_time_until_neglible =
                std::max(i->second.kon_time_until_neglible - ms, static_cast<int64_t>(-0x1FFFFFFFl));
            i->second.vibdelay += ms;
        }
    }
}

MIDIplay::MIDIplay():
    cmf_percussion_mode(false),
    config(NULL),
    trackStart(false),
    loopStart(false),
    loopEnd(false),
    loopStart_passed(false),
    invalidLoop(false),
    loopStart_hit(false)
{
    devices.clear();
}

uint64_t MIDIplay::ReadVarLen(size_t tk)
{
    uint64_t result = 0;

    for(;;)
    {
        uint8_t byte = TrackData[tk][CurrentPosition.track[tk].ptr++];
        result = (result << 7) + (byte & 0x7F);
        if(!(byte & 0x80))
            break;
    }

    return result;
}


double MIDIplay::Tick(double s, double granularity)
{
    if(CurrentPosition.began)
        CurrentPosition.wait -= s;

    int AntiFreezeCounter = 10000;//Limit 10000 loops to avoid freezing

    while((CurrentPosition.wait <= granularity * 0.5l) && (AntiFreezeCounter > 0))
    {
        //std::fprintf(stderr, "wait = %g...\n", CurrentPosition.wait);
        ProcessEvents();

        if(CurrentPosition.wait <= 0.0l)
            AntiFreezeCounter--;
    }

    if(AntiFreezeCounter <= 0)
        CurrentPosition.wait += 1.0l;/* Add extra 1 second when over 10000 events

                                           with zero delay are been detected */

    for(uint16_t c = 0; c < opl.NumChannels; ++c)
        ch[c].AddAge(static_cast<int64_t>(s * 1000.0));

    UpdateVibrato(s);
    UpdateArpeggio(s);
    return CurrentPosition.wait;
}


void MIDIplay::NoteUpdate(uint16_t MidCh,
                          MIDIplay::MIDIchannel::activenoteiterator i,
                          unsigned props_mask,
                          int32_t select_adlchn)
{
    MIDIchannel::NoteInfo &info = i->second;
    const int16_t tone    = info.tone;
    const uint8_t vol     = info.vol;
    //const int midiins = info.midiins;
    const uint32_t insmeta = info.insmeta;
    const adlinsdata &ains = opl.GetAdlMetaIns(insmeta);
    AdlChannel::Location my_loc;
    my_loc.MidCh = MidCh;
    my_loc.note  = i->first;

    for(std::map<uint16_t, uint16_t>::iterator
        jnext = info.phys.begin();
        jnext != info.phys.end();
       )
    {
        std::map<uint16_t, uint16_t>::iterator j(jnext++);
        uint16_t c   = j->first;
        uint16_t ins = j->second;

        if(select_adlchn >= 0 && c != select_adlchn) continue;

        if(props_mask & Upd_Patch)
        {
            opl.Patch(c, ins);
            AdlChannel::LocationData &d = ch[c].users[my_loc];
            d.sustained = false; // inserts if necessary
            d.vibdelay  = 0;
            d.kon_time_until_neglible = ains.ms_sound_kon;
            d.ins       = ins;
        }
    }

    for(std::map<unsigned short, unsigned short>::iterator
        jnext = info.phys.begin();
        jnext != info.phys.end();
       )
    {
        std::map<unsigned short, unsigned short>::iterator j(jnext++);
        uint16_t c   = j->first;
        uint16_t ins = j->second;

        if(select_adlchn >= 0 && c != select_adlchn) continue;

        if(props_mask & Upd_Off) // note off
        {
            if(Ch[MidCh].sustain == 0)
            {
                AdlChannel::users_t::iterator k = ch[c].users.find(my_loc);

                if(k != ch[c].users.end())
                    ch[c].users.erase(k);

                //UI.IllustrateNote(c, tone, midiins, 0, 0.0);

                if(ch[c].users.empty())
                {
                    opl.NoteOff(c);
                    ch[c].koff_time_until_neglible =
                        ains.ms_sound_koff;
                }
            }
            else
            {
                // Sustain: Forget about the note, but don't key it off.
                //          Also will avoid overwriting it very soon.
                AdlChannel::LocationData &d = ch[c].users[my_loc];
                d.sustained = true; // note: not erased!
                //UI.IllustrateNote(c, tone, midiins, -1, 0.0);
            }

            info.phys.erase(j);
            continue;
        }

        if(props_mask & Upd_Pan)
            opl.Pan(c, Ch[MidCh].panning);

        if(props_mask & Upd_Volume)
        {
            uint32_t volume;

            switch(opl.m_volumeScale)
            {
            case OPL3::VOLUME_Generic:
            case OPL3::VOLUME_CMF:
            {
                volume = vol * Ch[MidCh].volume * Ch[MidCh].expression;

                /* If the channel has arpeggio, the effective volume of
                     * *this* instrument is actually lower due to timesharing.
                     * To compensate, add extra volume that corresponds to the
                     * time this note is *not* heard.
                     * Empirical tests however show that a full equal-proportion
                     * increment sounds wrong. Therefore, using the square root.
                     */
                //volume = (int)(volume * std::sqrt( (double) ch[c].users.size() ));

                if(opl.LogarithmicVolumes)
                    volume = volume * 127 / (127 * 127 * 127) / 2;
                else
                {
                    // The formula below: SOLVE(V=127^3 * 2^( (A-63.49999) / 8), A)
                    volume = volume > 8725 ? static_cast<unsigned int>(std::log(volume) * 11.541561 + (0.5 - 104.22845)) : 0;
                    // The incorrect formula below: SOLVE(V=127^3 * (2^(A/63)-1), A)
                    //opl.Touch_Real(c, volume>11210 ? 91.61112 * std::log(4.8819E-7*volume + 1.0)+0.5 : 0);
                }

                opl.Touch_Real(c, volume);
                //opl.Touch(c, volume);
            }
            break;

            case OPL3::VOLUME_DMX:
            {
                volume = 2 * ((Ch[MidCh].volume * Ch[MidCh].expression) * 127 / 16129) + 1;
                //volume = 2 * (Ch[MidCh].volume) + 1;
                volume = (DMX_volume_mapping_table[vol] * volume) >> 9;
                opl.Touch_Real(c, volume);
            }
            break;

            case OPL3::VOLUME_APOGEE:
            {
                volume = ((Ch[MidCh].volume * Ch[MidCh].expression) * 127 / 16129);
                volume = ((64 * (vol + 0x80)) * volume) >> 15;
                //volume = ((63 * (vol + 0x80)) * Ch[MidCh].volume) >> 15;
                opl.Touch_Real(c, volume);
            }
            break;

            case OPL3::VOLUME_9X:
            {
                //volume = 63 - W9X_volume_mapping_table[(((vol * Ch[MidCh].volume /** Ch[MidCh].expression*/) * 127 / 16129 /*2048383*/) >> 2)];
                volume = 63 - W9X_volume_mapping_table[(((vol * Ch[MidCh].volume * Ch[MidCh].expression) * 127 / 2048383) >> 2)];
                //volume = W9X_volume_mapping_table[vol >> 2] + volume;
                opl.Touch_Real(c, volume);
            }
            break;
            }

            /* DEBUG ONLY!!!
                    static uint32_t max = 0;

                    if(volume == 0)
                        max = 0;

                    if(volume > max)
                        max = volume;

                    printf("%d\n", max);
                    fflush(stdout);
                    */
        }

        if(props_mask & Upd_Pitch)
        {
            AdlChannel::LocationData &d = ch[c].users[my_loc];

            // Don't bend a sustained note
            if(!d.sustained)
            {
                double bend = Ch[MidCh].bend + opl.GetAdlIns(ins).finetune;
                double phase = 0.0;

                if((ains.flags & adlinsdata::Flag_Pseudo4op) && ins == ains.adlno2)
                {
                    phase = ains.fine_tune;//0.125; // Detune the note slightly (this is what Doom does)
                }

                if(Ch[MidCh].vibrato && d.vibdelay >= Ch[MidCh].vibdelay)
                    bend += Ch[MidCh].vibrato * Ch[MidCh].vibdepth * std::sin(Ch[MidCh].vibpos);

                #ifdef ADLMIDI_USE_DOSBOX_OPL
#define BEND_COEFFICIENT 172.00093
                #else
#define BEND_COEFFICIENT 172.4387
                #endif
                opl.NoteOn(c, BEND_COEFFICIENT * std::exp(0.057762265 * (tone + bend + phase)));
#undef BEND_COEFFICIENT
            }
        }
    }

    if(info.phys.empty())
        Ch[MidCh].activenotes.erase(i);
}


void MIDIplay::ProcessEvents()
{
    loopEnd = false;
    const size_t    TrackCount = TrackData.size();
    const Position  RowBeginPosition(CurrentPosition);

    for(size_t tk = 0; tk < TrackCount; ++tk)
    {
        if(CurrentPosition.track[tk].status >= 0
           && CurrentPosition.track[tk].delay <= 0)
        {
            // Handle event
            HandleEvent(tk);

            // Read next event time (unless the track just ended)
            if(CurrentPosition.track[tk].ptr >= TrackData[tk].size())
                CurrentPosition.track[tk].status = -1;

            if(CurrentPosition.track[tk].status >= 0)
                CurrentPosition.track[tk].delay += ReadVarLen(tk);
        }
    }

    // Find shortest delay from all track
    uint64_t shortest = 0;
    bool     shortest_no = true;

    for(size_t tk = 0; tk < TrackCount; ++tk)
        if((CurrentPosition.track[tk].status >= 0) && (shortest_no || CurrentPosition.track[tk].delay < shortest))
        {
            shortest = CurrentPosition.track[tk].delay;
            shortest_no = false;
        }

    //if(shortest > 0) UI.PrintLn("shortest: %ld", shortest);

    // Schedule the next playevent to be processed after that delay
    for(size_t tk = 0; tk < TrackCount; ++tk)
        CurrentPosition.track[tk].delay -= shortest;

    fraction<uint64_t> t = shortest * Tempo;

    if(CurrentPosition.began)
        CurrentPosition.wait += t.valuel();

    //if(shortest > 0) UI.PrintLn("Delay %ld (%g)", shortest, (double)t.valuel());
    /*
    if(CurrentPosition.track[0].ptr > 8119)
        loopEnd = true;
        // ^HACK: CHRONO TRIGGER LOOP
    */

    if(loopStart_hit && (loopStart || loopEnd)) //Avoid invalid loops
    {
        invalidLoop = true;
        loopStart = false;
        loopEnd = false;
        LoopBeginPosition = trackBeginPosition;
    }
    else
        loopStart_hit = false;

    if(loopStart)
    {
        if(trackStart)
        {
            trackBeginPosition = RowBeginPosition;
            trackStart = false;
        }
        LoopBeginPosition = RowBeginPosition;
        loopStart = false;
        loopStart_hit = true;
    }

    if(shortest_no || loopEnd)
    {
        // Loop if song end reached
        loopEnd         = false;
        CurrentPosition = LoopBeginPosition;
        shortest = 0;
        if(opl._parent->QuitWithoutLooping == 1)
        {
            opl._parent->QuitFlag = 1;
            //^ HACK: QUIT WITHOUT LOOPING
        }
    }
}

void MIDIplay::HandleEvent(size_t tk)
{
    unsigned char byte = TrackData[tk][CurrentPosition.track[tk].ptr++];

    if(byte == 0xF7 || byte == 0xF0) // Ignore SysEx
    {
        uint64_t length = ReadVarLen(tk);
        //std::string data( length?(const char*) &TrackData[tk][CurrentPosition.track[tk].ptr]:0, length );
        CurrentPosition.track[tk].ptr += length;
        //UI.PrintLn("SysEx %02X: %u bytes", byte, length/*, data.c_str()*/);
        return;
    }

    if(byte == 0xFF)
    {
        // Special event FF
        uint8_t  evtype = TrackData[tk][CurrentPosition.track[tk].ptr++];
        uint64_t length = ReadVarLen(tk);
        std::string data(length ? (const char *) &TrackData[tk][CurrentPosition.track[tk].ptr] : 0, length);
        CurrentPosition.track[tk].ptr += length;

        if(evtype == 0x2F)
        {
            CurrentPosition.track[tk].status = -1;
            return;
        }

        if(evtype == 0x51)
        {
            Tempo = InvDeltaTicks * fraction<uint64_t>(ReadBEint(data.data(), data.size()));
            return;
        }

        if(evtype == 6)
        {
            for(size_t i = 0; i < data.size(); i++)
            {
                if(data[i] <= 'Z' && data[i] >= 'A')
                    data[i] = data[i] - ('Z' - 'z');
            }

            if((data == "loopstart") && (!invalidLoop))
            {
                loopStart = true;
                loopStart_passed = true;
            }

            if((data == "loopend") && (!invalidLoop))
            {
                if((loopStart_passed) && (!loopStart))
                    loopEnd = true;
                else
                    invalidLoop = true;
            }
        }

        if(evtype == 9)
            current_device[tk] = ChooseDevice(data);

        //if(evtype >= 1 && evtype <= 6)
        //    UI.PrintLn("Meta %d: %s", evtype, data.c_str());

        if(evtype == 0xE3) // Special non-spec ADLMIDI special for IMF playback: Direct poke to AdLib
        {
            uint8_t i = static_cast<uint8_t>(data[0]), v = static_cast<uint8_t>(data[1]);

            if((i & 0xF0) == 0xC0)
                v |= 0x30;

            //std::printf("OPL poke %02X, %02X\n", i, v);
            //std::fflush(stdout);
            opl.PokeN(0, i, v);
        }

        return;
    }

    // Any normal event (80..EF)
    if(byte < 0x80)
    {
        byte = static_cast<uint8_t>(CurrentPosition.track[tk].status | 0x80);
        CurrentPosition.track[tk].ptr--;
    }

    if(byte == 0xF3)
    {
        CurrentPosition.track[tk].ptr += 1;
        return;
    }

    if(byte == 0xF2)
    {
        CurrentPosition.track[tk].ptr += 2;
        return;
    }

    /*UI.PrintLn("@%X Track %u: %02X %02X",
                CurrentPosition.track[tk].ptr-1, (unsigned)tk, byte,
                TrackData[tk][CurrentPosition.track[tk].ptr]);*/
    uint8_t  MidCh = byte & 0x0F, EvType = byte >> 4;
    MidCh += current_device[tk];
    CurrentPosition.track[tk].status = byte;

    switch(EvType)
    {
    case 0x8: // Note off
    case 0x9: // Note on
    {
        uint8_t note = TrackData[tk][CurrentPosition.track[tk].ptr++];
        uint8_t vol  = TrackData[tk][CurrentPosition.track[tk].ptr++];
        //if(MidCh != 9) note -= 12; // HACK
        NoteOff(MidCh, note);

        // On Note on, Keyoff the note first, just in case keyoff
        // was omitted; this fixes Dance of sugar-plum fairy
        // by Microsoft. Now that we've done a Keyoff,
        // check if we still need to do a Keyon.
        // vol=0 and event 8x are both Keyoff-only.
        if(vol == 0 || EvType == 0x8) break;

        uint8_t midiins = Ch[MidCh].patch;

        if(MidCh % 16 == 9)
            midiins = 128 + note; // Percussion instrument

        /*
            if(MidCh%16 == 9 || (midiins != 32 && midiins != 46 && midiins != 48 && midiins != 50))
                break; // HACK
            if(midiins == 46) vol = (vol*7)/10;          // HACK
            if(midiins == 48 || midiins == 50) vol /= 4; // HACK
            */
        //if(midiins == 56) vol = vol*6/10; // HACK
        static std::set<uint32_t> bank_warnings;

        if(Ch[MidCh].bank_msb)
        {
            uint32_t bankid = midiins + 256 * Ch[MidCh].bank_msb;
            std::set<uint32_t>::iterator
            i = bank_warnings.lower_bound(bankid);

            if(i == bank_warnings.end() || *i != bankid)
            {
                ADLMIDI_ErrorString.clear();
                std::stringstream s;
                s << "[" << MidCh << "]Bank " << Ch[MidCh].bank_msb <<
                  " undefined, patch=" << ((midiins & 128) ? 'P' : 'M') << (midiins & 127);
                ADLMIDI_ErrorString = s.str();
                bank_warnings.insert(i, bankid);
            }
        }

        if(Ch[MidCh].bank_lsb)
        {
            unsigned bankid = Ch[MidCh].bank_lsb * 65536;
            std::set<unsigned>::iterator
            i = bank_warnings.lower_bound(bankid);

            if(i == bank_warnings.end() || *i != bankid)
            {
                ADLMIDI_ErrorString.clear();
                std::stringstream s;
                s << "[" << MidCh << "]Bank lsb " << Ch[MidCh].bank_lsb << " undefined";
                ADLMIDI_ErrorString = s.str();
                bank_warnings.insert(i, bankid);
            }
        }

        //int meta = banks[opl.AdlBank][midiins];
        const uint32_t      meta   = opl.GetAdlMetaNumber(midiins);
        const adlinsdata    &ains  = opl.GetAdlMetaIns(meta);
        int16_t tone = note;

        if(ains.tone)
        {
            /*if(ains.tone < 20)
                tone += ains.tone;
            else*/
            if(ains.tone < 128)
                tone = ains.tone;
            else
                tone -= ains.tone - 128;
        }

        uint16_t i[2] = { ains.adlno1, ains.adlno2 };
        bool pseudo_4op = ains.flags & adlinsdata::Flag_Pseudo4op;

        if((opl.AdlPercussionMode == 1) && PercussionMap[midiins & 0xFF]) i[1] = i[0];

        static std::set<uint8_t> missing_warnings;

        if(!missing_warnings.count(static_cast<uint8_t>(midiins)) && (ains.flags & adlinsdata::Flag_NoSound))
        {
            //UI.PrintLn("[%i]Playing missing instrument %i", MidCh, midiins);
            missing_warnings.insert(static_cast<uint8_t>(midiins));
        }

        // Allocate AdLib channel (the physical sound channel for the note)
        int32_t adlchannel[2] = { -1, -1 };

        for(uint32_t ccount = 0; ccount < 2; ++ccount)
        {
            if(ccount == 1)
            {
                if(i[0] == i[1]) break; // No secondary channel

                if(adlchannel[0] == -1)
                    break; // No secondary if primary failed
            }

            int32_t c = -1;
            long bs = -0x7FFFFFFFl;

            for(uint32_t a = 0; a < opl.NumChannels; ++a)
            {
                if(ccount == 1 && static_cast<int32_t>(a) == adlchannel[0]) continue;

                // ^ Don't use the same channel for primary&secondary

                if(i[0] == i[1] || pseudo_4op)
                {
                    // Only use regular channels
                    uint8_t expected_mode = 0;

                    if(opl.AdlPercussionMode == 1)
                    {
                        if(cmf_percussion_mode)
                            expected_mode = MidCh < 11 ? 0 : (3 + MidCh - 11); // CMF
                        else
                            expected_mode = PercussionMap[midiins & 0xFF];
                    }

                    if(opl.four_op_category[a] != expected_mode)
                        continue;
                }
                else
                {
                    if(ccount == 0)
                    {
                        // Only use four-op master channels
                        if(opl.four_op_category[a] != 1)
                            continue;
                    }
                    else
                    {
                        // The secondary must be played on a specific channel.
                        if(a != static_cast<uint32_t>(adlchannel[0]) + 3)
                            continue;
                    }
                }

                long s = CalculateAdlChannelGoodness(a, i[ccount], MidCh);

                if(s > bs)
                {
                    bs = s;    // Best candidate wins
                    c = static_cast<int32_t>(a);
                }
            }

            if(c < 0)
            {
                //UI.PrintLn("ignored unplaceable note");
                continue; // Could not play this note. Ignore it.
            }

            PrepareAdlChannelForNewNote(static_cast<size_t>(c), i[ccount]);
            adlchannel[ccount] = c;
        }

        if(adlchannel[0] < 0 && adlchannel[1] < 0)
        {
            // The note could not be played, at all.
            break;
        }

        //UI.PrintLn("i1=%d:%d, i2=%d:%d", i[0],adlchannel[0], i[1],adlchannel[1]);
        // Allocate active note for MIDI channel
        std::pair<MIDIchannel::activenoteiterator, bool>
        ir = Ch[MidCh].activenotes.insert(
                 std::make_pair(note, MIDIchannel::NoteInfo()));
        ir.first->second.vol     = vol;
        ir.first->second.tone    = tone;
        ir.first->second.midiins = midiins;
        ir.first->second.insmeta = meta;

        for(unsigned ccount = 0; ccount < 2; ++ccount)
        {
            int32_t c = adlchannel[ccount];

            if(c < 0)
                continue;

            ir.first->second.phys[ static_cast<uint16_t>(adlchannel[ccount]) ] = i[ccount];
        }

        CurrentPosition.began  = true;
        NoteUpdate(MidCh, ir.first, Upd_All | Upd_Patch);
        break;
    }

    case 0xA: // Note touch
    {
        uint8_t note = TrackData[tk][CurrentPosition.track[tk].ptr++];
        uint8_t vol = TrackData[tk][CurrentPosition.track[tk].ptr++];
        MIDIchannel::activenoteiterator
        i = Ch[MidCh].activenotes.find(note);

        if(i == Ch[MidCh].activenotes.end())
        {
            // Ignore touch if note is not active
            break;
        }

        i->second.vol = vol;
        NoteUpdate(MidCh, i, Upd_Volume);
        break;
    }

    case 0xB: // Controller change
    {
        uint8_t ctrlno = TrackData[tk][CurrentPosition.track[tk].ptr++];
        uint8_t value = TrackData[tk][CurrentPosition.track[tk].ptr++];

        switch(ctrlno)
        {
        case 1: // Adjust vibrato
            //UI.PrintLn("%u:vibrato %d", MidCh,value);
            Ch[MidCh].vibrato = value;
            break;

        case 0: // Set bank msb (GM bank)
            Ch[MidCh].bank_msb = value;
            break;

        case 32: // Set bank lsb (XG bank)
            Ch[MidCh].bank_lsb = value;
            break;

        case 5: // Set portamento msb
            Ch[MidCh].portamento = static_cast<uint16_t>((Ch[MidCh].portamento & 0x7F) | (value << 7));
            //UpdatePortamento(MidCh);
            break;

        case 37: // Set portamento lsb
            Ch[MidCh].portamento = (Ch[MidCh].portamento & 0x3F80) | (value);
            //UpdatePortamento(MidCh);
            break;

        case 65: // Enable/disable portamento
            // value >= 64 ? enabled : disabled
            //UpdatePortamento(MidCh);
            break;

        case 7: // Change volume
            Ch[MidCh].volume = value;
            NoteUpdate_All(MidCh, Upd_Volume);
            break;

        case 64: // Enable/disable sustain
            Ch[MidCh].sustain = value;

            if(!value) KillSustainingNotes(MidCh);

            break;

        case 11: // Change expression (another volume factor)
            Ch[MidCh].expression = value;
            NoteUpdate_All(MidCh, Upd_Volume);
            break;

        case 10: // Change panning
            Ch[MidCh].panning = 0x00;

            if(value  < 64 + 32) Ch[MidCh].panning |= 0x10;

            if(value >= 64 - 32) Ch[MidCh].panning |= 0x20;

            NoteUpdate_All(MidCh, Upd_Pan);
            break;

        case 121: // Reset all controllers
            Ch[MidCh].bend       = 0;
            Ch[MidCh].volume     = 100;
            Ch[MidCh].expression = 100;
            Ch[MidCh].sustain    = 0;
            Ch[MidCh].vibrato    = 0;
            Ch[MidCh].vibspeed   = 2 * 3.141592653 * 5.0;
            Ch[MidCh].vibdepth   = 0.5 / 127;
            Ch[MidCh].vibdelay   = 0;
            Ch[MidCh].panning    = 0x30;
            Ch[MidCh].portamento = 0;
            //UpdatePortamento(MidCh);
            NoteUpdate_All(MidCh, Upd_Pan + Upd_Volume + Upd_Pitch);
            // Kill all sustained notes
            KillSustainingNotes(MidCh);
            break;

        case 123: // All notes off
            NoteUpdate_All(MidCh, Upd_Off);
            break;

        case 91:
            break; // Reverb effect depth. We don't do per-channel reverb.

        case 92:
            break; // Tremolo effect depth. We don't do...

        case 93:
            break; // Chorus effect depth. We don't do.

        case 94:
            break; // Celeste effect depth. We don't do.

        case 95:
            break; // Phaser effect depth. We don't do.

        case 98:
            Ch[MidCh].lastlrpn = value;
            Ch[MidCh].nrpn = true;
            break;

        case 99:
            Ch[MidCh].lastmrpn = value;
            Ch[MidCh].nrpn = true;
            break;

        case 100:
            Ch[MidCh].lastlrpn = value;
            Ch[MidCh].nrpn = false;
            break;

        case 101:
            Ch[MidCh].lastmrpn = value;
            Ch[MidCh].nrpn = false;
            break;

        case 113:
            break; // Related to pitch-bender, used by missimp.mid in Duke3D

        case  6:
            SetRPN(MidCh, value, true);
            break;

        case 38:
            SetRPN(MidCh, value, false);
            break;

        case 103:
            cmf_percussion_mode = value;
            break; // CMF (ctrl 0x67) rhythm mode

        case 111://LoopStart unofficial controller
            if(!invalidLoop)
            {
                loopStart = true;
                loopStart_passed = true;
            }

            break;

        default:
            break;
            //UI.PrintLn("Ctrl %d <- %d (ch %u)", ctrlno, value, MidCh);
        }

        break;
    }

    case 0xC: // Patch change
        Ch[MidCh].patch = TrackData[tk][CurrentPosition.track[tk].ptr++];
        break;

    case 0xD: // Channel after-touch
    {
        // TODO: Verify, is this correct action?
        uint8_t vol = TrackData[tk][CurrentPosition.track[tk].ptr++];

        for(MIDIchannel::activenoteiterator
            i = Ch[MidCh].activenotes.begin();
            i != Ch[MidCh].activenotes.end();
            ++i)
        {
            // Set this pressure to all active notes on the channel
            i->second.vol = vol;
        }

        NoteUpdate_All(MidCh, Upd_Volume);
        break;
    }

    case 0xE: // Wheel/pitch bend
    {
        int a = TrackData[tk][CurrentPosition.track[tk].ptr++];
        int b = TrackData[tk][CurrentPosition.track[tk].ptr++];
        Ch[MidCh].bend = (a + b * 128 - 8192) * Ch[MidCh].bendsense;
        NoteUpdate_All(MidCh, Upd_Pitch);
        break;
    }
    }
}

long MIDIplay::CalculateAdlChannelGoodness(unsigned c, uint16_t ins, uint16_t) const
{
    long s = -ch[c].koff_time_until_neglible;

    // Same midi-instrument = some stability
    //if(c == MidCh) s += 4;
    for(AdlChannel::users_t::const_iterator
        j = ch[c].users.begin();
        j != ch[c].users.end();
        ++j)
    {
        s -= 4000;

        if(!j->second.sustained)
            s -= j->second.kon_time_until_neglible;
        else
            s -= j->second.kon_time_until_neglible / 2;

        MIDIchannel::activenotemap_t::const_iterator
        k = Ch[j->first.MidCh].activenotes.find(j->first.note);

        if(k != Ch[j->first.MidCh].activenotes.end())
        {
            // Same instrument = good
            if(j->second.ins == ins)
            {
                s += 300;

                // Arpeggio candidate = even better
                if(j->second.vibdelay < 70
                   || j->second.kon_time_until_neglible > 20000)
                    s += 0;
            }

            // Percussion is inferior to melody
            s += 50 * (k->second.midiins / 128);
            /*
                    if(k->second.midiins >= 25
                    && k->second.midiins < 40
                    && j->second.ins != ins)
                    {
                        s -= 14000; // HACK: Don't clobber the bass or the guitar
                    }
                    */
        }

        // If there is another channel to which this note
        // can be evacuated to in the case of congestion,
        // increase the score slightly.
        unsigned n_evacuation_stations = 0;

        for(unsigned c2 = 0; c2 < opl.NumChannels; ++c2)
        {
            if(c2 == c) continue;

            if(opl.four_op_category[c2]
               != opl.four_op_category[c]) continue;

            for(AdlChannel::users_t::const_iterator
                m = ch[c2].users.begin();
                m != ch[c2].users.end();
                ++m)
            {
                if(m->second.sustained)       continue;

                if(m->second.vibdelay >= 200) continue;

                if(m->second.ins != j->second.ins) continue;

                n_evacuation_stations += 1;
            }
        }

        s += n_evacuation_stations * 4;
    }

    return s;
}


void MIDIplay::PrepareAdlChannelForNewNote(size_t c, int ins)
{
    if(ch[c].users.empty()) return; // Nothing to do

    //bool doing_arpeggio = false;
    for(AdlChannel::users_t::iterator
        jnext = ch[c].users.begin();
        jnext != ch[c].users.end();
       )
    {
        AdlChannel::users_t::iterator j(jnext++);

        if(!j->second.sustained)
        {
            // Collision: Kill old note,
            // UNLESS we're going to do arpeggio
            MIDIchannel::activenoteiterator i
            (Ch[j->first.MidCh].activenotes.find(j->first.note));

            // Check if we can do arpeggio.
            if((j->second.vibdelay < 70
                || j->second.kon_time_until_neglible > 20000)
               && j->second.ins == ins)
            {
                // Do arpeggio together with this note.
                //doing_arpeggio = true;
                continue;
            }

            KillOrEvacuate(c, j, i);
            // ^ will also erase j from ch[c].users.
        }
    }

    // Kill all sustained notes on this channel
    // Don't keep them for arpeggio, because arpeggio requires
    // an intact "activenotes" record. This is a design flaw.
    KillSustainingNotes(-1, static_cast<int32_t>(c));

    // Keyoff the channel so that it can be retriggered,
    // unless the new note will be introduced as just an arpeggio.
    if(ch[c].users.empty())
        opl.NoteOff(c);
}

void MIDIplay::KillOrEvacuate(size_t from_channel, AdlChannel::users_t::iterator j, MIDIplay::MIDIchannel::activenoteiterator i)
{
    // Before killing the note, check if it can be
    // evacuated to another channel as an arpeggio
    // instrument. This helps if e.g. all channels
    // are full of strings and we want to do percussion.
    // FIXME: This does not care about four-op entanglements.
    for(uint32_t c = 0; c < opl.NumChannels; ++c)
    {
        uint16_t cs = static_cast<uint16_t>(c);

        if(c > std::numeric_limits<uint32_t>::max())
            break;

        if(c == from_channel)
            continue;

        if(opl.four_op_category[c]
           != opl.four_op_category[from_channel]
          ) continue;

        for(AdlChannel::users_t::iterator
            m = ch[c].users.begin();
            m != ch[c].users.end();
            ++m)
        {
            if(m->second.vibdelay >= 200
               && m->second.kon_time_until_neglible < 10000) continue;

            if(m->second.ins != j->second.ins) continue;

            // the note can be moved here!
            //                UI.IllustrateNote(
            //                    from_channel,
            //                    i->second.tone,
            //                    i->second.midiins, 0, 0.0);
            //                UI.IllustrateNote(
            //                    c,
            //                    i->second.tone,
            //                    i->second.midiins,
            //                    i->second.vol,
            //                    0.0);
            i->second.phys.erase(static_cast<uint16_t>(from_channel));
            i->second.phys[cs] = j->second.ins;
            ch[cs].users.insert(*j);
            ch[from_channel].users.erase(j);
            return;
        }
    }

    /*UI.PrintLn(
                "collision @%u: [%ld] <- ins[%3u]",
                c,
                //ch[c].midiins<128?'M':'P', ch[c].midiins&127,
                ch[c].age, //adlins[ch[c].insmeta].ms_sound_kon,
                ins
                );*/
    // Kill it
    NoteUpdate(j->first.MidCh,
               i,
               Upd_Off,
               static_cast<int32_t>(from_channel));
}

void MIDIplay::KillSustainingNotes(int32_t MidCh, int32_t this_adlchn)
{
    uint32_t first = 0, last = opl.NumChannels;

    if(this_adlchn >= 0)
    {
        first = static_cast<uint32_t>(this_adlchn);
        last = first + 1;
    }

    for(unsigned c = first; c < last; ++c)
    {
        if(ch[c].users.empty()) continue; // Nothing to do

        for(AdlChannel::users_t::iterator
            jnext = ch[c].users.begin();
            jnext != ch[c].users.end();
           )
        {
            AdlChannel::users_t::iterator j(jnext++);

            if((MidCh < 0 || j->first.MidCh == MidCh)
               && j->second.sustained)
            {
                //int midiins = '?';
                //UI.IllustrateNote(c, j->first.note, midiins, 0, 0.0);
                ch[c].users.erase(j);
            }
        }

        // Keyoff the channel, if there are no users left.
        if(ch[c].users.empty())
            opl.NoteOff(c);
    }
}

void MIDIplay::SetRPN(unsigned MidCh, unsigned value, bool MSB)
{
    bool nrpn = Ch[MidCh].nrpn;
    unsigned addr = Ch[MidCh].lastmrpn * 0x100 + Ch[MidCh].lastlrpn;

    switch(addr + nrpn * 0x10000 + MSB * 0x20000)
    {
    case 0x0000 + 0*0x10000 + 1*0x20000: // Pitch-bender sensitivity
        Ch[MidCh].bendsense = value / 8192.0;
        break;

    case 0x0108 + 1*0x10000 + 1*0x20000: // Vibrato speed
        if(value == 64)
            Ch[MidCh].vibspeed = 1.0;
        else if(value < 100)
            Ch[MidCh].vibspeed = 1.0 / (1.6e-2 * (value ? value : 1));
        else
            Ch[MidCh].vibspeed = 1.0 / (0.051153846 * value - 3.4965385);

        Ch[MidCh].vibspeed *= 2 * 3.141592653 * 5.0;
        break;

    case 0x0109 + 1*0x10000 + 1*0x20000: // Vibrato depth
        Ch[MidCh].vibdepth = ((value - 64) * 0.15) * 0.01;
        break;

    case 0x010A + 1*0x10000 + 1*0x20000: // Vibrato delay in millisecons
        Ch[MidCh].vibdelay =
            value ? long(0.2092 * std::exp(0.0795 * value)) : 0.0;
        break;

    default:/* UI.PrintLn("%s %04X <- %d (%cSB) (ch %u)",
                "NRPN"+!nrpn, addr, value, "LM"[MSB], MidCh);*/
        break;
    }
}

//void MIDIplay::UpdatePortamento(unsigned MidCh)
//{
//    // mt = 2^(portamento/2048) * (1.0 / 5000.0)
//    /*
//    double mt = std::exp(0.00033845077 * Ch[MidCh].portamento);
//    NoteUpdate_All(MidCh, Upd_Pitch);
//    */
//    //UI.PrintLn("Portamento %u: %u (unimplemented)", MidCh, Ch[MidCh].portamento);
//}

void MIDIplay::NoteUpdate_All(uint16_t MidCh, unsigned props_mask)
{
    for(MIDIchannel::activenoteiterator
        i = Ch[MidCh].activenotes.begin();
        i != Ch[MidCh].activenotes.end();
       )
    {
        MIDIchannel::activenoteiterator j(i++);
        NoteUpdate(MidCh, j, props_mask);
    }
}

void MIDIplay::NoteOff(uint16_t MidCh, uint8_t note)
{
    MIDIchannel::activenoteiterator
    i = Ch[MidCh].activenotes.find(note);

    if(i != Ch[MidCh].activenotes.end())
        NoteUpdate(MidCh, i, Upd_Off);
}


void MIDIplay::UpdateVibrato(double amount)
{
    for(size_t a = 0, b = Ch.size(); a < b; ++a)
        if(Ch[a].vibrato && !Ch[a].activenotes.empty())
        {
            NoteUpdate_All(static_cast<uint16_t>(a), Upd_Pitch);
            Ch[a].vibpos += amount * Ch[a].vibspeed;
        }
        else
            Ch[a].vibpos = 0.0;
}




uint64_t MIDIplay::ChooseDevice(const std::string &name)
{
    std::map<std::string, uint64_t>::iterator i = devices.find(name);

    if(i != devices.end())
        return i->second;

    size_t n = devices.size() * 16;
    devices.insert(std::make_pair(name, n));
    Ch.resize(n + 16);
    return n;
}

void MIDIplay::UpdateArpeggio(double) // amount = amount of time passed
{
    // If there is an adlib channel that has multiple notes
    // simulated on the same channel, arpeggio them.
    #if 0
    const unsigned desired_arpeggio_rate = 40; // Hz (upper limit)
    #if 1
    static unsigned cache = 0;
    amount = amount; // Ignore amount. Assume we get a constant rate.
    cache += MaxSamplesAtTime * desired_arpeggio_rate;

    if(cache < PCM_RATE) return;

    cache %= PCM_RATE;
    #else
    static double arpeggio_cache = 0;
    arpeggio_cache += amount * desired_arpeggio_rate;

    if(arpeggio_cache < 1.0) return;

    arpeggio_cache = 0.0;
    #endif
    #endif
    static unsigned arpeggio_counter = 0;
    ++arpeggio_counter;

    for(uint32_t c = 0; c < opl.NumChannels; ++c)
    {
retry_arpeggio:
        if(c > INT32_MAX)
            break;

        size_t n_users = ch[c].users.size();

        if(n_users > 1)
        {
            AdlChannel::users_t::const_iterator i = ch[c].users.begin();
            size_t rate_reduction = 3;

            if(n_users >= 3)
                rate_reduction = 2;

            if(n_users >= 4)
                rate_reduction = 1;

            std::advance(i, (arpeggio_counter / rate_reduction) % n_users);

            if(i->second.sustained == false)
            {
                if(i->second.kon_time_until_neglible <= 0l)
                {
                    NoteUpdate(
                        i->first.MidCh,
                        Ch[ i->first.MidCh ].activenotes.find(i->first.note),
                        Upd_Off,
                        static_cast<int32_t>(c));
                    goto retry_arpeggio;
                }

                NoteUpdate(
                    i->first.MidCh,
                    Ch[ i->first.MidCh ].activenotes.find(i->first.note),
                    Upd_Pitch | Upd_Volume | Upd_Pan,
                    static_cast<int32_t>(c));
            }
        }
    }
}
