package com.code2consciousness.dimbar;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
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
            if (manager != null) manager.createNotificationChannel(channel);
        }
        return new NotificationCompat.Builder(this, channelId)
                .setContentTitle("Screen Dimmer Active")
                .setContentText("Tap to adjust or close dimming.")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setOngoing(true)
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case "STOP":
                    removeOverlay();
                    stopSelf();
                    return START_NOT_STICKY;
                case "UPDATE_DIM":
                    float dimAmount = intent.getFloatExtra("dim_amount", 0.5f);
                    updateDim(dimAmount);
                    return START_STICKY;
            }
        }

        // Default start
        float dimAmount = 0.5f;
        if (intent != null && intent.hasExtra("dim_amount")) {
            dimAmount = intent.getFloatExtra("dim_amount", 0.5f);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14+
            startForeground(1, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(1, createNotification());
        }

        showOverlay(dimAmount);
        return START_STICKY;
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
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        windowManager.addView(overlayView, params);
    }

    public void updateDim(float dimAmount) {
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
