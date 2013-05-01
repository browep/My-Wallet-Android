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

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.ArrayUtils;

import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.webkit.WebView;

import com.google.bitcoin.core.Base58;
import com.google.bitcoin.core.DumpedPrivateKey;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.NetworkParameters;

import piuk.BitcoinAddress;
import piuk.BitcoinURI;
import piuk.MyWallet;

import com.google.bitcoin.uri.BitcoinURIParseException;

import piuk.blockchain.android.R;
import piuk.blockchain.android.Constants;
import piuk.blockchain.android.util.ActionBarFragment;
import piuk.blockchain.android.util.WalletUtils;

/**
 * @author Andreas Schildbach
 */
public final class SendCoinsActivity extends AbstractWalletActivity {
	public static final String INTENT_EXTRA_ADDRESS = "address";
	private static final String INTENT_EXTRA_QUERY = "query";
	final Map<String, ECKey> temporaryPrivateKeys = new HashMap<String, ECKey>();
	public String scanPrivateKeyAddress = null;

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.send_coins_content);

		final ActionBarFragment actionBar = getActionBarFragment();

		actionBar.setPrimaryTitle(R.string.send_coins_activity_title);

		actionBar.setBack(new OnClickListener() {
			public void onClick(final View v) {
				finish();
			}
		});

		actionBar.addButton(R.drawable.ic_action_qr).setOnClickListener(
				new OnClickListener() {
					public void onClick(final View v) {
						showQRReader("uri_qr_code");
					}
				});

		handleIntent(getIntent());
	}


	@Override
	protected void onNewIntent(final Intent intent) {
		handleIntent(intent);
	}

	@Override
	public void onActivityResult(final int requestCode, final int resultCode,
			final Intent intent) {

		String action = super.getQRCodeAction();

		if (action == null)
			return;

		try {

			if (resultCode == RESULT_OK
					&& "QR_CODE"
					.equals(intent.getStringExtra("SCAN_RESULT_FORMAT"))) {
				final String contents = intent.getStringExtra("SCAN_RESULT");
				if (action.equals("private_key_qr")) {
					System.out.println("Scanned PK " + contents);

					ECKey key = null;

					if (scanPrivateKeyAddress != null) {

						String format = WalletUtils.detectPrivateKeyFormat(contents);

						key = WalletUtils.parsePrivateKey(format, contents);

						if (!key.toAddressCompressed(Constants.NETWORK_PARAMETERS)
								.toString().equals(scanPrivateKeyAddress) && 
								!key.toAddress(Constants.NETWORK_PARAMETERS)
								.toString().equals(scanPrivateKeyAddress)) {
							throw new Exception(getString(R.string.wrong_private_key));
						} else {
							//Success
							temporaryPrivateKeys.put(scanPrivateKeyAddress, key);
						}
					} else {
						updateSendCoinsFragment(contents, null);
					}
				} else if (action.equals("uri_qr_code")) {

					final BitcoinURI bitcoinUri = new BitcoinURI(contents);
					final BitcoinAddress address = bitcoinUri.getAddress();

					updateSendCoinsFragment(
							address != null ? address.toString() : null,
									bitcoinUri.getAmount());
				}

			}

			synchronized (temporaryPrivateKeys) {
				temporaryPrivateKeys.notify();
			}

			scanPrivateKeyAddress = null;

		} catch (final Exception e) {
			longToast(e.getLocalizedMessage());
		}
	}

	private void handleIntent(final Intent intent) {
		final String action = intent.getAction();
		final Uri intentUri = intent.getData();
		final String scheme = intentUri != null ? intentUri.getScheme() : null;

		final String address;
		final BigInteger amount;

		if ((Intent.ACTION_VIEW.equals(action))
				&& intentUri != null
				&& "bitcoin".equals(scheme)) {
			try {
				final BitcoinURI bitcoinUri = new BitcoinURI(
						intentUri.toString());
				address = bitcoinUri.getAddress().toString();
				amount = bitcoinUri.getAmount();
			} catch (final BitcoinURIParseException x) {
				errorDialog(R.string.send_coins_uri_parse_error_title,
						intentUri.toString());
				return;
			}
		} else if (Intent.ACTION_WEB_SEARCH.equals(action)
				&& intent.hasExtra(INTENT_EXTRA_QUERY)) {
			try {
				final BitcoinURI bitcoinUri = new BitcoinURI(
						intent.getStringExtra(INTENT_EXTRA_QUERY));
				if (bitcoinUri.getAddress() == null) {
					address = null;
					amount = null;
				} else {
					address = bitcoinUri.getAddress().toString();
					amount = bitcoinUri.getAmount();
				}
			} catch (final BitcoinURIParseException x) {
				errorDialog(R.string.send_coins_uri_parse_error_title,
						intentUri.toString());
				return;
			}
		} else if (intent.hasExtra(INTENT_EXTRA_ADDRESS)) {
			address = intent.getStringExtra(INTENT_EXTRA_ADDRESS);
			amount = null;
		} else {
			return;
		}

		if (address != null || amount != null)
			updateSendCoinsFragment(address, amount);
		else
			longToast(R.string.send_coins_parse_address_error_msg);
	}

	private void updateSendCoinsFragment(final String address,
			final BigInteger amount) {
		final SendCoinsFragment sendCoinsFragment = (SendCoinsFragment) getSupportFragmentManager()
				.findFragmentById(R.id.send_coins_fragment);

		sendCoinsFragment.update(address, amount);
	}
}
