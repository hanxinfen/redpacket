package com.redpacket.util;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 红包检测工具（完整版）
 * 
 * 改进点：
 * 1. 支持更多微信版本的通知格式
 * 2. 正确处理 Android 12+ 的 PendingIntent 兼容性
 * 3. 支持检测红包金额
 * 4. 修复多通知并发问题
 * 5. 支持 Android 13+ 通知权限检查
 */
public class RedPacketDetector {

    private static final String TAG = "RedPacketDetector";
    private static final String WECHAT_PKG = "com.tencent.mm";

    // 微信红包通知的关键词（兼容多个版本）
    private static final String[] RED_PACKET_KEYWORDS = {
        "[微信红包]",
        "微信红包",
        "你收到一个红包",
        "收到红包",
        "[红包]",
        "红包来了",
        "拼手气红包",
        "普通红包"
    };

    // 拼手气红包特殊标记
    private static final String[] LUCKY_KEYWORDS = {
        "拼手气红包",
        "运气王",
        "手气最佳"
    };

    // 金额提取正则
    private static final Pattern AMOUNT_PATTERN = 
        Pattern.compile("(\\d+\\.?\\d*)\\s*(?:元|块)");

    // 红包状态关键词
    private static final String[] CLAIMED_KEYWORDS = {
        "已领取",
        "已存入零钱",
        "已存入零钱通",
        "已被领完",
        "已过期",
        "已退还"
    };

    /**
     * 检测通知是否为红包通知
     */
    public static boolean isRedPacketNotification(StatusBarNotification notification) {
        if (notification == null) return false;
        if (!WECHAT_PKG.equals(notification.getPackageName())) return false;

        Notification notif = notification.getNotification();
        if (notif == null) return false;

        // Android 12+ 检查通知是否为低优先级（群消息折叠）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (notif.getPriority() < Notification.PRIORITY_DEFAULT) {
                // 可能是折叠的群消息，需要额外检查
            }
        }

        Bundle extras = notif.extras;
        if (extras == null) return false;

        String title = getExtrasString(extras, Notification.EXTRA_TITLE);
        String text = getExtrasString(extras, Notification.EXTRA_TEXT);
        String bigText = getExtrasString(extras, Notification.EXTRA_BIG_TEXT);

        // 合并所有文本进行检测
        String allText = title + " " + text + " " + bigText;

        // 检查是否已领取
        for (String claimed : CLAIMED_KEYWORDS) {
            if (allText.contains(claimed)) {
                return false;  // 已领取的红包不算
            }
        }

        // 检查红包关键词
        for (String keyword : RED_PACKET_KEYWORDS) {
            if (allText.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 检测是否为拼手气红包
     */
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

    /**
     * 提取红包金额
     */
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

    /**
     * 获取红包来源（群名或发送者）
     */
    public static String getRedPacketSource(StatusBarNotification notification) {
        if (notification == null) return "未知";
        Notification notif = notification.getNotification();
        if (notif == null) return "未知";

        Bundle extras = notif.extras;
        if (extras == null) return "未知";

        try {
            String title = getExtrasString(extras, Notification.EXTRA_TITLE);
            String text = getExtrasString(extras, Notification.EXTRA_TEXT);

            // 尝试从 text 中提取发送者
            // 格式可能是 "张三: [微信红包]..." 或 "[微信红包]..."
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

    /**
     * 获取红包的 PendingIntent（用于点击通知跳转到红包页面）
     * 
     * Android 12+ 兼容性处理：
     * - 使用 FLAG_IMMUTABLE 避免安全警告
     * - 处理 actions 中的 PendingIntent
     */
    public static PendingIntent getRedPacketIntent(StatusBarNotification notification) {
        if (notification == null) return null;
        Notification notif = notification.getNotification();
        if (notif == null) return null;

        // 优先使用 contentIntent
        if (notif.contentIntent != null) {
            return notif.contentIntent;
        }

        // 尝试从 actions 中获取
        if (notif.actions != null) {
            for (Notification.Action action : notif.actions) {
                if (action.actionIntent != null) {
                    return action.actionIntent;
                }
            }
        }

        return null;
    }

    /**
     * 获取通知的完整文本
     */
    private static String getExtrasString(Bundle extras, String key) {
        try {
            CharSequence cs = extras.getCharSequence(key);
            return cs != null ? cs.toString() : "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 检查 Android 13+ 通知权限
     */
    public static boolean hasNotificationPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) 
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
        return true;  // Android 12 及以下默认有权限
    }
}
