package ru.wohlsoft.adlmidiplayer;

import android.content.SharedPreferences;

public class AppSettings
{
    static public SharedPreferences          m_setup = null;

    static private volatile boolean          m_isInit = false;

//    static public String              m_lastFile = "";
    static private boolean             m_useCustomBank = false;
    static private String              m_lastBankPath = "";
    static private int                 m_ADL_bank = 58;
    static private int                 m_ADL_tremolo = -1;
    static private int                 m_ADL_vibrato = -1;
    static private int                 m_ADL_scalable = -1;
    static private int                 m_ADL_softPanEnabled = 0;
    // Default 1 for performance reasons
    static private int                 m_ADL_runAtPcmRate = 1;

    static private int                 m_ADL_autoArpeggio = 0;

    static private int                 m_ADL_emulator = 2; // 2 is DosBox
    static private int                 m_adl_numChips = 2;
    static private int                 m_ADL_num4opChannels = -1;
    static private int                 m_ADL_volumeModel = 0;

    static private double              m_gainingLevel = 2.0;

    //! Cache of previously sent seek position
    static public int                 m_lastSeekPosition = -1;

    static public void loadSetup(SharedPreferences setup)
    {
        m_setup = setup;
        if(!m_isInit) {
            m_isInit = true;
            m_useCustomBank = setup.getBoolean("useCustomBank", m_useCustomBank);
            m_lastBankPath = setup.getString("lastBankPath", m_lastBankPath);
            m_ADL_bank = setup.getInt("adlBank", m_ADL_bank);
            m_ADL_tremolo = setup.getBoolean("flagTremolo", m_ADL_tremolo > 0) ? 1 : -1;
            m_ADL_vibrato = setup.getBoolean("flagVibrato", m_ADL_vibrato > 0) ? 1 : -1;
            m_ADL_scalable = setup.getBoolean("flagScalable", m_ADL_scalable > 0) ? 1 : -1;
            m_ADL_softPanEnabled = setup.getBoolean("flagSoftPan", m_ADL_softPanEnabled > 0) ? 1 : 0;
            m_ADL_runAtPcmRate = setup.getBoolean("flagRunAtPcmRate", m_ADL_runAtPcmRate > 0) ? 1 : 0;
            m_ADL_autoArpeggio = setup.getBoolean("flagAutoArpeggio", m_ADL_autoArpeggio > 0) ? 1 : 0;

            m_ADL_emulator = setup.getInt("emulator", m_ADL_emulator);
            m_adl_numChips = setup.getInt("numChips", m_adl_numChips);
            m_ADL_num4opChannels = setup.getInt("num4opChannels", m_ADL_num4opChannels);
            m_ADL_volumeModel = setup.getInt("volumeModel", m_ADL_volumeModel);

            m_gainingLevel = (double)setup.getFloat("gaining", (float)m_gainingLevel);
        }
    }


    static public void setBankPath(String bankFile)
    {
        m_lastBankPath = bankFile;
        m_setup.edit().putString("lastBankPath", m_lastBankPath).apply();
    }

    static public String getBankPath()
    {
        return m_lastBankPath;
    }


    static void setUseCustomBank(boolean use)
    {
        m_useCustomBank = use;
        m_setup.edit().putBoolean("useCustomBank", m_useCustomBank).apply();
    }

    static public boolean getUseCustomBank()
    {
        return AppSettings.m_useCustomBank;
    }


    static public void setEmbeddedBank(int bankId)
    {
        m_ADL_bank = bankId;
        m_setup.edit().putInt("adlBank", m_ADL_bank).apply();
    }

    static public int getEmbeddedBank()
    {
        return m_ADL_bank;
    }


    static public void setVolumeModel(int volumeModel)
    {
        m_ADL_volumeModel = volumeModel;
        m_setup.edit().putInt("volumeModel", m_ADL_volumeModel).apply();
    }

    static public int getVolumeModel()
    {
        return m_ADL_volumeModel;
    }


