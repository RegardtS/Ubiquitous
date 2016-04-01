package com.example.android.sunshine.app;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

public class WeatherListenerService extends WearableListenerService implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    private GoogleApiClient mGoogleApiClient;

    private static final String KEY_HIGH = "hightemp";
    private static final String KEY_LOW = "lowtemp";
    private static final String KEY_IMAGE = "imagetemp";
    private static final String KEY_TIME = "timestamp";
    private static final String PATH_CURRENT_TEMP = "/currenttemp";
    private static final String PATH_TIME = "/timestamp";
    private static final String PATH_START_WEATHER_SYNC = "/sync";


    String mHigh = "";
    String mLow = "";
    Bitmap mBitmap = null;

    @Override
    public void onCreate() {
        super.onCreate();
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        mGoogleApiClient.connect();
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        connectGoogleApiAgain();
        for (DataEvent event : dataEvents) {
            Uri uri = event.getDataItem().getUri();
            String path = uri.getPath();
            if (PATH_CURRENT_TEMP.equals(path)) {
                DataMap dataMap = DataMapItem.fromDataItem(event.getDataItem()).getDataMap();
                mHigh = dataMap.getString(KEY_HIGH);
                mLow = dataMap.getString(KEY_LOW);
                new LoadBitmapAsyncTask().execute(dataMap.getAsset(KEY_IMAGE));
            } else if (PATH_TIME.equals(path)) {
                DataMap dataMap = DataMapItem.fromDataItem(event.getDataItem()).getDataMap();
                mHigh = dataMap.getString(KEY_HIGH);
                mLow = dataMap.getString(KEY_LOW);
                dataMap.getString(KEY_TIME);
                new LoadBitmapAsyncTask().execute(dataMap.getAsset(KEY_IMAGE));
            }
        }
    }

    private void connectGoogleApiAgain() {
        // connect if not connected
        if (!mGoogleApiClient.isConnected()) {
            ConnectionResult connectionResult = mGoogleApiClient.blockingConnect(30, TimeUnit.SECONDS);
            if (!connectionResult.isSuccess()) {
                return;
            }
        }
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        if (messageEvent.getPath().equals(PATH_START_WEATHER_SYNC)) {
            connectGoogleApiAgain();
            sendMessage(messageEvent.getSourceNodeId());
        }
    }

    @Override
    public void onPeerConnected(Node peer) {
    }


    @Override
    public void onPeerDisconnected(Node peer) {
    }


    private void sendMessage(String nodeId) {
        Intent intent = new Intent("weatherProcessed");
        intent.putExtra("highTemp", mHigh);
        intent.putExtra("lowTemp", mLow);
        try {
            if (mBitmap != null) {
                String filename = "bitmap.png";
                FileOutputStream stream = this.openFileOutput(filename, Context.MODE_PRIVATE);
                mBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);

                stream.close();

                intent.putExtra("bitmapFilename", filename);
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
            } else {
                if (nodeId != null) {
                    Wearable.MessageApi.sendMessage(mGoogleApiClient, nodeId, "/itsnull", new byte[0]);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void sendMessage() {
        sendMessage(null);
    }

    private class LoadBitmapAsyncTask extends AsyncTask<Asset, Void, Bitmap> {

        @Override
        protected Bitmap doInBackground(Asset... params) {
            if (params.length > 0) {
                Asset asset = params[0];
                InputStream assetInputStream = Wearable.DataApi.getFdForAsset(mGoogleApiClient, asset).await().getInputStream();
                if (assetInputStream == null) {
                    return null;
                }
                return BitmapFactory.decodeStream(assetInputStream);
            } else {
                return null;
            }
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (bitmap != null) {
                mBitmap = bitmap;
                sendMessage();
            }
        }
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        Wearable.DataApi.addListener(mGoogleApiClient, this);
    }

    @Override //ConnectionCallbacks
    public void onConnectionSuspended(int cause) {
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        Wearable.DataApi.removeListener(mGoogleApiClient, this);
        mGoogleApiClient.connect();
    }
}
