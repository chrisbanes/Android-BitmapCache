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

import uk.co.senab.bitmapcache.BitmapLruCache;
import android.app.Application;
import android.content.Context;

public class SampleApplication extends Application {

	private BitmapLruCache mCache;

	@Override
	public void onCreate() {
		super.onCreate();

		BitmapLruCache.Builder builder = new BitmapLruCache.Builder();
		builder.setMemoryCacheSize(this);

		mCache = builder.build();
	}

	public BitmapLruCache getBitmapCache() {
		return mCache;
	}

	public static SampleApplication getApplication(Context context) {
		return (SampleApplication) context.getApplicationContext();
	}

}
