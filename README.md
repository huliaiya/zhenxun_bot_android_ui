# 真寻Bot的Android管理后台

真寻 Bot 的 Android 管理客户端，用于在手机上查看 Bot 运行状态、管理插件与群组、处理好友和群请求、浏览数据库与文件，以及直接和 Bot 对话。

界面采用毛玻璃（Glassmorphism）风格：半透明渐变面板、细描边、柔和光斑背景，同时适配浅色与深色模式。

## 使用前准备

App 需要访问真寻 Bot 的接口，请先把接口暴露到外部网络。修改真寻的 `.env.dev`：

```dotenv
HOST = 0.0.0.0
```

也可以使用 Nginx 等 Web 服务器做反向代理。登录时填写服务器地址和账号即可。

## 截图

<img src="https://github.com/YuS1aN/zhenxun_bot_android_ui/blob/master/docs/main.jpg?raw=true" width="162" height="360" /> <img src="https://github.com/YuS1aN/zhenxun_bot_android_ui/blob/master/docs/plugin.jpg?raw=true" width="162" height="360" /> <img src="https://github.com/YuS1aN/zhenxun_bot_android_ui/blob/master/docs/menu.jpg?raw=true" width="162" height="360" />
<br />
<img src="https://github.com/YuS1aN/zhenxun_bot_android_ui/blob/master/docs/db.jpg?raw=true" width="162" height="360" /> <img src="https://github.com/YuS1aN/zhenxun_bot_android_ui/blob/master/docs/edit.jpg?raw=true" width="162" height="360" /> <img src="https://github.com/YuS1aN/zhenxun_bot_android_ui/blob/master/docs/conversation.jpg?raw=true" width="162" height="360" />

## 构建环境

- JDK 17
- Android SDK Platform 34
- Android NDK 26.1.10909125
- CMake 3.22.1

项目使用 Gradle 8.4、Android Gradle Plugin 8.3.0、Kotlin 1.9.22，最低支持 Android 6.0（API 23）。

## 本地构建

```bash
./gradlew assembleRelease
```

产物位于 `app/build/outputs/apk/release/zxbot-release-v<versionName>.apk`。

需要临时指定版本名时：

```bash
./gradlew assembleRelease -PversionName=1.2.3
```

## 签名配置

仓库内已包含 release 签名材料：

| 文件 | 说明 |
| --- | --- |
| `zhenxun-release.jks` | release keystore |
| `keystore.properties` | keystore 路径、密码、别名等配置 |

当前签名别名与证书 CN 均为 `me.huli.zhenxun.webui`，应用包名（applicationId）同为 `me.huli.zhenxun.webui`。Gradle 在检测到 `keystore.properties` 且 keystore 文件存在时，会自动为 release 包签名；未检测到签名材料时，产物文件名会追加 `-unsigned` 后缀。

更换签名时，替换 `zhenxun-release.jks` 并同步更新 `keystore.properties` 中的 `storeFile`、`storePassword`、`keyAlias`、`keyPassword`。

仓库是公开的，签名材料会随仓库公开。正式对外分发时，建议改用私有 keystore，并通过 CI Secrets 注入。

## GitHub Actions 构建

工作流位于 `.github/workflows/release.yml`，触发条件：

- 推送到 `master` 或 `main`
- 推送 `v*` 标签
- 向 `master` 或 `main` 提交 Pull Request
- 手动触发 `workflow_dispatch`

构建流程：

1. 安装 JDK 17
2. 配置 Android SDK，并安装 NDK 26.1.10909125 与 CMake 3.22.1
3. 恢复签名材料（仓库内 keystore 或 Secrets 覆盖）
4. 执行 `assembleRelease`
5. 上传 APK 构建产物

产物名称：

- Artifact：`zhenxun-bot-manager-release`
- APK：`zxbot-release-v<versionName>.apk`

推送 `v*` 标签时，标签名会作为版本名（例如 `v1.2.3` 对应 `1.2.3`），构建完成后自动创建 GitHub Release 并上传 APK。

### 使用 Secrets 覆盖签名

需要在 CI 中使用独立签名时，在仓库 Settings → Secrets and variables → Actions 中配置：

| Secret | 说明 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | keystore 文件的 Base64 内容 |
| `ANDROID_KEYSTORE_PASSWORD` | keystore 密码 |
| `ANDROID_KEY_ALIAS` | 签名密钥别名 |
| `ANDROID_KEY_PASSWORD` | 签名密钥密码 |

生成 Base64 内容：

```bash
base64 -w 0 release.jks > release.jks.base64
```

配置后，工作流会用 Secrets 中的 keystore 覆盖仓库内的签名文件；未配置时直接使用仓库内的 keystore。

## 签名变更提示

不同签名密钥签出的 APK 无法直接覆盖安装。更换签名后，需要先卸载旧版本再安装新包。
