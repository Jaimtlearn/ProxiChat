# How to Install ProxiChat on iPhone (Using Windows PC Only)

No Mac needed. This guide uses free online services to build the app and a free Windows tool to install it.

**Total time:** ~30 minutes (mostly waiting for downloads)

**What you need:**
- Your Windows laptop
- Your iPhone + a USB Lightning/USB-C cable
- An internet connection

---

## PART 1: Build the App (Using GitHub — Free)

GitHub Actions will build the app on Apple's servers for free.

### Step 1: Create a GitHub account

1. Open your browser and go to **github.com**
2. Click **"Sign up"**
3. Enter your email, create a password, choose a username
4. Complete the verification and click **"Create account"**
5. Check your email and verify your account

### Step 2: Create a new repository

1. After logging in, click the **"+"** button in the top-right corner
2. Click **"New repository"**
3. Settings:
   - Repository name: **`ProxiChat`**
   - Keep it **Public** (free GitHub accounts need public repos for Actions)
   - Do NOT check "Add a README file"
4. Click **"Create repository"**

### Step 3: Upload the project files

You'll see a page that says "Quick setup". Do this:

1. Click **"uploading an existing file"** (it's a blue link on that page)
2. Now open **File Explorer** on your Windows laptop
3. Navigate to the project folder: `Bluetooth_based_chatting`
4. You need to upload these folders/files — do them in batches:

**Batch 1: Upload the `ios` folder**
- Open the `ios` folder
- Select EVERYTHING inside it (Ctrl+A)
- Drag and drop it into the GitHub upload area in your browser
- **IMPORTANT:** GitHub's drag-and-drop puts files flat. Instead, do this:
  
  **Better method — use the command line (see Step 3 Alternative below)**

### Step 3 (Alternative — Easier): Use Git from command line

This is actually easier than dragging files. Open **Command Prompt** on Windows:

1. Press `Windows key`, type **"cmd"**, press Enter
2. Type these commands one by one (press Enter after each):

```
cd path\to\Bluetooth_based_chatting
```
(Replace `path\to\` with the actual path, for example: `cd C:\Users\jaimit\Bluetooth_based_chatting`)

Now install Git if you don't have it:

3. Go to **git-scm.com** in your browser
4. Download and install Git for Windows (keep all default settings, just click Next)
5. **Close and reopen Command Prompt** after installing Git

Now push the code:

```
git init
git add .github/workflows/build-ios.yml
git add ios/
git commit -m "Add ProxiChat iOS app"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/ProxiChat.git
git push -u origin main
```

**Replace `YOUR_USERNAME` with your actual GitHub username.**

When asked for credentials:
- Username: your GitHub username
- Password: you need a **Personal Access Token** (GitHub no longer accepts passwords):
  1. Go to **github.com → Settings → Developer settings → Personal access tokens → Tokens (classic)**
  2. Click **"Generate new token (classic)"**
  3. Give it a name like "push"
  4. Check the **"repo"** checkbox
  5. Click **"Generate token"**
  6. **Copy the token** (looks like `ghp_xxxxxxxxxxxx`)
  7. Paste it as your password in Command Prompt (it won't show characters — that's normal, just paste and press Enter)

### Step 4: Wait for the build

1. Go to your repository on GitHub: `github.com/YOUR_USERNAME/ProxiChat`
2. Click the **"Actions"** tab at the top
3. You should see a workflow running called **"Build iOS App"**
4. Click on it to see progress
5. Wait for it to finish (takes about 5-10 minutes)
6. You'll see a green checkmark ✓ when it's done

**If you see a red X (failed):**
- Click on the failed job to see the error
- Most common fix: the Xcode version might differ. Edit `.github/workflows/build-ios.yml` and change `Xcode_15.4.app` to `Xcode_15.2.app` or `Xcode_16.0.app`

### Step 5: Download the IPA file

1. On the completed workflow page, scroll down to **"Artifacts"**
2. You'll see **"ProxiChat-iOS"**
3. Click on it to download a zip file
4. Extract the zip — inside you'll find **`ProxiChat.ipa`**
5. Save this file somewhere easy to find (like your Desktop)

---

## PART 2: Install on iPhone (Using Sideloadly — Free)

### Step 6: Install iTunes on Windows

Sideloadly needs iTunes to communicate with your iPhone.

1. Go to **apple.com/itunes/**
2. Download iTunes for Windows
3. Install it (you don't need to open it, just install)

### Step 7: Install Sideloadly

1. Go to **sideloadly.io** in your browser
2. Click **"Download for Windows"**
3. Install Sideloadly (follow the prompts)
4. Open Sideloadly

### Step 8: Connect your iPhone

1. Plug your iPhone into your Windows laptop with a USB cable
2. On your iPhone, if it asks **"Trust This Computer?"** — tap **Trust** and enter your passcode
3. In Sideloadly, your iPhone should appear in the device dropdown at the top

### Step 9: Install the app

1. In Sideloadly, click the big IPA icon area (or drag your `ProxiChat.ipa` file into it)
2. Browse and select the **`ProxiChat.ipa`** file you downloaded
3. Enter your **Apple ID** email in the Apple ID field
4. Click **"Start"**
5. Enter your Apple ID **password** when prompted
6. If you have **two-factor authentication**: enter the 6-digit code from your iPhone
7. Wait for it to finish (takes about 1-2 minutes)
8. You'll see "Done" when it's installed

### Step 10: Trust the app on your iPhone

The app is installed but won't open yet. You need to trust it:

1. On your iPhone, go to:
   **Settings → General → VPN & Device Management**
2. Under "Developer App", tap on your **Apple ID email**
3. Tap **"Trust [your email]"**
4. Tap **"Trust"** again to confirm

### Step 11: Open ProxiChat!

1. Go to your Home Screen
2. Find the **ProxiChat** app and tap it
3. Go through the onboarding:
   - Tap "Get Started"
   - Tap "Continue" (permissions will be requested when you start scanning)
   - Enter your display name
   - Tap "Start Chatting"
4. When asked for **Bluetooth** permission → tap **"Allow"**

**You're done! The app is installed and running!**

---

## Important Notes

### App Expiration
- With a **free Apple ID**, the app expires after **7 days**
- After 7 days, the app will stop opening
- To fix: just repeat **Steps 8-10** (takes 2 minutes — you already have everything installed)
- Your messages and settings are saved, only the certificate expires

### To Chat With Someone
- You need **another phone** with ProxiChat installed (iPhone or Android)
- Both phones need to be within **Bluetooth range** (~10-50 meters)
- Both phones will automatically discover each other
- Tap on the other device → it connects → start chatting!

---

## Quick Summary

| Step | What | Time |
|------|------|------|
| 1-3 | Upload code to GitHub | 10 min |
| 4 | Wait for build | 5-10 min |
| 5 | Download the IPA file | 1 min |
| 6-7 | Install iTunes + Sideloadly | 5 min |
| 8-10 | Install app on iPhone | 2 min |
| 11 | Open and use! | Done! |

**Total: ~25-30 minutes**

---

## Troubleshooting

**Sideloadly shows "provision.cpp:81" error:**
- Your Apple ID might have two-factor auth issues
- Try: create a fresh Apple ID just for this (appleid.apple.com)

**Sideloadly shows "Installation Error":**
- Make sure iTunes is installed (Step 6)
- Unplug and replug your iPhone
- Restart Sideloadly

**GitHub Actions build fails:**
- Click on the failed job, read the red error text
- Usually it's an Xcode version issue — edit the workflow file and try a different version

**App crashes on open:**
- Make sure you completed Step 10 (Trust the developer)
- If still crashing, try reinstalling (repeat Steps 8-10)

**iPhone not detected by Sideloadly:**
- Make sure iTunes is installed
- Try a different USB cable
- On iPhone: Settings → General → Transfer or Reset → Reset Location & Privacy, then replug and tap Trust
