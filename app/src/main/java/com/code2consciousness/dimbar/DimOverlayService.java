package com.code2consciousness.dimbar;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
    private boolean isPaused = false;
    private BroadcastReceiver pauseStateReceiver;
    private static final String CHANNEL_ID = "dim_overlay_channel";
    public static final String ACTION_SEND_CURRENT_DIM = "com.code2consciousness.dimbar.ACTION_SEND_CURRENT_DIM";

    public static final String ACTION_PAUSE_STATE_CHANGED = "com.code2consciousness.dimbar.ACTION_PAUSE_STATE_CHANGED";
    public static final String EXTRA_IS_PAUSED = "is_paused";

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        pauseStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_PAUSE_STATE_CHANGED.equals(intent.getAction())) {
                    isPaused = intent.getBooleanExtra(EXTRA_IS_PAUSED, false);
                    float dimAmount = isPaused ? 0f : currentDim;
                    updateDim(dimAmount);
                    updateNotification();
                }
            }
        };
        LocalBroadcastManager.getInstance(this)
                .registerReceiver(pauseStateReceiver, new IntentFilter(ACTION_PAUSE_STATE_CHANGED));
    }

    // Only the onStartCommand method is updated with new "REQUEST_CURRENT_DIM" action
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case "CLOSE":
                    removeOverlay();
                    stopSelf();
                    LocalBroadcastManager.getInstance(this)
                            .sendBroadcast(new Intent("com.code2consciousness.dimbar.ACTION_CLOSE_APP"));
                    return START_NOT_STICKY;

                case "PAUSE":
                    isPaused = !isPaused;
                    updateDim(isPaused ? 0f : currentDim);
                    sendPauseBroadcast(isPaused);
                    updateNotification();
                    return START_STICKY;

                case "UPDATE_DIM":
                    currentDim = intent.getFloatExtra("dim_amount", currentDim);
                    if (!isPaused) updateDim(currentDim);
                    updateNotification();
                    return START_STICKY;

                case "PLUS":
                    currentDim -= 0.05f;
                    if (currentDim < 0f) currentDim = 0f;
                    updateDim(currentDim);
                    notifyDimChange();
                    updateNotification();
                    return START_STICKY;

                case "MINUS":
                    currentDim += 0.05f;
                    if (currentDim > 1f) currentDim = 1f;
                    updateDim(currentDim);
                    notifyDimChange();
                    updateNotification();
                    return START_STICKY;

                // ✅ NEW: Request current dim
                case "REQUEST_CURRENT_DIM":
                    Intent dimIntent = new Intent(ACTION_SEND_CURRENT_DIM);
                    dimIntent.putExtra("dim_amount", currentDim);
                    LocalBroadcastManager.getInstance(this).sendBroadcast(dimIntent);
                    return START_STICKY;

            }
        }

        // Start foreground notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            startForeground(1, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else
            startForeground(1, createNotification());

        if (!isPaused) showOverlay(currentDim);
        return START_STICKY;
    }

    private void notifyDimChange() {
        Intent intent = new Intent("com.code2consciousness.dimbar.ACTION_DIM_CHANGED");
        intent.putExtra("dim_amount", currentDim);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null && manager.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                        "DimBar Overlay", NotificationManager.IMPORTANCE_LOW);
                channel.setShowBadge(false);
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        return buildNotification();
    }

    private void updateNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.notify(1, buildNotification());
    }

    private Notification buildNotification() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags |= PendingIntent.FLAG_IMMUTABLE;

        // === Intents ===
        PendingIntent openAppPending = PendingIntent.getActivity(
                this,
                0,
                new Intent(this, MainActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT |
                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        PendingIntent pausePending = PendingIntent.getService(this, 1,
                new Intent(this, DimOverlayService.class).setAction("PAUSE"), flags);

        PendingIntent stopPending = PendingIntent.getService(this, 2,
                new Intent(this, DimOverlayService.class).setAction("CLOSE"), flags);

        PendingIntent plusPending = PendingIntent.getService(this, 3,
                new Intent(this, DimOverlayService.class).setAction("PLUS"), flags);

        PendingIntent minusPending = PendingIntent.getService(this, 4,
                new Intent(this, DimOverlayService.class).setAction("MINUS"), flags);

        // === Notification Layout ===
        RemoteViews layout = new RemoteViews(getPackageName(), R.layout.notification_dimbar);

        layout.setOnClickPendingIntent(R.id.btn_pause, pausePending);
        layout.setOnClickPendingIntent(R.id.btn_close, stopPending);
        layout.setOnClickPendingIntent(R.id.btn_plus, plusPending);
        layout.setOnClickPendingIntent(R.id.btn_minus, minusPending);
        layout.setOnClickPendingIntent(R.id.icon_dimbar, openAppPending);

        // Pause button visuals
        layout.setImageViewResource(R.id.btn_pause, isPaused ? R.drawable.ic_play : R.drawable.ic_pause);
        layout.setInt(R.id.btn_pause, "setColorFilter", isPaused ? Color.GREEN : Color.parseColor("#FFC107"));
        layout.setInt(R.id.btn_close, "setColorFilter", Color.parseColor("#FFC107"));

        // === New: Grey-out + / − buttons ===
        boolean atMin = currentDim <= 0.0f;
        boolean atMax = currentDim >= 1.0f;

        layout.setInt(R.id.btn_minus, "setColorFilter", atMax ? Color.LTGRAY : Color.parseColor("#FFC107"));
        layout.setInt(R.id.btn_plus, "setColorFilter", atMin ? Color.LTGRAY : Color.parseColor("#FFC107"));

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setCustomContentView(layout)
                .setContentIntent(openAppPending)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build();
    }

    private void showOverlay(float dimAmount) {
        if (windowManager == null) windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;

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
        if (overlayView != null) overlayView.setAlpha(dimAmount);
        else showOverlay(dimAmount);
    }

    private void removeOverlay() {
        if (windowManager != null && overlayView != null) {
            windowManager.removeView(overlayView);
            overlayView = null;
        }
    }

    private void sendPauseBroadcast(boolean paused) {
        Intent intent = new Intent(ACTION_PAUSE_STATE_CHANGED);
        intent.putExtra(EXTRA_IS_PAUSED, paused);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        removeOverlay();
        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        removeOverlay();
        if (pauseStateReceiver != null)
            LocalBroadcastManager.getInstance(this).unregisterReceiver(pauseStateReceiver);
    }
}
