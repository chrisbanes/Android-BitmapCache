package uk.co.senab.bitmapcache;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Md5 {

	private static final char[] DIGITS = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e',
			'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z' };

	public static String encode(String string) {
		try {
			MessageDigest digest = MessageDigest.getInstance("MD5");
			return bytesToHexString(digest.digest(string.getBytes()));
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return null;
	}

	private static String bytesToHexString(byte[] bytes) {
		final char[] buf = new char[bytes.length * 2];

		byte b;
		int c = 0;
		for (int i = 0, z = bytes.length; i < z; i++) {
			b = bytes[i];
			buf[c++] = DIGITS[(b >> 4) & 0xf];
			buf[c++] = DIGITS[b & 0xf];
		}

		return new String(buf);
	}

}
