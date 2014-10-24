package com.emtronics.sickbeard;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.FileUtils;

import com.emtronics.sickbeard.api.SickbeardServer;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

public class Utils {

	private static String LOG = "Utils";

	public static boolean checkSystemGood(Context ctx)
	{
		Utils.ctx = ctx;
		//Check runner
		if (!checkRunnerLibAtLeast(ctx, GD.runnerVersion))
		{
			showNeedRunnerLib(ctx);
			return false;
		}

		//Check sickbeard script
		File scriptDir = new File(SickbeardSettings.scriptPath);
		if (!(new File (scriptDir,"SickBeard.py").exists()))
		{
			if (!scriptDir.exists())
				scriptDir.mkdirs();

			if (!scriptDir.canWrite())
			{
				final AlertDialog.Builder alert = new AlertDialog.Builder(ctx);
				alert.setMessage("Error, can not write to directory to install Sickbeard: " + scriptDir.toString() + ". Can not continue.");

				alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {

					}
				});
				alert.show();
				return false;
			}

			//Now ask to download sickbeard
			final AlertDialog.Builder alert = new AlertDialog.Builder(ctx);
			alert.setMessage("Need to download the Sickbeard Python files (approx 3MB). Click OK to continue");

			alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {
					new DLFileThread().execute("");
				}
			});
			alert.setNegativeButton("Cancel", new OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.cancel();
				}
			});
			alert.show();

			return false;
		}

		//Check config.ini exists
		File dataDir = new File(SickbeardSettings.dataPath);
		if (!(new File (dataDir,"config.ini").exists()))
		{
			if (!dataDir.exists())
				dataDir.mkdirs();
			try {
				copyFile(new FileInputStream(new File (scriptDir,"config.ini")),new FileOutputStream(new File (dataDir,"config.ini")));
			} catch (FileNotFoundException e) {
				Toast.makeText(ctx, "Error copying config.ini file: " +e.toString(), Toast.LENGTH_LONG).show();
				return false;
			} catch (IOException e) {
				Toast.makeText(ctx, "Error copying config.ini file: " +e.toString(), Toast.LENGTH_LONG).show();
				return false;
			}
		}

		//Check nzb dir exists and writable
		File nzbDir = new File(SickbeardSettings.nzbPath);
		if (!nzbDir.exists())
			nzbDir.mkdirs();
		
		if (!nzbDir.canWrite())
		{
			final AlertDialog.Builder alert = new AlertDialog.Builder(ctx);
			alert.setMessage("Error, can not write to NZB output directory:" + nzbDir.toString() + ". Can not continue.");

			alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {

				}
			});
			alert.show();
			return false;
		}
		
		try {
			List<String> lines = FileUtils.readLines(new File (dataDir,"config.ini"), "utf-8");
			for (int n=0;n<lines.size();n++)
			{
				String l = lines.get(n);
				if (l.startsWith("nzb_dir ="))
				{
					String new_l = "nzb_dir = \"" + SickbeardSettings.nzbPath + "\"";
					lines.set(n, new_l);
				}
			}
			FileUtils.writeLines(new File (dataDir,"config.ini"), lines);
			
		} catch (IOException e) {
			Toast.makeText(ctx, "Error modifying config.ini file: " +e.toString(), Toast.LENGTH_LONG).show();
			return false;
		}
		
		return true;
	}

	static public Context ctx;
	private static class DLFileThread extends AsyncTask<String, Integer, Long> {

		private ProgressDialog progressBar;
		String errorstring= null;

		@Override
		protected void onPreExecute() {
			progressBar = new ProgressDialog(ctx); 
			progressBar.setMessage("Downloading/Extracting files..");
			progressBar.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			progressBar.setCancelable(false);
			progressBar.show();
		}

		protected Long doInBackground(String... fn) {

			progressBar.setProgress(0);

			File outZipFile = new File(SickbeardSettings.scriptPath,"temp.zip");
			try
			{
				BufferedInputStream in = null;
				FileOutputStream fout = null;
				URL url = new URL("http://www.powernzb.co.uk/files/sickbeard_1.zip");
				URLConnection con;
				con = url.openConnection();
				con.setRequestProperty("User-Agent", 
						"Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1; .NET CLR 1.0.3705; .NET CLR 1.1.4322; .NET CLR 1.2.30703)");

				con.setConnectTimeout(4000);
				con.setReadTimeout(8000);
				InputStream ins = con.getInputStream();
				int dlSize = con.getContentLength();
				progressBar.setMax(dlSize);
				if (GD.DEBUG) Log.d(LOG,"File size = " + dlSize);

				in = new BufferedInputStream(ins);
				fout = new FileOutputStream(outZipFile);

				byte data[] = new byte[1024];
				int count;
				while ((count = in.read(data, 0, 1024)) != -1)
				{
					fout.write(data, 0, count);
					progressBar.setProgress(progressBar.getProgress() + count);
				}
				in.close();
				fout.close();
			} catch (IOException e) {
				errorstring = e.toString();
				return 1l;
			}

			InputStream in = null;
			OutputStream out = null;

			progressBar.setMax(650);
			progressBar.setProgress(0);
			try {
				ZipFile zipFile = new ZipFile(outZipFile);
				Enumeration entries = zipFile.entries();
				while(entries.hasMoreElements()) {
					ZipEntry entry = (ZipEntry)entries.nextElement();

					if(entry.isDirectory()) {
						// Assume directories are stored parents first then children.
						System.err.println("Extracting directory: " + entry.getName());
						// This is not robust, just for demonstration purposes.
						(new File(SickbeardSettings.scriptPath, entry.getName())).mkdirs();
						continue;
					}

					(new File(SickbeardSettings.scriptPath, entry.getName())).getParentFile().mkdirs();
					in = zipFile.getInputStream(entry);
					out =  new FileOutputStream(new File(SickbeardSettings.scriptPath, entry.getName()));
					copyFile(in,out);
					progressBar.setProgress(progressBar.getProgress() + 1);
				}
				//zipFile.close();
			} catch (IOException ioe) {
				if (GD.DEBUG) Log.d(LOG, "Error reading zip " + out + " - " + ioe.toString());
				errorstring = ioe.toString();
				return 1l;
			}


			outZipFile.delete();

			return 0l;
		}

		protected void onProgressUpdate(Integer... progress) {

		}

		protected void onPostExecute(Long result) {
			progressBar.dismiss();
			if (errorstring!=null)
				Toast.makeText(ctx, "Error: " + errorstring, Toast.LENGTH_LONG).show();
		}
	}

	static private void copyFile(InputStream in, OutputStream out) throws IOException {
		byte[] buffer = new byte[1024];
		int read;
		while((read = in.read(buffer)) != -1){
			out.write(buffer, 0, read);
		}
		out.close(); 
	}

	public static boolean checkRunnerLibAtLeast(Context ctx,int versioncode)
	{
		boolean parlibGood = false;

		try{
			PackageInfo pinfo = ctx.getPackageManager().getPackageInfo("com.emtronics.sickbeardrunner", 0 );
			if (pinfo.versionCode >= versioncode)
				parlibGood = true;
			else
				parlibGood = false;

		} catch( PackageManager.NameNotFoundException e ){
			//if (GD.DEBUG) Log.d(LOG, "NOT Exists");
			parlibGood = false;
		}
		return parlibGood;
	}

	public static void showNeedRunnerLib(final Context ctx)
	{
		final AlertDialog.Builder alert = new AlertDialog.Builder(ctx);
		alert.setMessage("You need to install latest version of the Sickbeard Runner, this can be downloaded for free from Google Play. Open Google Play now?");
		alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {

				Intent marketIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.emtronics.sickbeardrunner"));
				marketIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
				ctx.startActivity(marketIntent);
			}
		});

		alert.setNegativeButton("Cancel",
				new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				dialog.cancel();
			}
		});
		alert.show();
	}
	
	public static String getAPIKey()
	{
		try {
			List<String> lines = FileUtils.readLines(new File (SickbeardSettings.dataPath + "/config.ini"), "utf-8");
			for (int n=0;n<lines.size();n++)
			{
				String l = lines.get(n);
				if (l.startsWith("api_key ="))
				{
					return l.substring(l.lastIndexOf(' ')+1);
				}
			}		
		} catch (IOException e) {
			Toast.makeText(ctx, "Error finding config.ini file (Sickbeard installed?): " +e.toString(), Toast.LENGTH_LONG).show();
			return "Error";
		}
		return "Error";
	}
	
	public static void getServerDetails(SickbeardServer server) throws IOException
	{
		List<String> lines = FileUtils.readLines(new File (SickbeardSettings.dataPath + "/config.ini"), "utf-8");
		for (int n=0;n<lines.size();n++)
		{
			String l = lines.get(n);
			if (l.startsWith("api_key ="))
			{
				server.setApi(l.substring(l.lastIndexOf(' ')+1));
			}
			else if (l.startsWith("web_port ="))
			{
				server.setPort(l.substring(l.lastIndexOf(' ')+1));
			}
			else if (l.startsWith("web_host ="))
			{
				server.setHost(l.substring(l.lastIndexOf(' ')+1));
			}
			else if (l.startsWith("enable_https ="))
			{
				server.setSsl(l.substring(l.lastIndexOf(' ')+1).contentEquals("0")?false:true);
			}
		}	
		if (server.getApi() == null)
			throw new IOException("API key not found in config.ini");
		
		if (server.getPort() == null)
			throw new IOException("Port not found in config.ini");
		
		if (server.getHost() == null)
			throw new IOException("Host not found in config.ini");
		
	}
	
}
