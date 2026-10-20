# 真寻Bot的Android管理后台

界面采用毛玻璃（Glassmorphism）风格：半透明渐变卡片、细描边、柔和光斑背景；登录页使用渐变遮罩与玻璃表单。

****

食用前需要暴露真寻Bot接口到外部网络, 比如修改 `.env.dev`:
`HOST = 127.0.0.1` => `HOST = 0.0.0.0`

或者采用 `Nginx` 等Web服务器进行反向代理

<img src="https://github.com/YuS1aN/zhenxun_bot_android_ui/blob/master/docs/main.jpg?raw=true" width="162" height="360" /> <img src="https://github.com/YuS1aN/zhenxun_bot_android_ui/blob/master/docs/plugin.jpg?raw=true" width="162" height="360" /> <img src="https://github.com/YuS1aN/zhenxun_bot_android_ui/blob/master/docs/menu.jpg?raw=true" width="162" height="360" />
<br />
<img src="https://github.com/YuS1aN/zhenxun_bot_android_ui/blob/master/docs/db.jpg?raw=true" width="162" height="360" /> <img src="https://github.com/YuS1aN/zhenxun_bot_android_ui/blob/master/docs/edit.jpg?raw=true" width="162" height="360" /> <img src="https://github.com/YuS1aN/zhenxun_bot_android_ui/blob/master/docs/conversation.jpg?raw=true" width="162" height="360" />

## 构建正式版 APK

GitHub Actions 工作流位于 `.github/workflows/release.yml`，会在推送到 `master`/`main`、推送 `v*` 标签或手动触发时构建 release APK，并上传为构建产物。推送 `v*` 标签时还会自动创建 GitHub Release。

签名通过仓库 Secrets 配置，未配置时构建出的 APK 为未签名版本：

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

本地构建正式版：

```bash
./gradlew assembleRelease
```

产物路径为 `app/build/outputs/apk/release/zxbot-release-v<versionName>.apk`；未配置签名时文件名会追加 `-unsigned` 后缀。
