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
			wrapper.setDisplayed(true);
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
			mDisplayedBitmapWrapper.setDisplayed(false);
			mDisplayedBitmapWrapper = null;
		}
	}

}
