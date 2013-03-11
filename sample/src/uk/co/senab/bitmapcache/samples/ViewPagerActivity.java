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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.view.ViewPager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;

public class ViewPagerActivity extends Activity {

    static final int PUG_COUNT = 20;

    /**
     * This task simply gets a list of URLs of Photos from PugMe
     */
    private class PugListAsyncTask extends AsyncTask<Void, Void, ArrayList<String>> {

        static final String PUG_ME_URL = "http://pugme.herokuapp.com/bomb?count=" + PUG_COUNT;

        @Override
        protected ArrayList<String> doInBackground(Void... params) {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(PUG_ME_URL).openConnection();
                conn.setRequestProperty("Accept", "application/json");
                InputStream is = conn.getInputStream();

                StringBuilder sb = new StringBuilder();
                BufferedReader r = new BufferedReader(new InputStreamReader(is), 1024);
                for (String line = r.readLine(); line != null; line = r.readLine()) {
                    sb.append(line);
                }
                try {
                    is.close();
                } catch (IOException e) {
                }

                String response = sb.toString();
                JSONObject document = new JSONObject(response);

                JSONArray pugsJsonArray = document.getJSONArray("pugs");
                HashSet<String> pugUrls = new HashSet<String>(pugsJsonArray.length());

                for (int i = 0, z = pugsJsonArray.length(); i < z; i++) {
                    pugUrls.add(pugsJsonArray.getString(i));
                }

                return new ArrayList<String>(pugUrls);

            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(ArrayList<String> result) {
            super.onPostExecute(result);

            PugPagerAdapter adapter = new PugPagerAdapter(ViewPagerActivity.this, result);
            mViewPager.setAdapter(adapter);
        }

    }

    private ViewPager mViewPager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mViewPager = new ViewPager(this);
        setContentView(mViewPager);

        // Start Pug List Download
        new PugListAsyncTask().execute();
    }

}
