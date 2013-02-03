package piuk;

import java.math.BigInteger;
import java.util.Arrays;

import piuk.blockchain.android.Constants;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.AddressFormatException;
import com.google.bitcoin.core.DumpedPrivateKey;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.NetworkParameters;

public class MyECKey extends ECKey {
	private static final long serialVersionUID = 1L;
	private final String addr;
	private final String base58;
	private int tag;
	private String label;
	private MyWallet wallet;
	private ECKey _key;

	public int getTag() {
		return tag;
	}

	public void setTag(int tag) {
		this.tag = tag;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public MyECKey(String addr, String base58, MyWallet wallet) {
		super((BigInteger)null, null);

		this.base58 = base58;
		this.addr = addr;
		this.wallet = wallet;
	}

	private ECKey getInternalKey() {
		if (_key == null) {
			try {
				this._key = wallet.decodePK(base58);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		return _key;
	}

	@Override
	public DumpedPrivateKey getPrivateKeyEncoded(NetworkParameters params) {
		return getInternalKey().getPrivateKeyEncoded(params);
	}

	@Override
	public boolean verify(byte[] data, byte[] signature) {
		return getInternalKey().verify(data, signature);
	}
	@Override
	public byte[] sign(byte[] input) {
		return getInternalKey().sign(input);
	}

	@Override
	public byte[] getPubKey() {
		return getInternalKey().getPubKey();
	}

	@Override
	public byte[] toASN1() {
		return getInternalKey().toASN1();
	}

	@Override
	public byte[] getPrivKeyBytes() {
		return getInternalKey().getPrivKeyBytes();
	}

	/** Gets the hash160 form of the public key (as seen in addresses). */
	@Override
	public byte[] getPubKeyHash() {
		return toAddress(Constants.NETWORK_PARAMETERS).getHash160();
	}

	@Override
	public Address toAddress(NetworkParameters params) {
		try {
			return new Address(params, addr);
		} catch (AddressFormatException e) {
			e.printStackTrace();
		}

		return null;
	}
	
    @Override
    public boolean equals(Object o) {
    	if (o instanceof MyECKey)
    		return this.base58.equals(((MyECKey)o).base58);
    	
    	return false;
    }

    @Override
    public int hashCode() {
        return base58.hashCode();
    }
}