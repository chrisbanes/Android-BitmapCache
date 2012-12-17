/*******************************************************************************
 * Copyright 2011, 2012 Chris Banes.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package uk.co.senab.bitmapcache;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Process;
import android.util.Log;

import com.jakewharton.DiskLruCache;

/**
 * A cache which can be set to use multiple layers of caching for Bitmap objects
 * in an Android app. Instances are created via a {@link Builder} instance,
 * which can be used to alter the settings of the resulting cache.
 * 
 * <p>
 * Instances of this class should ideally be kept globally with the application,
 * for example in the {@link android.app.Application Application} object. You
 * should also use the bundled {@link CacheableImageView} wherever possible, as
 * the memory cache has a close relationship with it.
 * </p>
 * 
 * <p>
 * Clients can call {@link #get(String)} to retrieve a cached value from the
 * given Url. This will check all available caches for the value. There are also
 * the {@link #getFromDiskCache(String)} and {@link #getFromMemoryCache(String)}
 * which allow more granular access.
 * </p>
 * 
 * <p>
 * There are a number of update methods. {@link #put(String, InputStream)} and
 * {@link #put(String, InputStream, boolean)} are the preferred versions of the
 * method, as they allow 1:1 caching to disk of the original content. <br />
 * {@link #put(String, Bitmap)} and {@link #put(String, Bitmap, boolean)} should
 * only be used if you can't get access to the original InputStream.
 * </p>
 * 
 * @author Chris Banes
 */
public class BitmapLruCache {

	// The number of seconds after the last edit that the Disk Cache should be
	// flushed
	static final int DISK_CACHE_FLUSH_DELAY_SECS = 5;

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

	private final DiskLruCache mDiskCache;
	private final BitmapMemoryLruCache mMemoryCache;

	// Variables which are only used when the Disk Cache is enabled
	private final HashMap<String, ReentrantLock> mDiskCacheEditLocks;
	private final ScheduledThreadPoolExecutor mDiskCacheFlusherExecutor;
	private final DiskCacheFlushRunnable mDiskCacheFlusherRunnable;

	// Transient
	private ScheduledFuture<?> mDiskCacheFuture;

	protected BitmapLruCache(BitmapMemoryLruCache memoryCache, DiskLruCache diskCache) {
		mMemoryCache = memoryCache;
		mDiskCache = diskCache;

		if (null != diskCache) {
			mDiskCacheEditLocks = new HashMap<String, ReentrantLock>();
			mDiskCacheFlusherExecutor = new ScheduledThreadPoolExecutor(1);
			mDiskCacheFlusherRunnable = new DiskCacheFlushRunnable(diskCache);
		} else {
			mDiskCacheEditLocks = null;
			mDiskCacheFlusherExecutor = null;
			mDiskCacheFlusherRunnable = null;
		}
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
	 * main thread. If enabled, the result of this method will be cached in the
	 * memory cache.
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
						// decoded. Remove it and schedule a flush.
						mDiskCache.remove(transformUrlForDiskCacheKey(url));
						scheduleDiskCacheFlush();
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
	public CacheableBitmapWrapper put(final String url, final Bitmap bitmap) {
		return put(url, bitmap, true);
	}

	/**
	 * Advanced version of {@link #put(String, Bitmap)} which allows selective
	 * caching to the disk cache (if the disk cache is enabled).
	 * 
	 * @param url - String representing the URL of the image
	 * @param bitmap - Bitmap which has been decoded from {@code url}
	 * @param cacheToDiskIfEnabled - Cache to disk, if the disk cache is
	 *            enabled.
	 * @return CacheableBitmapWrapper which can be used to display the bitmap.
	 */
	public CacheableBitmapWrapper put(final String url, final Bitmap bitmap, final boolean cacheToDiskIfEnabled) {
		CacheableBitmapWrapper wrapper = new CacheableBitmapWrapper(url, bitmap);

		if (null != mDiskCache && cacheToDiskIfEnabled) {
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
				scheduleDiskCacheFlush();
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
	 * <li>If the memory cache is enabled, the Bitmap will be cached to memory</li>
	 * <li>If the disk cache is enabled, the contents of the file will be cached
	 * to disk.</li>
	 * </ul>
	 * 
	 * @param url - String representing the URL of the image
	 * @param inputStream - InputStream opened from {@code url}
	 * @return CacheableBitmapWrapper which can be used to display the bitmap.
	 */
	public CacheableBitmapWrapper put(final String url, final InputStream inputStream) {
		return put(url, inputStream, true);
	}

	/**
	 * Advanced version of {@link #put(String, InputStream)} which allows
	 * selective caching to the disk cache (if the disk cache is enabled).
	 * 
	 * @param url - String representing the URL of the image
	 * @param inputStream - InputStream opened from {@code url}
	 * @param cacheToDiskIfEnabled - Cache to disk, if the disk cache is
	 *            enabled.
	 * @return CacheableBitmapWrapper which can be used to display the bitmap.
	 */
	public CacheableBitmapWrapper put(final String url, final InputStream inputStream,
			final boolean cacheToDiskIfEnabled) {
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

				if (null != mDiskCache && cacheToDiskIfEnabled) {
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
						scheduleDiskCacheFlush();
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
	 * Removes the entry for {@code url} from all enabled caches, if it exists.
	 */
	public void remove(String url) {
		if (null != mMemoryCache) {
			mMemoryCache.remove(url);
		}

		if (null != mDiskCache) {
			try {
				mDiskCache.remove(transformUrlForDiskCacheKey(url));
				scheduleDiskCacheFlush();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
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

	private void scheduleDiskCacheFlush() {
		// If we already have a flush scheduled, cancel it
		if (null != mDiskCacheFuture) {
			mDiskCacheFuture.cancel(false);
		}

		// Schedule a flush
		mDiskCacheFuture = mDiskCacheFlusherExecutor.schedule(mDiskCacheFlusherRunnable, DISK_CACHE_FLUSH_DELAY_SECS,
				TimeUnit.SECONDS);
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
	public final static class Builder {

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
			BitmapMemoryLruCache memoryCache = null;
			DiskLruCache diskCache = null;

			if (isValidOptionsForMemoryCache()) {
				if (Constants.DEBUG) {
					Log.d("BitmapLruCache.Builder", "Creating Memory Cache");
				}
				memoryCache = new BitmapMemoryLruCache(mMemoryCacheMaxSize);
			}

			if (isValidOptionsForDiskCache()) {
				try {
					if (Constants.DEBUG) {
						Log.d("BitmapLruCache.Builder", "Creating Disk Cache");
					}
					diskCache = DiskLruCache.open(mDiskCacheLocation, 0, 1, mDiskCacheMaxSize);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			return new BitmapLruCache(memoryCache, diskCache);
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
			if (mDiskCacheEnabled) {
				if (null == mDiskCacheLocation) {
					Log.i(Constants.LOG_TAG,
							"Disk Cache has been enabled, but no location given. Please call setDiskCacheLocation(...)");
					return false;
				}

				return true;
			}
			return false;

		}

		private boolean isValidOptionsForMemoryCache() {
			return mMemoryCacheEnabled && mMemoryCacheMaxSize > 0;
		}
	}

	static final class DiskCacheFlushRunnable implements Runnable {

		private final DiskLruCache mDiskCache;

		public DiskCacheFlushRunnable(DiskLruCache cache) {
			mDiskCache = cache;
		}

		public void run() {
			// Make sure we're running with a background priority
			Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

			if (Constants.DEBUG) {
				Log.d(Constants.LOG_TAG, "Flushing Disk Cache");
			}
			try {
				mDiskCache.flush();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
