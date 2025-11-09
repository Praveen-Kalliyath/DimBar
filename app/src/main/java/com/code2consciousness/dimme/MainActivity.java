package com.code2consciousness.dimme;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_OVERLAY_PERMISSION = 1234;
    private static final int REQUEST_NOTIFICATION_PERMISSION = 5678;
    private SeekBar seekBar;
    private ImageButton stopButton;
    private ImageButton pauseButton;
    private BroadcastReceiver closeReceiver;
    private BroadcastReceiver pauseStateReceiver;
    private BroadcastReceiver dimChangeReceiver;
    private boolean isPaused = false;

    private LinearLayout outerLayout;
    private WindowManager windowManager;
    private WindowManager.LayoutParams overlayParams;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Disable activity animation
        overridePendingTransition(0, 0);

        // Transparent layout setup
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        // Initialize UI components
        initializeUI();

        // Check and request permissions
        checkAndRequestPermissions();
    }

    private void initializeUI() {
        GradientDrawable bgDrawable = new GradientDrawable();
        bgDrawable.setColor(Color.parseColor("#AA444444"));
        bgDrawable.setCornerRadius(96f);
        bgDrawable.setStroke(3, Color.parseColor("#FFC107"));

        LinearLayout innerLayout = new LinearLayout(this);
        innerLayout.setOrientation(LinearLayout.HORIZONTAL);
        innerLayout.setGravity(Gravity.CENTER);
        innerLayout.setPadding(24, 12, 24, 12);
        innerLayout.setBackground(bgDrawable);

        seekBar = new SeekBar(this);
        seekBar.setMax(100);
        seekBar.setProgressDrawable(getResources().getDrawable(R.drawable.custom_seekbar));
        seekBar.getThumb().setColorFilter(Color.YELLOW, PorterDuff.Mode.SRC_IN);
        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(
                500, ViewGroup.LayoutParams.WRAP_CONTENT);
        seekParams.gravity = Gravity.CENTER_VERTICAL;
        seekBar.setLayoutParams(seekParams);
        seekBar.setProgress((int) ((1 - DimOverlayService.currentDim) * 100));

        pauseButton = new ImageButton(this);
        pauseButton.setImageResource(R.drawable.ic_pause);
        pauseButton.setBackgroundColor(Color.TRANSPARENT);
        pauseButton.setColorFilter(Color.parseColor("#FFC107"), PorterDuff.Mode.SRC_IN);
        LinearLayout.LayoutParams pauseParams = new LinearLayout.LayoutParams(90, 90);
        pauseParams.gravity = Gravity.CENTER_VERTICAL;
        pauseParams.leftMargin = 16;
        pauseButton.setLayoutParams(pauseParams);

        ImageButton splitter = new ImageButton(this);
        splitter.setImageResource(android.R.drawable.divider_horizontal_bright);
        splitter.setColorFilter(Color.RED, PorterDuff.Mode.SRC_IN);
        splitter.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams splitterParams = new LinearLayout.LayoutParams(24, 60);
        splitterParams.gravity = Gravity.CENTER_VERTICAL;
        splitter.setLayoutParams(splitterParams);

        ImageButton minimizeButton = new ImageButton(this);
        minimizeButton.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        minimizeButton.setBackgroundColor(Color.TRANSPARENT);
        minimizeButton.setColorFilter(Color.LTGRAY, PorterDuff.Mode.SRC_IN);
        minimizeButton.setOnClickListener(v -> minimizeOverlay());
        innerLayout.addView(minimizeButton);

        stopButton = new ImageButton(this);
        stopButton.setImageResource(android.R.drawable.ic_lock_power_off);
        stopButton.setBackgroundColor(Color.TRANSPARENT);
        stopButton.setColorFilter(Color.parseColor("#FFC107"), PorterDuff.Mode.SRC_IN);
        stopButton.setScaleX(1.5f);
        stopButton.setScaleY(1.5f);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(100, 60);
        btnParams.gravity = Gravity.CENTER_VERTICAL;
        btnParams.leftMargin = 32;
        stopButton.setLayoutParams(btnParams);

        innerLayout.addView(seekBar);
        innerLayout.addView(pauseButton);
        innerLayout.addView(splitter);
        innerLayout.addView(stopButton);

        outerLayout = new LinearLayout(this);
        LinearLayout.LayoutParams outerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        outerLayout.setLayoutParams(outerParams);
        outerLayout.setOrientation(LinearLayout.VERTICAL);
        outerLayout.setGravity(Gravity.CENTER);
        outerLayout.setBackgroundColor(Color.TRANSPARENT);
        outerLayout.addView(innerLayout);
    }

    private void checkAndRequestPermissions() {
        boolean needsOverlay = !Settings.canDrawOverlays(this);
        boolean needsNotification = false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needsNotification = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED;
        }

        if (needsOverlay) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
        } else if (needsNotification && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATION_PERMISSION);
        } else {
            showFloatingLayout();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (Settings.canDrawOverlays(this)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                                REQUEST_NOTIFICATION_PERMISSION);
                        return;
                    }
                }
                showFloatingLayout();
            } else {
                Toast.makeText(this, "Overlay permission is required", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (Settings.canDrawOverlays(this)) {
                    showFloatingLayout();
                }
            } else {
                Toast.makeText(this, "Notification permission is required for service reliability",
                        Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void showFloatingLayout() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlayParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                        WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, // allows clicks outside to pass through
                android.graphics.PixelFormat.TRANSLUCENT
        );
        overlayParams.gravity = Gravity.CENTER;

        windowManager.addView(outerLayout, overlayParams);

        // Make movable and keep internal UI interactive
        outerLayout.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // Make floating interactive on touch
                        overlayParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                        windowManager.updateViewLayout(outerLayout, overlayParams);

                        initialX = overlayParams.x;
                        initialY = overlayParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        overlayParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                        overlayParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(outerLayout, overlayParams);
                        return true;

                    case MotionEvent.ACTION_UP:
                        // Optional: revert to click-through outside after moving
                        overlayParams.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                        windowManager.updateViewLayout(outerLayout, overlayParams);
                        return true;
                }
                return false;
            }
        });

        // Start the overlay service immediately
        Intent serviceIntent = new Intent(this, DimOverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            startForegroundService(serviceIntent);
        else
            startService(serviceIntent);
    }

    private void minimizeOverlay() {
        if (outerLayout != null) {
            outerLayout.setVisibility(View.GONE);
            Toast.makeText(this, "DimMe minimized. Tap the notification or app icon to reopen.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        // Bring overlay back or recreate if necessary
        if (outerLayout != null) {
            outerLayout.setVisibility(View.VISIBLE);
            if (outerLayout.getParent() == null && windowManager != null) {
                windowManager.addView(outerLayout, overlayParams);
            }
        } else {
            showFloatingLayout();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Intent intent = new Intent(this, DimOverlayService.class);
        intent.setAction("APP_VISIBLE");
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (closeReceiver != null)
            LocalBroadcastManager.getInstance(this).unregisterReceiver(closeReceiver);
        if (pauseStateReceiver != null)
            LocalBroadcastManager.getInstance(this).unregisterReceiver(pauseStateReceiver);
        if (dimChangeReceiver != null)
            LocalBroadcastManager.getInstance(this).unregisterReceiver(dimChangeReceiver);

        if (windowManager != null && outerLayout.getParent() != null)
            windowManager.removeView(outerLayout);
    }
}
