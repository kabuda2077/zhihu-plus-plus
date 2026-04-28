name: Manual-Lite-Only-Build-V2

on:
  workflow_dispatch:
    inputs:
      name:
        description: '构建名称'
        required: true
        default: 'manual-build'

permissions:
  contents: write

jobs:
  build:
    name: Build Lite Android App
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set current date
        run: echo "date_today=$(date +'%Y-%m-%d')" >> $GITHUB_ENV

      - name: Set repository name
        run: echo "repository_name=$(echo '${{ github.repository }}' | awk -F '/' '{print $2}')" >> $GITHUB_ENV

      - name: Set Up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: 'zulu'
          java-version: '17'
          cache: 'gradle'

      # 仍然保留 Rust 环境，因为某些编译插件可能需要
      - name: Setup rust
        uses: actions-rust-lang/setup-rust-toolchain@v1
        with:
          override: true

      - name: Install cargo-ndk
        run: cargo install cargo-ndk || true

      - name: Add Android Rust targets
        run: |
          rustup target add aarch64-linux-android
          rustup target add armv7-linux-androideabi
          rustup target add i686-linux-android
          rustup target add x86_64-linux-android

      - name: Change wrapper permissions
        run: chmod +x ./gradlew

      - name: Setup Android SDK
        uses: android-actions/setup-android@v3
      
      - name: Install Android NDK
        run: |
          echo "y" | sdkmanager --install "ndk;29.0.14206865"
          echo "ANDROID_NDK_HOME=$ANDROID_SDK_ROOT/ndk/29.0.14206865" >> $GITHUB_ENV

      # 核心编译步骤：只编译 LiteRelease
      - name: Build App
        run: |
          bash ./gradlew clean assembleLiteRelease -x ktlintCheck -x ktlintKotlinScriptCheck -x test
        env:
          CI_BUILD_MINIFY: true

      - name: Move files
        run: |
          mkdir -p app/build/upload
          # 移动原始生成的 Lite APK
          mv app/build/outputs/apk/lite/release/app-lite-release.apk app/build/upload/zhihu-lite.apk

      # --- 已删除 Recompress APK 步骤，避免签名报错 ---

      - name: Upload APK to Artifacts
        uses: actions/upload-artifact@v4
        with:
          name: ${{ env.date_today }} - Lite - ${{ inputs.name }}
          path: app/build/upload/zhihu-lite.apk

      - name: Create Release
        uses: softprops/action-gh-release@v2
        with:
          tag_name: "v-${{ env.date_today }}-${{ github.run_number }}"
          name: "知乎 Lite 自用版 (${{ env.date_today }})"
          body: |
            这是基于开源项目编译的知乎 Lite 纯净版。
            跳过了再压缩步骤以确保 Debug 签名兼容性。
          files: |
            app/build/upload/zhihu-lite.apk
          token: ${{ github.token }}
