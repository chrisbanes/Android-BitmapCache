Android-BitmapMemoryCache
=========================

![Screenshot](https://github.com/chrisbanes/Android-BitmapMemoryCache/raw/master/sample_screenshot.jpg)

This project came about as part of my blog post: [http://www.senab.co.uk/2012/07/01/android-bitmap-caching-revisited/]()

The basic premise is a LruCache used for Bitmap management on Android. Each Bitmap is reference counted, both my ImageViews and the Cache. When the bitmap is no longer being used or cached, it is recycled and the memory freed.

I have added a sample app to the source since, which can also be downloaded from the Downloads tab above. The sample app shows you how to use the library by creating a ViewPager of images downloaded from the web. These are cached in the LruCache. 

## Download
The easy way to use the library is by downloading the JAR file under the 'Downloads' tab, and importing it into your Eclipse project.


## License

    Copyright 2011, 2012 Chris Banes

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.