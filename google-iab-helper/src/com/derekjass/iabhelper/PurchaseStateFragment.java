package com.derekjass.iabhelper;

import java.util.List;

import android.app.Activity;
import android.content.Intent;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.AttributeSet;

import com.derekjass.iabhelper.BillingHelper.BillingError;
import com.derekjass.iabhelper.BillingHelper.OnErrorListener;
import com.derekjass.iabhelper.BillingHelper.OnProductPurchasedListener;
import com.derekjass.iabhelper.BillingHelper.OnPurchaseConsumedListener;
import com.derekjass.iabhelper.BillingHelper.OnPurchasesQueriedListener;

/**
 * This is a base class for any fragments that monitor purchase state for
 * Google's in-app billing. This class handles all of the IPC with Google's
 * in-app billing service.
 * <p>
 * Purchase state is managed automatically, and changes to it are passed to the
 * {@link #onPurchaseStateChanged(PurchaseState)} abstract method.
 * <p>
 * Subclasses of this class must set this fragment's arguments to a bundle that
 * may be created with {@link #getArgsBundle(String, String)}. Alternatively,
 * you may specify these arguments via xml when using an xml layout to place the
 * fragment.
 */
public abstract class PurchaseStateFragment extends Fragment {

	/**
	 * Callback to notify that a purchase has successfully been consumed.
	 */
	public interface PurchaseConsumedListener {
		/**
		 * Method called when purchase was successfully consumed.
		 * 
		 * @param purchase
		 *            the purchase that was consumed
		 */
		public void onPurchaseConsumed(Purchase purchase);
	}

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

	/**
	 * Product type for managed products.
	 */
	public static final String MANAGED_PRODUCT = "inapp";
	/**
	 * Product type for subscriptions.
	 */
	public static final String SUBSCRIPTION = "subs";
	/**
	 * The key to access the product ID string included in the arguments bundle.
	 */
	protected static final String EXTRA_PRODUCT_ID = "PRODUCT_ID";
	/**
	 * The key to access the product type String included in the arguments
	 * bundle.
	 */
	protected static final String EXTRA_PRODUCT_TYPE = "PRODUCT_TYPE";

	private BillingHelper mBillingHelper;
	private String mProductType;
	private String mProductId;
	private PurchaseState mPurchaseState;
	private Purchase mPurchase;

