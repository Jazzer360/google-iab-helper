package com.derekjass.iabhelper;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.os.Bundle;
import android.os.RemoteException;
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

	private static final String RESPONSE_CODE = "RESPONSE_CODE";
	private static final String ITEM_ID_LIST = "ITEM_ID_LIST";
	private static final String DETAILS_LIST = "DETAILS_LIST";
	private static final String INAPP_CONTINUATION_TOKEN = "INAPP_CONTINUATION_TOKEN";
	private static final String INAPP_DATA_SIGNATURE_LIST = "INAPP_DATA_SIGNATURE_LIST";
	private static final String INAPP_PURCHASE_DATA_LIST = "INAPP_PURCHASE_DATA_LIST";
	private static final String BUY_INTENT = "BUY_INTENT";
	private static final String INAPP_DATA_SIGNATURE = "INAPP_DATA_SIGNATURE";
	private static final String INAPP_PURCHASE_DATA = "INAPP_PURCHASE_DATA";

	private PurchaseState mPurchaseState;
	private BillingConnection mBilling;
	private ExecutorService mExecutor;
	private Product mProduct;
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

		mExecutor = Executors.newCachedThreadPool();

		refreshPurchaseState();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		mExecutor.shutdownNow();
		mExecutor = null;
		if (mBilling != null) {
			mBilling.close();
			mBilling = null;
		}
	}

	public void refreshPurchaseState() {
		if (mBilling == null) return;
		mExecutor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					String continuationToken = null;

					do {
						Bundle result = mBilling.getPurchases(3, getActivity()
								.getPackageName(), mProductType,
								continuationToken);

						int resultCode = result.getInt(RESPONSE_CODE);
						if (resultCode != 0) {
							onBillingError(BillingError
									.fromResponseCode(resultCode));
							return;
						}

						ArrayList<String> jsonArray = result
								.getStringArrayList(INAPP_PURCHASE_DATA_LIST);
						ArrayList<String> signatures = result
								.getStringArrayList(INAPP_DATA_SIGNATURE_LIST);
						continuationToken = result
								.getString(INAPP_CONTINUATION_TOKEN);

						for (int i = 0; i < jsonArray.size(); i++) {
							String json = jsonArray.get(i);
							String signature = signatures.get(i);
							if (validateSignature(json, signature)) {
								Purchase purchase = new Purchase(json,
										signature);
							} else {
								onBillingError(BillingError.INVALID_SIGNATURE);
								return;
							}
						}
					} while (continuationToken != null);

				} catch (RemoteException e) {
					onBillingError(BillingError.REMOTE_EXCEPTION);
				}
			}
		});
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
