package com.redpacket.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.redpacket.config.RedPacketConfig;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 无障碍服务（完整版）
 * 
 * 改进点：
 * 1. Android 12+ 手势点击兼容
 * 2. 多种点击方式自动降级
 * 3. 支持自定义抢红包数量
 * 4. 防重复点击保护
 * 5. 服务保活机制
 * 6. 修复 performAction 在部分机型失效问题
 * 7. 支持微信多开/分身检测
 * 8. 完整的错误处理和日志
 */
public class RedPacketAccessibilityService extends AccessibilityService {

    private static final String TAG = "AccessibilitySvc";
    private static final String WECHAT_PKG = "com.tencent.mm";

    // 微信红包相关的关键节点标识（兼容多个版本）
    private static final String[] RED_PACKET_OPEN_BUTTONS = {
        "com.tencent.mm:id/den",           // 红包"开"按钮 (8.x)
        "com.tencent.mm:id/dap",           // 红包"开"按钮 (7.x)
        "com.tencent.mm:id/cd2",           // 红包"开"按钮 (旧版)
        "com.tencent.mm:id/ada",           // 红包"开"按钮 (其他版本)
    };

    // 红包领取成功的标志文本
    private static final String[] CLAIMED_TEXTS = {
        "红包已领取",
        "已存入零钱",
        "已存入零钱通",
        "已领取",
        "手气最佳",
        "运气王",
        "红包已退还",
    };

    private Handler mHandler;
    private final AtomicBoolean mIsProcessing = new AtomicBoolean(false);
    private long mLastGrabTime = 0;
    private int mGrabCount = 0;
    private int mConsecutiveFails = 0;
    private static final int MAX_CONSECUTIVE_FAILS = 5;

    // 广播接收器
    private BroadcastReceiver mRedPacketReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.redpacket.RED_PACKET_DETECTED".equals(intent.getAction())) {
                if (!RedPacketConfig.isAutoGrabEnabled()) return;
                if (!RedPacketConfig.canGrabMore()) return;

                // 直接抓取，无延迟
                tryGrabRedPacket();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mHandler = new Handler(Looper.getMainLooper());
        Log.i(TAG, "✅ 无障碍服务已创建");

        // 注册广播
        IntentFilter filter = new IntentFilter("com.redpacket.RED_PACKET_DETECTED");
        registerReceiver(mRedPacketReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(mRedPacketReceiver);
        } catch (Exception e) {
            // ignore
        }
        Log.i(TAG, "❌ 无障碍服务已销毁");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        if (!RedPacketConfig.isAutoGrabEnabled()) return;

        int eventType = event.getEventType();

