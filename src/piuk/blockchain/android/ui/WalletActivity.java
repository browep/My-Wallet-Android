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

import com.google.android.gcm.GCMRegistrar;

import piuk.EventListeners;
import piuk.MyTransaction;
import piuk.blockchain.android.Constants;
import piuk.blockchain.android.R;
import piuk.blockchain.android.WalletApplication;
import piuk.blockchain.android.util.ActionBarFragment;
import piuk.blockchain.android.util.ErrorReporter;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Toast;

public final class WalletActivity extends AbstractWalletActivity {
	private static final int REQUEST_CODE_SCAN = 0;
	private static final int DIALOG_HELP = 0;
	public static WalletActivity instance = null;
			
	private ImageButton infoButton = null;
	AsyncTask<Void, Void, Void> mRegisterTask;
	WalletTransactionsFragment transactionsFragment = null;
	FrameLayout frameLayoutContainer = null;

	private final BroadcastReceiver mHandleMessageReceiver =
			new BroadcastReceiver() { 
		@Override
		public void onReceive(Context context, Intent intent) {
			String body = intent.getExtras().getString(Constants.BODY);
			String title = intent.getExtras().getString(Constants.TITLE);

			AlertDialog.Builder builder = new AlertDialog.Builder(context);
			builder.setTitle(title)
			.setMessage(body)
			.setCancelable(false)
			.setIcon(R.drawable.app_icon)
			.setNegativeButton(R.string.button_dismiss, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					dialog.cancel();
				}
			});

			builder.create().show();		// create and show the alert dialog

			if (application.getRemoteWallet() != null) {
				application.checkIfWalletHasUpdatedAndFetchTransactions(application.getRemoteWallet().getTemporyPassword());
			}
		}
	};

	public void showQRReader() {
		if (getPackageManager().resolveActivity(Constants.INTENT_QR_SCANNER, 0) != null) {
			startActivityForResult(Constants.INTENT_QR_SCANNER,
					REQUEST_CODE_SCAN);
		} else {
			showMarketPage(Constants.PACKAGE_NAME_ZXING);
			longToast(R.string.send_coins_install_qr_scanner_msg);
		}
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		instance = this;
		
		ErrorReporter.getInstance().check(this);

		setContentView(R.layout.wallet_content);

		final ActionBarFragment actionBar = getActionBarFragment();

		frameLayoutContainer = (FrameLayout)this.findViewById(R.id.frame_layout_container);

		transactionsFragment = (WalletTransactionsFragment) getSupportFragmentManager()
				.findFragmentById(R.id.wallet_transactions_fragment);


		actionBar.getPrimaryTitleView().setOnClickListener(
				new OnClickListener() {
					public void onClick(final View v) {
						if (application.getRemoteWallet() == null)
							return;

						WelcomeFragment.show(getSupportFragmentManager(), application);
					}
				});

		actionBar.getIconView().setOnClickListener(
				new OnClickListener() {
					public void onClick(final View v) {		
						if (application.getRemoteWallet() == null)
							return;

						WelcomeFragment.show(getSupportFragmentManager(), application);
					}
				});

		actionBar.addButton(R.drawable.ic_action_send).setOnClickListener(
				new OnClickListener() {
					public void onClick(final View v) {
						if (application.getRemoteWallet() == null)
							return;

						startActivity(new Intent(WalletActivity.this,
								SendCoinsActivity.class));
					}
				});

		actionBar.addButton(R.drawable.ic_action_receive).setOnClickListener(
				new OnClickListener() {
					public void onClick(final View v) {
						if (application.getRemoteWallet() == null)
							return;

						startActivity(new Intent(WalletActivity.this,
								RequestCoinsActivity.class));
					}
				});

		infoButton = actionBar.addButton(R.drawable.ic_action_info);

		infoButton.setOnClickListener(new OnClickListener() {
			public void onClick(final View v) {
				WalletApplication application = (WalletApplication) getApplication();

				if (application.getRemoteWallet() == null)
					return;

				Intent browserIntent = new Intent(
						Intent.ACTION_VIEW,
						Uri.parse("https://blockchain.info/wallet/iphone-view?guid="
								+ application.getRemoteWallet().getGUID()
								+ "&sharedKey="
								+ application.getRemoteWallet().getSharedKey()));

				startActivity(browserIntent);
			}
		});

		actionBar.addButton(R.drawable.ic_action_address_book)
		.setOnClickListener(new OnClickListener() {
			public void onClick(final View v) {
				if (application.getRemoteWallet() == null)
					return;

				WalletAddressesActivity
				.start(WalletActivity.this, true);
			}
		});

		actionBar.addButton(R.drawable.ic_action_exchange).setOnClickListener(
				new OnClickListener() {
					public void onClick(final View v) {
						if (application.getRemoteWallet() == null)
							return;

						startActivity(new Intent(WalletActivity.this,
								ExchangeRatesActivity.class));
					}
				});


		registerReceiver(mHandleMessageReceiver, new IntentFilter(Constants.DISPLAY_MESSAGE_ACTION));

		registerNotifications();
	}


	public void registerNotifications() {
		try {
			final String regId = GCMRegistrar.getRegistrationId(this);

			if (regId == null || regId.equals("")) {
				GCMRegistrar.register(this, Constants.SENDER_ID);
			} else {
				application.registerForNotificationsIfNeeded(regId);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	protected void onDestroy() {
		instance = null;

		if (mRegisterTask != null) {
			mRegisterTask.cancel(true);
		}
		unregisterReceiver(mHandleMessageReceiver);
		GCMRegistrar.onDestroy(this);
		super.onDestroy();
	}


	@Override
	protected void onResume() {
		super.onResume();

		registerNotifications();
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		super.onCreateOptionsMenu(menu);
		getMenuInflater().inflate(R.menu.wallet_options, menu);
		menu.findItem(R.id.wallet_options_donate).setVisible(!Constants.TEST);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
		case R.id.wallet_options_address_book:
			WalletAddressesActivity.start(WalletActivity.this, true);
			return true;

		case R.id.wallet_options_preferences:
			startActivity(new Intent(this, PreferencesActivity.class));
			return true;

		case R.id.wallet_options_donate:
			final Intent intent = new Intent(this, SendCoinsActivity.class);
			intent.putExtra(SendCoinsActivity.INTENT_EXTRA_ADDRESS,
					Constants.DONATION_ADDRESS);
			startActivity(intent);
			return true;

		case R.id.wallet_options_bug:
			Intent i = new Intent(Intent.ACTION_SEND);
			i.setType("text/plain");
			i.putExtra(Intent.EXTRA_EMAIL, new String[] { "support@pi.uk.com" });
			i.putExtra(Intent.EXTRA_SUBJECT, "Exception Report");

			String log = application.readExceptionLog();
			if (log != null)
				i.putExtra(Intent.EXTRA_TEXT, log);

			try {
				startActivity(Intent.createChooser(i, "Send mail..."));
			} catch (android.content.ActivityNotFoundException ex) {
				Toast.makeText(this, "There are no email clients installed.",
						Toast.LENGTH_SHORT).show();
			}

			return true;
		case R.id.wallet_options_help:
			showDialog(DIALOG_HELP);
			return true;
		}

		return false;
	}

	@Override
	protected Dialog onCreateDialog(final int id) {
		final WebView webView = new WebView(this);
		if (id == DIALOG_HELP)
			webView.loadUrl("file:///android_asset/help" + languagePrefix()
					+ ".html");

		final Dialog dialog = new Dialog(WalletActivity.this);
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		dialog.setContentView(webView);
		dialog.setCanceledOnTouchOutside(true);

		return dialog;
	}
}