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

import android.util.Pair;
import org.spongycastle.util.encoders.Hex;
import com.google.bitcoin.core.*;
import com.google.bitcoin.core.Transaction.SigHash;
import com.google.bitcoin.core.WalletTransaction.Pool;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.json.simple.JSONValue;
import piuk.blockchain.android.Constants;
import piuk.blockchain.android.ui.SendCoinsFragment;
import piuk.blockchain.android.ui.SendCoinsFragment.FeePolicy;

import java.io.DataOutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.*;


@SuppressWarnings("unchecked")
public class MyRemoteWallet extends MyWallet {
	public static final String WebROOT = "https://blockchain.info/";
	String _checksum;
	boolean _isNew = false;
	MyBlock latestBlock;
	public long lastMultiAddress;
	public BigInteger final_balance = BigInteger.ZERO;
	public BigInteger total_received = BigInteger.ZERO;
	public BigInteger total_sent = BigInteger.ZERO;
	public int n_tx = 0;
	List<MyTransaction> transactions = Collections.synchronizedList(new ArrayList<MyTransaction>());

	public boolean isAddressMine(String address) {
		for (Map<String, Object> map : this.getKeysMap()) {
			String addr = (String) map.get("addr");

			if (address.equals(addr))
				return true;
		}

		return false;
	}

	public static class Latestblock {
		int height;
		int block_index;
		Hash hash;
		long time;
	}

	public synchronized BigInteger getBalance() {
		return final_balance;
	}


	public boolean isNew() {
		return _isNew;
	}

	public MyRemoteWallet() throws Exception {
		super();

		this.temporyPassword = null;

		this._checksum  = null;

		this._isNew = true;
	}

	public MyRemoteWallet(String base64Payload, String password) throws Exception {
		super(base64Payload, password);

		this.temporyPassword = password;

		this._checksum  = new String(Hex.encode(MessageDigest.getInstance("SHA-256").digest(base64Payload.getBytes("UTF-8"))));

		this._isNew = false;
	}

	private static String fetchURL(String URL) throws Exception {
		URL url = new URL(URL);

		HttpURLConnection connection = (HttpURLConnection) url.openConnection();

		try {
			connection.setRequestProperty("Accept", "application/json");
			connection.setRequestProperty("charset", "utf-8");
			connection.setRequestMethod("GET");

			connection.setConnectTimeout(30000);
			connection.setReadTimeout(30000);

			connection.setInstanceFollowRedirects(false);

			connection.connect();

			if (connection.getResponseCode() == 200)
				return IOUtils.toString(connection.getInputStream(), "UTF-8");
			else if (connection.getResponseCode() == 500 && (connection.getContentType() == null || connection.getContentType().equals("text/plain")))
				throw new Exception("Error From Server: " +  IOUtils.toString(connection.getErrorStream(), "UTF-8"));
			else
				throw new Exception("Unknown response from server");

		} finally {
			connection.disconnect();
		}
	}

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

