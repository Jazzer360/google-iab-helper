package com.derekjass.iabhelper;

import android.app.Activity;

import com.derekjass.iabhelper.BillingHelper.BillingError;

/**
 * A simple fragment with no UI that tracks the purchase state of a product. Any
 * changes to the purchase state of a product will be reported back to the
 * activity that this fragment is attached to.
 * <p>
 * To use this class, you must instantiate the fragment with the
 * {@link #newInstance(String, ProductType)} method, and your activity must
 * implement {@link PurchaseStateListener} to handle any change in purchase
 * state.
 */
public class SimplePurchaseStateFragment extends PurchaseStateFragment {

	/**
	 * Callback to report any change in purchase state to the activity.
	 */
	public interface PurchaseStateListener {
		/**
		 * Called when the purchase state of a product has changed.
		 * 
		 * @param productId
		 *            the product ID of the product whose purchase state changed
		 * @param state
		 *            the new purchase state of the product
		 */
		public void onPurchaseStateChanged(String productId, PurchaseState state);
	}

	private PurchaseStateListener mListener;

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		try {
			mListener = (PurchaseStateListener) activity;
		} catch (ClassCastException e) {
			throw new ClassCastException(activity.toString()
					+ " must implement PurchaseStateListener");
		}
	}

	@Override
	protected void onBillingError(BillingError error) {}

	@Override
	protected void onPurchaseStateChanged(PurchaseState purchaseState) {
		mListener.onPurchaseStateChanged(getProductId(), purchaseState);
	}

	/**
	 * Returns an instance of a {@link SimplePurchaseStateFragment} set up for
	 * the specified product ID and product type.
	 * 
	 * @param productId
	 *            product ID of the product
	 * @param type
	 *            type of product (managed or subscription)
	 * @return PurchaseStateFragment for the specified product ID and type
	 */
	public static PurchaseStateFragment newInstance(String productId,
			ProductType type) {
		PurchaseStateFragment f = new SimplePurchaseStateFragment();
		f.setArguments(getArgsBundle(productId, type));
		return f;
	}
}
