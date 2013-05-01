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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.AddressFormatException;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionOutput;
import com.google.bitcoin.core.Utils;

import piuk.MyRemoteWallet;
import piuk.MyRemoteWallet.SendProgress;
import piuk.blockchain.android.R;
import piuk.blockchain.android.Constants;
import piuk.blockchain.android.WalletApplication;
import piuk.blockchain.android.WalletApplication.AddAddressCallback;
import piuk.blockchain.android.ui.CurrencyAmountView.Listener;
import piuk.blockchain.android.ui.dialogs.RequestPasswordDialog;

/**
 * @author Andreas Schildbach
 */
public final class SendCoinsFragment extends Fragment
{
	private WalletApplication application;
	private final Handler handler = new Handler();
	private Runnable sentRunnable;

	private AutoCompleteTextView receivingAddressView;
	private View receivingAddressErrorView;
	private CurrencyAmountView amountView;
	private Button viewGo;
	private Button viewCancel;

	
	public static enum FeePolicy {
		FeeOnlyIfNeeded,
		FeeForce,
		FeeNever
	}

	private State state = State.INPUT;

	private enum State
	{
		INPUT, SENDING, SENT
	}

	private final TextWatcher textWatcher = new TextWatcher()
	{
		public void afterTextChanged(final Editable s) {
			updateView();
		}

		public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after)
		{
		}

