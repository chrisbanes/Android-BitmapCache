/*******************************************************************************
 * Copyright (c) 2013 Chris Banes.
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
 ******************************************************************************/

package uk.co.senab.bitmapcache;

import com.jakewharton.disklrucache.DiskLruCache;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Looper;
import android.os.Process;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A cache which can be set to use multiple layers of caching for Bitmap objects in an Android app.
 * Instances are created via a {@link Builder} instance, which can be used to alter the settings of
 * the resulting cache.
 *
 * <p> Instances of this class should ideally be kept globally with the application, for example in
 * the {@link android.app.Application Application} object. You should also use the bundled {@link
 * CacheableImageView} wherever possible, as the memory cache has a closeStream relationship with it.
 * </p>
 *
 * <p> Clients can call {@link #get(String)} to retrieve a cached value from the given Url. This
 * will check all available caches for the value. There are also the {@link
 * #getFromDiskCache(String, android.graphics.BitmapFactory.Options)} and {@link
 * #getFromMemoryCache(String)} which allow more granular access. </p>
 *
 * <p> There are a number of update methods. {@link #put(String, InputStream)} and {@link
 * #put(String, InputStream)} are the preferred versions of the method, as they allow 1:1 caching to
 * disk of the original content. <br /> {@link #put(String, Bitmap)}} should only be used if you
 * can't get access to the original InputStream. </p>
 *
 * @author Chris Banes
 */
public class BitmapLruCache {

    /**
     * The recycle policy controls if the {@link android.graphics.Bitmap#recycle()} is automatically
     * called, when it is no longer being used. To set this, use the {@link
     * Builder#setRecyclePolicy(uk.co.senab.bitmapcache.BitmapLruCache.RecyclePolicy)
     * Builder.setRecyclePolicy()} method.
     */
    public static enum RecyclePolicy {
        /**
         * The Bitmap is never recycled automatically.
         */
        DISABLED,

        /**
         * The Bitmap is only automatically recycled if running on a device API v10 or earlier.
         */
        PRE_HONEYCOMB_ONLY,

        /**
         * The Bitmap is always recycled when no longer being used. This is the default.
         */
        ALWAYS;

        boolean canInBitmap() {
            switch (this) {
                case PRE_HONEYCOMB_ONLY:
                case DISABLED:
                    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB;
            }
            return false;
        }

        boolean canRecycle() {
            switch (this) {
                case DISABLED:
                    return false;
                case PRE_HONEYCOMB_ONLY:
                    return Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB;
                case ALWAYS:
                    return true;
            }

            return false;
        }
    }

    // The number of seconds after the last edit that the Disk Cache should be
    // flushed
    static final int DISK_CACHE_FLUSH_DELAY_SECS = 5;

    /**
     * @throws IllegalStateException if the calling thread is the main/UI thread.
     */
    private static void checkNotOnMainThread() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException(
                    "This method should not be called from the main/UI thread.");
        }
    }

    /**
     * The disk cache only accepts a reduced range of characters for the key values. This method
     * transforms the {@code url} into something accepted from {@link DiskLruCache}. Currently we
     * simply return a MD5 hash of the url.
     *
     * @param url - Key to be transformed
     * @return key which can be used for the disk cache
     */
    private static String transformUrlForDiskCacheKey(String url) {
        return Md5.encode(url);
    }

    private File mTempDir;

    private Resources mResources;

    /**
     * Memory Cache Variables
     */
    private BitmapMemoryLruCache mMemoryCache;

    private RecyclePolicy mRecyclePolicy;

    /**
     * Disk Cache Variables
     */
    private DiskLruCache mDiskCache;

    // Variables which are only used when the Disk Cache is enabled
    private HashMap<String, ReentrantLock> mDiskCacheEditLocks;

    private ScheduledThreadPoolExecutor mDiskCacheFlusherExecutor;

    private DiskCacheFlushRunnable mDiskCacheFlusherRunnable;

    // Transient
    private ScheduledFuture<?> mDiskCacheFuture;

    BitmapLruCache(Context context) {
        if (null != context) {
            // Make sure we have the application context
            context = context.getApplicationContext();

            mTempDir = context.getCacheDir();
            mResources = context.getResources();
        }
    }

    /**
     * Returns whether any of the enabled caches contain the specified URL. <p/> If you have the
     * disk cache enabled, you should not call this method from main/UI thread.
     *
     * @param url the URL to search for.
     * @return {@code true} if any of the caches contain the specified URL, {@code false}
     *         otherwise.
     */
    public boolean contains(String url) {
        return containsInMemoryCache(url) || containsInDiskCache(url);
    }

    /**
     * Returns whether the Disk Cache contains the specified URL. You should not call this method
     * from main/UI thread.
     *
     * @param url the URL to search for.
     * @return {@code true} if the Disk Cache is enabled and contains the specified URL, {@code
     *         false} otherwise.
     */
    public boolean containsInDiskCache(String url) {
        if (null != mDiskCache) {
            checkNotOnMainThread();

            try {
                return null != mDiskCache.get(transformUrlForDiskCacheKey(url));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return false;
    }

    /**
     * Returns whether the Memory Cache contains the specified URL. This method is safe to be called
     * from the main thread.
     *
     * @param url the URL to search for.
     * @return {@code true} if the Memory Cache is enabled and contains the specified URL, {@code
     *         false} otherwise.
     */
    public boolean containsInMemoryCache(String url) {
        return null != mMemoryCache && null != mMemoryCache.get(url);
    }

    /**
     * Returns the value for {@code url}. This will check all caches currently enabled. <p/> If you
     * have the disk cache enabled, you should not call this method from main/UI thread.
     *
     * @param url - String representing the URL of the image
     */
    public CacheableBitmapDrawable get(String url) {
        return get(url, null);
    }

    /**
     * Returns the value for {@code url}. This will check all caches currently enabled. <p/> If you
     * have the disk cache enabled, you should not call this method from main/UI thread.
     *
     * @param url        - String representing the URL of the image
     * @param decodeOpts - Options used for decoding the contents from the disk cache only.
     */
    public CacheableBitmapDrawable get(String url, BitmapFactory.Options decodeOpts) {
        CacheableBitmapDrawable result;

        // First try Memory Cache
        result = getFromMemoryCache(url);

        if (null == result) {
            // Memory Cache failed, so try Disk Cache
            result = getFromDiskCache(url, decodeOpts);
        }

        return result;
    }

    /**
     * Returns the value for {@code url} in the disk cache only. You should not call this method
     * from main/UI thread. <p/> If enabled, the result of this method will be cached in the memory
     * cache. <p /> Unless you have a specific requirement to only query the disk cache, you should
     * call {@link #get(String)} instead.
     *
     * @param url        - String representing the URL of the image
     * @param decodeOpts - Options used for decoding the contents from the disk cache.
     * @return Value for {@code url} from disk cache, or {@code null} if the disk cache is not
     *         enabled.
     */
    public CacheableBitmapDrawable getFromDiskCache(final String url,
            final BitmapFactory.Options decodeOpts) {
        CacheableBitmapDrawable result = null;

        if (null != mDiskCache) {
            checkNotOnMainThread();

            try {
                final String key = transformUrlForDiskCacheKey(url);
                // Try and decode bitmap
                result = decodeBitmap(new SnapshotInputStreamProvider(key), url, decodeOpts);

                if (null != result) {
                    if (null != mMemoryCache) {
                        mMemoryCache.put(result);
                    }
                } else {
                    // If we get here, the file in the cache can't be
                    // decoded. Remove it and schedule a flush.
                    mDiskCache.remove(key);
                    scheduleDiskCacheFlush();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return result;
    }

    /**
     * Returns the value for {@code url} in the memory cache only. This method is safe to be called
     * from the main thread. <p /> You should check the result of this method before starting a
     * threaded call.
     *
     * @param url - String representing the URL of the image
     * @return Value for {@code url} from memory cache, or {@code null} if the disk cache is not
     *         enabled.
     */
    public CacheableBitmapDrawable getFromMemoryCache(final String url) {
        CacheableBitmapDrawable result = null;

        if (null != mMemoryCache) {
            synchronized (mMemoryCache) {
                result = mMemoryCache.get(url);

                // If we get a value, but it has a invalid bitmap, remove it
                if (null != result && !result.isBitmapValid()) {
                    mMemoryCache.remove(url);
                    result = null;
                }
            }
        }

        return result;
    }

    /**
     * @return true if the Disk Cache is enabled.
     */
    public boolean isDiskCacheEnabled() {
        return null != mDiskCache;
    }

    /**
     * @return true if the Memory Cache is enabled.
     */
    public boolean isMemoryCacheEnabled() {
        return null != mMemoryCache;
    }

    /**
     * Caches {@code bitmap} for {@code url} into all enabled caches. If the disk cache is enabled,
     * the bitmap will be compressed losslessly. <p/> If you have the disk cache enabled, you should
     * not call this method from main/UI thread.
     *
     * @param url    - String representing the URL of the image.
     * @param bitmap - Bitmap which has been decoded from {@code url}.
     * @return CacheableBitmapDrawable which can be used to display the bitmap.
     */
    public CacheableBitmapDrawable put(final String url, final Bitmap bitmap) {
        return put(url, bitmap, Bitmap.CompressFormat.PNG, 100);
    }

    /**
     * Caches {@code bitmap} for {@code url} into all enabled caches. If the disk cache is enabled,
     * the bitmap will be compressed with the settings you provide.
     * <p/> If you have the disk cache enabled, you should not call this method from main/UI thread.
     *
     * @param url    - String representing the URL of the image.
     * @param bitmap - Bitmap which has been decoded from {@code url}.
     * @param compressFormat - Compression Format to use
     * @param compressQuality  - Level of compression to use
     * @return CacheableBitmapDrawable which can be used to display the bitmap.
     *
     * @see Bitmap#compress(Bitmap.CompressFormat, int, OutputStream)
     */
    public CacheableBitmapDrawable put(final String url, final Bitmap bitmap,
            Bitmap.CompressFormat compressFormat, int compressQuality) {

        CacheableBitmapDrawable d = new CacheableBitmapDrawable(url, mResources, bitmap,
                mRecyclePolicy, CacheableBitmapDrawable.SOURCE_UNKNOWN);

        if (null != mMemoryCache) {
            mMemoryCache.put(d);
        }

        if (null != mDiskCache) {
            checkNotOnMainThread();

            final String key = transformUrlForDiskCacheKey(url);
            final ReentrantLock lock = getLockForDiskCacheEdit(key);
            lock.lock();

            OutputStream os = null;

            try {
                DiskLruCache.Editor editor = mDiskCache.edit(key);
                os = editor.newOutputStream(0);
                bitmap.compress(compressFormat, compressQuality, os);
                os.flush();
                editor.commit();
            } catch (IOException e) {
                Log.e(Constants.LOG_TAG, "Error while writing to disk cache", e);
            } finally {
                IoUtils.closeStream(os);
                lock.unlock();
                scheduleDiskCacheFlush();
            }
        }

        return d;
    }

    /**
     * Caches resulting bitmap from {@code inputStream} for {@code url} into all enabled caches.
     * This version of the method should be preferred as it allows the original image contents to be
     * cached, rather than a re-compressed version. <p /> The contents of the InputStream will be
     * copied to a temporary file, then the file will be decoded into a Bitmap. Providing the decode
     * worked: <ul> <li>If the memory cache is enabled, the decoded Bitmap will be cached to
     * memory.</li> <li>If the disk cache is enabled, the contents of the original stream will be
     * cached to disk.</li> </ul> <p/> You should not call this method from the main/UI thread.
     *
     * @param url         - String representing the URL of the image
     * @param inputStream - InputStream opened from {@code url}
     * @return CacheableBitmapDrawable which can be used to display the bitmap.
     */
    public CacheableBitmapDrawable put(final String url, final InputStream inputStream) {
        return put(url, inputStream, null);
    }

    /**
     * Caches resulting bitmap from {@code inputStream} for {@code url} into all enabled caches.
     * This version of the method should be preferred as it allows the original image contents to be
     * cached, rather than a re-compressed version. <p /> The contents of the InputStream will be
     * copied to a temporary file, then the file will be decoded into a Bitmap, using the optional
     * <code>decodeOpts</code>. Providing the decode worked: <ul> <li>If the memory cache is
     * enabled, the decoded Bitmap will be cached to memory.</li> <li>If the disk cache is enabled,
     * the contents of the original stream will be cached to disk.</li> </ul> <p/> You should not
     * call this method from the main/UI thread.
     *
     * @param url         - String representing the URL of the image
     * @param inputStream - InputStream opened from {@code url}
     * @param decodeOpts  - Options used for decoding. This does not affect what is cached in the
     *                    disk cache (if enabled).
     * @return CacheableBitmapDrawable which can be used to display the bitmap.
     */
    public CacheableBitmapDrawable put(final String url, final InputStream inputStream,
            final BitmapFactory.Options decodeOpts) {
        checkNotOnMainThread();

        // First we need to save the stream contents to a temporary file, so it
        // can be read multiple times
        File tmpFile = null;
        try {
            tmpFile = File.createTempFile("bitmapcache_", null, mTempDir);

            // Pipe InputStream to file
            IoUtils.copy(inputStream, tmpFile);
        } catch (IOException e) {
            Log.e(Constants.LOG_TAG, "Error writing to saving stream to temp file: " + url, e);
        }

        CacheableBitmapDrawable d = null;

        if (null != tmpFile) {
            // Try and decode File
            d = decodeBitmap(new FileInputStreamProvider(tmpFile), url, decodeOpts);

            if (d != null) {
                if (null != mMemoryCache) {
                    d.setCached(true);
                    mMemoryCache.put(d.getUrl(), d);
                }

                if (null != mDiskCache) {
                    final String key = transformUrlForDiskCacheKey(url);
                    final ReentrantLock lock = getLockForDiskCacheEdit(url);
                    lock.lock();

                    try {
                        DiskLruCache.Editor editor = mDiskCache.edit(key);
                        IoUtils.copy(tmpFile, editor.newOutputStream(0));
                        editor.commit();
                    } catch (IOException e) {
                        Log.e(Constants.LOG_TAG, "Error writing to disk cache. URL: " + url, e);
                    } finally {
                        lock.unlock();
                        scheduleDiskCacheFlush();
                    }
                }
            }

            // Finally, delete the temporary file
            tmpFile.delete();
        }

        return d;
    }

    /**
     * Removes the entry for {@code url} from all enabled caches, if it exists. <p/> If you have the
     * disk cache enabled, you should not call this method from main/UI thread.
     */
    public void remove(String url) {
        if (null != mMemoryCache) {
            mMemoryCache.remove(url);
        }

        if (null != mDiskCache) {
            checkNotOnMainThread();

            try {
                mDiskCache.remove(transformUrlForDiskCacheKey(url));
                scheduleDiskCacheFlush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * This method iterates through the memory cache (if enabled) and removes any entries which are
     * not currently being displayed. A good place to call this would be from {@link
     * android.app.Application#onLowMemory() Application.onLowMemory()}.
     */
    public void trimMemory() {
        if (null != mMemoryCache) {
            mMemoryCache.trimMemory();
        }
    }

    synchronized void setDiskCache(DiskLruCache diskCache) {
        mDiskCache = diskCache;

        if (null != diskCache) {
            mDiskCacheEditLocks = new HashMap<String, ReentrantLock>();
            mDiskCacheFlusherExecutor = new ScheduledThreadPoolExecutor(1);
            mDiskCacheFlusherRunnable = new DiskCacheFlushRunnable(diskCache);
        }
    }

    void setMemoryCache(BitmapMemoryLruCache memoryCache) {
        mMemoryCache = memoryCache;
        mRecyclePolicy = memoryCache.getRecyclePolicy();
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

    private void scheduleDiskCacheFlush() {
        // If we already have a flush scheduled, cancel it
        if (null != mDiskCacheFuture) {
            mDiskCacheFuture.cancel(false);
        }

        // Schedule a flush
        mDiskCacheFuture = mDiskCacheFlusherExecutor
                .schedule(mDiskCacheFlusherRunnable, DISK_CACHE_FLUSH_DELAY_SECS,
                        TimeUnit.SECONDS);
    }

    private CacheableBitmapDrawable decodeBitmap(InputStreamProvider ip, String url,
            BitmapFactory.Options opts) {

        Bitmap bm = null;
        InputStream is = null;
        int source = CacheableBitmapDrawable.SOURCE_NEW;

        try {
            if (mRecyclePolicy.canInBitmap()) {
                // Create an options instance if we haven't been provided with one
                if (opts == null) {
                    opts = new BitmapFactory.Options();
                }

                if (opts.inSampleSize <= 1) {
                    opts.inSampleSize = 1;

                    if (addInBitmapOptions(ip, opts)) {
                        source = CacheableBitmapDrawable.SOURCE_INBITMAP;
                    }
                }
            }

            // Get InputStream for actual decode
            is = ip.getInputStream();
            // Decode stream
            bm = BitmapFactory.decodeStream(is, null, opts);
        } catch (Exception e) {
            Log.e(Constants.LOG_TAG, "Unable to decode stream",  e);
        } finally {
            IoUtils.closeStream(is);
        }

        if (bm != null) {
            return new CacheableBitmapDrawable(url, mResources, bm, mRecyclePolicy, source);
        }
        return null;
    }

    private boolean addInBitmapOptions(InputStreamProvider ip, BitmapFactory.Options opts) {
        // Create InputStream for decoding the bounds
        final InputStream is = ip.getInputStream();
        // Decode the bounds so we know what size Bitmap to look for
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(is, null, opts);
        IoUtils.closeStream(is);

        // Turn off just decoding bounds
        opts.inJustDecodeBounds = false;
        // Make sure the decoded file is mutable
        opts.inMutable = true;

        // Try and find Bitmap to use for inBitmap
        Bitmap reusableBm = mMemoryCache.getBitmapFromRemoved(opts.outWidth, opts.outHeight);
        if (reusableBm != null) {
            if (Constants.DEBUG) {
                Log.i(Constants.LOG_TAG, "Using inBitmap");
            }
            SDK11.addInBitmapOption(opts, reusableBm);
            return true;
        }

        return false;
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

        static final RecyclePolicy DEFAULT_RECYCLE_POLICY = RecyclePolicy.PRE_HONEYCOMB_ONLY;

        // Only used for Javadoc
        static final float DEFAULT_MEMORY_CACHE_HEAP_PERCENTAGE = DEFAULT_MEMORY_CACHE_HEAP_RATIO
                * 100;

        static final float MAX_MEMORY_CACHE_HEAP_PERCENTAGE = MAX_MEMORY_CACHE_HEAP_RATIO * 100;

        private static long getHeapSize() {
            return Runtime.getRuntime().maxMemory();
        }

        private Context mContext;

        private boolean mDiskCacheEnabled;

        private File mDiskCacheLocation;

        private long mDiskCacheMaxSize;

        private boolean mMemoryCacheEnabled;

        private int mMemoryCacheMaxSize;

        private RecyclePolicy mRecyclePolicy;

        /**
         * @deprecated You should now use {@link Builder(Context)}. This is so that we can reliably
         *             set up correctly.
         */
        public Builder() {
            this(null);
        }

        public Builder(Context context) {
            mContext = context;

            // Disk Cache is disabled by default, but it's default size is set
            mDiskCacheMaxSize = DEFAULT_DISK_CACHE_MAX_SIZE_MB * MEGABYTE;

            // Memory Cache is enabled by default, with a small maximum size
            mMemoryCacheEnabled = true;
            mMemoryCacheMaxSize = DEFAULT_MEM_CACHE_MAX_SIZE_MB * MEGABYTE;
            mRecyclePolicy = DEFAULT_RECYCLE_POLICY;
        }

        /**
         * @return A new {@link BitmapLruCache} created with the arguments supplied to this
         *         builder.
         */
        public BitmapLruCache build() {
            final BitmapLruCache cache = new BitmapLruCache(mContext);

            if (isValidOptionsForMemoryCache()) {
                if (Constants.DEBUG) {
                    Log.d("BitmapLruCache.Builder", "Creating Memory Cache");
                }
                cache.setMemoryCache(new BitmapMemoryLruCache(mMemoryCacheMaxSize, mRecyclePolicy));
            }

            if (isValidOptionsForDiskCache()) {
                new AsyncTask<Void, Void, DiskLruCache>() {

                    @Override
                    protected DiskLruCache doInBackground(Void... params) {
                        try {
                            return DiskLruCache.open(mDiskCacheLocation, 0, 1, mDiskCacheMaxSize);
                        } catch (IOException e) {
                            e.printStackTrace();
                            return null;
                        }
                    }

                    @Override
                    protected void onPostExecute(DiskLruCache result) {
                        cache.setDiskCache(result);
                    }

                }.execute();
            }

            return cache;
        }

        /**
         * Set whether the Disk Cache should be enabled. Defaults to {@code false}.
         *
         * @return This Builder object to allow for chaining of calls to set methods.
         */
        public Builder setDiskCacheEnabled(boolean enabled) {
            mDiskCacheEnabled = enabled;
            return this;
        }

        /**
         * Set the Disk Cache location. This location should be read-writeable.
         *
         * @return This Builder object to allow for chaining of calls to set methods.
         */
        public Builder setDiskCacheLocation(File location) {
            mDiskCacheLocation = location;
            return this;
        }

        /**
         * Set the maximum number of bytes the Disk Cache should use to store values. Defaults to
         * {@value #DEFAULT_DISK_CACHE_MAX_SIZE_MB}MB.
         *
         * @return This Builder object to allow for chaining of calls to set methods.
         */
        public Builder setDiskCacheMaxSize(long maxSize) {
            mDiskCacheMaxSize = maxSize;
            return this;
        }

        /**
         * Set whether the Memory Cache should be enabled. Defaults to {@code true}.
         *
         * @return This Builder object to allow for chaining of calls to set methods.
         */
        public Builder setMemoryCacheEnabled(boolean enabled) {
            mMemoryCacheEnabled = enabled;
            return this;
        }

        /**
         * Set the maximum number of bytes the Memory Cache should use to store values. Defaults to
         * {@value #DEFAULT_MEM_CACHE_MAX_SIZE_MB}MB.
         *
         * @return This Builder object to allow for chaining of calls to set methods.
         */
        public Builder setMemoryCacheMaxSize(int size) {
            mMemoryCacheMaxSize = size;
            return this;
        }

        /**
         * Sets the Memory Cache maximum size to be the default value of {@value
         * #DEFAULT_MEMORY_CACHE_HEAP_PERCENTAGE}% of heap size.
         *
         * @return This Builder object to allow for chaining of calls to set methods.
         */
        public Builder setMemoryCacheMaxSizeUsingHeapSize() {
            return setMemoryCacheMaxSizeUsingHeapSize(DEFAULT_MEMORY_CACHE_HEAP_RATIO);
        }

        /**
         * Sets the Memory Cache maximum size to be the given percentage of heap size. This is
         * capped at {@value #MAX_MEMORY_CACHE_HEAP_PERCENTAGE}% of the app heap size.
         *
         * @param percentageOfHeap - percentage of heap size. Valid values are 0.0 <= x <= {@value
         *                         #MAX_MEMORY_CACHE_HEAP_RATIO}.
         * @return This Builder object to allow for chaining of calls to set methods.
         */
        public Builder setMemoryCacheMaxSizeUsingHeapSize(float percentageOfHeap) {
            int size = Math
                    .round(getHeapSize() * Math.min(percentageOfHeap, MAX_MEMORY_CACHE_HEAP_RATIO));
            return setMemoryCacheMaxSize(size);
        }

        /**
         * Sets the recycle policy. This controls if {@link android.graphics.Bitmap#recycle()} is
         * called.
         *
         * @param recyclePolicy - New recycle policy, can not be null.
         * @return This Builder object to allow for chaining of calls to set methods.
         */
        public Builder setRecyclePolicy(RecyclePolicy recyclePolicy) {
            if (null == recyclePolicy) {
                throw new IllegalArgumentException("The recycle policy can not be null");
            }

            mRecyclePolicy = recyclePolicy;
            return this;
        }

        private boolean isValidOptionsForDiskCache() {
            boolean valid = mDiskCacheEnabled;

            if (valid) {
                if (null == mDiskCacheLocation) {
                    Log.i(Constants.LOG_TAG,
                            "Disk Cache has been enabled, but no location given. Please call setDiskCacheLocation(...)");
                    valid = false;
                } else if (!mDiskCacheLocation.canWrite()) {
                    Log.i(Constants.LOG_TAG,
                            "Disk Cache Location is not write-able, disabling disk caching.");
                    valid = false;
                }
            }

            return valid;
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

    interface InputStreamProvider {
        InputStream getInputStream();
    }

    static class FileInputStreamProvider implements InputStreamProvider {
        final File mFile;

        FileInputStreamProvider(File file) {
            mFile = file;
        }

        @Override
        public InputStream getInputStream() {
            try {
                return new FileInputStream(mFile);
            } catch (FileNotFoundException e) {
                Log.e(Constants.LOG_TAG, "Could not decode file: " + mFile.getAbsolutePath(), e);
            }
            return null;
        }
    }

    final class SnapshotInputStreamProvider implements InputStreamProvider {
        final String mKey;

        SnapshotInputStreamProvider(String key) {
            mKey = key;
        }

        @Override
        public InputStream getInputStream() {
            try {
                DiskLruCache.Snapshot snapshot = mDiskCache.get(mKey);
                if (snapshot != null) {
                    return snapshot.getInputStream(0);
                }
            } catch (IOException e) {
                Log.e(Constants.LOG_TAG, "Could open disk cache for url: " + mKey, e);
            }
            return null;
        }
    }
}