	@Override
	public void onInflate(Activity activity, AttributeSet attrs,
			Bundle savedInstanceState) {
		super.onInflate(activity, attrs, savedInstanceState);

		TypedArray a = activity.obtainStyledAttributes(attrs,
				R.styleable.PurchaseStateFragment);
		try {
			String id = a
					.getString(R.styleable.PurchaseStateFragment_product_id);
			int val = a.getInt(R.styleable.PurchaseStateFragment_product_type,
					-1);

			if (id != null) mProductId = id;

			switch (val) {
			case 0:
				mProductType = MANAGED_PRODUCT;
				break;
			case 1:
				mProductType = SUBSCRIPTION;
				break;
			}
		} finally {
			a.recycle();
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Bundle args = getArguments();
		if (args != null) {
			mProductId = args.getString(EXTRA_PRODUCT_ID);
			mProductType = args.getString(EXTRA_PRODUCT_TYPE);
		}

		if (mProductId == null || mProductType == null) {
			throw new IllegalStateException(
					"Subclasses of PurchaseStateFragment require arguments"
							+ " for product ID and product type");
		}

		if (mProductType.equals(MANAGED_PRODUCT)) {
			mBillingHelper = BillingHelper
					.newManagedProductHelper(getActivity());
		} else if (mProductType.equals(SUBSCRIPTION)) {
			mBillingHelper = BillingHelper.newSubscriptionHelper(getActivity());
		} else {
			throw new IllegalStateException(
					"Invalid product type argument for PurchaseStateFragment");
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		mBillingHelper.connect();
	}

	@Override
	public void onResume() {
		super.onResume();
		if (mPurchaseState == null) {
			setPurchaseState(PurchaseState.DEFAULT);
		}
		refreshPurchaseState();
	}

	@Override
	public void onStop() {
		super.onStop();
		mBillingHelper.disconnect();
	}

	/**
	 * Returns the product ID associated with this fragment.
	 * 
	 * @return product ID of the in-app product
	 */
	public String getProductId() {
		return mProductId;
	}

	/**
	 * Returns the purchase information associated with the product ID this
	 * fragment is handling. Returns null if the product is not purchased.
	 * 
	 * @return Purchase object that contains all relevant purchase info for the
	 *         product, or null if the product is not purchased
	 */
	public Purchase getPurchase() {
		return mPurchase;
	}

	/**
	 * Returns the known state of the product associated with this fragment.
	 * 
	 * @return purchase state of the product
	 */
	public PurchaseState getPurchaseState() {
		return mPurchaseState;
	}

	private void setPurchaseState(PurchaseState state) {
		if (mPurchaseState != state) {
			mPurchaseState = state;
			onPurchaseStateChanged(mPurchaseState);
		}
	}

	/**
	 * Called when there was an error while interacting with the in-app billing
	 * service.
	 * 
	 * @param error
	 *            the error that occurred
	 */
	protected abstract void onBillingError(BillingError error);

	/**
	 * Called when the purchase state of the product associated with this
	 * fragment has changed.
	 * 
	 * @param purchaseState
	 *            the new purchase state of the product
	 */
	protected abstract void onPurchaseStateChanged(PurchaseState purchaseState);

	/**
	 * Requests a refresh of the purchase state. If the state has changed from
	 * it's last known state, a call to the
	 * {@link #onPurchaseStateChanged(PurchaseState)} will occur with the new
	 * state passed as it's parameter.
	 */
	public void refreshPurchaseState() {
		mBillingHelper.queryPurchases(new OnPurchasesQueriedListener() {
			@Override
			public void onError(BillingError error) {
				onBillingError(error);
				setPurchaseState(PurchaseState.UNKNOWN);
			}

			@Override
			public void onPurchasesQueried(List<Purchase> purchases) {
				boolean purchased = false;
				for (Purchase purchase : purchases) {
					if (purchase.getProductId().equals(mProductId)) {
						purchased = purchase.isPurchased();
						mPurchase = purchased ? purchase : null;
						break;
					}
				}
				setPurchaseState(purchased ? PurchaseState.PURCHASED
						: PurchaseState.NOT_PURCHASED);
			}
		});
	}

	/**
	 * Triggers the purchasing process for the associated product. The
	 * requestCode parameter is used to identify this request's matching result
	 * that is handed back to this fragment's parent activity's
	 * onActivityResult(...) method.
	 * 
	 * @param requestCode
	 *            the request code used to launch the purchase
	 */
	public void purchaseProduct(int requestCode) {
		if (mPurchaseState != PurchaseState.NOT_PURCHASED) return;
		mBillingHelper.purchaseProduct(mProductId, null, getActivity(),
				requestCode, new OnErrorListener() {
					@Override
					public void onError(BillingError error) {
						onBillingError(error);
					}
				});
	}

	/**
	 * Should be called by the activity when it receives the result from the
	 * onActivityResult(...) method. Users should verify that the requestCode
	 * for the result matches the requestCode used when calling the
	 * {@link #purchaseProduct(int)} method.
	 * 
	 * @param data
	 *            the intent delivered to the activity with the result data
	 */
	public void handleActivityResult(Intent data) {
		mBillingHelper.handleActivityResult(data,
				new OnProductPurchasedListener() {
					@Override
					public void onError(BillingError error) {
						onBillingError(error);
					}

					@Override
					public void onProductPurchased(Purchase purchase) {
						if (purchase.getProductId().equals(mProductId)
								&& purchase.isPurchased()) {
							mPurchase = purchase;
							setPurchaseState(PurchaseState.PURCHASED);
						}
					}
				});
	}

	/**
	 * Consumes the in-app product, and updates the purchase state.
	 * 
	 * @param listener
	 *            the callback to notify when consumption was successful
	 */
	public void consumePurchase(final PurchaseConsumedListener listener) {
		if (mPurchaseState != PurchaseState.PURCHASED) return;
		mBillingHelper.consumePurchase(mPurchase,
				new OnPurchaseConsumedListener() {
					@Override
					public void onError(BillingError error) {
						onBillingError(error);
					}

					@Override
					public void onPurchaseConsumed(Purchase purchase) {
						if (listener != null) {
							listener.onPurchaseConsumed(purchase);
						}
						mPurchase = null;
						setPurchaseState(PurchaseState.NOT_PURCHASED);
					}
				});
	}

	/**
	 * Sets a PurchaseValidator to be used to validate any signatures that the
	 * billing service gives back. The use of a validator is optional, and may
	 * be set to null.
	 * 
	 * @param validator
	 *            signature validator used to validate signatures
	 */
	public void setSignatureValidator(SignatureValidator validator) {
		mBillingHelper.setSignatureValidator(validator);
	}

	/**
	 * Creates a new Bundle to hold the required arguments for the fragment.
	 * 
	 * @param productId
	 *            product ID of the in-app product to monitor
	 * @param type
	 *            the product type (either {@link #MANAGED_PRODUCT} or
	 *            {@link #SUBSCRIPTION})
	 * @return bundle containing the parameters mapped to the proper keys
	 */
	protected static Bundle getArgsBundle(String productId, String type) {
		Bundle args = new Bundle(2);
		args.putString(EXTRA_PRODUCT_ID, productId);
		args.putString(EXTRA_PRODUCT_TYPE, type);
		return args;
	}
}
