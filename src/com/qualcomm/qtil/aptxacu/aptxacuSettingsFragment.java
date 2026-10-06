package com.qualcomm.qtil.aptxacu;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

public class aptxacuSettingsFragment extends SettingsBasePreferenceFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener, aptxacuApplication.OnStateChangedListener {

    private static final String TAG = "aptxacuSettingsFragment";
    private static final String PREF_CAT_CODEC_AUDIO_PROFILE = "pref_cat_codec_audio_profile";
    private static final String PREF_AUDIO_PROFILE = "pref_audio_profile_preference";

    private SwitchPreferenceCompat mAptxAdaptive96KHzSampleRate;
    private ListPreference mAptxAndAptxHdPriority;
    private ListPreference mAudioProfileOverride;
    private Preference mAudioProfilePreferenceList;

    private aptxacuApplication mApp;
    private Context mContext;
    private boolean mResumed = false;
    private final aptxacuSettingsHandler mHandler = new aptxacuSettingsHandler();

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
        mContext = requireContext().getApplicationContext();
        mApp = (aptxacuApplication) mContext.getApplicationContext();

        if (mApp == null) {
            Log.e(TAG, "unable to get app");
            return;
        }

        setupPreferences();
        PreferenceManager.setDefaultValues(mContext, R.xml.preferences, false);
        update();
    }

    private void setupPreferences() {
        mAptxAndAptxHdPriority = setupListPreference(aptxacuALSDefs.APTX_AND_APTX_HD_PRIORITY, R.string.aptx_and_aptx_hd_priority_dialog_title);
        mAptxAdaptive96KHzSampleRate = setupSwitchPreference(aptxacuALSDefs.APTX_ADAPTIVE_96KHZ_SAMPLE_RATE);

        setupPreferenceCategory(PREF_CAT_CODEC_AUDIO_PROFILE);
        mAudioProfileOverride = setupListPreference(aptxacuALSDefs.AUDIO_PROFILE_OVERRIDE, R.string.audio_profile_override_dialog_title);

        mAudioProfilePreferenceList = findPreference(PREF_AUDIO_PROFILE);
        if (mAudioProfilePreferenceList != null) {
            mAudioProfilePreferenceList.setOnPreferenceClickListener(preference -> {
                startActivity(new Intent(mContext, aptxacuProfilePreferenceListActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK));
                return true;
            });
        }
    }

    private void setupPreferenceCategory(String key) {
        PreferenceCategory prefCat = findPreference(key);
        if (prefCat != null) {
            prefCat.setTitle(mContext.getString(R.string.pref_cat_codec_audio_profile));
            prefCat.setIconSpaceReserved(false);
        }
    }

    private ListPreference setupListPreference(String key, int titleResId) {
        ListPreference listPreference = findPreference(key);
        if (listPreference != null) {
            listPreference.setTitle(mContext.getString(titleResId));
            listPreference.setIconSpaceReserved(false);
            listPreference.setSingleLineTitle(false);
            listPreference.setPersistent(true);
        }
        return listPreference;
    }

    private SwitchPreferenceCompat setupSwitchPreference(String key) {
        SwitchPreferenceCompat switchPreference = findPreference(key);
        if (switchPreference != null) {
            switchPreference.setIconSpaceReserved(false);
            switchPreference.setSingleLineTitle(false);
            switchPreference.setPersistent(true);
        }
        return switchPreference;
    }

    @Override
    public void onResume() {
        super.onResume();
        mApp.registerOnStateChangedListener(this);
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        mResumed = true;
        update();
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        mApp.unregisterOnStateChangedListener(this);
        mResumed = false;
    }

    @Override
    public void onStateChanged(aptxacuApplication app) {
        update();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Message msg = mHandler.obtainMessage(getEventForKey(key));
        msg.obj = key;
        mHandler.sendMessage(msg);
        update();
    }

    private int getEventForKey(String key) {
        switch (key) {
            case aptxacuALSDefs.APTX_AND_APTX_HD_PRIORITY:
                return aptxacuALSDefs.EVENT_ACU_APTX_AND_APTX_HD_PRIORITY;
            case aptxacuALSDefs.APTX_ADAPTIVE_96KHZ_SAMPLE_RATE:
                return aptxacuALSDefs.EVENT_ACU_APTX_ADAPTIVE_96KHZ_SAMPLE_RATE;
            case aptxacuALSDefs.AUDIO_PROFILE_OVERRIDE:
                return aptxacuALSDefs.EVENT_ACU_AUDIO_PROFILE_OVERRIDE;
            default:
                return -1;
        }
    }

    public void update() {
        if (!mResumed) return;

        updateAptxAndAptxHdPriority();
        updateAptxAdaptive96KHzSampleRate();
        updateAudioProfileOverride();
    }

    private void updateAptxAndAptxHdPriority() {
        String val = mApp.GetAptxAndAptxHdPriority();
        if (mAptxAndAptxHdPriority != null) {
            if (mAptxAndAptxHdPriority.getValue() == null) {
                mAptxAndAptxHdPriority.setValueIndex(0);
            }
            switch (val.toUpperCase()) {
                case "DEFAULT": mAptxAndAptxHdPriority.setValueIndex(0); break;
                case "APTX": mAptxAndAptxHdPriority.setValueIndex(1); break;
                case "APTX_HD": mAptxAndAptxHdPriority.setValueIndex(2); break;
            }
        }
    }

    private void updateAptxAdaptive96KHzSampleRate() {
        String val = mApp.GetAptxAdaptive96KHzSampleRate();
        if (mAptxAdaptive96KHzSampleRate != null) {
            mAptxAdaptive96KHzSampleRate.setChecked("ON".equals(val));
        }
    }

    private void updateAudioProfileOverride() {
        String val = mApp.GetAudioProfileOverride();
        if (mAudioProfileOverride != null) {
            if (mAudioProfileOverride.getValue() == null) {
                mAudioProfileOverride.setValueIndex(0);
                mAudioProfilePreferenceList.setEnabled(true);
            }
            switch (val) {
                case "AUTO_ADJUST":
                    mAudioProfileOverride.setValueIndex(0);
                    mAudioProfilePreferenceList.setEnabled(true);
                    break;
                case "HIGH_QUALITY":
                    mAudioProfileOverride.setValueIndex(1);
                    mAudioProfilePreferenceList.setEnabled(false);
                    break;
                case "GAMING_MODE":
                    mAudioProfileOverride.setValueIndex(2);
                    mAudioProfilePreferenceList.setEnabled(false);
                    break;
            }
        }
    }

    private class aptxacuSettingsHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            if (msg == null) return;
            switch (msg.what) {
                case aptxacuALSDefs.EVENT_ACU_APTX_AND_APTX_HD_PRIORITY:
                    mApp.acuAptxAndAptxHdPriority();
                    break;
                case aptxacuALSDefs.EVENT_ACU_APTX_ADAPTIVE_96KHZ_SAMPLE_RATE:
                    mApp.acuAptxAdaptive96KHzSampleRate();
                    break;
                case aptxacuALSDefs.EVENT_ACU_AUDIO_PROFILE_OVERRIDE:
                    mApp.acuAudioProfileOverride();
                    break;
                default:
                    Log.e(TAG, "Unknown event received in handler");
            }
        }
    }
}
