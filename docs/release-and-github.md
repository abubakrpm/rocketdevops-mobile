# Putting the project on GitHub and publishing the APK

Run the commands in the Android Studio **Terminal** tab, inside the project folder.
The poster QR code (`docs/qr/`) opens `https://github.com/abubakrpm/rocketdevops-mobile/releases/latest`,
so the repository must be called **rocketdevops-mobile** on the account **abubakrpm**.
If you use another account or name, generate the QR code again. The poster itself shows no account name.

## 1. Create the empty repository (on the abubakrpm account)

1. Sign in as **abubakrpm** on github.com: **+** (top right) > **New repository**.
2. Name: `rocketdevops-mobile`. Public. Do **not** add a README, .gitignore or licence (the project has them).
3. Create repository.
4. **Settings > Collaborators > Add people**: add the other two team members by their GitHub usernames.
   They accept the email invitation and can then push directly, with the same rights to the code.
   Each of them can pin the repository on their own profile (**Customize your pins**), and their
   commits count in their own contribution graph.

## 2. Push the code

One person does this first push (any of the three, after accepting the invitation).
Check Git is installed: `git --version`. If not, install Git for Windows (git-scm.com), then restart Android Studio.

```
git config --global user.name "Your Name"
git config --global user.email "you@example.com"
git init -b main
git add .
git commit -m "RocketDevOps mobile 1.0"
git remote add origin https://github.com/abubakrpm/rocketdevops-mobile.git
git push -u origin main
```

The first push opens a browser window to sign in to GitHub (with the account of the person pushing).
The others then run `git clone https://github.com/abubakrpm/rocketdevops-mobile.git` to get the code,
and `git pull` before starting work, `git push` after. `.gitignore` already keeps out build
folders, `local.properties`, APK files and keystores, so no personal paths or secrets are uploaded.

## 3. Build the release APK

```
.\gradlew.bat :app:assembleRelease
```

The file is `app\build\outputs\apk\release\app-release.apk`. It is optimised and shrunk with R8 and
signed with the debug key (fine for sharing from GitHub, not for the Play Store).

**Test the release build before publishing** (R8 can break things that work in debug):
1. Copy the APK to the phone (USB, or Google Drive) and open it. Allow "Install unknown apps" if asked.
2. Open the app: lessons, one lab, one quiz, the cheat sheet search and Profile must all work.

## 4. Publish the release

1. On the repository page: **Releases** (right side) > **Create a new release**.
2. Tag: `v1.0` (create new tag). Title: `RocketDevOps 1.0`.
3. Description, for example:
   > First release. 3 tracks (Linux, Git, Docker), 17 lessons, 50 quiz questions, 16 labs in a
   > simulated terminal and an 80-command cheat sheet. Works offline. Android 8.0 or newer.
   > Install: download the APK, open it and allow installing from this source.
4. Rename the APK to `RocketDevOps-1.0.apk` and drag it into the assets box.
5. **Publish release**.
6. Scan `docs/qr/download-qr-poster.png` with a phone: it must open this release page.

## Later updates

Change `versionCode` (+1) and `versionName` in `app/build.gradle.kts`, commit, push, build the APK
again and create a new release (`v1.1`). The QR code keeps working because it always opens the latest release.
