package piuk;

import com.google.bitcoin.core.*;

import java.math.BigInteger;

public class MyECKey extends ECKey {
    private static final long serialVersionUID = 1L;
    protected final String addr;
    protected final String base58;
    protected final MyWallet wallet;

    private int tag;
    private String label;
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

        this.addr = addr;
        this.base58 = base58;
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
    public ECDSASignature sign(Sha256Hash input) {
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
    public byte[] getPubKeyHash() {
        return getInternalKey().getPubKeyHash();
    }

    /** Gets the hash160 form of the public key (as seen in addresses). */
    public byte[] getCompressedPubKeyHash() {
        return getInternalKey().getCompressedPubKeyHash();
    }

    /**
     * Gets the raw public key value. This appears in transaction scriptSigs. Note that this is <b>not</b> the same
     * as the pubKeyHash/address.
     */
    public byte[] getPubKeyCompressed() {
        return getInternalKey().getPubKeyCompressed();
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
    public Address toAddressCompressed(NetworkParameters params) {
        try {
            return new Address(params, addr);
        } catch (AddressFormatException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public String toString() {
        return getInternalKey().toString();
    }

    @Override
    public String toStringWithPrivate() {
        return getInternalKey().toStringWithPrivate();
    }


    @Override
    public boolean equals(Object o) {
        if (o instanceof MyECKey)
            return this.base58.equals(((MyECKey)o).base58);
        else if (o instanceof ECKey)
            return this.getInternalKey().equals(o);

        return false;
    }

    @Override
    public int hashCode() {
        return this.getInternalKey().hashCode();
    }
}