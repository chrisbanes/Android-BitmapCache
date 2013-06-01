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
package uk.co.senab.bitmapcache;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.AttributeSet;
import android.widget.ImageView;

public class CacheableImageView extends ImageView {

    private static void onDrawableSet(Drawable drawable) {
        if (drawable instanceof CacheableBitmapDrawable) {
            ((CacheableBitmapDrawable) drawable).setBeingUsed(true);
        }
    }

    private static void onDrawableUnset(final Drawable drawable) {
        if (drawable instanceof CacheableBitmapDrawable) {
            ((CacheableBitmapDrawable) drawable).setBeingUsed(false);
        }
    }

    public CacheableImageView(Context context) {
        super(context);
    }

    public CacheableImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CacheableImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        final Drawable previousDrawable = getDrawable();

        // Set new Drawable
        super.setImageDrawable(drawable);

        if (drawable != previousDrawable) {
            onDrawableSet(drawable);
            onDrawableUnset(previousDrawable);
        }
    }

    @Override
    public void setImageResource(int resId) {
        final Drawable previousDrawable = getDrawable();
        super.setImageResource(resId);
        onDrawableUnset(previousDrawable);
    }

    @Override
    public void setImageURI(Uri uri) {
        final Drawable previousDrawable = getDrawable();
        super.setImageURI(uri);
        onDrawableUnset(previousDrawable);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        // Will cause displayed bitmap wrapper to be 'free-able'
        setImageDrawable(null);
    }

}
