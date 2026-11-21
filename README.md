<div align="center">

简体中文 | [繁體中文](README.zh-TW.md) | [English](README.en.md)

# Cove Recipe

**中文、离线、本地优先的 Android 个人菜谱本：厨房模式、全局计时、来源追踪购物清单与 ZIP 完整备份**

[![Release](https://img.shields.io/github/v/release/vanvan-program/cove-recipe?style=flat-square)](https://github.com/vanvan-program/cove-recipe/releases/latest)
[![Android](https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-3DDC84?style=flat-square)](#安装与数据安全)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21%20%7C%20Compose%20%7C%20Room-7F52FF?style=flat-square)](#技术栈与工程结构)
[![License](https://img.shields.io/badge/License-MIT-green?style=flat-square)](LICENSE)

[下载安装包（Releases）](https://github.com/vanvan-program/cove-recipe/releases) · [更新日志](CHANGELOG.md) · [第三方组件说明](THIRD_PARTY_NOTICES.md) · [问题反馈](https://github.com/vanvan-program/cove-recipe/issues)

</div>

---

## 项目定位

**Cove Recipe** 是一款菜谱离线可用、本地优先的 Android 个人菜谱本（最低支持 Android 8 / API 26，目标 SDK 为 Android 16 / API 36）。专注记录与烹饪体验本身：**无账号、无广告、无云端同步。联网用于你手动发起的版本检查和安装包下载**。

> [!TIP]
> 1.1.0 ～ 1.5.0 的逐版本更新说明已整理至 **[CHANGELOG.md](CHANGELOG.md)**。

## 界面示例

以下为 1.4.0 专用模拟器测试截图（使用测试菜谱数据）：

<p>
  <img src="docs/screenshots/140-home.png" width="230" alt="菜谱首页" />
  <img src="docs/screenshots/140-detail-seasonings.png" width="230" alt="菜谱详情与调料备注" />
  <img src="docs/screenshots/140-shopping-sources.png" width="230" alt="购物清单与来源菜谱" />
</p>

## 核心功能

- **结构化菜谱管理**：
  - 记录封面图（自动压缩至最长边约 1600px 并存于应用私有目录）、结构化**食材**、**调料备注**、**备菜说明**、可排序烹饪步骤、火候与步骤参考时间。
  - 支持长按拖拽自定义排序（含置顶分组与无障碍上移/下移）、最多三级自定义分类、自由标签，以及菜名 / 食材 / 标签 / 分类联合搜索、收藏、置顶与最近使用。
- **厨房模式与全局倒计时**：
  - 烹饪时启用大字体、屏幕常亮、步骤导航与食材/调料速查面板。
  - 内置全局倒计时器（小时/分钟/秒滚轮设置，1 秒至 7 天），基于单调时钟运行，支持暂停/继续/修改、通知栏实时倒计时及到点循环震动提醒。
- **可追踪来源的购物清单**：
  - 支持从单道菜谱、首页多选或购物页直接批量加入食材与调料；自动合并同名食材并保留「来自：菜名」来源标注。
  - 支持采购备注、已买勾选与删除撤销。
- **ZIP v3 完整备份与严格校验**：
  - 单文件 ZIP 打包全部菜谱、分类、标签、购物清单与封面图片（兼容导入格式 v1 / v2）。
  - 恢复前先校验版本、引用关系、大小上限（单 JSON ≤ 16 MB、单图 ≤ 32 MB、解压总量 ≤ 256 MB）与图片合法性，事务内原子替换，失败不破坏原数据。

## 安装与数据安全

- 可从 [GitHub Releases](https://github.com/vanvan-program/cove-recipe/releases) 下载发布包，或自行构建 `app/build/outputs/apk/debug/app-debug.apk`（PowerShell 脚本另复制为 `dist/CoveRecipe-1.5.0.apk`）。
- 应用包名为 `cn.myrecipe.app`。升级请**直接覆盖安装**并保持同一签名，切勿先卸载；不同电脑生成的 debug 签名不同，换包或卸载前请务必在设置中导出 ZIP 备份（系统云备份与设备迁移均已关闭）。
- 首次使用计时器需授予「通知」与「精确闹钟」权限；锁屏与常规切后台可持续计时，手机重启后进行中的计时会标记为中断且不补响。
- 导出的 ZIP 备份未加密，请妥善保存在信任的位置。

## 技术栈与工程结构

- **技术栈**：Kotlin `2.0.21` · Jetpack Compose (BOM `2025.02.00`) · Material 3 · Room `2.6.1`（数据库 v3，支持显式无损迁移）· Navigation `2.8.9` · DataStore `1.1.3` · AGP `8.9.2` · Gradle `8.11.1`
- **模块划分**（单 `app` 模块）：
  - `core` — Room 数据库、图片管理、计时服务、ZIP 备份契约与主题
  - `data/repository` — 本地仓库层
  - `feature` — 菜谱、编辑、厨房模式、购物清单、分类标签与设置页面（ViewModel + StateFlow）

## 从源码构建与测试

- **环境要求**：JDK `17+` · Android SDK `36` · Gradle `8.11.1`

```powershell
git clone https://github.com/vanvan-program/cove-recipe.git
cd cove-recipe
# 配置 JDK 与 ANDROID_HOME（或 local.properties）后运行：
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

- macOS / Linux 使用 `./gradlew` 执行相同任务；本机具备 `.tools/` 离线环境时可运行 `.\scripts\build.ps1 -Offline`（加 `-DeviceTests` 运行模拟器测试）。验证说明见 `docs/1.4.0验证.md`。

## 开源协议

本项目采用 **[MIT License](LICENSE)** 开源，第三方依赖与许可说明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
