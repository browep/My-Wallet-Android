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

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.math.BigInteger;
import java.util.Date;

import com.google.android.gcm.GCMRegistrar;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.Transaction;

import piuk.MyRemoteWallet;
import piuk.MyRemoteWallet.SendProgress;
import piuk.blockchain.android.Constants;
import piuk.blockchain.android.R;
import piuk.blockchain.android.WalletApplication;
import piuk.blockchain.android.ui.AbstractWalletActivity.QrCodeDelagate;
import piuk.blockchain.android.ui.SendCoinsFragment.FeePolicy;
import piuk.blockchain.android.ui.dialogs.WelcomeDialog;
import piuk.blockchain.android.util.ActionBarFragment;
import piuk.blockchain.android.util.ErrorReporter;
import piuk.blockchain.android.util.Iso8601Format;
import piuk.blockchain.android.util.WalletUtils;
import android.annotation.SuppressLint;
import android.app.Activity;
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
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.FrameLayout;

public final class WalletActivity extends AbstractWalletActivity {
	public static WalletActivity instance = null;

	AsyncTask<Void, Void, Void> mRegisterTask;
	WalletTransactionsFragment transactionsFragment = null;
	FrameLayout frameLayoutContainer = null;

	private static final int DIALOG_EXPORT_KEYS = 1;

	long lastMesssageTime = 0;

