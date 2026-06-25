package com.redpacket.util;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.util.Log;

public class RedPacketDetector {

    private static final String TAG = "RedPacketDetector";
    private static final String WECHAT_PKG = "com.tencent.mm";
    private static final String[] KEYWORDS = {"[微信红包]", "微信红包", "红包", "拼手气红包"};

    public static boolean isRedPacketNotification(StatusBarNotification notification) {
        if (notification == null) return false;
        if (!WECHAT_PKG.equals(notification.getPackageName())) return false;

        Notification notif = notification.getNotification();
        if (notif == null) return false;

        Bundle extras = notif.extras;
        if (extras == null) return false;

        String title = "";
        String text = "";
        try {
            CharSequence titleCs = extras.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence textCs = extras.getCharSequence(Notification.EXTRA_TEXT);
            if (titleCs != null) title = titleCs.toString();
            if (textCs != null) text = textCs.toString();
        } catch (Exception e) {
            return false;
        }

        for (String keyword : KEYWORDS) {
            if (title.contains(keyword) || text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    public static String getRedPacketSource(StatusBarNotification notification) {
        try {
            Bundle extras = notification.getNotification().extras;
            String title = extras.getString(Notification.EXTRA_TITLE, "");
            return title.isEmpty() ? "未知" : title;
        } catch (Exception e) {
            return "未知";
        }
    }

    public static boolean isLuckyRedPacket(StatusBarNotification notification) {
        try {
            Bundle extras = notification.getNotification().extras;
            String text = extras.getString(Notification.EXTRA_TEXT, "");
            return text.contains("拼手气");
        } catch (Exception e) {
            return false;
        }
    }
}
