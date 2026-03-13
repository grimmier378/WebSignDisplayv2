# WebSignDisplay v2

Android digital signage app for Amazon Firestick kiosk displays. Loads a configurable URL fullscreen in a WebView and keeps the display active and in the foreground.

---

## What the App Does

WebSignDisplay v2 is designed for resort and hospitality digital signage. It displays a single configurable web URL fullscreen on an Amazon Firestick-connected TV. Key behaviors:

- Runs fullscreen with the system UI hidden (kiosk mode)
- Automatically switches between cached and live content based on network availability
- Supports HTML5 fullscreen video
- Recovers from crashes automatically via a scheduled restart
- Optionally reloads the page on a configurable interval
- Optionally keeps itself in the foreground when the app is sent to the background by Fire TV system navigation (Aggressive Restart)

---

## Recommended Hardware

- Firestick 4K or Firestick 4K Max recommended
- Avoid Lite models for signage
- Disable TV sleep and screensaver features

---

## Quick Deploy (Most Common Install Method)

1. ## Download Precompiled APK

If you do not wish to build the project yourself, precompiled APKs are available in the releases folder.

2. Copy APK to USB drive
3. Install via OTG method
4. Launch app
5. Configure Sign URL
6. Enable Aggressive Restart

---

## Building the APK

### Option A: Linux (headless, no Android Studio)

1. Install OpenJDK 17:
   ```
   sudo apt-get install -y openjdk-17-jdk
   ```

2. Install Android command-line tools (`sdkmanager`) from https://developer.android.com/studio#command-line-tools-only and extract to a directory (e.g., `~/android-sdk`).

   > **Note:** `sdkmanager` is located at `cmdline-tools/latest/bin/sdkmanager` inside the extracted directory. Either add that `bin` folder to your PATH, or invoke `sdkmanager` using its full path (e.g., `~/android-sdk/cmdline-tools/latest/bin/sdkmanager`).

3. Accept SDK licenses:
   ```
   yes | sdkmanager --licenses
   ```

4. Set `sdk.dir` in `local.properties` at the project root to point to your SDK directory:
   ```
   sdk.dir=/home/youruser/android-sdk
   ```

5. Build the debug APK:
   ```
   ./gradlew assembleDebug
   ```

   Output: `app/build/outputs/apk/debug/app-debug.apk`
   I generally rename this to WebSignDisplay.apk
   and copy this some place easier to find like a thumb drive.

### Option B: Windows (Android Studio)

1. Open the project folder in Android Studio.
2. Go to **Build → Build Bundle(s)/APK(s) → Build APK(s)**.
3. The APK will be placed in `app/build/outputs/apk/debug/`.

---

## Installing on a Firestick via OTG Cable + USB Drive (No Computer Required)

This method allows you to install the app directly on the Firestick using a USB drive and remote control — no PC or ADB required.

> **Prerequisites**
> - OTG splitter cable compatible with Firestick  
> - USB flash drive (FAT32 or exFAT recommended)  
> - File manager app (recommended: X-Plorer File Manager)  
> - APK file copied to the USB drive  

Example OTG cables can be found here:  
https://www.amazon.com/fire-stick-usb-splitter/s?k=fire+stick+usb+splitter  

---

### 1. Connect the OTG Cable and USB Drive

1. Unplug power from the Firestick  
2. Connect the OTG splitter cable to the Firestick  
3. Plug the Firestick power cable into the OTG power port  
4. Insert the USB drive containing the APK into the OTG USB port  
5. Power the Firestick back on  

---

### 2. Enable Developer Options

- Go to **Settings → My Fire TV → About**
- Click on **Fire TV Stick** (device name) **7 times** until Developer Options unlock
- Go to **Settings → My Fire TV → Developer Options**
- Enable:
  - **ADB Debugging**
  - **Apps from Unknown Sources**

---

### 3. Install a File Manager

If not already installed:

