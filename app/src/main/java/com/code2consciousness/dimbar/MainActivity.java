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
    private BroadcastReceiver pauseStateReceiver;
    private BroadcastReceiver dimChangeReceiver;
    private boolean isPaused = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Disable activity open animation
        overridePendingTransition(0, 0);

        // Transparent layout setup
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        // Add these flags to make layout properly centered
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        getWindow().setDimAmount(0f);

        // Center the window
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.width = WindowManager.LayoutParams.WRAP_CONTENT;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.gravity = Gravity.CENTER;
        getWindow().setAttributes(params);

        GradientDrawable bgDrawable = new GradientDrawable();
        bgDrawable.setColor(Color.parseColor("#AA444444"));
        bgDrawable.setCornerRadius(96f);
        bgDrawable.setStroke(3, Color.parseColor("#FFC107"));

        LinearLayout innerLayout = new LinearLayout(this);
//        LinearLayout.LayoutParams innerParams = new LinearLayout.LayoutParams(
//                ViewGroup.LayoutParams.WRAP_CONTENT,
//                ViewGroup.LayoutParams.WRAP_CONTENT
//        );
//        innerLayout.setLayoutParams(innerParams);
        innerLayout.setOrientation(LinearLayout.HORIZONTAL);
        innerLayout.setGravity(Gravity.CENTER);
        innerLayout.setPadding(32, 16, 32, 16);
        innerLayout.setBackground(bgDrawable);

        seekBar = new SeekBar(this);
        seekBar.setMax(100);
        seekBar.setProgressDrawable(getResources().getDrawable(R.drawable.custom_seekbar));
        seekBar.getThumb().setColorFilter(Color.YELLOW, PorterDuff.Mode.SRC_IN);
        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(
                550, ViewGroup.LayoutParams.WRAP_CONTENT);
        seekParams.gravity = Gravity.CENTER_VERTICAL;
        seekBar.setLayoutParams(seekParams);

        // Initialize SeekBar from service dim value
        seekBar.setProgress((int) ((1 - DimOverlayService.currentDim) * 100));

        pauseButton = new ImageButton(this);
        pauseButton.setImageResource(R.drawable.ic_pause);
        pauseButton.setBackgroundColor(Color.TRANSPARENT);
        pauseButton.setColorFilter(Color.parseColor("#FFC107"), PorterDuff.Mode.SRC_IN);
        LinearLayout.LayoutParams pauseParams = new LinearLayout.LayoutParams(100, 180);
        pauseParams.gravity = Gravity.CENTER_VERTICAL;
        pauseParams.leftMargin = 16;
        pauseButton.setLayoutParams(pauseParams);

        ImageButton splitter = new ImageButton(this);
        splitter.setImageResource(android.R.drawable.divider_horizontal_bright);
        splitter.setColorFilter(Color.RED, PorterDuff.Mode.SRC_IN);
        splitter.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams splitterParams = new LinearLayout.LayoutParams(32, 80);
        splitterParams.gravity = Gravity.CENTER_VERTICAL;
        splitter.setLayoutParams(splitterParams);

        stopButton = new ImageButton(this);
        stopButton.setImageResource(android.R.drawable.ic_lock_power_off);
        stopButton.setBackgroundColor(Color.TRANSPARENT);
        stopButton.setColorFilter(Color.parseColor("#FFC107"), PorterDuff.Mode.SRC_IN);
        stopButton.setScaleX(1.5f);
        stopButton.setScaleY(1.5f);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(120, 120);
        btnParams.gravity = Gravity.CENTER_VERTICAL;
        btnParams.leftMargin = 32;
        stopButton.setLayoutParams(btnParams);

        innerLayout.addView(seekBar);
        innerLayout.addView(pauseButton);
        innerLayout.addView(splitter);
        innerLayout.addView(stopButton);

        LinearLayout outerLayout = new LinearLayout(this);
        LinearLayout.LayoutParams outerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        outerLayout.setLayoutParams(outerParams);
        outerLayout.setOrientation(LinearLayout.VERTICAL);
        outerLayout.setGravity(Gravity.CENTER);
        outerLayout.setBackgroundColor(Color.TRANSPARENT);
        outerLayout.addView(innerLayout);

        setContentView(outerLayout);

        // SeekBar listener
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (!isPaused) {
                    float dimAmount = (100 - progress) / 100f;
                    DimOverlayService.currentDim = dimAmount; // update service dim value
                    updateOverlay(dimAmount);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
            }
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

        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission();
        }

        // Close app receiver
        closeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                finish();
            }
        };
        LocalBroadcastManager.getInstance(this)
                .registerReceiver(closeReceiver, new IntentFilter("com.code2consciousness.dimbar.ACTION_CLOSE_APP"));

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
                .registerReceiver(dimChangeReceiver, new IntentFilter("com.code2consciousness.dimbar.ACTION_DIM_CHANGED"));
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent);
            else startService(intent);
        }
    }

    private void stopOverlay() {
        Intent intent = new Intent(this, DimOverlayService.class);
        intent.setAction("CLOSE");
        startService(intent);
        finish();
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) return true;
        }
        return false;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        overridePendingTransition(0, 0); // no animation
        // ✅ DO NOT reset SeekBar here — it already reflects current value
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
    }
}
