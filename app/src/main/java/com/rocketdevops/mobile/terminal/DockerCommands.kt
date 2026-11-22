package com.rocketdevops.mobile.terminal

import com.rocketdevops.mobile.terminal.DockerDaemon.Container
import com.rocketdevops.mobile.terminal.DockerDaemon.ImageRef

/**
 * The `docker` command, working only on the in-memory DockerDaemon.
 * Output and error messages follow the real Docker CLI so learners recognise them later.
 */
class DockerCommands(
    private val daemon: DockerDaemon,
    private val fs: VirtualFileSystem,
) : CommandHandler {

    override fun run(args: List<String>, io: Io) {
        val sub = args.firstOrNull() ?: return usage(io)
        val rest = args.drop(1)
        when (sub) {
            "pull" -> pull(rest, io)
            "images" -> images(io)
            "image" -> when (rest.firstOrNull()) {
                "ls", "list" -> images(io)
                "pull" -> pull(rest.drop(1), io)
                "rm" -> rmi(rest.drop(1), io)
                else -> io.fail("docker: 'image ${rest.firstOrNull().orEmpty()}' is not supported in the simulator")
            }
            "run" -> runContainer(rest, io)
            "ps" -> ps(rest, io)
            "container" -> when (rest.firstOrNull()) {
                "ls", "list" -> ps(rest.drop(1), io)
                "logs" -> logs(rest.drop(1), io)
                "stop" -> stop(rest.drop(1), io)
                "rm" -> rm(rest.drop(1), io)
                else -> io.fail("docker: 'container ${rest.firstOrNull().orEmpty()}' is not supported in the simulator")
            }
            "stop" -> stop(rest, io)
            "start" -> start(rest, io)
            "restart" -> { stop(rest, io); if (io.exitCode == 0) { io.out.clear(); start(rest, io) } }
            "rm" -> rm(rest, io)
            "rmi" -> rmi(rest, io)
            "logs" -> logs(rest, io)
            "exec" -> exec(rest, io)
            "build" -> build(rest, io)
            "version", "--version", "-v" -> io.out += "Docker version 27.4.0 (simulated)"
            "login", "push", "compose", "network", "volume" ->
                io.fail("docker $sub: not available in the offline simulator.")
            else -> io.fail("docker: '$sub' is not a docker command.\nSee 'docker --help'")
        }
    }

    private fun usage(io: Io) {
        io.out += "Usage:  docker [OPTIONS] COMMAND"
        io.out += ""
        io.out += "Commands in this simulator:"
        io.out += "  pull, images, run, ps, stop, start, restart, rm, rmi, logs, exec, build"
    }

    private fun noSuchContainer(name: String) = "Error response from daemon: No such container: $name"

    // ---------------------------------------------------------------- images

    private fun pull(args: List<String>, io: Io) {
        val text = args.firstOrNull { !it.startsWith("-") }
            ?: return io.fail("\"docker pull\" requires exactly 1 argument.")
        val ref = ImageRef.parse(text)
        if (!daemon.canPull(ref)) {
            return io.fail("Error response from daemon: pull access denied for ${ref.repository}, repository does not exist or may require 'docker login'")
        }
        if (!text.contains(':')) io.out += "Using default tag: latest"
        io.out += pullLines(ref)
    }

    /** Pull output; also used when `docker run` needs an image first. */
    private fun pullLines(ref: ImageRef): List<String> {
        val digest = DockerDaemon.sha256("digest|$ref")
        return if (daemon.hasImage(ref)) {
            listOf(
                "${ref.tag}: Pulling from library/${ref.repository}",
                "Digest: sha256:$digest",
                "Status: Image is up to date for $ref",
                "docker.io/library/$ref",
            )
        } else {
            daemon.addImage(ref)
            listOf(
                "${ref.tag}: Pulling from library/${ref.repository}",
                "${digest.take(12)}: Pull complete",
                "Digest: sha256:$digest",
                "Status: Downloaded newer image for $ref",
                "docker.io/library/$ref",
            )
        }
    }

    private fun images(io: Io) {
        val rows = listOf(listOf("REPOSITORY", "TAG", "IMAGE ID", "CREATED", "SIZE")) +
            daemon.images.map { ref ->
                listOf(ref.repository, ref.tag, DockerDaemon.sha256("image|$ref").take(12), "2 weeks ago", ImageInfo.of(ref.repository).size)
            }
        io.out += table(rows)
    }

    private fun rmi(args: List<String>, io: Io) {
        val names = args.filter { !it.startsWith("-") }
        if (names.isEmpty()) return io.fail("\"docker rmi\" requires at least 1 argument.")
        for (name in names) {
            val ref = ImageRef.parse(name)
            val user = daemon.containers.firstOrNull { it.image == ref }
            when {
                !daemon.hasImage(ref) -> io.fail("Error response from daemon: No such image: $ref")
                user != null -> io.fail(
                    "Error response from daemon: conflict: unable to remove repository reference \"$name\" " +
                        "(must force) - container ${user.shortId} is using its referenced image",
                )
                else -> {
                    daemon.images.remove(ref)
                    io.out += "Untagged: $ref"
                }
            }
        }
    }

    // ---------------------------------------------------------------- run

    private fun runContainer(args: List<String>, io: Io) {
        var detach = false
        var name: String? = null
        var autoRemove = false
        val ports = mutableListOf<DockerDaemon.PortMapping>()
        var i = 0

        // Options come before the image name.
        while (i < args.size && args[i].startsWith("-")) {
            val arg = args[i]
            fun value(): String = args.getOrNull(++i) ?: throw ShellError("flag needs an argument: '$arg' in -${arg.trimStart('-')}")
            when {
                arg == "-d" || arg == "--detach" -> detach = true
                arg == "--rm" -> autoRemove = true
                arg == "-i" || arg == "-t" || arg == "-it" || arg == "-ti" || arg == "--interactive" || arg == "--tty" -> Unit
                arg == "-dit" || arg == "-itd" -> detach = true
                arg == "--name" -> name = value()
                arg.startsWith("--name=") -> name = arg.substringAfter('=')
                arg == "-p" || arg == "--publish" -> ports += parsePortOrFail(value())
                arg.startsWith("--publish=") -> ports += parsePortOrFail(arg.substringAfter('='))
                arg.startsWith("-p") && arg.length > 2 -> ports += parsePortOrFail(arg.drop(2))
                arg == "-e" || arg == "--env" || arg == "-v" || arg == "--volume" -> value() // accepted, not simulated
                arg.startsWith("--env=") || arg.startsWith("--volume=") -> Unit
                else -> return io.fail("unknown flag: $arg\nSee 'docker run --help'.")
            }
            i++
        }
        val imageText = args.getOrNull(i) ?: return io.fail("\"docker run\" requires at least 1 argument.\nSee 'docker run --help'.")
        val commandArgs = args.drop(i + 1)
        val ref = ImageRef.parse(imageText)

        val containerName = name ?: daemon.generateName()
        daemon.find(containerName)?.takeIf { it.name == containerName }?.let { existing ->
            return io.fail(
                "docker: Error response from daemon: Conflict. The container name \"/$containerName\" is already in use by container " +
                    "\"${existing.id}\". You have to remove (or rename) that container to be able to reuse that name.",
            )
        }

        // Like real Docker, run pulls the image first if it is not on the machine.
        if (!daemon.hasImage(ref)) {
            if (!daemon.canPull(ref)) {
                io.err += "Unable to find image '$ref' locally"
                return io.fail("docker: Error response from daemon: pull access denied for ${ref.repository}, repository does not exist or may require 'docker login'")
            }
            // Real Docker prints this progress on stderr; we show it as normal output so it is not red.
            io.out += "Unable to find image '$ref' locally"
            io.out += pullLines(ref)
        }

        val info = ImageInfo.of(ref.repository)
        if (info.longRunning) {
            ports.firstOrNull { daemon.runningContainerOnPort(it.host) != null }?.let { taken ->
                return io.fail(
                    "docker: Error response from daemon: driver failed programming external connectivity on endpoint $containerName: " +
                        "Bind for 0.0.0.0:${taken.host} failed: port is already allocated.",
                )
            }
        }

        val container = Container(
            id = daemon.nextId(containerName),
            name = containerName,
            image = ref,
            running = info.longRunning,
            ports = ports,
            command = if (commandArgs.isEmpty()) info.command else commandArgs.joinToString(" "),
            autoRemove = autoRemove,
        )
        daemon.containers += container

        // Short-lived images (alpine, hello-world): run the command and exit.
        if (!info.longRunning) {
            when {
                commandArgs.firstOrNull() == "echo" -> io.out += commandArgs.drop(1).joinToString(" ")
                commandArgs.isEmpty() && info.logs.isNotEmpty() -> io.out += info.logs
                commandArgs.isEmpty() || commandArgs.first() in SHELLS ->
                    io.out += "(simulated) The container started a shell and exited, because interactive shells are not available here."
                else -> io.out += "(simulated) Ran \"${commandArgs.joinToString(" ")}\" in the container. It has exited."
            }
            if (autoRemove) daemon.containers.remove(container)
            return
        }

        if (detach) {
            io.out += container.id
        } else {
            io.out += info.logs
            io.out += "(simulated) The container keeps running in the background. Tip: add -d to start it detached."
        }
    }

    private fun parsePortOrFail(text: String) =
        DockerDaemon.parsePort(text) ?: throw ShellError("docker: invalid publish opts format (should be name=value but no '=' found): \"$text\". Use host:container, e.g. 8080:80")

    // ---------------------------------------------------------------- containers

    private fun ps(args: List<String>, io: Io) {
        val all = "-a" in args || "--all" in args
        val quiet = "-q" in args || "--quiet" in args || "-aq" in args || "-qa" in args
        val showAll = all || "-aq" in args || "-qa" in args
        val shown = daemon.containers.filter { showAll || it.running }.reversed() // newest first
        if (quiet) {
            io.out += shown.map { it.shortId }
            return
        }
        val rows = listOf(listOf("CONTAINER ID", "IMAGE", "COMMAND", "CREATED", "STATUS", "PORTS", "NAMES")) +
            shown.map { c ->
                listOf(
                    c.shortId,
                    if (c.image.tag == "latest") c.image.repository else c.image.toString(),
                    "\"${c.command.take(20)}${if (c.command.length > 20) "…" else ""}\"",
                    "2 minutes ago",
                    if (c.running) "Up 2 minutes" else "Exited (0) 1 minute ago",
                    c.ports.joinToString(", ").ifEmpty {
                        ImageInfo.of(c.image.repository).exposedPort?.takeIf { c.running }?.let { "$it/tcp" }.orEmpty()
                    },
                    c.name,
                )
            }
        io.out += table(rows)
    }

    private fun requireContainers(args: List<String>, command: String, io: Io): List<String>? {
        val names = args.filter { !it.startsWith("-") }
        if (names.isEmpty()) {
            io.fail("\"docker $command\" requires at least 1 argument.\nSee 'docker $command --help'.")
            return null
        }
        return names
    }

    private fun stop(args: List<String>, io: Io) {
        val names = requireContainers(args, "stop", io) ?: return
        for (name in names) {
            val c = daemon.find(name)
            if (c == null) {
                io.fail(noSuchContainer(name))
                continue
            }
            c.running = false
            if (c.autoRemove) daemon.containers.remove(c)
            io.out += name
        }
    }

    private fun start(args: List<String>, io: Io) {
        val names = requireContainers(args, "start", io) ?: return
        for (name in names) {
            val c = daemon.find(name)
            if (c == null) {
                io.fail(noSuchContainer(name))
                continue
            }
            val taken = c.ports.firstOrNull { p -> daemon.runningContainerOnPort(p.host)?.let { it !== c } == true }
            if (taken != null) {
                io.fail("Error response from daemon: driver failed programming external connectivity: Bind for 0.0.0.0:${taken.host} failed: port is already allocated")
                continue
            }
            c.running = ImageInfo.of(c.image.repository).longRunning
            io.out += name
        }
    }

    private fun rm(args: List<String>, io: Io) {
        val force = "-f" in args || "--force" in args
        val names = requireContainers(args, "rm", io) ?: return
        for (name in names) {
            val c = daemon.find(name)
            if (c == null) {
                io.fail(noSuchContainer(name))
                continue
            }
            if (c.running && !force) {
                io.fail("Error response from daemon: cannot remove container \"/${c.name}\": container is running: stop the container before removing or force remove")
                continue
            }
            daemon.containers.remove(c)
            io.out += name
        }
    }

    private fun logs(args: List<String>, io: Io) {
        val name = args.firstOrNull { !it.startsWith("-") }
            ?: return io.fail("\"docker logs\" requires exactly 1 argument.\nSee 'docker logs --help'.")
        val c = daemon.find(name) ?: return io.fail(noSuchContainer(name))
        io.out += ImageInfo.of(c.image.repository).logs
        if ("-f" in args || "--follow" in args) {
            io.out += "(simulated) Following stops here: the simulator shows the logs once."
        }
    }

    private fun exec(args: List<String>, io: Io) {
        val positional = args.filter { !it.startsWith("-") }
        if (positional.size < 2) {
            return io.fail("\"docker exec\" requires at least 2 arguments.\nSee 'docker exec --help'.")
        }
        val name = positional[0]
        val command = positional.drop(1)
        val c = daemon.find(name) ?: return io.fail(noSuchContainer(name))
        if (!c.running) return io.fail("Error response from daemon: container ${c.id} is not running")

        when (command.first()) {
            in SHELLS -> {
                io.out += "(simulated) You opened a shell inside \"${c.name}\" as root."
                io.out += "Interactive shells are not available here, so it closed again."
                io.out += "Run one command instead, for example: docker exec ${c.name} ls /"
            }
            "ls" -> io.out += listOf("bin", "boot", "dev", "etc", "home", "lib", "proc", "root", "run", "srv", "tmp", "usr", "var")
            "hostname" -> io.out += c.shortId
            "whoami" -> io.out += "root"
            "echo" -> io.out += command.drop(1).joinToString(" ")
            "pwd" -> io.out += "/"
            "nginx" -> if (c.image.repository == "nginx") io.out += "nginx version: nginx/1.27.3" else execNotFound(command.first(), io)
            "cat" -> if (command.getOrNull(1) == "/etc/os-release") {
                io.out += "PRETTY_NAME=\"Debian GNU/Linux 12 (bookworm)\""
            } else {
                io.fail("cat: ${command.getOrNull(1) ?: ""}: No such file or directory")
            }
            else -> execNotFound(command.first(), io)
        }
    }

    private fun execNotFound(program: String, io: Io) {
        io.fail("OCI runtime exec failed: exec failed: unable to start container process: exec: \"$program\": executable file not found in \$PATH: unknown")
    }

    // ---------------------------------------------------------------- build

    /** docker build -t myapp . : reads the Dockerfile from the fake filesystem. */
    private fun build(args: List<String>, io: Io) {
        var tag: String? = null
        var context: String? = null
        var i = 0
        while (i < args.size) {
            when (val arg = args[i]) {
                "-t", "--tag" -> tag = args.getOrNull(++i)
                else -> if (!arg.startsWith("-")) context = arg
            }
            i++
        }
        val contextTyped = context ?: return io.fail("ERROR: \"docker build\" requires exactly 1 argument.")
        val dockerfilePath = fs.resolve("$contextTyped/Dockerfile")
        val dockerfile = fs.file(dockerfilePath)
            ?: return io.fail("ERROR: failed to solve: failed to read dockerfile: open Dockerfile: no such file or directory")

        val instructions = dockerfile.content.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        val first = instructions.firstOrNull()
            ?: return io.fail("ERROR: failed to solve: the Dockerfile cannot be empty")
        if (!first.uppercase().startsWith("FROM ")) {
            return io.fail("ERROR: failed to solve: no build stage in current context (the first instruction must be FROM)")
        }
        val known = setOf("FROM", "RUN", "COPY", "ADD", "WORKDIR", "EXPOSE", "ENV", "CMD", "ENTRYPOINT", "USER", "ARG", "LABEL")
        instructions.forEach { line ->
            val keyword = line.substringBefore(' ').uppercase()
            if (keyword !in known) return io.fail("ERROR: failed to solve: dockerfile parse error: unknown instruction: $keyword")
        }

        val base = ImageRef.parse(first.substringAfter(' ').trim().substringBefore(' '))
        if (!daemon.hasImage(base) && !daemon.canPull(base)) {
            return io.fail("ERROR: failed to solve: ${base}: pull access denied, repository does not exist or may require authorization")
        }
        daemon.addImage(base)

        instructions.forEachIndexed { index, line ->
            io.out += "Step ${index + 1}/${instructions.size} : $line"
            io.out += " ---> ${DockerDaemon.sha256("step|$line|$index").take(12)}"
        }
        val imageId = DockerDaemon.sha256("build|${dockerfile.content}").take(12)
        io.out += "Successfully built $imageId"
        if (tag != null) {
            val ref = ImageRef.parse(tag)
            daemon.addImage(ref)
            io.out += "Successfully tagged $ref"
        }
    }

    companion object {
        private val SHELLS = setOf("sh", "bash", "/bin/sh", "/bin/bash")

        /** Formats rows into left-aligned columns separated by three spaces, like the Docker CLI. */
        fun table(rows: List<List<String>>): List<String> {
            val widths = rows.first().indices.map { col -> rows.maxOf { it[col].length } }
            return rows.map { row ->
                row.mapIndexed { col, cell -> if (col == row.lastIndex) cell else cell.padEnd(widths[col]) }
                    .joinToString("   ")
                    .trimEnd()
            }
        }
    }
}
