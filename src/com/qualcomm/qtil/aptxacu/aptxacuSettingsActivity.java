package com.qualcomm.qtil.aptxacu;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;
import com.android.settingslib.collapsingtoolbar.R;

public class aptxacuSettingsActivity extends CollapsingToolbarBaseActivity {

    private aptxacuSettingsFragment mFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FragmentManager fragmentManager = getSupportFragmentManager();
        Fragment fragment = fragmentManager.findFragmentById(R.id.content_frame);
        if (fragment == null) {
            mFragment = new aptxacuSettingsFragment();
            fragmentManager.beginTransaction()
                .add(R.id.content_frame, mFragment)
                .commit();
        } else if (fragment instanceof aptxacuSettingsFragment) {
            mFragment = (aptxacuSettingsFragment) fragment;
        }

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
}
