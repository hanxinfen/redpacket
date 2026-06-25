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
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        });

        btnAccessibility.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        });

        btnStart.setOnClickListener(v -> {
            tvStatus.setText("✅ 请手动开启通知监听和无障碍权限");
            Toast.makeText(this, "请在系统设置中开启权限", Toast.LENGTH_LONG).show();
        });
    }
}
