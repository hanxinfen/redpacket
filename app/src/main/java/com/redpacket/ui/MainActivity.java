package com.redpacket.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.redpacket.autograb.R;
import com.redpacket.service.RedPacketNotificationListener;

public class MainActivity extends AppCompatActivity {

    private TextView tvStatus;
    private Button btnNotification, btnAccessibility, btnStart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tv_status);
        btnNotification = findViewById(R.id.btn_notification);
        btnAccessibility = findViewById(R.id.btn_accessibility);
        btnStart = findViewById(R.id.btn_start);

        btnNotification.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            startActivity(intent);
        });

        btnAccessibility.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });

        btnStart.setOnClickListener(v -> checkAndStart());
    }

    private void checkAndStart() {
        boolean notificationEnabled = RedPacketNotificationListener.isEnabled(this);
        boolean accessibilityEnabled = isAccessibilityEnabled();

        if (notificationEnabled && accessibilityEnabled) {
            tvStatus.setText("✅ 服务已开启");
            Toast.makeText(this, "红包助手已启动", Toast.LENGTH_SHORT).show();
        } else {
            String msg = "请开启以下权限:\n";
            if (!notificationEnabled) msg += "• 通知监听权限\n";
            if (!accessibilityEnabled) msg += "• 无障碍权限\n";
            tvStatus.setText(msg);
            Toast.makeText(this, "请先开启所有权限", Toast.LENGTH_LONG).show();
        }
    }

    private boolean isAccessibilityEnabled() {
        String service = getPackageName() + "/" + 
            com.redpacket.service.RedPacketAccessibilityService.class.getName();
        try {
            String enabled = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            );
            return enabled != null && enabled.contains(service);
        } catch (Exception e) {
            return false;
        }
    }
}