    static public void setDeepTremolo(boolean flag)
    {
        m_ADL_tremolo = flag ? 1 : -1;
        m_setup.edit().putBoolean("flagTremolo", flag).apply();
    }
    static public boolean getDeepTremolo()
    {
        return m_ADL_tremolo > 0;
    }
    static public int getDeepTremoloRaw()
    {
        return m_ADL_tremolo;
    }


    static public void setDeepVibrato(boolean flag)
    {
        m_ADL_vibrato = flag ? 1 : -1;
        m_setup.edit().putBoolean("flagVibrato", flag).apply();
    }
    static public boolean getDeepVibrato()
    {
        return m_ADL_vibrato > 0;
    }
    static public int getDeepVibratoRaw()
    {
        return m_ADL_vibrato;
    }


    static public void setScalableModulators(boolean flag)
    {
        m_ADL_scalable = flag ? 1 : -1;
        m_setup.edit().putBoolean("flagScalable", flag).apply();
    }

    static public boolean getScalableModulation()
    {
        return m_ADL_scalable > 0;
    }
    static public int getScalableModulationRaw()
    {
        return m_ADL_scalable;
    }


    static public void setRunAtPcmRate(boolean flag)
    {
        m_ADL_runAtPcmRate = flag ? 1 : 0;
        m_setup.edit().putBoolean("flagRunAtPcmRate", flag).apply();
    }

    static public boolean getRunAtPcmRate()
    {
        return m_ADL_runAtPcmRate > 0;
    }

    static public int getRunAtPcmRateRaw()
    {
        return m_ADL_runAtPcmRate;
    }


    static public void setFullPanningStereo(boolean flag)
    {
        m_ADL_softPanEnabled = flag ? 1 : 0;
        m_setup.edit().putBoolean("flagSoftPan", flag).apply();
    }
    static public boolean getFullPanningStereo()
    {
        return m_ADL_softPanEnabled > 0;
    }
    static public int getFullPanningStereoRaw()
    {
        return m_ADL_softPanEnabled;
    }


    static public void setAutoArpeggio(boolean flag)
    {
        m_ADL_autoArpeggio = flag ? 1 : 0;
        m_setup.edit().putBoolean("flagAutoArpeggio", flag).apply();
    }
    static public boolean getAutoArpeggio()
    {
        return m_ADL_autoArpeggio > 0;
    }
    static public int getAutoArpeggioRaw()
    {
        return m_ADL_autoArpeggio;
    }


    static public void setEmulator(int emul)
    {
        if(m_ADL_emulator != emul)
        {
            m_ADL_emulator = emul;
            m_setup.edit().putInt("emulator", m_ADL_emulator).apply();
        }
    }
    static public int getEmulator()
    {
        return m_ADL_emulator;
    }

    static public void setChipsCount(int chips)
    {
        m_adl_numChips = chips;
        m_setup.edit().putInt("numChips", AppSettings.m_adl_numChips).apply();
        if(m_ADL_num4opChannels > getFourOpMax()) {
            m_ADL_num4opChannels = getFourOpMax();
            m_setup.edit().putInt("num4opChannels", m_ADL_num4opChannels).apply();
        }
    }

    static public int getChips4opsRaw()
    {
        return m_ADL_num4opChannels;
    }

    static public int getChipsCount()
    {
        return m_adl_numChips;
    }

    static public int getFourOpMax()
    {
        return 6 * m_adl_numChips;
    }


    static public void setFourOpChanCount(int fourOps)
    {
        m_ADL_num4opChannels = fourOps;
        if(m_ADL_num4opChannels > getFourOpMax()) {
            m_ADL_num4opChannels = getFourOpMax();
        }
        m_setup.edit().putInt("num4opChannels", m_ADL_num4opChannels).apply();
    }
    static public int getFourOpChanCount()
    {
        return m_ADL_num4opChannels;
    }

    static public void setGaining(double gaining)
    {
        m_gainingLevel = gaining;
        m_setup.edit().putFloat("gaining", (float)m_gainingLevel).apply();
    }
    static public double getGaining()
    {
        return m_gainingLevel;
    }

}
