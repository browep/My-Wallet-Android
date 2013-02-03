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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.webkit.WebView;
import android.widget.ImageButton;
import android.widget.Toast;
import com.google.bitcoin.core.AbstractWalletEventListener;
import com.google.bitcoin.core.Wallet;

import piuk.EventListeners;
import piuk.blockchain.android.R;
import piuk.blockchain.android.Constants;
import piuk.blockchain.android.WalletApplication;
import piuk.blockchain.android.util.ActionBarFragment;
import piuk.blockchain.android.util.ErrorReporter;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

/**
 * @author Andreas Schildbach
 */
public final class WalletActivity extends AbstractWalletActivity {
	private static final int REQUEST_CODE_SCAN = 0;
	private static final int DIALOG_HELP = 0;
	private ImageButton infoButton = null;
	private Handler handler = new Handler();

	private EventListeners.EventListener eventListener = new EventListeners.EventListener() {
		@Override
		public void onWalletDidChange() {
			handler.post(new Runnable() {
				public void run() {
					checkDialogs();
				}
			});
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

	public void checkDialogs() {
		WalletApplication application = (WalletApplication) getApplication();

		if (application.hasDecryptionError || application.isNewWallet()) {
			infoButton.setImageResource(R.drawable.ic_action_info_red);

			WelcomeFragment.show(getSupportFragmentManager(), application);
		} else {
			WelcomeFragment.hide();
			
			infoButton.setImageResource(R.drawable.ic_action_info);
		}
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);

		ErrorReporter.getInstance().check(this);

		setContentView(R.layout.wallet_content);

		final ActionBarFragment actionBar = getActionBarFragment();

		actionBar.getPrimaryTitleView().setOnClickListener(
				new OnClickListener() {
					public void onClick(final View v) {
						WelcomeFragment.show(getSupportFragmentManager(),
								application);
					}
				});
		
		actionBar.getIconView().setOnClickListener(
				new OnClickListener() {
					public void onClick(final View v) {						
						WelcomeFragment.show(getSupportFragmentManager(),
								application);
					}
				});

		actionBar.addButton(R.drawable.ic_action_send).setOnClickListener(
				new OnClickListener() {
					public void onClick(final View v) {
						startActivity(new Intent(WalletActivity.this,
								SendCoinsActivity.class));
					}
				});

		actionBar.addButton(R.drawable.ic_action_receive).setOnClickListener(
				new OnClickListener() {
					public void onClick(final View v) {
						startActivity(new Intent(WalletActivity.this,
								RequestCoinsActivity.class));
					}
				});

		infoButton = actionBar.addButton(R.drawable.ic_action_info);

		infoButton.setOnClickListener(new OnClickListener() {
			public void onClick(final View v) {
				WalletApplication application = (WalletApplication) getApplication();

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
						WalletAddressesActivity
								.start(WalletActivity.this, true);
					}
				});

		actionBar.addButton(R.drawable.ic_action_exchange).setOnClickListener(
				new OnClickListener() {
					public void onClick(final View v) {
						startActivity(new Intent(WalletActivity.this,
								ExchangeRatesActivity.class));
					}
				});

		/*
		 * if ((getResources().getConfiguration().screenLayout &
		 * Configuration.SCREENLAYOUT_SIZE_MASK) >=
		 * Configuration.SCREENLAYOUT_SIZE_LARGE) {
		 * actionBar.addButton(R.drawable
		 * .ic_action_address_book).setOnClickListener(new OnClickListener() {
		 * public void onClick(final View v) {
		 * WalletAddressesActivity.start(WalletActivity.this, true); } }); }
		 * 
		 * if ((getResources().getConfiguration().screenLayout &
		 * Configuration.SCREENLAYOUT_SIZE_MASK) <
		 * Configuration.SCREENLAYOUT_SIZE_LARGE) { final FragmentManager fm =
		 * getSupportFragmentManager(); final FragmentTransaction ft =
		 * fm.beginTransaction();
		 * ft.hide(fm.findFragmentById(R.id.exchange_rates_fragment));
		 * ft.commit(); }
		 */

		checkDialogs();
	}

	@Override
	protected void onResume() {

		WalletApplication application = (WalletApplication) getApplication();

		application.connect();
		
		if (!application.getRemoteWallet().isUptoDate(Constants.MultiAddrTimeThreshold)) {
			application.checkIfWalletHasUpdatedAndFetchTransactions();
		}
		
		EventListeners.addEventListener(eventListener);

		super.onResume();

		checkDialogs();
	}

	@Override
	protected void onPause() {
		WalletApplication application = (WalletApplication) getApplication();

		application.diconnectSoon();

		EventListeners.removeEventListener(eventListener);

		super.onPause();
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