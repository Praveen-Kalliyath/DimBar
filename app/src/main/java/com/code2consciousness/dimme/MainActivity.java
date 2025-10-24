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

        // Notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 5678);
            }
        }

        // Disable activity animation
        overridePendingTransition(0, 0);

        // Transparent layout setup
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        // Window flags removed: floating layout will be managed via WindowManager

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

        // --- Setup floating overlay ---
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission();
        } else {
            showFloatingLayout();
        }

        // SeekBar listener
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (!isPaused) {
                    float dimAmount = (100 - progress) / 100f;
                    DimOverlayService.currentDim = dimAmount;
                    updateOverlay(dimAmount);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {}

            @Override
            public void onStopTrackingTouch(SeekBar sb) {}
        });

        // Pause button
        pauseButton.setOnClickListener(v -> {
            Intent pauseIntent = new Intent(this, DimOverlayService.class);
            pauseIntent.setAction("PAUSE");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                startForegroundService(pauseIntent);
            else
                startService(pauseIntent);
        });

        stopButton.setOnClickListener(v -> stopOverlay());

        // Close app receiver
        closeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                finish();
            }
        };
        LocalBroadcastManager.getInstance(this)
                .registerReceiver(closeReceiver, new IntentFilter("com.code2consciousness.dimme.ACTION_CLOSE_APP"));

        // Pause state updates
        pauseStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (DimOverlayService.ACTION_PAUSE_STATE_CHANGED.equals(intent.getAction())) {
                    isPaused = intent.getBooleanExtra(DimOverlayService.EXTRA_IS_PAUSED, false);
                    pauseButton.setImageResource(isPaused ? R.drawable.ic_play : R.drawable.ic_pause);
                    pauseButton.setColorFilter(isPaused ? Color.GREEN : Color.parseColor("#FFC107"), PorterDuff.Mode.SRC_IN);
                }
            }
        };
        LocalBroadcastManager.getInstance(this)
                .registerReceiver(pauseStateReceiver, new IntentFilter(DimOverlayService.ACTION_PAUSE_STATE_CHANGED));

        // Dim changes from notification buttons
        dimChangeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                float dim = intent.getFloatExtra("dim_amount", DimOverlayService.currentDim);
                DimOverlayService.currentDim = dim;
                seekBar.setProgress((int) ((1 - dim) * 100));
                updateOverlay(dim);
            }
        };
        LocalBroadcastManager.getInstance(this)
                .registerReceiver(dimChangeReceiver, new IntentFilter("com.code2consciousness.dimme.ACTION_DIM_CHANGED"));
    }

    private void requestOverlayPermission() {
        Toast.makeText(this, "Please grant permission to draw over other apps", Toast.LENGTH_LONG).show();
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) return true;
        }
        return false;
    }


    private void updateOverlay(float dimAmount) {
        Intent intent = new Intent(this, DimOverlayService.class);
        intent.putExtra("dim_amount", dimAmount);

        if (isServiceRunning(DimOverlayService.class)) {
            intent.setAction("UPDATE_DIM");
            startService(intent);
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                startForegroundService(intent);
            else
                startService(intent);
        }
    }

    private void stopOverlay() {
        Intent intent = new Intent(this, DimOverlayService.class);
        intent.setAction("CLOSE");
        startService(intent);

        // Remove the floating overlay immediately
        if (windowManager != null && outerLayout.getParent() != null) {
            windowManager.removeView(outerLayout);
        }

        finish();
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
        if (windowManager != null && outerLayout.getParent() != null) {
            outerLayout.setVisibility(View.GONE);
            Toast.makeText(this, "DimMe minimized. Tap the notification to reopen.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 5678) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showFloatingLayout();
            } else {
                Toast.makeText(this, "Notification permission is required for DimMe notifications.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        overridePendingTransition(0, 0);

        if (outerLayout != null && outerLayout.getVisibility() == View.GONE) {
            outerLayout.setVisibility(View.VISIBLE);
        }
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
