# 今晚打地鼠

一款用裸手捏合操作的 PICO 空间打地鼠游戏。瞄准亮灯的洞口，等地鼠露头再捏合。五轮依次加入金鼠、锅盖鼠、木偶和鼠王。前四轮结束可三选一强化，用连击和金鼠赚到的奖券可以重抽。

游戏支持暂停、音效开关、减少动效和本机最高分。设置里的「透视」可在房间背景和全包围的虚拟游艺厅之间切换。

## 构建

需要 JDK 17 和 Android SDK 35。用 Android Studio 打开仓库，或在命令行把 ANDROID_HOME 指向已安装的 Android SDK。工程通过 Gradle Wrapper 使用 PICO Spatial SDK 6.2.8。

### Windows

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

### macOS / Linux

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

## 试玩

在 PICO OS 6 模拟器启动后安装 APK，再从应用列表打开「今晚打地鼠」。

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

选择「开一局」。第一只地鼠会等你敲中，模拟器里可用空间点选。瞄准洞口时看洞沿反馈。鼠王关里，红灯是虚晃，金灯预告真正的露头。结算会显示最高连击、打中的金鼠数和本局选过的强化。

右侧的「设置」可以暂停这一局并切换透视。
