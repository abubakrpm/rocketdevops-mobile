package com.rocketdevops.mobile.terminal

import com.rocketdevops.mobile.data.content.DockerSeed
import com.rocketdevops.mobile.data.content.FsEntry
import com.rocketdevops.mobile.data.content.LabEnvironment
import com.rocketdevops.mobile.data.content.ProcessSeed

/**
 * The free terminal ("Playground"): the same simulator the labs use, with no objectives.
 * The learner can try any supported Linux, Git or Docker command in any order.
 *
 * The starting machine has a small project to explore, so the first `ls` already shows
 * something interesting. Like every lab, NOTHING runs on the phone: it is all in memory.
 */
object Playground {

    const val HOST = "playground"

    private const val HOME = "/home/student"
    private const val PROJECT = "$HOME/projects/webapp"

    val environment = LabEnvironment(
        cwd = HOME,
        files = listOf(
            FsEntry(
                "$HOME/README.md",
                content = """
                    # Welcome to the playground
                    This is a pretend Linux machine. Break it, fix it, reset it.
                    Try: cd projects/webapp, git init, docker run -d -p 8080:80 nginx
                """.trimIndent(),
            ),
            FsEntry("$HOME/notes.txt", content = "buy milk\nlearn grep\nfinish the docker labs\n"),
            FsEntry("$HOME/downloads", directory = true),
            FsEntry(
                "$PROJECT/index.html",
                content = "<!DOCTYPE html>\n<html>\n  <body>\n    <h1>Hello from RocketDevOps</h1>\n  </body>\n</html>\n",
            ),
            FsEntry(
                "$PROJECT/app.py",
                content = "from flask import Flask\napp = Flask(__name__)\n\n@app.route('/')\ndef home():\n    return 'Hello, DevOps!'\n",
            ),
            FsEntry(
                "$PROJECT/Dockerfile",
                content = "FROM nginx\nCOPY index.html /usr/share/nginx/html/index.html\nEXPOSE 80\n",
            ),
            FsEntry(
                "$PROJECT/logs/app.log",
                content = listOf(
                    "2026-11-28 09:00:01 INFO  server started on port 8080",
                    "2026-11-28 09:02:13 INFO  GET / 200",
                    "2026-11-28 09:05:47 WARN  slow response: 1200 ms",
                    "2026-11-28 09:06:02 ERROR database connection refused",
                    "2026-11-28 09:06:05 INFO  retrying database connection",
                    "2026-11-28 09:06:09 ERROR database connection refused",
                    "2026-11-28 09:07:30 INFO  database connected",
                ).joinToString("\n", postfix = "\n"),
            ),
            FsEntry(
                "$PROJECT/scripts/hello.sh",
                content = "echo Hello from a shell script\nwhoami\npwd\n",
                mode = "644", // not executable yet: a good chmod exercise
            ),
        ),
        // The fake Docker Hub: every image the simulator knows realistic output for.
        docker = DockerSeed(
            registryImages = listOf("nginx", "redis", "httpd", "postgres", "alpine", "busybox", "ubuntu", "hello-world"),
        ),
        processes = listOf(
            ProcessSeed(pid = 812, command = "python3 app.py", cpu = "1.2", mem = "2.4"),
            ProcessSeed(pid = 977, command = "node server.js", cpu = "0.8", mem = "3.1", ignoresTerm = true),
            ProcessSeed(pid = 120, command = "/usr/sbin/sshd", user = "root"),
        ),
    )

    /** Printed when the playground opens or is reset. */
    val welcome: List<String> = listOf(
        "RocketDevOps playground: a free simulated terminal.",
        "No objectives. Type any Linux, Git or Docker command.",
        "Type `help` for the list, or tap a suggestion below.",
    )

    /**
     * Tap-to-fill ideas shown above the terminal; they are not run until Enter is pressed.
     * In this order they tell a small story (explore, then Git, then Docker) and every one
     * succeeds: PlaygroundControllerTest runs them all and checks there is no error.
     */
    val suggestions: List<String> = listOf(
        "ls -la",
        "cat README.md",
        "mkdir backup && cp notes.txt backup/",
        "cd projects/webapp",
        "grep ERROR logs/app.log",
        "chmod +x scripts/hello.sh",
        "sh scripts/hello.sh",
        "git init",
        "git add .",
        "git commit -m \"first version\"",
        "git log --oneline",
        "docker build -t mysite .",
        "docker run -d -p 8080:80 nginx",
        "docker ps",
        "ps aux",
    )

    fun newEngine(): TerminalEngine = TerminalEngine(environment, host = HOST)
}
