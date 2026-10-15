# 发布更新与联网边界

Root v1.5.4（versionCode 16）、手机 v1.0.5（versionCode 6）提供 CNB 国内下载，GitHub 保留备用。两处必须放置完全相同的原签名 APK。覆盖安装前停止任务、核对待确认交易，不要先卸载。没有更新功能的旧版需要先手动下载；带检查功能的旧版仍通过兼容清单获得升级提醒。

## 发布顺序

1. 保持各版包名及发布者签名，对有改动的应用递增 `versionCode`（本次仅手机版；Root版未改动，保持16/1.5.4），构建和验证。文件名必须为 `epic7-root-v版本.apk` 或 `epic7-mobile-v版本.apk`。
2. 将 APK、`SHA256SUMS.txt` 和使用说明上传到 GitHub Release，并放入 [CNB 通知仓库](https://cnb.cool/longye-hub/epic7-notices) 同标签 Release 的正式附件区。源码仍发布在 GitHub；CNB 的自动 Source code 附件只是通知仓库的文本源文件。
3. 匿名下载两渠道正式 APK，核对字节数和 SHA256。**先验证安装包可公开下载，再发布版本清单**。
4. 更新 CNB 与本仓库 `updates/` 下的 `root-v2.properties`、`mobile-v2.properties`；同时更新对应的 `root.properties`、`mobile.properties`（未升级的应用保留原清单） 的 schema=1 GitHub 链接，兼容 Root v1.5.2/v1.5.3、手机 v1.0.2/v1.0.3。不要把旧路径直接改为 schema=2，否则旧解析器会拒绝清单。
5. 运行公开测试、生产网络读取检查，确认包类型、标签、版本、哈希一致。普通公告另外按递增编号发布，见 [ANNOUNCEMENTS.md](ANNOUNCEMENTS.md)。

格式为严格 UTF-8 无 BOM 的 `key=value` 文本，每个值一行，不是 Java Properties 转义格式。字段须完整唯一，不接受额外设备字段、操作指令或任意链接。

```text
schema=2
packageName=com.e7.bmfarm.mobile
versionCode=6
versionName=1.0.5
channel=preview
releaseUrl=https://cnb.cool/longye-hub/epic7-notices/-/releases/tag/2026.10.15
downloadUrl=https://cnb.cool/longye-hub/epic7-notices/-/releases/download/2026.10.15/epic7-mobile-v1.0.5.apk
fallbackUrl=https://github.com/longye-hub/epic7-bookmark-assistant/releases/download/2026.10.15/epic7-mobile-v1.0.5.apk
sha256=这里填写上传APK的64位十六进制SHA256
notes=这里写一行公开更新说明
```

schema=1 不含 `fallbackUrl`，`releaseUrl` 和 `downloadUrl` 都必须指向作者 GitHub 源码仓库。schema=2 必须使用作者 CNB 通知仓库的正式发布路径和作者 GitHub 对应备用链接；标签、版本与包类型必须一致。拒绝 HTTP、其他作者/仓库、查询参数、限时分享链接或任意临时对象地址。`channel` 仅为 `preview` 或 `stable`，当前两版继续以测试渠道发布。

## 请求与隔离

私有 `:updates` 普通进程服务 `exported=false`。主程序通过 Messenger 只发送 CHECK 信号及本机请求序号，不发送当前版本、截图、余额、设备标识、交易记录、已读状态或可配置 URL。服务只回传公开文本。

新版仅请求 CNB 固定目录 `https://cnb.cool/longye-hub/epic7-notices/-/git/raw/main/` 下的两类版本清单之一和 `announcements.properties`。GET 无正文、Cookie、Authorization 或用户字段；固定 User-Agent `Epic7-UpdateCheck/1`。拒绝元数据重定向、单文件超过 16KiB 和无效 UTF-8，采用系统 TLS 校验，不修改信任锚。退出启动页取消请求；不自动请求 GitHub 备用地址。

APK 下载交给浏览器，浏览器会跟随 CNB 正式下载链接到平台对象存储的正常跳转。助手不自行下载、不读取浏览器下载文件、不静默安装。`sha256` 用于发布资产核验，不能宣称助手已自动验证用户手机上的下载文件。

**同 UID 的普通进程共享整个应用权限。** 这是用户选择的代码隔离，不能宣称系统级权限隔离或绝对不泄露。平台与网络仍可获得 IP 等正常连接信息。没有平台/统计/推送 SDK、用户注册或后台推送；提醒发生在打开助手时，每24小时最多自动检查一次，可关闭或手动检查。
