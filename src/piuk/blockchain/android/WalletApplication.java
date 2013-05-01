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

package piuk.blockchain.android;

import android.app.Activity;
import android.app.Application;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.*;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.widget.Toast;
import com.google.bitcoin.core.*;

import org.apache.commons.io.IOUtils;
import piuk.BitcoinAddress;
import piuk.EventListeners;
import piuk.Hash;
import piuk.MyRemoteWallet;
import piuk.MyRemoteWallet.NotModfiedException;
import piuk.blockchain.android.R;
import piuk.blockchain.android.ui.AbstractWalletActivity;
import piuk.blockchain.android.ui.PinEntryActivity;
import piuk.blockchain.android.ui.SuccessCallback;
import piuk.blockchain.android.ui.dialogs.RequestPasswordDialog;
import piuk.blockchain.android.util.ErrorReporter;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.CookieHandler;
import java.security.Security;
import java.util.ArrayList;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;

/**
 * @author Andreas Schildbach
 */
public class WalletApplication extends Application {
	private MyRemoteWallet blockchainWallet;

	private final Handler handler = new Handler();
	private Timer timer;
	public int decryptionErrors = 0;


	private final ServiceConnection serviceConnection = new ServiceConnection() {
		private BlockchainService service;

		public void onServiceConnected(final ComponentName name,
				final IBinder binder) {
			service = ((BlockchainService.LocalBinder) binder).getService();
		}

		public void onServiceDisconnected(final ComponentName name) {
		}
	};

