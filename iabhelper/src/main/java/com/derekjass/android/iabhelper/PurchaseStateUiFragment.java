package com.derekjass.android.iabhelper;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * A subclass of {@link PurchaseStateFragment} that chooses which fragment to
 * show, based on the current purchase state of the product. When purchase state
 * changes, the fragment automatically changes which fragment is shown.
 * <p/>
 * Subclasses must specify which fragment to show based on the purchase state by
 * overriding the {@link #getFragmentForState(PurchaseState)} method. Returning
 * null from this method is okay, and simply ignores the change in state.
 */
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
        return inflater.inflate(R.layout.fragment_container, container,
                false);
    }

    @Override
    protected void onPurchaseStateChanged(PurchaseState purchaseState) {
        if (mResumed) {
            Fragment fragment = getFragmentForState(purchaseState);
            showFragment(fragment);
        }
    }

    /**
     * Called when this fragment's state has changed to get a fragment to show
     * based on the purchase state of the fragment. This method may return null
     * to ignore the associated change in state.
     *
     * @param state purchase state to get a fragment for
     * @return the fragment that should be associated with the purchase state
     */
    protected abstract Fragment getFragmentForState(PurchaseState state);

    private void showFragment(Fragment fragment) {
        if (fragment == null) return;
        FragmentManager fm = getChildFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        ft.replace(R.id.container, fragment);
        ft.commit();
    }
}
