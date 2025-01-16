package com.qualcomm.qtil.aptxacu;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;
import java.io.FileDescriptor;
import java.io.PrintWriter;

public class aptxacuService extends Service {
    private static final String APTXACU_SERVICE = "com.qualcomm.qtil.aptxacu.aptxacuService";
    private static final String TAG = "aptxacuService";
    private Messenger mMessenger;

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind: " + intent);
        if (APTXACU_SERVICE.equals(intent.getAction())) {
            mMessenger = new Messenger(new IncomingHandler(this));
            return mMessenger.getBinder();
        }
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        aptxacuApplication app = (aptxacuApplication) getApplicationContext();
        app.dump(writer);
        writer.flush();
        writer.close();
        Log.i(TAG, "dump complete");
    }

    private static class IncomingHandler extends Handler {
        private Context applicationContext;

        IncomingHandler(Context context) {
            Log.i(TAG, "IncomingHandler");
            this.applicationContext = context.getApplicationContext();
        }

        @Override
        public void handleMessage(Message msg) {
            aptxacuApplication app = (aptxacuApplication) applicationContext;
            Intent intent = (Intent) msg.obj;
            String action = intent.getAction();

            if (aptxacuALSDefs.ACTION_ALS_PREFERENCES_UPDATED.equals(action)) {
                Log.i(TAG, "handleMessage ACTION_ALS_PREFERENCES_UPDATED");
                app.alsPreferencesUpdated(intent);
            } else {
                Log.i(TAG, "handleMessage unknown action: " + action);
                super.handleMessage(msg);
            }
        }
    }
}
