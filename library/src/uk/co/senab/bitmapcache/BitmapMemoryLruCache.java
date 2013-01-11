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

import java.util.Map.Entry;

import android.support.v4.util.LruCache;

final class BitmapMemoryLruCache extends LruCache<String, CacheableBitmapDrawable> {

	BitmapMemoryLruCache(int maxSize) {
		super(maxSize);
	}

	CacheableBitmapDrawable put(CacheableBitmapDrawable value) {
		if (null != value) {
			value.setCached(true);
			return put(value.getUrl(), value);
		}

		return null;
	}

	@Override
	protected int sizeOf(String key, CacheableBitmapDrawable value) {
		return value.getMemorySize();
	}

	@Override
	protected void entryRemoved(boolean evicted, String key, CacheableBitmapDrawable oldValue,
			CacheableBitmapDrawable newValue) {
		// Notify the wrapper that it's no longer being cached
		oldValue.setCached(false);
	}

	void trimMemory() {
		for (Entry<String, CacheableBitmapDrawable> entry : snapshot().entrySet()) {
			CacheableBitmapDrawable value = entry.getValue();
			if (null == value || !value.isBeingDisplayed()) {
				remove(entry.getKey());
			}
		}
	}

}
