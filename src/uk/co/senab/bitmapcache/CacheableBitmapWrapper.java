package uk.co.senab.bitmapcache;

import android.graphics.Bitmap;
import android.util.Log;

public class CacheableBitmapWrapper {
	
	static final String LOG_TAG = "CacheableBitmapWrapper";

	private final String mUrl;
	private final Bitmap mBitmap;

	// Number of ImageViews currently showing bitmap
	private int mImageViewsCount;

	// Number of caches currently referencing the wrapper
	private int mCacheCount;

	public CacheableBitmapWrapper(String url, Bitmap bitmap) {
		mUrl = url;
		mBitmap = bitmap;
		mImageViewsCount = 0;
		mCacheCount = 0;
	}

	/**
	 * @return true - if the wrapper is currently referenced by a cache.
	 */
	public boolean isReferencedByCache() {
		return mCacheCount > 0;
	}

	/**
	 * @return true - if the bitmap is currently being displayed by a
	 *         {@link CacheableImageView}.
	 */
	public boolean isBeingDisplayed() {
		return mImageViewsCount > 0;
	}

	/**
	 * Returns the currently reference Bitmap
	 * 
	 * @return Bitmap - referenced Bitmaps
	 */
	public Bitmap getBitmap() {
		return mBitmap;
	}

	public String getUrl() {
		return mUrl;
	}

	/**
	 * Returns true when this wrapper has a bitmap and the bitmap has not been
	 * recycled.
	 * 
	 * @return true - if it's bitmap is not null and the bitmap has not been
	 *         recycled.
	 */
	public boolean hasValidBitmap() {
		return null != mBitmap && !mBitmap.isRecycled();
	}

	/**
	 * Used to signal to the wrapper whether it is being referenced by a cache or not.
	 * @param added - true if the cache has been added to a cache, false if removed.
	 */
	void setCached(boolean added) {
		if (added) {
			mCacheCount++;
		} else {
			mCacheCount--;
		}
		checkState();
	}

	/**
	 * Used to signal to the wrapper whether it is being displayed by an ImageView or not.
	 * @param added - true if displayed, false if not.
	 */
	void setDisplayed(boolean displayed) {
		if (displayed) {
			mImageViewsCount++;
		} else {
			mImageViewsCount--;
		}
		checkState();
	}

	/**
	 * Checks whether the wrapper is currently referenced, and is being
	 * displayed. If neither of those conditions are met then the bitmap is
	 * recycled and freed.
	 */
	private void checkState() {
		if (mCacheCount <= 0 && mImageViewsCount <= 0 && hasValidBitmap()) {
			if (BuildConfig.DEBUG) {
				Log.d(LOG_TAG, "Recycling bitmap with url: " + mUrl);
			}
			mBitmap.recycle();
		}
	}

}
