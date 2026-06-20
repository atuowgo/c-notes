# 移动端 — Capacitor 包壳(V3 的另一半)

> 状态:**已搭好包壳并完成 Android APK 构建验证**(2026-06-20)。移动端复用 `@cnotes/web` 的
> 同一份构建产物(`apps/web/dist`),由 Capacitor 装进原生 WebView。

## ✅ Android APK 构建已验证(2026-06-20)

装好 Android SDK 后,`./gradlew :app:assembleDebug` **BUILD SUCCESSFUL**,产出可安装的调试包:

- 产物:`apps/mobile/android/app/build/outputs/apk/debug/app-debug.apk`(约 3.8 MB)。
- 包信息:`package=com.cnotes.app`,label「知识炼金炉」,minSdk 22 / targetSdk 34 / compileSdk 34。
- APK 内 `assets/public/` 即 `@cnotes/web` 的最新 dist(index.html + 同名 hash 的 js/css),
  确认「一处 web 多端复用」在原生壳内成立。

构建步骤(本沙箱现装现验):
```bash
export ANDROID_HOME=/opt/android-sdk
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
cd frontend && pnpm --filter @cnotes/mobile sync          # 产 web dist 并 cap sync 进原生工程
cd apps/mobile/android && echo "sdk.dir=$ANDROID_HOME" > local.properties
ANDROID_HOME=$ANDROID_HOME ./gradlew :app:assembleDebug    # 产 app-debug.apk
```

> 仍需真机/模拟器才能"运行/截屏"(`adb install` 后启动);本环境无设备,但 APK 产物本身
> 已是构建链路打通的证据。发布包(AAB/release)走 `assembleRelease` + 签名密钥。

## 形态

- `apps/mobile`(`@cnotes/mobile`):仅持有 `capacitor.config.ts` + 依赖 + 脚本;
  `webDir` 指向 `../web/dist`,**不重复实现 UI**——一处 web 代码,多端复用(收件箱、阅读、
  知识网、深聊在手机上是同一套)。
- 生成的原生工程 `apps/mobile/android/`(及未来 `ios/`)**不入仓**(`.gitignore` 已忽略),
  随时可由 `npx cap add android` 重建。

## 已验证(本环境内可跑的部分)

- `pnpm --filter @cnotes/mobile sync` → 构建 web 产物并 `cap sync`,资产复制到原生工程成功。
- `npx cap add android` → 原生 Android 工程脚手架创建成功,web 资产已落到
  `android/app/src/main/assets/public`。
- Playwright `e2e/mobile-viewport.e2e.ts`(Pixel 7 视口)→ 同一份 dist 在手机屏可正常渲染、
  打开文章、深聊 FAB 可见。**这正是 Capacitor 包壳后 WebView 内加载的内容**。

## 真机 / 模拟器构建(环境前置,外部步骤)

打 APK / 跑模拟器需安装 **Android SDK + JDK 17**(本沙箱未装,属环境限制):

```bash
# 1) 装 Android Studio 或命令行 SDK,设 ANDROID_HOME / sdkmanager 装好 platform + build-tools
# 2) 构建 web 并同步
cd frontend
pnpm --filter @cnotes/mobile sync
# 3) 用 Android Studio 打开,或命令行构建
pnpm --filter @cnotes/mobile open:android        # 打开 Android Studio
#   或: cd apps/mobile/android && ./gradlew assembleDebug   # 产 APK
```

iOS 同理:`npx cap add ios` + Xcode(需 macOS)。

## 开发期热更新(可选)

`capacitor.config.ts` 里 `server.url` 取消注释并指向你电脑的局域网地址(如 `http://192.168.1.10:8088`,
即同源 nginx),手机 App 即加载 dev 服务器,改 web 代码免重打包。
