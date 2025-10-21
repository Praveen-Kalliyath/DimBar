package com.code2consciousness.dimbar;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
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
    private boolean isPaused = false;
    private float lastDim = 0.5f;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Transparent activity
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        getWindow().setDimAmount(0f);

        // Rounded background for controls
        GradientDrawable bgDrawable = new GradientDrawable();
        bgDrawable.setColor(Color.parseColor("#AA222222"));
        bgDrawable.setCornerRadius(48f);

        LinearLayout innerLayout = new LinearLayout(this);
        innerLayout.setOrientation(LinearLayout.HORIZONTAL);
        innerLayout.setGravity(Gravity.CENTER);
        innerLayout.setPadding(64, 64, 64, 64);
        innerLayout.setBackground(bgDrawable);
        LinearLayout.LayoutParams innerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        innerParams.gravity = Gravity.CENTER;
        innerLayout.setLayoutParams(innerParams);

        // SeekBar
        seekBar = new SeekBar(this);
        seekBar.setMax(100);
        seekBar.setProgress(50);
        // keep your custom SeekBar drawable if present
        // seekBar.setProgressDrawable(getResources().getDrawable(R.drawable.custom_seekbar));
        seekBar.getThumb().setColorFilter(Color.YELLOW, PorterDuff.Mode.SRC_IN);
        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(
                600,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        seekParams.gravity = Gravity.CENTER_VERTICAL;
        seekBar.setLayoutParams(seekParams);

        // Pause button
        pauseButton = new ImageButton(this);
        pauseButton.setImageResource(android.R.drawable.ic_media_pause);
        pauseButton.setBackgroundColor(Color.TRANSPARENT);
        pauseButton.setColorFilter(Color.YELLOW, PorterDuff.Mode.SRC_IN);
        LinearLayout.LayoutParams pauseParams = new LinearLayout.LayoutParams(30, 160);
        pauseParams.gravity = Gravity.CENTER_VERTICAL;
        pauseParams.leftMargin = 16;
        pauseButton.setLayoutParams(pauseParams);

        // Stop button
        stopButton = new ImageButton(this);
        stopButton.setImageResource(android.R.drawable.ic_lock_power_off);
        stopButton.setBackgroundColor(Color.TRANSPARENT);
        stopButton.setColorFilter(Color.RED, PorterDuff.Mode.SRC_IN);
        stopButton.setScaleX(1.5f);
        stopButton.setScaleY(1.5f);
        stopButton.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                160, 160);
        btnParams.gravity = Gravity.CENTER_VERTICAL;
        btnParams.leftMargin = 32;
        stopButton.setLayoutParams(btnParams);

        // Add views to layout
        innerLayout.addView(seekBar);
        innerLayout.addView(pauseButton);
        innerLayout.addView(stopButton);

        LinearLayout outerLayout = new LinearLayout(this);
        outerLayout.setOrientation(LinearLayout.VERTICAL);
        outerLayout.setGravity(Gravity.CENTER);
        outerLayout.setBackgroundColor(Color.TRANSPARENT);
        outerLayout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        outerLayout.addView(innerLayout);

        setContentView(outerLayout);

        // SeekBar listener
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!isPaused) {
                    float dimAmount = (100 - progress) / 100f;
                    lastDim = dimAmount;
                    updateOverlay(dimAmount);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Pause button listener
        pauseButton.setOnClickListener(v -> {
            if (isPaused) {
                // Resume
                isPaused = false;
                pauseButton.setColorFilter(Color.YELLOW, PorterDuff.Mode.SRC_IN);
                updateOverlay(lastDim);
            } else {
                // Pause
                isPaused = true;
                pauseButton.setColorFilter(Color.GRAY, PorterDuff.Mode.SRC_IN);
                updateOverlay(0f);
            }
        });

        // Stop button listener
        stopButton.setOnClickListener(v -> stopOverlay());

        // Request overlay permission if needed
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission();
        } else {
            updateOverlay(seekBar.getProgress() / 100f);
        }

        // Register BroadcastReceiver to close app
        closeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                finish();
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(closeReceiver,
                new IntentFilter("com.code2consciousness.dimbar.ACTION_CLOSE_APP"));
    }

    private void requestOverlayPermission() {
        Toast.makeText(this, "Please grant permission to draw over other apps", Toast.LENGTH_LONG).show();
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
    }

    private void updateOverlay(float dimAmount) {
        Intent intent = new Intent(this, DimOverlayService.class);
        intent.putExtra("dim_amount", dimAmount);

        if (isServiceRunning(DimOverlayService.class)) {
            intent.setAction("UPDATE_DIM");
            startService(intent);
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        }
    }

    private void stopOverlay() {
        Intent intent = new Intent(this, DimOverlayService.class);
        intent.setAction("CLOSE");
        startService(intent);
        finish(); // close MainActivity
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (closeReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(closeReceiver);
        }
    }
}
