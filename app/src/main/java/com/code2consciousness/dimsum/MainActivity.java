package com.code2consciousness.dimsum;

import android.content.Intent;
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

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_OVERLAY_PERMISSION = 1234;
    private SeekBar seekBar;
    private ImageButton stopButton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Make activity window fully transparent
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND, WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        getWindow().setDimAmount(0f);

        // Create a rounded-corner, semi-transparent grey background for controls
        GradientDrawable bgDrawable = new GradientDrawable();
        bgDrawable.setColor(Color.parseColor("#AA222222")); // semi-transparent dark grey
        bgDrawable.setCornerRadius(48f);

        LinearLayout innerLayout = new LinearLayout(this);
        innerLayout.setOrientation(LinearLayout.HORIZONTAL); // horizontal for seekbar + button
        innerLayout.setGravity(Gravity.CENTER);
        innerLayout.setPadding(64, 64, 64, 64);
        innerLayout.setBackground(bgDrawable);
        LinearLayout.LayoutParams innerParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        innerParams.gravity = Gravity.CENTER;
        innerLayout.setLayoutParams(innerParams);

        seekBar = new SeekBar(this);
        seekBar.setMax(100);
        seekBar.setProgress(50);
        seekBar.setProgressDrawable(getResources().getDrawable(R.drawable.custom_seekbar));
        seekBar.getThumb().setColorFilter(Color.YELLOW, PorterDuff.Mode.SRC_IN);
        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(
            600, // wider seekbar in px
            ViewGroup.LayoutParams.WRAP_CONTENT); // use WRAP_CONTENT for height
        seekParams.gravity = Gravity.CENTER_VERTICAL;
        seekBar.setLayoutParams(seekParams);

        stopButton = new ImageButton(this);
        stopButton.setImageResource(android.R.drawable.ic_lock_power_off);
        stopButton.setBackgroundColor(Color.TRANSPARENT);
        stopButton.setColorFilter(Color.YELLOW, PorterDuff.Mode.SRC_IN);
        stopButton.setScaleX(1.5f);
        stopButton.setScaleY(1.5f);
        stopButton.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
            160, // width in px (increased for boldness)
            160); // height in px (increased for boldness)
        btnParams.gravity = Gravity.CENTER_VERTICAL;
        btnParams.leftMargin = 32;
        stopButton.setLayoutParams(btnParams);

        innerLayout.addView(seekBar);
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

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float dimAmount = (100 - progress) / 100f; // invert seekbar direction
                startOrUpdateOverlay(dimAmount);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        stopButton.setOnClickListener(v -> stopOverlay());

        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission();
        } else {
            startOrUpdateOverlay(seekBar.getProgress() / 100f);
        }
    }

    private void requestOverlayPermission() {
        Toast.makeText(this, "Please grant permission to draw over other apps", Toast.LENGTH_LONG).show();
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (Settings.canDrawOverlays(this)) {
                startOrUpdateOverlay(seekBar.getProgress() / 100f);
            } else {
                Toast.makeText(this, "Overlay permission not granted!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startOrUpdateOverlay(float dimAmount) {
        if (Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(this, DimOverlayService.class);
            intent.putExtra("dim_amount", dimAmount);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        }
    }

    private void stopOverlay() {
        Intent intent = new Intent(this, DimOverlayService.class);
        intent.setAction("STOP");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }
}
