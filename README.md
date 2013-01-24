Android-BitmapCache
=========================

This project came about as part of my blog post: [http://www.senab.co.uk/2012/07/01/android-bitmap-caching-revisited/](http://www.senab.co.uk/2012/07/01/android-bitmap-caching-revisited/)

Android-BitmapCache is a specialised cache, for use with Android Bitmap objects. 

I have added a sample app to the source since, which can also be downloaded from the Downloads tab above. The sample app shows you how to use the library by creating a ViewPager of images downloaded from the web. These are cached in the LruCache and/or Disk Cache.

## Summary

A cache which can be set to use multiple layers of caching for Bitmap objects
in an Android app. Instances are created via a `BitmapLruCache.Builder` instance,
which can be used to alter the settings of the resulting cache.

Instances of this class should ideally be kept globally with the application,
for example in the `Application` object. You
should also use the bundled [CacheableImageView](https://github.com/chrisbanes/Android-BitmapCache/blob/master/library/src/uk/co/senab/bitmapcache/CacheableImageView.java) wherever possible, as
the memory cache has a close relationship with it.
 
Clients can call `get(String)` to retrieve a cached value from the
given Url. This will check all available caches for the value. There are also
the `getFromDiskCache(String)` and `getFromMemoryCache(String)`
which allow more granular access.

There are a number of update methods. `put(String, InputStream)` and
`put(String, InputStream, boolean)` are the preferred versions of the
method, as they allow 1:1 caching to disk of the original content. <br />
`put(String, Bitmap)` and `put(String, Bitmap, boolean)` should
only be used if you can't get access to the original InputStream.

## Usage
The easy way to use the library is by downloading the JAR file, and importing it into your Eclipse project. You can find the latest JAR file from here: [http://bit.ly/android-bitmapcache-jar](http://bit.ly/android-bitmapcache-jar). Just remember that you must include all of the required libraries below too.

If you are a Maven user you can also add this library as a dependency since it
it distributed to the central repositories. Simply add the following to your
`pom.xml`:

```xml
<dependency>
    <groupId>com.github.chrisbanes.bitmapcache</groupId>
    <artifactId>library</artifactId>
    <version>2.1</version>
</dependency>
```

### Requirements
 * [DiskLruCache](https://github.com/JakeWharton/DiskLruCache). Tested with v1.3.1.
 * [Android v4 Support Library](http://developer.android.com/tools/extras/support-library.html). Preferably the latest available.

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