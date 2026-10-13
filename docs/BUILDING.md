# 从源码构建

当前构建入口为 **Windows + PowerShell 7 + JDK 17 + Android SDK**，未接入 Android Studio/Gradle 项目模型。Linux/macOS 构建未验证。

## 准备工具

- JDK 17，并设置 `JAVA_HOME`。
- 从 Android 官方工具安装 SDK，设置 `ANDROID_HOME`。
- 安装 Android Platform 33、34，以及 Build Tools 34.0.0。自行阅读并接受供应方许可；仓库不分发这些工具。

已配置好 `sdkmanager` 的情况下，组件名称为：

```powershell
sdkmanager "platforms;android-33" "platforms;android-34" "build-tools;34.0.0"
```

工具资料：[Android 命令行工具](https://developer.android.com/tools)、[签名工具](https://developer.android.com/tools/apksigner)。

## 构建与测试

在仓库根目录执行：

```powershell
./scripts/test.ps1
./scripts/build.ps1 -Variant all
# 只构建一个版本：-Variant root 或 -Variant mobile
```

也可通过 `-JavaHome`、`-SdkRoot` 显式传入路径。特殊 SDK 目录可用 `-BuildToolsPath`、`-RootAndroidJar`、`-MobileAndroidJar` 指定，不需要修改源码。

APK 输出到 `dist/`，临时编译文件在 `build/`。脚本会执行 Java 编译、DEX 转换、资源打包、对齐、签名和验签。运行测试不连接模拟器，也不操作游戏。

## 签名与更新

首次构建在 `.local-signing/` 生成一把**你本机自己的调试签名密钥**，使用标准调试口令，后续构建复用。它只适合本地学习测试，不是生产签名方案。不要把该目录上传或当作官方身份凭据。

发布者的签名密钥及口令未包含在源码中。自己构建的 APK 与 Release APK 签名不同，不能直接覆盖安装官方包；卸载会丢失应用数据和待核验记录，切换前先人工核实交易并做好必要记录。不要为绕过签名错误盲目清除数据。

两版独立包名允许共存。源码与资源对应公开的 Root v1.5.1 / Mobile v1.0.1；ZIP 元数据、工具版本和签名差异意味着自行构建不保证与 Release 文件 SHA256 相同。

## 测试范围

公开脚本覆盖布局、输入取消与动画时序。完整历史图像重放使用了未公开的现场截图，详见 [VALIDATION.md](VALIDATION.md)。构建或公开测试成功不等于所有手机适配或真实交易验证通过。
