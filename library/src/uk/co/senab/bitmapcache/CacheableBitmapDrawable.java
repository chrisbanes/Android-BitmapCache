package uk.co.senab.bitmapcache;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.util.Log;

public class CacheableBitmapDrawable extends BitmapDrawable {

	static final String LOG_TAG = "CacheableBitmapDrawable";

	private final String mUrl;

	// Number of Views currently displaying bitmap
	private int mDisplayingCount;

	// Number of caches currently referencing the wrapper
	private int mCacheCount;

	public CacheableBitmapDrawable(Bitmap bitmap) {
		this(null, bitmap);
	}

	@SuppressWarnings("deprecation")
	public CacheableBitmapDrawable(String url, Bitmap bitmap) {
		super(bitmap);

		mUrl = url;
		mDisplayingCount = 0;
		mCacheCount = 0;
	}

	/**
	 * @return Amount of heap size currently being used by {@code Bitmap}
	 */
	int getMemorySize() {
		int size = 0;

		final Bitmap bitmap = getBitmap();
		if (null != bitmap && !bitmap.isRecycled()) {
			size = bitmap.getRowBytes() * bitmap.getHeight();
		}

		return size;
	}

	/**
	 * @return the URL associated with the BitmapDrawable
	 */
	public String getUrl() {
		return mUrl;
	}

	/**
	 * Returns true when this wrapper has a bitmap and the bitmap has not been
	 * recycled.
	 * 
	 * @return true - if the bitmap has not been recycled.
	 */
	public synchronized boolean hasValidBitmap() {
		Bitmap bitmap = getBitmap();
		if (null != bitmap) {
			return !bitmap.isRecycled();
		}
		return false;
	}

	/**
	 * @return true - if the bitmap is currently being displayed by a
	 *         {@link CacheableImageView}.
	 */
	public synchronized boolean isBeingDisplayed() {
		return mDisplayingCount > 0;
	}

	/**
	 * @return true - if the wrapper is currently referenced by a cache.
	 */
	public synchronized boolean isReferencedByCache() {
		return mCacheCount > 0;
	}

	/**
	 * Used to signal to the Drawable whether it is being used or not.
	 * 
	 * @param beingUsed - true if being used, false if not.
	 */
	public synchronized void setBeingUsed(boolean beingUsed) {
		if (beingUsed) {
			mDisplayingCount++;
		} else {
			mDisplayingCount--;
		}
		checkState();
	}

	/**
	 * Used to signal to the wrapper whether it is being referenced by a cache
	 * or not.
	 * 
	 * @param added - true if the wrapper has been added to a cache, false if
	 *            removed.
	 */
	synchronized void setCached(boolean added) {
		if (added) {
			mCacheCount++;
		} else {
			mCacheCount--;
		}
		checkState();
	}

	/**
	 * Checks whether the wrapper is currently referenced, and is being
	 * displayed. If neither of those conditions are met then the bitmap is
	 * recycled and freed.
	 */
	private void checkState() {
		if (Constants.DEBUG) {
			Log.d(LOG_TAG, String.format("checkState() Displaying: %d, Caching: %d, URL: %s", mDisplayingCount,
					mCacheCount, mUrl));
		}

		if (mCacheCount <= 0 && mDisplayingCount <= 0 && hasValidBitmap()) {
			if (Constants.DEBUG) {
				Log.d(LOG_TAG, "Recycling bitmap with url: " + mUrl);
			}
			getBitmap().recycle();
		}
	}

}
