package com.code2consciousness.dimbar;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.RemoteViews;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class DimOverlayService extends Service {

    private WindowManager windowManager;
    private FrameLayout overlayView;
    private float currentDim = 0.5f;
    private static final String CHANNEL_ID = "dim_overlay_channel";

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Handle notification actions
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case "CLOSE":
                    removeOverlay();
                    stopSelf();
                    // Close MainActivity if open
                    LocalBroadcastManager.getInstance(this)
                            .sendBroadcast(new Intent("com.code2consciousness.dimbar.ACTION_CLOSE_APP"));
                    return START_NOT_STICKY;

                case "PAUSE":
                    updateDim(0f);
                    return START_STICKY;

                case "UPDATE_DIM":
                    float dimAmount = intent.getFloatExtra("dim_amount", currentDim);
                    updateDim(dimAmount);
                    return START_STICKY;
            }
        }

        // Start foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            startForeground(1, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, createNotification());
        }

        float dimAmount = (intent != null) ? intent.getFloatExtra("dim_amount", 0.5f) : 0.5f;
        currentDim = dimAmount;
        showOverlay(dimAmount);

        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null && manager.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "DimBar Overlay",
                        NotificationManager.IMPORTANCE_LOW
                );
                channel.setShowBadge(false);
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags |= PendingIntent.FLAG_IMMUTABLE;

        // Tap notification → open MainActivity
        Intent openAppIntent = new Intent(this, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openAppPending = PendingIntent.getActivity(this, 0, openAppIntent, flags);

        // Pause action
        PendingIntent pausePending = PendingIntent.getService(
                this, 1,
                new Intent(this, DimOverlayService.class).setAction("PAUSE"),
                flags
        );

        // Close action
        PendingIntent stopPending = PendingIntent.getService(
                this, 2,
                new Intent(this, DimOverlayService.class).setAction("CLOSE"),
                flags
        );

        // RemoteViews custom layout
        RemoteViews notificationLayout = new RemoteViews(getPackageName(), R.layout.notification_dimbar);
        notificationLayout.setOnClickPendingIntent(R.id.btn_pause, pausePending);
        notificationLayout.setOnClickPendingIntent(R.id.btn_close, stopPending);
        notificationLayout.setOnClickPendingIntent(R.id.icon_dimbar, openAppPending);
        notificationLayout.setInt(R.id.btn_pause, "setColorFilter", Color.GRAY); // Transparent bg
        notificationLayout.setInt(R.id.btn_close, "setColorFilter", Color.GRAY); // Transparent bg


        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_view)
//                .setContentTitle("DimBar Active")
//                .setContentText("Tap to adjust or close dimming")
                .setCustomContentView(notificationLayout)
//                .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                .setContentIntent(openAppPending)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                // ✅ Icon-only buttons (no text)
                .addAction(R.drawable.ic_pause, "", pausePending)
                .addAction(R.drawable.ic_power, "", stopPending)
                .build();
    }


    private void showOverlay(float dimAmount) {
        if (windowManager == null) {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        }
        if (overlayView != null) windowManager.removeView(overlayView);

        overlayView = new FrameLayout(this);
        overlayView.setBackgroundColor(0xFF000000);
        overlayView.setAlpha(dimAmount);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                        WindowManager.LayoutParams.FLAG_FULLSCREEN,
                PixelFormat.TRANSLUCENT
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        params.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

        params.gravity = Gravity.TOP | Gravity.START;
        windowManager.addView(overlayView, params);
    }

    public void updateDim(float dimAmount) {
        currentDim = dimAmount;
        if (overlayView != null) overlayView.setAlpha(dimAmount);
        else showOverlay(dimAmount);
    }

    private void removeOverlay() {
        if (windowManager != null && overlayView != null) {
            windowManager.removeView(overlayView);
            overlayView = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        removeOverlay();
    }
}
