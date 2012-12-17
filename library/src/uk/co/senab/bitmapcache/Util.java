package uk.co.senab.bitmapcache;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;

public class Util {

	public static long copy(File in, OutputStream out) throws IOException {
		return copy(new FileInputStream(in), out);
	}

	public static long copy(InputStream in, File out) throws IOException {
		return copy(in, new FileOutputStream(out));
	}

	public static String md5(String string) {
		try {
			// Create MD5 Hash
			MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
			digest.update(string.getBytes());

			byte messageDigest[] = digest.digest();

			// Create Hex String
			StringBuffer hexString = new StringBuffer();
			for (int i = 0, z = messageDigest.length; i < z; i++) {
				hexString.append(Integer.toHexString(0xFF & messageDigest[i]));
			}
			return hexString.toString();

		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static void saveBitmap(Bitmap bitmap, OutputStream out) {
		bitmap.compress(CompressFormat.PNG, 100, out);
	}

	/**
	 * Pipe an InputStream to the given OutputStream
	 * <p />
	 * Taken from Apache Commons IOUtils.
	 * 
	 * @param in
	 * @param out
	 * @throws IOException
	 */
	private static long copy(InputStream input, OutputStream output) throws IOException {
		byte[] buffer = new byte[1024 * 4];
		long count = 0;
		int n = 0;
		while (-1 != (n = input.read(buffer))) {
			output.write(buffer, 0, n);
			count += n;
		}
		return count;
	}

}
