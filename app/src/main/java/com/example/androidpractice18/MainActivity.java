package com.example.androidpractice18;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.baidu.location.BDAbstractLocationListener;
import com.baidu.location.BDLocation;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.MapStatusUpdate;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MarkerOptions;
import com.baidu.mapapi.map.MyLocationConfiguration;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.map.OverlayOptions;
import com.baidu.mapapi.map.Polyline;
import com.baidu.mapapi.map.PolylineOptions;
import com.baidu.mapapi.model.LatLng;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 100;

    private MapView mapView;
    private BaiduMap baiduMap;
    private TextView tvStatus, tvPointCount, tvLatLng;
    private Button btnStartNative, btnStartBaidu, btnStop, btnClear;

    private LocationClient locationClient;
    private boolean isBaiduLocationStarted = false;
    private boolean isNativeTracking = false;

    private List<LatLng> trackPointsForMap;
    private Polyline currentPolyline;

    private BroadcastReceiver locationUpdateReceiver;
    private TrackManager trackManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        trackPointsForMap = new ArrayList<>();
        trackManager = TrackManager.getInstance(this);

        // 百度地图 SDK 初始化：必须先同意隐私协议
        SDKInitializer.setAgreePrivacy(getApplicationContext(), true);
        SDKInitializer.initialize(getApplicationContext());

        setContentView(R.layout.activity_main);

        initViews();
        initBaiduMap();
        initBroadcastReceiver();

        checkAndRequestPermissions();

        updateUI();
        loadAndDrawExistingTrack();
    }

    private void initViews() {
        mapView = findViewById(R.id.mapView);
        tvStatus = findViewById(R.id.tvStatus);
        tvPointCount = findViewById(R.id.tvPointCount);
        tvLatLng = findViewById(R.id.tvLatLng);
        btnStartNative = findViewById(R.id.btnStartNative);
        btnStartBaidu = findViewById(R.id.btnStartBaidu);
        btnStop = findViewById(R.id.btnStop);
        btnClear = findViewById(R.id.btnClear);

        btnStartNative.setOnClickListener(v -> startNativeTracking());
        btnStartBaidu.setOnClickListener(v -> startBaiduLocationTracking());
        btnStop.setOnClickListener(v -> stopAllTracking());
        btnClear.setOnClickListener(v -> clearTrajectory());
    }

    private void initBaiduMap() {
        baiduMap = mapView.getMap();
        baiduMap.setMapStatus(MapStatusUpdateFactory.zoomTo(15f));
        baiduMap.setMyLocationEnabled(true);
        baiduMap.setMyLocationConfiguration(new MyLocationConfiguration(
                MyLocationConfiguration.LocationMode.FOLLOWING, true, null));
    }

    private void initBroadcastReceiver() {
        locationUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("LOCATION_UPDATED".equals(intent.getAction())) {
                    double lat = intent.getDoubleExtra("latitude", 0);
                    double lng = intent.getDoubleExtra("longitude", 0);
                    updateMapWithNewPoint(lat, lng);
                    updateUI();
                }
            }
        };
        IntentFilter filter = new IntentFilter("LOCATION_UPDATED");
        registerReceiver(locationUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    private void checkAndRequestPermissions() {
        List<String> needPermissions = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            needPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            needPermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
            needPermissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            needPermissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        if (!needPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    needPermissions.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE);
        } else {
            Toast.makeText(this, "权限已具备", Toast.LENGTH_SHORT).show();
            loadAndDrawExistingTrack();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                Toast.makeText(this, "权限已获取", Toast.LENGTH_SHORT).show();
                loadAndDrawExistingTrack();
            } else {
                Toast.makeText(this, "部分权限被拒绝，部分功能可能不可用", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void loadAndDrawExistingTrack() {
        trackPointsForMap.clear();
        List<TrackPoint> points = trackManager.getTrackPoints();
        if (points != null && !points.isEmpty()) {
            for (TrackPoint point : points) {
                trackPointsForMap.add(new LatLng(point.getLatitude(), point.getLongitude()));
            }
            drawPolyline();
            TrackPoint last = points.get(points.size() - 1);
            moveCameraTo(last.getLatitude(), last.getLongitude());
        }
    }

    private void startNativeTracking() {
        if (isNativeTracking) {
            Toast.makeText(this, "原生定位已在运行", Toast.LENGTH_SHORT).show();
            return;
        }
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            new AlertDialog.Builder(this)
                    .setTitle("提示")
                    .setMessage("请开启GPS定位服务以获得更精确的轨迹记录")
                    .setPositiveButton("去设置", (d, w) ->
                            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                    .setNegativeButton("取消", null)
                    .show();
        }
        TrackingService.start(this);
        isNativeTracking = true;
        tvStatus.setText("状态：原生定位轨迹记录中");
        tvStatus.setTextColor(Color.GREEN);
        Toast.makeText(this, "原生定位已启动，退到后台仍会持续记录", Toast.LENGTH_LONG).show();
    }

    private void startBaiduLocationTracking() {
        if (isBaiduLocationStarted) {
            Toast.makeText(this, "百度定位已在运行", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            locationClient = new LocationClient(getApplicationContext());
        } catch (Exception e) {
            Log.e(TAG, "Failed to create LocationClient", e);
            Toast.makeText(this, "百度定位初始化失败", Toast.LENGTH_SHORT).show();
            return;
        }
        locationClient.registerLocationListener(new BDAbstractLocationListener() {
            @Override
            public void onReceiveLocation(BDLocation location) {
                if (location != null && (location.getLocType() == BDLocation.TypeGpsLocation
                        || location.getLocType() == BDLocation.TypeNetWorkLocation)) {
                    double lat = location.getLatitude();
                    double lng = location.getLongitude();
                    TrackPoint point = new TrackPoint(
                            lat, lng, location.getRadius(), location.getSpeed(),
                            System.currentTimeMillis(), "Baidu");
                    trackManager.addTrackPoint(point);
                    updateMapWithNewPoint(lat, lng);
                    updateUI();
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
        option.setOpenGps(true);
        option.setCoorType("bd09ll");
        option.setLocationPurpose(LocationClientOption.BDLocationPurpose.Sport);

        locationClient.setLocOption(option);
        try {
            locationClient.start();
            isBaiduLocationStarted = true;
            tvStatus.setText("状态：百度定位轨迹记录中");
            tvStatus.setTextColor(Color.BLUE);
            Toast.makeText(this, "百度定位已启动（仅前台）", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Failed to start Baidu location client", e);
            Toast.makeText(this, "百度定位启动失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopAllTracking() {
        if (isNativeTracking) {
            TrackingService.stop(this);
            isNativeTracking = false;
        }
        if (isBaiduLocationStarted && locationClient != null && locationClient.isStarted()) {
            locationClient.stop();
            isBaiduLocationStarted = false;
        }
        tvStatus.setText("状态：已停止");
        tvStatus.setTextColor(Color.GRAY);
        Toast.makeText(this, "所有定位已停止，轨迹已保存", Toast.LENGTH_SHORT).show();
    }

    private void clearTrajectory() {
        new AlertDialog.Builder(this)
                .setTitle("确认清除")
                .setMessage("确定要清除所有轨迹点吗？")
                .setPositiveButton("确定", (dialog, which) -> {
                    trackManager.clearTrackPoints();
                    trackPointsForMap.clear();
                    if (currentPolyline != null) {
                        currentPolyline.remove();
                        currentPolyline = null;
                    }
                    baiduMap.clear();
                    tvPointCount.setText("已记录轨迹点: 0");
                    tvLatLng.setText("最后位置: 暂无");
                    Toast.makeText(this, "轨迹已清除", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateMapWithNewPoint(double latitude, double longitude) {
        LatLng newPoint = new LatLng(latitude, longitude);
        trackPointsForMap.add(newPoint);

        drawPolyline();
        addMarkerAtPoint(latitude, longitude);
        moveCameraTo(latitude, longitude);

        MyLocationData locData = new MyLocationData.Builder()
                .accuracy(10)
                .direction(0)
                .latitude(latitude)
                .longitude(longitude)
                .build();
        baiduMap.setMyLocationData(locData);

        trackManager.saveTrackPoints();

        runOnUiThread(() -> {
            tvLatLng.setText(String.format("最后位置: %.6f, %.6f", latitude, longitude));
            tvPointCount.setText("已记录轨迹点: " + trackPointsForMap.size());
        });
    }

    private void drawPolyline() {
        if (trackPointsForMap.size() < 2) return;
        if (currentPolyline != null) {
            currentPolyline.remove();
        }
        PolylineOptions polylineOptions = new PolylineOptions()
                .points(trackPointsForMap)
                .width(10)
                .color(Color.argb(255, 66, 133, 244));
        currentPolyline = (Polyline) baiduMap.addOverlay(polylineOptions);
    }

    private void addMarkerAtPoint(double latitude, double longitude) {
        LatLng point = new LatLng(latitude, longitude);
        OverlayOptions markerOptions = new MarkerOptions()
                .position(point)
                .title("轨迹点");
        baiduMap.addOverlay(markerOptions);
    }

    private void moveCameraTo(double latitude, double longitude) {
        LatLng center = new LatLng(latitude, longitude);
        MapStatusUpdate msu = MapStatusUpdateFactory.newLatLng(center);
        baiduMap.setMapStatus(msu);
    }

    private void updateUI() {
        runOnUiThread(() -> {
            int pointCount = trackManager.getPointCount();
            tvPointCount.setText("已记录轨迹点: " + pointCount);
            TrackPoint last = trackManager.getLastPoint();
            if (last != null) {
                tvLatLng.setText(String.format("最后位置: %.6f, %.6f",
                        last.getLatitude(), last.getLongitude()));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
        updateUI();
        if (!trackPointsForMap.isEmpty() && currentPolyline == null) {
            drawPolyline();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (locationUpdateReceiver != null) {
            unregisterReceiver(locationUpdateReceiver);
        }
        stopAllTracking();
        trackManager.saveTrackPoints();
        baiduMap.setMyLocationEnabled(false);
        mapView.onDestroy();
    }
}