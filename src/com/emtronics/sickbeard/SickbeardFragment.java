package com.emtronics.sickbeard;

import java.io.IOException;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.ClipboardManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockFragment;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.emtronics.pythoncommon.PythonServiceAIDL;
import com.emtronics.pythoncommon.PythonServiceListener;
import com.emtronics.sickbeard.api.APIUtils;
import com.emtronics.sickbeard.api.EmptyObject;
import com.emtronics.sickbeard.api.SickbeardResponse;
import com.emtronics.sickbeard.api.SickbeardServer;
import com.google.gson.reflect.TypeToken;

public class SickbeardFragment extends SherlockFragment  {
	private final String LOG = "SickbeardFragment";

	/** Flag indicating whether we have called bind on the service. */
	boolean isBound;

	PythonServiceAIDL pythonServiceApi = null;

	Activity ctx_;

	ListView listView;
	TextView messageTextView;
	TextView ipAddrTextView;
	TextView dataDirTextView;
	TextView nzbDirTextView;
	TextView apiKeyTextView;
	ImageView runningImageView;

	BaseAdapter listAdapter;
	int listLength;
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		if (GD.DEBUG) Log.d(LOG,"onCreateView");
		ctx_ = getActivity();
		Utils.ctx = ctx_;
		SickbeardSettings.reloadSettings(ctx_);

		View view = inflater.inflate(R.layout.sb_fragment, container, false);
		setHasOptionsMenu(true);

		listView = (ListView)view.findViewById(R.id.log_listview);
		listAdapter = new MyCustomAdapter();
		listView.setAdapter(listAdapter);

		ipAddrTextView = (TextView)view.findViewById(R.id.ip_addr_text);
		dataDirTextView = (TextView)view.findViewById(R.id.data_dir_text);
		messageTextView = (TextView)view.findViewById(R.id.sb_service_message);
		nzbDirTextView = (TextView)view.findViewById(R.id.nzb_dir_text);
		apiKeyTextView= (TextView)view.findViewById(R.id.api_key_text);
		runningImageView = (ImageView)view.findViewById(R.id.running_image);
		
