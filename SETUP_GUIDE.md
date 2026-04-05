# ProxiChat - Complete Setup Guide (Beginner Friendly)

This guide will walk you through every step to get ProxiChat running on your iPhone or Android phone. No prior experience needed.

---

## OPTION A: Install on iPhone (iOS 16+)

### What You Need
- A Mac computer (MacBook, iMac, or Mac Mini) — **required for iOS apps**
- Xcode installed on that Mac (free from the Mac App Store)
- A free Apple ID (the same one you use for iCloud/App Store)
- Your iPhone with a USB cable
- iOS 16 or later on your iPhone

### Step-by-Step

#### Step 1: Install Xcode (if you don't have it)
1. Open the **App Store** on your Mac
2. Search for **"Xcode"**
3. Click **Get** / **Install** (it's free, but ~12 GB so it takes a while)
4. Wait for it to finish installing
5. Open Xcode once and accept the license agreement

#### Step 2: Copy the project to your Mac
Copy the **entire `ios/` folder** from this project to your Mac. You can:
- Use a USB drive
- Email it to yourself as a zip
- Use Google Drive / iCloud
- Use AirDrop if you're near your Mac

The folder you need is:
```
ios/
├── ProxiChat.xcodeproj/
└── ProxiChat/
    ├── ProxiChatApp.swift
    ├── ContentView.swift
    ├── Models/
    ├── Bluetooth/
    ├── Data/
    ├── ViewModels/
    ├── Views/
    ├── Theme/
    └── Resources/
```

#### Step 3: Open the project in Xcode
1. On your Mac, find the `ios` folder you copied
2. **Double-click** on `ProxiChat.xcodeproj` — it will open in Xcode
3. Wait for Xcode to finish loading (you'll see activity in the top bar)

#### Step 4: Sign in with your Apple ID
1. In Xcode, go to the menu bar: **Xcode → Settings** (or press `Cmd + ,`)
2. Click the **Accounts** tab
3. Click the **+** button at the bottom left
4. Choose **Apple ID**
5. Sign in with your Apple ID and password

#### Step 5: Set up signing (tell Apple this is your app)
1. In the left sidebar of Xcode, click the **blue ProxiChat icon** at the very top (the project file)
2. In the middle panel, make sure **ProxiChat** is selected under **TARGETS**
3. Click the **Signing & Capabilities** tab
4. Check the box **"Automatically manage signing"** (if not already checked)
5. In the **Team** dropdown, select your Apple ID / name
6. If you see a **Bundle Identifier** error, change `com.proxichat.app` to something unique like `com.yourname.proxichat`

> If you see a red error about provisioning, that's normal for first-time setup. 
> Xcode will fix it once you connect your iPhone.

#### Step 6: Connect your iPhone
1. Plug your iPhone into your Mac using a **USB cable**
2. If your iPhone asks **"Trust This Computer?"** — tap **Trust** and enter your passcode
3. In Xcode's top toolbar, click where it says **"iPhone"** or **"Any iOS Device"**
4. Select **your iPhone** from the list

#### Step 7: Build and install
1. Press the **Play button** (▶) in the top-left of Xcode, OR press **Cmd + R**
2. Wait for it to compile (first time takes 1-2 minutes)
3. The app will install on your iPhone automatically

#### Step 8: Trust the developer certificate (first time only)
The first time you install, your iPhone will NOT open the app. You need to trust it:

1. On your iPhone, go to: **Settings → General → VPN & Device Management**
   (On older iOS: Settings → General → Profiles & Device Management)
2. You'll see your Apple ID email under **Developer App**
3. Tap on it
4. Tap **"Trust"**
5. Tap **"Trust"** again to confirm
6. Now go back to your home screen and tap the **ProxiChat** app — it will open!

#### Step 9: Grant permissions
When ProxiChat opens for the first time:
1. Complete the onboarding (3 screens)
2. When asked for **Bluetooth** permission → tap **Allow**
3. Set your display name
4. You're ready to chat!

### Important Notes for iPhone
- With a **free Apple ID**, the app expires after **7 days**. You'll need to reconnect to your Mac and press Play again to reinstall.
- With a **paid Apple Developer account** ($99/year), the app lasts 1 year.
- The app only works on a **real iPhone**, NOT the Xcode Simulator (Bluetooth doesn't work in simulators).

---

## OPTION B: Install on Android Phone

### What You Need
- A computer (Windows, Mac, or Linux)
- Android Studio installed (free)
- Your Android phone with a USB cable
- Android 8.0 (Oreo) or later on your phone

### Step-by-Step

#### Step 1: Install Android Studio (if you don't have it)
1. Go to https://developer.android.com/studio on your computer
2. Click **"Download Android Studio"**
3. Run the installer and follow the prompts (keep all default settings)
4. When it first opens, it will download additional components — let it finish (this takes 10-20 minutes)

#### Step 2: Enable Developer Options on your Android phone
This lets your computer install apps directly on your phone:

1. On your phone, go to **Settings → About Phone**
2. Find **"Build Number"** (might be under "Software Information")
3. **Tap "Build Number" 7 times** rapidly
4. You'll see a message: "You are now a developer!"
5. Go back to **Settings → System → Developer Options**
   (or Settings → Developer Options on some phones)
6. Turn on **"USB Debugging"**

#### Step 3: Open the project in Android Studio
1. Copy the **entire project folder** (`Bluetooth_based_chatting/`) to your computer
2. Open Android Studio
3. Click **"Open"** (not "New Project")
4. Navigate to the `Bluetooth_based_chatting` folder and select it
5. Click **OK**
6. Wait for Android Studio to sync and download dependencies (first time takes 5-10 minutes)
   - You'll see progress at the bottom bar
   - If it asks to update Gradle or SDK, click **OK/Update**

#### Step 4: Connect your Android phone
1. Plug your phone into your computer with a **USB cable**
2. Your phone will ask **"Allow USB debugging?"** — tap **Allow** (check "Always allow" too)
3. In Android Studio's toolbar at the top, you should see your phone name appear in the device dropdown

#### Step 5: Build and install
1. In Android Studio, click the **green Play button** (▶) in the toolbar, OR press **Shift + F10**
2. Make sure your phone is selected in the device dropdown
3. Wait for it to build (first time takes 3-5 minutes)
4. The app will install and open on your phone automatically!

#### Step 6: Grant permissions
When ProxiChat opens:
1. Go through the onboarding screens
2. Grant **Bluetooth** permissions → tap Allow
3. Grant **Location** permissions → tap Allow (needed for Bluetooth scanning on Android)
4. If asked for **Notification** permission → tap Allow
5. Set your display name
6. You're ready!

### Troubleshooting (Android)

**"No device found" in Android Studio?**
- Make sure USB Debugging is ON (Step 2)
- Try a different USB cable (some cables are charge-only)
- Try a different USB port on your computer
- On your phone, change USB mode to "File Transfer" or "MTP"

**Build fails with "SDK not found"?**
- Android Studio menu: **Tools → SDK Manager**
- Make sure **Android 14 (API 34)** is checked and installed
- Click Apply

**Gradle sync fails?**
- Click **File → Invalidate Caches → Invalidate and Restart**
- Wait for it to re-sync

---

## Testing the App

To test chatting, you need **two phones** nearby (within ~10-50 meters):

1. Install ProxiChat on **both** phones (can be any mix of iPhone + Android)
2. Open ProxiChat on both phones
3. Both phones will start scanning automatically
4. You should see the other phone appear in the **"Nearby"** list
5. Tap on the other device to **connect**
6. Once connected, tap again to open the **chat screen**
7. Start typing and sending messages!

### What if devices don't see each other?
- Make sure **Bluetooth is ON** on both phones
- Make sure **Location is ON** on Android phones
- Make sure both phones have **granted all permissions**
- Try closing and reopening the app
- Make sure the phones are within ~10 meters of each other
- Restart Bluetooth on both phones (toggle off then on)

---

## Quick Reference

| | iPhone | Android |
|---|---|---|
| **Tool needed** | Xcode (Mac only) | Android Studio (any computer) |
| **Tool cost** | Free | Free |
| **Min phone version** | iOS 16 | Android 8.0 |
| **Install method** | USB cable to Mac | USB cable to computer |
| **Internet needed?** | Only to download Xcode | Only to download Android Studio |
| **App needs internet?** | No — works 100% offline | No — works 100% offline |
| **Developer account** | Free Apple ID works (7-day limit) | Not needed |
