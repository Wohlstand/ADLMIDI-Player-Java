package ru.wohlsoft.adlmidiplayer;

import android.content.SharedPreferences;

public class PlayerSetup {

    private SharedPreferences  m_setup = null;

    public boolean             m_useCustomBank = false;
    public String              m_lastBankPath = "";
    public int                 m_ADL_bank = 58;
    public int                 m_ADL_tremolo = -1;
    public int                 m_ADL_vibrato = -1;
    public int                 m_ADL_scalable = -1;
    public int                 m_ADL_softPanEnabled = 0;
    // Default 1 for performance reasons
    public int                 m_ADL_runAtPcmRate = 1;

    public int                 m_ADL_emulator = 2; // 2 is DosBox
    public int                 m_adl_numChips = 2;
    public int                 m_ADL_num4opChannels = -1;
    public int                 m_ADL_volumeModel = 0;

    public double              m_gainingLevel = 2.0;

    public void setSetup(SharedPreferences setup)
    {
        m_setup = setup;
        loadSetup();
    }

    public void loadSetup()
    {
        m_useCustomBank = m_setup.getBoolean("useCustomBank", m_useCustomBank);
        m_lastBankPath = m_setup.getString("lastBankPath", m_lastBankPath);
        m_ADL_bank = m_setup.getInt("adlBank", m_ADL_bank);

        m_ADL_tremolo = m_setup.getBoolean("flagTremolo", m_ADL_tremolo > 0) ? 1 : -1;
        m_ADL_vibrato = m_setup.getBoolean("flagVibrato", m_ADL_vibrato > 0) ? 1 : -1;
        m_ADL_scalable = m_setup.getBoolean("flagScalable", m_ADL_scalable > 0) ? 1 : -1;
        m_ADL_softPanEnabled = m_setup.getBoolean("flagSoftPan", m_ADL_softPanEnabled > 0) ? 1 : 0;
        m_ADL_runAtPcmRate = m_setup.getBoolean("flagRunAtPcmRate", m_ADL_runAtPcmRate > 0) ? 1 : 0;

        m_ADL_emulator = m_setup.getInt("emulator", m_ADL_emulator);
        m_adl_numChips = m_setup.getInt("numChips", m_adl_numChips);
        m_ADL_num4opChannels = m_setup.getInt("num4opChannels", m_ADL_num4opChannels);
        m_ADL_volumeModel = m_setup.getInt("volumeModel", m_ADL_volumeModel);

        m_gainingLevel = (double)m_setup.getFloat("gaining", (float)m_gainingLevel);
    }

    public void syncSetup()
    {
        m_setup.edit()
                .putBoolean("useCustomBank", m_useCustomBank)
                .putString("lastBankPath", m_lastBankPath)
                .putInt("adlBank", m_ADL_bank)

                .putBoolean("flagTremolo", m_ADL_tremolo > 0)
                .putBoolean("flagVibrato", m_ADL_vibrato > 0)
                .putBoolean("flagScalable", m_ADL_scalable > 0)
                .putBoolean("flagSoftPan", m_ADL_softPanEnabled > 0)
                .putBoolean("flagRunAtPcmRate", m_ADL_runAtPcmRate > 0)

                .putInt("emulator", m_ADL_emulator)
                .putInt("numChips", m_adl_numChips)
                .putInt("num4opChannels", m_ADL_num4opChannels)
                .putInt("volumeModel", m_ADL_volumeModel)

                .putFloat("gaining", (float)m_gainingLevel)
        .apply();
    }
}
