package uk.co.senab.bitmapcache.samples;

import uk.co.senab.bitmapcache.BitmapLruCache;
import android.app.Application;
import android.content.Context;

public class SampleApplication extends Application {

	private BitmapLruCache mCache;

	@Override
	public void onCreate() {
		super.onCreate();

		// Using default constructor, using 1/8th of Heap space (RAM)
		mCache = new BitmapLruCache(this);
	}

	public BitmapLruCache getBitmapCache() {
		return mCache;
	}

	public static SampleApplication getApplication(Context context) {
		return (SampleApplication) context.getApplicationContext();
	}

}
