package uk.co.senab.bitmapcache;

import android.graphics.Bitmap;

/**
 * @deprecated - You should use {@link CacheableBitmapDrawable} instead.
 */
public class CacheableBitmapWrapper extends CacheableBitmapDrawable {

	public CacheableBitmapWrapper(Bitmap bitmap) {
		super(bitmap);
	}

	public CacheableBitmapWrapper(String url, Bitmap bitmap) {
		super(url, bitmap);
	}

}
