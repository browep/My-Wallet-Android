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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import piuk.MyRemoteWallet;
import piuk.blockchain.android.R;
import piuk.blockchain.android.WalletApplication;

/**
 * @author Andreas Schildbach
 */
public final class PasswordFragment extends DialogFragment {
	private static final String FRAGMENT_TAG = PasswordFragment.class
			.getName();
	private SuccessCallback callback = null;
	private static List<WeakReference<PasswordFragment>> fragmentRefs = new ArrayList<WeakReference<PasswordFragment>>();

	public static final int PasswordTypeMain = 1;
	public static final int PasswordTypeSecond = 2;

	private int passwordType;

	public static void hide() {
		for (WeakReference<PasswordFragment> fragmentRef : fragmentRefs) {
			if (fragmentRef != null && fragmentRef.get() != null) {
				try {
					fragmentRef.get().dismissAllowingStateLoss();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
	public static DialogFragment show(final FragmentManager fm,
			SuccessCallback callback, int passwordType) {

		final DialogFragment prev = (DialogFragment) fm
				.findFragmentById(R.layout.password_dialog);

		final FragmentTransaction ft = fm.beginTransaction();

		if (prev != null) {
			prev.dismiss();
			ft.remove(prev);
		}

		ft.addToBackStack(null);

		final PasswordFragment newFragment = instance();

		if (passwordType == PasswordTypeMain) 
			newFragment.setCancelable(false);

		newFragment.show(ft, FRAGMENT_TAG);

		newFragment.passwordType = passwordType;

		newFragment.callback = callback;

		return newFragment;
	}

	private static PasswordFragment instance() {
		final PasswordFragment fragment = new PasswordFragment();

		fragmentRefs.add(new WeakReference<PasswordFragment>(fragment));

		return fragment;
	}

	@Override
	public void onCancel(DialogInterface dialog) {
		callback.onFail();
	}

	@Override
	public Dialog onCreateDialog(final Bundle savedInstanceState) {
		final FragmentActivity activity = getActivity();
		final LayoutInflater inflater = LayoutInflater.from(activity);

		final Builder dialog = new AlertDialog.Builder(activity);

		if (passwordType == PasswordTypeSecond) 
			dialog.setTitle(R.string.second_password_title);
		else
			dialog.setTitle(R.string.main_password_title);

		final View view = inflater.inflate(R.layout.password_dialog, null);

		dialog.setView(view);

		final TextView passwordField = (TextView) view
				.findViewById(R.id.password_field);

		passwordField.setTextColor(Color.BLACK);

		final TextView titleTextView = (TextView) view
				.findViewById(R.id.title_text_view);

		if (passwordType == PasswordTypeSecond)
			titleTextView.setText(R.string.second_password_text);
		else
			titleTextView.setText(R.string.main_password_text);

		final Button continueButton = (Button) view
				.findViewById(R.id.password_continue);

		final WalletApplication application = (WalletApplication) getActivity()
				.getApplication();

		continueButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {

				try {
					if (passwordField.getText() == null)
						return;

					MyRemoteWallet wallet = application.getRemoteWallet();

					if (passwordType == PasswordTypeSecond) {
						String secondPassword = passwordField.getText().toString();

						if (wallet == null) {
							dismiss();

							callback.onFail();
						} if (wallet.validateSecondPassword(secondPassword)) {
							wallet.setTemporySecondPassword(secondPassword);

							Toast.makeText(getActivity().getApplication(),
									R.string.second_password_correct,
									Toast.LENGTH_SHORT).show();

							dismiss();

							callback.onSuccess();
						} else {
							Toast.makeText(getActivity().getApplication(),
									R.string.second_password_incorrect,
									Toast.LENGTH_SHORT).show();
						}
					} else {
						String password = passwordField.getText().toString();

						application.checkIfWalletHasUpdatedAndFetchTransactions(password, new SuccessCallback() {
							@Override
							public void onSuccess() {
								dismiss();

								callback.onSuccess();
							}

							@Override
							public void onFail() {
								dismiss();

								callback.onFail();
							}
						});

					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});

		return dialog.create();
	}
}
