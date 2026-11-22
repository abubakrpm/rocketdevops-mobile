# Content format

All lessons, quizzes, labs and cheat-sheet entries live as JSON in
`app/src/main/assets/content/`. You can add content without changing any Kotlin code.

```
content/
  index.json          lists the track files (in display order) and the cheat sheet
  tracks/linux.json   one file per track
  tracks/git.json
  tracks/docker.json
  cheatsheet.json
```

After editing, run the unit tests (`./gradlew test`). `ContentFilesTest` loads the real files
and lists every problem it finds, such as a duplicate id or a wrong answer key.

## Rules

- Every `id` is lowercase letters, digits and dashes (`docker-lab-run`) and is unique across ALL files.
  Lab objective ids only need to be unique inside their lab.
- Text is in sentence case. Keep lessons short: they are read on a phone.

## Track → module

```json
{
  "id": "docker", "title": "Docker", "description": "…", "icon": "container",
  "modules": [ { "id": "docker-run", "title": "Running containers",
                 "lessons": [ … ], "quiz": { … }, "labs": [ … ] } ]
}
```

`icon` is `terminal`, `branch` or `container`. `lessons`, `quiz` and `labs` are each optional,
but a module needs at least one of them.

## Lesson

```json
{ "id": "docker-run-basics", "title": "Run your first container", "estimatedMinutes": 5,
  "body": [
    { "type": "heading",   "text": "Ports" },
    { "type": "paragraph", "text": "…" },
    { "type": "code",      "code": "docker ps", "caption": "optional" },
    { "type": "tip",       "text": "…" } ] }
```

## Quiz

```json
{ "id": "docker-run-quiz", "title": "Docker basics", "questions": [ … ] }
```

| `type` | `options` | `correctAnswers` | notes |
|---|---|---|---|
| `MULTIPLE_CHOICE` | 2 or more | the exact option text(s) | options are shuffled, so answers are text, not positions |
| `TRUE_FALSE` | leave out | `["True"]` or `["False"]` | |
| `TYPE_THE_COMMAND` | leave out | accepted commands | add `acceptedPatterns` (regular expressions) for answers with many valid forms |

Every question needs an `explanation` (shown in the "Why" card). Add `"isCode": true` to show the options in the mono font.

## Lab

```json
{ "id": "docker-lab-first-container", "title": "…", "scenario": "…",
  "startingState": {
    "cwd": "/home/student",
    "files":  [ { "path": "/home/student/app.sh", "content": "…", "mode": "644" },
                { "path": "/home/student/projects", "directory": true } ],
    "git":    { "currentBranch": "main", "branches": ["main"], "commits": ["Add README"] },
    "docker": { "registryImages": ["nginx"], "localImages": [],
                "containers": [ { "name": "web", "image": "nginx", "running": true, "ports": ["8080:80"] } ] }
  },
  "objectives": [ { "id": "port", "label": "Map port",
                    "check": { "type": "docker-container-running", "image": "nginx", "port": "8080:80" } } ],
  "hints": [ "gentle nudge", "bigger hint", "Type: the exact commands" ],
  "solution": [ "docker pull nginx", "docker run -d --name web -p 8080:80 nginx" ] }
```

`git` and `docker` are optional. Leave `git` out if the learner should run `git init` themselves.
Every lab needs **exactly 3 hints** and a **solution**: the commands that finish it.
Learners never see the solution; `LabSolutionsTest` runs it in the simulator to prove the lab can be completed.

### Objective checks

Prefer checks on the final state: then any correct command counts, not only the one you had in mind.

| `type` | fields | passes when |
|---|---|---|
| `command` | `pattern` | any command typed so far matches the regular expression |
| `cwd` | `path` | the shell is in that folder |
| `path-exists` | `path`, `directory` (optional) | the file or folder exists |
| `file-contains` | `path`, `text` | the file contains the text |
| `file-mode` | `path`, `mode` | the file has those permissions, e.g. `755` |
| `git-initialized` | none | a repository exists |
| `git-commit-count` | `atLeast` | there are at least that many commits |
| `git-branch-exists` | `name` | the branch exists |
| `git-current-branch` | `name` | that branch is checked out |
| `docker-image-present` | `image` | the image is on the fake machine |
| `docker-container-running` | `image`, `port` (optional) | a container from that image is running (with that port published) |
| `file-not-contains` | `path`, `text` | the file exists and does not contain the text |
| `git-merged` | `branch` | that branch is merged into the current one, the merge is finished, and no conflict markers were committed |
| `process-stopped` | `command` | no running process has that text in its command |

Docker images used in checks must be listed in `registryImages` or `localImages`, or be built by the
solution (`docker build -t myapp .`), otherwise the lab could never be finished (the validator reports this).

Only `command` checks look at what was typed, and only commands that succeeded count.
A lab must not be finished before the learner does anything (`LabSolutionsTest` checks this).

### Ready-made branches (merge and conflict labs)

`extraCommits` adds commits on top of the seeded history, per branch. Paths are relative to the
repository. The learner starts on `currentBranch` and sees its files.

```json
"git": { "currentBranch": "main", "branches": ["main", "new-title"], "commits": ["Add home page"],
         "extraCommits": [
           { "branch": "new-title", "message": "Friendlier title", "files": { "index.html": "<h1>Hello there</h1>" } },
           { "branch": "main",      "message": "Formal title",     "files": { "index.html": "<h1>Good day</h1>" } } ] }
```

A solution command may fail only with a merge conflict (lines starting with `CONFLICT`), because the
conflict lab is about exactly that.

### Processes (ps and kill labs)

```json
"processes": [
  { "pid": 1,    "command": "/sbin/init", "user": "root" },
  { "pid": 2210, "command": "python3 backup.py", "cpu": "98.7", "ignoresTerm": true } ]
```

The learner's own `bash` is always PID 1000 and `ps` is 1001, so do not use those numbers.
`root` processes cannot be stopped ("Operation not permitted"). `ignoresTerm: true` means a plain
`kill` is ignored and `kill -9` is needed. Supported: `ps`, `ps aux`, `kill [-9] PID`, `pgrep`, `pkill`.

## Cheat sheet entry

```json
{ "id": "cheat-docker-ps", "category": "docker", "command": "docker ps",
  "description": "List running containers", "example": "docker ps -a",
  "commonFlags": [ { "flag": "-a", "meaning": "include stopped containers" } ] }
```

`category` is `linux`, `git` or `docker`.