	private final BroadcastReceiver mHandleMessageReceiver =
			new BroadcastReceiver() { 
		@Override
		public void onReceive(Context context, Intent intent) {

			//Throttle messages to once every 30 seconds 
			if (lastMesssageTime > System.currentTimeMillis()-30000) {
				return;
			}

			lastMesssageTime = System.currentTimeMillis();

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


	@SuppressLint("NewApi")
	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		instance = this;

		ErrorReporter.getInstance().check(this);

		setContentView(R.layout.wallet_content);

		final ActionBarFragment actionBar = getActionBarFragment();

		actionBar.setPrimaryTitle(R.string.app_name);

		frameLayoutContainer = (FrameLayout)this.findViewById(R.id.frame_layout_container);

		transactionsFragment = (WalletTransactionsFragment) getSupportFragmentManager()
				.findFragmentById(R.id.wallet_transactions_fragment);

		final Activity activity = this;

		actionBar.getPrimaryTitleView().setOnClickListener(
				new OnClickListener() {
					public void onClick(final View v) {
						if (application.getRemoteWallet() == null)
							return;

						WelcomeDialog.show(getSupportFragmentManager(), activity, application);
					}
				});

		actionBar.getIconView().setOnClickListener(
				new OnClickListener() {
					public void onClick(final View v) {		
						if (application.getRemoteWallet() == null)
							return;

						WelcomeDialog.show(getSupportFragmentManager(), activity, application);
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

		actionBar.addButton(android.R.drawable.ic_menu_more).setOnClickListener(
				new OnClickListener() {
					public void onClick(final View v) {
						openOptionsMenu();
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
	public boolean onCreateOptionsMenu(final Menu menu)
	{
		super.onCreateOptionsMenu(menu);

		getMenuInflater().inflate(R.menu.main_menu, menu);

		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(final Menu menu)
	{
		super.onPrepareOptionsMenu(menu);

		final boolean isInP2PMode = application.isInP2PFallbackMode();

		MenuItem leavP2PMode = menu.findItem(R.id.menu_leave_p2p_mode);

		leavP2PMode.setVisible(isInP2PMode);

		MenuItem enterP2PMode = menu.findItem(R.id.menu_start_p2p_mode);

		enterP2PMode.setVisible(!isInP2PMode);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		WalletApplication application = (WalletApplication) getApplication();

		switch (item.getItemId()) {
		case R.id.menu_account_settings:
			if (application.getRemoteWallet() == null)
				return false; 

			Intent browserIntent = new Intent(
					Intent.ACTION_VIEW,
					Uri.parse("https://"+Constants.BLOCKCHAIN_DOMAIN+"/wallet/iphone-view?guid="
							+ application.getRemoteWallet().getGUID()
							+ "&sharedKey="
							+ application.getRemoteWallet().getSharedKey()));

			startActivity(browserIntent);

			return true;
		case R.id.wallet_options_address_book:
			if (application.getRemoteWallet() == null)
				return false;

			WalletAddressesActivity.start(WalletActivity.this, false);

			return true;

		case R.id.wallet_options_exchange_rates:

			if (application.getRemoteWallet() == null)
				return false;

			startActivity(new Intent(WalletActivity.this,
					ExchangeRatesActivity.class));

			return true;
		case R.id.scan_private_key:
			if (application.getRemoteWallet() == null)
				return false;

			showQRReader(new QrCodeDelagate() {
				@Override
				public void didReadQRCode(String data) throws Exception {
					handleScanPrivateKey(data);
				}
			});
			return true;
		case R.id.backup_wallet:
			showDialog(DIALOG_EXPORT_KEYS);
			return true;
		case R.id.buy_bitcoins:
			openDeopositPage();
			return true;
		case R.id.menu_leave_p2p_mode:
			application.leaveP2PMode();
			return true;
		case R.id.menu_start_p2p_mode:
			AlertDialog.Builder builder = new AlertDialog.Builder(self);

			builder.setTitle(R.string.start_p2p_mode);

			builder.setMessage(R.string.start_p2p_mode_description);

			builder.setNegativeButton(R.string.ignore, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					dialog.dismiss();
				}
			});

			builder.setPositiveButton(R.string.start_p2p_mode, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					startP2PMode();	
				}
			});

			builder.show();
			return true;
		default:
			return false;
		}
	}

	public void openDeopositPage() {
		if (application.getRemoteWallet() == null)
			return;

		MyRemoteWallet wallet = application.getRemoteWallet();

		//Don't allow deposits into new wallets
		if (wallet == null || wallet.isNew())
			return;

		Address address = application.determineSelectedAddress();

		if (address == null)
			return;

		String sharedKey = wallet.getSharedKey();

		String guid = wallet.getGUID();

		Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://"+Constants.BLOCKCHAIN_DOMAIN+"/deposit?address="+address+"&guid="+guid+"&sharedKey="+sharedKey));

		startActivity(browserIntent);
	}

	public void handleScanPrivateKey(String data) throws Exception {
		String format = WalletUtils.detectPrivateKeyFormat(data);

		final ECKey key = WalletUtils.parsePrivateKey(format, data);

		final AlertDialog.Builder b = new AlertDialog.Builder(this);

		b.setPositiveButton(android.R.string.yes, null);

		b.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			}
		});

		b.setTitle("Scan Private Key");

		b.setMessage("Fetching Balance. Please Wait");

		final AlertDialog dialog = b.show();

		dialog.getButton(Dialog.BUTTON1).setEnabled(false);					

		new Thread() {
			public void run() {
				try {
					BigInteger balance = MyRemoteWallet.getAddressBalance(key.toAddress(NetworkParameters.prodNet()).toString());

					//Try compressed instead
					if (balance.longValue() == 0) {
						balance = MyRemoteWallet.getAddressBalance(key.toAddressCompressed(NetworkParameters.prodNet()).toString());
					}

					final BigInteger finalBalance = balance;

					handler.post(new Runnable() {
						@Override
						public void run() {

							if (finalBalance.longValue() == 0) {
								dialog.setMessage("The Balance of this address is zero.");	
								return;
							}

							dialog.getButton(Dialog.BUTTON1).setEnabled(true);

							dialog.getButton(Dialog.BUTTON1).setOnClickListener(new OnClickListener() {
								@Override
								public void onClick(View v) {

									try {
										MyRemoteWallet wallet = new MyRemoteWallet();

										wallet.addKey(key, null);

										Address to = application.determineSelectedAddress();

										if (to == null) {
											handler.post(new Runnable() {
												public void run() {
													dialog.dismiss();
												}
											});

											return;
										}

										BigInteger baseFee = wallet.getBaseFee();

										wallet.sendCoinsAsync(to.toString(), finalBalance.subtract(baseFee), FeePolicy.FeeForce, baseFee, new SendProgress() {

											@Override
											public boolean onReady(
													Transaction tx,
													BigInteger fee,
													FeePolicy feePolicy,
													long priority) {
												return true;
											}

											@Override
											public void onSend(
													Transaction tx,
													String message) {
												handler.post(new Runnable() {
													public void run() {
														dialog.dismiss();

														longToast("Private Key Successfully Swept");
													}
												});
											}

											@Override
											public ECKey onPrivateKeyMissing(String address) {
												return null;
											}

											@Override
											public void onError(final String message) {
												handler.post(new Runnable() {
													public void run() {
														dialog.dismiss();

														longToast(message);
													}
												});															
											}

											@Override
											public void onProgress(String message) {}
										});

									} catch (final Exception e) {
										e.getLocalizedMessage();

										handler.post(new Runnable() {
											public void run() {
												dialog.dismiss();

												longToast(e.getLocalizedMessage());
											}
										});
									}
								}
							});

							dialog.setMessage("The Balance of this address is "+WalletUtils.formatValue(finalBalance)+" BTC. Would you like to sweep it?");
						}
					});
				} catch (final Exception e) {
					e.printStackTrace();

					handler.post(new Runnable() {
						public void run() {
							dialog.dismiss();

							longToast(e.getLocalizedMessage());
						}
					});
				}
			}
		}.start();

	}

	private Dialog createExportKeysDialog()
	{
		final View view = getLayoutInflater().inflate(R.layout.export_keys_dialog, null);

		final AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setInverseBackgroundForced(true);
		builder.setTitle(R.string.export_keys_dialog_title);
		builder.setView(view);
		builder.setPositiveButton(R.string.export_keys_dialog_button_export, new Dialog.OnClickListener()
		{
			public void onClick(final DialogInterface dialog, final int which)
			{
				exportPrivateKeys();
			}
		});

		builder.setNegativeButton(R.string.button_cancel, new Dialog.OnClickListener()
		{
			public void onClick(final DialogInterface dialog, final int which)
			{
				dialog.dismiss();
			}
		});


		final AlertDialog dialog = builder.create();

		return dialog;
	}


	private void mailPrivateKeys(final File file)
	{
		final Intent intent = new Intent(Intent.ACTION_SEND);
		intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.export_keys_dialog_mail_subject));
		intent.putExtra(Intent.EXTRA_TEXT,
				getString(R.string.export_keys_dialog_mail_text) + "\n\n" + String.format(Constants.WEBMARKET_APP_URL, getPackageName()) + "\n\n"
						+ Constants.SOURCE_URL + '\n');
		intent.setType("x-bitcoin/private-keys");
		intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
		startActivity(Intent.createChooser(intent, getString(R.string.export_keys_dialog_mail_intent_chooser)));
	}

