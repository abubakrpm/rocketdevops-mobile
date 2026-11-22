package com.rocketdevops.mobile.terminal

import com.rocketdevops.mobile.data.content.DockerSeed
import java.security.MessageDigest

/**
 * The state of a pretend Docker engine: a fake registry (what `docker pull` can download),
 * local images and containers. No real containers, network or downloads exist.
 */
class DockerDaemon(seed: DockerSeed) {

    /** "nginx" or "nginx:1.27". The tag defaults to "latest", like real Docker. */
    data class ImageRef(val repository: String, val tag: String) {
        override fun toString() = "$repository:$tag"

        companion object {
            fun parse(text: String): ImageRef {
                val name = text.removePrefix("docker.io/").removePrefix("library/")
                val colon = name.lastIndexOf(':')
                return if (colon > 0) ImageRef(name.substring(0, colon), name.substring(colon + 1)) else ImageRef(name, "latest")
            }
        }
    }

    data class PortMapping(val host: Int, val container: Int) {
        override fun toString() = "0.0.0.0:$host->$container/tcp"
    }

    class Container(
        val id: String,          // 64 hex characters; `docker ps` shows the first 12
        val name: String,
        val image: ImageRef,
        var running: Boolean,
        val ports: List<PortMapping>,
        val command: String,
        val autoRemove: Boolean = false,
    ) {
        val shortId: String get() = id.take(12)
    }

    /** Repository names the fake registry knows (any tag of them can be pulled). */
    val registry: MutableSet<String> = seed.registryImages.map { ImageRef.parse(it).repository }.toMutableSet()
    val images: MutableList<ImageRef> = mutableListOf()
    val containers: MutableList<Container> = mutableListOf()
    private var counter = 0

    init {
        seed.localImages.map(ImageRef::parse).forEach(::addImage)
        for (c in seed.containers) {
            val image = ImageRef.parse(c.image)
            addImage(image)
            containers += Container(
                id = nextId(c.name),
                name = c.name,
                image = image,
                running = c.running,
                ports = c.ports.mapNotNull(::parsePort),
                command = ImageInfo.of(image.repository).command,
            )
        }
    }

    // ---------------------------------------------------------------- queries

    fun hasImage(ref: ImageRef): Boolean = ref in images
    fun hasRepository(repository: String): Boolean = images.any { it.repository == repository }
    fun canPull(ref: ImageRef): Boolean = ref.repository in registry

    /** Finds a container by name, full id, or the start of its id (as Docker allows). */
    fun find(nameOrId: String): Container? =
        containers.firstOrNull { it.name == nameOrId }
            ?: containers.firstOrNull { nameOrId.length >= 3 && it.id.startsWith(nameOrId) }

    fun runningContainerOnPort(hostPort: Int): Container? =
        containers.firstOrNull { c -> c.running && c.ports.any { it.host == hostPort } }

    // ---------------------------------------------------------------- changes

    fun addImage(ref: ImageRef) {
        if (ref !in images) images += ref
    }

    fun nextId(seedText: String): String {
        counter++
        return sha256("$seedText|$counter")
    }

    /** Docker-style random names, but deterministic: "eager_turing", "brave_hopper", ... */
    fun generateName(): String {
        val adjectives = listOf("eager", "brave", "calm", "quirky", "happy", "sharp", "jolly", "keen")
        val scientists = listOf("turing", "hopper", "lovelace", "torvalds", "ritchie", "knuth", "curie", "khwarizmi")
        var n = containers.size
        while (true) {
            val name = "${adjectives[n % adjectives.size]}_${scientists[(n / adjectives.size) % scientists.size]}"
            if (find(name) == null) return name
            n++
        }
    }

    companion object {
        /** "8080:80" -> PortMapping(8080, 80), or null if the text is not in that form. */
        fun parsePort(text: String): PortMapping? {
            val parts = text.removePrefix("0.0.0.0:").split(':')
            if (parts.size != 2) return null
            val host = parts[0].toIntOrNull() ?: return null
            val container = parts[1].removeSuffix("/tcp").toIntOrNull() ?: return null
            return if (host in 1..65535 && container in 1..65535) PortMapping(host, container) else null
        }

        fun sha256(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

/**
 * Pretend facts about well-known images, so output looks realistic.
 * Unknown images get sensible defaults.
 */
data class ImageInfo(
    val size: String,
    val command: String,
    val exposedPort: Int?,
    val longRunning: Boolean, // false: the container exits right after starting (like alpine)
    val logs: List<String>,
) {
    companion object {
        private val known = mapOf(
            "nginx" to ImageInfo(
                size = "192MB",
                command = "/docker-entrypoint.sh nginx -g 'daemon off;'",
                exposedPort = 80,
                longRunning = true,
                logs = listOf(
                    "/docker-entrypoint.sh: Configuration complete; ready for start up",
                    "2026/01/01 12:00:00 [notice] 1#1: nginx/1.27.3",
                    "2026/01/01 12:00:00 [notice] 1#1: start worker processes",
                ),
            ),
            "redis" to ImageInfo(
                size = "117MB",
                command = "docker-entrypoint.sh redis-server",
                exposedPort = 6379,
                longRunning = true,
                logs = listOf(
                    "1:M 01 Jan 2026 12:00:00.000 * Server initialized",
                    "1:M 01 Jan 2026 12:00:00.000 * Ready to accept connections tcp",
                ),
            ),
            "httpd" to ImageInfo("148MB", "httpd-foreground", 80, true, listOf("AH00094: Command line: 'httpd -D FOREGROUND'")),
            "postgres" to ImageInfo("438MB", "docker-entrypoint.sh postgres", 5432, true, listOf("LOG:  database system is ready to accept connections")),
            "alpine" to ImageInfo("7.8MB", "/bin/sh", null, false, emptyList()),
            "busybox" to ImageInfo("4.3MB", "sh", null, false, emptyList()),
            "ubuntu" to ImageInfo("78.1MB", "/bin/bash", null, false, emptyList()),
            "hello-world" to ImageInfo(
                size = "13.3kB",
                command = "/hello",
                exposedPort = null,
                longRunning = false,
                logs = listOf(
                    "",
                    "Hello from Docker!",
                    "This message shows that your installation appears to be working correctly.",
                ),
            ),
        )

        fun of(repository: String): ImageInfo =
            known[repository] ?: ImageInfo("120MB", "./start.sh", null, true, listOf("Application started"))
    }
}
