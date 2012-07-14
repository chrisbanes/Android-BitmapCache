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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ImageView;

public class CacheableImageView extends ImageView {

	private CacheableBitmapWrapper mDisplayedBitmapWrapper;

	public CacheableImageView(Context context) {
		super(context);
	}

	public CacheableImageView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	/**
	 * 
	 * @param wrapper
	 */
	public void setImageCachedBitmap(final CacheableBitmapWrapper wrapper) {
		if (null != wrapper) {
			wrapper.setBeingUsed(true);
			setImageDrawable(new BitmapDrawable(getResources(), wrapper.getBitmap()));
		} else {
			setImageDrawable(null);
		}

		// Finally, set our new BitmapWrapper
		mDisplayedBitmapWrapper = wrapper;
	}

	@Override
	public void setImageBitmap(Bitmap bm) {
		setImageCachedBitmap(new CacheableBitmapWrapper(bm));
	}

	@Override
	public void setImageDrawable(Drawable drawable) {
		super.setImageDrawable(drawable);
		resetCachedDrawable();
	}

	@Override
	public void setImageResource(int resId) {
		super.setImageResource(resId);
		resetCachedDrawable();
	}
	
	public CacheableBitmapWrapper getCachedBitmapWrapper() {
		return mDisplayedBitmapWrapper;
	}

	@Override
	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();

		// Will cause displayed bitmap wrapper to be 'free-able'
		setImageDrawable(null);
	}

	/**
	 * Called when the current cached bitmap has been removed. This method will
	 * remove the displayed flag and remove this objects reference to it.
	 */
	private void resetCachedDrawable() {
		if (null != mDisplayedBitmapWrapper) {
			mDisplayedBitmapWrapper.setBeingUsed(false);
			mDisplayedBitmapWrapper = null;
		}
	}

}
