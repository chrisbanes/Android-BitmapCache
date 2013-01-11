package uk.co.senab.bitmapcache;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.util.Log;
import android.widget.ImageView;

public class CacheableBitmapDrawable extends BitmapDrawable {

	static final String LOG_TAG = "CacheableBitmapDrawable";

	private final String mUrl;

	// Number of Views currently displaying bitmap
	private int mDisplayingCount;

	// Number of caches currently referencing the wrapper
	private int mCacheCount;

	CacheableBitmapDrawable(Bitmap bitmap) {
		this(null, bitmap);
	}

	@SuppressWarnings("deprecation")
	CacheableBitmapDrawable(String url, Bitmap bitmap) {
		super(bitmap);

		mUrl = url;
		mDisplayingCount = 0;
		mCacheCount = 0;
	}

	/**
	 * 
	 * @param imageView
	 */
	public final void display(ImageView imageView) {
		if (null != imageView) {
			imageView.setImageDrawable(this);

			/**
			 * ImageView does not call setVisible(...) when the Drawable is
			 * changed after onAttachedToWindow(). Thus, if the ImageView is
			 * already attached to the Window, we have to call
			 * setBeingUsed(true) to make sure our reference count is correct.
			 */
			if (!isVisible()) {
				setBeingUsed(true);
			}
		}
	}

	/**
	 * @return Amount of heap size currently being used by {@code Bitmap}
	 */
	int getMemorySize() {
		final Bitmap bitmap = getBitmap();
		int size = 0;

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

	@Override
	public boolean setVisible(boolean visible, boolean restart) {
		if (Constants.DEBUG) {
			Log.d(LOG_TAG, "setVisible: " + visible);
		}

		final boolean superResult = super.setVisible(visible, restart);

		// If the visibility has changed, change the reference count
		if (superResult) {
			setBeingUsed(visible);
		}

		return superResult;
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
		if (mCacheCount <= 0 && mDisplayingCount <= 0 && hasValidBitmap()) {
			if (Constants.DEBUG) {
				Log.d(Constants.LOG_TAG, "Recycling bitmap with url: " + mUrl);
			}
			getBitmap().recycle();
		}
	}

}