		apiKeyTextView.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				ClipboardManager ClipMan = (ClipboardManager) ctx_.getSystemService(ctx_.CLIPBOARD_SERVICE);
				ClipMan.setText(Utils.getAPIKey());
				Toast.makeText(ctx_, "API Key copied to clipboard", Toast.LENGTH_LONG).show();
			}
		});

		Button start_sb = (Button)view.findViewById(R.id.start_sb_button);
		start_sb.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				try {
					if (!isBound)
						doBindService();

					if (Utils.checkSystemGood(ctx_))
					{
						if (pythonServiceApi!=null)
							pythonServiceApi.startThread(SickbeardSettings.scriptPath,"SickBeard.py","--datadir "+SickbeardSettings.dataPath);
					}

				} catch (RemoteException e) {

				}
			}
		});

		Button stop_sb = (Button)view.findViewById(R.id.stop_sb_button);
		stop_sb.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				/*
				try {
					if (pythonServiceApi!=null)
						pythonServiceApi.keyboardInterrupt();
				} catch (RemoteException e) {

				}
				 */
				new ShutdownSBTask().execute("");
			}
		});

		Button browser = (Button)view.findViewById(R.id.open_sb_web_button);
		browser.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if (pythonServiceApi!=null)
				{
					try {

						String ip =  pythonServiceApi.getIPAddress();
						Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://" + ip));
						startActivity(browserIntent);
					} catch (RemoteException e) {
						listLength =  0;
					}
				}
			}
		});


		return view;
	}

	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		if (GD.DEBUG) Log.d(LOG,"onHiddenChanged hidden=" + hidden);

	}

	
	@Override
	public void onDestroy() {
		super.onDestroy();

		try {
			pythonServiceApi.removeListener(serviceListener);
			doUnbindService();
		} catch (Throwable t) {
			// catch any issues, typical for destroy routines
			// even if we failed to destroy something, we need to continue destroying
			Log.w(LOG, "Failed to unbind from the service", t);
		}
	}

	@Override
	public void onStart() {
		if (GD.DEBUG) Log.d(LOG,"onStart");
		doBindService();
		super.onStart();
	}

	@Override
	public void onStop() {
		if (GD.DEBUG) Log.d(LOG,"onStop");
		doUnbindService();
		super.onStop();
	}

	@Override
	public void onResume() {
		super.onResume();
		dataDirTextView.setText(SickbeardSettings.dataPath);
		nzbDirTextView.setText(SickbeardSettings.nzbPath);
		apiKeyTextView.setText(Utils.getAPIKey());
		updateView();
	}

	@Override
	public void onPause() {
		super.onPause();
	}
	public void updateView()
	{
		if (pythonServiceApi!=null) //Need to do here, and NOT getsize to avoid Illegal state exception
		{
			try {
				listLength =  pythonServiceApi.getLogSize();
				String ip =  pythonServiceApi.getIPAddress(); ipAddrTextView.setText(ip);
				if (pythonServiceApi.getThreadRunning() == 0)
					runningImageView.setImageResource(R.drawable.not_running);
				else
					runningImageView.setImageResource(R.drawable.running);
			} catch (RemoteException e) {
				listLength =  0;
			}
		}
		else
			listLength =  0;

		listAdapter.notifyDataSetChanged();
	}

	ServiceConnection pythonServiceConection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			if (GD.DEBUG) Log.i(LOG, "Service bound ");
			pythonServiceApi = PythonServiceAIDL.Stub.asInterface(service);
			updateView();
			try {
				pythonServiceApi.addListener(serviceListener);
			} catch (RemoteException e) {
				Log.e(LOG, "Failed to add listener", e);
			}
			setStatusMessage("Service connected");
			isBound = true;
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			if (GD.DEBUG) Log.i(LOG, "Service Unbound ");
			setStatusMessage("Error: Service disconnected");
			pythonServiceApi = null;
			isBound = false;

		}
	};

	private PythonServiceListener.Stub serviceListener = new PythonServiceListener.Stub() {

		@Override
		public void updateLog() throws RemoteException {
			// code runs in a thread
			ctx_.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					updateView();
				}
			});
		}

		@Override
		public void updateMessage(String msg) throws RemoteException {
			if (GD.DEBUG) Log.d(LOG,"updateMessage msg=" + msg);
			final String  msg_ = msg;
			ctx_.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					setStatusMessage(msg_);
				}
			});
		}

	};

	void doBindService() {
		// Establish a connection with the service.  We use an explicit
		// class name because there is no reason to be able to let other
		// applications replace our component.
		if (GD.DEBUG) Log.d(LOG,"doBindService");
		
		if (!Utils.checkRunnerLibAtLeast(ctx_, GD.runnerVersion))
		{
			Utils.showNeedRunnerLib(ctx_);
			setStatusMessage("SBRunner out of date");
			return ;
		}
		
		setStatusMessage("Connecting to service...");
		if (!isBound)
		{
			Intent svc = new Intent("com.emtronics.sickbeardrunner.PythonService");
			getActivity().startService(svc);

			boolean bound = getActivity().bindService( svc, pythonServiceConection,0);
			if (GD.DEBUG) Log.d(LOG,"doBindService bound="+bound);
			if (!bound)
				setStatusMessage("Error connecting to service");
		}
	}

	void doUnbindService() {
		if (isBound) {

			// Detach our existing connection.
			getActivity().unbindService(pythonServiceConection);
			isBound = false;

		}
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater){
		if (GD.DEBUG) Log.d(LOG,"onCreateOptionsMenu");
		inflater.inflate(R.menu.sb_menu, menu);
		return;
	}


	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int itemId = item.getItemId();
		if (itemId == R.id.menu_force_kill) {
			if (pythonServiceApi!=null)
				try {
					pythonServiceApi.exit();
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		}
		return true;
	}

	private class ShutdownSBTask extends AsyncTask<String, Integer, Long> {
		String error=null;
		protected Long doInBackground(String... urls) {
			SickbeardServer server = new SickbeardServer();

			try {
				Utils.getServerDetails(server);
			} catch (IOException e1) {
				error = e1.toString();
				e1.printStackTrace();
				return 1l;
			}

			Log.d(LOG,server.toString());
			try {
				APIUtils.sendCommand(server, "cmd=sb.shutdown",  new TypeToken<SickbeardResponse<EmptyObject>>(){}.getType());
			} catch (Exception e) {
				error = e.toString();
				e.printStackTrace();
			}
			return 0l;
		}

		protected void onProgressUpdate(Integer... progress) {

		}

		protected void onPostExecute(Long result) {
			if (error!=null)
				Toast.makeText(ctx_, "Error sending SHUTDOWN: " + error, Toast.LENGTH_LONG).show();
			else
				setStatusMessage("Sent SHUTDOWN...");
		}
	}

	private class MyCustomAdapter extends BaseAdapter {


		private LayoutInflater mInflater;

		public MyCustomAdapter() {
			mInflater = (LayoutInflater)ctx_.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		}

		@Override
		public int getCount() {
			return listLength;
		}


		@Override
		public View getView(int position, View convertView, ViewGroup parent) {

			View v = convertView;
			if (v == null) {
				LayoutInflater vi = (LayoutInflater)ctx_.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				v = vi.inflate(R.layout.log_list_item, null);
			}

			String l;	
			if (pythonServiceApi!=null)
			{
				try {
					l = pythonServiceApi.getLog(listLength-position-1);
				} catch (RemoteException e) {
					l= "ERROR";
					e.printStackTrace();
				}
			}
			else
				l = "ERROR";

			TextView tv = (TextView)v.findViewById(R.id.log_textview);
			tv.setText(l);

			return v;
		}

		@Override
		public Object getItem(int position) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public long getItemId(int position) {
			// TODO Auto-generated method stub
			return 0;
		}

	}
	private void setStatusMessage(String msg)
	{
		messageTextView.setText(msg);
	}
}
