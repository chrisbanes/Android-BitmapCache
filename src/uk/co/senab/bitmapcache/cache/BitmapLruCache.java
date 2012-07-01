package uk.co.senab.bitmapcache.cache;

import java.util.Map.Entry;

import uk.co.senab.bitmapcache.BuildConfig;
import uk.co.senab.util.LruCache;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

public class BitmapLruCache extends LruCache<String, CacheableBitmapWrapper> {

	static final int DEFAULT_CACHE_SIZE = 8; // 1/8th
	static final String LOG_TAG = "BitmapLruCache";
	static final int MEGABYTE = 1024 * 1024;

	/**
	 * Initialise LruCache with default size of 1/8th of heap size.
	 * 
	 * @param context
	 */
	public BitmapLruCache(Context context) {
		this(MEGABYTE
				* ((ActivityManager) context
						.getSystemService(Context.ACTIVITY_SERVICE))
						.getMemoryClass() / DEFAULT_CACHE_SIZE);
	}

	public BitmapLruCache(int maxSize) {
		super(maxSize);
	}

	@Override
	protected int sizeOf(String key, CacheableBitmapWrapper value) {
		if (value.hasValidBitmap()) {
			Bitmap bitmap = value.getBitmap();
			return bitmap.getRowBytes() * bitmap.getHeight();
		}
		return 0;
	}

	/**
	 * Convenience method that just calls through to
	 * {@link LruCache#put(K key, V value) } with the Key as
	 * {@link CacheableBitmapWrapper#getUrl() }
	 * 
	 * @param newValue
	 *            - Value to put into cache
	 * @return oldValue
	 */
	public CacheableBitmapWrapper put(CacheableBitmapWrapper newValue) {
		return put(newValue.getUrl(), newValue);
	}
	
	@Override
	public boolean canRemoveEntry(String key, CacheableBitmapWrapper value) {
		return !value.isBeingDisplayed();
	}

	@Override
	protected void entryRemoved(boolean evicted, String key,
			CacheableBitmapWrapper oldValue, CacheableBitmapWrapper newValue) {

		if (BuildConfig.DEBUG) {
			Log.d(LOG_TAG, "EntryRemoved. Key: " + key);
		}

		// If the value being removed isn't being displayed, recycle it
		if (!oldValue.isBeingDisplayed()) {
			Bitmap bitmap = oldValue.getBitmap();
			bitmap.recycle();
		} else {
			// Should handle here. Maybe a second-level SoftReference Cache?
		}
	}

	/**
	 * This method iterates through the cache and removes any Bitmap entries
	 * which are not currently being displayed. A good place to call this would
	 * be from {@link android.app.Application#onLowMemory()
	 * Application.onLowMemory()}.
	 */
	public void trimMemory() {
		for (Entry<String, CacheableBitmapWrapper> entry : snapshot()
				.entrySet()) {
			CacheableBitmapWrapper value = entry.getValue();
			if (null == value || !value.isBeingDisplayed()) {
				remove(entry.getKey());
			}
		}
	}

}
