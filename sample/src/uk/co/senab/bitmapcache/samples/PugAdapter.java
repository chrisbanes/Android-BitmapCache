package uk.co.senab.bitmapcache.samples;

import java.util.ArrayList;

import uk.co.senab.bitmapcache.BitmapLruCache;
import android.content.Context;
import android.support.v4.view.PagerAdapter;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView.ScaleType;

public class PugAdapter extends PagerAdapter {

	private final ArrayList<String> mPugUrls;
	private final Context mContext;
	private final BitmapLruCache mCache;

	public PugAdapter(Context context, ArrayList<String> pugUrls) {
		mPugUrls = pugUrls;
		mContext = context;
		mCache = SampleApplication.getApplication(context).getBitmapCache();
	}

	@Override
	public int getCount() {
		return null != mPugUrls ? mPugUrls.size() : 0;
	}

	@Override
	public View instantiateItem(ViewGroup container, int position) {
		NetworkedCacheableImageView imageView = new NetworkedCacheableImageView(mContext, mCache);

		String pugUrl = mPugUrls.get(position);
		imageView.loadImage(mCache, pugUrl);

		imageView.setScaleType(ScaleType.FIT_CENTER);
		container.addView(imageView, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);

		return imageView;
	}

	@Override
	public void destroyItem(ViewGroup container, int position, Object object) {
		container.removeView((View) object);
	}

	@Override
	public boolean isViewFromObject(View view, Object object) {
		return view == object;
	}

}
