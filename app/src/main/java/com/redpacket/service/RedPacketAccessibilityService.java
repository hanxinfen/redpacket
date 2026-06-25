package com.redpacket.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class RedPacketAccessibilityService extends AccessibilityService {

    private static final String TAG = "AccessibilitySvc";
    private static final String WECHAT_PKG = "com.tencent.mm";
    private static final String PREFS_NAME = "redpacket_prefs";

    private static final String[] RED_PACKET_OPEN_BUTTONS = {
        "com.tencent.mm:id/den", "com.tencent.mm:id/dap",
        "com.tencent.mm:id/cd2", "com.tencent.mm:id/ada"
    };

    private static final String[] CLAIMED_TEXTS = {
        "红包已领取", "已存入零钱", "已领取", "已存入零钱通"
    };

    private Handler mHandler;
    private final AtomicBoolean mIsProcessing = new AtomicBoolean(false);
    private long mLastGrabTime = 0;
    private int mGrabCount = 0;
    private SharedPreferences prefs;

    public static volatile boolean sHasRedPacket = false;
    public static volatile String sLastSource = "";
    public static volatile boolean sIsLucky = false;

    private BroadcastReceiver mRedPacketReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.redpacket.RED_PACKET_DETECTED".equals(intent.getAction())) {
                sHasRedPacket = true;
                sLastSource = intent.getStringExtra("source");
                sIsLucky = intent.getBooleanExtra("isLucky", false);
                tryGrabRedPacket();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mHandler = new Handler(Looper.getMainLooper());
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        mGrabCount = prefs.getInt("grab_count", 0);
        Log.i(TAG, "✅ 无障碍服务已创建");

        IntentFilter filter = new IntentFilter("com.redpacket.RED_PACKET_DETECTED");
        registerReceiver(mRedPacketReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(mRedPacketReceiver);
        } catch (Exception e) {}
        Log.i(TAG, "❌ 无障碍服务已销毁");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        if (sHasRedPacket && !mIsProcessing.get()) {
            tryGrabRedPacket();
        }
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "⚠️ 无障碍服务被中断");
    }

    private void tryGrabRedPacket() {
        if (!mIsProcessing.compareAndSet(false, true)) return;

        try {
            AccessibilityNodeInfo rootNode = getRootInActiveWindow();
            if (rootNode == null) {
                mIsProcessing.set(false);
                return;
            }

            CharSequence rootPkg = rootNode.getPackageName();
            if (rootPkg == null || !rootPkg.toString().equals(WECHAT_PKG)) {
                rootNode.recycle();
                mIsProcessing.set(false);
                return;
            }

            boolean clicked = false;

            for (String resId : RED_PACKET_OPEN_BUTTONS) {
                try {
                    List<AccessibilityNodeInfo> nodes = rootNode.findAccessibilityNodeInfosByViewId(resId);
                    if (nodes != null && !nodes.isEmpty()) {
                        for (AccessibilityNodeInfo node : nodes) {
                            if (node != null && node.isClickable() && node.isEnabled()) {
                                clicked = clickNode(node);
                                if (clicked) break;
                            }
                        }
                    }
                } catch (Exception e) {}
                if (clicked) break;
            }

            if (!clicked) {
                try {
                    List<AccessibilityNodeInfo> nodes = rootNode.findAccessibilityNodeInfosByText("开");
                    if (nodes != null) {
                        for (AccessibilityNodeInfo node : nodes) {
                            if (node != null && node.isClickable() && node.isEnabled()) {
                                clicked = clickNode(node);
                                if (clicked) break;
                            }
                        }
                    }
                } catch (Exception e) {}
            }

            if (!clicked) {
                clicked = findAndClickByPosition(rootNode);
            }

            if (clicked) {
                mGrabCount++;
                mLastGrabTime = System.currentTimeMillis();
                sHasRedPacket = false;
                prefs.edit().putInt("grab_count", mGrabCount).apply();

                Log.i(TAG, "✅ 红包已点击！累计: " + mGrabCount);

                Intent uiIntent = new Intent("com.redpacket.GRAB_SUCCESS");
                uiIntent.putExtra("count", mGrabCount);
                uiIntent.putExtra("source", sLastSource);
                uiIntent.setPackage(getPackageName());
                sendBroadcast(uiIntent);
            } else {
                Log.d(TAG, "未找到红包按钮");
            }

            rootNode.recycle();
        } catch (Exception e) {
            Log.e(TAG, "抓取红包异常", e);
        } finally {
            mIsProcessing.set(false);
        }
    }

    private boolean findAndClickByPosition(AccessibilityNodeInfo root) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false;

        try {
            Rect rootBounds = new Rect();
            root.getBoundsInScreen(rootBounds);
            int centerX = rootBounds.width() / 2;
            int centerY = (int)(rootBounds.height() * 0.55);
            return findClickInRect(root, centerX, centerY, 200);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean findClickInRect(AccessibilityNodeInfo node, int targetX, int targetY, int radius) {
        if (node == null) return false;

        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);

        float cx = bounds.centerX();
        float cy = bounds.centerY();
        float dist = (float) Math.sqrt(Math.pow(cx - targetX, 2) + Math.pow(cy - targetY, 2));

        if (dist < radius && node.isClickable() && node.isEnabled()) {
            return clickNode(node);
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                if (findClickInRect(child, targetX, targetY, radius)) {
                    child.recycle();
                    return true;
                }
                child.recycle();
            }
        }
        return false;
    }

    private boolean clickNode(AccessibilityNodeInfo node) {
        if (node == null) return false;

        if (node.isClickable()) {
            if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
        }

        AccessibilityNodeInfo parent = node.getParent();
        if (parent != null && parent.isClickable()) {
            boolean result = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            parent.recycle();
            if (result) return true;
        }
        if (parent != null) parent.recycle();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                Rect bounds = new Rect();
                node.getBoundsInScreen(bounds);
                int x = bounds.centerX();
                int y = bounds.centerY();

                if (x > 0 && y > 0) {
                    Path path = new Path();
                    path.moveTo(x, y);
                    GestureDescription.Builder builder = new GestureDescription.Builder();
                    builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 50));
                    dispatchGesture(builder.build(), null, null);
                    return true;
                }
            } catch (Exception e) {}
        }

        return false;
    }

    public int getGrabCount() {
        return mGrabCount;
    }

    public long getLastGrabTime() {
        return mLastGrabTime;
    }
}
