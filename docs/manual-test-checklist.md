# Manual test checklist

Run this on a real phone before a demo or a release. Tick each box. It takes about 20 minutes.
Automated tests cover the logic (`./gradlew :app:testDebugUnitTest`) and four screens
(`./gradlew :app:connectedDebugAndroidTest`); this list covers what only a person can judge.

Phone: __________________  Android version: ______  Date: __________  Tester: __________

## 0. Fresh start
- [ ] Uninstall the app, install it again, open it: the Welcome screen shows the rocket.
- [ ] Type a name and press Continue: Home says "Hi, <first name>" and the avatar shows the initials.
- [ ] The phone is in airplane mode for the rest of this list (the app must work fully offline).

## 1. One full lab per track (spec)
- [ ] Linux: "Stop a runaway program". `kill 2210` is ignored, `kill -9 2210` finishes it. Success screen shows.
- [ ] Git: "Fix a merge conflict". `git merge new-title` shows CONFLICT, the fix finishes the lab.
- [ ] Docker: "Build your own image". The build prints steps, `docker run` finishes the lab.
- [ ] Each finished lab shows a green "Done" chip in the Labs tab.

## 2. One quiz per track (spec)
- [ ] Linux, Git and Docker: finish one quiz each. Right answers turn green, wrong ones red, the "Why" card appears.
- [ ] A typed answer with extra spaces (`git  log   --oneline`) still counts.
- [ ] "Retry wrong ones" asks only the questions that were wrong.

## 3. Progress survives closing the app (spec)
- [ ] Note the streak, Profile numbers and track percentages.
- [ ] Swipe the app away from Recents, open it again: every number is the same, no Welcome screen.
- [ ] Leave a quiz in the middle ("Leave quiz?"), close the app, reopen the quiz: it continues at the same question.

## 4. Rotation keeps state (spec)
- [ ] In a lab, type three commands and rotate: the terminal output and the green objectives stay.
- [ ] In a quiz, answer a question and rotate: the answer stays locked, the score is the same.
- [ ] In the cheat sheet, type a search and rotate: the text and results stay.

## 5. Light and dark (spec)
For each: Home, Track, Lesson, Quiz, Results, Lab, Cheat sheet, Profile, Settings.
- [ ] Light theme: all text readable, nothing invisible.
- [ ] Dark theme: all text readable, nothing invisible, the terminal stays dark.

## 6. Large text (spec: font scale 1.3)
- [ ] Phone Settings, Display, Font size: largest (about 1.3). In the app, Settings, Font size: Large.
- [ ] Home, Track, Quiz, Lab and Profile: nothing overlaps or is cut in the middle of a word.
- [ ] Every button can still be pressed. Put both settings back afterwards.

## 7. Settings
- [ ] Quiz timer on: a 30-second ring counts down; at 0 the question shows "Time's up". It pauses while the app is in the background.
- [ ] Reset progress, Cancel: nothing changes.
- [ ] Reset progress, Reset: "Progress reset", Home shows 0% and no streak.
- [ ] Reset with "Also forget the name": the app returns to the Welcome screen.
- [ ] The About card shows no personal name or link, and says the terminal is simulated.

## 8. Motion
- [ ] Opening a screen slides it in from the right; Back slides it out to the right. No text overlaps.
- [ ] Switching bottom tabs fades (no sliding). The bottom bar slides away on detail screens.
- [ ] Next quiz question slides in; finishing a lab makes the Profile numbers roll up.
- [ ] Phone Settings > Accessibility > Remove animations ON: everything changes instantly. Turn it off again.

## 9. TalkBack (accessibility)
- [ ] Turn TalkBack on. The bottom tabs are read as "Home, tab 1 of 4" and so on.
- [ ] Quiz options, the copy buttons in the cheat sheet and the lab quick keys all have names.

## Problems found

| # | Screen | What happened | Steps to repeat | Fixed? |
|---|---|---|---|---|
| 1 |  |  |  |  |
| 2 |  |  |  |  |
