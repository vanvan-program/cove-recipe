# 第三方组件说明

Cove Recipe 自有源码采用 [MIT License](LICENSE)。第三方组件仍遵循各自的许可证。

| 组件 | 项目 | 许可证 |
| --- | --- | --- |
| Gradle Wrapper | https://github.com/gradle/gradle | Apache-2.0 |
| Kotlin 与 Compose Compiler 插件 | https://github.com/JetBrains/kotlin | Apache-2.0 |
| Android Gradle Plugin | https://android.googlesource.com/platform/tools/base | Apache-2.0 |
| AndroidX（Compose、Room、Navigation、Lifecycle、DataStore、Activity、测试库） | https://android.googlesource.com/platform/frameworks/support | Apache-2.0 |
| Kotlin Symbol Processing | https://github.com/google/ksp | Apache-2.0 |
| JUnit 4（仅测试） | https://github.com/junit-team/junit4 | EPL-1.0 |
| JSON-java（仅测试） | https://github.com/stleary/JSON-java | Public Domain |

依赖版本以根目录与 `app/build.gradle.kts` 为准。Gradle 从配置的仓库下载依赖，本仓库不包含 Android SDK、本机依赖缓存或签名私钥。Gradle 启动脚本保留原有版权及 Apache-2.0 声明。