	private void exportPrivateKeys()
	{

		if (application.getRemoteWallet() == null)
			return;

		try
		{
			Constants.EXTERNAL_WALLET_BACKUP_DIR.mkdirs();
			final File file = new File(Constants.EXTERNAL_WALLET_BACKUP_DIR, "wallet-"
					+ Iso8601Format.newDateFormat().format(new Date()) + ".aes.json");

			final Writer cipherOut = new FileWriter(file);
			cipherOut.write(application.getRemoteWallet().getPayload());
			cipherOut.close();

			final AlertDialog.Builder dialog = new AlertDialog.Builder(this).setInverseBackgroundForced(true).setMessage(
					getString(R.string.export_keys_dialog_success, file));
			dialog.setPositiveButton(R.string.export_keys_dialog_button_archive, new Dialog.OnClickListener()
			{
				public void onClick(final DialogInterface dialog, final int which)
				{
					mailPrivateKeys(file);
				}
			});
			dialog.setNegativeButton(R.string.button_dismiss, null);
			dialog.show();
		}
		catch (final Exception x)
		{
			new AlertDialog.Builder(this).setInverseBackgroundForced(true).setIcon(android.R.drawable.ic_dialog_alert)
			.setTitle(R.string.import_export_keys_dialog_failure_title)
			.setMessage(getString(R.string.export_keys_dialog_failure, x.getMessage())).setNeutralButton(R.string.button_dismiss, null)
			.show();

			x.printStackTrace();
		}
	}


	@Override
	protected Dialog onCreateDialog(final int id) {
		if (id == DIALOG_EXPORT_KEYS) {
			return createExportKeysDialog();
		} else {
			return null;
		}
	}
}