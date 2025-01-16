package com.qualcomm.qtil.aptxacu;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.UserManager;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class aptxacuApplication extends Application {
    private static final String TAG = "aptxacuApplication";

    private static final int ALS_RETRY_ATTEMPTS = 5;
    private static final int ALS_RETRY_DELAY_MS = 1000;

    private Messenger mACUService;
    private Messenger mALSService;
    private boolean mACUBound = false;
    private boolean mALSBound = false;
    private String mALSAppPackageName = "com.qualcomm.qtil.aptxals";
    private String mALSAppServiceClass = "aptxalsService";
    private String mVersionName;
    private String mPackageName;

    private final aptxacuApplicationHandler mHandler = new aptxacuApplicationHandler();
    private final List<OnStateChangedListener> mListener = new ArrayList<>();
    private final Application.ActivityLifecycleCallbacks mCallbacks = new Application.ActivityLifecycleCallbacks() {
        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}

        @Override
        public void onActivityStarted(Activity activity) {
            bindALSService();
        }

        @Override
        public void onActivityDestroyed(Activity activity) {
            unbindALSService();
        }

        @Override
        public void onActivityResumed(Activity activity) {}

        @Override
        public void onActivityPaused(Activity activity) {}

        @Override
        public void onActivityStopped(Activity activity) {}

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
    };

    private ServiceConnection mACUServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.i(TAG, "onServiceConnected: " + className);
            mACUService = new Messenger(service);
            mACUBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            Log.i(TAG, "onServiceDisconnected: " + className);
            mACUService = null;
            mACUBound = false;
        }
    };

    private ServiceConnection mALSConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.i(TAG, "onServiceConnected: " + className);
            mALSService = new Messenger(service);
            mALSBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            Log.i(TAG, "onServiceDisconnected: " + className);
            mALSService = null;
            mALSBound = false;
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate");
        printVersionInfo();

        if (!checkPermissions()) {
            throw new RuntimeException("Insufficient permissions to function.");
        }

        bindACUService();
        registerActivityLifecycleCallbacks(mCallbacks);
    }

    private boolean checkPermissions() {
        List<String> notGrantedList = new ArrayList<>();
        try {
            PackageInfo pkgInfo = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_PERMISSIONS);
            for (String perm : pkgInfo.requestedPermissions) {
                if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
                    notGrantedList.add(perm);
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Failed to get requestedPermissions " + e.getMessage());
        }

        notGrantedList.forEach(perm -> Log.w(TAG, "Not granted for " + perm));
        return notGrantedList.isEmpty();
    }

    private void printVersionInfo() {
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_META_DATA);
            if (packageInfo != null) {
                mPackageName = packageInfo.packageName;
                mVersionName = packageInfo.versionName;
                Log.i(TAG, "printVersionInfo packageName: " + mPackageName + " versionName: " + mVersionName);
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Error retrieving packageInfo: " + e.getMessage());
        }
    }

    private void bindACUService() {
        try {
            Intent mainServiceIntent = new Intent(this, aptxacuService.class);
            mainServiceIntent.setAction(aptxacuService.class.getName());
            bindService(mainServiceIntent, mACUServiceConnection, Context.BIND_AUTO_CREATE);
        } catch (SecurityException e) {
            Log.e(TAG, "Can't bind to aptxacuService");
        }
    }

    private void bindALSService() {
        if (!mALSBound) {
            try {
                Log.i(TAG, "Binding to ALS Service");
                Intent intent = new Intent();
                intent.setAction(mALSAppPackageName + "." + mALSAppServiceClass);
                intent.setComponent(new ComponentName(mALSAppPackageName, mALSAppPackageName + "." + mALSAppServiceClass));
                bindService(intent, mALSConnection, Context.BIND_AUTO_CREATE);
            } catch (SecurityException e) {
                Log.e(TAG, "Can't bind to ALS Service");
            }
        }
    }

    private void unbindALSService() {
        if (mALSBound) {
            Log.i(TAG, "Unbinding from ALS Service");
            unbindService(mALSConnection);
            mALSService = null;
            mALSBound = false;
        }
    }

    public interface OnStateChangedListener {
        void onStateChanged(aptxacuApplication aptxacuapplication);
    }

    public void registerOnStateChangedListener(OnStateChangedListener listener) {
        mListener.add(listener);
    }

    public void unregisterOnStateChangedListener(OnStateChangedListener settingsScreen) {
        mListener.remove(settingsScreen);
    }

    public void updateListeners() {
        for (OnStateChangedListener settingsScreen : mListener) {
            settingsScreen.onStateChanged(this);
        }
    }

    public String GetAptxAndAptxHdPriority() {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            return prefs.getString(aptxacuALSDefs.APTX_AND_APTX_HD_PRIORITY, "DEFAULT");
        } catch (Exception e) {
            Log.e(TAG, "Exception: " + e);
            return "DEFAULT";
        }
    }

    public String GetAptxAdaptive96KHzSampleRate() {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            boolean value = prefs.getBoolean(aptxacuALSDefs.APTX_ADAPTIVE_96KHZ_SAMPLE_RATE, false);
            return value ? "ON" : "OFF";
        } catch (Exception e) {
            Log.e(TAG, "Exception: " + e);
            return "OFF";
        }
    }

    public String GetAudioProfileOverride() {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            return prefs.getString(aptxacuALSDefs.AUDIO_PROFILE_OVERRIDE, "AUTO_ADJUST");
        } catch (Exception e) {
            Log.e(TAG, "Exception: " + e);
            return "AUTO_ADJUST";
        }
    }

    public String GetAppAudioProfilePreferenceList() {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            return prefs.getString(aptxacuALSDefs.APP_AUDIO_PROFILE_PREFERENCE_LIST, "");
        } catch (Exception e) {
            Log.e(TAG, "Exception: " + e);
            return "";
        }
    }

    public void SetAptxAndAptxHdPriority(String value) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(aptxacuALSDefs.APTX_AND_APTX_HD_PRIORITY, value);
            editor.commit();
        } catch (Exception e) {
            Log.e(TAG, "Exception: " + e);
        }
    }

    public void SetAptxAdaptive96KHzSampleRate(String value) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(aptxacuALSDefs.APTX_ADAPTIVE_96KHZ_SAMPLE_RATE, "ON".equals(value));
            editor.apply();
        } catch (Exception e) {
            Log.e(TAG, "Exception: " + e);
        }
    }

    public void SetAudioProfileOverride(String value) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(aptxacuALSDefs.AUDIO_PROFILE_OVERRIDE, value);
            editor.commit();
        } catch (Exception e) {
            Log.e(TAG, "Exception: " + e);
        }
    }

    public void SetAppAudioProfilePreferenceList(String value) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(aptxacuALSDefs.APP_AUDIO_PROFILE_PREFERENCE_LIST, value);
            editor.commit();
            acuAudioProfilePreferenceListUpdated();
        } catch (Exception e) {
            Log.e(TAG, "Exception: " + e);
        }
    }

    public void alsPreferencesUpdated(Intent intent) {
        intent.setComponent(new ComponentName(mALSAppPackageName, mALSAppPackageName + "." + mALSAppServiceClass));
        Message msg = mHandler.obtainMessage(aptxacuALSDefs.EVENT_ALS_PREFERENCES_UPDATED);
        msg.obj = intent;
        mHandler.sendMessage(msg);
    }

    public void acuAptxAndAptxHdPriority() {
        Message msg = mHandler.obtainMessage(aptxacuALSDefs.EVENT_ACU_APTX_AND_APTX_HD_PRIORITY);
        mHandler.sendMessage(msg);
    }

    public void acuAptxAdaptive96KHzSampleRate() {
        Message msg = mHandler.obtainMessage(aptxacuALSDefs.EVENT_ACU_APTX_ADAPTIVE_96KHZ_SAMPLE_RATE);
        mHandler.sendMessage(msg);
    }

    public void acuAudioProfileOverride() {
        Message msg = mHandler.obtainMessage(aptxacuALSDefs.EVENT_ACU_AUDIO_PROFILE_OVERRIDE);
        mHandler.sendMessage(msg);
    }

    public void acuAudioProfilePreferenceListUpdated() {
        Message msg = mHandler.obtainMessage(aptxacuALSDefs.EVENT_ACU_APP_AUDIO_PROFILE_PREFERENCE_LIST);
        mHandler.sendMessage(msg);
    }

    private class aptxacuApplicationHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            Intent intent = (Intent) msg.obj;
            Context context = getApplicationContext();
            switch (msg.what) {
                case aptxacuALSDefs.EVENT_ACU_APTX_AND_APTX_HD_PRIORITY:
                    handleEvent(msg, context, aptxacuALSDefs.ACTION_ACU_APTX_AND_APTX_HD_PRIORITY, aptxacuALSDefs.APTX_AND_APTX_HD_PRIORITY, GetAptxAndAptxHdPriority());
                    break;
                case aptxacuALSDefs.EVENT_ACU_APTX_ADAPTIVE_96KHZ_SAMPLE_RATE:
                    handleEvent(msg, context, aptxacuALSDefs.ACTION_ACU_APTX_ADAPTIVE_96KHZ_SAMPLE_RATE, aptxacuALSDefs.APTX_ADAPTIVE_96KHZ_SAMPLE_RATE, GetAptxAdaptive96KHzSampleRate());
                    break;
                case aptxacuALSDefs.EVENT_ACU_AUDIO_PROFILE_OVERRIDE:
                    handleEvent(msg, context, aptxacuALSDefs.ACTION_ACU_AUDIO_PROFILE_OVERRIDE, aptxacuALSDefs.AUDIO_PROFILE_OVERRIDE, GetAudioProfileOverride());
                    break;
                case aptxacuALSDefs.EVENT_ACU_APP_AUDIO_PROFILE_PREFERENCE_LIST:
                    handleEvent(msg, context, aptxacuALSDefs.ACTION_ACU_APP_AUDIO_PROFILE_PREFERENCE_LIST, aptxacuALSDefs.APP_AUDIO_PROFILE_PREFERENCE_LIST, GetAppAudioProfilePreferenceList());
                    break;
                case aptxacuALSDefs.EVENT_ALS_PREFERENCES_UPDATED:
                    if (intent != null) {
                        SetAptxAndAptxHdPriority(intent.getStringExtra(aptxacuALSDefs.APTX_AND_APTX_HD_PRIORITY));
                        SetAptxAdaptive96KHzSampleRate(intent.getStringExtra(aptxacuALSDefs.APTX_ADAPTIVE_96KHZ_SAMPLE_RATE));
                        SetAudioProfileOverride(intent.getStringExtra(aptxacuALSDefs.AUDIO_PROFILE_OVERRIDE));
                        updateListeners();
                    }
                    break;
            }
        }

        private void handleEvent(Message msg, Context context, String action, String extraKey, String extraValue) {
            if (mALSBound) {
                Intent intentALS = new Intent(context, aptxacuService.class);
                intentALS.setAction(action);
                intentALS.putExtra(extraKey, extraValue);
                Message msgALS = obtainMessage(aptxacuALSDefs.EVENT_APP_STATE_CHANGED);
                msgALS.obj = intentALS;
                try {
                    mALSService.send(msgALS);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            } else {
                retryBindService(msg);
            }
        }

        private void retryBindService(Message msg) {
            int attempt = msg.arg1;
            if (attempt > ALS_RETRY_ATTEMPTS) {
                Log.e(TAG, "Failed to bind after 5 attempts");
            } else {
                bindALSService();
                removeMessages(msg.what);
                Message msgDelayed = obtainMessage(msg.what);
                msgDelayed.arg1 = attempt + 1;
                sendMessageDelayed(msgDelayed, ALS_RETRY_DELAY_MS);
            }
        }
    }

    private static boolean isUserUnlocked(Context context) {
        UserManager userManager = (UserManager) context.getSystemService(UserManager.class);
        return userManager.isUserUnlocked();
    }

    public void dump(PrintWriter pw) {
        synchronized (this) {
            pw.println("[aptxacuApplication]");
            pw.println("aptxacu packageName: " + mPackageName + " versionName: " + mVersionName);
            pw.println("ACU Bound: " + mACUBound);

            if (mACUBound && isUserUnlocked(this)) {
                pw.println("\nUser Preferences:");
                pw.println("Set aptX and aptX HD priority: " + GetAptxAndAptxHdPriority());
                pw.println("Enable 96KHz Samplerate: " + GetAptxAdaptive96KHzSampleRate());
                pw.println("Set audio profile override: " + GetAudioProfileOverride());
                pw.println("App audio profile preference list: " + GetAppAudioProfilePreferenceList());
            }
            pw.flush();
        }
    }
}
