package com.emtronics.sickbeard.api;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;

import android.util.Log;

import com.emtronics.sickbeard.GD;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public class APIUtils {
	final static String LOG = "SABApiUtils";

	public static class MySSLSocketFactory extends org.apache.http.conn.ssl.SSLSocketFactory {
		SSLContext sslContext = SSLContext.getInstance("TLS");

		public MySSLSocketFactory(KeyStore truststore) throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException {
			super(truststore);

			TrustManager tm = new X509TrustManager() {
				public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
				}

				public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
				}

				public X509Certificate[] getAcceptedIssuers() {
					return null;
				}
			};

			sslContext.init(null, new TrustManager[] { tm }, null);
		}

		@Override
		public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException, UnknownHostException {
			return sslContext.getSocketFactory().createSocket(socket, host, port, autoClose);
		}

		@Override
		public Socket createSocket() throws IOException {
			return sslContext.getSocketFactory().createSocket();
		}
	}

	public static HttpClient getNewHttpClient(SickbeardServer server) {

		if (!server.isSsl())
		{
			return new DefaultHttpClient();
		}
		else
		{

			try {
				KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
				trustStore.load(null, null);

				SSLSocketFactory sf = new MySSLSocketFactory(trustStore);
				sf.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

				HttpParams params = new BasicHttpParams();
				HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
				HttpProtocolParams.setContentCharset(params, HTTP.UTF_8);

				SchemeRegistry registry = new SchemeRegistry();
				registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
				registry.register(new Scheme("https", sf, 443));

				ClientConnectionManager ccm = new ThreadSafeClientConnManager(params, registry);

				return new DefaultHttpClient(ccm, params);
			} catch (Exception e) {
				return new DefaultHttpClient();
			}
		}
	}

	public static String getSABQueueOutput(SickbeardServer server,String queue) throws ClientProtocolException, IOException
	{
		HttpClient httpclient =  getNewHttpClient(server);


		HttpPost httppost = new HttpPost(server.getHost() + ":" + server.getPort() + "/api?");

		List<NameValuePair> nameValuePairs =  new ArrayList<NameValuePair>();
		nameValuePairs.add(new BasicNameValuePair("mode",  queue));
		nameValuePairs.add(new BasicNameValuePair("output","json"));
		nameValuePairs.add(new BasicNameValuePair("apikey",server.getApi()));
		httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs));

		HttpResponse httpResponse = httpclient.execute(httppost);

		int code = httpResponse.getStatusLine().getStatusCode();
		StringBuilder xmldata = new StringBuilder();
		if (code == 200) //OK
		{
			BufferedReader rd = new BufferedReader(new InputStreamReader(
					httpResponse.getEntity().getContent()));

			String line = "";

			while ((line = rd.readLine()) != null) {
				//Log.d(LOG,"xml = " + line);
				xmldata.append(line);
			}
		}
		return xmldata.toString();
	}

	
	public static <T> SickbeardResponse<T>  sendCommand(SickbeardServer server,String cmd, Type type ) throws Exception
	{
		HttpClient httpclient =  getNewHttpClient(server);

		String url = server.getHost() + ":" + server.getPort() + "/api/" + server.getApi()+"/?" + cmd;
		if (GD.DEBUG) Log.d(LOG,"url = " + url);
		HttpPost httppost = new HttpPost(url);
		

		HttpResponse httpResponse = httpclient.execute(httppost);
		int code = httpResponse.getStatusLine().getStatusCode();
		StringBuilder jsondata = new StringBuilder();
		
		if (code == 200) //OK
		{
			BufferedReader rd = new BufferedReader(new InputStreamReader(
					httpResponse.getEntity().getContent()));

			String line = "";

			while ((line = rd.readLine()) != null) {
				//Log.d(LOG,"xml = " + line);
				jsondata.append(line);
			}
			rd.close();
		}
		
		GsonBuilder build = new GsonBuilder();
		SickbeardResponse<T> response = null;
		try {
			response = build.create().fromJson( jsondata.toString(), type );
		
			tryExtractError(response);
			return response;
		} catch (Exception e) {
			e.printStackTrace();
			// well something messed up
			// if this part messes up then something REALLY bad happened
			response = build.create().fromJson( jsondata.toString(), new TypeToken<SickbeardResponse<Object>>(){}.getType());
			tryExtractError(response);
			// DO NOT RETURN AN ACTUAL OBJECT!!!!!
			// this makes the code in the UI confused
			return null;
		}
	}

	private static <T> void tryExtractError(SickbeardResponse<T> response) throws Exception {
		if ( response.result.compareTo("error") == 0 || response.result.compareTo("failure") == 0 ) {
			if ( response.message != null && response.message.length() > 0 )
				throw new Exception( response.message );
			else if ( response.data != null && response.data.toString().length() > 0 )
					throw new Exception( response.data.toString() );
			throw new Exception( "Unknown Error occurred ... Ut Oh.");
		}
	}
	public static int uploadFile(SickbeardServer server, String filepath,String category) throws IOException, NoSuchAlgorithmException, KeyManagementException
	{
		if (GD.DEBUG) Log.d(LOG,"filepath = " + filepath);

		File file =  new File(filepath);
		String filename =  URLEncoder.encode(file.getName(), "UTF-8");


		long epoch = System.currentTimeMillis()/1000;
		String boundary = "---------------------------" + epoch;
		// Create the body for the request (POST)
		String bodyTop = "--" + boundary + "\nContent-Disposition: form-data; name=\"name\"; filename=\"" +filename + "\"\n";
		// StreamReader to read the NZB File

		if (GD.DEBUG) Log.d("uploadFile","body = " + bodyTop);


		// Read the NZB and add to the request body
		bodyTop += "Content-Type: application/x-nzb\n\n";
		//+ fileContent.toString();

		// Category
		String bodyBot = "\n--" + boundary + "\nContent-Disposition: form-data; name=\"cat\"\n\n" + category;
		// Priority
		bodyBot += "\n--" + boundary + "\nContent-Disposition: form-data; name=\"pp\"\n\n" + "-1";
		// Scripts
		bodyBot += "\n--" + boundary + "\nContent-Disposition: form-data; name=\"script\"\n\n" + "Default";

		bodyBot += "\n--" + boundary + "--\n\n";

		// Build the URL
		String sabURL = server.getHost() + ":" + server.getPort() + "/sabnzbd/api?mode=addfile&name=" + filename + "&apikey=" + server.getApi();
		if (GD.DEBUG) Log.d("uploadFile","sabURL = " + sabURL);

		if (server.isSsl())
		{
			TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
				public java.security.cert.X509Certificate[] getAcceptedIssuers() {
					return null;
				}
				public void checkClientTrusted(X509Certificate[] certs, String authType) {
				}
				public void checkServerTrusted(X509Certificate[] certs, String authType) {
				}
			} };
			// Install the all-trusting trust manager
			final SSLContext sc = SSLContext.getInstance("SSL");
			sc.init(null, trustAllCerts, new java.security.SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
			// Create all-trusting host name verifier
			HostnameVerifier allHostsValid = new HostnameVerifier() {
				public boolean verify(String hostname, SSLSession session) {
					return true;
				}
			};

			// Install the all-trusting host verifier
			HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
		}

		URL url = new URL(sabURL);
		
		HttpURLConnection conn;
		
		if (server.isSsl())
			conn = (HttpsURLConnection) url.openConnection();
		else
			conn = (HttpURLConnection) url.openConnection();

		conn.setConnectTimeout(8 * 1000);
		conn.setDoOutput(true);
		conn.setDoOutput(true); // Allow Outputs
		conn.setUseCaches(false); // Don't use a Cached Copy
		conn.setRequestMethod("POST");
		conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
		//conn.setRequestProperty("Content-Length", Integer.toString(bodyBytes.length));

		DataOutputStream   dos = new DataOutputStream(conn.getOutputStream());
		dos.write(bodyTop.getBytes("UTF-8"));

		FileInputStream fis = new FileInputStream(file);

		byte[] buffer = new byte[1024];
		int length;
		while ((length = fis.read(buffer)) != -1) {
			dos.write(buffer, 0, length);
		}
		fis.close();

		dos.write(bodyBot.getBytes("UTF-8"));

		dos.flush();
		dos.close();

		InputStream is = conn.getInputStream();
		BufferedReader rd = new BufferedReader(new InputStreamReader(is));
		String line;
		StringBuffer response = new StringBuffer(); 
		while((line = rd.readLine()) != null) {
			response.append(line);
			response.append('\r');
		}

		if (GD.DEBUG) Log.d("uploadFile","RESPONCE =  = " + response.toString());

		int  serverResponseCode = conn.getResponseCode();
		String serverResponseMessage = conn.getResponseMessage();

		if (GD.DEBUG) Log.d("uploadFile","serverResponseCode = " + serverResponseCode);
		if (GD.DEBUG) Log.d("uploadFile","serverResponseMessage = " + serverResponseMessage);


		conn.disconnect();

		return serverResponseCode;
	}

}