			if (connection.getResponseCode() != 200)
				throw new Exception("Error Response " + IOUtils.toString(connection.getErrorStream(), "UTF-8"));
			else
				return IOUtils.toString(connection.getInputStream(), "UTF-8");

		} finally {
			connection.disconnect();
		}
	}

	@Override
	public synchronized boolean addKey(ECKey key, String label) throws Exception {
		boolean success = super.addKey(key, label);

		EventListeners.invokeWalletDidChange();

		return success;
	}

	public List<MyTransaction> getMyTransactions() {
		return transactions;
	}

	public void addTransaction(MyTransaction tx) {
		this.transactions.add(tx);
	}

	public void prependTransaction(MyTransaction tx) {
		this.transactions.add(0, tx);
	}

	public List<MyTransaction> getTransactions() {
		return this.transactions;
	}

	public void parseMultiAddr(String response, boolean notifications) throws Exception {

		System.out.println("parseMultiAddr() " + notifications);
		
		transactions.clear();

		BigInteger previousBalance = final_balance;

		Map<String, Object> top = (Map<String, Object>) JSONValue.parse(response);

		Map<String, Object> info_obj = (Map<String, Object>) top.get("info");

		Map<String, Object> block_obj = (Map<String, Object>) info_obj.get("latest_block");

		if (block_obj != null) {
			Sha256Hash hash = new Sha256Hash(Hex.decode((String)block_obj.get("hash")));
			int blockIndex = ((Number)block_obj.get("block_index")).intValue();
			int blockHeight = ((Number)block_obj.get("height")).intValue();
			long time = ((Number)block_obj.get("time")).longValue();

			MyBlock block = new MyBlock();

			block.height = blockHeight;
			block.hash = hash;
			block.blockIndex = blockIndex;
			block.time = time;

			this.latestBlock = block;
		}

		Map<String, Object> wallet_obj = (Map<String, Object>) top.get("wallet");

		this.final_balance = BigInteger.valueOf(((Number)wallet_obj.get("final_balance")).longValue());
		this.total_sent = BigInteger.valueOf(((Number)wallet_obj.get("total_sent")).longValue());
		this.total_received = BigInteger.valueOf(((Number)wallet_obj.get("total_received")).longValue());
		this.n_tx = ((Number)wallet_obj.get("n_tx")).intValue();

		List<Map<String, Object>> transactions = (List<Map<String, Object>>) top.get("txs");

		MyTransaction newestTransaction = null;
		if (transactions != null) {
			for (Map<String, Object> transactionDict : transactions) {
				MyTransaction tx = MyTransaction.fromJSONDict(transactionDict);

				if (tx == null)
					continue;

				if (newestTransaction == null)
					newestTransaction = tx;

				addTransaction(tx);
			}
		}

		if (notifications) {
			if (this.final_balance.compareTo(previousBalance) != 0) {
				if (newestTransaction.getResult().compareTo(BigInteger.ZERO) >= 0)
					EventListeners.invokeOnCoinsReceived(newestTransaction, newestTransaction.getResult().longValue());
				else
					EventListeners.invokeOnCoinsSent(newestTransaction, newestTransaction.getResult().longValue());
			}
		} else {
			EventListeners.invokeOnTransactionsChanged();
		}
	}

	public boolean isUptoDate(long time) {
		long now = System.currentTimeMillis();

		if (lastMultiAddress < now - time) {
			return false;
		} else {
			return true;
		}
	}

	public synchronized String doMultiAddr() throws Exception {
		String url =  WebROOT + "multiaddr?active=" + StringUtils.join(getActiveAddresses(), "|")+ "&archived=" + StringUtils.join(getArchivedAddresses(), "|");

		String response = fetchURL(url);

		parseMultiAddr(response, true);

		lastMultiAddress = System.currentTimeMillis();

		return response;
	}

	public synchronized boolean remoteSave() throws Exception {
		return remoteSave(null);
	}

	public interface SendProgress {
		//Return false to cancel
		public boolean onReady(Transaction tx, BigInteger fee, SendCoinsFragment.FeePolicy feePolicy, long priority);
		public void onSend(Transaction tx, String message);

		//Return true to cancel the transaction or false to continue without it
		public ECKey onPrivateKeyMissing(String address);

		public void onError(String message);
		public void onProgress(String message);
	}

	public void sendCoinsAsync(final String toAddress, final BigInteger amount, final FeePolicy feePolicy, final BigInteger fee, final SendProgress progress) {

		new Thread() {
			@Override
			public void run() {
				List<ECKey> tempKeys = new ArrayList<ECKey>();

				try {

					//Construct a new transaction
					progress.onProgress("Getting Unspent Outputs");

					List<MyTransactionOutPoint> unspent = getUnspentOutputPoints();
					List<MyTransactionOutPoint> toRemove = new ArrayList<MyTransactionOutPoint>();
					Set<String> alreadyAskedFor = new HashSet<String>();

					for (MyTransactionOutPoint output : unspent) {

						BitcoinScript script = new BitcoinScript(output.getScriptBytes());

						String addr = script.getAddress().toString();

						Map<String, Object> keyMap = findKey(addr);

						if (keyMap.get("priv") == null) {
							if (alreadyAskedFor.add(addr)) {
								ECKey key = progress.onPrivateKeyMissing(addr);

								if (key != null) {
									tempKeys.add(key);
								} else {
									toRemove.add(output);
								}
							} else {
								toRemove.add(output);
							}
						}
					}

					//Remove those outputs which we could not find a private key for
					unspent.removeAll(toRemove);

					progress.onProgress("Constructing Transaction");

					Pair<Transaction, Long> pair = makeTransaction(unspent, toAddress, amount, fee);

					//Transaction cancelled
					if (pair == null)
						return;

					Transaction tx = pair.first;
					Long priority = pair.second;

					//If returns false user cancelled
					//Probably because they want to recreate the transaction with different fees
					if (!progress.onReady(tx, fee, feePolicy, priority))
						return;

					progress.onProgress("Signing Inputs");

					//Now sign the inputs
					tx.signInputs(SigHash.ALL, getBitcoinJWallet());

					progress.onProgress("Broadcasting Transaction");

					String response = pushTx(tx);

					progress.onSend(tx, response);

				} catch (Exception e) {
					e.printStackTrace();

					progress.onError(e.getLocalizedMessage());

				} 
			}
		}.start();
	}

	//Rerutns response message
	public String pushTx(Transaction tx) throws Exception {

		String hexString = new String(Hex.encode(tx.bitcoinSerialize()));

		if (hexString.length() > 16384)
			throw new Exception("Blockchain wallet's cannot handle transactions over 16kb in size. Please try splitting your transaction");

		String response = postURL(WebROOT + "pushtx", "tx="+hexString);

		return response;
	}

	//You must sign the inputs
	public Pair<Transaction, Long> makeTransaction(List<MyTransactionOutPoint> unspent, String toAddress, BigInteger amount, BigInteger fee) throws Exception {

		long priority = 0;

		if (unspent == null || unspent.size() == 0)
			throw new Exception("No free outputs to spend. Some transactions maybe pending confirmation.");

		if (fee == null)
			fee = BigInteger.ZERO;

		if (amount == null || amount.compareTo(BigInteger.ZERO) <= 0)
			throw new Exception("You must provide an amount");

		//Construct a new transaction
		Transaction tx = new Transaction(params);

		//Add the output
		BitcoinScript toOutputScript = BitcoinScript.createSimpleOutBitoinScript(new BitcoinAddress(toAddress));

		TransactionOutput output = new TransactionOutput(params, null, amount, toOutputScript.getProgram());

		tx.addOutput(output);

		//Now select the appropriate inputs
		BigInteger valueSelected = BigInteger.ZERO;
		BigInteger valueNeeded =  amount.add(fee);
		MyTransactionOutPoint firstOutPoint = null;

		for (MyTransactionOutPoint outPoint : unspent) {

			BitcoinScript script = new BitcoinScript(outPoint.getScriptBytes());

			if (script.getOutType() == BitcoinScript.ScriptOutTypeStrange)
				continue;

			MyTransactionInput input = new MyTransactionInput(params, null, new byte[0], outPoint);

			input.outpoint = outPoint;

			tx.addInput(input);

			valueSelected = valueSelected.add(outPoint.value);

			priority += outPoint.value.longValue() * outPoint.confirmations;

			if (firstOutPoint == null)
				firstOutPoint = outPoint;

			if (valueSelected.compareTo(valueNeeded) >= 0)
				break;
		}

		//Check the amount we have selected is greater than the amount we need
		if (valueSelected.compareTo(valueNeeded) < 0) {
			throw new Exception("Insufficient Funds");
		}

		BigInteger change = valueSelected.subtract(amount).subtract(fee);

		//Now add the change if there is any
		if (change.compareTo(BigInteger.ZERO) > 0) {
			BitcoinScript inputScript = new BitcoinScript(firstOutPoint.getConnectedPubKeyScript());

			//Return change to the first address
			BitcoinScript change_script = BitcoinScript.createSimpleOutBitoinScript(inputScript.getAddress());

			TransactionOutput change_output = new TransactionOutput(params, null, change, change_script.getProgram());

			tx.addOutput(change_output);
		}

		long estimatedSize = tx.bitcoinSerialize().length + (114 * tx.getInputs().size());

		priority /= estimatedSize;

		return new Pair<Transaction, Long>(tx, priority);
	}

	public List<MyTransactionOutPoint> getUnspentOutputPoints() throws Exception {

		StringBuffer buffer =  new StringBuffer(WebROOT + "unspent?");

		for (Map<String, Object> map : this.getKeysMap()) {
			String addr = (String) map.get("addr");

			//Only include active addresses
			if (map.get("tag") == null || (Long)map.get("tag") == 0)
				buffer.append("&addr[]="+addr);
		}

		List<MyTransactionOutPoint> outputs = new ArrayList<MyTransactionOutPoint>();

		String response = fetchURL(buffer.toString());

		Map<String, Object> root = (Map<String, Object>) JSONValue.parse(response);

		List<Map<String, Object>> outputsRoot = (List<Map<String, Object>>) root.get("unspent_outputs");

		for (Map<String, Object> outDict : outputsRoot) {

			byte[] hashBytes = Hex.decode((String)outDict.get("tx_hash"));

			ArrayUtils.reverse(hashBytes);

			Sha256Hash txHash = new Sha256Hash(hashBytes);

			int txOutputN = ((Number)outDict.get("tx_output_n")).intValue();
			BigInteger value = BigInteger.valueOf(((Number)outDict.get("value")).longValue());
			byte[] scriptBytes = Hex.decode((String)outDict.get("script"));
			int confirmations = ((Number)outDict.get("confirmations")).intValue();

			//Contrstuct the output
			MyTransactionOutPoint outPoint = new MyTransactionOutPoint(txHash, txOutputN, value, scriptBytes);

			outPoint.setConfirmations(confirmations);

			outputs.add(outPoint);
		}

		return outputs;
	}

	/**
	 * Register this account/device pair within the server.
	 * @throws Exception 
	 *
	 */
	public boolean registerNotifications(final String regId) throws Exception {
		if (_isNew) return false;

		StringBuilder args = new StringBuilder();

		args.append("guid=" + getGUID());
		args.append("&sharedKey=" + getSharedKey());
		args.append("&method=register-android-device");
		args.append("&payload="+URLEncoder.encode(regId));
		args.append("&length="+regId.length());

		String response = postURL(WebROOT + "wallet", args.toString());

		return response != null && response.length() > 0;
	}

	/** k
	 * Unregister this account/device pair within the server.
	 * @throws Exception 
	 */
	public boolean unregisterNotifications(final String regId) throws Exception {    
		if (_isNew) return false;

		StringBuilder args = new StringBuilder();

		args.append("guid=" + getGUID());
		args.append("&sharedKey=" + getSharedKey());
		args.append("&method=unregister-android-device");
		args.append("&payload="+URLEncoder.encode(regId));
		args.append("&length="+regId.length());

		String response = postURL(WebROOT + "wallet", args.toString());

		return response != null && response.length() > 0;
	}


	/**
	 * Register this account/device pair within the server.
	 * @throws Exception 
	 *
	 */
	public static String getPairingEncryptionPassword(final String guid) throws Exception {
		StringBuilder args = new StringBuilder();

		args.append("guid=" + guid);
		args.append("&method=pairing-encryption-password");

		return postURL(WebROOT + "wallet", args.toString());
	}

	public synchronized boolean remoteSave(String kaptcha) throws Exception {

		String payload = this.getPayload();

		String old_checksum = this._checksum;
		this._checksum  = new String(Hex.encode(MessageDigest.getInstance("SHA-256").digest(payload.getBytes("UTF-8"))));

		String method = _isNew ? "insert" : "update";

		if (kaptcha == null && _isNew)
		{
			throw new Exception("Must provide a kaptcha to insert wallet");
		}
		else if (kaptcha == null)
		{
			kaptcha = "";
		}

		String urlEncodedPayload = URLEncoder.encode(payload);

		StringBuilder args = new StringBuilder();
		args.append("guid=");
		args.append(URLEncoder.encode(this.getGUID(), "utf-8"));
		args.append("&sharedKey=");
		args.append(URLEncoder.encode(this.getSharedKey(), "utf-8"));
		args.append("&payload=");
		args.append(urlEncodedPayload);
		args.append("&method=");
		args.append(method);
		args.append("&length=");
		args.append(payload.length());
		args.append("&checksum=");
		args.append(URLEncoder.encode(_checksum, "utf-8"));
		args.append("&kaptcha=");
		args.append(kaptcha);

		if (old_checksum != null && old_checksum.length() > 0)
		{
			args.append("&old_checksum=");
			args.append(old_checksum);
		}

		postURL(WebROOT + "wallet", args.toString());

		_isNew = false;

		return true;
	}

	public void remoteDownload() {

	}

	public String getChecksum() {
		return _checksum;
	}

	public synchronized String setPayload(String payload) throws Exception {

		MyRemoteWallet tempWallet = new MyRemoteWallet(payload, temporyPassword);

		this.root = tempWallet.root;

		this.temporySecondPassword = null;

		this._checksum = tempWallet._checksum;

		_isNew = false;

		return payload;
	}

	public static class NotModfiedException extends Exception {
		private static final long serialVersionUID = 1L;
	}

	public static String getWalletPayload(String guid, String sharedKey, String checkSumString) throws Exception {
		String payload = fetchURL(WebROOT + "wallet/wallet.aes.json?guid="+guid+"&sharedKey="+sharedKey+"&checksum="+checkSumString);

		if (payload == null) {
			throw new Exception("Error downloading wallet");
		}

		if (payload.equals("Not modified")) {
			throw new NotModfiedException();
		}

		return payload;
	}

	public static String getWalletPayload(String guid, String sharedKey) throws Exception {
		String payload = fetchURL(WebROOT + "wallet/wallet.aes.json?guid="+guid+"&sharedKey="+sharedKey);

		if (payload == null) {
			throw new Exception("Error downloading wallet");
		}

		return payload;
	}

}
