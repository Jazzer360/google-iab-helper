package com.derekjass.iabhelper;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public abstract class PurchaseStateUiFragment extends PurchaseStateFragment {

	private boolean mResumed = false;

	@Override
	public void onResume() {
		super.onResume();
		mResumed = true;
		PurchaseState state = getPurchaseState();
		Fragment f = getFragmentForState(state);
		showFragment(f);
	}

	@Override
	public void onPause() {
		super.onPause();
		mResumed = false;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_container, container,
				false);
		return view;
	}

	@Override
	protected void onPurchaseStateChanged(PurchaseState purchaseState) {
		if (mResumed) {
			Fragment fragment = getFragmentForState(purchaseState);
			showFragment(fragment);
		}
	}

	protected abstract Fragment getFragmentForState(PurchaseState state);

	protected void showFragment(Fragment fragment) {
		if (fragment == null) return;
		FragmentManager fm = getChildFragmentManager();
		FragmentTransaction ft = fm.beginTransaction();
		ft.replace(R.id.container, fragment);
		ft.commit();
	}
}
