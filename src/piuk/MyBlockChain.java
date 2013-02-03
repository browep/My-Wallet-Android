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

package piuk;

import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.json.simple.JSONValue;
import org.spongycastle.util.encoders.Hex;

import piuk.blockchain.android.Constants;
import piuk.blockchain.android.WalletApplication;

import com.google.bitcoin.core.Block;
import com.google.bitcoin.core.BlockChain;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.PeerEventListener;
import com.google.bitcoin.core.ProtocolException;
import com.google.bitcoin.core.Sha256Hash;
import com.google.bitcoin.core.StoredBlock;
import com.google.bitcoin.core.TransactionInput;
import com.google.bitcoin.core.TransactionOutput;
import com.google.bitcoin.core.WalletTransaction.Pool;
import com.google.bitcoin.store.BlockStoreException;
import com.google.bitcoin.store.MemoryBlockStore;

import de.roderick.weberknecht.WebSocketConnection;
import de.roderick.weberknecht.WebSocketEventHandler;
import de.roderick.weberknecht.WebSocketException;
import de.roderick.weberknecht.WebSocketMessage;

public class MyBlockChain  implements WebSocketEventHandler {
	final String URL = "ws://api.blockchain.info:8335/inv";
	int nfailures = 0;
	WebSocketConnection _websocket;
	WalletApplication application;
	StoredBlock latestBlock;
	boolean isConnected = false;
	boolean isRunning = true;

	public MyRemoteWallet getRemoteWallet() {
		return application.getRemoteWallet();
	}

	public static class MyBlock extends Block {
		private static final long serialVersionUID = 1L;

		long time;
		Sha256Hash hash;
		int blockIndex;

		public MyBlock(NetworkParameters params) throws ProtocolException {
			super(params);
		}

		@Override
		public long getTimeSeconds() {
			return time;
		}

		@Override
		public Sha256Hash getHash() {
			return hash;
		}
	}

	private final Set<PeerEventListener> listeners = new CopyOnWriteArraySet<PeerEventListener>();

	public int getBestChainHeight() {
		return getChainHead().getHeight();
	}

	public StoredBlock getChainHead() {
		if (latestBlock != null) {
			return latestBlock;
		} else {
			return getRemoteWallet()._multiAddrBlock;
		}
	}