		public void onTextChanged(final CharSequence s, final int start, final int before, final int count)
		{
		}
	};

	private final Listener listener = new Listener()
	{
		public void changed()
		{
			updateView();
		}

		public void done()
		{
			viewGo.requestFocusFromTouch();
		}
	};

	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		final Activity activity = getActivity();

		application = (WalletApplication) activity.getApplication();
	}

	public abstract class RightDrawableOnTouchListener implements OnTouchListener {
	    Drawable drawable;
	    private int fuzz = 40;

	    /**
	     * @param keyword
	     */
	    public RightDrawableOnTouchListener(TextView view) {
	        super();
	        final Drawable[] drawables = view.getCompoundDrawables();
	        if (drawables != null && drawables.length == 4)
	            this.drawable = drawables[2];
	    }

	    /*
	     * (non-Javadoc)
	     * 
	     * @see android.view.View.OnTouchListener#onTouch(android.view.View, android.view.MotionEvent)
	     */
	    @Override
	    public boolean onTouch(final View v, final MotionEvent event) {
	    		    	 
	        if (event.getAction() == MotionEvent.ACTION_DOWN && drawable != null) {
		    	System.out.println("event " + event);

	        	final int x = (int) event.getX();
	            final int y = (int) event.getY();
	            
		    	System.out.println("x " + x);
		    	System.out.println("y " + y);

	            final Rect bounds = drawable.getBounds();
		    	System.out.println("bounds " + bounds);

	            if (x >= (v.getRight() - bounds.width() - fuzz) && x <= (v.getRight() - v.getPaddingRight() + fuzz)
	                    && y >= (v.getPaddingTop() - fuzz) && y <= (v.getHeight() - v.getPaddingBottom()) + fuzz) {
	                return onDrawableTouch(event);
	            }
	        }
	        return false;
	    }

	    public abstract boolean onDrawableTouch(final MotionEvent event);

	}
	
	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState)
	{

		final SendCoinsActivity activity = (SendCoinsActivity) getActivity();

		final View view = inflater.inflate(R.layout.send_coins_fragment, container);

		if (application.getRemoteWallet() == null)
			return view;

		final BigInteger available = application.getRemoteWallet().getBalance();

		receivingAddressView = (AutoCompleteTextView) view.findViewById(R.id.send_coins_receiving_address);

		AutoCompleteAdapter adapter = new AutoCompleteAdapter(this.getLabelList());

		receivingAddressView.setAdapter(adapter);
		receivingAddressView.addTextChangedListener(textWatcher);

		receivingAddressView.setOnTouchListener(new RightDrawableOnTouchListener(receivingAddressView) {
	        @Override
	        public boolean onDrawableTouch(final MotionEvent event) {
				activity.showQRReader("uri_qr_code");
				
				return true;
	        }
	    });

		receivingAddressErrorView = view.findViewById(R.id.send_coins_receiving_address_error);

		final CurrencyAmountView availableView = (CurrencyAmountView) view.findViewById(R.id.send_coins_available);
		availableView.setAmount(available);

		amountView = (CurrencyAmountView) view.findViewById(R.id.send_coins_amount);
		amountView.setListener(listener);
		amountView.setContextButton(R.drawable.ic_input_calculator, new OnClickListener()
		{
			public void onClick(final View v)
			{
				final FragmentTransaction ft = getFragmentManager().beginTransaction();
				final Fragment prev = getFragmentManager().findFragmentByTag(AmountCalculatorFragment.FRAGMENT_TAG);
				if (prev != null)
					ft.remove(prev);
				ft.addToBackStack(null);
				final DialogFragment newFragment = new AmountCalculatorFragment(new AmountCalculatorFragment.Listener()
				{
					public void use(final BigInteger amount)
					{
						amountView.setAmount(amount);
					}
				});
				newFragment.show(ft, AmountCalculatorFragment.FRAGMENT_TAG);
			}
		});

		viewGo = (Button) view.findViewById(R.id.send_coins_go);
		viewGo.setOnClickListener(new OnClickListener()
		{
			final SendProgress progress = new SendProgress() {
				public void onSend(final Transaction tx, final String message) {
					handler.post(new Runnable() {
						public void run() {
							state = State.SENT;

							activity.longToast(message);

							Intent intent = activity.getIntent();
							intent.putExtra("tx", tx.getHash());
							activity.setResult(Activity.RESULT_OK, intent);

							activity.finish();

							updateView();
						}
					});

					try {
						Thread.sleep(2000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}

					application.doMultiAddr();
				}

				public void onError(final String message) {
					handler.post(new Runnable() {
						public void run() {

							System.out.println("On Error");

							if (message != null)
								activity.longToast(message);

							state = State.INPUT;

							updateView();
						}
					});
				}

				public void onProgress(final String message) {
					handler.post(new Runnable() {
						public void run() {
							state = State.SENDING;

							updateView();
						}
					});
				}

				public boolean onReady(Transaction tx, BigInteger fee, FeePolicy feePolicy, long priority) {

					boolean containsOutputLessThanThreshold = false;
					for (TransactionOutput output : tx.getOutputs()) {
						if (output.getValue().compareTo(Constants.FEE_THRESHOLD_MIN) < 0) {
							containsOutputLessThanThreshold = true;
							break;
						}
					}

					if (fee.compareTo(BigInteger.ZERO) == 0  && feePolicy != FeePolicy.FeeNever && (priority < 57600000L || tx.bitcoinSerialize().length > 1024 || containsOutputLessThanThreshold)) {

						handler.post(new Runnable() {
							public void run() {
								AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
								builder.setMessage(R.string.ask_for_fee)
								.setCancelable(false)
								.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog, int id) {
										makeTransaction(FeePolicy.FeeForce);
									}
								})
								.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog, int id) {
										makeTransaction(FeePolicy.FeeNever);
									}
								});

								AlertDialog alert = builder.create();

								alert.show();
							}
						});

						handler.post(new Runnable() {
							public void run() {
								state = State.INPUT;
								updateView();
							}
						});

						return false;
					}

					return true;
				}


				public ECKey onPrivateKeyMissing(final String address) {

					handler.post(new Runnable() {
						public void run() {
							AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
							builder.setMessage(getString(R.string.ask_for_private_key, address))
							.setCancelable(false)
							.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog, int id) {
									activity.scanPrivateKeyAddress = address;

									activity.showQRReader("private_key_qr");
								}
							})
							.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog, int id) {

									synchronized (activity.temporaryPrivateKeys) {
										activity.temporaryPrivateKeys.notify();
									}

									dialog.cancel();
								}
							});

							AlertDialog alert = builder.create();

							alert.show();
						}
					});

					try {
						synchronized (activity.temporaryPrivateKeys) {
							activity.temporaryPrivateKeys.wait();
						}
					} catch (InterruptedException e) {
						e.printStackTrace();
					}

					return activity.temporaryPrivateKeys.get(address);
				}
			};

			public void send(Address receivingAddress, BigInteger fee, FeePolicy feePolicy) {

				if (application.getRemoteWallet() == null)
					return;

				final BigInteger amount = amountView.getAmount();
				final WalletApplication application = (WalletApplication) getActivity().getApplication();

				application.getRemoteWallet().sendCoinsAsync(receivingAddress.toString(), amount, feePolicy, fee, progress);
			}

			public void makeTransaction(final FeePolicy feePolicy) {

				if (application.getRemoteWallet() == null)
					return;

				try {
					MyRemoteWallet wallet = application.getRemoteWallet();

					BigInteger baseFee = wallet.getBaseFee();

					final BigInteger fee = (feePolicy == FeePolicy.FeeForce || wallet.getFeePolicy() == 1) ? baseFee : BigInteger.ZERO;

					String addressString = getToAddress();

					try {
						Address receivingAddress = new Address(Constants.NETWORK_PARAMETERS, addressString);

						send(receivingAddress, fee, feePolicy);
					} catch (AddressFormatException e) {

						if (isValidEmail(addressString)) {

							//Generate a new Archived Address
							application.addKeyToWallet(new ECKey(), "Sent To " + addressString, 2, new AddAddressCallback() {
								public void onSavedAddress(String address) {
									try {
										send(new Address(Constants.NETWORK_PARAMETERS, address), fee, feePolicy);
									} catch (AddressFormatException e) {
										e.printStackTrace();
									}
								}
								public void onError() {
									System.out.println("Generate Address Failed");
								}
							});
						}
					}

				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			public void onClick(final View v)
			{
				if (application.getRemoteWallet() == null)
					return;

				MyRemoteWallet remoteWallet = application.getRemoteWallet();

				if (remoteWallet.isDoubleEncrypted() == false) {
					makeTransaction(FeePolicy.FeeOnlyIfNeeded);
				} else {
					if (remoteWallet.temporySecondPassword == null) {
						RequestPasswordDialog.show(getFragmentManager(), new SuccessCallback() {

							public void onSuccess() {
								makeTransaction(FeePolicy.FeeOnlyIfNeeded);
							}

							public void onFail() {
								Toast.makeText(application, R.string.send_no_password_error, Toast.LENGTH_LONG).show();
							}
						}, RequestPasswordDialog.PasswordTypeSecond);
					} else {
						makeTransaction(FeePolicy.FeeOnlyIfNeeded);
					}
				}
			}
		});

		viewCancel = (Button) view.findViewById(R.id.send_coins_cancel);
		viewCancel.setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				activity.setResult(Activity.RESULT_CANCELED);

				activity.finish();
			}
		});

		updateView();

		return view;
	}

	protected void onServiceBound()
	{
		System.out.println("service bound");
	}

	protected void onServiceUnbound()
	{
		System.out.println("service unbound");
	}

	@Override
	public void onResume() {

		super.onResume();
	}

	@Override
	public void onDestroyView()
	{
		super.onDestroyView();

		handler.removeCallbacks(sentRunnable);
	}


	@Override
	public void onDestroy()
	{
		super.onDestroy();

		if (application.getRemoteWallet() == null)
			return;

		//Clear the second password
		MyRemoteWallet remoteWallet = application.getRemoteWallet();

		remoteWallet.setTemporySecondPassword(null);
	}

	public class AutoCompleteAdapter extends ArrayAdapter<Pair<String, String>>
	{
		public AutoCompleteAdapter(List<Pair<String, String>> data)
		{
			super(getActivity(), R.layout.simple_dropdown_item_2line, data);
		}

		@Override
		public View getView(final int position, View row, final ViewGroup parent) {

			if (row == null) {
				row = getLayoutInflater(null).inflate(R.layout.simple_dropdown_item_2line, parent, false);
			}

			final Pair<String, String> pair = getItem(position);

			((TextView) row.findViewById(android.R.id.text1)).setText(pair.first);

			((TextView) row.findViewById(android.R.id.text2)).setText(pair.second);

			return row;
		}
	}

	public List<Pair<String, String>> getLabelList() {
		List<Pair<String, String>> array = new ArrayList<Pair<String, String>>();

		if (application.getRemoteWallet() == null) {
			return array;
		}

		Map<String, String> labelMap = application.getRemoteWallet().getLabelMap();

		synchronized(labelMap) {
			for (Map.Entry<String, String> entry : labelMap.entrySet()) {
				array.add(new Pair<String, String>(entry.getValue(), entry.getKey()) {
					public String toString() {
						return first.toString();
					}
				});
			}
		}

		return array;
	}


	private boolean isValidEmail(String email) {
		return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
	}


	public String getToAddress() {
		final String userEntered = receivingAddressView.getText().toString().trim();
		if (userEntered.length() > 0) {
			try {
				new Address(Constants.NETWORK_PARAMETERS, userEntered);
				
				return userEntered;
			} catch (AddressFormatException e) {
				List<Pair<String, String>> labels = this.getLabelList();
				
				for (Pair<String, String> label : labels) {
					if (label.first.toLowerCase(Locale.ENGLISH).equals(userEntered.toLowerCase(Locale.ENGLISH))) {
						try {
							new Address(Constants.NETWORK_PARAMETERS, label.second);
							
							return label.second;
						} catch (AddressFormatException e1) {}
					}
				}
			}
		}
		
		return null;
	}

	private void updateView()
	{
		
		String address = getToAddress();
		
		if (receivingAddressView.getText().toString().trim().length() == 0 || address != null) {
			receivingAddressErrorView.setVisibility(View.GONE);	
		} else {
			receivingAddressErrorView.setVisibility(View.VISIBLE);
		}

		final BigInteger amount = amountView.getAmount();
		final boolean validAmount = amount != null && amount.signum() > 0;

		receivingAddressView.setEnabled(state == State.INPUT);

		amountView.setEnabled(state == State.INPUT);

		viewGo.setEnabled(state == State.INPUT && address != null && validAmount);
		if (state == State.INPUT)
			viewGo.setText(R.string.send_coins_fragment_button_send);
		else if (state == State.SENDING)
			viewGo.setText(R.string.send_coins_sending_msg);
		else if (state == State.SENT)
			viewGo.setText(R.string.send_coins_sent_msg);

		viewCancel.setEnabled(state != State.SENDING);
		viewCancel.setText(state != State.SENT ? R.string.button_cancel : R.string.send_coins_fragment_button_back);
	}

	public void update(final String receivingAddress, final BigInteger amount)
	{
		if (receivingAddressView == null)
			return;
		
		receivingAddressView.setText(receivingAddress);
		
		flashReceivingAddress();

		if (amount != null)
			amountView.setAmount(amount);

		if (receivingAddress != null && amount == null)
			amountView.requestFocus();

		updateView();
	}

	private Runnable resetColorRunnable = new Runnable()
	{
		public void run()
		{
			receivingAddressView.setTextColor(Color.parseColor("#888888"));
		}
	};

	public void flashReceivingAddress()
	{
		receivingAddressView.setTextColor(Color.parseColor("#cc5500"));
		handler.removeCallbacks(resetColorRunnable);
		handler.postDelayed(resetColorRunnable, 500);
	}
}
