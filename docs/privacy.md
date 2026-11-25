# Privacy

**Guest mode (default):** nothing leaves the phone. Progress and settings are stored only in the
app's private storage and are deleted when the app is uninstalled.

**With an account (optional):**

- Firebase Authentication (Google) stores the email address and a password hash. The app never
  sees or stores the password.
- Cloud Firestore stores one document per learner: the display name, finished lessons and labs
  with their dates, quiz scores, daily activity counts and achievements.
- Nothing else is collected: no location, contacts, device identifiers, analytics or ads.
  Terminal commands typed in labs are never sent anywhere.
- Each learner can read and write only their own document (see `firestore.rules`).
- **Reset progress** while signed in empties the online copy too. To delete the account itself,
  contact the project maintainers, who remove it from the Firebase console.
