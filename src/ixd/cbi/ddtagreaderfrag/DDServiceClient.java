package ixd.cbi.ddtagreaderfrag;

import android.util.Log;

import com.loopj.android.http.*;

public class DDServiceClient {
	private static String BASE_URL = "http://127.0.0.1:3000/";
	public static final String TAG = DDServiceClient.class.getSimpleName();
	public static final String ACTION_NFC_ADD = "nfcadd";
	public static final String ACTION_NFC_REMOVE = "nfcremove";
	public static final String PARAM_ID = "id";
	public static final String PARAM_READER = "reader";

	private static AsyncHttpClient client = new AsyncHttpClient();
	
	public static void setBaseUrl(String baseUrl){
		BASE_URL = baseUrl;
		if( !BASE_URL.endsWith("/") )
			BASE_URL += "/";
		if(!BASE_URL.startsWith("http://") )
			BASE_URL = "http://" + BASE_URL;
		Log.i(TAG, "Change BASE_URL to " + BASE_URL);
	}

	public static void get(String url, RequestParams params, AsyncHttpResponseHandler responseHandler) {
      client.get(getAbsoluteUrl(url), params, responseHandler);
	}

	public static void post(String url, RequestParams params, AsyncHttpResponseHandler responseHandler) {
      client.post(getAbsoluteUrl(url), params, responseHandler);
	}

	private static String getAbsoluteUrl(String relativeUrl) {
      return BASE_URL + relativeUrl;
	}
	
	public static void tagAdded(String tag, String readerId){
		tagEvent(ACTION_NFC_ADD, tag, readerId);
	}
	
	public static void tagRemoved(String tag, String readerId){
		tagEvent(ACTION_NFC_REMOVE, tag, readerId);
	}
	
	private static void tagEvent(String type, String tag, String readerId){
		RequestParams params = new RequestParams();
        params.put(PARAM_ID, tag);
        params.put(PARAM_READER, readerId);
        
        DDServiceClient.get(type, params, new AsyncHttpResponseHandler(){

			@Override
			public void onFailure(Throwable e, String str) {
				Log.e(TAG, str, e);
			}

			@Override
			public void onSuccess(String arg0) {
				Log.i(TAG, arg0);
			}
        	
        });
	}
}
