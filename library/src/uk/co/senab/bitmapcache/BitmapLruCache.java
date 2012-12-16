package uk.co.senab.bitmapcache;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.jakewharton.DiskLruCache;
import com.jakewharton.DiskLruCache.Editor;

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

	public void put(String url, InputStream inputStream) {
		// First we need to save the stream contents to a temporary file, so it
		// can be read multiple times
		File tmpFile = null;
		try {
			tmpFile = File.createTempFile("bitmapcache_", null);

			// Pipe InputStream to file
			// TODO Add Buffered Layer
			Util.pipe(inputStream, new FileOutputStream(tmpFile));

			// Close the original InputStream
			try {
				inputStream.close();
			} finally {
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		if (null != tmpFile) {
			if (null != mDiskCache) {
				try {
					Editor editor = mDiskCache.edit(url);
					if (null != editor) {
						Util.pipe(inputStream, editor.newOutputStream(0));
						editor.commit();
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			if (null != mMemoryCache) {
				putIntoMemoryCache(url, BitmapFactory.decodeFile(tmpFile.getAbsolutePath()));
			}

			// Finally, delete the temporary file
			tmpFile.delete();
		}
	}

	public void put(String url, Bitmap bitmap) {
		if (null != mDiskCache) {
			try {
				Editor editor = mDiskCache.edit(url);
				if (null != editor) {
					Util.saveBitmap(bitmap, editor.newOutputStream(0));
					editor.commit();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		if (null != mMemoryCache) {
			putIntoMemoryCache(url, bitmap);
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

	private void putIntoMemoryCache(String url, Bitmap bitmap) {
		if (null != bitmap) {
			CacheableBitmapWrapper wrapper = new CacheableBitmapWrapper(url, bitmap);
			wrapper.setCached(true);
			mMemoryCache.put(url, wrapper);
		} else {
			// TODO Add log here
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
