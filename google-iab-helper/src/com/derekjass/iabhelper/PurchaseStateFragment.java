package com.derekjass.iabhelper;

import java.util.List;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;

import com.derekjass.iabhelper.BillingHelper.BillingError;
import com.derekjass.iabhelper.BillingHelper.OnProductPurchasedListener;
import com.derekjass.iabhelper.BillingHelper.OnPurchaseConsumedListener;
import com.derekjass.iabhelper.BillingHelper.OnPurchasesQueriedListener;

/**
 * TODO
 * 
 * - Allow subclass to choose what fragment classes get swapped at each state
 * change.
 * 
 * - onPurchase
 * 
 * Users may attach subclasses of this fragment to an activity, and upon
 * attaching, the fragment connects to the play billing service to check the
 * purchase status of the product ID associated with the fragment.
 * 
 * Needs to be fragments associated with views, and fragments not associated
 * with views.
 * 
 * After the fragment is connected, it should check the purchase status of the
 * product, and allow subclasses a chance to specify the behavior
 * 
 * @author Derek
 *
 */
public abstract class PurchaseStateFragment extends Fragment {

	public enum ProductType {
		MANAGED_PRODUCT, SUBSCRIPTION;
	}

	public enum PurchaseState {
		PURCHASED, NOT_PURCHASED, UNKNOWN;
	}

	protected static final String EXTRA_PRODUCT_ID = "PRODUCT_ID";
	protected static final String EXTRA_PRODUCT_TYPE = "PRODUCT_TYPE";

	private BillingHelper mBillingHelper;
	private ProductType mProductType;
	private String mProductId;
	private PurchaseState mPurchaseState;
	private Purchase mPurchase;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Bundle args = getArguments();
		if (args == null) {
			throw new IllegalArgumentException();// TODO
		}
		mProductId = args.getString(EXTRA_PRODUCT_ID);
		mProductType = (ProductType) args.getSerializable(EXTRA_PRODUCT_TYPE);

		if (mProductId == null || mProductType == null) {
			throw new IllegalArgumentException();// TODO
		}
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		switch (mProductType) {
		case MANAGED_PRODUCT:
			mBillingHelper = BillingHelper
					.newManagedProductHelper(getActivity());
			break;
		case SUBSCRIPTION:
			mBillingHelper = BillingHelper.newSubscriptionHelper(getActivity());
			break;
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		setPurchaseState(PurchaseState.UNKNOWN);
		mBillingHelper.connect();
		refreshPurchaseState();
	}

	@Override
	public void onStop() {
		super.onStop();
		mBillingHelper.disconnect();
	}

	public PurchaseState getPurchaseState() {
		return mPurchaseState;
	}

	private void setPurchaseState(PurchaseState state) {
		if (mPurchaseState != state) {
			mPurchaseState = state;
			onPurchaseStateChanged(mPurchaseState);
		}
	}

	protected abstract void onPurchaseStateChanged(PurchaseState purchaseState);

	public void refreshPurchaseState() {
		mBillingHelper.queryPurchases(new OnPurchasesQueriedListener() {
			@Override
			public void onError(BillingError error) {
				setPurchaseState(PurchaseState.UNKNOWN);
			}

			@Override
			public void onPurchasesQueried(List<Purchase> purchases) {
				boolean purchased = false;
				for (Purchase purchase : purchases) {
					if (purchase.getProductId().equals(mProductId)) {
						purchased = purchase.isPurchased();
						mPurchase = purchased ? purchase : null;
						setPurchaseState(purchased ? PurchaseState.PURCHASED
								: PurchaseState.NOT_PURCHASED);
						break;
					}
				}
			}
		});
	}

	public void purchaseProduct() {
		if (mPurchaseState != PurchaseState.NOT_PURCHASED) return;
		mBillingHelper.purchaseProduct(mProductId, null, getActivity(), 0,
				new OnProductPurchasedListener() {
					@Override
					public void onError(BillingError error) {}

					@Override
					public void onProductPurchased(Purchase purchase) {
						if (purchase.isPurchased()) {
							mPurchase = purchase;
							setPurchaseState(PurchaseState.PURCHASED);
						}
					}
				});
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		mBillingHelper.handleActivityResult(requestCode, resultCode, data);
	}

	public void consumeProduct() {
		if (mPurchaseState != PurchaseState.PURCHASED) return;
		mBillingHelper.consumePurchase(mPurchase,
				new OnPurchaseConsumedListener() {
					@Override
					public void onError(BillingError error) {}

					@Override
					public void onPurchaseConsumed(Purchase purchase) {
						mPurchase = null;
						setPurchaseState(PurchaseState.NOT_PURCHASED);
					}
				});
	}
}