        // 监听窗口变化和内容变化
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {

            // 检查是否有待领取的红包
            if (RedPacketNotificationListener.hasPendingRedPacket() && !mIsProcessing.get()) {
                tryGrabRedPacket();
            }
        }
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "⚠️ 无障碍服务被中断");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "✅ 无障碍服务已连接");
    }

    /**
     * 尝试抓取红包（核心逻辑）
     */
    private void tryGrabRedPacket() {
        if (!mIsProcessing.compareAndSet(false, true)) {
            return;  // 已在处理中
        }

        try {
            AccessibilityNodeInfo rootNode = getRootInActiveWindow();
            if (rootNode == null) {
                Log.d(TAG, "无法获取当前窗口根节点");
                mIsProcessing.set(false);
                return;
            }

            // 检查当前界面是否为微信
            CharSequence rootPkg = rootNode.getPackageName();
            if (rootPkg == null || !rootPkg.toString().equals(WECHAT_PKG)) {
                rootNode.recycle();
                mIsProcessing.set(false);
                return;
            }

            // 尝试多种方式查找并点击"开"按钮
            boolean clicked = false;

            // 方式1: 通过 resource-id 查找
            if (!clicked) {
                clicked = findAndClickById(rootNode);
            }

            // 方式2: 通过 text 查找
            if (!clicked) {
                clicked = findAndClickByText(rootNode);
            }

            // 方式3: 通过 content-description 查找
            if (!clicked) {
                clicked = findAndClickByDesc(rootNode);
            }

            // 方式4: 通过节点位置查找（红包领取按钮通常在屏幕中央偏下）
            if (!clicked) {
                clicked = findAndClickByPosition(rootNode);
            }

            if (clicked) {
                mGrabCount++;
                mLastGrabTime = System.currentTimeMillis();
                mConsecutiveFails = 0;

                // 更新配置统计
                RedPacketConfig.incrementGrabbedCount();

                // 如果是拼手气红包，单独计数
                if (RedPacketNotificationListener.sIsLuckyRedPacket) {
                    RedPacketConfig.incrementLuckyGrabbedCount();
                }

                Log.i(TAG, "✅ 红包已点击！累计抢到 " + mGrabCount + " 个");

                // 通知 UI 更新
                Intent uiIntent = new Intent("com.redpacket.GRAB_SUCCESS");
                uiIntent.putExtra("count", mGrabCount);
                uiIntent.putExtra("source", RedPacketNotificationListener.sLastRedPacketSource);
                uiIntent.putExtra("amount", RedPacketNotificationListener.sLastAmount);
                uiIntent.setPackage(getPackageName());
                sendBroadcast(uiIntent);

                // 延迟后检查是否领取成功
                mHandler.postDelayed(this::checkGrabResult, 1000);
            } else {
                mConsecutiveFails++;
                Log.d(TAG, "未找到红包按钮 (连续失败: " + mConsecutiveFails + ")");

                // 连续失败过多，可能界面不对
                if (mConsecutiveFails >= MAX_CONSECUTIVE_FAILS) {
                    Log.w(TAG, "连续失败过多，等待新通知");
                    mConsecutiveFails = 0;
                }
            }

            rootNode.recycle();
        } catch (Exception e) {
            Log.e(TAG, "抓取红包异常", e);
        } finally {
            mIsProcessing.set(false);
        }
    }

    /**
     * 通过 resource-id 查找"开"按钮
     */
    private boolean findAndClickById(AccessibilityNodeInfo root) {
        for (String resId : RED_PACKET_OPEN_BUTTONS) {
            try {
                List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(resId);
                if (nodes != null && !nodes.isEmpty()) {
                    for (AccessibilityNodeInfo node : nodes) {
                        if (isWeChatNode(node) && isClickable(node)) {
                            Log.d(TAG, "通过 resource-id 找到按钮: " + resId);
                            return clickNode(node);
                        }
                        node.recycle();
                    }
                }
            } catch (Exception e) {
                // 某些机型可能不支持此方法
                Log.d(TAG, "resource-id 查找失败: " + resId);
            }
        }
        return false;
    }

    /**
     * 通过 text 查找"开"按钮
     */
    private boolean findAndClickByText(AccessibilityNodeInfo root) {
        try {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText("开");
            if (nodes != null) {
                for (AccessibilityNodeInfo node : nodes) {
                    if (isWeChatNode(node) && isClickable(node)) {
                        // 额外验证：检查节点大小是否合理（红包按钮不会太小）
                        Rect bounds = new Rect();
                        node.getBoundsInScreen(bounds);
                        int width = bounds.width();
                        int height = bounds.height();
                        if (width > 30 && height > 30 && width < 500 && height < 500) {
                            Log.d(TAG, "通过 text 找到按钮");
                            return clickNode(node);
                        }
                    }
                    node.recycle();
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "text 查找失败");
        }
        return false;
    }

    /**
     * 通过 content-description 查找"开"按钮
     */
    private boolean findAndClickByDesc(AccessibilityNodeInfo root) {
        try {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText("开");
            if (nodes != null && !nodes.isEmpty()) {
                for (AccessibilityNodeInfo node : nodes) {
                    if (isWeChatNode(node) && isClickable(node)) {
                        Log.d(TAG, "通过 content-description 找到按钮");
                        return clickNode(node);
                    }
                    node.recycle();
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "content-description 查找失败");
        }
        return false;
    }

    /**
     * 通过位置查找红包按钮（最后手段）
     * 红包领取按钮通常在屏幕中央偏下位置
     */
    private boolean findAndClickByPosition(AccessibilityNodeInfo root) {
        try {
            // 遍历所有可点击节点，查找位于屏幕中央区域的按钮
            Rect screenBounds = new Rect();
            root.getBoundsInScreen(screenBounds);

            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int screenHeight = getResources().getDisplayMetrics().heightPixels;

            // 红包按钮通常在屏幕中央偏下（30%-70% 宽度，30%-70% 高度）
            float centerX = screenWidth / 2f;
            float centerY = screenHeight * 0.55f;  // 偏下一点

            return findAndClickInArea(root, centerX, centerY, 150);
        } catch (Exception e) {
            Log.d(TAG, "位置查找失败");
        }
        return false;
    }

    /**
     * 在指定区域查找可点击节点
     */
    private boolean findAndClickInArea(AccessibilityNodeInfo node, float targetX, float targetY, int radius) {
        if (node == null) return false;

        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);

        float nodeCenterX = bounds.centerX();
        float nodeCenterY = bounds.centerY();

        float distance = (float) Math.sqrt(
            Math.pow(nodeCenterX - targetX, 2) + Math.pow(nodeCenterY - targetY, 2));

        if (distance < radius && isClickable(node) && isWeChatNode(node)) {
            Log.d(TAG, "通过位置找到按钮，距离: " + distance);
            return clickNode(node);
        }

        // 递归查找子节点
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                if (findAndClickInArea(child, targetX, targetY, radius)) {
                    child.recycle();
                    return true;
                }
                child.recycle();
            }
        }

        return false;
    }

    /**
     * 检查节点是否为微信节点
     */
    private boolean isWeChatNode(AccessibilityNodeInfo node) {
        if (node == null) return false;
        CharSequence pkg = node.getPackageName();
        return pkg != null && pkg.toString().equals(WECHAT_PKG);
    }

    /**
     * 检查节点是否可点击
     */
    private boolean isClickable(AccessibilityNodeInfo node) {
        if (node == null) return false;
        return node.isClickable() || node.isLongClickable();
    }

    /**
     * 点击节点（多种方式）
     */
    private boolean clickNode(AccessibilityNodeInfo node) {
        if (node == null) return false;

        // 方式1: performAction
        if (node.isClickable()) {
            boolean result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            if (result) return true;
        }

        // 方式2: 点击父节点
        AccessibilityNodeInfo parent = node.getParent();
        if (parent != null) {
            if (parent.isClickable()) {
                boolean result = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                parent.recycle();
                if (result) return true;
            } else {
                parent.recycle();
            }
        }

        // 方式3: 手势模拟点击（Android 7.0+）
        return performGestureClick(node);
    }

    /**
     * 手势模拟点击（Android 7.0+）
     * 这是最可靠的点击方式，兼容性最好
     */
    private boolean performGestureClick(AccessibilityNodeInfo node) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false;  // Android 7.0 以下不支持手势
        }

        try {
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);

            int x = bounds.centerX();
            int y = bounds.centerY();

            Path clickPath = new Path();
            clickPath.moveTo(x, y);

            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(new GestureDescription.StrokeDescription(clickPath, 0, 50));

            final boolean[] success = {false};

            dispatchGesture(builder.build(), new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    success[0] = true;
                    Log.i(TAG, "手势点击完成");
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    Log.w(TAG, "手势点击被取消");
                }
            }, null);

            // 等待手势完成
            Thread.sleep(100);
            return success[0];
        } catch (Exception e) {
            Log.e(TAG, "手势点击异常", e);
            return false;
        }
    }

    /**
     * 检查红包领取结果
     */
    private void checkGrabResult() {
        try {
            AccessibilityNodeInfo rootNode = getRootInActiveWindow();
            if (rootNode == null) return;

            // 检查是否显示"已领取"等文本
            for (String text : CLAIMED_TEXTS) {
                List<AccessibilityNodeInfo> nodes = rootNode.findAccessibilityNodeInfosByText(text);
                if (nodes != null && !nodes.isEmpty()) {
                    Log.i(TAG, "红包领取成功: " + text);
                    for (AccessibilityNodeInfo node : nodes) {
                        node.recycle();
                    }
                    rootNode.recycle();
                    return;
                }
            }

            rootNode.recycle();
        } catch (Exception e) {
            Log.e(TAG, "检查领取结果异常", e);
        }
    }

    /**
     * 获取抢到的红包数量
     */
    public int getGrabCount() {
        return mGrabCount;
    }

    /**
     * 获取最后一次抢红包的时间
     */
    public long getLastGrabTime() {
        return mLastGrabTime;
    }
}
