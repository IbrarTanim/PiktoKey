package com.limbika.piktoplus.key;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.limbika.shared.DeviceUtils;

import org.ksoap2.SoapEnvelope;
import org.ksoap2.serialization.SoapObject;
import org.ksoap2.serialization.SoapPrimitive;
import org.ksoap2.serialization.SoapSerializationEnvelope;
import org.ksoap2.transport.HttpTransportSE;
import org.xmlpull.v1.XmlPullParserException;

/**
 *	Piktoplus installer main activity.
 */
public class MainActivity extends Activity implements OnClickListener {

	private static final String TAG = "Piktokey";
	private static final String PKG = "com.limbika.piktoplus";
	private static final String APK = "piktoplus.apk";


	public static final String NAMESPACE = "http://limbika.com/";
	public static final String URL = "http://192.52.243.6/Piktoplus/api.asmx";
	public static final String WS_TOKEN = "jh9u52^&%8189hu8!";

    public static final int progress_bar_type = 0;
    // File url to download   192.52.243.6/Piktoplus/Download.ashx?file=PiktoPlus.apk
    private static String file_url = "http://192.52.243.6/Piktoplus/Download.ashx?file=PiktoPlus.apk";


    private ProgressDialog PdSummary;
    private ProgressDialog pDialog;

    private ApiHandler			mHandler = new ApiHandler(this);
	private File				mApk;
	private LoadingView			mLoadingView;
	private LinearLayout		mNormalView;
	private LinearLayout		mInstalledView;
	private EditText edtKey, edtEmail;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
		
		File dir = getExternalCacheDir();
		mApk = new File(dir, APK);
		
		mLoadingView 	= (LoadingView)  findViewById(R.id.ll_loading);
		mNormalView		= (LinearLayout) findViewById(R.id.ll_normal);
		mInstalledView	= (LinearLayout) findViewById(R.id.ll_installed);
		edtKey = (EditText) 	 findViewById(R.id.edtKey);
		edtEmail 	= (EditText) 	 findViewById(R.id.edtEmail);


		findViewById(R.id.ll_main).setOnClickListener(this);
		findViewById(R.id.btn_normal).setOnClickListener(this);
		findViewById(R.id.btn_retry).setOnClickListener(this);
		
