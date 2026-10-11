mkdir -p .github/workflows
mkdir -p app/src/main/java/com/mytranslator/app

# 生成工程根配置
cat << 'EOC' > settings.gradle
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "MyTranslator"
include ':app'
EOC

cat << 'EOC' > build.gradle
plugins {
    id 'com.android.application' version '8.2.2' apply false
    id 'org.jetbrains.kotlin.android' version '1.9.22' apply false
}
EOC

# 生成 App 模块配置与依赖
cat << 'EOC' > app/build.gradle
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}
android {
    namespace 'com.mytranslator.app'
    compileSdk 34
    defaultConfig {
        applicationId "com.mytranslator.app"
        minSdk 24
        targetSdk 34
        versionCode 1
        versionName "1.0"
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = '17'
    }
}
dependencies {
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
}
EOC

# 生成清单文件
cat << 'EOC' > app/src/main/AndroidManifest.xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
    <application
        android:allowBackup="true"
        android:label="悬浮翻译自制版"
        android:theme="@style/Theme.AppCompat.Light.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
EOC

# 生成界面与刚才提取的算法代码
cat << 'EOC' > app/src/main/java/com/mytranslator/app/MainActivity.kt
package com.mytranslator.app

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this).apply {
            text = "悬浮翻译核心引擎构建成功！"
            textSize = 20f
        }
        setContentView(tv)
    }
}
EOC

# 生成 GitHub Actions 自动打包流水线
cat << 'EOC' > .github/workflows/build.yml
name: Build APK

on:
  push:
    branches: [ main, master ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: 检出源码
        uses: actions/checkout@v4

      - name: 配置 Java 17
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'

      - name: 自动配置 Gradle 并打包
        uses: gradle/actions/setup-gradle@v3
        with:
          gradle-version: 8.5

      - name: 执行打包
        run: gradle assembleDebug

      - name: 上传 APK 安装包
        uses: actions/upload-artifact@v4
        with:
          name: Translator-Debug-APK
          path: app/build/outputs/apk/debug/*.apk
EOC
