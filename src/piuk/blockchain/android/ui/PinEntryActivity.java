package piuk.blockchain.android.ui;

/*Client ID: 	381130279932.apps.googleusercontent.com
Redirect URIs: 	urn:ietf:wg:oauth:2.0:oob http://localhost
Application type: 	Android
Package name: 	com.ultimasquare.pinview
Certificate fingerprint (SHA1): 	86:F2:4D:FD:34:98:BF:0C:47:94:34:D4:8C:68:A3:84:B7:D7:B2:0F
Deep Linking: 	Disabled*/


import java.io.DataOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import org.apache.commons.io.IOUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.spongycastle.util.encoders.Hex;

import piuk.MyWallet;
import piuk.blockchain.android.R;
import piuk.blockchain.android.WalletApplication;

import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.content.Context;
import android.content.SharedPreferences.Editor;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class PinEntryActivity extends AbstractWalletActivity {
	public static final int UNKNOWN = 1;
	public static final int BEGIN_SETUP = 1;
	public static final int CONFIRM_PIN_SETUP = 2;
	public static final int BEGIN_CHECK_PIN = 4;
	public static final int FINISHING_SETUP = 3;
	public static final int VALIDATING_PIN = 5;
	public static final int PBKDF2Iterations = 2000;

	private static final String WebROOT = "https://blockchain.info/pin-store";

	int stage = UNKNOWN;

	String previousEntered;
	String userEntered;
	String userPin="8888";

	final int PIN_LENGTH = 4;
	boolean keyPadLockedFlag = false;
	Context appContext;

	TextView titleView;

	TextView pinBox0;
	TextView pinBox1;
	TextView pinBox2;
	TextView pinBox3;

	TextView [] pinBoxArray;

	TextView statusView;

	Button button0;
	Button button1;
	Button button2;
	Button button3;
	Button button4;
	Button button5;
	Button button6;
	Button button7;
	Button button8;
	Button button9;
	Button button10;
	Button buttonDelete;

	public static String postURL(String request, String urlParameters) throws Exception {

		URL url = new URL(request);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		try {
			connection.setDoOutput(true);
			connection.setDoInput(true);
			connection.setInstanceFollowRedirects(false);
			connection.setRequestMethod("POST");
			connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			connection.setRequestProperty("charset", "utf-8");
			connection.setRequestProperty("Accept", "application/json");
			connection.setRequestProperty("Content-Length", "" + Integer.toString(urlParameters.getBytes().length));
			connection.setUseCaches (false);

			connection.connect();

			DataOutputStream wr = new DataOutputStream(connection.getOutputStream ());
			wr.writeBytes(urlParameters);
			wr.flush();
			wr.close();

			connection.setConnectTimeout(30000);
			connection.setReadTimeout(30000);

			connection.setInstanceFollowRedirects(false);

			if (connection.getResponseCode() == 500)
				return IOUtils.toString(connection.getErrorStream(), "UTF-8");
			else
				return IOUtils.toString(connection.getInputStream(), "UTF-8");

		} finally {
			connection.disconnect();
		}
	}

	public static JSONObject apiGetValue(String key, String pin) throws Exception {

		System.out.println(pin);

		StringBuilder args = new StringBuilder();

		args.append("key=" + key);
		args.append("&pin="+ pin);
		args.append("&method=get");

		String response = postURL(WebROOT, args.toString());

		if (response == null || response.length() == 0)
			throw new Exception("Invalid Server Response");

		try {
			return (JSONObject) new JSONParser().parse(response);
		} catch (ParseException e) {
			throw new Exception("Invalid Server Response");
		}		
	}

	public static JSONObject apiStoreKey(String key, String value, String pin) throws Exception {
		StringBuilder args = new StringBuilder();

		args.append("key=" + key);
		args.append("&value=" + value);
		args.append("&pin="+pin);
		args.append("&method=put");

		String response = postURL(WebROOT, args.toString());

		if (response == null || response.length() == 0)
			throw new Exception("Invalid Server Response");

		try {
			return (JSONObject) new JSONParser().parse(response);
		} catch (ParseException e) {
			throw new Exception("Invalid Server Response");
		}		
	}

	public static void clearPrefValues(WalletApplication application) throws Exception {
		Editor editor = PreferenceManager.getDefaultSharedPreferences(application).edit();

		editor.remove("pin_kookup_key");
		editor.remove("encrypted_password");

		if (!editor.commit()) {
			throw new Exception("Error Saving Preferences");
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		appContext = this;
		userEntered = "";

		setContentView(R.layout.activity_pin_entry_view);

		buttonDelete = (Button) findViewById(R.id.buttonDeleteBack);
		buttonDelete.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {

				if (keyPadLockedFlag == true)
				{
					return;
				}

				if (userEntered.length()>0)
				{
					userEntered = userEntered.substring(0,userEntered.length()-1);
					pinBoxArray[userEntered.length()].setText("");
				}
			}
		}
				);

		titleView = (TextView)findViewById(R.id.titleBox);

		pinBox0 = (TextView)findViewById(R.id.pinBox0);
		pinBox1 = (TextView)findViewById(R.id.pinBox1);
		pinBox2 = (TextView)findViewById(R.id.pinBox2);
		pinBox3 = (TextView)findViewById(R.id.pinBox3);

		pinBoxArray = new TextView[PIN_LENGTH];
		pinBoxArray[0] = pinBox0;
		pinBoxArray[1] = pinBox1;
		pinBoxArray[2] = pinBox2;
		pinBoxArray[3] = pinBox3;



		statusView = (TextView) findViewById(R.id.statusMessage);

		View.OnClickListener pinButtonHandler = new View.OnClickListener() {
			public void onClick(View v) {

				if (keyPadLockedFlag == true)
				{
					return;
				}

				Button pressedButton = (Button)v;

				if (userEntered.length()<PIN_LENGTH)
				{ 
					final String PIN = userEntered + pressedButton.getText();

					userEntered = PIN;

					//Update pin boxes
					pinBoxArray[userEntered.length()-1].setText("8");

					if (userEntered.length() == PIN_LENGTH)
					{
						if (stage == BEGIN_CHECK_PIN) {
							stage = VALIDATING_PIN;

							Toast.makeText(application, "Validating PIN", Toast.LENGTH_LONG)
							.show();	

							new Thread(new Runnable() {
								public void run() {
									String pin_lookup_key = PreferenceManager.getDefaultSharedPreferences(application).getString("pin_kookup_key", null);
									String encrypted_password = PreferenceManager.getDefaultSharedPreferences(application).getString("encrypted_password", null);

									try {
										JSONObject response = apiGetValue(pin_lookup_key, PIN);

										String decryptionKey = (String) response.get("success");

										if (decryptionKey != null) {											
											String password = MyWallet.decrypt(encrypted_password, decryptionKey, PBKDF2Iterations);

											application.checkIfWalletHasUpdatedAndFetchTransactions(password, new SuccessCallback() {
												@Override
												public void onSuccess() {
													handler.post(new Runnable() {
														public void run() {
															Toast.makeText(application, R.string.welcome_title, Toast.LENGTH_LONG)
															.show();	

															finish();
														}
													});
												}

												@Override
												public void onFail() {
													handler.post(new Runnable() {
														public void run() {
															Toast.makeText(application,
																	R.string.toast_wallet_decryption_failed, Toast.LENGTH_LONG)
																	.show();	

															try {
																clearPrefValues(application);
															} catch (Exception e) {
																e.printStackTrace();
															}

															begin();
														}
													});
												}
											});
										} else if (response.get("error") != null) {

											//PINIncorrect
											if (!response.containsKey("code") || ((Number)response.get("code")).intValue() != 2) {
												clearPrefValues(application);
											}

											throw new Exception((String) response.get("error"));
										} else {
											clearPrefValues(application);

											throw new Exception("Unknown Error");
										}
									} catch (final Exception e) {
										e.printStackTrace();
										
										handler.post(new Runnable() {
											public void run() {
												Toast.makeText(application,
														e.getLocalizedMessage(), Toast.LENGTH_LONG)
														.show();	

												begin();
											}
										});
									}
								}
							}).start();
						} if (stage == BEGIN_SETUP) {
							previousEntered = userEntered;

							clear();

							titleView.setText("Confirm PIN");	
							statusView.setText("Please renter the PIN code");

							stage = CONFIRM_PIN_SETUP;
						} else if (stage == CONFIRM_PIN_SETUP) {
							if (previousEntered.equals(userEntered)) {
								stage = FINISHING_SETUP;

								statusView.setText("Saving PIN. Please Wait.");

								new Thread(new Runnable() {
									public void run() {
										try {
											byte[] bytes = new byte[16];

											SecureRandom random = new SecureRandom();

											random.nextBytes(bytes);

											final String key = new String(Hex.encode(bytes), "UTF-8");

											random.nextBytes(bytes);

											final String value = new String(Hex.encode(bytes), "UTF-8");

											JSONObject response = apiStoreKey(key, value, PIN);

											if (response.get("success") != null) {
												handler.post(new Runnable() {
													public void run() {
														try {
															Editor editor = PreferenceManager.getDefaultSharedPreferences(application).edit();

															editor.putString("pin_kookup_key", key);
															editor.putString("encrypted_password", MyWallet.encrypt(application.getRemoteWallet().getTemporyPassword(), value, PBKDF2Iterations));

															if (!editor.commit()) {
																throw new Exception("Error Saving Preferences");
															}

															Toast.makeText(application,
																	R.string.toast_pin_saved, Toast.LENGTH_LONG)
																	.show();	

															finish();
														} catch (Exception e) {
															e.printStackTrace();

															Toast.makeText(application,
																	e.getLocalizedMessage(), Toast.LENGTH_LONG)
																	.show();	

															begin();
														}
													}
												});
											} else if (response.get("error") != null) {
												throw new Exception((String) response.get("error"));
											} else {
												throw new Exception("Unknown Error");
											}
										} catch (final Exception e) {
											e.printStackTrace();

											handler.post(new Runnable() {
												public void run() {
													Toast.makeText(application,
															e.getLocalizedMessage(), Toast.LENGTH_LONG)
															.show();	

													begin();
												}
											});
										}
									}
								}).start();
							} else {
								statusView.setText("PIN does not match");
																
								begin();
							}
						} else {
							begin();
						}
					}	
				} 
				else
				{
					//Roll over
					pinBoxArray[0].setText("");
					pinBoxArray[1].setText("");
					pinBoxArray[2].setText("");
					pinBoxArray[3].setText("");

					userEntered = "";

					statusView.setText("");

					userEntered = userEntered + pressedButton.getText();

					//Update pin boxes
					pinBoxArray[userEntered.length()-1].setText("8");
				}
			}
		};


		button0 = (Button)findViewById(R.id.button0);
		button0.setOnClickListener(pinButtonHandler);

		button1 = (Button)findViewById(R.id.button1);
		button1.setOnClickListener(pinButtonHandler);

		button2 = (Button)findViewById(R.id.button2);
		button2.setOnClickListener(pinButtonHandler);

		button3 = (Button)findViewById(R.id.button3);
		button3.setOnClickListener(pinButtonHandler);

		button4 = (Button)findViewById(R.id.button4);
		button4.setOnClickListener(pinButtonHandler);

		button5 = (Button)findViewById(R.id.button5);
		button5.setOnClickListener(pinButtonHandler);

		button6 = (Button)findViewById(R.id.button6);
		button6.setOnClickListener(pinButtonHandler);

		button7 = (Button)findViewById(R.id.button7);
		button7.setOnClickListener(pinButtonHandler);

		button8 = (Button)findViewById(R.id.button8);
		button8.setOnClickListener(pinButtonHandler);

		button9 = (Button)findViewById(R.id.button9);
		button9.setOnClickListener(pinButtonHandler);

		buttonDelete = (Button)findViewById(R.id.buttonDeleteBack);
	}


	public void begin() {
		clear();

		String pin_lookup_key = PreferenceManager.getDefaultSharedPreferences(this).getString("pin_kookup_key", null);
		String encrypted_password = PreferenceManager.getDefaultSharedPreferences(this).getString("encrypted_password", null);

		if (pin_lookup_key == null || encrypted_password == null) {
			stage = BEGIN_SETUP;

			titleView.setText("Create New PIN");

			if (application.getRemoteWallet() == null || application.hasDecryptionError) {
				if (application.getGUID() != null && application.getSharedKey() != null) {
					PasswordFragment.show(
							getSupportFragmentManager(),
							new SuccessCallback() { 
								public void onSuccess() {
									statusView.setText("Password Ok. Please create a PIN.");
								}
								public void onFail() {							
									WelcomeFragment.show(getSupportFragmentManager(), (WalletApplication)getApplication());
								}
							}, PasswordFragment.PasswordTypeMain);
				} else {
					WelcomeFragment.show(getSupportFragmentManager(), (WalletApplication)getApplication());
				}
			} else {
				WelcomeFragment.hide();
			}
		} else {
			titleView.setText("Enter PIN");	

			stage = BEGIN_CHECK_PIN;
			
			WelcomeFragment.hide();
		}
	}

	@Override
	protected void onResume() {
		super.onResume();

		begin();
	}

	public void clear() {
		statusView.setText("");

		//Roll over
		pinBoxArray[0].setText("");
		pinBoxArray[1].setText("");
		pinBoxArray[2].setText("");
		pinBoxArray[3].setText("");

		userEntered = "";
	}

	@Override
	public void onBackPressed() {
		// TODO Auto-generated method stub

		//App not allowed to go back to Parent activity until correct pin entered.
		return;
		//super.onBackPressed();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		//getMenuInflater().inflate(R.menu.activity_pin_entry_view, menu);
		return true;
	}
}
