package com.derekjass.iabhelper;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

import com.android.vending.billing.IInAppBillingService;

public class Billing {

	public static class BillingConnection implements IInAppBillingService {

		private Billing mBilling;
		private boolean mConnected = true;

		private BillingConnection(Billing billing) {
			mBilling = billing;
		}

		public void close() {
			mConnected = false;
			mBilling.closeConnection(this);
			mBilling = null;
		}

		private void checkConnected() {
			if (!mConnected) {
				throw new IllegalStateException("BillingConnection was closed");
			}
		}

		@Override
		public IBinder asBinder() {
			checkConnected();
			return mBilling.mService.asBinder();
		}

		@Override
		public int isBillingSupported(int apiVersion, String packageName,
				String type) throws RemoteException {
			checkConnected();
			return mBilling.mService.isBillingSupported(apiVersion,
					packageName, type);
		}

		@Override
		public Bundle getSkuDetails(int apiVersion, String packageName,
				String type, Bundle skusBundle) throws RemoteException {
			checkConnected();
			return mBilling.mService.getSkuDetails(apiVersion, packageName,
					type, skusBundle);
		}

		@Override
		public Bundle getBuyIntent(int apiVersion, String packageName,
				String sku, String type, String developerPayload)
				throws RemoteException {
			checkConnected();
			return mBilling.mService.getBuyIntent(apiVersion, packageName, sku,
					type, developerPayload);
		}

		@Override
		public Bundle getPurchases(int apiVersion, String packageName,
				String type, String continuationToken) throws RemoteException {
			checkConnected();
			return mBilling.mService.getPurchases(apiVersion, packageName,
					type, continuationToken);
		}

		@Override
		public int consumePurchase(int apiVersion, String packageName,
				String purchaseToken) throws RemoteException {
			checkConnected();
			return mBilling.mService.consumePurchase(apiVersion, packageName,
					purchaseToken);
		}
	}

	private static Billing sInstance;
	private static Lock sLock = new ReentrantLock();

	private Context mContext;
	private ServiceConnection mConnection;
	private boolean mBound;
	private IInAppBillingService mService;
	private int mConnections;

	private Billing(Context context) {
		mContext = context.getApplicationContext();
		mConnection = new ServiceConnection() {
			@Override
			public void onServiceDisconnected(ComponentName name) {}

			@Override
			public void onServiceConnected(ComponentName name, IBinder service) {
				mService = IInAppBillingService.Stub.asInterface(service);
			}
		};
		Intent i = new Intent(
				"com.android.vending.billing.InAppBillingService.BIND");
		mBound = mContext.bindService(i, mConnection, Context.BIND_AUTO_CREATE);
	}

	private void closeConnection(BillingConnection billingConnection) {
		sLock.lock();
		try {
			mConnections--;
			tryDisconnect();
		} finally {
			sLock.unlock();
		}
	}

	private void tryDisconnect() {
		if (mConnections == 0) {
			mContext.unbindService(mConnection);
			sInstance = null;
			mContext = null;
			mConnection = null;
			mService = null;
		}
	}

	public static BillingConnection getConnection(Context context) {
		BillingConnection connection = null;
		sLock.lock();
		try {
			if (sInstance == null) {
				sInstance = new Billing(context);
			}
			if (sInstance.mBound) {
				sInstance.mConnections++;
				connection = new BillingConnection(sInstance);
			}
		} finally {
			sLock.unlock();
		}
		return connection;
	}
}
