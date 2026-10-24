package dev.intent.app.markdown

/** Small deterministic lexer; unsupported fence languages retain their exact plain text. */
data class CodeToken(val text: String, val kind: String = "plain")

private val codePattern = Regex(
    "\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|//[^\\n]*|/\\*[\\s\\S]*?\\*/|" +
        "\\b\\d+(?:\\.\\d+)?\\b|\\b[A-Za-z_][A-Za-z_0-9]*\\b"
)
private val keywords = setOf(
    "val", "var", "fun", "class", "interface", "object", "return", "if", "else", "when",
    "for", "while", "in", "import", "package", "true", "false", "null", "private", "public",
    "const", "let", "function", "async", "await", "def", "None", "True", "False", "from",
    "struct", "enum", "impl", "fn", "pub", "use", "mut", "match", "void", "new", "static"
)
private val languages = setOf(
    "kotlin", "kt", "java", "javascript", "js", "typescript", "ts", "json", "python", "py",
    "rust", "rs", "swift", "c", "cpp"
)

fun codeTokens(code: String, language: String): List<CodeToken> {
    if (language.lowercase() !in languages) return listOf(CodeToken(code))
    val tokens = mutableListOf<CodeToken>()
    var start = 0
    codePattern.findAll(code).forEach { match ->
        if (start <
            match.range.first
        ) {
            tokens.add(CodeToken(code.substring(start, match.range.first)))
        }
        val text = match.value
        val kind = when {
            text.startsWith('"') || text.startsWith('\'') -> "string"
            text.startsWith("//") || text.startsWith("/*") -> "comment"
            text.first().isDigit() -> "number"
            text in keywords -> "keyword"
            else -> "plain"
        }
        tokens.add(CodeToken(text, kind))
        start = match.range.last + 1
    }
    if (start < code.length) tokens.add(CodeToken(code.substring(start)))
    return tokens
}
