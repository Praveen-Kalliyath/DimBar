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
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Toast;
import android.widget.RemoteViews;
import android.util.Log;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class DimOverlayService extends Service {

    private static final String TAG = "DimOverlayService";

    private WindowManager windowManager;
    private FrameLayout dimOverlayView;

    // Floating controls (moved from MainActivity)
    private LinearLayout floatingControls;
    private WindowManager.LayoutParams floatingParams;
    // Generated ids for child views so we can find them reliably across OEMs
    private final int floatingSeekBarId = View.generateViewId();
    private final int floatingPauseButtonId = View.generateViewId();
    // Direct references to floating child views for robust updates
    private SeekBar floatingSeekBar;
    private ImageButton floatingPauseButton;

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

    private boolean isAppVisible = false;

    public void setAppVisible(boolean visible) {
        isAppVisible = visible;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        createNotificationChannel();

        LocalBroadcastManager.getInstance(this).registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("APP_VISIBLE".equals(intent.getAction())) {
                    isAppVisible = true;
                    removeFloatingControls(); // hide service floating UI if app is in foreground
                }
            }
        }, new IntentFilter("APP_VISIBLE"));

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
        // On Android 13+ we must have POST_NOTIFICATIONS runtime permission to show notifications.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                // Launch an Activity that will request the permission from the user
                Intent permIntent = new Intent(this, NotificationPermissionActivity.class);
                permIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(permIntent);
                Log.i(TAG, "Notification permission missing — launched NotificationPermissionActivity and stopping service until permission is granted.");
                stopSelf();
                return START_NOT_STICKY;
            }
        }

        // Always start as foreground first
        if (!isAppVisible) {
            showFloatingControls();
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(1, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE);
            } else {
                startForeground(1, createNotification());
            }
        } catch (Exception e) {
            Log.e(TAG, "startForeground failed", e);
        }

        // Make sure the notification is pushed/updated immediately so action buttons render
        updateNotification();

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
                        updateFloatingSeekBar();
                    }
                    return START_STICKY;

                case "MINUS":
                    if (!isPaused) {
                        currentDim += 0.05f;
                        if (currentDim > 1f) currentDim = 1f;

                        updateDim(currentDim);
                        notifyDimChange();
                        updateNotification();
                        updateFloatingSeekBar();
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
                    if (!Settings.canDrawOverlays(this)) {
                        Toast.makeText(this, "Overlay permission required. Open the app to grant permission.", Toast.LENGTH_SHORT).show();
                        return START_STICKY;
                    }
                    if (!isAppVisible) showFloatingControls();
                    return START_STICKY;
            }
        }

        // Default behavior: start foreground and show dim overlay
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            startForeground(1, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else
            startForeground(1, createNotification());

        // Refresh the notification right after starting foreground to encourage immediate rendering of actions
        updateNotification();

        new android.os.Handler(getMainLooper()).postDelayed(() -> {
            if (!isPaused) showDimOverlay(currentDim);
            if (!isAppVisible) showFloatingControls();
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
                // Use HIGH importance so the foreground notification is visible in the panel
                NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                        "DimMe Overlay", NotificationManager.IMPORTANCE_HIGH);
                channel.setDescription("DimMe: screen dimming overlay (tap to open)");
                channel.setShowBadge(false);
                // Make sure the notification is visible on the lock screen
                channel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);
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

        // Changed: tapping notification will open the main app activity (safer/best-practice)
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

        Intent openAppIntent = new Intent(this, MainActivity.class);
        openAppIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openAppPending = PendingIntent.getActivity(
                this,
                0,
                openAppIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ?
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE :
                        PendingIntent.FLAG_UPDATE_CURRENT
        );

        RemoteViews layout = new RemoteViews(getPackageName(), R.layout.notification_dimme);

        layout.setOnClickPendingIntent(R.id.btn_pause, pausePending);
        // btn_close is now a container (icon + label) so hook the click to the container id
        layout.setOnClickPendingIntent(R.id.btn_close, stopPending);
        layout.setOnClickPendingIntent(R.id.btn_plus, plusPending);
        layout.setOnClickPendingIntent(R.id.btn_minus, minusPending);
        layout.setOnClickPendingIntent(R.id.icon_dimme, openServicePending);

        // NOTE: RemoteViews.setImageViewResource will not reliably load vector drawables
        // on all devices because the system process inflates the RemoteViews without
        // AppCompat vector support. Render the drawable to a Bitmap and use
        // setImageViewBitmap to ensure the icon updates correctly.
        try {
            android.graphics.drawable.Drawable d = ContextCompat.getDrawable(this, isPaused ? R.drawable.ic_play : R.drawable.ic_pause);
            if (d != null) {
                // Tint the drawable before drawing so RemoteViews shows the expected color.
                int tintColor = isPaused ? Color.GREEN : Color.parseColor("#FFC107");
                try {
                    android.graphics.drawable.Drawable wrapped = androidx.core.graphics.drawable.DrawableCompat.wrap(d).mutate();
                    androidx.core.graphics.drawable.DrawableCompat.setTint(wrapped, tintColor);
                    d = wrapped;
                } catch (Throwable ignored) {
                }

                android.graphics.Bitmap bmp;
                if (d instanceof android.graphics.drawable.BitmapDrawable) {
                    bmp = ((android.graphics.drawable.BitmapDrawable) d).getBitmap();
                } else {
                    int w = d.getIntrinsicWidth() > 0 ? d.getIntrinsicWidth() : 48;
                    int h = d.getIntrinsicHeight() > 0 ? d.getIntrinsicHeight() : 48;
                    bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
                    android.graphics.Canvas canvas = new android.graphics.Canvas(bmp);
                    d.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                    d.draw(canvas);
                }
                layout.setImageViewBitmap(R.id.btn_pause, bmp);
            } else {
                // fallback to resource if drawable lookup failed for any reason
                layout.setImageViewResource(R.id.btn_pause, isPaused ? R.drawable.ic_play : R.drawable.ic_pause);
            }
        } catch (Exception ex) {
            // last-resort fallback
            layout.setImageViewResource(R.id.btn_pause, isPaused ? R.drawable.ic_play : R.drawable.ic_pause);
        }
        // Attempt to color the pause icon; keep this as an additional hint but the bitmap above
        // already contains the drawable color. Some OEMs may ignore setInt on RemoteViews.
        layout.setInt(R.id.btn_pause, "setColorFilter", isPaused ? Color.GREEN : Color.parseColor("#FFC107"));
        // color the close icon/view
        layout.setInt(R.id.btn_close, "setColorFilter", Color.parseColor("#FFC107"));

        boolean atMin = currentDim <= 0.0f;
        boolean atMax = currentDim >= 1.0f;

        layout.setInt(R.id.btn_minus, "setColorFilter", atMax ? Color.LTGRAY : Color.parseColor("#FFC107"));
        layout.setInt(R.id.btn_plus, "setColorFilter", atMin ? Color.LTGRAY : Color.parseColor("#FFC107"));

        // Build notification with DecoratedCustomViewStyle and explicit actions so buttons show immediately
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("DimMe")
                .setContentText("Screen dimming active")
                .setCustomContentView(layout)
                // don't set DecoratedCustomViewStyle (prevents expand affordance)
                // and provide a normal content title/text as a reliable fallback
//                .setContentIntent(openServicePending)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        // Removed explicit actions: adding actions makes the notification show an expand affordance.
        // We rely on the RemoteViews click handlers (setOnClickPendingIntent) instead so the
        // collapsed notification displays only our custom content up to the close button.

        return builder.build();
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
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            params.flags |= WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS |
                          WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;

            dimOverlayView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );

            windowManager.addView(dimOverlayView, params);

            // Post the system UI flags update to ensure it takes effect
            dimOverlayView.post(() -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    final WindowInsetsController insetsController = dimOverlayView.getWindowInsetsController();
                    if (insetsController != null) {
                        insetsController.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                        insetsController.hide(WindowInsets.Type.systemBars());
                    }
                }
            });
        } else {
            params.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

            windowManager.addView(dimOverlayView, params);
        }

        params.gravity = Gravity.TOP | Gravity.START;
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
        floatingControls.setPadding(0,0,0,0);

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.HORIZONTAL);
        inner.setPadding(15, 2, 15, 2);

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
        seekBar.setId(floatingSeekBarId); // Set generated ID
        // keep reference for robust updates
        floatingSeekBar = seekBar;

        ImageButton pauseButton = new ImageButton(this);
        pauseButton.setImageResource(isPaused ? R.drawable.ic_play : R.drawable.ic_pause);
        pauseButton.setBackgroundColor(Color.TRANSPARENT);
        pauseButton.setColorFilter(isPaused ? Color.GREEN : Color.parseColor("#FFC107"), PorterDuff.Mode.SRC_IN);
        LinearLayout.LayoutParams pauseParams = new LinearLayout.LayoutParams(90, 90);
        pauseParams.leftMargin = 16;
        pauseButton.setLayoutParams(pauseParams);
        pauseButton.setId(floatingPauseButtonId); // Set generated ID
        // keep reference for robust updates
        floatingPauseButton = pauseButton;

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
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );

        // Handle display metrics for different screen sizes
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(metrics);

        // Adjust seekbar width based on screen width
        int seekBarWidth = Math.min(500, (int)(metrics.widthPixels * 0.7));
        if (seekBar != null) {
            LinearLayout.LayoutParams adjustedSeekParams = (LinearLayout.LayoutParams) seekBar.getLayoutParams();
            adjustedSeekParams.width = seekBarWidth;
            seekBar.setLayoutParams(adjustedSeekParams);
        }

        // Add to window
        try {
            windowManager.addView(floatingControls, floatingParams);
        } catch (Exception e) {
            Log.e(TAG, "addView floatingControls failed", e);
        }

        // SeekBar listener
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                float dimAmount = (100 - progress) / 100f;
                currentDim = dimAmount;  // always update
                if (!isPaused) {
                    updateDim(dimAmount); // only apply overlay if not paused
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
            // update icon locally immediately to the expected new state (do not change service state here)
            boolean expectedPaused = !isPaused;
            pauseButton.setImageResource(expectedPaused ? R.drawable.ic_play : R.drawable.ic_pause);
            pauseButton.setColorFilter(expectedPaused ? Color.GREEN : Color.parseColor("#FFC107"), PorterDuff.Mode.SRC_IN);
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
                        // Accessibility: dispatch a click so performClick is handled
                        v.performClick();
                        return true;
                }
                return false;
            }
        });
        // make it clickable for accessibility
        floatingControls.setClickable(true);
        Log.d(TAG, "Floating controls created and added");
    }

    private void updateFloatingPauseState() {
        if (floatingControls == null) return;
        // Use generated id to find the pause ImageButton reliably and run on main thread
        new android.os.Handler(getMainLooper()).post(() -> {
            try {
                ImageButton pb = floatingPauseButton != null ? floatingPauseButton : (ImageButton) floatingControls.findViewById(floatingPauseButtonId);
                if (pb != null) {
                    Log.d(TAG, "Updating pause button state: isPaused=" + isPaused);
                     pb.setImageResource(isPaused ? R.drawable.ic_play : R.drawable.ic_pause);
                     pb.setColorFilter(isPaused ? Color.GREEN : Color.parseColor("#FFC107"), PorterDuff.Mode.SRC_IN);
                     pb.invalidate();
                 }
             } catch (Exception ignored) {
             }
         });
     }

     private void updateFloatingSeekBar() {
         updateFloatingSeekBar(3);
     }

    // Retry-aware updater: attempts tries with small delays to handle OEM redraw/attach delays
    private void updateFloatingSeekBar(int attempts) {
        if (attempts <= 0) return;
        if (floatingControls == null) return;

        new android.os.Handler(getMainLooper()).post(() -> {
            try {
                SeekBar sb = floatingSeekBar != null ? floatingSeekBar : (SeekBar) floatingControls.findViewById(floatingSeekBarId);
                if (sb != null) {
                   int progress = (int) ((1 - currentDim) * 100);
                    Log.d(TAG, "Setting floating seekbar progress to " + progress + " (currentDim=" + currentDim + ") attempts="+  attempts);
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            sb.setProgress(progress, false);
                        }
                    } catch (NoSuchMethodError ignored) {
                        sb.setProgress(progress);
                    }
                    sb.postInvalidateOnAnimation();
                    sb.refreshDrawableState();
                    try {
                        if (windowManager != null && floatingControls.getParent() != null) {
                            floatingControls.requestLayout();
                            windowManager.updateViewLayout(floatingControls, floatingParams);
                        }
                    } catch (Exception ex) {
                        Log.d(TAG, "updateViewLayout failed while refreshing seekbar", ex);
                    }
                } else {
                    Log.d(TAG, "floating seekbar instance is null when updating (attempts=" + attempts + ")");
                    if (attempts > 1) {
                        new android.os.Handler(getMainLooper()).postDelayed(() -> updateFloatingSeekBar(attempts - 1), 120);
                    }
                }
            } catch (Exception ex) {
                Log.d(TAG, "Exception in updateFloatingSeekBar", ex);
                if (attempts > 1) new android.os.Handler(getMainLooper()).postDelayed(() -> updateFloatingSeekBar(attempts - 1), 120);
            }
        });
    }

     private void removeFloatingControls() {
         if (windowManager != null && floatingControls != null) {
             try {
                 windowManager.removeView(floatingControls);
             } catch (IllegalArgumentException ignored) {
             }
             floatingControls = null;
             floatingSeekBar = null;
             floatingPauseButton = null;
            Log.d(TAG, "Floating controls removed and references cleared");
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
