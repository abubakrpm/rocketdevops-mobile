package com.rocketdevops.mobile.data.content

import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checks that the JSON format documented in ContentModels.kt really parses as described. */
class ContentParsingTest {

    @Test
    fun lessonBlocksPickTheirTypeFromTheTypeField() {
        val json = """
            [
              {"type": "heading", "text": "Title"},
              {"type": "paragraph", "text": "Words"},
              {"type": "code", "code": "ls -la", "caption": "List"},
              {"type": "tip", "text": "Careful"}
            ]
        """
        val blocks = ContentJson.decodeFromString<List<LessonBlock>>(json)
        assertEquals(
            listOf(
                LessonBlock.Heading("Title"),
                LessonBlock.Paragraph("Words"),
                LessonBlock.Code("ls -la", "List"),
                LessonBlock.Tip("Careful"),
            ),
            blocks,
        )
    }

    @Test
    fun objectiveChecksPickTheirTypeFromTheTypeField() {
        val json = """
            [
              {"type": "command", "pattern": "^pwd$"},
              {"type": "git-initialized"},
              {"type": "docker-container-running", "image": "nginx", "port": "8080:80"}
            ]
        """
        val checks = ContentJson.decodeFromString<List<ObjectiveCheck>>(json)
        assertEquals(ObjectiveCheck.CommandMatches("^pwd$"), checks[0])
        assertEquals(ObjectiveCheck.GitInitialized, checks[1])
        assertEquals(ObjectiveCheck.DockerContainerRunning("nginx", "8080:80"), checks[2])
    }

    @Test
    fun optionalFieldsGetDefaultsAndUnknownFieldsAreIgnored() {
        val json = """{"id": "q", "type": "TRUE_FALSE", "prompt": "P", "correctAnswers": ["True"],
                       "explanation": "E", "someFutureField": 42}"""
        val question = ContentJson.decodeFromString<QuizQuestion>(json)
        assertEquals(emptyList<String>(), question.options)
        assertEquals(listOf("True", "False"), question.displayOptions)
        assertEquals(false, question.isCode)
    }

    @Test
    fun loaderNamesTheBrokenFile() {
        val source = MapContentSource(
            mapOf(
                "index.json" to """{"tracks": ["tracks/bad.json"], "cheatSheet": "cheat.json"}""",
                "tracks/bad.json" to """{"id": "x", "title": "missing the rest"}""",
                "cheat.json" to "[]",
            ),
        )
        val result = ContentLoader(source).load()
        assertTrue(result is ContentLoadResult.Failure)
        val message = (result as ContentLoadResult.Failure).problems.single()
        assertTrue("message should name the file: $message", message.startsWith("tracks/bad.json"))
    }

    @Test
    fun loaderReportsAMissingFile() {
        val source = MapContentSource(mapOf("index.json" to """{"tracks": ["tracks/gone.json"], "cheatSheet": "c.json"}"""))
        val result = ContentLoader(source).load() as ContentLoadResult.Failure
        assertTrue(result.problems.single().contains("tracks/gone.json"))
    }
}
