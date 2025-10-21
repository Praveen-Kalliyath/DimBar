package com.code2consciousness.dimbar;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.core.app.NotificationCompat;

public class DimOverlayService extends Service {
    private WindowManager windowManager;
    private View overlayView;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification createNotification() {
        String channelId = "dim_overlay_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "DimBar", NotificationManager.IMPORTANCE_MIN);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
        Intent stopIntent = new Intent(this, DimOverlayService.class);
        stopIntent.setAction("STOP");
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        // Pause action
        Intent pauseIntent = new Intent(this, DimOverlayService.class);
        pauseIntent.setAction("PAUSE");
        PendingIntent pausePendingIntent = PendingIntent.getService(this, 1, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        // Preset dim actions
        Intent lowIntent = new Intent(this, DimOverlayService.class);
        lowIntent.setAction("DIM_LOW");
        PendingIntent lowPendingIntent = PendingIntent.getService(this, 2, lowIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent mediumIntent = new Intent(this, DimOverlayService.class);
        mediumIntent.setAction("DIM_MEDIUM");
        PendingIntent mediumPendingIntent = PendingIntent.getService(this, 3, mediumIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent highIntent = new Intent(this, DimOverlayService.class);
        highIntent.setAction("DIM_HIGH");
        PendingIntent highPendingIntent = PendingIntent.getService(this, 4, highIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        // Close action
        Intent closeIntent = new Intent(this, DimOverlayService.class);
        closeIntent.setAction("CLOSE");
        PendingIntent closePendingIntent = PendingIntent.getService(this, 5, closeIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setContentTitle("Screen Dimmer Active")
                .setContentText("Tap to adjust or close dimming.")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_media_pause, "Pause", pausePendingIntent)
                .addAction(android.R.drawable.ic_menu_manage, "Low", lowPendingIntent)
                .addAction(android.R.drawable.ic_menu_manage, "Medium", mediumPendingIntent)
                .addAction(android.R.drawable.ic_menu_manage, "High", highPendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close", closePendingIntent);
        return builder.build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case "STOP":
                case "CLOSE":
                    stopSelf();
                    return START_NOT_STICKY;
                case "PAUSE":
                    showOverlay(0f);
                    return START_STICKY;
                case "DIM_LOW":
                    showOverlay(0.2f);
                    return START_STICKY;
                case "DIM_MEDIUM":
                    showOverlay(0.5f);
                    return START_STICKY;
                case "DIM_HIGH":
                    showOverlay(0.8f);
                    return START_STICKY;
            }
        }
        float dimAmount = 0.5f; // Default dim
        if (intent != null && intent.hasExtra("dim_amount")) {
            dimAmount = intent.getFloatExtra("dim_amount", 0.5f);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(1, createNotification());
        }
        showOverlay(dimAmount);
        return START_STICKY;
    }

    private void showOverlay(float dimAmount) {
        if (windowManager == null) {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        }
        if (overlayView != null) {
            windowManager.removeView(overlayView);
        }
        overlayView = new FrameLayout(this);
        overlayView.setBackgroundColor(0xFF000000);
        overlayView.setAlpha(dimAmount);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        windowManager.addView(overlayView, params);
    }

    public void updateDim(float dimAmount) {
        if (overlayView != null) {
            overlayView.setAlpha(dimAmount);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (windowManager != null && overlayView != null) {
            windowManager.removeView(overlayView);
        }
    }
}
