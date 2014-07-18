package com.derekjass.iabhelper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;

import com.android.vending.billing.IInAppBillingService;

public class BillingHelper {

	/**
	 * Callback to deliver results of the calls made to the billing service.
	 * <p>
	 * These calls are run on the main UI thread.
	 */
	public interface Callbacks {
		public void onProductsQueried(List<Product> products);

		public void onPurchasesQueried(List<Purchase> purchases);

		public void onProductPurchased(Purchase purchase);

		public void onPurchaseConsumed(Purchase purchase);
	}

	public interface ErrorHandler {
		public void onError(Error error);
	}

	private enum ProductType {
		MANAGED_PRODUCT("inapp"), SUBSCRIPTION("subs");

		private String mToken;

		private ProductType(String token) {
			mToken = token;
		}

		private String token() {
			return mToken;
		}
	}

	public enum StaticResponse {
		PURCHASED("android.test.purchased"),
		CANCELED("android.test.canceled"),
		REFUNDED("android.test.refunded"),
		UNAVAILABLE("android.test.unavailable");

		private String mId;

		private StaticResponse(String id) {
			mId = id;
		}

		private String id() {
			return mId;
		}
	}

	public enum Error {
		USER_CANCELLED,
		BILLING_UNAVAILABLE,
		ITEM_UNAVAILABLE,
		DEVELOPER_ERROR,
		ERROR,
		ITEM_ALREADY_OWNED,
		ITEM_NOT_OWNED,
		REMOTE_EXCEPTION,
		PENDING_INTENT;

		private static Error fromResponseCode(int code) {
			switch (code) {
			case 1:
				return USER_CANCELLED;
			case 3:
				return BILLING_UNAVAILABLE;
			case 4:
				return ITEM_UNAVAILABLE;
			case 5:
				return DEVELOPER_ERROR;
			case 6:
				return ERROR;
			case 7:
				return ITEM_ALREADY_OWNED;
			case 8:
				return ITEM_NOT_OWNED;
			default:
				throw new IllegalArgumentException("Undefined error code: "
						+ code);
			}
		}
	}

	private static final String RESPONSE_CODE = "RESPONSE_CODE";

	private Context mContext;
	private Callbacks mCallbacks;
	private ErrorHandler mErrorHandler;
	private ProductType mProductType;
	private Handler mHandler;
	private ServiceConnection mConnection;
	private IInAppBillingService mService;
	private CountDownLatch mBindLatch;
	private ExecutorService mExecutor;
	private StaticResponse mStaticResponse;

	private BillingHelper(Context context, Callbacks callbacks,
			ErrorHandler errorHandler, ProductType productType) {
		mContext = context;
		mCallbacks = callbacks;
		mErrorHandler = errorHandler;
		mProductType = productType;
		mHandler = new Handler(Looper.getMainLooper());
		mConnection = new ServiceConnection() {
			@Override
			public void onServiceDisconnected(ComponentName name) {}

			@Override
			public void onServiceConnected(ComponentName name, IBinder service) {
				mService = IInAppBillingService.Stub.asInterface(service);
				mBindLatch.countDown();
			}
		};
	}

	public static BillingHelper newManagedProductHelper(Context context,
			Callbacks callbacks, ErrorHandler errorHandler) {
		return new BillingHelper(context, callbacks, errorHandler,
				ProductType.MANAGED_PRODUCT);
	}

	public static BillingHelper newSubscriptionHelper(Context context,
			Callbacks callbacks, ErrorHandler errorHandler) {
		return new BillingHelper(context, callbacks, errorHandler,
				ProductType.SUBSCRIPTION);
	}

	public void connect() {
		mExecutor = Executors.newSingleThreadExecutor();
		mBindLatch = new CountDownLatch(1);
		mContext.bindService(new Intent(
				"com.android.vending.billing.InAppBillingService.BIND"),
				mConnection, Context.BIND_AUTO_CREATE);
	}

	public void disconnect() {
		mExecutor.shutdownNow();
		mContext.unbindService(mConnection);
		mService = null;
	}

