package com.derekjass.iabhelper;

import java.util.List;

import android.app.Activity;
import android.content.Intent;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.AttributeSet;

import com.derekjass.iabhelper.BillingHelper.BillingError;
import com.derekjass.iabhelper.BillingHelper.OnProductPurchasedListener;
import com.derekjass.iabhelper.BillingHelper.OnPurchaseConsumedListener;
import com.derekjass.iabhelper.BillingHelper.OnPurchasesQueriedListener;

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
	public void onInflate(Activity activity, AttributeSet attrs,
			Bundle savedInstanceState) {
		super.onInflate(activity, attrs, savedInstanceState);

		TypedArray a = activity.obtainStyledAttributes(attrs,
				R.styleable.PurchaseStateFragment);

		String id = a.getString(R.styleable.PurchaseStateFragment_product_id);
		int val = a.getInt(R.styleable.PurchaseStateFragment_product_type, -1);

		if (id != null) {
			mProductId = id;
		}
		switch (val) {
		case 0:
			mProductType = ProductType.MANAGED_PRODUCT;
			break;
		case 1:
			mProductType = ProductType.SUBSCRIPTION;
			break;
		}

		a.recycle();
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Bundle args = getArguments();
		if (args != null) {
			mProductId = args.getString(EXTRA_PRODUCT_ID);
			mProductType = (ProductType) args
					.getSerializable(EXTRA_PRODUCT_TYPE);
		}

		if (mProductId == null || mProductType == null) {
			throw new IllegalStateException(
					"Subclasses of PurchaseStateFragment require arguments"
							+ " for product ID and product type");
		}

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
		mBillingHelper.connect();
	}

	@Override
	public void onResume() {
		super.onResume();
		refreshPurchaseState();
	}

	@Override
	public void onStop() {
		super.onStop();
		mBillingHelper.disconnect();
	}

	public Purchase getPurchase() {
		return mPurchase;
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

	protected abstract void onBillingError(BillingError error);

	protected abstract void onPurchaseStateChanged(PurchaseState purchaseState);

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

	public void purchaseProduct() {
		if (mPurchaseState != PurchaseState.NOT_PURCHASED) return;
		mBillingHelper.purchaseProduct(mProductId, null, getActivity(), 1,
				new OnProductPurchasedListener() {
					@Override
					public void onError(BillingError error) {
						onBillingError(error);
					}

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
		mBillingHelper.handleActivityResult(requestCode, resultCode, data);
		super.onActivityResult(requestCode, resultCode, data);
	}

	public void consumeProduct() {
		if (mPurchaseState != PurchaseState.PURCHASED) return;
		mBillingHelper.consumePurchase(mPurchase,
				new OnPurchaseConsumedListener() {
					@Override
					public void onError(BillingError error) {
						onBillingError(error);
					}

					@Override
					public void onPurchaseConsumed(Purchase purchase) {
						mPurchase = null;
						setPurchaseState(PurchaseState.NOT_PURCHASED);
					}
				});
	}

	protected static Bundle getArgsBundle(String productId, ProductType type) {
		Bundle args = new Bundle(2);
		args.putString(EXTRA_PRODUCT_ID, productId);
		args.putSerializable(EXTRA_PRODUCT_TYPE, type);
		return args;
	}
}
