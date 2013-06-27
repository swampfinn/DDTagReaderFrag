package ixd.cbi.ddtagreaderfrag;

import ixd.cbi.ddtagreader.R;
import android.os.Bundle;
import android.preference.PreferenceFragment;

public class SettingsFragment extends PreferenceFragment {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		
		super.onCreate(savedInstanceState);
		
		addPreferencesFromResource(R.xml.dd_clienturl);
	}
	

}
