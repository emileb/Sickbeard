package com.emtronics.sickbeard;

import java.io.File;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;

public class SickbeardSettings {
	private static final String LOG = "SickbeardSettings";
	public static String scriptPath;
	public static String dataPath;
	public static String nzbPath;
	
	public static void reloadSettings(Context ctx)
	{
		if (GD.DEBUG) Log.d(LOG,"reloadSettings");

		SharedPreferences prefs;// = PreferenceManager.getDefaultSharedPreferences(ctx);
		prefs = ctx.getSharedPreferences(ctx.getPackageName() + "_preferences",	Context.MODE_MULTI_PROCESS);
		
		scriptPath =  prefs.getString("sb_script_path",null);
		if (scriptPath==null)
		{
			scriptPath =  new File (Environment.getExternalStorageDirectory(),"Sickbeard/Script").toString();
			SharedPreferences.Editor editor = prefs.edit();
			editor.putString("sb_script_path",scriptPath);
			editor.commit();
		}
		
		dataPath =  prefs.getString("sb_data_path",null);
		if (dataPath==null)
		{
			dataPath =  new File (Environment.getExternalStorageDirectory(),"Sickbeard/Data").toString();
			SharedPreferences.Editor editor = prefs.edit();
			editor.putString("sb_data_path",dataPath);
			editor.commit();
		}
		
		nzbPath =  prefs.getString("sb_nzb_path",null);
		if (nzbPath==null)
		{
			nzbPath =  new File (Environment.getExternalStorageDirectory(),"Sickbeard/NZB").toString();
			SharedPreferences.Editor editor = prefs.edit();
			editor.putString("sb_nzb_path",nzbPath);
			editor.commit();
		}
		
		if (GD.DEBUG) Log.d(LOG,"scriptPath = " + scriptPath);
		if (GD.DEBUG) Log.d(LOG,"dataPath = " + dataPath);
		if (GD.DEBUG) Log.d(LOG,"nzbPath = " + nzbPath);
		
		
	}
	
	
}