1. Open the **Amazon Appstore**
2. Search for **X-Plorer File Manager**
3. Install and launch the app

---

### 4. Grant Install Permissions to X-Plorer

- Go to **Settings → My Fire TV → Developer Options**
- Select **Install Unknown Apps**
- Enable permission for **X-Plorer**

(This step is required or APK installs will fail.)

---

### 5. Install the APK from USB

1. Open **X-Plorer**
2. Navigate to the **USB storage** (usually shown as `USB Storage` or `External`)
3. Locate your APK file
4. Select it and choose **Install**
5. Confirm installation prompts

---

### 6. Launch the App

After installation:

- Go to **Your Apps & Channels**
- Find and launch the installed application

---

## Notes / Troubleshooting

- USB drive must be formatted **FAT32 or exFAT**
- Some Firestick models provide limited USB power — use quality OTG cables
- If the USB drive does not appear:
  - Restart Firestick
  - Re-seat OTG connections
- If install fails:
  - Verify **Install Unknown Apps** permission is enabled
  - Confirm APK is compatible with FireOS / Android version

---

## Installing on a Firestick via ADB Sideload

> **Prerequisite: ADB (Android Debug Bridge)**
> ADB is a command-line tool included in Android Platform-Tools. If you don't have it:
> 1. Download from: https://developer.android.com/tools/releases/platform-tools
> 2. Extract the zip to a convenient location (e.g., `C:\platform-tools` on Windows or `~/platform-tools` on Linux)
> 3. Either add the folder to your PATH, or run `adb` from the extracted folder

### 1. Enable Developer Options on the Firestick

- Go to **Settings → My Fire TV → About**
- Click on **Fire TV Stick** (the device name) seven times until Developer Options appears
- Go to **Settings → My Fire TV → Developer Options**
- Enable **ADB Debugging** and **Apps from Unknown Sources**

### 2. Find the Firestick IP Address

- Go to **Settings → My Fire TV → About → Network**

### 3. Connect via ADB from your computer

```
adb connect <firestick-ip>
```

Example:
```
adb connect 192.168.1.50
```

### 4. Install the APK

Replace `path/app.apk` with the full path and filename of your APK.

```
adb install path/app.apk 
```

To reinstall over an existing version:
```
adb install -r path/app.apk
```

---

## Configuring the App

Once installed, open the app on the Firestick. On first launch, the app will automatically open the Settings screen because no URL has been configured yet. To access Settings on subsequent launches, use any of the following:

- Press **F2** on a keyboard
- Press the **MENU** button on the Fire TV remote (the three-line button)
- Tap the **invisible button at the bottom of the screen** (useful for touchscreen deployments)

### Settings

| Setting | Description |
|---|---|
| **Sign URL** | The full URL to display (e.g., `https://yoursign.example.com`). The app loads this URL on startup and after each reload. |
| **Auto Start on Boot** | Launches the app automatically when the Firestick powers on. See Known Issues below. |
| **Aggressive Restart** | When enabled, the app relaunches itself if it is sent to the background by a Fire TV remote button press. This keeps the display active without manual intervention. |
| **Auto Reload Page** | Periodically reloads the sign URL on a configurable interval. Useful if the sign content updates remotely. |
| **Reload Interval (minutes)** | How often to reload the page when Auto Reload is enabled. Minimum 1 minute, default 10 minutes. |

---

## Known Issues

### Auto-Start on Boot is Non-Functional on Latest Fire OS

Amazon has restricted background launch capabilities in recent Fire OS releases. The Auto Start on Boot setting is currently non-functional on up-to-date Firestick devices and will not automatically launch the app after a power cycle.

**Workaround:** Launch the app manually once after each power cycle. Once running, the **Aggressive Restart** feature will keep the app in the foreground and prevent it from being backgrounded by remote button presses.

This issue is tracked for a future release pending a resolution from Amazon's platform.
