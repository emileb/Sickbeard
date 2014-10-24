package com.emtronics.sickbeard;

import java.io.File;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.util.Log;

public class SickbeardSettingsActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {
	SickbeardSettingsActivity top;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		top = this;
		addPreferencesFromResource(R.xml.sab_preferences);
	}


	
	@Override
	protected void onResume() {
		super.onResume();
		// Set up a listener whenever a key changes            
		getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
	}


	@Override
	public void onPause()
	{
		super.onPause();
	
		//Log.d("test","SETTINMGS " + AppSettings.useMobileData);
		// Unregister the listener whenever a key changes            
		getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);    
	}


	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
	
		
	}




}
