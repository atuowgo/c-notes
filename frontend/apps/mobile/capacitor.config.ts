import type { CapacitorConfig } from '@capacitor/cli';

// 移动端包壳:复用 @cnotes/web 的构建产物(../web/dist)。
// 真机/模拟器构建需安装 Android SDK(npx cap add android 后用 Android Studio 打开 open:android)。
const config: CapacitorConfig = {
  appId: 'com.cnotes.app',
  appName: '知识炼金炉',
  webDir: '../web/dist',
  // 开发期可指向同源 dev 服务器实现热更新(取消注释并改成你的局域网地址):
  // server: { url: 'http://192.168.1.10:8088', cleartext: true },
};

export default config;
