package com.derekjass.iabhelper;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public abstract class PurchaseStateUiFragment extends PurchaseStateFragment {

	@Override
	public final View onCreateView(LayoutInflater inflater,
			ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_container, container,
				false);
		return view;
	}

	@Override
	protected void onPurchaseStateChanged(PurchaseState purchaseState) {
		Fragment fragment = getFragmentForState(purchaseState);
		if (fragment != null) {
			FragmentManager fm = getChildFragmentManager();
			FragmentTransaction ft = fm.beginTransaction();
			ft.replace(R.layout.fragment_container, fragment);
			ft.commit();
		}
	}

	protected abstract Fragment getFragmentForState(PurchaseState state);
}
