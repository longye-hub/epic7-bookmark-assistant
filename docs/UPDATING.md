# 发布更新与联网边界

## 老用户怎样获得更新

老的 Root v1.5.1 / Mobile v1.0.1 没有联网更新功能，需要先从 Releases 手动下载本次新版并覆盖安装。不要先卸载，卸载会丢失设置和待核验记录。

本次版本提供启动页自动提醒（每24小时最多一次，可关闭）、手动检查及普通公告。只在启动页查询；没有常驻后台推送或强制升级。发现新版显示公开说明和测试渠道，用户自行选择是否下载，安装仍由安卓系统确认。普通公告见 [ANNOUNCEMENTS.md](ANNOUNCEMENTS.md)。

## 以后怎样发新版

1. 保持各版包名和发布者现有签名，分别递增 `versionCode`；不能只改展示的 `versionName`。目前 Root 为15/1.5.3，手机版为4/1.0.3。
2. 完成相应测试，上传对应 APK 到新 Release。文件名须为 `epic7-root-v版本.apk` 或 `epic7-mobile-v版本.apk`，与清单一致。
3. 核验上传文件SHA256后，再更新 [CNB通知仓库](https://cnb.cool/longye-hub/epic7-notices) 中对应清单，同时同步本仓库 `updates/root.properties` / `updates/mobile.properties` 到main，兼容仍使用GitHub的旧客户端。**先让安装包可公开下载，再更新清单**，避免提醒指向不存在的文件。
4. 运行公开测试并核对清单的包名、版本号、渠道、说明、文件地址和哈希。手机与Root单独管理；只有其中一版更新时只修改对应清单。

格式是严格UTF-8 `key=value` 文本，不是Java Properties转义格式。字段必须完整且唯一，禁止增加设备信息、任意链接、脚本或上传参数；修改协议需要增加schema并先更新客户端。

```text
schema=1
packageName=com.e7.bmfarm.mobile
versionCode=4
versionName=1.0.3
channel=preview
releaseUrl=https://github.com/longye-hub/epic7-bookmark-assistant/releases/tag/2026.10.14
downloadUrl=https://github.com/longye-hub/epic7-bookmark-assistant/releases/download/2026.10.14/epic7-mobile-v1.0.3.apk
sha256=这里填写上传APK的64位十六进制SHA256
notes=这里写一行公开更新说明，可使用中文
```

`channel` 为 `preview` 或 `stable`，客户端显示其属性；当前没有用户可选的双渠道开关。当前测试包对应清单为preview，不能通过普通latest-release接口遗漏测试版。

## 请求与隔离

只读更新服务是私有 `:updates` 进程，`exported=false`。主程序只通过Messenger发送CHECK信号及本机请求序号，更新服务回传公开文本，不接收画面、私有交易记录、设备标识或任意URL。APK不会在助手内下载：链接交给浏览器。

两版固定请求作者CNB仓库main下的公开文件，不自动回退到GitHub：

- `https://cnb.cool/longye-hub/epic7-notices/-/git/raw/main/root.properties`
- `https://cnb.cool/longye-hub/epic7-notices/-/git/raw/main/mobile.properties`
- `https://cnb.cool/longye-hub/epic7-notices/-/git/raw/main/announcements.properties`

请求方法只有GET；无正文、Cookie、Authorization、设备ID、账户信息、当前版本或公告已读状态；User-Agent固定为 `Epic7-UpdateCheck/1`。拒绝重定向，单文件最多读取16KiB，使用系统TLS信任锚，不允许明文HTTP或自定义信任所有证书。

**普通独立进程共享应用UID和权限。** 此方案是经用户选择的代码隔离，不能阻止被攻陷的同UID代码访问私有数据，也没有把安卓INTERNET权限变成“只接收更新”的细分权限。网络连接仍使托管平台或网络链路获得IP等基础连接信息。README和界面不得宣称系统级隔离、绝对不可能泄露或匿名到无法识别网络来源。

`sha256`用于公开资产核验与发布验证；助手不下载APK，不能声称它在设备上已经自动核对了下载文件。系统签名兼容检查与用户核验发布哈希是不同环节。保持签名密钥安全，本仓库不包含作者签名材料；他人自行构建的不同签名包不能保证覆盖原版。
