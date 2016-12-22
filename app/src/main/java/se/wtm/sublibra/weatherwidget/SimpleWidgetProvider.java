package se.wtm.sublibra.weatherwidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;

public class SimpleWidgetProvider extends AppWidgetProvider {

    private static final String TAG = "WeatherWidget";

    private Context theContext = null;

    private int[] widgetIds = null;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        final String URL = "http://sublibra.wtm.se:666/json/sensor/info?key=&item=outputFormat&value=jsonp&id=135";
        theContext = context;
        widgetIds = appWidgetIds;

        int N = appWidgetIds.length;

        // Perform this loop procedure for each App Widget that belongs to this provider
        for (int widgetId: widgetIds){

            RemoteViews remoteViews = new RemoteViews(context.getPackageName(),
                    R.layout.simple_widget);

            ConnectivityManager connMgr = (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
            if (networkInfo != null && networkInfo.isConnected()) {
                new DownloadWebpageTask().execute(URL);
            } else {
                remoteViews.setTextViewText(R.id.temperature, "No network connection available.");
                remoteViews.setTextViewText(R.id.humidity, "");
            }

            Intent intent = new Intent(context, SimpleWidgetProvider.class);
            intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context,
                    0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            remoteViews.setOnClickPendingIntent(R.id.refresh, pendingIntent);
            appWidgetManager.updateAppWidget(widgetId, remoteViews);
        }
    }

    // Uses AsyncTask to create a task away from the main UI thread. This task takes a
    // URL string and uses it to create an HttpUrlConnection. Once the connection
    // has been established, the AsyncTask downloads the contents of the webpage as
    // an InputStream. Finally, the InputStream is converted into a string, which is
    // displayed in the UI by the AsyncTask's onPostExecute method.
    private class DownloadWebpageTask extends AsyncTask<String, Void, WeatherData> {

        @Override
        protected void onPreExecute(){
            for (int widgetId: widgetIds){
                AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(theContext);
                RemoteViews remoteViews = new RemoteViews(theContext.getPackageName(),R.layout.simple_widget);
                remoteViews.setTextViewText(R.id.temperature,"\u27F3");
                remoteViews.setTextViewText(R.id.humidity,"\u27F3");
                remoteViews.setTextViewText(R.id.status,"Waiting for update");
                appWidgetManager.partiallyUpdateAppWidget(widgetId, remoteViews);
            }
        }

        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(WeatherData result) {
            for (int widgetId: widgetIds){
                AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(theContext);
                RemoteViews remoteViews = new RemoteViews(theContext.getPackageName(),R.layout.simple_widget);
                if (result != null && result.getErrorMessage() == null){
                    Log.d(TAG, "Content: " + result.toString());
                    remoteViews.setTextViewText(R.id.temperature, Double.toString(result.getTemperature()) + "\u2103");
                    remoteViews.setTextViewText(R.id.humidity, Integer.toString(result.getHumidity()) + "%");
                    remoteViews.setTextViewText(R.id.status, result.getLastUpdatedString());
                } else {
                    Log.d(TAG, "Error received: " + result.toString());
                    remoteViews.setTextViewText(R.id.temperature, "N/A");
                    remoteViews.setTextViewText(R.id.humidity, "N/A");
                    remoteViews.setTextViewText(R.id.status, result.getErrorMessage());
                }
                appWidgetManager.partiallyUpdateAppWidget(widgetId, remoteViews);
            }
        }

        @Override
        protected WeatherData doInBackground(String... urls) {
            // params comes from the execute() call: params[0] is the url.
            try {
                String sensorData = downloadUrl(urls[0]);
                if (sensorData != null) {
                    return parseWeatherData(sensorData);
                } else {
                    return new WeatherData("Invalid response from server");
                }
            } catch (SocketTimeoutException e){
                Log.d(TAG, "Could not contact server. Socket timeout" + e.getMessage());
                return new WeatherData("Could not contact server. Socket timeout");
            } catch (IOException e) {
                Log.d(TAG, "Unable to retrieve web page. URL may be invalid." + e.getMessage());
                return null;
            }
        }

        /**
         * Given a URL, establishes an HttpUrlConnection and retrieves the data as a string
         * response limited to 500 characters since sensor input is short
         * @param myurl
         * @return sensor data as string
         * @throws IOException
         * @throws SocketTimeoutException
         */
        private String downloadUrl(String myurl) throws IOException, SocketTimeoutException {
            int len = 500; // only read 500 first chars
            InputStream is = null;
            Reader reader = null;
            try {
                URL url = new URL(myurl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setReadTimeout(10000 /* milliseconds */);
                conn.setConnectTimeout(15000 /* milliseconds */);
                conn.setRequestMethod("GET");
                conn.setDoInput(true);
                conn.connect();
                int response = conn.getResponseCode();
                Log.d(TAG, "HTTP response is: " + response);
                is = conn.getInputStream();
                reader = new InputStreamReader(is, "UTF-8");
                char[] buffer = new char[len];
                reader.read(buffer);
                return new String(buffer);
            } finally {
                // Makes sure that the InputStream is closed after the app is
                // finished using it.
                if (is != null) {
                    is.close();
                }
            }
        }

        /**
         * Parse the json data returned from the server
         * @param sensorData data returned from the server as json
         * @return WeatherData object containing the sensor data or an error message
         */
        private WeatherData parseWeatherData(String sensorData){
            WeatherData wd = new WeatherData();
            try {
                  JSONObject jsonObj = new JSONObject(new String(sensorData));

                // Check if we have a valid sensor. i.e. it contains the data node
                if(jsonObj.has("data")) {
                    String lastUpdateDate = jsonObj.getString("lastUpdated");
                    wd.setLastUpdated(Long.parseLong(lastUpdateDate));
                    Log.d(TAG, "lastUpdatedDate:" + lastUpdateDate);

                    // Get json "data" array
                    JSONArray data = jsonObj.getJSONArray("data");

                    for (int i = 0; i < data.length(); i++) {
                        JSONObject c = data.getJSONObject(i);
                        String name = c.getString("name");
                        switch (name) {
                            case "temp":
                                wd.setTemperature(Double.parseDouble(c.getString("value")));
                                Log.d(TAG, "temp: " + c.getString("value"));
                                break;
                            case "humidity":
                                wd.setHumidity(Integer.parseInt(c.getString("value")));
                                Log.d(TAG, "humidity: " + c.getString("value"));
                                break;
                            default:
                                wd = null;
                                break;
                        }
                    }
                } else {
                   Log.d(TAG, "error: " + jsonObj.getString("error"));
                   wd.setErrorMessage(jsonObj.getString("error"));
                }
            } catch (JSONException e) {
                Log.d(TAG, e.getMessage());
                wd.setErrorMessage("Could not parse the sensor reply (json)");
            } catch (NullPointerException e){
                Log.d(TAG, e.getMessage());
                wd.setErrorMessage("No response from the sensor server");
            }
            return wd;
        }
    }
}