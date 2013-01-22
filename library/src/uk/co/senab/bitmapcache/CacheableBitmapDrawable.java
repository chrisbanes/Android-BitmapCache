package uk.co.senab.bitmapcache;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public class CacheableBitmapDrawable extends BitmapDrawable {

	static final String LOG_TAG = "CacheableBitmapDrawable";

	private final String mUrl;

	// Number of Views currently displaying bitmap
	private int mDisplayingCount;

	// Has it been displayed yet
	private boolean mHasBeenDisplayed;

	// Number of caches currently referencing the wrapper
	private int mCacheCount;

	// Handler which may be used later
	private static final Handler sHandler = new Handler(Looper.getMainLooper());

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
			mHasBeenDisplayed = true;
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
	 * Calls {@link #checkState(boolean)} with default parameter of
	 * <code>false</code>.
	 */
	private void checkState() {
		checkState(false);
	}

	/**
	 * Checks whether the wrapper is currently referenced by a cache, and is
	 * being displayed. If neither of those conditions are met then the bitmap
	 * is ready to be recycled. Whether this happens now, or is delayed depends
	 * on whether the Drawable has been displayed or not.
	 * <ul>
	 * <li>If it has been displayed, it is recycled straight away.</li>
	 * <li>If it has not been displayed, and <code>ignoreBeenDisplayed</code> is
	 * <code>false</code>, a call to <code>checkState(true)</code> is queued to
	 * be called after a delay.</li>
	 * <li>If it has not been displayed, and <code>ignoreBeenDisplayed</code> is
	 * <code>true</code>, it is recycled straight away.</li>
	 * </ul>
	 * 
	 * @see Constants#UNUSED_DRAWABLE_RECYCLE_DELAY_MS
	 * 
	 * @param ignoreBeenDisplayed - Whether to ignore the 'has been displayed'
	 *            flag when deciding whether to recycle() now.
	 */
	private synchronized void checkState(final boolean ignoreBeenDisplayed) {
		if (Constants.DEBUG) {
			Log.d(LOG_TAG, String.format("checkState(). Been Displayed: %b, Displaying: %d, Caching: %d, URL: %s",
					mHasBeenDisplayed, mDisplayingCount, mCacheCount, mUrl));
		}

		// We only want to recycle if it has been displayed.
		if (mCacheCount <= 0 && mDisplayingCount <= 0 && hasValidBitmap()) {

			/**
			 * If we have been displayed or we don't care whether we have been
			 * or not, then recycle() now. Otherwise, we retry in 1 second.
			 */
			if (mHasBeenDisplayed || ignoreBeenDisplayed) {
				if (Constants.DEBUG) {
					Log.d(LOG_TAG, "Recycling bitmap with url: " + mUrl);
				}
				getBitmap().recycle();
			} else {
				if (Constants.DEBUG) {
					Log.d(LOG_TAG, "Unused Bitmap which hasn't been displayed, delaying recycle(): " + mUrl);
				}
				sHandler.postDelayed(new CheckStateRunnable(), Constants.UNUSED_DRAWABLE_RECYCLE_DELAY_MS);
			}
		}
	}

	/**
	 * Runnable which run a {@link CacheableBitmapDrawable#checkState(boolean)
	 * checkState(false)} call.
	 * 
	 * @author chrisbanes
	 */
	private final class CheckStateRunnable implements Runnable {

		@Override
		public void run() {
			checkState(true);
		}

	}

}
