package com.redpacket.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import com.redpacket.config.RedPacketConfig;
import com.redpacket.util.RedPacketDetector;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 通知监听服务（完整版）
 * 
 * 改进点：
 * 1. 使用线程安全的队列管理红包通知
 * 2. 支持 Android 13+ 通知权限检查
 * 3. 修复多红包同时到达的并发问题
 * 4. 支持白名单群聊过滤
 * 5. 支持只抢拼手气红包设置
 * 6. 通知系统状态变化
 */
public class RedPacketNotificationListener extends NotificationListenerService {

    private static final String TAG = "NotifListener";
    private static final String WECHAT_PKG = "com.tencent.mm";

    // 红包通知队列（线程安全）
    private static final ConcurrentLinkedQueue<RedPacketInfo> sRedPacketQueue = 
        new ConcurrentLinkedQueue<>();

    // 状态标志
    public static volatile boolean sHasRedPacket = false;
    public static volatile long sLastRedPacketTime = 0;
    public static volatile String sLastRedPacketSource = "";
    public static volatile boolean sIsLuckyRedPacket = false;
    public static volatile float sLastAmount = 0f;

    // 回调
    public interface OnRedPacketListener {
        void onRedPacketDetected(String source, boolean isLucky, float amount);
        void onServiceConnected();
        void onServiceDisconnected();
    }

    private static OnRedPacketListener sListener;

    public static void setOnRedPacketListener(OnRedPacketListener listener) {
        sListener = listener;
    }

    /**
     * 获取下一个待处理的红包信息
     */
    public static RedPacketInfo pollNextRedPacket() {
        return sRedPacketQueue.poll();
    }

    /**
     * 检查队列中是否有待处理的红包
     */
    public static boolean hasPendingRedPacket() {
        return !sRedPacketQueue.isEmpty();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        if (!WECHAT_PKG.equals(sbn.getPackageName())) return;

        // 检查自动抢开关
        if (!RedPacketConfig.isAutoGrabEnabled()) return;

        // 检查是否还能继续抢（总数量 + 拼手气数量都检查）
        if (!RedPacketConfig.canGrabMore()) {
            Log.d(TAG, "已达到抢红包数量限制");
            return;
        }

        // 如果是拼手气红包，额外检查拼手气数量限制
        if (RedPacketDetector.isLuckyRedPacket(sbn) && !RedPacketConfig.canGrabMoreLucky()) {
            Log.d(TAG, "已达到拼手气红包数量限制");
            return;
        }

        // 检测是否为红包通知
        if (RedPacketDetector.isRedPacketNotification(sbn)) {
            String source = RedPacketDetector.getRedPacketSource(sbn);
            boolean isLucky = RedPacketDetector.isLuckyRedPacket(sbn);
            // float amount = RedPacketDetector.extractAmount(sbn);

            // 白名单过滤
            if (!RedPacketConfig.isGroupWhitelisted(source)) {
                Log.d(TAG, "群聊不在白名单中，跳过: " + source);
                return;
            }

            // 只抢拼手气红包过滤
            if (RedPacketConfig.isLuckyOnly() && !isLucky) {
                Log.d(TAG, "只抢拼手气模式，非拼手气红包跳过");
                return;
            }

            Log.i(TAG, "🔴 检测到红包！来源: " + source + ", 拼手气: " + isLucky + ", 金额: " + amount);

            // 创建红包信息
            RedPacketInfo info = new RedPacketInfo();
            info.source = source;
            info.isLucky = isLucky;
            info.amount = amount;
            info.timestamp = System.currentTimeMillis();
            info.notification = sbn;

            // 加入队列
            sRedPacketQueue.offer(info);

            // 更新状态
            sHasRedPacket = true;
            sLastRedPacketTime = System.currentTimeMillis();
            sLastRedPacketSource = source;
            sIsLuckyRedPacket = isLucky;
            sLastAmount = amount;

            // 通知回调
            if (sListener != null) {
                sListener.onRedPacketDetected(source, isLucky, amount);
            }

            // 发送广播
            Intent intent = new Intent("com.redpacket.RED_PACKET_DETECTED");
            intent.putExtra("source", source);
            intent.putExtra("isLucky", isLucky);
            intent.putExtra("amount", amount);
            intent.setPackage(getPackageName());
            sendBroadcast(intent);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn == null) return;
        if (!WECHAT_PKG.equals(sbn.getPackageName())) return;

        if (RedPacketDetector.isRedPacketNotification(sbn)) {
            Log.d(TAG, "红包通知已移除");
            // 注意：不清除 sHasRedPacket，因为可能还有待处理的红包
        }
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.i(TAG, "✅ 通知监听服务已连接");

        // 通知回调
        if (sListener != null) {
            sListener.onServiceConnected();
        }

        // Android 12+ 需要主动刷新现有通知
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                StatusBarNotification[] activeNotifications = getActiveNotifications();
                if (activeNotifications != null) {
                    for (StatusBarNotification sbn : activeNotifications) {
                        onNotificationPosted(sbn);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "刷新通知失败", e);
            }
        }
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        Log.w(TAG, "⚠️ 通知监听服务已断开");

        if (sListener != null) {
            sListener.onServiceDisconnected();
        }
    }

    /**
     * 红包信息数据类
     */
    public static class RedPacketInfo {
        public String source;
        public boolean isLucky;
        public float amount;
        public long timestamp;
        public StatusBarNotification notification;
    }
}

    public static boolean isEnabled(android.content.Context context) {
        String packageName = context.getPackageName();
        java.util.Set<String> enabledListeners = 
            android.service.notification.NotificationManagerCompat
                .getEnabledListenerPackages(context);
        return enabledListeners != null && enabledListeners.contains(packageName);
    }
