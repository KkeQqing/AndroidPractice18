package com.example.androidpractice18;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TrackPoint {
    private double latitude;   // 纬度
    private double longitude;  // 经度
    private float accuracy;    // 精度（米）
    private float speed;       // 速度（米/秒）
    private long timestamp;    // 时间戳
    private String provider;   // 定位来源（GPS/Network/Baidu）

    public TrackPoint() {}

    public TrackPoint(double latitude, double longitude, float accuracy, float speed, long timestamp, String provider) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracy = accuracy;
        this.speed = speed;
        this.timestamp = timestamp;
        this.provider = provider;
    }

    // Getters and Setters
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public float getAccuracy() { return accuracy; }
    public void setAccuracy(float accuracy) { this.accuracy = accuracy; }
    public float getSpeed() { return speed; }
    public void setSpeed(float speed) { this.speed = speed; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getFormattedTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }
}