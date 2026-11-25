# Firebase setup (optional accounts)

Accounts let a learner sign out, reinstall or change phone and get all progress back by signing in
again. They use Firebase on the free **Spark** plan: no credit card, no cost. Without this setup the
app still builds and runs in guest-only mode (the sign-in buttons are hidden).

## What the free plan allows

| | Spark (free) limit | RocketDevOps use |
|---|---|---|
| Email / password sign-ins | free | one per sign-in |
| Firestore storage | 1 GiB | about 10 KB per learner |
| Firestore writes | 20,000 per day | a few dozen per active learner per day |
| Firestore reads | 50,000 per day | one per sign-in |

If a limit is ever reached, Firebase stops answering until the next day; it never charges on Spark.
Phone / SMS sign-in is **not** used because it is billed.

## 1. Create the project

1. Open <https://console.firebase.google.com> and sign in with a Google account.
2. **Create a project** (any name, for example `rocketdevops-mobile`). Google Analytics: **off**.
3. The project starts on the Spark plan. Do not upgrade.

## 2. Add the Android app

1. Project overview > **Add app** > Android.
2. Package name: `com.rocketdevops.mobile` (must match exactly). Nickname: optional.
   SHA-1: not needed for email sign-in.
3. **Download `google-services.json`** and put it in the `app/` folder
   (next to `app/build.gradle.kts`).
4. Skip the remaining console steps: the Gradle plugin and libraries are already in the project.

`google-services.json` is not a secret (it ships inside every APK); access is controlled by the
security rules below. It can be committed so teammates can build with accounts.

## 3. Turn on email sign-in

Build > **Authentication** > Get started > Sign-in method > **Email/Password** > Enable > Save.
(Leave "Email link" off.)

## 4. Create the database

1. Build > **Firestore Database** > Create database.
2. Location: one near the users (for Pakistan, `asia-south1` Mumbai). This cannot be changed later.
3. Start in **production mode**.
4. Open the **Rules** tab, replace everything with the contents of
   [`firestore.rules`](../firestore.rules) and press **Publish**.

The rules allow each signed-in learner to read and write only `users/{their uid}`.

## 5. Test

1. Sync Gradle and run the app. The Welcome screen now shows **Sign in**.
2. Create an account, finish a lesson, then Settings > **Sign out**.
3. Sign in again: the lesson is still finished. In the console, Firestore shows one document under
   `users` and Authentication shows the account.
4. Airplane mode: finish something, try Sign out. The app warns that it is not saved online yet.

## Removing a learner

Authentication > Users > delete the account, and Firestore > `users` > delete the document with the
same uid.
