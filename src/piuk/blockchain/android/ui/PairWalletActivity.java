package piuk.blockchain.android.ui;

import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;
import piuk.MyRemoteWallet;
import piuk.MyWallet;
import piuk.blockchain.android.R;
import piuk.blockchain.android.WalletApplication;
import piuk.blockchain.android.ui.dialogs.RequestIdentifierDialog;
import piuk.blockchain.android.ui.dialogs.RequestPasswordDialog;
import piuk.blockchain.android.ui.dialogs.WelcomeDialog;
import piuk.blockchain.android.util.ActionBarFragment;
import java.util.regex.Pattern;

import org.spongycastle.util.encoders.Hex;

public class PairWalletActivity extends AbstractWalletActivity {
	final PairWalletActivity activity = this;

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.pair_wallet_content);

		final ActionBarFragment actionBar = getActionBarFragment();

		actionBar.setPrimaryTitle(R.string.pair_wallet_title);

		actionBar.setBack(new OnClickListener() {
			public void onClick(final View v) {
				finish();
			}
		});

		final Button pairDeviceButton = (Button) getWindow().findViewById(
				R.id.pair_qr_button);

		pairDeviceButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				showQRReader("scan_pairing_code");
			}
		});

		final Button pairManuallyButton = (Button) getWindow().findViewById(
				R.id.pair_manually_button);

		pairManuallyButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				RequestIdentifierDialog.show(getSupportFragmentManager(), new RequestIdentifierDialog.SuccessCallback() {
					@Override
					public void onSuccess(String guid) {
						pairManually(guid);
					}

					@Override
					public void onFail(String message) {
						finish();

						Toast.makeText(application, message, Toast.LENGTH_LONG).show();
					}
				});
			}
		});
	}

	public void pairManually(final String guid) {

		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					final String payload = MyRemoteWallet.getWalletManualPairing(guid);
					
					handler.post(new Runnable() {

						@Override
						public void run() {
							RequestPasswordDialog.show(
									getSupportFragmentManager(),
									new SuccessCallback() {  
										public void onSuccess() {
											String password = RequestPasswordDialog.getPasswordResult();

											try {
												MyWallet wallet = new MyWallet(payload, password);

												String sharedKey = wallet.getSharedKey();

												application.clearWallet();

												PinEntryActivity.clearPrefValues(application);

												Editor edit = PreferenceManager.getDefaultSharedPreferences(activity).edit();

												edit.putString("guid", guid);
												edit.putString("sharedKey", sharedKey);

												edit.commit();

												application.checkIfWalletHasUpdatedAndFetchTransactions(password, guid, sharedKey, new SuccessCallback(){
													@Override
													public void onSuccess() {
																											
														finish();
													}

													@Override
													public void onFail() {
														finish();

														Toast.makeText(application, R.string.toast_error_syncing_wallet, Toast.LENGTH_LONG)
														.show();
													}
												});
											} catch (final Exception e) {

												Toast.makeText(application, e.getLocalizedMessage(), Toast.LENGTH_LONG)
												.show();

												application.writeException(e);

												finish();
											}


										}
										public void onFail() {							
											WelcomeDialog.show(getSupportFragmentManager(), (WalletApplication)getApplication());
										}
									}, RequestPasswordDialog.PasswordTypeMainNoValidate);
						}
					});
				} catch (final Exception e) {
					e.printStackTrace();

					handler.post(new Runnable() {
						public void run() {

							Toast.makeText(application, e.getLocalizedMessage(), Toast.LENGTH_LONG)
							.show();

							application.writeException(e);

							finish();
						}
					});
				}
			}
		}).start();
	}

	
	@Override
	public void onActivityResult(final int requestCode, final int resultCode,
			final Intent intent) {
		
		String action = super.getQRCodeAction();

		if (action == null)
			return;
		
		if (action.equals("scan_pairing_code")
				&& resultCode == RESULT_OK
				&& "QR_CODE"
				.equals(intent.getStringExtra("SCAN_RESULT_FORMAT"))) {
			final WalletApplication application = (WalletApplication) getApplication();

			try {
				final String raw_code = intent.getStringExtra("SCAN_RESULT");

				if (raw_code == null || raw_code.length() == 0) {
					throw new Exception("Invalid Pairing QR Code");
				}

				if (raw_code.charAt(0) != '1') {
					throw new Exception("Invalid Pairing Version Code " + raw_code.charAt(0));
				}

				final Handler handler = new Handler();

				{
					String[] components = raw_code.split("\\|", Pattern.LITERAL);

					if (components.length < 3) {
						throw new Exception("Invalid Pairing QR Code. Not enough components.");
					}

					final String guid = components[1];
					if (guid.length() != 36) {
						throw new Exception("Invalid Pairing QR Code. GUID wrong length.");
					}

					final String encrypted_data = components[2];

					new Thread(new Runnable() {

						@Override
						public void run() {

							try {
								String temp_password = MyRemoteWallet.getPairingEncryptionPassword(guid);

								String decrypted = MyWallet.decrypt(encrypted_data, temp_password, MyWallet.DefaultPBKDF2Iterations);

								String[] sharedKeyAndPassword = decrypted.split("\\|", Pattern.LITERAL);

								if (sharedKeyAndPassword.length < 2) {
									throw new Exception("Invalid Pairing QR Code. sharedKeyAndPassword Incorrect number of components.");
								}

								final String sharedKey = sharedKeyAndPassword[0];
								if (sharedKey.length() != 36) {
									throw new Exception("Invalid Pairing QR Code. sharedKey wrong length.");
								}

								final String password = new String(Hex.decode(sharedKeyAndPassword[1]), "UTF-8");

								application.clearWallet();

								PinEntryActivity.clearPrefValues(application);

								Editor edit = PreferenceManager.getDefaultSharedPreferences(
										activity).edit();

								edit.putString("guid", guid);
								edit.putString("sharedKey", sharedKey);

								edit.commit();

								handler.post(new Runnable() {

									@Override
									public void run() {
										application.checkIfWalletHasUpdatedAndFetchTransactions(password, guid, sharedKey, new SuccessCallback(){

											@Override
											public void onSuccess() {													
												finish();
											}

											@Override
											public void onFail() {
												finish();

												Toast.makeText(application, R.string.toast_error_syncing_wallet, Toast.LENGTH_LONG)
												.show();
											}
										});
									}
								});

							} catch (final Exception e) {
								e.printStackTrace();

								handler.post(new Runnable() {
									public void run() {

										Toast.makeText(application, e.getLocalizedMessage(), Toast.LENGTH_LONG)
										.show();

										e.printStackTrace();

										application.writeException(e);
									}
								});
							}
						}
					}).start();
				}
			} catch (Exception e) {

				Toast.makeText(application, e.getLocalizedMessage(), Toast.LENGTH_LONG)
				.show();

				e.printStackTrace();

				application.writeException(e);
			}
		}
	}
}
