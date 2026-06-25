package com.redpacket.config;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 红包配置管理
 * 
 * 支持自定义设置：
 * - 最大抢红包数量
 * - 是否开启自动抢
 * - 延迟时间
 * - 只抢拼手气红包
 * - 白名单群聊
 */
public class RedPacketConfig {

    private static final String PREF_NAME = "red_packet_config";
    private static SharedPreferences sPrefs;

    // 配置键
    private static final String KEY_MAX_GRAB_COUNT = "max_grab_count";
    private static final String KEY_LUCKY_GRAB_COUNT = "lucky_grab_count";
    private static final String KEY_AUTO_GRAB_ENABLED = "auto_grab_enabled";
    private static final String KEY_GRAB_DELAY_MS = "grab_delay_ms";
    private static final String KEY_LUCKY_ONLY = "lucky_only";
    private static final String KEY_WHITELIST_GROUPS = "whitelist_groups";
    private static final String KEY_GRABBED_COUNT = "grabbed_count";
    private static final String KEY_LUCKY_GRABBED_COUNT = "lucky_grabbed_count";
    private static final String KEY_TOTAL_AMOUNT = "total_amount";

    // 默认值
    public static final int DEFAULT_MAX_GRAB_COUNT = 0;  // 0 = 不限制
    public static final int DEFAULT_LUCKY_GRAB_COUNT = 5;  // 默认抢5个拼手气红包
    public static final boolean DEFAULT_AUTO_GRAB_ENABLED = true;
    public static final int DEFAULT_GRAB_DELAY_MS = 0;   // 默认无延迟
    public static final boolean DEFAULT_LUCKY_ONLY = false;

    public static void init(Context context) {
        sPrefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static SharedPreferences getPrefs() {
        return sPrefs;
    }

    // === 抢红包数量设置 ===

    /**
     * 设置最大抢红包数量（0 = 不限制）
     */
    public static void setMaxGrabCount(int count) {
        sPrefs.edit().putInt(KEY_MAX_GRAB_COUNT, count).apply();
    }

    public static int getMaxGrabCount() {
        return sPrefs.getInt(KEY_MAX_GRAB_COUNT, DEFAULT_MAX_GRAB_COUNT);
    }

    // === 拼手气红包数量设置 ===

    /**
     * 设置拼手气红包最大数量
     */
    public static void setLuckyGrabCount(int count) {
        sPrefs.edit().putInt(KEY_LUCKY_GRAB_COUNT, count).apply();
    }

    public static int getLuckyGrabCount() {
        return sPrefs.getInt(KEY_LUCKY_GRAB_COUNT, DEFAULT_LUCKY_GRAB_COUNT);
    }

    /**
     * 检查拼手气红包是否还能继续抢
     */
    public static boolean canGrabMoreLucky() {
        int max = getLuckyGrabCount();
        if (max <= 0) return false;
        int grabbed = getLuckyGrabbedCount();
        return grabbed < max;
    }

    public static void incrementLuckyGrabbedCount() {
        int count = getLuckyGrabbedCount();
        sPrefs.edit().putInt(KEY_LUCKY_GRABBED_COUNT, count + 1).apply();
    }

    public static int getLuckyGrabbedCount() {
        return sPrefs.getInt(KEY_LUCKY_GRABBED_COUNT, 0);
    }

    /**
     * 检查是否还能继续抢（总数量 + 拼手气数量都检查）
     */
    public static boolean canGrabMore() {
        // 总数量检查
        int maxTotal = getMaxGrabCount();
        if (maxTotal > 0 && getGrabbedCount() >= maxTotal) return false;
        // 拼手气数量检查
        if (!canGrabMoreLucky()) return false;
        return true;
    }

    // === 自动抢开关 ===

    public static void setAutoGrabEnabled(boolean enabled) {
        sPrefs.edit().putBoolean(KEY_AUTO_GRAB_ENABLED, enabled).apply();
    }

    public static boolean isAutoGrabEnabled() {
        return sPrefs.getBoolean(KEY_AUTO_GRAB_ENABLED, DEFAULT_AUTO_GRAB_ENABLED);
    }

    // === 延迟设置 ===

    public static void setGrabDelayMs(int ms) {
        sPrefs.edit().putInt(KEY_GRAB_DELAY_MS, ms).apply();
    }

    public static int getGrabDelayMs() {
        return sPrefs.getInt(KEY_GRAB_DELAY_MS, DEFAULT_GRAB_DELAY_MS);
    }

    // === 只抢拼手气红包 ===

    public static void setLuckyOnly(boolean luckyOnly) {
        sPrefs.edit().putBoolean(KEY_LUCKY_ONLY, luckyOnly).apply();
    }

    public static boolean isLuckyOnly() {
        return sPrefs.getBoolean(KEY_LUCKY_ONLY, DEFAULT_LUCKY_ONLY);
    }

    // === 白名单群聊 ===

    public static void setWhitelistGroups(String groups) {
        sPrefs.edit().putString(KEY_WHITELIST_GROUPS, groups).apply();
    }

    public static String getWhitelistGroups() {
        return sPrefs.getString(KEY_WHITELIST_GROUPS, "");
    }

    /**
     * 检查群聊是否在白名单中
     * 白名单为空表示所有群都抢
     */
    public static boolean isGroupWhitelisted(String groupName) {
        String whitelist = getWhitelistGroups();
        if (whitelist.isEmpty()) return true;  // 空白名单 = 全部抢
        String[] groups = whitelist.split(",");
        for (String g : groups) {
            if (g.trim().equals(groupName)) return true;
        }
        return false;
    }

    // === 统计数据 ===

    public static void incrementGrabbedCount() {
        int count = getGrabbedCount();
        sPrefs.edit().putInt(KEY_GRABBED_COUNT, count + 1).apply();
    }

    public static int getGrabbedCount() {
        return sPrefs.getInt(KEY_GRABBED_COUNT, 0);
    }

    public static void addAmount(float amount) {
        float total = getTotalAmount();
        sPrefs.edit().putFloat(KEY_TOTAL_AMOUNT, total + amount).apply();
    }

    public static float getTotalAmount() {
        return sPrefs.getFloat(KEY_TOTAL_AMOUNT, 0f);
    }

    public static void resetStats() {
        sPrefs.edit()
            .putInt(KEY_GRABBED_COUNT, 0)
            .putFloat(KEY_TOTAL_AMOUNT, 0f)
            .apply();
    }
}
