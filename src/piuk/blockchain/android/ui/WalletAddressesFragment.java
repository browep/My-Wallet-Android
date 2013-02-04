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

import piuk.EventListeners;
import piuk.blockchain.android.AddressBookProvider;
import piuk.blockchain.android.Constants;
import piuk.blockchain.android.R;
import piuk.blockchain.android.WalletApplication;
import piuk.blockchain.android.util.QrDialog;
import piuk.blockchain.android.util.WalletUtils;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.ListFragment;
import android.support.v4.view.ViewPager;
import android.text.ClipboardManager;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.google.bitcoin.uri.BitcoinURI;

/**
 * @author Andreas Schildbach
 */
public class WalletAddressesFragment extends ListFragment
{
	private WalletApplication application;
	private Activity activity;
	private String[] addresses;
	private int tag_filter = 0;

	private EventListeners.EventListener eventListener = new EventListeners.EventListener() {
	    @Override
		public void onWalletDidChange() {

			try {
				updateView();
			} catch (Exception e) {
				e.printStackTrace();
			} 
		}
	};
	
	private ViewPager pagerView;

	public WalletAddressesFragment() {}

	public WalletAddressesFragment(int tag_filter, ViewPager pagerView) {
		super();

		this.tag_filter = tag_filter;
		this.pagerView = pagerView;
	}

	public void setKeys() {
		
		if (tag_filter == 2)
			addresses = application.getRemoteWallet().getArchivedAddresses();
		else
			addresses = application.getRemoteWallet().getActiveAddresses();
	}

	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		activity = getActivity();
		application = (WalletApplication) activity.getApplication();
		setKeys();

		setListAdapter(new Adapter());
	}

	@Override
	public void onViewCreated(final View view, final Bundle savedInstanceState)
	{
		super.onViewCreated(view, savedInstanceState);

		registerForContextMenu(getListView());
	}

	public void onHide() {
		this.unregisterForContextMenu(getListView());
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	@Override
	public void onResume()
	{
		super.onResume();

		EventListeners.addEventListener(eventListener);

		activity.getContentResolver().registerContentObserver(AddressBookProvider.CONTENT_URI, true, contentObserver);

		updateView();
	}

	@Override
	public void onPause()
	{
		activity.getContentResolver().unregisterContentObserver(contentObserver);

		EventListeners.removeEventListener(eventListener);

		super.onPause();
	}

	@Override
	public void onListItemClick(final ListView l, final View v, final int position, final long id)
	{
		EditAddressBookEntryFragment.edit(getFragmentManager(), addresses[position].toString());
	}

	@Override
	public void onCreateContextMenu(final ContextMenu menu, final View v, final ContextMenuInfo info)
	{
		activity.getMenuInflater().inflate(R.menu.wallet_addresses_context, menu);
	}

	@Override
	public boolean onContextItemSelected(final MenuItem item)
	{
		if (pagerView.getCurrentItem() == 0 && tag_filter == 2)
			return false;
		else if (pagerView.getCurrentItem() == 1 && tag_filter == 0)
			return false;

		final AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) item.getMenuInfo();

		final String address = (String) getListView().getAdapter().getItem(menuInfo.position);
		
		switch (item.getItemId())
		{
			case R.id.wallet_addresses_context_edit:
			{
				EditAddressBookEntryFragment.edit(getFragmentManager(), address.toString());
				return true;
			}

			case R.id.wallet_addresses_context_show_qr:
			{
				final String uri = BitcoinURI.convertToBitcoinURI(address, null, null, null);
				final int size = (int) (256 * getResources().getDisplayMetrics().density);
				new QrDialog(activity, WalletUtils.getQRCodeBitmap(uri, size)).show();
				return true;
			}

			case R.id.wallet_addresses_context_copy_to_clipboard:
			{
				handleCopyToClipboard(address.toString());
				return true;
			}

			case R.id.wallet_addresses_context_default:
			{
				handleDefault(address);
				return true;
			}

			default:
				return false;
		}
	}

	private void handleDefault(final String address)
	{
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
		prefs.edit().putString(Constants.PREFS_KEY_SELECTED_ADDRESS, address.toString()).commit();
	}

	private void handleCopyToClipboard(final String address)
	{
		final ClipboardManager clipboardManager = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
		clipboardManager.setText(address);
		((AbstractWalletActivity) activity).toast(R.string.wallet_address_fragment_clipboard_msg);
	}


	private void updateView()
	{
		setKeys();

		final ListAdapter adapter = getListAdapter();

		if (adapter != null)
			((BaseAdapter) adapter).notifyDataSetChanged();
	}

	private class Adapter extends BaseAdapter
	{
		final Resources res = getResources();

		public int getCount() {
			return addresses.length;
		}

		public Object getItem(final int position) {
			return addresses[position];
		}

		public long getItemId(final int position) {
			return addresses[position].hashCode();
		}

		public View getView(final int position, View row, final ViewGroup parent) {
			final String address = (String) getItem(position);

			if (row == null)
				row = getLayoutInflater(null).inflate(R.layout.address_book_row, null);

			final TextView addressView = (TextView) row.findViewById(R.id.address_book_row_address);
		
			addressView.setText(address.toString());

			final TextView labelView = (TextView) row.findViewById(R.id.address_book_row_label);
			final String label = AddressBookProvider.resolveLabel(activity.getContentResolver(), address.toString());
			if (label != null) {
				labelView.setText(label);
				labelView.setTextColor(res.getColor(R.color.less_significant));
			}
			else {
				labelView.setText(R.string.wallet_addresses_fragment_unlabeled);
				labelView.setTextColor(res.getColor(R.color.insignificant));
			}

			return row;
		}
	}

	private final Handler handler = new Handler();

	private final ContentObserver contentObserver = new ContentObserver(handler)
	{
		@Override
		public void onChange(final boolean selfChange)
		{
			try {
				handler.post(new Runnable() {
					public void run() {
						updateView();
					}
				});
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	};
}
