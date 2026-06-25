package com.redpacket.service;

import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.NotificationManagerCompat;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import com.redpacket.config.RedPacketConfig;
import com.redpacket.util.RedPacketDetector;

import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

public class RedPacketNotificationListener extends NotificationListenerService {

    private static final String TAG = "NotifListener";
    private static final String WECHAT_PKG = "com.tencent.mm";

    private static final ConcurrentLinkedQueue<RedPacketInfo> sRedPacketQueue = new ConcurrentLinkedQueue<>();

    public static volatile boolean sHasRedPacket = false;
    public static volatile long sLastRedPacketTime = 0;
    public static volatile String sLastRedPacketSource = "";
    public static volatile boolean sIsLuckyRedPacket = false;
    public static volatile float sLastAmount = 0f;

    public interface OnRedPacketListener {
        void onRedPacketDetected(String source, boolean isLucky, float amount);
        void onServiceConnected();
        void onServiceDisconnected();
    }

    private static OnRedPacketListener sListener;

    public static void setOnRedPacketListener(OnRedPacketListener listener) {
        sListener = listener;
    }

    public static RedPacketInfo pollNextRedPacket() {
        return sRedPacketQueue.poll();
    }

    public static boolean hasPendingRedPacket() {
        return !sRedPacketQueue.isEmpty();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        if (!WECHAT_PKG.equals(sbn.getPackageName())) return;

        if (!RedPacketConfig.isAutoGrabEnabled()) {
            return;
        }

        if (!RedPacketConfig.canGrabMore()) {
            Log.d(TAG, "已达到总抢红包数量限制");
            return;
        }

        if (!RedPacketDetector.isRedPacketNotification(sbn)) {
            return;
        }

        String source = RedPacketDetector.getRedPacketSource(sbn);
        boolean isLucky = RedPacketDetector.isLuckyRedPacket(sbn);
        float amount = 0f;

        if (isLucky && !RedPacketConfig.canGrabMoreLucky()) {
            Log.d(TAG, "已达到拼手气红包数量限制");
            return;
        }

        if (RedPacketConfig.isLuckyOnly() && !isLucky) {
            Log.d(TAG, "只抢拼手气模式，跳过普通红包");
            return;
        }

        if (!RedPacketConfig.isGroupWhitelisted(source)) {
            Log.d(TAG, "群聊不在白名单中: " + source);
            return;
        }

        Log.i(TAG, "🔴 检测到红包！来源: " + source + ", 拼手气: " + isLucky);

        RedPacketInfo info = new RedPacketInfo();
        info.source = source;
        info.isLucky = isLucky;
        info.amount = amount;
        info.timestamp = System.currentTimeMillis();

        sRedPacketQueue.offer(info);

        sHasRedPacket = true;
        sLastRedPacketTime = System.currentTimeMillis();
        sLastRedPacketSource = source;
        sIsLuckyRedPacket = isLucky;
        sLastAmount = amount;

        if (sListener != null) {
            sListener.onRedPacketDetected(source, isLucky, amount);
        }

        Intent intent = new Intent("com.redpacket.RED_PACKET_DETECTED");
        intent.putExtra("source", source);
        intent.putExtra("isLucky", isLucky);
        intent.putExtra("amount", amount);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn == null) return;
        if (!WECHAT_PKG.equals(sbn.getPackageName())) return;

        if (RedPacketDetector.isRedPacketNotification(sbn)) {
            Log.d(TAG, "红包通知已移除");
        }
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.i(TAG, "✅ 通知监听服务已连接");

        if (sListener != null) {
            sListener.onServiceConnected();
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

    public static boolean isEnabled(Context context) {
        String packageName = context.getPackageName();
        Set<String> enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(context);
        return enabledListeners != null && enabledListeners.contains(packageName);
    }

    public static class RedPacketInfo {
        public String source;
        public boolean isLucky;
        public float amount;
        public long timestamp;
    }
}
