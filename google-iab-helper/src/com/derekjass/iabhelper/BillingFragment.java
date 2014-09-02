package com.derekjass.iabhelper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.os.Bundle;
import android.support.v4.app.Fragment;

import com.derekjass.iabhelper.Billing.BillingConnection;

public abstract class BillingFragment extends Fragment {

	/**
	 * Enumeration of all possible purchase states of an in-app product.
	 */
	public enum PurchaseState {
		/**
		 * Default purchase state. This is what the state is set to initially
		 * before any communication with the billing service.
		 */
		DEFAULT,
		/**
		 * Product is verified as purchased.
		 */
		PURCHASED,
		/**
		 * Product is verified as not purchased.
		 */
		NOT_PURCHASED,
		/**
		 * Purchase state is unknown. Usually indicates a problem contacting the
		 * billing service.
		 */
		UNKNOWN;
	}


	private PurchaseState mPurchaseState;
	private BillingConnection mBilling;
	private Purchase mPurchase;
	private String mProductId;
	private String mProductType;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mPurchaseState = PurchaseState.DEFAULT;

		if (!getClass().isAnnotationPresent(ProductId.class)) {
			// TODO
			throw new RuntimeException();
		}

		ProductId idAnnotation = getClass().getAnnotation(ProductId.class);
		mProductId = idAnnotation.value();

		if (mProductId == null) {
			// TODO
			throw new RuntimeException();
		}

		boolean subscription = getClass().isAnnotationPresent(
				Subscription.class);
		mProductType = subscription ? "subs" : "inapp";

		mBilling = Billing.getConnection(getActivity());
		if (mBilling == null) {
			onBillingError(BillingError.PLAY_SERVICES_UNAVAILABLE);
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (mBilling != null) {
			mBilling.close();
			mBilling = null;
		}
	}

	public void purchaseProduct(String payload) {
		if (mBilling == null) return;
	}

	public void consumePurchase() {
		if (mBilling == null) return;
	}

	public void onPurchaseStateChanged(PurchaseState state) {}

	public void onBillingError(BillingError error) {}

	public void onPurchaseConsumed() {}

	public boolean validateSignature(String data, String signature) {
		return true;
	}

	private void setPurchaseState(PurchaseState state) {
		if (mPurchaseState != state) {
			mPurchaseState = state;
			getActivity().runOnUiThread(new Runnable() {
				@Override
				public void run() {
					onPurchaseStateChanged(mPurchaseState);
				}
			});
		}
	}
}
