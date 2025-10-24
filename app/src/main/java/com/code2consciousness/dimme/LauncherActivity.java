package com.code2consciousness.dimme;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

public class LauncherActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1️⃣ Overlay permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent overlayIntent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(overlayIntent);
            finish(); // finish immediately
            return;
        }

        // 2️⃣ Notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            int notifPermission = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS);
            if (notifPermission != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Intent notifIntent = new Intent(this, NotificationPermissionActivity.class);
                startActivity(notifIntent);
                finish(); // finish immediately
                return;
            }
        }

        // 3️⃣ Start overlay service
        Intent svcIntent = new Intent(this, DimOverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svcIntent);
        } else {
            startService(svcIntent);
        }

        // 4️⃣ Close launcher activity
        finish();
    }
}
