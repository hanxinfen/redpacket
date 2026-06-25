package com.redpacket.ui;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.redpacket.R;
import com.redpacket.config.RedPacketConfig;
import com.redpacket.service.RedPacketAccessibilityService;
import com.redpacket.service.RedPacketNotificationListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 主界面（完整版）
 * 
 * 功能：
 * 1. 服务状态监控
 * 2. 抢红包统计
 * 3. 自定义设置（最大数量、延迟、白名单等）
 * 4. 实时日志
 * 5. Android 12+ 兼容性处理
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    // UI 组件
    private TextView tvNotifStatus;
    private TextView tvAccessStatus;
    private TextView tvGrabCount;
    private TextView tvLastGrab;
    private TextView tvLog;
    private Button btnNotifAccess;
    private Button btnAccessSettings;
    private Button btnSettings;
    private Switch switchAutoGrab;
    private ScrollView svLog;

    private Handler mHandler;
    private SimpleDateFormat mSdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    // 广播接收器
    private BroadcastReceiver mGrabReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.redpacket.GRAB_SUCCESS".equals(intent.getAction())) {
                int count = intent.getIntExtra("count", 0);
                String source = intent.getStringExtra("source");
                float amount = intent.getFloatExtra("amount", 0f);
                updateGrabInfo(count, source, amount);
            }
        }
    };

    private BroadcastReceiver mDetectReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.redpacket.RED_PACKET_DETECTED".equals(intent.getAction())) {
                String source = intent.getStringExtra("source");
                boolean isLucky = intent.getBooleanExtra("isLucky", false);
                float amount = intent.getFloatExtra("amount", 0f);
                String luckyTag = isLucky ? " [拼手气]" : "";
                String amountTag = amount > 0 ? " ¥" + amount : "";
                appendLog("🔴 检测到红包！" + source + luckyTag + amountTag);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化配置
        RedPacketConfig.init(this);

        mHandler = new Handler(Looper.getMainLooper());

        initViews();
        setupListeners();
        updateServiceStatus();
        updateStatsDisplay();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateServiceStatus();
        updateStatsDisplay();

        // 注册广播
        IntentFilter grabFilter = new IntentFilter("com.redpacket.GRAB_SUCCESS");
        registerReceiver(mGrabReceiver, grabFilter, Context.RECEIVER_NOT_EXPORTED);

        IntentFilter detectFilter = new IntentFilter("com.redpacket.RED_PACKET_DETECTED");
        registerReceiver(mDetectReceiver, detectFilter, Context.RECEIVER_NOT_EXPORTED);

        // 定时刷新
        mHandler.postDelayed(mStatusUpdater, 2000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(mGrabReceiver);
            unregisterReceiver(mDetectReceiver);
        } catch (Exception e) {
            // ignore
        }
        mHandler.removeCallbacks(mStatusUpdater);
    }

    private void initViews() {
        tvNotifStatus = findViewById(R.id.tv_notif_status);
        tvAccessStatus = findViewById(R.id.tv_access_status);
        tvGrabCount = findViewById(R.id.tv_grab_count);
        tvLastGrab = findViewById(R.id.tv_last_grab);
        tvLog = findViewById(R.id.tv_log);
        btnNotifAccess = findViewById(R.id.btn_notif_access);
        btnAccessSettings = findViewById(R.id.btn_access_settings);
        btnSettings = findViewById(R.id.btn_settings);
        switchAutoGrab = findViewById(R.id.switch_auto_grab);
        svLog = findViewById(R.id.sv_log);
    }

    private void setupListeners() {
        // 通知访问
        btnNotifAccess.setOnClickListener(v -> {
            try {
                startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
            } catch (Exception e) {
                Toast.makeText(this, "无法打开通知访问设置", Toast.LENGTH_SHORT).show();
            }
        });

        // 无障碍服务
        btnAccessSettings.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (Exception e) {
                Toast.makeText(this, "无法打开无障碍设置", Toast.LENGTH_SHORT).show();
            }
        });

        // 设置按钮
        btnSettings.setOnClickListener(v -> showSettingsDialog());

        // 自动抢开关
        switchAutoGrab.setChecked(RedPacketConfig.isAutoGrabEnabled());
        switchAutoGrab.setOnCheckedChangeListener((buttonView, isChecked) -> {
            RedPacketConfig.setAutoGrabEnabled(isChecked);
            appendLog(isChecked ? "🟢 自动抢红包已开启" : "🔴 自动抢红包已关闭");
        });

        // Android 13+ 通知权限请求
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) 
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 100);
            }
        }
    }

    /**
     * 显示设置对话框
     */
    private void showSettingsDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_settings, null);

        // 拼手气红包数量
        TextView tvLuckyCount = dialogView.findViewById(R.id.tv_lucky_count);
        SeekBar seekbarLucky = dialogView.findViewById(R.id.seekbar_lucky_count);
        Button btnMinus = dialogView.findViewById(R.id.btn_lucky_minus);
        Button btnPlus = dialogView.findViewById(R.id.btn_lucky_plus);

        int currentLuckyCount = RedPacketConfig.getLuckyGrabCount();
        tvLuckyCount.setText(String.valueOf(currentLuckyCount));
        seekbarLucky.setProgress(currentLuckyCount);

        // SeekBar 改变
        seekbarLucky.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvLuckyCount.setText(String.valueOf(progress));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // - 按钮
        btnMinus.setOnClickListener(v -> {
            int val = seekbarLucky.getProgress();
            if (val > 0) {
                seekbarLucky.setProgress(val - 1);
                tvLuckyCount.setText(String.valueOf(val - 1));
            }
        });

        // + 按钮
        btnPlus.setOnClickListener(v -> {
            int val = seekbarLucky.getProgress();
            if (val < 50) {
                seekbarLucky.setProgress(val + 1);
                tvLuckyCount.setText(String.valueOf(val + 1));
            }
        });

        // 其他设置
        EditText etMaxCount = dialogView.findViewById(R.id.et_max_count);
        EditText etDelay = dialogView.findViewById(R.id.et_delay);
        RadioButton rbNoLimit = dialogView.findViewById(R.id.rb_no_limit);
        RadioButton rbLuckyOnly = dialogView.findViewById(R.id.rb_lucky_only);
        RadioButton rbAll = dialogView.findViewById(R.id.rb_all_types);
        EditText etWhitelist = dialogView.findViewById(R.id.et_whitelist);
        RadioGroup rgLimit = dialogView.findViewById(R.id.rg_limit);

        // 填充当前值
        int maxCount = RedPacketConfig.getMaxGrabCount();
        if (maxCount <= 0) {
            rbNoLimit.setChecked(true);
            etMaxCount.setText("");
            etMaxCount.setEnabled(false);
        } else {
            rgLimit.check(R.id.rb_all);
            etMaxCount.setText(String.valueOf(maxCount));
        }

        etDelay.setText(String.valueOf(RedPacketConfig.getGrabDelayMs()));
        rbLuckyOnly.setChecked(RedPacketConfig.isLuckyOnly());
        etWhitelist.setText(RedPacketConfig.getWhitelistGroups());

        rgLimit.setOnCheckedChangeListener((group, checkedId) -> {
            etMaxCount.setEnabled(checkedId == R.id.rb_all);
            if (checkedId == R.id.rb_no_limit) etMaxCount.setText("");
        });

        new AlertDialog.Builder(this)
            .setTitle("⚙️ 设置")
            .setView(dialogView)
            .setPositiveButton("保存", (dialog, which) -> {
                // 保存拼手气数量
                RedPacketConfig.setLuckyGrabCount(seekbarLucky.getProgress());

                // 保存总数量
                if (rgLimit.getCheckedRadioButtonId() == R.id.rb_no_limit) {
                    RedPacketConfig.setMaxGrabCount(0);
                } else {
                    String countStr = etMaxCount.getText().toString().trim();
                    int count = countStr.isEmpty() ? 0 : Integer.parseInt(countStr);
                    RedPacketConfig.setMaxGrabCount(count);
                }

                String delayStr = etDelay.getText().toString().trim();
                int delay = delayStr.isEmpty() ? 0 : Integer.parseInt(delayStr);
                RedPacketConfig.setGrabDelayMs(delay);
                RedPacketConfig.setLuckyOnly(rbLuckyOnly.isChecked());
                RedPacketConfig.setWhitelistGroups(etWhitelist.getText().toString().trim());

                appendLog("⚙️ 设置已保存 | 拼手气: " + RedPacketConfig.getLuckyGrabCount() + " 个");
                Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("取消", null)
            .setNeutralButton("重置统计", (dialog, which) -> {
                RedPacketConfig.resetStats();
                updateStatsDisplay();
                appendLog("📊 统计数据已重置");
            })
            .show();
    }

    /**
     * 更新服务状态
     */
    private void updateServiceStatus() {
        boolean notifEnabled = isNotificationListenerEnabled();
        tvNotifStatus.setText(notifEnabled ? "✅ 已开启" : "❌ 未开启");
        tvNotifStatus.setTextColor(notifEnabled ? 0xFF4CAF50 : 0xFFF44336);
        btnNotifAccess.setText(notifEnabled ? "已授权" : "去授权");

        boolean accessEnabled = isAccessibilityServiceEnabled();
        tvAccessStatus.setText(accessEnabled ? "✅ 已开启" : "❌ 未开启");
        tvAccessStatus.setTextColor(accessEnabled ? 0xFF4CAF50 : 0xFFF44336);
        btnAccessSettings.setText(accessEnabled ? "已授权" : "去授权");

        if (notifEnabled && accessEnabled) {
            // 静默更新，不刷日志
        } else {
            StringBuilder sb = new StringBuilder("⚠️ 请先授权：");
            if (!notifEnabled) sb.append("通知访问 ");
            if (!accessEnabled) sb.append("无障碍服务");
            appendLog(sb.toString());
        }
    }

    /**
     * 更新统计显示
     */
    private void updateStatsDisplay() {
        int count = RedPacketConfig.getGrabbedCount();
        tvGrabCount.setText(String.valueOf(count));

        if (count > 0) {
            tvLastGrab.setText("累计 " + count + " 个");
        } else {
            tvLastGrab.setText("暂无记录");
        }
    }

    /**
     * 检查通知监听服务是否已授权
     */
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

    /**
     * 检查无障碍服务是否已授权
     */
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

    /**
     * 更新抢红包信息
     */
    private void updateGrabInfo(int count, String source, float amount) {
        tvGrabCount.setText(String.valueOf(count));
        String time = mSdf.format(new Date());
        String amountStr = amount > 0 ? " ¥" + amount : "";
        tvLastGrab.setText(time + " · " + source + amountStr);
        appendLog("✅ 抢到红包！" + source + amountStr + " (第" + count + "个)");
        updateStatsDisplay();
    }

    /**
     * 追加日志
     */
    private void appendLog(String msg) {
        String time = mSdf.format(new Date());
        String logEntry = "[" + time + "] " + msg + "\n";

        String current = tvLog.getText().toString();
        String[] lines = current.split("\n");
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, lines.length - 100);  // 保留最近100条
        for (int i = start; i < lines.length; i++) {
            if (!lines[i].isEmpty()) {
                sb.append(lines[i]).append("\n");
            }
        }
        sb.append(logEntry);

        tvLog.setText(sb.toString());

        // 滚动到底部
        svLog.post(() -> svLog.fullScroll(View.FOCUS_DOWN));
    }

    /**
     * 定时刷新状态
     */
    private Runnable mStatusUpdater = new Runnable() {
        @Override
        public void run() {
            updateServiceStatus();
            mHandler.postDelayed(this, 3000);
        }
    };
}
