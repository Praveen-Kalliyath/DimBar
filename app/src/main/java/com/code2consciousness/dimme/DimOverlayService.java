package com.code2consciousness.dimme;

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
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Toast;
import android.widget.RemoteViews;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class DimOverlayService extends Service {

    private WindowManager windowManager;
    private FrameLayout dimOverlayView;

    // Floating controls (moved from MainActivity)
    private LinearLayout floatingControls;
    private WindowManager.LayoutParams floatingParams;

    static float currentDim = 0.5f;
    private boolean isPaused = false;
    private BroadcastReceiver pauseStateReceiver;

    private static final String CHANNEL_ID = "dim_overlay_channel";

    // Public actions (kept same)
    public static final String ACTION_SEND_CURRENT_DIM = "com.code2consciousness.dimme.ACTION_SEND_CURRENT_DIM";
    public static final String ACTION_REQUEST_DIM = "com.code2consciousness.dimme.ACTION_REQUEST_DIM";
    public static final String ACTION_PAUSE_STATE_CHANGED = "com.code2consciousness.dimme.ACTION_PAUSE_STATE_CHANGED";
    public static final String EXTRA_IS_PAUSED = "is_paused";

    // Internal service actions
    private static final String ACTION_SHOW_FLOATING = "SHOW_FLOATING";

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

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
                    // update floating UI pause icon if visible
                    updateFloatingPauseState();
                }
            }
        };
        LocalBroadcastManager.getInstance(this)
                .registerReceiver(pauseStateReceiver, new IntentFilter(ACTION_PAUSE_STATE_CHANGED));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Always start as foreground first
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                startForeground(1, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else
                startForeground(1, createNotification());
        } catch (Exception e) {
            e.printStackTrace();
        }

        String action = intent != null ? intent.getAction() : null;

        if (action != null) {
            switch (action) {
                case "CLOSE":
                    removeFloatingControls();
                    removeDimOverlay();
                    stopSelf();
                    LocalBroadcastManager.getInstance(this)
                            .sendBroadcast(new Intent("com.code2consciousness.dimme.ACTION_CLOSE_APP"));
                    return START_NOT_STICKY;

                case "PAUSE":
                    isPaused = !isPaused;
                    updateDim(isPaused ? 0f : currentDim);
                    sendPauseBroadcast(isPaused);
                    updateNotification();
                    updateFloatingPauseState();
                    return START_STICKY;

                case "UPDATE_DIM":
                    currentDim = intent.getFloatExtra("dim_amount", currentDim);
                    if (!isPaused) updateDim(currentDim);
                    updateNotification();
                    return START_STICKY;

                case "PLUS":
                    if (!isPaused) {
                        currentDim -= 0.05f;
                        if (currentDim < 0f) currentDim = 0f;
                        updateDim(currentDim);
                        notifyDimChange();
                        updateNotification();
                    }
                    return START_STICKY;

                case "MINUS":
                    if (!isPaused) {
                        updateDim(currentDim);
                        currentDim += 0.05f;
                        if (currentDim > 1f) currentDim = 1f;
                        notifyDimChange();
                        updateNotification();
                    }
                    return START_STICKY;

                case "REQUEST_CURRENT_DIM":
                    Intent dimIntent = new Intent(ACTION_SEND_CURRENT_DIM);
                    dimIntent.putExtra("dim_amount", currentDim);
                    LocalBroadcastManager.getInstance(this).sendBroadcast(dimIntent);
                    return START_STICKY;

                case "REQUEST_DIM":
                    notifyDimChange();
                    return START_STICKY;

                case ACTION_SHOW_FLOATING:
                    // Ensure floating controls are visible / created
                    if (!Settings.canDrawOverlays(this)) {
                        // If overlay permission missing, try to open settings via a toast hint.
                        // We can't start Settings activity from Service easily; the LauncherActivity will handle permission on cold start.
                        Toast.makeText(this, "Overlay permission required. Open the app to grant permission.", Toast.LENGTH_SHORT).show();
                        return START_STICKY;
                    }
                    showFloatingControls(); // shows or recreates
                    return START_STICKY;
            }
        }

        // Default behavior: start foreground and show dim overlay
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            startForeground(1, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else
            startForeground(1, createNotification());

        new android.os.Handler(getMainLooper()).postDelayed(() -> {
            if (!isPaused) showDimOverlay(currentDim);
            showFloatingControls();
        }, 500); // half-second delay

        return START_STICKY;
    }

    private void notifyDimChange() {
        Intent intent = new Intent("com.code2consciousness.dimme.ACTION_DIM_CHANGED");
        intent.putExtra("dim_amount", currentDim);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null && manager.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                        "DimMe Overlay", NotificationManager.IMPORTANCE_LOW);
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

        // Changed: tapping notification will send intent to the service to SHOW_FLOATING
        PendingIntent openServicePending = PendingIntent.getService(
                this,
                0,
                new Intent(this, DimOverlayService.class).setAction(ACTION_SHOW_FLOATING),
                flags
        );

        PendingIntent pausePending = PendingIntent.getService(this, 1,
                new Intent(this, DimOverlayService.class).setAction("PAUSE"), flags);

        PendingIntent stopPending = PendingIntent.getService(this, 2,
                new Intent(this, DimOverlayService.class).setAction("CLOSE"), flags);

        PendingIntent plusPending = PendingIntent.getService(this, 3,
                new Intent(this, DimOverlayService.class).setAction("PLUS"), flags);

        PendingIntent minusPending = PendingIntent.getService(this, 4,
                new Intent(this, DimOverlayService.class).setAction("MINUS"), flags);

        RemoteViews layout = new RemoteViews(getPackageName(), R.layout.notification_dimme);

        layout.setOnClickPendingIntent(R.id.btn_pause, pausePending);
        layout.setOnClickPendingIntent(R.id.btn_close, stopPending);
        layout.setOnClickPendingIntent(R.id.btn_plus, plusPending);
        layout.setOnClickPendingIntent(R.id.btn_minus, minusPending);
        layout.setOnClickPendingIntent(R.id.icon_dimme, openServicePending);

        layout.setImageViewResource(R.id.btn_pause, isPaused ? R.drawable.ic_play : R.drawable.ic_pause);
        layout.setInt(R.id.btn_pause, "setColorFilter", isPaused ? Color.GREEN : Color.parseColor("#FFC107"));
        layout.setInt(R.id.btn_close, "setColorFilter", Color.parseColor("#FFC107"));

        boolean atMin = currentDim <= 0.0f;
        boolean atMax = currentDim >= 1.0f;

        layout.setInt(R.id.btn_minus, "setColorFilter", atMax ? Color.LTGRAY : Color.parseColor("#FFC107"));
        layout.setInt(R.id.btn_plus, "setColorFilter", atMin ? Color.LTGRAY : Color.parseColor("#FFC107"));

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setCustomContentView(layout)
                .setContentIntent(openServicePending)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build();
    }

    // --- DIM OVERLAY (fullscreen) ---
    private void showDimOverlay(float dimAmount) {
        if (windowManager == null) windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (dimOverlayView != null) windowManager.removeView(dimOverlayView);

        dimOverlayView = new FrameLayout(this);
        dimOverlayView.setBackgroundColor(0xFF000000);
        dimOverlayView.setAlpha(dimAmount);

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
        windowManager.addView(dimOverlayView, params);
    }

    public void updateDim(float dimAmount) {
        if (dimOverlayView != null) dimOverlayView.setAlpha(dimAmount);
        else showDimOverlay(dimAmount);
    }

    private void removeDimOverlay() {
        if (windowManager != null && dimOverlayView != null) {
            try {
                windowManager.removeView(dimOverlayView);
            } catch (IllegalArgumentException ignored) {
            }
            dimOverlayView = null;
        }
    }

    // --- FLOATING CONTROLS (seekbar + pause + stop + drag) ---
    private void showFloatingControls() {
        if (!Settings.canDrawOverlays(this)) {
            // cannot show overlay without permission
            return;
        }

        if (windowManager == null) windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        if (floatingControls != null && floatingControls.getParent() != null) {
            // already shown; ensure visible
            floatingControls.setVisibility(View.VISIBLE);
            return;
        }

        // create UI programmatically (kept visually same as your MainActivity)
        floatingControls = new LinearLayout(this);
        floatingControls.setOrientation(LinearLayout.VERTICAL);
        floatingControls.setBackgroundColor(0x00FFFFFF); // transparent container

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.HORIZONTAL);
        inner.setPadding(24, 12, 24, 12);

        GradientDrawable bgDrawable = new GradientDrawable();
        bgDrawable.setColor(Color.parseColor("#AA444444"));
        bgDrawable.setCornerRadius(96f);
        bgDrawable.setStroke(3, Color.parseColor("#FFC107"));
        inner.setBackground(bgDrawable);
        inner.setGravity(Gravity.CENTER_VERTICAL);

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(100);
        try {
            seekBar.setProgressDrawable(getResources().getDrawable(R.drawable.custom_seekbar));
            seekBar.getThumb().setColorFilter(Color.YELLOW, PorterDuff.Mode.SRC_IN);
        } catch (Exception ignored) {
        }
        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(500, WindowManager.LayoutParams.WRAP_CONTENT);
        seekParams.gravity = Gravity.CENTER_VERTICAL;
        seekBar.setLayoutParams(seekParams);
        seekBar.setProgress((int) ((1 - currentDim) * 100));

        ImageButton pauseButton = new ImageButton(this);
        pauseButton.setImageResource(isPaused ? R.drawable.ic_play : R.drawable.ic_pause);
        pauseButton.setBackgroundColor(Color.TRANSPARENT);
        pauseButton.setColorFilter(isPaused ? Color.GREEN : Color.parseColor("#FFC107"), PorterDuff.Mode.SRC_IN);
        LinearLayout.LayoutParams pauseParams = new LinearLayout.LayoutParams(90, 90);
        pauseParams.leftMargin = 16;
        pauseButton.setLayoutParams(pauseParams);

        ImageButton splitter = new ImageButton(this);
        splitter.setImageResource(android.R.drawable.divider_horizontal_bright);
        splitter.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams splitterParams = new LinearLayout.LayoutParams(24, 60);
        splitter.setLayoutParams(splitterParams);

        ImageButton stopButton = new ImageButton(this);
        stopButton.setImageResource(android.R.drawable.ic_lock_power_off);
        stopButton.setBackgroundColor(Color.TRANSPARENT);
        stopButton.setColorFilter(Color.parseColor("#FFC107"), PorterDuff.Mode.SRC_IN);
        stopButton.setScaleX(1.5f);
        stopButton.setScaleY(1.5f);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(100, 60);
        btnParams.leftMargin = 32;
        stopButton.setLayoutParams(btnParams);

        ImageButton minimizeButton = new ImageButton(this);
        minimizeButton.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        minimizeButton.setBackgroundColor(Color.TRANSPARENT);
        minimizeButton.setColorFilter(Color.LTGRAY, PorterDuff.Mode.SRC_IN);

        inner.addView(minimizeButton);
        inner.addView(seekBar);
        inner.addView(pauseButton);
        inner.addView(splitter);
        inner.addView(stopButton);

        floatingControls.addView(inner);

        // Setup floating params
        floatingParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                        WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );
        floatingParams.gravity = Gravity.CENTER;
        // initial position (center)
        floatingParams.x = 0;
        floatingParams.y = 0;

        // Add to window
        try {
            windowManager.addView(floatingControls, floatingParams);
        } catch (Exception e) {
            // guard
            e.printStackTrace();
        }

        // SeekBar listener
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (!isPaused) {
                    float dimAmount = (100 - progress) / 100f;
                    currentDim = dimAmount;
                    updateDim(dimAmount);
                    updateNotification();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
            }
        });

        // Pause / Play toggle
        pauseButton.setOnClickListener(v -> {
            // toggle via service action to keep consistent
            Intent pauseIntent = new Intent(this, DimOverlayService.class).setAction("PAUSE");
            startService(pauseIntent);
            // update icon locally immediately
            pauseButton.setImageResource(isPaused ? R.drawable.ic_play : R.drawable.ic_pause);
        });

        // Stop overlay
        stopButton.setOnClickListener(v -> {
            Intent stopIntent = new Intent(this, DimOverlayService.class).setAction("CLOSE");
            startService(stopIntent);
        });

        // Minimize floating controls
        minimizeButton.setOnClickListener(v -> {
            if (floatingControls != null) {
                floatingControls.setVisibility(View.GONE);
                Toast.makeText(this, "DimMe minimized. Tap the notification to reopen.", Toast.LENGTH_SHORT).show();
            }
        });

        // Drag handling
        floatingControls.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (floatingParams == null) return false;
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // Make interactive
                        floatingParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                        try {
                            windowManager.updateViewLayout(floatingControls, floatingParams);
                        } catch (Exception ignored) {
                        }
                        initialX = floatingParams.x;
                        initialY = floatingParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        floatingParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                        floatingParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                        try {
                            windowManager.updateViewLayout(floatingControls, floatingParams);
                        } catch (Exception ignored) {
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        // revert
                        floatingParams.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                        try {
                            windowManager.updateViewLayout(floatingControls, floatingParams);
                        } catch (Exception ignored) {
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void updateFloatingPauseState() {
        if (floatingControls == null) return;
        // Update pause icon/color inside floatingControls if present
        View inner = floatingControls.getChildAt(0);
        if (inner instanceof LinearLayout) {
            LinearLayout row = (LinearLayout) inner;
            // we know structure: minimize (0), seekBar (1), pause (2), splitter, stop
            if (row.getChildCount() >= 3) {
                View pauseView = row.getChildAt(2);
                if (pauseView instanceof ImageButton) {
                    ImageButton pb = (ImageButton) pauseView;
                    pb.setImageResource(isPaused ? R.drawable.ic_play : R.drawable.ic_pause);
                    pb.setColorFilter(isPaused ? Color.GREEN : Color.parseColor("#FFC107"), PorterDuff.Mode.SRC_IN);
                }
            }
        }
    }

    private void removeFloatingControls() {
        if (windowManager != null && floatingControls != null) {
            try {
                windowManager.removeView(floatingControls);
            } catch (IllegalArgumentException ignored) {
            }
            floatingControls = null;
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
        removeDimOverlay();
        removeFloatingControls();
        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        removeDimOverlay();
        removeFloatingControls();
        if (pauseStateReceiver != null)
            LocalBroadcastManager.getInstance(this).unregisterReceiver(pauseStateReceiver);
    }
}
