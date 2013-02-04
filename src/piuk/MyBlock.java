package piuk;

import com.google.bitcoin.core.Sha256Hash;

public class MyBlock {
	int height;
	long time;
	Sha256Hash hash;
	int blockIndex;

	public long getTime() {
		return time;
	}
	
	public int getHeight() {
		return height;
	}

	public Sha256Hash getHash() {
		return hash;
	}
}