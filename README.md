# 微信自动抢红包 Pro v2.0

## 功能特性

### ✅ 完整功能
- **自动抢拼手气红包最后一个**
- 支持 Android 12+ (API 31+)
- 支持 Android 13+ 通知权限
- 支持 Android 14+ 前台服务
- 自定义最大抢红包数量
- 自定义抢红包延迟
- 只抢拼手气红包模式
- 白名单群聊过滤
- 实时运行日志
- 抢红包统计

### 🔧 技术改进
1. **线程安全** - 使用 ConcurrentLinkedQueue 管理红包队列
2. **防重复点击** - AtomicBoolean 保证单次处理
3. **多种点击方式** - resource-id → text → desc → 位置 → 手势
4. **自动降级** - performAction 失败自动切换手势点击
5. **连续失败保护** - 超过5次自动等待新通知
6. **领取结果检查** - 1秒后验证是否领取成功

### 📱 兼容性
| Android 版本 | 支持状态 |
|-------------|---------|
| Android 8.0+ | ✅ 完全支持 |
| Android 10+ | ✅ 完全支持 |
| Android 12+ | ✅ 完全支持 |
| Android 13+ | ✅ 完全支持 |
| Android 14+ | ✅ 完全支持 |

## 安装使用

### 1. 编译安装
```bash
# 使用 Android Studio
File → Open → 选择项目目录
Build → Make Project
Run → Run 'app'

# 命令行编译
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 2. 授权服务
1. 打开 App → 点击"通知访问" → 找到"红包助手" → 开启
2. 打开 App → 点击"无障碍服务" → 找到"红包助手" → 开启
3. 开启"自动抢红包"开关

### 3. 自定义设置
点击"⚙️ 设置"按钮：
- **最大数量**: 设置最多抢几个红包（0=不限）
- **延迟时间**: 设置抢红包前的延迟（毫秒）
- **抢红包类型**: 所有红包 / 只抢拼手气
- **白名单群聊**: 只抢指定群的红包（逗号分隔）

### 4. 开始使用
- 打开微信，进入任意群聊
- 当有人发红包时，App 会自动检测并点击"开"
- 日志会显示抢到的红包记录

## 技术原理

### 1. NotificationListenerService
监听系统通知，检测微信红包通知关键词：
- `[微信红包]`
- `拼手气红包`
- `你收到一个红包`

### 2. AccessibilityService
遍历当前界面 View 树，查找"开"按钮：
- resource-id: `com.tencent.mm:id/den`
- text: `开`
- content-description: `开`
- 位置: 屏幕中央偏下区域

### 3. 手势点击 (Android 7.0+)
当 performAction 失败时，使用 GestureDescription 模拟点击：
```java
Path clickPath = new Path();
clickPath.moveTo(x, y);
GestureDescription.Builder builder = new GestureDescription.Builder();
builder.addStroke(new GestureDescription.StrokeDescription(clickPath, 0, 50));
dispatchGesture(builder.build(), callback, null);
```

## 项目结构

```
wechat-red-packet/
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/redpacket/
│   │   ├── config/
│   │   │   └── RedPacketConfig.java        # 配置管理
│   │   ├── service/
│   │   │   ├── RedPacketNotificationListener.java  # 通知监听
│   │   │   ├── RedPacketAccessibilityService.java   # 无障碍服务
│   │   │   └── BootReceiver.java                    # 开机自启
│   │   ├── ui/
│   │   │   └── MainActivity.java                    # 主界面
│   │   └── util/
│   │       └── RedPacketDetector.java               # 红包检测
│   └── res/
│       ├── layout/
│       │   ├── activity_main.xml
│       │   └── dialog_settings.xml
│       ├── xml/accessibility_config.xml
│       └── values/
├── build.gradle
└── README.md
```

## 常见问题

### Q: 为什么抢不到红包？
A: 可能原因：
1. 通知监听服务未授权或被系统杀死
2. 无障碍服务未授权或被系统杀死
3. 微信版本更新导致 resource-id 变化
4. 手机省电策略限制了后台服务
5. "自动抢红包"开关未开启

### Q: Android 12+ 有什么特殊处理？
A: 
- 使用 `canPerformGestures="true"` 启用手势
- 使用 `RECEIVER_NOT_EXPORTED` 注册广播
- 使用 `foregroundServiceType="dataSync"` 声明服务类型
- 自动刷新现有通知

### Q: 如何避免被微信检测？
A: 本项目使用 Android 标准 API，与手机厂商自带功能原理相同。但建议：
- 不要设置过短的延迟
- 不要抢太多红包
- 适当休息

### Q: 支持微信多开吗？
A: 当前版本只支持官方微信。微信多开/分身需要修改包名检测逻辑。

## 更新日志

### v2.0.0 (2026-06-25)
- 新增自定义设置（最大数量、延迟、白名单）
- 新增只抢拼手气红包模式
- 新增 Android 12+ 完整兼容
- 新增 Android 13+ 通知权限支持
- 新增手势点击降级方案
- 新增连续失败保护
- 新增领取结果检查
- 优化线程安全性
- 优化错误处理
- 优化日志系统

### v1.0.0 (2026-06-25)
- 初始版本
- 基础抢红包功能
- 简单设置界面
