package uk.co.senab.bitmapcache.samples;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import uk.co.senab.bitmapcache.BitmapLruCache;
import uk.co.senab.bitmapcache.CacheableBitmapWrapper;
import uk.co.senab.bitmapcache.CacheableImageView;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;

/**
 * Simple extension of CacheableImageView which allows downloading of Images of
 * the Internet.
 * 
 * This code isn't production quality, but works well enough for this sample.s
 * 
 * @author Chris Banes
 * 
 */
public class NetworkedCacheableImageView extends CacheableImageView {

	/**
	 * This task simply gets a list of URLs of Pug Photos
	 */
	private class ImageUrlAsyncTask extends AsyncTask<String, Void, CacheableBitmapWrapper> {

		@Override
		protected CacheableBitmapWrapper doInBackground(String... params) {
			try {
				String url = params[0];

				HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
				InputStream is = new BufferedInputStream(conn.getInputStream());
				Bitmap bitmap = BitmapFactory.decodeStream(is);

				if (null != bitmap) {
					return new CacheableBitmapWrapper(url, bitmap);
				}

			} catch (MalformedURLException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}

			return null;
		}

		@Override
		protected void onPostExecute(CacheableBitmapWrapper result) {
			super.onPostExecute(result);

			// Display the image
			setImageCachedBitmap(result);

			// Add to cache
			mCache.put(result);
		}
	}

	private final BitmapLruCache mCache;
	private ImageUrlAsyncTask mCurrentTask;

	public NetworkedCacheableImageView(Context context, BitmapLruCache cache) {
		super(context);
		mCache = cache;
	}

	public void loadImage(BitmapLruCache cache, String url) {
		// First check whether there's already a task running, if so cancel it
		if (null != mCurrentTask) {
			mCurrentTask.cancel(false);
		}

		// Check to see if the cache already has the bitmap
		CacheableBitmapWrapper wrapper = mCache.get(url);

		if (null != wrapper && wrapper.hasValidBitmap()) {
			// The cache has it, so just display it
			setImageCachedBitmap(wrapper);
		} else {
			// Cache doesn't have the URL, do network request...
			mCurrentTask = new ImageUrlAsyncTask();
			mCurrentTask.execute(url);
		}
	}

}
