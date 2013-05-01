/*
 * Copyright 2011-2012 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package piuk.blockchain.android.ui;

import piuk.EventListeners;
import piuk.MyTransaction;
import piuk.blockchain.android.Constants;
import piuk.blockchain.android.ExchangeRatesProvider;
import piuk.blockchain.android.R;
import piuk.blockchain.android.WalletApplication;
import piuk.blockchain.android.util.QrDialog;
import piuk.blockchain.android.util.WalletUtils;
import android.app.Activity;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;

/**
 * @author Andreas Schildbach
 */
public final class WalletBalanceFragment extends Fragment implements
LoaderManager.LoaderCallbacks<Cursor> {
	private WalletApplication application;
	private SharedPreferences prefs;
	private final Handler handler = new Handler();
	private ImageView qrView;
	private Bitmap qrCodeBitmap;

	private CurrencyAmountView viewBalance;

	private EventListeners.EventListener eventListener = new EventListeners.EventListener() {
		@Override
		public String getDescription() {
			return "Wallet Balance Listener";
		}

		@Override
		public void onWalletDidChange() {
			try {
				updateView();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		@Override
		public void onCoinsSent(final MyTransaction tx, final long result) {
			try {
				updateView();
			} catch (Exception e) {
				e.printStackTrace();
			}
		};

		@Override
		public void onCoinsReceived(final MyTransaction tx, final long result) {
			try {
				updateView();
			} catch (Exception e) {
				e.printStackTrace();
			}
		};
		
		@Override
		public void onCurrencyChanged() {
			try {				
				updateView();
			} catch (Exception e) {
				e.printStackTrace();
			}
		};


		@Override
		public void onTransactionsChanged() {
			try {
				updateView();
			} catch (Exception e) {
				e.printStackTrace();
			}
		};


	};

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		final Activity activity = getActivity();
		application = (WalletApplication) activity.getApplication();
		prefs = PreferenceManager.getDefaultSharedPreferences(activity);

		EventListeners.addEventListener(eventListener);
	}

	@Override
	public void onActivityCreated(final Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		getLoaderManager().initLoader(0, null, this);
	}

	@Override
	public View onCreateView(final LayoutInflater inflater,
			final ViewGroup container, final Bundle savedInstanceState) {
		final View view = inflater.inflate(R.layout.wallet_balance_fragment,
				container, false);
		viewBalance = (CurrencyAmountView) view
				.findViewById(R.id.wallet_balance);

		viewBalance.setOnClickListener(new OnClickListener() {
			public void onClick(final View v) {
				application.setShouldDisplayLocalCurrency(!application.getShouldDisplayLocalCurrency());
				
				updateView();
			}
		});
		
		qrView = (ImageView) view.findViewById(R.id.request_coins_qr);
		qrView.setOnClickListener(new OnClickListener() {
			public void onClick(final View v) {
				new QrDialog(getActivity(), qrCodeBitmap).show();
			}
		});

		return view;
	}

	@Override
	public void onResume() {
		super.onResume();

		updateView();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		EventListeners.removeEventListener(eventListener);
	}

	public void updateView() {

		if (application.getRemoteWallet() == null)
			return;

		boolean displayLocal = application.getShouldDisplayLocalCurrency();

		if (displayLocal && application.getRemoteWallet().currencyConversion > 0 && application.getRemoteWallet().currencyCode != null) {
			viewBalance.setCurrencyCode(application.getRemoteWallet().currencyCode);

			viewBalance.setAmount(application.getRemoteWallet().getBalance().doubleValue() / application.getRemoteWallet().currencyConversion);
		} else {
			viewBalance.setCurrencyCode(Constants.CURRENCY_CODE_BITCOIN);

			viewBalance.setAmount(application.getRemoteWallet().getBalance());
		}
		String[] active = application.getRemoteWallet().getActiveAddresses();

		if (active.length > 0) {
			final int size = (int) (256 * getResources().getDisplayMetrics().density);
			qrCodeBitmap = WalletUtils.getQRCodeBitmap(active[0], size);
			qrView.setImageBitmap(qrCodeBitmap);
		} else {
			qrView.setVisibility(View.INVISIBLE);
		}

		getLoaderManager().restartLoader(0, null, this);
	}

	public Loader<Cursor> onCreateLoader(final int id, final Bundle args) {
		final String exchangeCurrency = prefs.getString(
				Constants.PREFS_KEY_EXCHANGE_CURRENCY,
				Constants.DEFAULT_EXCHANGE_CURRENCY);
		return new CursorLoader(getActivity(),
				ExchangeRatesProvider.CONTENT_URI, null,
				ExchangeRatesProvider.KEY_CURRENCY_CODE,
				new String[] { exchangeCurrency }, null);
	}

	public void onLoadFinished(final Loader<Cursor> loader, final Cursor data) {
		if (data != null) {
			data.moveToFirst();
		}
	}

	public void onLoaderReset(final Loader<Cursor> loader) {
	}
}
