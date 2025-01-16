package com.qualcomm.qtil.aptxacu;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class aptxacuProfilePreferenceListActivity extends CollapsingToolbarBaseActivity {

    private static final String TAG = "aptxacuProfilePreferenceListActivity";
    private static final String USER_PREF_FILE_NAME = "user-profile-prefs.json";

    private AppAdapter installedAppAdapter;
    private List<AppList> installedApps;
    private Map<String, String> profilePreferenceListMap;
    private String ACUUserPrefFilePath;
    private RecyclerView userInstalledApps;
    private final aptxacuProfilePreferenceListHandler handler = new aptxacuProfilePreferenceListHandler(this);
    private Context context;
    private aptxacuApplication app;
    private String[] spinnerLabels;
    private String[] spinnerValues;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");

        initializeFields();
        if (!isAppValid()) return;

        profilePreferenceListMap = new ConcurrentHashMap<>();
        if (getACUFilePath()) {
            Log.i(TAG, "Profile Preferences User File is: " + ACUUserPrefFilePath);
            loadPrefFile(ACUUserPrefFilePath);
        }

        setContentView(R.layout.activity_app_profile_list);

        setupUIComponents();
        setupRecyclerView();
        getActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void initializeFields() {
        context = getApplicationContext();
        app = (aptxacuApplication) context;
    }

    private boolean isAppValid() {
        if (app == null) {
            Log.e(TAG, "Unable to get app");
            return false;
        }
        return true;
    }

    private void setupRecyclerView() {
        userInstalledApps = findViewById(R.id.recyclerview);
        userInstalledApps.setLayoutManager(new LinearLayoutManager(this, RecyclerView.VERTICAL, false));
        installedApps = getInstalledApps();
        installedAppAdapter = new AppAdapter(this, installedApps, new ArrayList<>(List.of(spinnerLabels)), new ArrayList<>(List.of(spinnerValues)));
        userInstalledApps.setAdapter(installedAppAdapter);
    }

    private void setupUIComponents() {
        spinnerLabels = getResources().getStringArray(R.array.spinner_labels);
        spinnerValues = getResources().getStringArray(R.array.spinner_values);
    }

    private List<AppList> getInstalledApps() {
        PackageManager pkgMgr = getPackageManager();
        List<AppList> apps = new ArrayList<>();
        List<PackageInfo> installedPackages = pkgMgr.getInstalledPackages(PackageManager.GET_META_DATA);

        for (PackageInfo pkgInfo : installedPackages) {
            if (!isSystemPackage(pkgInfo)) {
                String appName = pkgInfo.applicationInfo.loadLabel(pkgMgr).toString();
                Drawable icon = pkgInfo.applicationInfo.loadIcon(pkgMgr);
                String packageName = pkgInfo.applicationInfo.packageName;
                String profilePref = profilePreferenceListMap.getOrDefault(packageName, spinnerValues[0]);
                apps.add(new AppList(appName, icon, packageName, profilePref));
            }
        }

        apps.sort(AppList.AppNameComparator);
        return apps;
    }

    private boolean isSystemPackage(PackageInfo pkgInfo) {
        return (pkgInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
    }

    private void loadPrefFile(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            Log.e(TAG, "File path is null or empty.");
            return;
        }

        File file = new File(filePath);
        if (!file.exists()) {
            Log.w(TAG, "Profile Preference File " + filePath + " does not exist: skipping");
            return;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            StringBuilder jsonStringBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonStringBuilder.append(line);
            }

            JSONArray jsonArray = new JSONArray(jsonStringBuilder.toString());
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.optJSONObject(i);
                if (jsonObject != null) {
                    jsonObject.keys().forEachRemaining(key -> profilePreferenceListMap.put(key, jsonObject.optString(key)));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading Profile Preference File: " + filePath, e);
        }
    }

    private void saveAppListToJson(List<AppList> installedApps) {
        JSONArray jsonArray = new JSONArray();
        for (AppList appList : installedApps) {
            JSONObject jsonObj = new JSONObject();
            try {
                jsonObj.put(appList.packageName, appList.profilePref);
            } catch (JSONException e) {
                Log.e(TAG, "saveAppListToJson error: " + e.toString());
            }
            jsonArray.put(jsonObj);
        }

        setAppAudioProfilePreferenceList(jsonArray);
        if (getACUFilePath()) {
            try (FileWriter file = new FileWriter(ACUUserPrefFilePath, false)) {
                file.write(jsonArray.toString());
                file.close();
            } catch (Exception e) {
                Log.e(TAG, "saveAppListToJson error: " + e.toString());
            }
        }
    }

    private void setAppAudioProfilePreferenceList(JSONArray jsonArray) {
        String audioProfilePreferenceList = jsonArray.toString();
        Message msg = handler.obtainMessage(aptxacuALSDefs.EVENT_APP_STATE_CHANGED);
        msg.obj = audioProfilePreferenceList;
        handler.sendMessage(msg);
    }

    private boolean getACUFilePath() {
        try {
            ACUUserPrefFilePath = getFilesDir().getAbsolutePath() + "/" + USER_PREF_FILE_NAME;
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Exception on Profile Preference File resource " + e.toString());
            return false;
        }
    }

    @Override
    public void onPause() {
        saveAppListToJson(installedApps);
        super.onPause();
    }

    public static class AppList {
        public static final Comparator<AppList> AppNameComparator = (a1, a2) -> a1.appName.toUpperCase().compareTo(a2.appName.toUpperCase());

        private final String appName;
        private final Drawable icon;
        private final String packageName;
        private String profilePref;

        public AppList(String appName, Drawable icon, String packageName, String profilePref) {
            this.appName = appName;
            this.icon = icon;
            this.packageName = packageName;
            this.profilePref = profilePref;
        }

        public String getName() {
            return this.appName;
        }

        public Drawable getIcon() {
            return this.icon;
        }

        public String getPackages() {
            return this.packageName;
        }

        public String getProfilePref() {
            return this.profilePref;
        }

        public void setProfilePref(String profilePref) {
            this.profilePref = profilePref;
        }
    }

    public class AppAdapter extends RecyclerView.Adapter<AppAdapter.ViewHolder> {
        private final LayoutInflater layoutInflater;
        private final List<AppList> listStorage;
        private final ArrayList<String> spinnerLabels;
        private final ArrayList<String> spinnerValues;

        public AppAdapter(Context context, List<AppList> listStorage, ArrayList<String> spinnerLabels, ArrayList<String> spinnerValues) {
            this.layoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            this.listStorage = listStorage;
            this.spinnerLabels = spinnerLabels;
            this.spinnerValues = spinnerValues;
        }

        @Override
        public int getItemCount() {
            return listStorage.size();
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = layoutInflater.inflate(R.layout.installed_app_list, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            AppList appList = listStorage.get(position);
            holder.textInListView.setText(appList.getName());
            holder.imageInListView.setImageDrawable(appList.getIcon());
            
            ArrayAdapter<String> adapter = new ArrayAdapter<>(layoutInflater.getContext(), android.R.layout.simple_spinner_item, spinnerLabels);
            adapter.setDropDownViewResource(R.layout.profile_spinner_dropdown_item);
            holder.spinnerInListView.setAdapter(adapter);
            holder.spinnerInListView.setSelection(spinnerValues.indexOf(appList.getProfilePref()));

            holder.spinnerInListView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parentView, View view, int spnPosition, long id) {
                    appList.setProfilePref(spinnerValues.get(spnPosition));
                }

                @Override
                public void onNothingSelected(AdapterView<?> parentView) {}
            });
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView imageInListView;
            Spinner spinnerInListView;
            TextView textInListView;

            public ViewHolder(View view) {
                super(view);
                imageInListView = view.findViewById(R.id.app_icon);
                spinnerInListView = view.findViewById(R.id.spinner);
                textInListView = view.findViewById(R.id.list_app_name);
            }
        }
    }

    private static class aptxacuProfilePreferenceListHandler extends Handler {
        private final aptxacuProfilePreferenceListActivity activity;

        public aptxacuProfilePreferenceListHandler(aptxacuProfilePreferenceListActivity activity) {
            this.activity = activity;
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == aptxacuALSDefs.EVENT_APP_STATE_CHANGED) {
                String audioProfilePreferenceList = (String) msg.obj;
                activity.app.SetAppAudioProfilePreferenceList(audioProfilePreferenceList);
            }
        }
    }
}
