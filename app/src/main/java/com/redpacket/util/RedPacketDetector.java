package com.redpacket.util;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RedPacketDetector {

    private static final String TAG = "RedPacketDetector";
    private static final String WECHAT_PKG = "com.tencent.mm";

    private static final String[] RED_PACKET_KEYWORDS = {
        "[微信红包]", "微信红包", "你收到一个红包", "收到红包", "[红包]", "拼手气红包", "普通红包"
    };

    private static final String[] LUCKY_KEYWORDS = {
        "拼手气红包", "运气王", "手气最佳"
    };

    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(\\d+\\.?\\d*)\\s*(?:元|块)");

    private static final String[] CLAIMED_KEYWORDS = {
        "已领取", "已存入零钱", "已存入零钱通", "已被领完", "已过期", "已退还"
    };

    public static boolean isRedPacketNotification(StatusBarNotification notification) {
        if (notification == null) return false;
        if (!WECHAT_PKG.equals(notification.getPackageName())) return false;

        Notification notif = notification.getNotification();
        if (notif == null) return false;

        Bundle extras = notif.extras;
        if (extras == null) return false;

        String title = getExtrasString(extras, Notification.EXTRA_TITLE);
        String text = getExtrasString(extras, Notification.EXTRA_TEXT);
        String bigText = getExtrasString(extras, Notification.EXTRA_BIG_TEXT);

        String allText = title + " " + text + " " + bigText;

        for (String claimed : CLAIMED_KEYWORDS) {
            if (allText.contains(claimed)) {
                return false;
            }
        }

        for (String keyword : RED_PACKET_KEYWORDS) {
            if (allText.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    public static boolean isLuckyRedPacket(StatusBarNotification notification) {
        if (notification == null) return false;
        Notification notif = notification.getNotification();
        if (notif == null) return false;

        Bundle extras = notif.extras;
        if (extras == null) return false;

        String title = getExtrasString(extras, Notification.EXTRA_TITLE);
        String text = getExtrasString(extras, Notification.EXTRA_TEXT);
        String bigText = getExtrasString(extras, Notification.EXTRA_BIG_TEXT);
        String allText = title + " " + text + " " + bigText;

        for (String keyword : LUCKY_KEYWORDS) {
            if (allText.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    public static float extractAmount(StatusBarNotification notification) {
        if (notification == null) return 0f;
        Notification notif = notification.getNotification();
        if (notif == null) return 0f;

        Bundle extras = notif.extras;
        if (extras == null) return 0f;

        String text = getExtrasString(extras, Notification.EXTRA_TEXT);
        String bigText = getExtrasString(extras, Notification.EXTRA_BIG_TEXT);
        String allText = text + " " + bigText;

        Matcher matcher = AMOUNT_PATTERN.matcher(allText);
        if (matcher.find()) {
            try {
                return Float.parseFloat(matcher.group(1));
            } catch (NumberFormatException e) {
                return 0f;
            }
        }
        return 0f;
    }

    public static String getRedPacketSource(StatusBarNotification notification) {
        if (notification == null) return "未知";
        Notification notif = notification.getNotification();
        if (notif == null) return "未知";

        Bundle extras = notif.extras;
        if (extras == null) return "未知";

        try {
            String title = getExtrasString(extras, Notification.EXTRA_TITLE);
            String text = getExtrasString(extras, Notification.EXTRA_TEXT);

            if (text.contains(":")) {
                String sender = text.split(":")[0].trim();
                if (!sender.contains("[微信红包]") && !sender.contains("微信红包")) {
                    return title.isEmpty() ? sender : title + " · " + sender;
                }
            }

            return title.isEmpty() ? "未知" : title;
        } catch (Exception e) {
            return "未知";
        }
    }

    public static PendingIntent getRedPacketIntent(StatusBarNotification notification) {
        if (notification == null) return null;
        Notification notif = notification.getNotification();
        if (notif == null) return null;

        if (notif.contentIntent != null) {
            return notif.contentIntent;
        }

        if (notif.actions != null) {
            for (Notification.Action action : notif.actions) {
                if (action.actionIntent != null) {
                    return action.actionIntent;
                }
            }
        }

        return null;
    }

    private static String getExtrasString(Bundle extras, String key) {
        try {
            CharSequence cs = extras.getCharSequence(key);
            return cs != null ? cs.toString() : "";
        } catch (Exception e) {
            return "";
        }
    }

    public static boolean hasNotificationPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }
}
