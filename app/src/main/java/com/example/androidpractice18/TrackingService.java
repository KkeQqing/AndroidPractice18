package com.example.androidpractice18;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.baidu.location.BDAbstractLocationListener;
import com.baidu.location.BDLocation;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;

public class TrackingService extends Service implements LocationListener {

    private static final String TAG = "TrackingService";
    private static final String CHANNEL_ID = "tracking_channel";
    private static final int NOTIFICATION_ID = 1001;

    private LocationManager locationManager;
    private LocationClient baiduLocationClient;
    private TrackManager trackManager;
    private Handler autoSaveHandler;
    private Runnable autoSaveRunnable;

    public static void start(Context context) {
        Intent intent = new Intent(context, TrackingService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, TrackingService.class);
        context.stopService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "TrackingService created");
        trackManager = TrackManager.getInstance(this);
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "TrackingService onStartCommand");
        startNativeLocationTracking();
        startBaiduLocationTracking();
        setupAutoSave();
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "轨迹记录服务",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("后台持续记录运动轨迹");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        int pointCount = trackManager != null ? trackManager.getPointCount() : 0;

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("轨迹记录中")
                .setContentText("已记录 " + pointCount + " 个轨迹点")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void startNativeLocationTracking() {
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        boolean isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

        if (isGPSEnabled) {
            try {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        2000,
                        5,
                        this,
                        Looper.getMainLooper()
                );
                Log.d(TAG, "Native GPS tracking started");
            } catch (SecurityException e) {
                Log.e(TAG, "GPS permission error", e);
            }
        }

        if (isNetworkEnabled) {
            try {
                locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        2000,
                        10,
                        this,
                        Looper.getMainLooper()
                );
                Log.d(TAG, "Native Network tracking started");
            } catch (SecurityException e) {
                Log.e(TAG, "Network permission error", e);
            }
        }
    }

    private void startBaiduLocationTracking() {
        // 新增：定位 SDK 隐私同意
        LocationClient.setAgreePrivacy(true);
        try {
            baiduLocationClient = new LocationClient(getApplicationContext());
        } catch (Exception e) {
            Log.e(TAG, "Failed to create Baidu LocationClient", e);
            return;
        }
        baiduLocationClient.registerLocationListener(new BDAbstractLocationListener() {
            @Override
            public void onReceiveLocation(BDLocation location) {
                if (location != null && (location.getLocType() == BDLocation.TypeGpsLocation
                        || location.getLocType() == BDLocation.TypeNetWorkLocation)) {
                    double lat = location.getLatitude();
                    double lng = location.getLongitude();
                    float accuracy = location.getRadius();
                    float speed = location.getSpeed();

                    TrackPoint point = new TrackPoint(
                            lat, lng, accuracy, speed,
                            System.currentTimeMillis(),
                            "Baidu"
                    );
                    trackManager.addTrackPoint(point);

                    Intent broadcastIntent = new Intent("LOCATION_UPDATED");
                    broadcastIntent.putExtra("latitude", lat);
                    broadcastIntent.putExtra("longitude", lng);
                    sendBroadcast(broadcastIntent);

                    Log.d(TAG, "Baidu location: (" + lat + ", " + lng + ")");
                }
            }
        });

        LocationClientOption option = new LocationClientOption();
        option.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy);
        option.setScanSpan(1000);
        option.setNeedNewVersionRgc(true);
        option.setIsNeedAddress(true);
        option.setIsNeedLocationPoiList(false);
        option.setIsNeedAltitude(true);
        option.setIsNeedLocationDescribe(false);
        option.setOpenGps(true);
        option.setCoorType("bd09ll");
        option.setLocationPurpose(LocationClientOption.BDLocationPurpose.Sport);

        baiduLocationClient.setLocOption(option);
        try {
            baiduLocationClient.start();
            Log.d(TAG, "Baidu location tracking started");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start Baidu location", e);
        }
    }

    private void setupAutoSave() {
        autoSaveHandler = new Handler(Looper.getMainLooper());
        autoSaveRunnable = new Runnable() {
            @Override
            public void run() {
                if (trackManager != null) {
                    trackManager.saveTrackPoints();
                    // 更新通知内容（需要 POST_NOTIFICATIONS 权限）
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(TrackingService.this,
                                android.Manifest.permission.POST_NOTIFICATIONS)
                                == PackageManager.PERMISSION_GRANTED) {
                            updateNotification();
                        }
                    } else {
                        updateNotification();
                    }
                }
                autoSaveHandler.postDelayed(this, 5000);
            }
        };
        autoSaveHandler.postDelayed(autoSaveRunnable, 5000);
    }

    private void updateNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification());
        }
    }

    // LocationListener 回调
    @Override
    public void onLocationChanged(Location location) {
        if (location != null) {
            double lat = location.getLatitude();
            double lng = location.getLongitude();
            float accuracy = location.getAccuracy();
            float speed = location.getSpeed();
            String provider = location.getProvider();

            TrackPoint point = new TrackPoint(
                    lat, lng, accuracy, speed,
                    location.getTime(),
                    provider
            );
            trackManager.addTrackPoint(point);

            Intent intent = new Intent("LOCATION_UPDATED");
            intent.putExtra("latitude", lat);
            intent.putExtra("longitude", lng);
            sendBroadcast(intent);

            Log.d(TAG, "Native location from " + provider + ": (" + lat + ", " + lng + ")");
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}

    @Override
    public void onProviderEnabled(String provider) {
        Log.d(TAG, "Provider enabled: " + provider);
    }

    @Override
    public void onProviderDisabled(String provider) {
        Log.d(TAG, "Provider disabled: " + provider);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "TrackingService destroyed");

        if (locationManager != null) {
            try {
                locationManager.removeUpdates(this);
            } catch (SecurityException e) {
                Log.e(TAG, "Stop tracking error", e);
            }
        }

        if (baiduLocationClient != null && baiduLocationClient.isStarted()) {
            baiduLocationClient.stop();
        }

        if (autoSaveHandler != null && autoSaveRunnable != null) {
            autoSaveHandler.removeCallbacks(autoSaveRunnable);
        }

        if (trackManager != null) {
            trackManager.saveTrackPoints();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}