		setState( isInstalled() ? State.INSTALLED : State.NORMAL );
	}
	
	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.btn_normal:
			hideKeyboard();
			
			String id  = DeviceUtils.getDeviceSecureId(getApplicationContext());
			String key = edtKey.getText().toString();

			
			/* No key inserted */
			if ( key.length() == 0 ) {
				Toast.makeText(this, R.string.empty_license, Toast.LENGTH_SHORT).show();
				return;
			}
			Log.i(TAG, "id=" + id + ", key=" + key);
			setKeepScreenOn();
			//download(id, key);

			new AsycWS().execute();



			break;

		case R.id.btn_retry:
			setState(State.NORMAL);
			break;

		case R.id.ll_main:
			hideKeyboard();
			break;
		}
	}
	
	private void hideKeyboard() {
		InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.hideSoftInputFromWindow(edtKey.getWindowToken(), 0);
	}
	
	/**
	 * Handle the message of the thread.
	 * @param msg The message.
	 */
	private void handleMessage(Message msg) {
		switch (msg.what) {
		case 0:
			install();
			finish();
			break;
			
		case -4:
			setState(State.NORMAL);
			showError(R.string.incorrect);
			break;

		default:
			setState(State.NORMAL);
			showError(R.string.error);
			break;
		}
	}
	
	/**
	 * Download Piktoplus.apk to install it.
	 * @param id The id of the device.
	 * @param key The license key.
	 */
	private void download(final String id, final String key) {
		setState(State.LOADING);
		Thread thread = new Thread(new Runnable() {
			
			@Override
			public void run() {
				String token = Connector.get().gettoken(id);
				Log.d(TAG, "token=" + token);
				int what = Connector.get().download(token.trim(), key, id, mApk);
				mHandler.sendEmptyMessage(what);
			}
		});
		thread.start();
	}
	
	/**
	 * Install Piktoplus.apk into the device.
	 */
	private void install() {
		Intent intent = new Intent(Intent.ACTION_VIEW);
		intent.setDataAndType(Uri.fromFile(mApk), "application/vnd.android.package-archive");
		startActivity(intent);
	}
	
	/**
	 * @return True if Piktoplus is installed.
	 */
	private boolean isInstalled() {
		final PackageManager pm = getPackageManager();
        List<ApplicationInfo> allPackages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        for (ApplicationInfo info : allPackages) {
         	if ( PKG.equals(info.packageName) )
         		return true;
        }
		return false;
	}
	
	/**
	 * Show server's error.
	 */
	private void showError(int res) {
		AlertDialog.Builder builder = new Builder(this);
		builder.
			setTitle(R.string.app_name).
			setMessage(res).
			setPositiveButton(android.R.string.ok, null).
			create().show();
	}
	
	/**
	 * Set the view state.
	 * @param state The state.
	 */
	private void setState(State state) {
		switch (state) {
		case INSTALLED:
			mInstalledView.setVisibility(View.VISIBLE);
			mNormalView.setVisibility(View.GONE);
			mLoadingView.setVisibility(View.GONE);
			break;
			
		case LOADING:
			mInstalledView.setVisibility(View.GONE);
			mNormalView.setVisibility(View.GONE);
			mLoadingView.setVisibility(View.VISIBLE);
			break;
			
		case NORMAL:
			mInstalledView.setVisibility(View.GONE);
			mNormalView.setVisibility(View.VISIBLE);
			mLoadingView.setVisibility(View.GONE);
			break;
		}
	}
	
	/**
	 * Keep the device's screen turned on and bright.
	 */
	public void setKeepScreenOn()  {
		LayoutParams params = getWindow().getAttributes();
		params.flags |= LayoutParams.FLAG_KEEP_SCREEN_ON;
		getWindow().setAttributes(params);
	}
	
	/**
	 * View state of the activity.
	 */
	enum State {
		NORMAL,
		LOADING,
		INSTALLED
	}
	
	/**
	 *	Api call handler.
	 */
	static class ApiHandler extends Handler {
		
		private final WeakReference<MainActivity> mActivity;
		
		public ApiHandler(MainActivity activity) {
			mActivity = new WeakReference<MainActivity>(activity);
		}
		
		@Override
		public void handleMessage(Message msg) {
			mActivity.get().handleMessage(msg);
		}
		
	}


	public boolean checkRegistration() {
		Boolean WsFlag = true;
		String Method = "CheckRegistration";


		SoapObject request = new SoapObject(MainActivity.NAMESPACE, Method);
		request.addProperty("email", edtEmail.getText().toString().replace(" ", ""));
		request.addProperty("token", edtKey.getText().toString().replace(" ", ""));
		request.addProperty("deviceId", Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID));
		request.addProperty("security", WS_TOKEN);


		SoapSerializationEnvelope envelope = new SoapSerializationEnvelope(SoapEnvelope.VER11);
		envelope.dotNet = true;
		envelope.setOutputSoapObject(request);

		HttpTransportSE http = new HttpTransportSE(MainActivity.URL);

		try {
			http.call(MainActivity.NAMESPACE + Method, envelope);
			SoapPrimitive response = (SoapPrimitive) envelope.getResponse();
			String Message = response.toString();
			if(!Message.equals("ok"))
				WsFlag = false;
			System.out.println(Message);
		} catch (IOException Ex) {
			Ex.printStackTrace();
			WsFlag = false;
			System.out.println(Ex.getMessage());
		} catch (XmlPullParserException Ex) {
			Ex.printStackTrace();
			WsFlag = false;
			System.out.println(Ex.getMessage());
		} catch (Exception Ex) {
			Ex.printStackTrace();
			WsFlag = false;
			System.out.println(Ex.getMessage());
		}
		return WsFlag;
	}



	class AsycWS extends AsyncTask<String, String, String> {
		@Override
		protected void onPreExecute() {
			setState(State.LOADING);
		}

		@Override
		protected String doInBackground(String... params) {
			if (checkRegistration() == true) {
				return "ok";
			} else {
				return "err";
			}
		}

		@Override
		protected void onPostExecute(String result) {
			if (result.equals("ok")) {
				setState(State.NORMAL);

                new DownloadFileFromURL().execute(file_url);

                edtEmail.setText("");
				edtKey.setText("");
				Toast.makeText(MainActivity.this, "Registration Successful !", Toast.LENGTH_SHORT).show();
			} else {
				setState(State.NORMAL);
				Toast.makeText(MainActivity.this, "Registration Unsuccessful, Please Try Again !", Toast.LENGTH_SHORT).show();
			}
		}
	}


    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case progress_bar_type:
                pDialog = new ProgressDialog(this);
                pDialog.setMessage("Downloading file. Please wait...");
                pDialog.setIndeterminate(false);
                pDialog.setMax(100);
                pDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                pDialog.setCancelable(true);
                pDialog.show();
                return pDialog;
            default:
                return null;
        }
    }

    /**
     * Background Async Task to download file
     */
    class DownloadFileFromURL extends AsyncTask<String, String, String> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            showDialog(progress_bar_type);
        }

        /**
         * Downloading file in background thread
         */
        @Override
        protected String doInBackground(String... f_url) {
            int count;
            try {
                java.net.URL url = new URL(f_url[0]);
                URLConnection conection = url.openConnection();
                conection.connect();
                // getting file length
                int lenghtOfFile = conection.getContentLength();

                // input stream to read file - with 8k buffer
                InputStream input = new BufferedInputStream(url.openStream(), 8192);

                // Output stream to write file
                //OutputStream output = new FileOutputStream("/sdcard/piktoStorage/PiktoPlus.apk");
				OutputStream output = new FileOutputStream("/sdcard/PiktoPlus.apk");

                byte data[] = new byte[1024];

                long total = 0;

                while ((count = input.read(data)) != -1) {
                    total += count;
                    // publishing the progress....
                    // After this onProgressUpdate will be called
                    publishProgress("" + (int) ((total * 100) / lenghtOfFile));

                    // writing data to file
                    output.write(data, 0, count);
                }

                // flushing output
                output.flush();

                // closing streams
                output.close();
                input.close();

            } catch (Exception e) {
                Log.e("Error: ", e.getMessage());
            }

            return null;
        }

        /**
         * Updating progress bar
         */
        protected void onProgressUpdate(String... progress) {
            pDialog.setProgress(Integer.parseInt(progress[0]));
        }

        /**
         * After completing background task
         * Dismiss the progress dialog
         **/
        @Override
        protected void onPostExecute(String file_url) {


			Intent intent = new Intent(Intent.ACTION_VIEW);
			//File file = new File("/sdcard/piktoStorage/PiktoPlus.apk");
			File file = new File("/sdcard/PiktoPlus.apk");
			intent.setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive");
			startActivity(intent);
            dismissDialog(progress_bar_type);

        }

    }




}