	public void queryProducts(final List<String> ids) {
		if (ids == null) {
			throw new IllegalArgumentException("ids may not be null");
		}
		if (ids.size() == 0) {
			throw new IllegalArgumentException("ids must not be empty");
		}
		if (ids.size() > 20) {
			throw new IllegalArgumentException(
					"ids must contain at most 20 ids");
		}
		mExecutor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					ArrayList<String> skus = new ArrayList<String>();
					for (String id : ids) {
						if (!skus.contains(id)) {
							skus.add(id);
						}
					}

					Bundle skuBundle = new Bundle();
					skuBundle.putStringArrayList("ITEM_ID_LIST", skus);

					mBindLatch.await();
					Bundle result = mService.getSkuDetails(3,
							mContext.getPackageName(), mProductType.token(),
							skuBundle);

					int resultCode = result.getInt(RESPONSE_CODE);
					if (resultCode != 0) {
						deliverError(Error.fromResponseCode(resultCode));
						return;
					}

					ArrayList<String> jsonArray = result
							.getStringArrayList("DETAILS_LIST");
					List<Product> products = new ArrayList<Product>();

					for (String json : jsonArray) {
						products.add(new Product(json));
					}

					deliverProductsQueried(products);
				} catch (RemoteException e) {
					deliverError(Error.REMOTE_EXCEPTION);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
		});
	}

	public void queryPurchases() {
		mExecutor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					String continuationToken = null;
					List<Purchase> purchases = new ArrayList<Purchase>();

					mBindLatch.await();
					do {
						Bundle result = mService.getPurchases(3,
								mContext.getPackageName(),
								mProductType.token(), continuationToken);

						int resultCode = result.getInt(RESPONSE_CODE);
						if (resultCode != 0) {
							deliverError(Error.fromResponseCode(resultCode));
							return;
						}

						ArrayList<String> jsonArray = result
								.getStringArrayList("INAPP_PURCHASE_DATA_LIST");
						ArrayList<String> signatures = result
								.getStringArrayList("INAPP_DATA_SIGNATURE_LIST");
						continuationToken = result
								.getString("INAPP_CONTINUATION_TOKEN");

						for (int i = 0; i < jsonArray.size(); i++) {
							String json = jsonArray.get(i);
							String signature = signatures.get(i);
							purchases.add(new Purchase(json, signature));
						}
					} while (continuationToken != null);

					deliverPurchasesQueried(purchases);
				} catch (RemoteException e) {
					deliverError(Error.REMOTE_EXCEPTION);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}
			}
		});
	}

	public void purchaseProduct(String productId, Activity activity,
			int requestCode) {
		purchaseProduct(productId, null, activity, requestCode);
	}

	public void purchaseProduct(final String productId, final String payload,
			final Activity activity, final int requestCode) {
		if (productId == null || activity == null) {
			throw new IllegalArgumentException(
					"productId and activity may not be null");
		}
		mExecutor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					String sku = getProductId(productId);

					mBindLatch.await();
					Bundle result = mService.getBuyIntent(3,
							mContext.getPackageName(), sku,
							mProductType.token(), payload);

					PendingIntent intent = result.getParcelable("BUY_INTENT");

					activity.startIntentSenderForResult(
							intent.getIntentSender(), requestCode,
							new Intent(), 0, 0, 0);

				} catch (RemoteException e) {
					deliverError(Error.REMOTE_EXCEPTION);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} catch (SendIntentException e) {
					deliverError(Error.PENDING_INTENT);
				}
			}
		});

	}

	public void handleActivityResult(int requestCode, int resultCode,
			Intent data) {
		if (resultCode != Activity.RESULT_OK) return;

	}

	public void consumePurchase(Purchase purchase) {
		if (mProductType == ProductType.SUBSCRIPTION) {
			throw new UnsupportedOperationException(
					"Cannot consume a subscription");
		}
	}

	public void setStaticResponse(StaticResponse response) {
		mStaticResponse = response;
	}

	private String getProductId(String id) {
		return mStaticResponse != null ? mStaticResponse.id() : id;
	}

	private void deliverError(final Error error) {
		if (mErrorHandler == null) return;
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				mErrorHandler.onError(error);
			}
		});
	}

	private void deliverProductsQueried(final List<Product> products) {
		if (mCallbacks == null) return;
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				mCallbacks.onProductsQueried(products);
			}
		});
	}

	private void deliverPurchasesQueried(final List<Purchase> purchases) {
		if (mCallbacks == null) return;
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				mCallbacks.onPurchasesQueried(purchases);
			}
		});
	}

	private void deliverProductPurchased(final Purchase purchase) {
		if (mCallbacks == null) return;
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				mCallbacks.onProductPurchased(purchase);
			}
		});
	}

	private void deliverPurchaseConsumed(final Purchase purchase) {
		if (mCallbacks == null) return;
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				mCallbacks.onPurchaseConsumed(purchase);
			}
		});
	}
}
