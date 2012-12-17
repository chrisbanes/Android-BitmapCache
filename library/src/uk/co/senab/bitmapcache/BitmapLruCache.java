package uk.co.senab.bitmapcache;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.concurrent.locks.ReentrantLock;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.jakewharton.DiskLruCache;

public class BitmapLruCache {

	/**
	 * The disk cache only accepts a reduced range of characters for the key
	 * values. This method transforms the {@code url} into something accepted
	 * from {@link DiskLruCache}. Currently we simply return a MD5 hash of the
	 * url.
	 * 
	 * @param url - Key to be transformed
	 * @return key which can be used for the disk cache
	 */
	private static String transformUrlForDiskCacheKey(String url) {
		return Util.md5(url);
	}

	private DiskLruCache mDiskCache;
	private BitmapMemoryLruCache mMemoryCache;

	private final HashMap<String, ReentrantLock> mDiskCacheEditLocks;

	protected BitmapLruCache() {
		mDiskCacheEditLocks = new HashMap<String, ReentrantLock>();
	}

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
			final ReentrantLock lock = getLockForDiskCacheEdit(url);
			lock.lock();
			try {
				DiskLruCache.Editor editor = mDiskCache.edit(transformUrlForDiskCacheKey(url));
				Util.saveBitmap(bitmap, editor.newOutputStream(0));
				editor.commit();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				lock.unlock();
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
			Util.copy(inputStream, tmpFile);

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
			// Try and decode File
			Bitmap bitmap = BitmapFactory.decodeFile(tmpFile.getAbsolutePath());

			if (null != bitmap) {
				wrapper = new CacheableBitmapWrapper(url, bitmap);

				if (null != mDiskCache) {
					final ReentrantLock lock = getLockForDiskCacheEdit(url);
					lock.lock();
					try {
						DiskLruCache.Editor editor = mDiskCache.edit(transformUrlForDiskCacheKey(url));
						Util.copy(tmpFile, editor.newOutputStream(0));
						editor.commit();
					} catch (IOException e) {
						e.printStackTrace();
					} finally {
						lock.unlock();
					}
				}

				if (null != mMemoryCache) {
					wrapper.setCached(true);
					mMemoryCache.put(wrapper.getUrl(), wrapper);
				}

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

	private ReentrantLock getLockForDiskCacheEdit(String url) {
		synchronized (mDiskCacheEditLocks) {
			ReentrantLock lock = mDiskCacheEditLocks.get(url);
			if (null == lock) {
				lock = new ReentrantLock();
				mDiskCacheEditLocks.put(url, lock);
			}
			return lock;
		}
	}

	private void putIntoMemoryCache(CacheableBitmapWrapper wrapper) {
		wrapper.setCached(true);
		mMemoryCache.put(wrapper.getUrl(), wrapper);
	}

	/**
	 * Builder class for {link {@link BitmapLruCache}. An example call:
	 * 
	 * <pre>
	 * BitmapLruCache.Builder builder = new BitmapLruCache.Builder();
	 * builder.setMemoryCacheEnabled(true).setMemoryCacheMaxSizeUsingHeapSize(this);
	 * builder.setDiskCacheEnabled(true).setDiskCacheLocation(...);
	 * 
	 * BitmapLruCache cache = builder.build();
	 * </pre>
	 * 
	 * @author Chris Banes
	 */
	public static class Builder {

		static final int MEGABYTE = 1024 * 1024;

		static final float DEFAULT_MEMORY_CACHE_HEAP_RATIO = 1f / 8f;
		static final float MAX_MEMORY_CACHE_HEAP_RATIO = 0.75f;

		static final int DEFAULT_DISK_CACHE_MAX_SIZE_MB = 10;
		static final int DEFAULT_MEM_CACHE_MAX_SIZE_MB = 3;

		// Only used for Javadoc
		static final float DEFAULT_MEMORY_CACHE_HEAP_PERCENTAGE = DEFAULT_MEMORY_CACHE_HEAP_RATIO * 100;
		static final float MAX_MEMORY_CACHE_HEAP_PERCENTAGE = MAX_MEMORY_CACHE_HEAP_RATIO * 100;

		private static long getHeapSize() {
			return Runtime.getRuntime().maxMemory();
		}

		private boolean mDiskCacheEnabled;
		private File mDiskCacheLocation;
		private long mDiskCacheMaxSize;

		private boolean mMemoryCacheEnabled;
		private int mMemoryCacheMaxSize;

		public Builder() {
			// Disk Cache is disabled by default, but it's default size is set
			mDiskCacheMaxSize = DEFAULT_DISK_CACHE_MAX_SIZE_MB * MEGABYTE;

			// Memory Cache is enabled by default, with a small maximum size
			mMemoryCacheEnabled = true;
			mMemoryCacheMaxSize = DEFAULT_MEM_CACHE_MAX_SIZE_MB * MEGABYTE;
		}

		/**
		 * @return A new {@link BitmapLruCache} created with the arguments
		 *         supplied to this builder.
		 */
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

		/**
		 * Set whether the Disk Cache should be enabled. Defaults to
		 * {@code false}.
		 * 
		 * @return This Builder object to allow for chaining of calls to set
		 *         methods.
		 */
		public Builder setDiskCacheEnabled(boolean enabled) {
			mDiskCacheEnabled = enabled;
			return this;
		}

		/**
		 * Set the Disk Cache location. This location should be read-writeable.
		 * 
		 * @return This Builder object to allow for chaining of calls to set
		 *         methods.
		 */
		public Builder setDiskCacheLocation(File location) {
			mDiskCacheLocation = location;
			return this;
		}

		/**
		 * Set the maximum number of bytes the Disk Cache should use to store
		 * values. Defaults to {@value #DEFAULT_DISK_CACHE_MAX_SIZE_MB}MB.
		 * 
		 * @return This Builder object to allow for chaining of calls to set
		 *         methods.
		 */
		public Builder setDiskCacheMaxSize(long maxSize) {
			mDiskCacheMaxSize = maxSize;
			return this;
		}

		/**
		 * Set whether the Memory Cache should be enabled. Defaults to
		 * {@code true}.
		 * 
		 * @return This Builder object to allow for chaining of calls to set
		 *         methods.
		 */
		public Builder setMemoryCacheEnabled(boolean enabled) {
			mMemoryCacheEnabled = enabled;
			return this;
		}

		/**
		 * Set the maximum number of bytes the Memory Cache should use to store
		 * values. Defaults to {@value #DEFAULT_MEM_CACHE_MAX_SIZE_MB}MB.
		 * 
		 * @return This Builder object to allow for chaining of calls to set
		 *         methods.
		 */
		public Builder setMemoryCacheMaxSize(int size) {
			mMemoryCacheMaxSize = size;
			return this;
		}

		/**
		 * Sets the Memory Cache maximum size to be the default value of
		 * {@value #DEFAULT_MEMORY_CACHE_HEAP_PERCENTAGE}% of heap size.
		 * 
		 * @param context - Context, needed to retrieve heap size.
		 */
		public Builder setMemoryCacheMaxSizeUsingHeapSize() {
			return setMemoryCacheMaxSizeUsingHeapSize(DEFAULT_MEMORY_CACHE_HEAP_RATIO);
		}

		/**
		 * Sets the Memory Cache maximum size to be the given percentage of heap
		 * size. This is capped at {@value #MAX_MEMORY_CACHE_HEAP_PERCENTAGE}%
		 * of the app heap size.
		 * 
		 * @param context - Context, needed to retrieve heap size.
		 * @param percentageOfHeap - percentage of heap size. Valid values are
		 *            0.0 <= x <= {@value #MAX_MEMORY_CACHE_HEAP_RATIO}.
		 */
		public Builder setMemoryCacheMaxSizeUsingHeapSize(float percentageOfHeap) {
			int size = (int) Math.round(getHeapSize() * Math.min(percentageOfHeap, MAX_MEMORY_CACHE_HEAP_RATIO));
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
