/*
application.getRemoteWallet() * Copyright 2011-2012 the original author or authors.
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

import com.codebutler.android_websockets.WebSocketClient;
import com.google.bitcoin.core.Sha256Hash;
import com.google.bitcoin.core.TransactionInput;
import com.google.bitcoin.core.TransactionOutput;

public class WebSocketHandler {
	final static String URL = "ws://ws.blockchain.info/inv";
	int nfailures = 0;
	static WalletApplication application;
	boolean isConnected = false;
	boolean isRunning = true;
	long lastConnectAttempt = 0;
	
	private WebSocketClient client;

	public int getBestChainHeight() {
		return getChainHead().getHeight();
	}

	public MyBlock getChainHead() {

		if (application.getRemoteWallet() == null)
			return null;

		return application.getRemoteWallet().latestBlock;
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
			if (client != null) client.send(message);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public synchronized void subscribe() {
		if (application.getRemoteWallet() == null)
			return;

		System.out.println("Websocket subscribe");

		send("{\"op\":\"blocks_sub\"}");

		send("{\"op\":\"wallet_sub\",\"guid\":\""+ application.getRemoteWallet().getGUID() + "\"}");

		String[] active = application.getRemoteWallet().getActiveAddresses();
		for (String address : active) {
			send("{\"op\":\"addr_sub\", \"addr\":\""+ address + "\"}");
		}
	}


	public boolean isConnected() {
		return this.isConnected || (client != null && client.isConnected());
	}

	public void stop() {
		System.out.println("stop()");

		this.isRunning = false;

		if (client != null) {
			client.disconnect();

			client = null;
		}

		EventListeners.removeEventListener(walletEventListener);
	}

	public static WebSocketClient newClient(final WebSocketHandler handler) throws URISyntaxException {
		return new WebSocketClient(new URI(URL), new WebSocketClient.Listener() {

			@Override
			public void onConnect() {
				handler.isConnected = true;

				handler.subscribe();

				handler.nfailures = 0;				
			}

			@Override
			public void onMessage(String message) {
				if (application.getRemoteWallet() == null)
					return;

				System.out.println("onMessage() text "  + message);

				MyRemoteWallet wallet = application.getRemoteWallet();

				try {
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


						if (application.getRemoteWallet() != null) {					
							application.getRemoteWallet().latestBlock = block;
						}

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
								application.checkIfWalletHasUpdatedAndFetchTransactions(application.getRemoteWallet().getTemporyPassword());
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
			public void onMessage(byte[] data) {
				System.out.println("onMessage() data");

			}

			@Override
			public void onDisconnect(int code, String reason) {
				handler.isConnected = false;

				++handler.nfailures;
			}

			@Override
			public void onError(Exception error) {
				error.printStackTrace();
			}

		}, null);
	}

	public void connect() throws URISyntaxException, InterruptedException {

		if (application.getRemoteWallet() == null)
			return;

		client = newClient(this);

		isConnected = false;
 
		lastConnectAttempt = System.currentTimeMillis();
		
		System.out.println("WebSocket connect()");

		client.connect();

		EventListeners.addEventListener(walletEventListener);
	}
 
	public void start() {

		if (lastConnectAttempt > System.currentTimeMillis()-30000)
			return; 
		
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
		}
	}
}
