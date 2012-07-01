package uk.co.senab.bitmapcache.cache;

import android.graphics.Bitmap;

public class CacheableBitmapWrapper {

	private final String mUrl;
	private final Bitmap mBitmap;

	// Number of ImageViews currently showing bitmap
	private int mImageViewsCount;

	public CacheableBitmapWrapper(String url, Bitmap bitmap) {
		mUrl = url;
		mBitmap = bitmap;
		mImageViewsCount = 0;
	}

	public void setDisplayed(boolean displayed) {
		if (displayed) {
			mImageViewsCount++;
		} else {
			mImageViewsCount--;
		}
	}

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

}
