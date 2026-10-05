# OPPO/一加/真我 ColorOS 17 状态栏时钟显秒

基于 libxposed API 102 的 LSPosed 模块，APK 包名为 `io.github.coolzyd9107.coloros17clockseconds`。保持 ColorOS 状态栏时钟的显秒状态，不让系统五分钟后自动关闭；用户仍可在设置或长按状态栏时钟关闭显秒。

## 工作方式

模块作用于 `com.android.systemui`、`com.android.settings` 和 `com.android.launcher`。ColorOS 17 使用 `ClockSecondsRepository` 保存显秒模式和截止时间：模式 `1` 表示用户已开启，模式 `0` 表示用户已关闭。SystemUI 中，模块在模式为 `1` 时让系统的五分钟有效性检查通过，并阻止状态栏时钟超时任务排队，不依赖人为延长截止时间；模式为 `0` 时不干预系统行为。Settings 中，模块让显秒设置项根据实际开关模式显示状态，并显示过期提示；Launcher 中恢复最近任务内存信息的设置和可用状态，仍由用户自行开关。

## 构建

需要 JDK 17 或更高版本以及 Android SDK 35。Windows：

```powershell
.\gradlew.bat assembleDebug
```

生成的可安装 APK：`app\build\outputs\apk\debug\app-debug.apk`。

## GitHub Actions Release

Actions 工作流位于 `.github/workflows/release.yml`。推送 `v*` 格式的 tag（例如 `v1.0.0`）会构建签名 release APK 并附加到 GitHub Release；也可以在 Actions 页面手动运行，下载构建 artifact。

仓库的 **Settings > Secrets and variables > Actions** 中需要配置以下 repository secrets：

| Secret | 内容 |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | `.secrets\coloros-clock-release.p12` 的单行 Base64 内容 |
| `RELEASE_STORE_PASSWORD` | 创建 PKCS12 keystore 时设置的 store password |
| `RELEASE_KEY_ALIAS` | 创建 keystore 时设置的 alias；当前本地 keystore 使用 `coloros-clock-release` |
| `RELEASE_KEY_PASSWORD` | keystore 中私钥的 password；当前本地 PKCS12 使用与 store password 相同的值 |

Windows PowerShell 可用以下命令生成 `RELEASE_KEYSTORE_BASE64` 的值，然后将完整输出粘贴到对应 secret：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes(".secrets\coloros-clock-release.p12"))
```

Keystore 保存在被 Git 忽略的 `.secrets` 目录中，不要提交或公开。不要将密码写入项目文件；签名私钥用于后续版本升级，应妥善备份。

## 启用

安装 APK 后，在 LSPosed 管理器中启用模块，并确认作用域包含 `System UI (com.android.systemui)`、`Settings (com.android.settings)` 和 `Launcher (com.android.launcher)`，然后重启对应进程或设备使 hook 生效。模块不需要单独的启动器界面，也不申请额外权限。

## 兼容性

hook 目标来自 ColorOS 17 的 `com.oplus.systemui.statusbar.clock.ClockSecondsRepository` 和 `ClockSecondsController`。若后续系统版本重命名这些类或方法，模块会记录 hook 初始化错误，届时需要针对新版本重新适配。
