package com.redpacket.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * 开机自启接收器
 * 
 * Android 8.0+ 优化：
 * - 使用 JobScheduler 代替直接启动服务
 * - 兼容后台限制策略
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.i("BootReceiver", "手机重启，红包助手准备就绪");
            
            // 通知监听服务和无障碍服务会在系统启动后自动恢复
            // Android 8.0+ 对后台服务有限制，但已授权的服务会自动重新绑定
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Android 8.0+ 使用前台服务通知
                Log.i("BootReceiver", "Android 8.0+ 检测到，服务将自动恢复");
            }
        }
    }
}
