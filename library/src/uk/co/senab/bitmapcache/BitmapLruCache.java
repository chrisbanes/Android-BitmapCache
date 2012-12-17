package uk.co.senab.bitmapcache;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.jakewharton.DiskLruCache;

public class BitmapLruCache {

	private static String transformUrlForDiskCacheKey(String url) {
		return Util.md5(url);
	}

	private DiskLruCache mDiskCache;
	private BitmapMemoryLruCache mMemoryCache;

	/**
	 * Returns the value for {@code url}. This will check all caches currently
	 * enabled, meaning that this probably isn't safe to be called on the main
	 * thread.
	 * 
	 * @param url - String representing the URL of the image
	 */
	public CacheableBitmapWrapper get(String url) {
		CacheableBitmapWrapper result = null;

		// First try Memory Cache
		result = getFromMemoryCache(url);

		if (null == result) {
			// Memory Cache failed, so try Disk Cache
			result = getFromDiskCache(url);
		}

		return result;
	}

	/**
	 * Returns the value for {@code url} in the disk cache only. As this will
	 * read from the file system, this method is not safe to be called from the
	 * main thread.
	 * <p />
	 * Unless you have a specific requirement to only query the disk cache, you
	 * should call {@link #get(String)} instead.
	 * 
	 * @param url - String representing the URL of the image
	 * @return Value for {@code url} from disk cache, or {@code null} if the
	 *         disk cache is not enabled.
	 */
	public CacheableBitmapWrapper getFromDiskCache(final String url) {
		CacheableBitmapWrapper result = null;

		if (null != mDiskCache) {
			try {
				DiskLruCache.Snapshot snapshot = mDiskCache.get(transformUrlForDiskCacheKey(url));
				if (null != snapshot) {
					// Try and decode bitmap
					Bitmap bitmap = BitmapFactory.decodeStream(snapshot.getInputStream(0));

					if (null != bitmap) {
						result = new CacheableBitmapWrapper(url, bitmap);
						putIntoMemoryCache(result);
					} else {
						// If we get here, the file in the cache can't be
						// decoded. Remove it.
						mDiskCache.remove(transformUrlForDiskCacheKey(url));
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return result;
	}

	/**
	 * Returns the value for {@code url} in the memory cache only. This method
	 * is safe to be called from the main thread.
	 * <p />
	 * You should check the result of this method before starting a threaded
	 * call.
	 * 
	 * Unless you have a specific requirement to only query the disk cache, you
	 * should call {@link #get(String)} instead.
	 * 
	 * @param url - String representing the URL of the image
	 * @return Value for {@code url} from memory cache, or {@code null} if the
	 *         disk cache is not enabled.
	 */
	public CacheableBitmapWrapper getFromMemoryCache(final String url) {
		CacheableBitmapWrapper result = null;

		if (null != mMemoryCache) {
			result = mMemoryCache.get(url);

			// If we get a value, but it has a invalid bitmap, remove it
			if (null != result && !result.hasValidBitmap()) {
				mMemoryCache.remove(url);
				result = null;
			}
		}

		return result;
	}

	/**
	 * Caches {@code bitmap} for {@code url} into all enabled caches. If the
	 * disk cache is enabled, the bitmap will be compressed losslessly.
	 * 
	 * @param url - String representing the URL of the image
	 * @param bitmap - Bitmap which has been decoded from {@code url}
	 * @return CacheableBitmapWrapper which can be used to display the bitmap.
	 */
	public CacheableBitmapWrapper put(String url, Bitmap bitmap) {
		CacheableBitmapWrapper wrapper = new CacheableBitmapWrapper(url, bitmap);

		if (null != mDiskCache) {
			try {
				DiskLruCache.Editor editor = mDiskCache.edit(transformUrlForDiskCacheKey(url));
				if (null != editor) {
					Util.saveBitmap(bitmap, editor.newOutputStream(0));
					editor.commit();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		if (null != mMemoryCache) {
			putIntoMemoryCache(wrapper);
		}

		return wrapper;
	}

	/**
	 * Caches resulting bitmap from {@code inputStream} for {@code url} into all
	 * enabled caches. This version of the method should be preferred as it
	 * allows the original image contents to be cached, rather than a
	 * re-compressed version.
	 * <p />
	 * The contents of the InputStream will be copied to a temporary file, then
	 * the file will be decoded into a Bitmap. Providing the decode worked:
	 * <ul>
	 * <li>If the memory cache is enabled, the Bitmap will be cached</li>
	 * <li>If the disk cache is enabled, the contents of the file will be
	 * cached.</li>
	 * </ul>
	 * 
	 * @param url - String representing the URL of the image
	 * @param inputStream - InputStream opened from {@code url}
	 * @return CacheableBitmapWrapper which can be used to display the bitmap.
	 */
	public CacheableBitmapWrapper put(final String url, final InputStream inputStream) {
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

		CacheableBitmapWrapper wrapper = null;

		if (null != tmpFile) {
			wrapper = new CacheableBitmapWrapper(url, BitmapFactory.decodeFile(tmpFile.getAbsolutePath()));

			if (null != mDiskCache) {
				try {
					DiskLruCache.Editor editor = mDiskCache.edit(transformUrlForDiskCacheKey(url));
					if (null != editor) {
						Util.pipe(tmpFile, editor.newOutputStream(0));
						editor.commit();
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			if (null != mMemoryCache) {
				wrapper.setCached(true);
				mMemoryCache.put(wrapper.getUrl(), wrapper);
			}

			// Finally, delete the temporary file
			tmpFile.delete();
		}

		return wrapper;
	}

	/**
	 * This method iterates through the memory cache (if enabled) and removes
	 * any entries which are not currently being displayed. A good place to call
	 * this would be from {@link android.app.Application#onLowMemory()
	 * Application.onLowMemory()}.
	 */
	public void trimMemory() {
		if (null != mMemoryCache) {
			mMemoryCache.trimMemory();
		}
	}

	void setDiskLruCache(DiskLruCache cache) {
		mDiskCache = cache;
	}

	void setMemoryLruCache(BitmapMemoryLruCache cache) {
		mMemoryCache = cache;
	}

	private void putIntoMemoryCache(CacheableBitmapWrapper wrapper) {
		wrapper.setCached(true);
		mMemoryCache.put(wrapper.getUrl(), wrapper);
	}

	public static class Builder {

		static final int MEGABYTE = 1024 * 1024;

		static final float DEFAULT_MEMORY_CACHE_HEAP_RATIO = 1f / 8f;
		static final int DEFAULT_DISK_CACHE_MAX_SIZE = MEGABYTE * 10;

		static final float MAX_MEMORY_CACHE_HEAP_RATIO = 0.75f;

		private static int getHeapSize(Context context) {
			return ((ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryClass();
		}

		private boolean mDiskCacheEnabled;
		private File mDiskCacheLocation;
		private long mDiskCacheMaxSize;

		private boolean mMemoryCacheEnabled;
		private int mMemoryCacheMaxSize;

		public Builder() {
			mDiskCacheMaxSize = DEFAULT_DISK_CACHE_MAX_SIZE;

			// Memory Cache is enabled by default
			mMemoryCacheEnabled = true;
		}

		public BitmapLruCache build() {
			BitmapLruCache cache = new BitmapLruCache();

			if (isValidOptionsForMemoryCache()) {
				Log.d("BitmapLruCache.Builder", "Creating Memory Cache");
				BitmapMemoryLruCache memoryCache = new BitmapMemoryLruCache(mMemoryCacheMaxSize);
				cache.setMemoryLruCache(memoryCache);
			}

			if (isValidOptionsForDiskCache()) {
				try {
					DiskLruCache diskCache = DiskLruCache.open(mDiskCacheLocation, 0, 1, mDiskCacheMaxSize);
					cache.setDiskLruCache(diskCache);
					Log.d("BitmapLruCache.Builder", "Created Memory Cache");
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			return cache;
		}

		public Builder setDiskCacheEnabled(boolean enabled) {
			mDiskCacheEnabled = enabled;
			return this;
		}

		public Builder setDiskCacheLocation(File location) {
			mDiskCacheLocation = location;
			return this;
		}

		public Builder setDiskCacheMaxSize(long maxSize) {
			mDiskCacheMaxSize = maxSize;
			return this;
		}

		public Builder setMemoryCacheEnabled(boolean enabled) {
			mMemoryCacheEnabled = enabled;
			return this;
		}

		public Builder setMemoryCacheMaxSize(int size) {
			mMemoryCacheMaxSize = size;
			return this;
		}

		/**
		 * Initialise LruCache with default size of
		 * {@value #DEFAULT_MEMORY_CACHE_HEAP_RATIO} of heap size.
		 * 
		 * @param context - context
		 */
		public Builder setMemoryCacheMaxSizeUsingHeapSize(Context context) {
			return setMemoryCacheMaxSizeUsingHeapSize(context, DEFAULT_MEMORY_CACHE_HEAP_RATIO);
		}

		/**
		 * Sets the Memory Cache size to be the given percentage of heap size.
		 * This is capped at {@value #MAX_MEMORY_CACHE_HEAP_RATIO} denoting 75%
		 * of the app heap size.
		 * 
		 * @param context - Context
		 * @param percentageOfHeap - percentage of heap size. Valid values are
		 *            0.0 <= x <= {@value #MAX_MEMORY_CACHE_HEAP_RATIO}.
		 */
		public Builder setMemoryCacheMaxSizeUsingHeapSize(Context context, float percentageOfHeap) {
			int size = Math.round(MEGABYTE * getHeapSize(context)
					* Math.min(percentageOfHeap, MAX_MEMORY_CACHE_HEAP_RATIO));
			return setMemoryCacheMaxSize(size);
		}

		private boolean isValidOptionsForDiskCache() {
			return mDiskCacheEnabled && null != mDiskCacheLocation;

		}

		private boolean isValidOptionsForMemoryCache() {
			return mMemoryCacheEnabled && mMemoryCacheMaxSize > 0;
		}
	}
}
