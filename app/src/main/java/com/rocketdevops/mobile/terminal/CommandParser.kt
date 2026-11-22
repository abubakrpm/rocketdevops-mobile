package com.rocketdevops.mobile.terminal

/*
 * STEP 1 OF RUNNING A COMMAND: turn the typed text into a structure.
 *
 *   echo "hello world" > notes.txt && cat notes.txt | grep hello
 *
 * Tokenizer   -> [echo] [hello world] [>] [notes.txt] [&&] [cat] [notes.txt] [|] [grep] [hello]
 * CommandParser -> two steps joined by &&:
 *      1) pipeline: echo "hello world"            redirect: > notes.txt
 *      2) pipeline: cat notes.txt | grep hello    (runs only if step 1 succeeded)
 *
 * This is plain text processing. Nothing is executed on the phone.
 */

/** A piece of the command line. Quotes are already removed from words. */
sealed interface Token {
    data class Word(val text: String) : Token
    data object Pipe : Token            // |
    data object RedirectOut : Token     // >   (replace file)
    data object RedirectAppend : Token  // >>  (add to end of file)
    data object And : Token             // &&  (run next only if this one succeeded)
    data object Semicolon : Token       // ;   (run next anyway)
}

/** Thrown for input the shell cannot understand, e.g. an unclosed quote. */
class ParseException(message: String) : Exception(message)

object Tokenizer {

    fun tokenize(input: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val word = StringBuilder()
        var inWord = false // true once a word has started, so "" still produces an (empty) word
        var i = 0

        fun endWord() {
            if (inWord) {
                tokens += Token.Word(word.toString())
                word.clear()
                inWord = false
            }
        }

        while (i < input.length) {
            val c = input[i]
            when {
                // 'single quotes': everything inside is literal
                c == '\'' -> {
                    val end = input.indexOf('\'', startIndex = i + 1)
                    if (end < 0) throw ParseException("unexpected EOF while looking for matching `''")
                    word.append(input, i + 1, end)
                    inWord = true
                    i = end + 1
                    continue
                }
                // "double quotes": literal except \" and \\
                c == '"' -> {
                    inWord = true
                    i++
                    var closed = false
                    while (i < input.length) {
                        val d = input[i]
                        if (d == '"') {
                            closed = true
                            i++
                            break
                        }
                        if (d == '\\' && i + 1 < input.length && input[i + 1] in "\"\\") {
                            word.append(input[i + 1])
                            i += 2
                            continue
                        }
                        word.append(d)
                        i++
                    }
                    if (!closed) throw ParseException("unexpected EOF while looking for matching `\"'")
                    continue
                }
                // backslash escapes the next character: my\ file.txt
                c == '\\' -> {
                    if (i + 1 < input.length) {
                        word.append(input[i + 1])
                        inWord = true
                    }
                    i += 2
                    continue
                }
                c.isWhitespace() -> endWord()
                c == '|' -> {
                    endWord()
                    if (input.getOrNull(i + 1) == '|') throw ParseException("|| is not supported in the simulator")
                    tokens += Token.Pipe
                }
                c == '>' -> {
                    endWord()
                    if (input.getOrNull(i + 1) == '>') {
                        tokens += Token.RedirectAppend
                        i++
                    } else {
                        tokens += Token.RedirectOut
                    }
                }
                c == '&' -> {
                    endWord()
                    if (input.getOrNull(i + 1) != '&') {
                        throw ParseException("background jobs (&) are not supported in the simulator")
                    }
                    tokens += Token.And
                    i++
                }
                c == ';' -> {
                    endWord()
                    tokens += Token.Semicolon
                }
                else -> {
                    word.append(c)
                    inWord = true
                }
            }
            i++
        }
        endWord()
        return tokens
    }
}

/** One program with its arguments, e.g. ["grep", "-i", "error"]. */
data class SimpleCommand(val args: List<String>)

data class Redirect(val path: String, val append: Boolean)

/** Commands joined by |, with an optional > or >> at the end. */
data class Pipeline(val commands: List<SimpleCommand>, val redirect: Redirect?)

/** @param onlyIfPreviousSucceeded true when this step came after && */
data class Step(val pipeline: Pipeline, val onlyIfPreviousSucceeded: Boolean)

data class CommandLine(val steps: List<Step>)

object CommandParser {

    fun parse(input: String): CommandLine {
        val tokens = Tokenizer.tokenize(input)
        val steps = mutableListOf<Step>()
        var current = mutableListOf<Token>()
        var nextNeedsSuccess = false

        for (token in tokens) {
            when (token) {
                Token.And, Token.Semicolon -> {
                    if (current.isEmpty()) throw syntaxError(if (token == Token.And) "&&" else ";")
                    steps += Step(parsePipeline(current), nextNeedsSuccess)
                    current = mutableListOf()
                    nextNeedsSuccess = token == Token.And
                }
                else -> current += token
            }
        }
        if (current.isNotEmpty()) {
            steps += Step(parsePipeline(current), nextNeedsSuccess)
        } else if (nextNeedsSuccess) {
            throw ParseException("syntax error: a command is missing after &&")
        }
        return CommandLine(steps)
    }

    private fun parsePipeline(tokens: List<Token>): Pipeline {
        // Split on |
        val parts = mutableListOf<MutableList<Token>>(mutableListOf())
        for (token in tokens) {
            if (token == Token.Pipe) parts += mutableListOf<Token>() else parts.last() += token
        }
        if (parts.any { it.isEmpty() }) throw syntaxError("|")

        var redirect: Redirect? = null
        val commands = parts.mapIndexed { index, part ->
            val args = mutableListOf<String>()
            var i = 0
            while (i < part.size) {
                when (val token = part[i]) {
                    is Token.Word -> args += token.text
                    Token.RedirectOut, Token.RedirectAppend -> {
                        val target = part.getOrNull(i + 1) as? Token.Word ?: throw syntaxError("newline")
                        if (index != parts.lastIndex) {
                            throw ParseException("redirection is only supported at the end of a pipeline in the simulator")
                        }
                        redirect = Redirect(target.text, append = token == Token.RedirectAppend)
                        i++ // skip the file name
                    }
                    else -> Unit
                }
                i++
            }
            if (args.isEmpty()) throw syntaxError(if (redirect != null) "newline" else "|")
            SimpleCommand(args)
        }
        return Pipeline(commands, redirect)
    }

    private fun syntaxError(near: String) = ParseException("syntax error near unexpected token `$near'")
}
