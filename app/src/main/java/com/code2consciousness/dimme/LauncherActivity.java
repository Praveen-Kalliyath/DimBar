package com.code2consciousness.dimme;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Tiny NoDisplay launcher which only starts the DimOverlayService (foreground) and immediately finishes.
 * If overlay permission is missing, forward to MainActivity (which can request permission) or to system settings.
 */
public class LauncherActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // If overlay permission not granted, open permission flow via Settings (or start MainActivity to request)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            // launch your MainActivity to show permission request flow if you have it there.
            // If you prefer to send users straight to system settings:
            Intent perm = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            perm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(perm);
            // finish quickly; user will return to launcher and can tap app again once permission is granted
            finish();
            return;
        }

        // Start the service (foreground) — service will manage overlays
        Intent svc = new Intent(this, DimOverlayService.class);
        // Starting the service without an action will start foreground and dim overlay
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc);
        } else {
            startService(svc);
        }

        // Finish immediately — No UI shown
        finish();
    }
}
