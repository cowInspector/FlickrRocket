package com.bottlerocket.flickrrocket.ui;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.bottlerocket.flickrrocket.R;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.view.MenuItemCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SearchView;
import android.widget.SearchView.OnQueryTextListener;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

	String INITIAL_URL = "http://api.flickr.com/services/rest/?format=json"
			+ "&sort=random&method=flickr.photos.search&tags=rocket"
			+ "&tag_mode=all&api_key=0e2b6aaf8a6901c264acb91f151a3350"
			+ "&nojsoncallback=1";

	ArrayList<String> photoURIList = new ArrayList<String>();
	static int CURRENT_PHOTO_INDEX = 0;
	static int TOTAL_PHOTO_COUNT = 0;

	private TextView txtView;
	private ImageView imgView;
	JSONArray searchResults;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		new DownloadSearchResultTask().execute(INITIAL_URL);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		MenuItem item = menu.findItem(R.id.action_settings);
		SearchView searchView = (SearchView) MenuItemCompat.getActionView(item);
		searchView.setOnQueryTextListener(mOnQueryTextListener);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.save:
			saveImage();
			break;
		case R.id.share:
			Intent sendIntent = new Intent();
			sendIntent.setAction(Intent.ACTION_SEND);
			sendIntent.putExtra(Intent.EXTRA_TEXT,
					"Search " + photoURIList.get(CURRENT_PHOTO_INDEX)
							+ " on Flickr.");
			sendIntent.setType("text/plain");
			startActivity(sendIntent);
		}
		return super.onOptionsItemSelected(item);
	}

	private void saveImage() {
		imgView.buildDrawingCache();
		Bitmap bm = imgView.getDrawingCache();
		MediaStore.Images.Media.insertImage(getContentResolver(), bm, "Image",
				"Saved");
		Toast toast = Toast.makeText(getApplicationContext(), "Image Saved",
				Toast.LENGTH_LONG);
		toast.show();
	}

	/*
	 * Listener for changes to the search ActionView.
	 */
	private OnQueryTextListener mOnQueryTextListener = new OnQueryTextListener() {

		@Override
		public boolean onQueryTextSubmit(String query) {
			String searchURL = "http://api.flickr.com/services/rest/?format=json"
					+ "&sort=random&method=flickr.photos.search"
					+ "&tags="
					+ query.trim()
					+ "&tag_mode=all&api_key=0e2b6aaf8a6901c264acb91f151a3350&nojsoncallback=1";
			photoURIList.clear();
			imgView = (ImageView) findViewById(R.id.ImageView01);
			imgView.setVisibility(View.INVISIBLE);
			new DownloadSearchResultTask().execute(searchURL);
			return false;
		}

		@Override
		public boolean onQueryTextChange(String newText) {
			return false;
		}
	};

	public void getNextImage(View view) {
		imgView = (ImageView) findViewById(R.id.ImageView01);
		CURRENT_PHOTO_INDEX++;
		if (CURRENT_PHOTO_INDEX > TOTAL_PHOTO_COUNT) {
			CURRENT_PHOTO_INDEX = 0;
		}

		new DownloadImageFromURL().execute(photoURIList
				.get(CURRENT_PHOTO_INDEX));

	}

	private class DownloadSearchResultTask extends
			AsyncTask<String, Void, String> {

		@Override
		protected String doInBackground(String... params) {
			try {
				return downloadUrl(params[0]);
			} catch (IOException e) {
				return "Unable to retrieve web page. URL may be invalid.";
			}
		}

		// Given a URL, establishes an HttpUrlConnection and retrieves
		// the web page content as a InputStream, which it returns as
		// a string.
		private String downloadUrl(String myurl) throws IOException {
			InputStream is = null;

			try {
				URL url = new URL(myurl);
				HttpURLConnection conn = (HttpURLConnection) url
						.openConnection();
				conn.setReadTimeout(100000);
				conn.setConnectTimeout(150000);
				conn.setRequestMethod("GET");
				// conn.setDoInput(true);
				conn.setDoOutput(true);
				// Starts the query
				conn.connect();
				int response = conn.getResponseCode();
				Log.d("HttpExample", "The response is: " + response);
				is = conn.getInputStream();

				// Convert the InputStream into a string
				String contentAsString = readIt(is);
				return contentAsString;

				// Makes sure that the InputStream is closed after the app is
				// finished using it.
			} catch (Exception e) {
				System.out.println(e.getMessage());
			} finally {
				if (is != null) {
					is.close();
				}
			}
			return myurl;
		}

		public String readIt(InputStream stream) throws IOException,
				UnsupportedEncodingException {
			BufferedReader br = null;
			StringBuilder sb = new StringBuilder();
			String line;
			try {

				br = new BufferedReader(new InputStreamReader(stream));
				while ((line = br.readLine()) != null) {
					sb.append(line);
				}

			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				if (br != null) {
					try {
						br.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
			return sb.toString();
		}

		@Override
		protected void onPostExecute(String result) {
			super.onPostExecute(result);
			try {
				// JSON
				JSONObject jsonPhoto = ((JSONObject) new JSONObject(result))
						.getJSONObject("photos");
				searchResults = jsonPhoto.optJSONArray("photo");// ("photo",
																// "defaultValue");
				JSONObject temp = searchResults.getJSONObject(0);
				String photoURI = buildPhotoURI(temp);
				photoURIList.add(photoURI);
				for (int i = 1; i < searchResults.length(); i++) {
					photoURIList.add(buildPhotoURI(searchResults
							.getJSONObject(i)));
				}
				TOTAL_PHOTO_COUNT = searchResults.length();
				new DownloadImageFromURL().execute(photoURI);
			} catch (Exception e) {
				Toast toast = Toast.makeText(getApplicationContext(),
						"0 search results", Toast.LENGTH_LONG);
				toast.show();
			}
		}
	}

	private class DownloadImageFromURL extends AsyncTask<String, Void, Bitmap> {

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			imgView = (ImageView) findViewById(R.id.ImageView01);
			imgView.setVisibility(View.INVISIBLE);
			ProgressBar pBar = (ProgressBar) findViewById(R.id.progressBar1);
			pBar.setVisibility(View.VISIBLE);
			pBar.animate();
		}

		@Override
		protected Bitmap doInBackground(String... params) {
			Bitmap bm = null;
			try {
				URL aURL = new URL(params[0]);
				URLConnection conn = aURL.openConnection();
				conn.connect();
				InputStream is = conn.getInputStream();
				BufferedInputStream bis = new BufferedInputStream(is);
				bm = BitmapFactory.decodeStream(bis);
				bis.close();
				is.close();
			} catch (Exception e) {

			}
			return bm;
		}

		@Override
		protected void onProgressUpdate(Void... values) {
			super.onProgressUpdate(values);
			ProgressBar pBar = (ProgressBar) findViewById(R.id.progressBar1);
			pBar.setVisibility(View.VISIBLE);
			pBar.animate();
		}

		@Override
		protected void onPostExecute(Bitmap result) {
			super.onPostExecute(result);

			imgView = (ImageView) findViewById(R.id.ImageView01);
			imgView.setVisibility(View.VISIBLE);
			int REQ_WIDTH = 0;
			int REQ_HEIGHT = 0;
			REQ_WIDTH = imgView.getWidth();
			REQ_HEIGHT = imgView.getHeight();
			try {
				imgView.setImageBitmap(Bitmap.createScaledBitmap(result,
						REQ_WIDTH, REQ_HEIGHT, true));
			} catch (Exception e) {
				Toast toast = Toast.makeText(getApplicationContext(),
						"Error downloading the image...", Toast.LENGTH_LONG);
				toast.show();
			}
			ProgressBar pBar = (ProgressBar) findViewById(R.id.progressBar1);
			pBar.setVisibility(View.INVISIBLE);
			/*
			 * RotateAnimation animation = new RotateAnimation(0f, 350f, 15f,
			 * 15f); animation.setInterpolator(new LinearInterpolator());
			 * animation.setDuration(700);
			 */
			Animation fadeOutAnimation = new AlphaAnimation(0.0f, 1.0f);
			fadeOutAnimation.setDuration(600);
			// animation.setRepeatCount(1);
			imgView.setAnimation(fadeOutAnimation);
		}
	}

	public String buildPhotoURI(JSONObject temp) {
		String photoURI = null;
		try {
			photoURI = "http://farm" + temp.getInt("farm")
					+ ".static.flickr.com/" + temp.getString("server") + "/"
					+ temp.getString("id") + "_" + temp.getString("secret")
					+ "_b.jpg";
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return photoURI;
	}
}
