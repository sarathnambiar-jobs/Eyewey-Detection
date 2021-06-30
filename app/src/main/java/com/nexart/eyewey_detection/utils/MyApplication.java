package com.nexart.eyewey_detection.utils;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.downloader.PRDownloader;
import com.downloader.PRDownloaderConfig;
import com.mapzen.speakerbox.Speakerbox;

public class MyApplication extends Application {

    public static Speakerbox speakerbox;
    private static MyApplication single_instance = null;
    private static final String TAG = MyApplication.class.getSimpleName();
    private static Context context;


    @Override
    public void onCreate() {
        super.onCreate();
        speakerbox = new Speakerbox(this);
        MyApplication.context = getApplicationContext();
        // Enabling database for resume support even after the application is killed:
        /*PRDownloaderConfig config = PRDownloaderConfig.newBuilder()
                .setDatabaseEnabled(true)
                .build();
        PRDownloader.initialize(getApplicationContext(), config);*/

        // Setting timeout globally for the download network requests:
        PRDownloaderConfig config = PRDownloaderConfig.newBuilder()
                .setReadTimeout(30_000)
                .setConnectTimeout(30_000)
                .setDatabaseEnabled(true)
                .build();
        PRDownloader.initialize(getApplicationContext(), config);
    }

    public static MyApplication getInstance() {
        if (single_instance == null) {
            single_instance = new MyApplication();
        }
        return single_instance;
    }

    public static Context getAppContext() {
        return MyApplication.context;
    }

    /**
     * Check Internet Connectivity
     *
     * @return {@code Boolean}
     */
    public boolean checkConnection() {
        Log.e(TAG, "checkConnection: ");
        return true;
        //ConnectivityManager cm = (ConnectivityManager) getSystemService(getApplicationContext().CONNECTIVITY_SERVICE);
        //return cm.getActiveNetworkInfo() != null && cm.getActiveNetworkInfo().isConnected();
    }

    public SharedPreferences getPreference() {
        SharedPreferences pref = getAppContext().getSharedPreferences("Eyewey2", MODE_PRIVATE);
        return pref;
    }
}