package com.derekjass.iabhelper;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class PurchaseActivity extends Activity {

	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getIntent().getExtras().getParcelable(BUY_INTENT);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {

	}
}
