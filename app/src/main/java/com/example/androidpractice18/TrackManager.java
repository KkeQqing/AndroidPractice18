package com.example.androidpractice18;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class TrackManager {
    private static final String TAG = "TrackManager";
    private static final String PREFS_NAME = "track_data";
    private static final String KEY_TRACK_POINTS = "track_points";

    private static TrackManager instance;
    private Context context;
    private List<TrackPoint> trackPoints;
    private List<TrackPoint> unSavedPoints;

    private TrackManager(Context context) {
        this.context = context.getApplicationContext();
        loadTrackPoints();
        this.unSavedPoints = new ArrayList<>();
    }

    public static synchronized TrackManager getInstance(Context context) {
        if (instance == null) {
            instance = new TrackManager(context);
        }
        return instance;
    }

    // 添加新的轨迹点
    public void addTrackPoint(TrackPoint point) {
        if (point == null) return;
        trackPoints.add(point);
        unSavedPoints.add(point);
        Log.d(TAG, "Added track point: (" + point.getLatitude() + ", " + point.getLongitude() + ")");
    }

    // 获取所有轨迹点
    public List<TrackPoint> getTrackPoints() {
        return new ArrayList<>(trackPoints);
    }

    // 清除所有轨迹点
    public void clearTrackPoints() {
        trackPoints.clear();
        unSavedPoints.clear();
        saveTrackPoints();
    }

    // 获取最后的位置
    public TrackPoint getLastPoint() {
        if (trackPoints.isEmpty()) return null;
        return trackPoints.get(trackPoints.size() - 1);
    }

    // 获取未保存到 SharedPreferences 的新点（用于不频繁持久化）
    public List<TrackPoint> getUnSavedPoints() {
        return new ArrayList<>(unSavedPoints);
    }

    public void markPointsAsSaved() {
        unSavedPoints.clear();
    }

    // 保存轨迹点到 SharedPreferences
    public void saveTrackPoints() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Gson gson = new Gson();
        String json = gson.toJson(trackPoints);
        prefs.edit().putString(KEY_TRACK_POINTS, json).apply();
        Log.d(TAG, "Saved " + trackPoints.size() + " track points");
    }

    // 加载轨迹点
    private void loadTrackPoints() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_TRACK_POINTS, null);
        Gson gson = new Gson();
        if (json != null) {
            Type type = new TypeToken<ArrayList<TrackPoint>>(){}.getType();
            trackPoints = gson.fromJson(json, type);
            if (trackPoints == null) {
                trackPoints = new ArrayList<>();
            }
        } else {
            trackPoints = new ArrayList<>();
        }
        Log.d(TAG, "Loaded " + trackPoints.size() + " track points");
    }

    public int getPointCount() {
        return trackPoints.size();
    }
}