	final private EventListeners.EventListener walletEventListener = new EventListeners.EventListener() {
		@Override
		public void onWalletDidChange() {
			try {
				if (getRemoteWallet()._multiAddrBlock != null) {

					if (latestBlock == null
							|| latestBlock.getHeight() < getRemoteWallet()._multiAddrBlock
									.getHeight()) {
						latestBlock = getRemoteWallet()._multiAddrBlock;

						for (PeerEventListener listener : listeners) {
							listener.onBlocksDownloaded(null,
									latestBlock.getHeader(), 0);
						}
					}
				}

				if (isRunning) {
					start();
				} else if (isConnected()) {
					// Disconnect and reconnect
					// To resubscribe
					subscribe();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	};

	public MyBlockChain(NetworkParameters params, WalletApplication application) throws BlockStoreException, WebSocketException, URISyntaxException {

		this._websocket = new WebSocketConnection(new URI(URL));

		this._websocket.setEventHandler(this);

		this.application = application;
	}

	public synchronized void subscribe() {
		try {
			String message = "{\"op\":\"blocks_sub\"}{\"op\":\"wallet_sub\",\"guid\":\""
					+ getRemoteWallet().getGUID() + "\"}";

			for (Map<String, Object> key : this.getRemoteWallet().getKeysMap()) {
				message += "{\"op\":\"addr_sub\", \"addr\":\""
						+ key.get("addr") + "\"}";
			}

			_websocket.send(message);

		} catch (WebSocketException e) {
			e.printStackTrace();
		}
	}

	public void removePeerEventListener(PeerEventListener listener) {
		listeners.remove(listener);
	}

	public void addPeerEventListener(PeerEventListener listener) {
		listeners.add(listener);
	}

	@Override
	public void onClose() {
		System.out.println("onClose()");

		this.isConnected = false;

		if (!isRunning)
			return;

		for (PeerEventListener listener : listeners) {
			listener.onPeerConnected(null, 0);
		}

		if (nfailures < 5) {
			try {
				++nfailures;

				_websocket.connect();
			} catch (WebSocketException e) {
				e.printStackTrace();
			}
		}
	}

	public boolean isConnected() {
		return this.isConnected;
	}

	public void stop() {
		System.out.println("start()");

		try {
			this.isRunning = false;

			_websocket.close();
		} catch (WebSocketException e) {
			e.printStackTrace();
		}

		EventListeners.removeEventListener(walletEventListener);
	}

	public void start() {
		this.isRunning = true;

		System.out.println("start()");

		try {
			_websocket.connect();
		} catch (WebSocketException e) {
			e.printStackTrace();
		}

		EventListeners.addEventListener(walletEventListener);
	}

	@Override
	@SuppressWarnings("unchecked")
	public void onMessage(WebSocketMessage wmessage) {

		System.out.println("OnMessage()");

		try {
			String message = wmessage.getText();

			System.out.println("Websocket() onMessage() " + message);

			Map<String, Object> top = (Map<String, Object>) JSONValue
					.parse(message);

			if (top == null)
				return;

			String op = (String) top.get("op");

			if (op.equals("block")) {
				Map<String, Object> x = (Map<String, Object>) top.get("x");

				if (x == null)
					return;

				Sha256Hash hash = new Sha256Hash(Hex.decode((String) x
						.get("hash")));
				int blockIndex = ((Number) x.get("blockIndex")).intValue();
				int blockHeight = ((Number) x.get("height")).intValue();
				long time = ((Number) x.get("time")).longValue();

				MyBlock block = new MyBlock(Constants.NETWORK_PARAMETERS);
				block.hash = hash;
				block.blockIndex = blockIndex;
				block.time = time;

				this.latestBlock = new StoredBlock(block, BigInteger.ZERO,
						blockHeight);

				List<MyTransaction> transactions = getRemoteWallet()
						.getMyTransactions();
				List<Number> txIndexes = (List<Number>) x.get("txIndexes");
				for (Number txIndex : txIndexes) {
					for (MyTransaction tx : transactions) {

						MyTransactionConfidence confidence = (MyTransactionConfidence) tx
								.getConfidence();

						if (tx.txIndex == txIndex.intValue()
								&& confidence.height != blockHeight) {
							confidence.height = blockHeight;
							confidence.runListeners();
						}
					}
				}

				for (PeerEventListener listener : listeners) {
					listener.onBlocksDownloaded(null, block, 0);
				}

			} else if (op.equals("utx")) {
				Map<String, Object> x = (Map<String, Object>) top.get("x");

				MyTransaction tx = MyTransaction.fromJSONDict(x);

				BigInteger result = BigInteger.ZERO;

				BigInteger previousBalance = getRemoteWallet().getBitcoinJWallet().final_balance;

				for (TransactionInput input : tx.getInputs()) {
					// if the input is from me subtract the value
					MyTransactionInput myinput = (MyTransactionInput) input;

					if (getRemoteWallet().isAddressMine(input.getFromAddress()
							.toString())) {
						result = result.subtract(myinput.value);

						getRemoteWallet().getBitcoinJWallet().final_balance = getRemoteWallet()
								.getBitcoinJWallet().final_balance
								.subtract(myinput.value);
						getRemoteWallet().getBitcoinJWallet().total_sent = getRemoteWallet()
								.getBitcoinJWallet().total_sent
								.add(myinput.value);
					}
				}

				for (TransactionOutput output : tx.getOutputs()) {
					// if the input is from me subtract the value
					MyTransactionOutput myoutput = (MyTransactionOutput) output;

					if (getRemoteWallet().isAddressMine(myoutput.getToAddress()
							.toString())) {
						result = result.add(myoutput.getValue());

						getRemoteWallet().getBitcoinJWallet().final_balance = getRemoteWallet()
								.getBitcoinJWallet().final_balance.add(myoutput
								.getValue());
						getRemoteWallet().getBitcoinJWallet().total_received = getRemoteWallet()
								.getBitcoinJWallet().total_sent.add(myoutput
								.getValue());
					}
				}

				tx.result = result;

				getRemoteWallet().getBitcoinJWallet().addWalletTransaction(
						Pool.SPENT, tx);

				if (result.compareTo(BigInteger.ZERO) >= 0) {
					System.out.println("On Received");

					EventListeners.invokeOnCoinsReceived(tx, previousBalance,
							getRemoteWallet().getBitcoinJWallet().final_balance);
				} else {
					EventListeners.invokeOnCoinsSent(tx, previousBalance,
							getRemoteWallet().getBitcoinJWallet().final_balance);
				}
			} else if (op.equals("on_change")) {
				String newChecksum = (String) top.get("checksum");
				String oldChecksum = getRemoteWallet().getChecksum();

				System.out.println("On change " + newChecksum + " "
						+ oldChecksum);

				if (!newChecksum.equals(oldChecksum)) {
					try {
						application.checkIfWalletHasUpdatedAndFetchTransactions();

					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onOpen() {
		System.out.println("onOpen()");

		this.isConnected = true;

		subscribe();

		for (PeerEventListener listener : listeners) {
			listener.onPeerConnected(null, 1);
		}

		nfailures = 0;
	}
}
