package uk.co.senab.bitmapcache;

import android.app.ActivityManager;
import android.content.Context;

import com.jakewharton.DiskLruCache;

public class BitmapLruCache {

	private DiskLruCache mDiskCache;
	private BitmapMemoryLruCache mMemoryCache;

	void setDiskLruCache(DiskLruCache cache) {
		mDiskCache = cache;
	}

	void setMemoryLruCache(BitmapMemoryLruCache cache) {
		mMemoryCache = cache;
	}

	public CacheableBitmapWrapper get(String url) {
		if (null != mMemoryCache) {
			return mMemoryCache.get(url);
		}

		return null;
	}

	public void put(CacheableBitmapWrapper value) {
		if (null != mMemoryCache) {
			value.setCached(true);
			mMemoryCache.put(value.getUrl(), value);
		}
	}

	/**
	 * This method iterates through the cache and removes any Bitmap entries
	 * which are not currently being displayed. A good place to call this would
	 * be from {@link android.app.Application#onLowMemory()
	 * Application.onLowMemory()}.
	 */
	public void trimMemory() {
		if (null != mMemoryCache) {
			mMemoryCache.trimMemory();
		}
	}

	public static class Builder {

		static final float DEFAULT_CACHE_SIZE = 1f / 8f;
		static final float MAX_CACHE_SIZE = 0.75f;
		static final int MEGABYTE = 1024 * 1024;

		private boolean mValidMemoryCache;
		private boolean mValidDiskCache;

		private int mMemoryCacheSize;

		public Builder setMemoryCacheSize(int size) {
			mMemoryCacheSize = size;
			mValidMemoryCache = true;
			return this;
		}

		/**
		 * Sets the Memory Cache size to be the given percentage of heap size.
		 * This is capped at {@value #MAX_CACHE_SIZE} denoting 75% of the app
		 * heap size.
		 * 
		 * @param context - Context
		 * @param percentageOfHeap - percentage of heap size. Valid values are
		 *            0.0 <= x <= {@value #MAX_CACHE_SIZE}.
		 */
		public Builder setMemoryCacheSize(Context context, float percentageOfHeap) {
			int size = Math.round(MEGABYTE * getHeapSize(context) * Math.min(percentageOfHeap, MAX_CACHE_SIZE));
			return setMemoryCacheSize(size);
		}

		/**
		 * Initialise LruCache with default size of {@value #DEFAULT_CACHE_SIZE}
		 * of heap size.
		 * 
		 * @param context - context
		 */
		public Builder setMemoryCacheSize(Context context) {
			return setMemoryCacheSize(context, DEFAULT_CACHE_SIZE);
		}

		public BitmapLruCache build() {
			BitmapLruCache cache = new BitmapLruCache();

			if (mValidMemoryCache) {
				BitmapMemoryLruCache memoryCache = new BitmapMemoryLruCache(mMemoryCacheSize);
				cache.setMemoryLruCache(memoryCache);
			}

			return cache;
		}

		private static int getHeapSize(Context context) {
			return ((ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryClass();
		}
	}
}
