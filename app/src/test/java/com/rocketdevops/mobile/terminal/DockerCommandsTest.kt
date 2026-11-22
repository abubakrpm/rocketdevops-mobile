package com.rocketdevops.mobile.terminal

import com.rocketdevops.mobile.data.content.ContainerSeed
import com.rocketdevops.mobile.data.content.DockerSeed
import com.rocketdevops.mobile.data.content.FsEntry
import com.rocketdevops.mobile.data.content.LabEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DockerCommandsTest {

    private fun docker(seed: DockerSeed = DockerSeed(registryImages = listOf("nginx", "redis", "alpine")), files: List<FsEntry> = emptyList()) =
        TerminalEngine(LabEnvironment(docker = seed, files = files))

    @Test
    fun dockerIsMissingWhenTheLabDoesNotUseIt() {
        TerminalEngine().fails("docker ps", "docker: command not found")
    }

    @Test
    fun pullAndListImages() {
        val t = docker()
        val pulled = t.ok("docker pull nginx")
        assertTrue(pulled.contains("Using default tag: latest"))
        assertTrue(pulled.contains("Status: Downloaded newer image for nginx:latest"))
        assertTrue(t.ok("docker pull nginx").contains("Image is up to date"))
        val images = t.ok("docker images").lines()
        assertTrue(images[0].startsWith("REPOSITORY"))
        assertTrue(images[1].startsWith("nginx") && images[1].contains("latest"))
        t.fails("docker pull doesnotexist", "pull access denied")
    }

    @Test
    fun runDetachedWithNameAndPort() {
        val t = docker()
        val id = t.ok("docker run -d --name web -p 8080:80 nginx")
        assertTrue(id.lines().last().matches(Regex("[0-9a-f]{64}")))
        assertTrue(id.contains("Unable to find image 'nginx:latest' locally")) // run pulls automatically

        val ps = t.ok("docker ps").lines()
        assertTrue(ps[0].startsWith("CONTAINER ID"))
        assertTrue(ps[1].contains("0.0.0.0:8080->80/tcp") && ps[1].endsWith("web"))
    }

    @Test
    fun conflictsLikeRealDocker() {
        val t = docker()
        t.ok("docker run -d --name web -p 8080:80 nginx")
        t.fails("docker run -d --name web nginx", "is already in use")
        t.fails("docker run -d --name web2 -p 8080:80 nginx", "port is already allocated")
        t.fails("docker run -d -p 80 nginx", "invalid publish opts format")
        t.fails("docker run -d --nme web nginx", "unknown flag: --nme")
        t.fails("docker run -d private/app", "pull access denied")
    }

    @Test
    fun stopStartRemove() {
        val t = docker()
        t.ok("docker run -d --name web nginx")
        t.fails("docker rm web", "container is running")
        assertEquals("web", t.ok("docker stop web"))
        assertEquals(1, t.ok("docker ps").lines().size)        // only the header
        assertTrue(t.ok("docker ps -a").contains("Exited (0)"))
        t.ok("docker start web")
        t.ok("docker rm -f web")
        assertTrue(t.docker!!.containers.isEmpty())
        t.fails("docker stop ghost", "No such container: ghost")
    }

    @Test
    fun containerCanBeFoundByIdPrefix() {
        val t = docker()
        val id = t.ok("docker run -d --name web nginx").lines().last()
        assertEquals(id.take(5), t.ok("docker stop ${id.take(5)}"))
    }

    @Test
    fun logsAndExecOnARunningContainer() {
        val t = docker(DockerSeed(localImages = listOf("nginx"), containers = listOf(ContainerSeed("web", "nginx", ports = listOf("8080:80")))))
        assertTrue(t.ok("docker logs web").contains("nginx/1.27.3"))
        assertTrue(t.ok("docker exec -it web sh").contains("opened a shell"))
        assertEquals("nginx version: nginx/1.27.3", t.ok("docker exec web nginx -v"))
        t.fails("docker exec web python", "executable file not found")
        t.ok("docker stop web")
        t.fails("docker exec web ls", "is not running")
    }

    @Test
    fun shortLivedImagesExitImmediately() {
        val t = docker()
        assertTrue(t.ok("docker run alpine echo hello").lines().last() == "hello")
        val alpine = t.docker!!.containers.single()
        assertFalse(alpine.running)
        t.ok("docker run --rm alpine echo bye")
        assertEquals(1, t.docker!!.containers.size) // --rm removed the second one
    }

    @Test
    fun rmiRefusesImagesInUse() {
        val t = docker()
        t.ok("docker run -d --name web nginx")
        t.fails("docker rmi nginx", "is using its referenced image")
        t.ok("docker rm -f web")
        assertEquals("Untagged: nginx:latest", t.ok("docker rmi nginx"))
    }

    @Test
    fun buildReadsTheDockerfile() {
        val dockerfile = "FROM nginx\nCOPY index.html /usr/share/nginx/html/\nEXPOSE 80"
        val t = docker(files = listOf(FsEntry("/home/student/site/Dockerfile", content = dockerfile)))
        t.ok("cd site")
        val out = t.ok("docker build -t mysite .")
        assertTrue(out.contains("Step 1/3 : FROM nginx"))
        assertTrue(out.contains("Successfully tagged mysite:latest"))
        assertTrue(t.docker!!.hasRepository("mysite"))
        t.ok("cd ..")
        t.fails("docker build -t x .", "no such file or directory")
    }

    @Test
    fun typosInSubcommands() {
        docker().fails("docker pss", "'pss' is not a docker command")
    }
}
