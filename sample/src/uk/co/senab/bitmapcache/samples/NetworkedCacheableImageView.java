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
package uk.co.senab.bitmapcache.samples;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import uk.co.senab.bitmapcache.BitmapLruCache;
import uk.co.senab.bitmapcache.CacheableBitmapDrawable;
import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;

/**
 * Simple extension of CacheableImageView which allows downloading of Images of
 * the Internet.
 * 
 * This code isn't production quality, but works well enough for this sample.s
 * 
 * @author Chris Banes
 * 
 */
public class NetworkedCacheableImageView extends ImageView {

	/**
	 * This task simply fetches an Bitmap from the specified URL and wraps it in
	 * a wrapper. This implementation is NOT 'best practice' or production ready
	 * code.
	 */
	private class ImageUrlAsyncTask extends AsyncTask<String, Void, CacheableBitmapDrawable> {

		@Override
		protected CacheableBitmapDrawable doInBackground(String... params) {
			try {
				String url = params[0];

				// Now we're not on the main thread we can check all caches
				CacheableBitmapDrawable result = mCache.get(url);

				if (null == result) {
					Log.d("ImageUrlAsyncTask", "Downloading: " + url);

					// The bitmap isn't cached so download from the web
					HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
					InputStream is = new BufferedInputStream(conn.getInputStream());

					// Add to cache
					result = mCache.put(url, is);
				} else {
					Log.d("ImageUrlAsyncTask", "Got from Cache: " + url);
				}

				return result;

			} catch (MalformedURLException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}

			return null;
		}

		@Override
		protected void onPostExecute(CacheableBitmapDrawable result) {
			super.onPostExecute(result);

			if (null != result) {
				// If we have a result, call it's display method, giving the
				// ImageView as a parameter
				result.display(NetworkedCacheableImageView.this);
			} else {
				// If we're don't have a result, just display an empty space
				setImageDrawable(null);
			}
		}
	}

	private final BitmapLruCache mCache;
	private ImageUrlAsyncTask mCurrentTask;

	public NetworkedCacheableImageView(Context context, AttributeSet attrs) {
		super(context, attrs);
		mCache = SampleApplication.getApplication(context).getBitmapCache();
	}

	public boolean loadImage(String url) {
		return loadImage(url, true);
	}

	/**
	 * Loads the Bitmap.
	 * 
	 * @param url - URL of image
	 * @param fullSize - Whether the image should be kept at the original size
	 * @return true if the bitmap was found in the cache
	 */
	public boolean loadImage(String url, final boolean fullSize) {
		// First check whether there's already a task running, if so cancel it
		if (null != mCurrentTask) {
			mCurrentTask.cancel(false);
		}

		// Check to see if the memory cache already has the bitmap. We can
		// safely do
		// this on the main thread.
		BitmapDrawable wrapper = mCache.getFromMemoryCache(url);

		if (null != wrapper) {
			// The cache has it, so just display it
			setImageDrawable(wrapper);
			return true;
		} else {
			// Memory Cache doesn't have the URL, do threaded request...
			setImageDrawable(null);

			mCurrentTask = new ImageUrlAsyncTask();
			mCurrentTask.execute(url);
			return false;
		}
	}

}
