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

import android.graphics.Bitmap;
import android.util.Log;

public class CacheableBitmapWrapper {

	private final String mUrl;
	private final Bitmap mBitmap;

	// Number of ImageViews currently showing bitmap
	private int mImageViewsCount;

	// Number of caches currently referencing the wrapper
	private int mCacheCount;

	CacheableBitmapWrapper(Bitmap bitmap) {
		this(null, bitmap);
	}

	CacheableBitmapWrapper(String url, Bitmap bitmap) {
		if (null == bitmap) {
			throw new IllegalArgumentException("Bitmap can not be null");
		}

		mBitmap = bitmap;
		mUrl = url;
		mImageViewsCount = 0;
		mCacheCount = 0;
	}

	/**
	 * @return true - if the wrapper is currently referenced by a cache.
	 */
	public synchronized boolean isReferencedByCache() {
		return mCacheCount > 0;
	}

	/**
	 * @return true - if the bitmap is currently being displayed by a
	 *         {@link CacheableImageView}.
	 */
	public synchronized boolean isBeingDisplayed() {
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
	 * @return true - if the bitmap has not been recycled.
	 */
	public synchronized boolean hasValidBitmap() {
		return !mBitmap.isRecycled();
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
	 * Used to signal to the wrapper whether it is being used or not. Being used
	 * could be that it is being displayed by an ImageView.
	 * 
	 * @param beingUsed - true if being used, false if not.
	 */
	public synchronized void setBeingUsed(boolean beingUsed) {
		if (beingUsed) {
			mImageViewsCount++;
		} else {
			mImageViewsCount--;
		}
		checkState();
	}

	/**
	 * Checks whether the wrapper is currently referenced, and is being
	 * displayed. If neither of those conditions are met then the bitmap is
	 * recycled and freed.
	 */
	private void checkState() {
		if (mCacheCount <= 0 && mImageViewsCount <= 0 && hasValidBitmap()) {
			if (Constants.DEBUG) {
				Log.d(Constants.LOG_TAG, "Recycling bitmap with url: " + mUrl);
			}
			mBitmap.recycle();
		}
	}

}
