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

import org.json.simple.JSONValue;
import org.spongycastle.util.encoders.Hex;

import piuk.blockchain.android.WalletApplication;

import com.google.bitcoin.core.Sha256Hash;
import com.google.bitcoin.core.TransactionInput;
import com.google.bitcoin.core.TransactionOutput;

import de.tavendo.autobahn.WebSocket.ConnectionHandler;
import de.tavendo.autobahn.WebSocketConnection;
import de.tavendo.autobahn.WebSocketException;

public class WebSocketHandler {
	final static String URL = "ws://blockchain.info/inv";
	int nfailures = 0;
	WalletApplication application;
	MyBlock latestBlock;
	boolean isConnected = false;
	boolean isRunning = true;

	private WebSocketConnection client = new WebSocketConnection();

	public MyRemoteWallet getRemoteWallet() {
		return application.getRemoteWallet();
	}

	public int getBestChainHeight() {
		return getChainHead().getHeight();
	}

	public MyBlock getChainHead() {
		if (latestBlock != null) {
			return latestBlock;
		} else {
			return getRemoteWallet().latestBlock;
		}
	}

	final private EventListeners.EventListener walletEventListener = new EventListeners.EventListener() {
		@Override
		public void onWalletDidChange() {
			try {

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

	public WebSocketHandler(WalletApplication application) {
		this.application = application;
	}

	public void send(String message) {
		try {
			client.sendTextMessage(message);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public synchronized void subscribe() {
		System.out.println("Websocket subscribe");

		send("{\"op\":\"blocks_sub\"}");

		send("{\"op\":\"wallet_sub\",\"guid\":\""+ getRemoteWallet().getGUID() + "\"}");

		for (Map<String, Object> key : this.getRemoteWallet().getKeysMap()) {
			send("{\"op\":\"addr_sub\", \"addr\":\""+ key.get("addr") + "\"}");
		}
	}


	public boolean isConnected() {
		return this.isConnected;
	}

	public void stop() {
		System.out.println("start()");

		this.isRunning = false;

		client.disconnect();

		EventListeners.removeEventListener(walletEventListener);
	}

	public void connect() throws URISyntaxException, InterruptedException, WebSocketException {

		final WebSocketHandler handler = this;

		client.connect(URL, new ConnectionHandler() {

			@Override
			public void onOpen() {
				System.out.println("Websocket onOpen()");

				handler.isConnected = true;

				handler.subscribe();

				handler.nfailures = 0;
			}

			@Override
			public void onRawTextMessage(byte[] payload) {
				System.out.println("onTextMessage()");
			}

			@Override
			public void onTextMessage(String message) {

				System.out.println("onMessage() " + message);

				MyRemoteWallet wallet = handler.getRemoteWallet();

				try {
					System.out.println("Websocket() onMessage() " + message);

					Map<String, Object> top = (Map<String, Object>) JSONValue.parse(message);

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

						MyBlock block = new MyBlock();

						block.height = blockHeight;
						block.hash = hash;
						block.blockIndex = blockIndex;
						block.time = time;

						handler.latestBlock = block;

						List<MyTransaction> transactions = wallet.getMyTransactions();
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

					} else if (op.equals("utx")) {
						Map<String, Object> x = (Map<String, Object>) top.get("x");

						MyTransaction tx = MyTransaction.fromJSONDict(x);

						BigInteger result = BigInteger.ZERO;

						for (TransactionInput input : tx.getInputs()) {
							// if the input is from me subtract the value
							MyTransactionInput myinput = (MyTransactionInput) input;

							if (wallet.isAddressMine(input.getFromAddress()
									.toString())) {
								result = result.subtract(myinput.value);

								wallet.final_balance = wallet.final_balance
										.subtract(myinput.value);
								wallet.total_sent = wallet.total_sent
										.add(myinput.value);
							}
						}

						for (TransactionOutput output : tx.getOutputs()) {
							// if the input is from me subtract the value
							MyTransactionOutput myoutput = (MyTransactionOutput) output;

							if (wallet.isAddressMine(myoutput.getToAddress()
									.toString())) {
								result = result.add(myoutput.getValue());

								wallet.final_balance = wallet
										.final_balance.add(myoutput
												.getValue());
								wallet.total_received = wallet
										.total_sent.add(myoutput
												.getValue());
							}
						}

						tx.result = result;

						wallet.prependTransaction(tx);

						if (result.compareTo(BigInteger.ZERO) >= 0) {
							EventListeners.invokeOnCoinsReceived(tx, result.longValue());
						} else {
							EventListeners.invokeOnCoinsSent(tx, result.longValue());
						}
					} else if (op.equals("on_change")) {
						String newChecksum = (String) top.get("checksum");
						String oldChecksum = wallet.getChecksum();

						System.out.println("On change " + newChecksum + " " + oldChecksum);

						if (!newChecksum.equals(oldChecksum)) {
							try {
								handler.application.checkIfWalletHasUpdatedAndFetchTransactions();
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
			public void onBinaryMessage(byte[] payload) {
				System.out.println("onBinaryMessage()");
			}

			@Override
			public void onClose(int code, String reason) {
				handler.isConnected = false;

				++handler.nfailures;
			}
		});

		EventListeners.addEventListener(walletEventListener);
	}

	public void start() {
		this.isRunning = true;

		System.out.println("WebSocket start()");

		try {
			if (client != null)
				stop();

			connect();

		} catch (URISyntaxException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (WebSocketException e) {
			e.printStackTrace();
		}
	}
}
