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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.commons.io.IOUtils;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager.LayoutParams;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import piuk.EventListeners;
import piuk.MyRemoteWallet;
import piuk.blockchain.android.R;
import piuk.blockchain.android.WalletApplication;

/**
 * @author Andreas Schildbach
 */
public final class NewAccountFragment extends DialogFragment {
	private static final String FRAGMENT_TAG = NewAccountFragment.class.getName();
	private WalletApplication application;

	public static Bitmap loadBitmap(String url) throws MalformedURLException,
	IOException {
		Bitmap bitmap = null;

		final byte[] data = IOUtils.toByteArray(new URL(url).openStream());
		BitmapFactory.Options options = new BitmapFactory.Options();

		bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);

		return bitmap;
	}

	public void refreshCaptcha(View view) {
		final ImageView captchaImage = (ImageView) view
				.findViewById(R.id.captcha_image);

		new Thread(new Runnable() {
			public void run() {
				try {
					final Bitmap b = loadBitmap("https://blockchain.info/kaptcha.jpg");
					captchaImage.post(new Runnable() {
						public void run() {
							captchaImage.setImageBitmap(b);
						}
					});
				} catch (Exception e) {

					captchaImage.post(new Runnable() {
						public void run() {
							Toast.makeText(getActivity().getApplication(),
									R.string.toast_error_downloading_captcha,
									Toast.LENGTH_LONG).show();
						}
					});

					e.printStackTrace();
				}
			}
		}).start();
	}

	public static NewAccountFragment show(final FragmentManager fm, WalletApplication application) {
		final DialogFragment prev = (DialogFragment) fm
				.findFragmentById(R.layout.new_account_dialog);

		final FragmentTransaction ft = fm.beginTransaction();

		if (prev != null) {
			prev.dismiss();
			ft.remove(prev);
		}

		ft.addToBackStack(null);

		final NewAccountFragment newFragment = instance();

		newFragment.application = application;
		
		newFragment.show(ft, FRAGMENT_TAG);

		return newFragment;
	}
	
	@Override
	public void onCancel(DialogInterface dialog) {		
		WelcomeFragment.show(getFragmentManager(), application);
	}
	

	static NewAccountFragment instance() {
		final NewAccountFragment fragment = new NewAccountFragment();

		return fragment;
	}


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    	
   
    	final View view = inflater.inflate(R.layout.new_account_dialog, null);		

		final Button createButton = (Button) view.findViewById(R.id.create_button);
		final TextView password = (TextView) view.findViewById(R.id.password);
		final TextView password2 = (TextView) view.findViewById(R.id.password2);
		final TextView captcha = (TextView) view.findViewById(R.id.captcha);

		refreshCaptcha(view);

		createButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				final WalletApplication application = (WalletApplication) getActivity()
						.getApplication();

				if (password.getText().length() < 10
						|| password.getText().length() > 255) {
					Toast.makeText(application,
							R.string.new_account_password_length_error,
							Toast.LENGTH_LONG).show();
					return;
				}

				if (!password.getText().toString()
						.equals(password2.getText().toString())) {
					Toast.makeText(application,
							R.string.new_account_password_mismatch_error,
							Toast.LENGTH_LONG).show();
					return;
				}

				if (captcha.getText().length() == 0) {
					Toast.makeText(application,
							R.string.new_account_no_kaptcha_error,
							Toast.LENGTH_LONG).show();
					return;
				}

				final ProgressDialog progressDialog = ProgressDialog.show(
						getActivity(), "",
						getString(R.string.creating_account), true);

				progressDialog.show();

				final Handler handler = new Handler();

				new Thread() {
					@Override
					public void run() {
						try {

							if (!application.getRemoteWallet().isNew())
								return;

							application.getRemoteWallet().setTemporyPassword(password.getText().toString());

							if (!application.getRemoteWallet().remoteSave(captcha.getText().toString())) {
								throw new Exception("Unknown Error inserting wallet");
							}

							EventListeners.invokeWalletDidChange();
							
							application.hasDecryptionError = false;

							handler.post(new Runnable() {
								public void run() {
									progressDialog.dismiss();

									dismiss();

									Toast.makeText(
											getActivity().getApplication(),
											R.string.new_account_success,
											Toast.LENGTH_LONG).show();

									Editor edit = PreferenceManager
											.getDefaultSharedPreferences(
													application
													.getApplicationContext())
													.edit();

									edit.putString("guid", application
											.getRemoteWallet().getGUID());
									edit.putString("sharedKey", application
											.getRemoteWallet().getSharedKey());
									edit.putString("password", password
											.getText().toString());

									if (edit.commit()) {
										application.checkIfWalletHasUpdatedAndFetchTransactions();
									} else {
										Activity activity = (Activity) getActivity();

										final Builder dialog = new AlertDialog.Builder(
												activity);

										dialog.setTitle(R.string.error_pairing_wallet);
										dialog.setMessage("Error saving preferences");
										dialog.setNeutralButton(
												R.string.button_dismiss, null);
										dialog.show();
									}
								}
							});
						} catch (final Exception e) {
							e.printStackTrace();

							handler.post(new Runnable() {
								public void run() {
									progressDialog.dismiss();

									refreshCaptcha(view);

									captcha.setText(null);

									Toast.makeText(
											getActivity().getApplication(),
											e.getLocalizedMessage(),
											Toast.LENGTH_LONG).show();
								}
							});
						}
					}
				}.start();
			}
		});

		return view;
    }
    /*
	@Override
	public Dialog onCreateDialog(final Bundle savedInstanceState) {
		super.onCreateDialog(savedInstanceState);
		
		System.out.println("On Create Dialog");
		
		final FragmentActivity activity = getActivity();

		final Builder dialog = new AlertDialog.Builder(activity)
		.setTitle(R.string.new_account_title);
		
		Dialog _dialog = dialog.create();		

		_dialog.setOnCancelListener(new OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
				System.out.print("On Cancel");
				
				WelcomeFragment.show(getFragmentManager(), (WalletApplication) activity.getApplication());
			}
		});
		
		_dialog.setOnDismissListener(new OnDismissListener() {
			@Override
			public void onDismiss(DialogInterface dialog) {
				System.out.print("On Dismiss");
				
				WelcomeFragment.show(getFragmentManager(), (WalletApplication) activity.getApplication());
			}
		});
		
		return _dialog;
	}*/
}
