package ixd.cbi.ddtagreaderfrag;

import ixd.cbi.ddtagreader.R;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.Toast;

public class MainActivity extends Activity 
	implements	MainViewFragment.OnMessageSelectedListener, 
				SharedPreferences.OnSharedPreferenceChangeListener, 
				DDConstants {
	
	public static final String MIME_TEXT_PLAIN = "text/plain";
	public static final String TAG = "DDTagReader";
	
	private NfcAdapter mNfcAdapter;
	private List<String> mMessageList;
	private ArrayAdapter<String> mListAdapter;
	private long mTagCheckInterval = 2000;
	private String mReaderId;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.view_main);
        
        // Set up the message list stuff
        mMessageList  = new ArrayList<String>();
        mListAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, mMessageList);
        
        setupMainViewFragment(savedInstanceState);
        setupNFC(savedInstanceState);
        loadPreferences(savedInstanceState);
        
        
        handleIntent(getIntent());
    }
    
    /**
     * Loads and sets saved preferences for this activity.
     * 
     * @param savedInstanceState
     */
    private void loadPreferences(Bundle savedInstanceState){
    	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String url = prefs.getString(PREF_KEY_URL, null);
        DDServiceClient.setBaseUrl(url);
        String val = prefs.getString(PREF_KEY_TAG_CHECK_INTERVAL, null);
		mTagCheckInterval = (val != null ? Long.valueOf(val): 2)*1000L;
		mReaderId = prefs.getString(PREF_KEY_READER_ID, "0");
    }

    /**
     * Sets up this activity to read NFC tags. No surprises here.
     * 
     * @param savedInstanceState
     */
    private void setupNFC(Bundle savedInstanceState){
    	mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (mNfcAdapter == null) {
            // Stop here, we definitely need NFC
            Toast.makeText(this, "This device doesn't support NFC.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (!mNfcAdapter.isEnabled()) {
        	addMessage("NFC is disabled");
        } else {
        	addMessage("Ready to read tags...");
        }
    }

    /**
     * Sets up the mainview fragment of this activity. The contentview is empty until
     * this has been called.
     * 
     * @param savedInstanceState
     */
    private void setupMainViewFragment(Bundle savedInstanceState){
    	
    	if (findViewById(R.id.fragment_container) != null) {

            // However, if we're being restored from a previous state,
            // then we don't need to do anything and should return or else
            // we could end up with overlapping fragments.
            if (savedInstanceState != null) {
                return;
            }

            // Create an instance of ExampleFragment
            MainViewFragment firstFragment = new MainViewFragment();

            // In case this activity was started with special instructions from an Intent,
            // pass the Intent's extras to the fragment as arguments
            firstFragment.setArguments(getIntent().getExtras());
            
            firstFragment.setListAdapter(mListAdapter);

            // Add the fragment to the 'fragment_container' FrameLayout
            getFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, firstFragment).commit();
        }
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }
    
    
    
    @Override
	public boolean onOptionsItemSelected(MenuItem item) {
    	switch( item.getItemId() ){
    	case R.id.action_settings:
    		Log.d(TAG, "Settings selected");
    		showSettings();
    		return true;
    	default: return super.onOptionsItemSelected(item);		
    	}
	}

    private void showSettings(){
    	FragmentManager fragMan = getFragmentManager();
    	FragmentTransaction fragTrans = fragMan.beginTransaction();
    	Fragment frag = new SettingsFragment();
    	fragTrans.replace(R.id.fragment_container, frag);
    	fragTrans.addToBackStack(null);
    	fragTrans.commit();
    }

	private void handleIntent(Intent intent) {
    	String action = intent.getAction();
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            String type = intent.getType();
            if (MIME_TEXT_PLAIN.equals(type)) {
                Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
                new NdefReaderTask().execute(tag);
            } else {
                Log.d("TAG", "Wrong mime type: " + type);
            }
        } else if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)) {
            // In case we would still use the Tech Discovered Intent
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            String[] techList = tag.getTechList();
            String searchedTech = Ndef.class.getName();
            for (String tech : techList) {
                if (searchedTech.equals(tech)) {
                    new NdefReaderTask().execute(tag);
                    break;
                }
            }
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        /**
         * It's important, that the activity is in the foreground (resumed). Otherwise
         * an IllegalStateException is thrown.
         */
        setupForegroundDispatch(this, mNfcAdapter);
        
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);
    }
    
    @Override
    protected void onPause() {
        /**
         * Call this before onPause, otherwise an IllegalArgumentException is thrown as well.
         */
        stopForegroundDispatch(this, mNfcAdapter);
        
        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);
        
        super.onPause();
    }
    
    @Override
    protected void onNewIntent(Intent intent) {
        /**
         * This method gets called, when a new Intent gets associated with the current activity instance.
         * Instead of creating a new activity, onNewIntent will be called. For more information have a look
         * at the documentation.
         *
         * In our case this method gets called, when the user attaches a Tag to the device.
         */
        handleIntent(intent);
    }
    
    /**
     * Checks whether <code>tag</code> is still connected.
     * 
     * @param tag the tag to check
     * @return true if <code>tag</> is still connected, false otherwise.
     */
    private boolean isConnected(Tag tag) {
    	boolean connected = false;
    	Ndef ndef = Ndef.get(tag);
    	if( ndef.isConnected() ) {
    		connected = true;
    	}else {
    		try {
    			ndef.connect();
    			connected = true;
    		}catch(IOException ioe){
    			Log.i(TAG, "Tag removed or not connected");
    			connected = false;
    		}finally{
    			try{
    				ndef.close();
    			}catch(Exception e){}
    		}
    	}
    	return connected;
    }
    
    /**
     * Notifies the client about the 'loss' of NFC tags, i.e. they are no
     * longer connected, and also updates the view.
     * 
     * @param id the id of the tag that was removed
     */
    private void tagRemoved(String id){
    	Log.d(TAG, "Remove: " + id);
    	addMessage("<< " + id);
    	DDServiceClient.tagRemoved(id, mReaderId);
    }
    
    /**
     * Notifies the webclient about tags that are read by the NFC reader.
     * Also updates the view correspondingly, and starts a task for detecting
     * when the tag is removed.
     * 
     * @param tag the NFC tag that was read
     * @param id the id, or NDEF content that was read from the tag
     */
    private void tagAdded(Tag tag, String id){
    	Log.d(TAG, "Add: " + id);
    	DDServiceClient.tagAdded(id, mReaderId);
    	new NdefDisconnectedTask().execute(tag,id, mTagCheckInterval);
    	addMessage(">> "+id);
    }
    
    /**
     * Adds a (String) message to the listview
     * 
     * @param msg a message
     */
    private void addMessage(String msg){
    	mMessageList.add(msg);
    	mListAdapter.notifyDataSetChanged();
    }
    
    /**
     * @param activity The corresponding {@link Activity} requesting the foreground dispatch.
     * @param adapter The {@link NfcAdapter} used for the foreground dispatch.
     */
    public static void setupForegroundDispatch(final Activity activity, NfcAdapter adapter) {
        final Intent intent = new Intent(activity.getApplicationContext(), activity.getClass());
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        final PendingIntent pendingIntent = PendingIntent.getActivity(activity.getApplicationContext(), 0, intent, 0);
        IntentFilter[] filters = new IntentFilter[1];
        String[][] techList = new String[][]{};
        // Notice that this is the same filter as in our manifest.
        filters[0] = new IntentFilter();
        filters[0].addAction(NfcAdapter.ACTION_NDEF_DISCOVERED);
        filters[0].addCategory(Intent.CATEGORY_DEFAULT);
        try {
            filters[0].addDataType(MIME_TEXT_PLAIN);
        } catch (MalformedMimeTypeException e) {
            throw new RuntimeException("Check your mime type.");
        }
        adapter.enableForegroundDispatch(activity, pendingIntent, filters, techList);
    }
    
    /**
     * @param activity The corresponding {@link BaseActivity} requesting to stop the foreground dispatch.
     * @param adapter The {@link NfcAdapter} used for the foreground dispatch.
     */
    public static void stopForegroundDispatch(final Activity activity, NfcAdapter adapter) {
        adapter.disableForegroundDispatch(activity);
    }
    
    /**
     * Background task for reading the data. Do not block the UI thread while reading.
     *
     * @author Ralf Wondratschek
     *
     */
    private class NdefReaderTask extends AsyncTask<Tag, Void, String> {
    	Tag mTag = null;
        @Override
        protected String doInBackground(Tag... params) {
            Tag tag = params[0];
            mTag = tag;
            Ndef ndef = Ndef.get(tag);
            if (ndef == null) {
                // NDEF is not supported by this Tag.
                return null;
            }
            NdefMessage ndefMessage = ndef.getCachedNdefMessage();
            NdefRecord[] records = ndefMessage.getRecords();
            for (NdefRecord ndefRecord : records) {
                if (ndefRecord.getTnf() == NdefRecord.TNF_WELL_KNOWN && Arrays.equals(ndefRecord.getType(), NdefRecord.RTD_TEXT)) {
                    try {
                        return readText(ndefRecord);
                    } catch (UnsupportedEncodingException e) {
                        Log.e("TAG", "Unsupported Encoding", e);
                    }
                }
            }
            return null;
        }
        private String readText(NdefRecord record) throws UnsupportedEncodingException {
            /*
             * See NFC forum specification for "Text Record Type Definition" at 3.2.1
             *
             * http://www.nfc-forum.org/specs/
             *
             * bit_7 defines encoding
             * bit_6 reserved for future use, must be 0
             * bit_5..0 length of IANA language code
             */
            byte[] payload = record.getPayload();
            // Get the Text Encoding
            String textEncoding = ((payload[0] & 128) == 0) ? "UTF-8" : "UTF-16";
            // Get the Language Code
            int languageCodeLength = payload[0] & 0063;
            // String languageCode = new String(payload, 1, languageCodeLength, "US-ASCII");
            // e.g. "en"
            // Get the Text
            return new String(payload, languageCodeLength + 1, payload.length - languageCodeLength - 1, textEncoding);
        }
        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                tagAdded(mTag, result);
            }
        }
    }
    
    /**
     * Background task for detecting when an NFC tag is removed via polling it
     * at intervals.
     * 
     * Maybe this is abusing AsyncTasks a bit but it works.
     * 
     * @author jarmo
     *
     */
    private class NdefDisconnectedTask extends AsyncTask<Object, String, Boolean>{
    	
    	String id;
    	Tag tag;
    	long startTime;

		@Override
		protected void onPreExecute() {
			startTime = System.currentTimeMillis();
		}

		@Override
		protected Boolean doInBackground(Object... params) {
			tag = (Tag) params[0];
			id = (String) params[1];
			final long sleeptime = (Long)params[2];
			long runTime = 0;
			
			do{
				
				try {
					Thread.sleep(sleeptime);
				} catch (InterruptedException e) {
				}
				runTime = (System.currentTimeMillis() - startTime)/1000;
				
				Log.d(TAG, String.format("Tag:'%s' connected for %d seconds", id, runTime));
			}while(isConnected(tag));
			
			return false;
		}

		@Override
		protected void onPostExecute(Boolean result) {
			if( !result ){
				tagRemoved(id);
			}
		}
    }

	@Override
	public void onMessageSelected(int position) {
		Log.d(TAG, String.format("List[%d] clicked", position));
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
		Log.d(TAG, String.format("Changing preference '%s'", key));
		if( key.equals(PREF_KEY_URL) ) {
			DDServiceClient.setBaseUrl( prefs.getString(key, null) );
		}
		
		if( key.equals(PREF_KEY_TAG_CHECK_INTERVAL) ){
			String val = prefs.getString(key, null);
			mTagCheckInterval = (val != null ? Long.valueOf(val): 2)*1000L;
		}
		
	}
}
