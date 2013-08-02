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
package uk.co.senab.bitmapcache.samples;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import uk.co.senab.bitmapcache.CacheableBitmapDrawable;

public class PugListAdapter extends BaseAdapter {

    private final ArrayList<String> mPugUrls;

    private final Context mContext;

    public PugListAdapter(Context context, ArrayList<String> pugUrls) {
        mPugUrls = pugUrls;
        mContext = context;
    }

    @Override
    public int getCount() {
        return null != mPugUrls ? mPugUrls.size() : 0;
    }

    @Override
    public String getItem(int position) {
        return mPugUrls.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (null == convertView) {
            convertView = LayoutInflater.from(mContext)
                    .inflate(R.layout.gridview_item_layout, parent, false);
        }

        NetworkedCacheableImageView imageView = (NetworkedCacheableImageView) convertView
                .findViewById(R.id.nciv_pug);
        TextView status = (TextView) convertView.findViewById(R.id.tv_status);

        final boolean fromCache = imageView
                .loadImage(mPugUrls.get(position), false, new UpdateTextViewListener(status));

        if (fromCache) {
            status.setText("From Memory Cache");
            status.setBackgroundColor(mContext.getResources().getColor(R.color.translucent_green));
        } else {
            status.setText("Loading...");
            status.setBackgroundDrawable(null);
        }

        return convertView;
    }

    static class UpdateTextViewListener
            implements NetworkedCacheableImageView.OnImageLoadedListener {
        private final WeakReference<TextView> mTextViewRef;

        public UpdateTextViewListener(TextView tv) {
            mTextViewRef = new WeakReference<TextView>(tv);
        }

        @Override
        public void onImageLoaded(CacheableBitmapDrawable result) {
            final TextView tv = mTextViewRef.get();
            if (tv == null) {
                return;
            }

            if (result == null) {
                tv.setText("Failed");
                tv.setBackgroundDrawable(null);
                return;
            }

            switch (result.getSource()) {
                case CacheableBitmapDrawable.SOURCE_UNKNOWN:
                case CacheableBitmapDrawable.SOURCE_NEW:
                    tv.setText("From Disk/Network");
                    tv.setBackgroundColor(tv.getResources().getColor(R.color.translucent_red));
                    break;
                case CacheableBitmapDrawable.SOURCE_INBITMAP:
                    tv.setText("Reused Bitmap");
                    tv.setBackgroundColor(tv.getResources().getColor(R.color.translucent_blue));
                    break;
            }
        }
    }

}