	private EventListeners.EventListener eventListener = new EventListeners.EventListener() {
		@Override
		public String getDescription() {
			return "Main Wallet Did Change Listener";
		}

		@Override
		public void onWalletDidChange() {
			try {
				localSaveWallet();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	};

	public void clearWallet() {
		Editor edit = PreferenceManager.getDefaultSharedPreferences(
				this).edit();

		edit.remove("guid");
		edit.remove("sharedKey");

		this.blockchainWallet = null;
		this.decryptionErrors = 0;

		edit.commit();
	}

	public void connect() {
		System.out.println("connect()");

		if (timer != null) {
			try {
				timer.cancel();

				timer.purge();

				timer = null;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		bindService(new Intent(this, BlockchainService.class), serviceConnection, Context.BIND_AUTO_CREATE);
	}

	public void diconnectSoon() {

		try {
			if (timer == null) {
				timer = new Timer();

				timer.schedule(new TimerTask() {

					@Override
					public void run() {
						handler.post(new Runnable() {
							public void run() {
								try {
									System.out.println("diconnectSoon() set blockchainWallet = null");

									blockchainWallet = null;

									decryptionErrors = 0;

								} catch (Exception e) {
									e.printStackTrace();
								}
							}
						});
					}
				}, 20000);

				timer.schedule(new TimerTask() {

					@Override
					public void run() {
						handler.post(new Runnable() {
							public void run() {
								try {
									unbindService(serviceConnection);
								} catch (Exception e) {
									e.printStackTrace();
								}
							}
						});
					}
				}, 2000);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void generateNewWallet() throws Exception {
		this.blockchainWallet = new MyRemoteWallet();

		this.decryptionErrors = 0;
	}

	public void checkWalletStatus(final AbstractWalletActivity activity) {

		if (activity == null || PinEntryActivity.active)
			return;

		System.out.println("checkWalletStatus()");

		boolean passwordSaved = PreferenceManager.getDefaultSharedPreferences(this).contains("encrypted_password");

		if (blockchainWallet != null && decryptionErrors == 0 && passwordSaved) {
			if (!blockchainWallet.isUptoDate(Constants.MultiAddrTimeThreshold)) {
				checkIfWalletHasUpdatedAndFetchTransactions(blockchainWallet.getTemporyPassword());
			} else {
				System.out.println("upToDate()  " + (System.currentTimeMillis() - blockchainWallet.lastMultiAddress) + " ms");
			}
		} else if (blockchainWallet == null || decryptionErrors > 0 || !passwordSaved) {

			//Remove old password 
			String old_password = PreferenceManager.getDefaultSharedPreferences(this).getString("password", null);

			if (old_password != null) {
				readLocalWallet(old_password);

				PreferenceManager.getDefaultSharedPreferences(this).edit().remove("password").commit();
			}

			handler.post(new Runnable() {
				@Override
				public void run() {	
					if (!PinEntryActivity.active) {
						Intent intent = new Intent(activity, PinEntryActivity.class);

						activity.startActivity(intent);
					}
				}
			});
		}	
	}

	@Override
	public void onCreate() {
		super.onCreate();

		ErrorReporter.getInstance().init(this);

		try {
			// Need to save session cookie for kaptcha
			@SuppressWarnings("rawtypes")
			Class aClass = getClass().getClassLoader().loadClass(
					"java.net.CookieManager");

			CookieHandler.setDefault((CookieHandler) aClass.newInstance());

			Security.addProvider(new org.spongycastle.jce.provider.BouncyCastleProvider());

		} catch (Throwable e) {
			e.printStackTrace();
		}


		EventListeners.addEventListener(eventListener);

		connect();
	}

	/*public Wallet getWallet() {
		return blockchainWallet.getBitcoinJWallet();
	}*/

	public MyRemoteWallet getRemoteWallet() {
		return blockchainWallet;
	}


	public String getGUID() {
		return PreferenceManager.getDefaultSharedPreferences(this).getString("guid", null);
	}

	public long getLastTriedToRegisterForNotifications() {
		return PreferenceManager.getDefaultSharedPreferences(this).getLong("last_notification_register", 0);
	}

	public boolean hasRegisteredForNotifications(String guid) {
		String registered_guid = PreferenceManager.getDefaultSharedPreferences(this).getString("registered_guid", null);

		return registered_guid != null && registered_guid.equals(guid); 
	}

	public boolean setLastRegisteredForNotifications(long time) {
		Editor edit = PreferenceManager
				.getDefaultSharedPreferences(
						this
						.getApplicationContext())
						.edit();

		edit.putLong("last_notification_register", time);

		return edit.commit();
	}

	public boolean setRegisteredForNotifications(String guid) {
		Editor edit = PreferenceManager
				.getDefaultSharedPreferences(
						this
						.getApplicationContext())
						.edit();

		edit.putString("registered_guid", guid);

		return edit.commit();
	}

	public void registerForNotificationsIfNeeded(final String registration_id) {

		if (blockchainWallet == null)
			return;

		if (!blockchainWallet.isNew() && !hasRegisteredForNotifications(getGUID())) {

			if (getLastTriedToRegisterForNotifications() > System.currentTimeMillis()-30000) {
				System.out.println("Registered Recently");
				return;
			}

			setLastRegisteredForNotifications(System.currentTimeMillis());

			new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						if (blockchainWallet.registerNotifications(registration_id)) {
							setRegisteredForNotifications(getGUID());
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}

			}).start();
		} else {
			System.out.println("New wallet or already Registered");
		}
	}

	public void unRegisterForNotifications(final String registration_id) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					if (blockchainWallet.unregisterNotifications(registration_id)) {
						setRegisteredForNotifications(null);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

		}).start();
	}

	public String getSharedKey() {
		return PreferenceManager.getDefaultSharedPreferences(this).getString(
				"sharedKey", null);
	}

	public void notifyWidgets() {
		final Context context = getApplicationContext();

		// notify widgets
		final AppWidgetManager appWidgetManager = AppWidgetManager
				.getInstance(context);
		for (final AppWidgetProviderInfo providerInfo : appWidgetManager
				.getInstalledProviders()) {
			// limit to own widgets
			if (providerInfo.provider.getPackageName().equals(
					context.getPackageName())) {
				final Intent intent = new Intent(
						AppWidgetManager.ACTION_APPWIDGET_UPDATE);
				intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS,
						appWidgetManager.getAppWidgetIds(providerInfo.provider));
				context.sendBroadcast(intent);
			}
		}
	}

	public synchronized String readExceptionLog() {
		try {
			FileInputStream multiaddrCacheFile = openFileInput(Constants.EXCEPTION_LOG);

			return IOUtils.toString(multiaddrCacheFile);

		} catch (IOException e1) {
			e1.printStackTrace();

			return null;
		}
	}

	public synchronized void writeException(Exception e) {
		try {
			FileOutputStream file = openFileOutput(Constants.EXCEPTION_LOG,
					MODE_APPEND);

			PrintStream stream = new PrintStream(file);

			e.printStackTrace(stream);

			stream.close();

			file.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}

	public synchronized void writeMultiAddrCache(String repsonse) {
		if (blockchainWallet == null)
			return;

		try {
			FileOutputStream file = openFileOutput(blockchainWallet.getGUID()
					+ Constants.MULTIADDR_FILENAME, Constants.WALLET_MODE);

			file.write(repsonse.getBytes());

			file.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public synchronized void checkIfWalletHasUpdatedAndFetchTransactions(final String password) {
		checkIfWalletHasUpdatedAndFetchTransactions(password, null);
	}

	public synchronized void checkIfWalletHasUpdatedAndFetchTransactions(final String password, final SuccessCallback callbackFinal) {
		if (getGUID() == null || getSharedKey() == null) {
			if (callbackFinal != null) callbackFinal.onFail();
			return;
		}

		checkIfWalletHasUpdatedAndFetchTransactions(password, getGUID(), getSharedKey(), callbackFinal);
	}

	public synchronized void checkIfWalletHasUpdatedAndFetchTransactions(final String password, final String guid, final String sharedKey, final SuccessCallback callbackFinal) {

		new Thread(new Runnable() {
			public void run() {
				System.out.println("checkIfWalletHasUpdatedAndFetchTransactions()");

				String payload = null;
				SuccessCallback callback = callbackFinal;

				try {
					if (blockchainWallet == null) {
						if (readLocalWallet(password)) {
							System.out.println("Try success");

							if (callback != null)  {
								handler.post(new Runnable() {
									public void run() {
										callbackFinal.onSuccess();
									};
								});

								callback = null;
							}

							readLocalMultiAddr();
						}

						payload = MyRemoteWallet.getWalletPayload(guid, sharedKey);				
					} else {
						payload = MyRemoteWallet.getWalletPayload(guid, sharedKey, blockchainWallet.getChecksum());		
					}

				} catch (NotModfiedException e) {
					if (blockchainWallet != null) {

						if (callback != null)  {
							handler.post(new Runnable() {
								public void run() {
									callbackFinal.onSuccess();
								};
							});
							callback = null;
						}
						
						if (!blockchainWallet.isUptoDate(Constants.MultiAddrTimeThreshold)) {
							doMultiAddr();
						} else {
							System.out.println("Skipping doMultiAddr");
						}
					}
				} catch (final Exception e) {
					e.printStackTrace();

					writeException(e);

					handler.post(new Runnable() {
						public void run() {
							Toast.makeText(WalletApplication.this,
									e.getLocalizedMessage(), Toast.LENGTH_SHORT)
									.show();
						}
					});
				}

				if (payload == null) {
					if (callback != null)  {
						handler.post(new Runnable() {
							public void run() {
								callbackFinal.onFail();
							};
						});
						callback = null;
					}
					return;
				}

				try {
					if (blockchainWallet == null) {
						blockchainWallet = new MyRemoteWallet(payload, password);

						System.out.println("Set Wallet " + blockchainWallet);

					} else {						
						blockchainWallet.setTemporyPassword(password);

						blockchainWallet.setPayload(payload);
					}

					decryptionErrors = 0;

					if (callback != null)  {
						handler.post(new Runnable() {
							public void run() {
								callbackFinal.onSuccess();
							};
						});
						callback = null;
					}

					EventListeners.invokeWalletDidChange();

				} catch (Exception e) {
					e.printStackTrace();

					decryptionErrors++;

					System.out.println("checkIfWalletHasUpdatedAndFetchTransactions() Set blockchainWallet null");

					blockchainWallet = null;

					if (callback != null)  {
						handler.post(new Runnable() {
							public void run() {
								callbackFinal.onFail();
							};
						});
						callback = null;
					}

					EventListeners.invokeWalletDidChange();

					writeException(e);

					handler.post(new Runnable() {
						public void run() {
							Toast.makeText(WalletApplication.this,
									R.string.toast_wallet_decryption_failed,
									Toast.LENGTH_LONG).show();
						}
					});

					return;
				}

				if (decryptionErrors > 0)
					return;

				// Write the wallet to the cache file
				try {
					FileOutputStream file = openFileOutput(
							Constants.WALLET_FILENAME, Constants.WALLET_MODE);
					file.write(payload.getBytes("UTF-8"));
					file.close();
				} catch (Exception e) {
					e.printStackTrace();
				}

				try {
					// Copy our labels into the address book
					if (blockchainWallet.getLabelMap() != null) {
						for (Entry<String, String> labelObj : blockchainWallet
								.getLabelMap().entrySet()) {
							AddressBookProvider.setLabel(getContentResolver(),
									labelObj.getKey(), labelObj.getValue());
						}
					}
				} catch (Exception e) {
					e.printStackTrace();

					writeException(e);
				}

				try {
					// Get the balance and transaction
					doMultiAddr();
					
				} catch (Exception e) {
					e.printStackTrace();

					writeException(e);

					handler.post(new Runnable() {
						public void run() {
							Toast.makeText(WalletApplication.this,
									R.string.toast_error_syncing_wallet,
									Toast.LENGTH_LONG).show();
						}
					});
				}
			}
		}).start();
	}

	public void doMultiAddr() {
		if (blockchainWallet == null)
			return;

		new Thread(new Runnable() {
			public void run() {
				try {
					writeMultiAddrCache(blockchainWallet.doMultiAddr());

					//After multi addr the currency is set
					if (blockchainWallet.currencyCode != null)
						setCurrency(blockchainWallet.currencyCode);
					
					handler.post(new Runnable() {
						public void run() {
							notifyWidgets();
						}
					});
				} catch (Exception e) {
					e.printStackTrace();

					writeException(e);

					handler.post(new Runnable() {
						public void run() {
							Toast.makeText(
									WalletApplication.this,
									R.string.toast_error_downloading_transactions,
									Toast.LENGTH_LONG).show();
						}
					});
				}
			}
		}).start();
	}

	public static interface AddAddressCallback {
		public void onSavedAddress(String address);

		public void onError();
	}

	public void addKeyToWallet(ECKey key, String label, int tag,
			final AddAddressCallback callback) {

		if (blockchainWallet == null)
			return;

		try {
			blockchainWallet.addKey(key, label);

			final String address = new BitcoinAddress(new Hash(
					key.getPubKeyHash()), (short) 0).toString();

			if (tag != 0) {
				blockchainWallet.setTag(address, tag);
			}

			new Thread() {
				@Override
				public void run() {
					try {
						blockchainWallet.remoteSave();

						handler.post(new Runnable() {
							public void run() {
								callback.onSavedAddress(address);

								notifyWidgets();
							}
						});

					} catch (Exception e) {
						e.printStackTrace();

						writeException(e);

						handler.post(new Runnable() {
							public void run() {
								callback.onError();

								Toast.makeText(WalletApplication.this,
										R.string.toast_error_syncing_wallet,
										Toast.LENGTH_LONG).show();
							}
						});
					}
				}
			}.start();

		} catch (Exception e) {
			e.printStackTrace();

			writeException(e);

			callback.onError();
		}

		localSaveWallet();
	}

	public void setAddressLabel(String address, String label) {
		if (blockchainWallet == null)
			return;

		try {
			blockchainWallet.addLabel(address, label);

			new Thread() {
				@Override
				public void run() {
					try {
						blockchainWallet.remoteSave();
					} catch (Exception e) {
						e.printStackTrace();

						writeException(e);

						handler.post(new Runnable() {
							public void run() {
								Toast.makeText(WalletApplication.this,
										R.string.toast_error_syncing_wallet,
										Toast.LENGTH_LONG).show();
							}
						});
					}
				}
			}.start();
		} catch (Exception e) {
			e.printStackTrace();

			Toast.makeText(WalletApplication.this,
					R.string.error_setting_label, Toast.LENGTH_LONG).show();
		}
	}

	public boolean setCurrency(String currency) { 
		return PreferenceManager.getDefaultSharedPreferences(this).edit().putString(Constants.PREFS_KEY_EXCHANGE_CURRENCY, currency).commit();
	}
	
	public boolean setShouldDisplayLocalCurrency(boolean value) { 
		return PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean("should_display_local_currency", value).commit();
	}

	public boolean getShouldDisplayLocalCurrency() { 
		return PreferenceManager.getDefaultSharedPreferences(this).getBoolean("should_display_local_currency", false);
	}
	
	public boolean readLocalMultiAddr() {
		if (blockchainWallet == null)
			return false;

		try {
			// Restore the multi address cache
			FileInputStream multiaddrCacheFile = openFileInput(blockchainWallet
					.getGUID() + Constants.MULTIADDR_FILENAME);

			String multiAddr = IOUtils.toString(multiaddrCacheFile);

			blockchainWallet.parseMultiAddr(multiAddr, false);

			if (blockchainWallet.currencyCode != null)
				setCurrency(blockchainWallet.currencyCode);

			return true;

		} catch (Exception e) {
			writeException(e);

			e.printStackTrace();

			return false;
		}
	}

	public boolean readLocalWallet(String password) {
		try {
			// Read the wallet from local file
			FileInputStream file = openFileInput(Constants.WALLET_FILENAME);

			String payload = null;

			payload = IOUtils.toString(file, "UTF-8");

			MyRemoteWallet wallet = new MyRemoteWallet(payload, password);

			if (wallet.getGUID().equals(getGUID())) {
				this.blockchainWallet = wallet;

				this.decryptionErrors = 0;

				EventListeners.invokeWalletDidChange();

				return true;
			} else {
				return false;
			}
		} catch (Exception e) {
			writeException(e);

			e.printStackTrace();
		}

		return false;
	}

	public void localSaveWallet() {
		if (blockchainWallet == null)
			return;

		try {
			if (blockchainWallet.isNew())
				return;

			FileOutputStream file = openFileOutput(
					Constants.LOCAL_WALLET_FILENAME, Constants.WALLET_MODE);

			file.write(blockchainWallet.getPayload().getBytes());

			file.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public Address determineSelectedAddress() {
		if (blockchainWallet == null)
			return null;

		final String[] addresses = blockchainWallet.getActiveAddresses();

		if (addresses.length == 0)
			return null;

		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		final String defaultAddress = addresses[0];
		final String selectedAddress = prefs.getString(Constants.PREFS_KEY_SELECTED_ADDRESS, defaultAddress);

		// sanity check
		for (final String address : addresses) {
			if (address.equals(selectedAddress)) {
				try {
					return new Address(Constants.NETWORK_PARAMETERS, address);
				} catch (WrongNetworkException e) {
					e.printStackTrace();
				} catch (AddressFormatException e) {
					e.printStackTrace();
				}
			}
		}

		return null;
	}

	public final int applicationVersionCode() {
		try {
			return getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
		} catch (NameNotFoundException x) {
			return 0;
		}
	}

	public final String applicationVersionName() {
		try {
			return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
		} catch (NameNotFoundException x) {
			return "unknown";
		}
	}
}
