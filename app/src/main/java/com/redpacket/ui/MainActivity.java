package com.redpacket.ui;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.redpacket.autograb.R;
import com.redpacket.service.RedPacketAccessibilityService;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "redpacket_prefs";
    private static final String KEY_GRAB_COUNT = "grab_count";
    private static final String KEY_LAST_SOURCE = "last_source";
    private static final String KEY_LAST_TIME = "last_time";
    private static final String KEY_AUTO_GRAB = "auto_grab";
    private static final String KEY_MAX_COUNT = "max_count";
    private static final String KEY_DELAY_MS = "delay_ms";
    private static final String KEY_LUCKY_ONLY = "lucky_only";
    private static final String KEY_WHITELIST = "whitelist";
    private static final String KEY_LUCKY_GRAB_COUNT = "lucky_grab_count";

    private TextView tvNotifStatus, tvAccessStatus, tvGrabCount, tvLastGrab, tvLog;
    private Button btnNotifAccess, btnAccessSettings, btnSettings;
    private Switch switchAutoGrab;
    private ScrollView svLog;

    private Handler mHandler;
    private SimpleDateFormat mSdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private SharedPreferences prefs;

    private BroadcastReceiver mGrabReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.redpacket.GRAB_SUCCESS".equals(intent.getAction())) {
                int count = intent.getIntExtra("count", 0);
                String source = intent.getStringExtra("source");
                updateGrabInfo(count, source);
            }
        }
    };

    private BroadcastReceiver mDetectReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.redpacket.RED_PACKET_DETECTED".equals(intent.getAction())) {
                String source = intent.getStringExtra("source");
                boolean isLucky = intent.getBooleanExtra("isLucky", false);
                appendLog("🔴 检测到红包！" + source + (isLucky ? " [拼手气]" : ""));
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        mHandler = new Handler(Looper.getMainLooper());

        tvNotifStatus = findViewById(R.id.tv_notif_status);
        tvAccessStatus = findViewById(R.id.tv_access_status);
        tvGrabCount = findViewById(R.id.tv_grab_count);
        tvLastGrab = findViewById(R.id.tv_last_grab);
        tvLog = findViewById(R.id.tv_log);
        btnNotifAccess = findViewById(R.id.btn_notif);
        btnAccessSettings = findViewById(R.id.btn_access);
        btnSettings = findViewById(R.id.btn_setting);
        switchAutoGrab = findViewById(R.id.switch_auto_grab);
        svLog = findViewById(R.id.sv_log);

        btnNotifAccess.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            } catch (Exception e) {
                Toast.makeText(this, "无法打开通知访问设置", Toast.LENGTH_SHORT).show();
            }
        });

        btnAccessSettings.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (Exception e) {
                Toast.makeText(this, "无法打开无障碍设置", Toast.LENGTH_SHORT).show();
            }
        });

        btnSettings.setOnClickListener(v -> showSettingsDialog());

        switchAutoGrab.setChecked(prefs.getBoolean(KEY_AUTO_GRAB, true));
        switchAutoGrab.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(KEY_AUTO_GRAB, isChecked).apply();
            appendLog(isChecked ? "🟢 自动抢红包已开启" : "🔴 自动抢红包已关闭");
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 100);
            }
        }

        updateServiceStatus();
        updateStatsDisplay();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateServiceStatus();
        updateStatsDisplay();

        IntentFilter grabFilter = new IntentFilter("com.redpacket.GRAB_SUCCESS");
        registerReceiver(mGrabReceiver, grabFilter, Context.RECEIVER_NOT_EXPORTED);

        IntentFilter detectFilter = new IntentFilter("com.redpacket.RED_PACKET_DETECTED");
        registerReceiver(mDetectReceiver, detectFilter, Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(mGrabReceiver);
            unregisterReceiver(mDetectReceiver);
        } catch (Exception e) {}
    }

    private void showSettingsDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        TextView tvMaxLabel = new TextView(this);
        tvMaxLabel.setText("最大抢红包数量 (0=不限制)");
        layout.addView(tvMaxLabel);
        EditText etMaxCount = new EditText(this);
        etMaxCount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etMaxCount.setText(String.valueOf(prefs.getInt(KEY_MAX_COUNT, 0)));
        layout.addView(etMaxCount);

        TextView tvDelayLabel = new TextView(this);
        tvDelayLabel.setText("抢红包延迟 (毫秒)");
        layout.addView(tvDelayLabel);
        EditText etDelay = new EditText(this);
        etDelay.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etDelay.setText(String.valueOf(prefs.getInt(KEY_DELAY_MS, 300)));
        layout.addView(etDelay);

        CheckBox cbLuckyOnly = new CheckBox(this);
        cbLuckyOnly.setText("仅抢拼手气红包");
        cbLuckyOnly.setChecked(prefs.getBoolean(KEY_LUCKY_ONLY, false));
        layout.addView(cbLuckyOnly);

        TextView tvWhitelistLabel = new TextView(this);
        tvWhitelistLabel.setText("白名单 (群名，用逗号分隔)");
        layout.addView(tvWhitelistLabel);
        EditText etWhitelist = new EditText(this);
        etWhitelist.setText(prefs.getString(KEY_WHITELIST, ""));
        layout.addView(etWhitelist);

        TextView tvLuckyCountLabel = new TextView(this);
        tvLuckyCountLabel.setText("拼手气红包抢前 N 个 (0=不限制)");
        layout.addView(tvLuckyCountLabel);
        EditText etLuckyCount = new EditText(this);
        etLuckyCount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etLuckyCount.setText(String.valueOf(prefs.getInt(KEY_LUCKY_GRAB_COUNT, 0)));
        layout.addView(etLuckyCount);

        new AlertDialog.Builder(this)
            .setTitle("⚙️ 设置")
            .setView(layout)
            .setPositiveButton("保存", (dialog, which) -> {
                try {
                    int maxCount = Integer.parseInt(etMaxCount.getText().toString().trim());
                    prefs.edit().putInt(KEY_MAX_COUNT, maxCount).apply();
                } catch (NumberFormatException e) {}

                try {
                    int delay = Integer.parseInt(etDelay.getText().toString().trim());
                    prefs.edit().putInt(KEY_DELAY_MS, delay).apply();
                } catch (NumberFormatException e) {}

                prefs.edit()
                    .putBoolean(KEY_LUCKY_ONLY, cbLuckyOnly.isChecked())
                    .putString(KEY_WHITELIST, etWhitelist.getText().toString().trim())
                    .apply();

                try {
                    int luckyCount = Integer.parseInt(etLuckyCount.getText().toString().trim());
                    prefs.edit().putInt(KEY_LUCKY_GRAB_COUNT, luckyCount).apply();
                } catch (NumberFormatException e) {}

                appendLog("⚙️ 设置已保存");
                Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("取消", null)
            .setNeutralButton("重置统计", (dialog, which) -> {
                prefs.edit().putInt(KEY_GRAB_COUNT, 0).putString(KEY_LAST_SOURCE, "").apply();
                updateStatsDisplay();
                appendLog("📊 统计数据已重置");
            })
            .show();
    }

    private void updateServiceStatus() {
        boolean notifEnabled = isNotificationListenerEnabled();
        tvNotifStatus.setText(notifEnabled ? "✅ 已开启" : "❌ 未开启");
        tvNotifStatus.setTextColor(notifEnabled ? 0xFF4CAF50 : 0xFFF44336);

        boolean accessEnabled = isAccessibilityServiceEnabled();
        tvAccessStatus.setText(accessEnabled ? "✅ 已开启" : "❌ 未开启");
        tvAccessStatus.setTextColor(accessEnabled ? 0xFF4CAF50 : 0xFFF44336);
    }

    private void updateStatsDisplay() {
        int count = prefs.getInt(KEY_GRAB_COUNT, 0);
        tvGrabCount.setText(String.valueOf(count));

        String last = prefs.getString(KEY_LAST_SOURCE, "");
        long lastTime = prefs.getLong(KEY_LAST_TIME, 0);
        if (count > 0 && !TextUtils.isEmpty(last)) {
            String time = lastTime > 0 ? mSdf.format(new Date(lastTime)) : "";
            tvLastGrab.setText(time + " · " + last);
        } else {
            tvLastGrab.setText("暂无记录");
        }
    }

    private void updateGrabInfo(int count, String source) {
        prefs.edit()
            .putInt(KEY_GRAB_COUNT, count)
            .putString(KEY_LAST_SOURCE, source)
            .putLong(KEY_LAST_TIME, System.currentTimeMillis())
            .apply();
        updateStatsDisplay();
        appendLog("✅ 抢到红包！" + source + " (第" + count + "个)");
    }

    private boolean isNotificationListenerEnabled() {
        String pkgName = getPackageName();
        String flat = Settings.Secure.getString(getContentResolver(),
                "enabled_notification_listeners");
        if (!TextUtils.isEmpty(flat)) {
            String[] names = flat.split(":");
            for (String name : names) {
                ComponentName cn = ComponentName.unflattenFromString(name);
                if (cn != null && TextUtils.equals(pkgName, cn.getPackageName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isAccessibilityServiceEnabled() {
        String pkgName = getPackageName();
        String serviceName = pkgName + "/" + RedPacketAccessibilityService.class.getCanonicalName();
        String flat = Settings.Secure.getString(getContentResolver(),
                "enabled_accessibility_services");
        if (!TextUtils.isEmpty(flat)) {
            String[] names = flat.split(":");
            for (String name : names) {
                ComponentName cn = ComponentName.unflattenFromString(name);
                if (cn != null) {
                    String enabledName = cn.getPackageName() + "/" + cn.getClassName();
                    if (enabledName.equals(serviceName)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void appendLog(String msg) {
        String time = mSdf.format(new Date());
        String logEntry = "[" + time + "] " + msg + "\n";

        String current = tvLog.getText().toString();
        String[] lines = current.split("\n");
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, lines.length - 100);
        for (int i = start; i < lines.length; i++) {
            if (!lines[i].isEmpty()) {
                sb.append(lines[i]).append("\n");
            }
        }
        sb.append(logEntry);
        tvLog.setText(sb.toString());

        svLog.post(() -> svLog.fullScroll(View.FOCUS_DOWN));
    }
}
