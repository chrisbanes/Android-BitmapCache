package uk.co.senab.bitmapcache;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;

public class Util {

	/**
	 * Pipe an InputStream to the given OutputStream
	 * 
	 * @param in
	 * @param out
	 * @throws IOException
	 */
	// TODO Can probably be optimized
	public static void pipe(InputStream in, OutputStream out) throws IOException {
		byte[] buffer = new byte[1024];
		int len = in.read(buffer);
		while (len != -1) {
			out.write(buffer, 0, len);
			len = in.read(buffer);
		}
	}

	public static void saveBitmap(Bitmap bitmap, OutputStream out) {
		bitmap.compress(CompressFormat.PNG, 100, out);
	}

}
