package com.spmisha134.skillops.copy.conversion

internal data class SkillMarkdown(
    val fields: LinkedHashMap<String, String>,
    val body: String,
) {
    val name: String get() = fields["name"].orEmpty().unquote()
    val description: String get() = fields["description"].orEmpty().unquote()

    fun render(targetName: String): String {
        val updated = LinkedHashMap(fields)
        updated["name"] = targetName
        return buildString {
            appendLine("---")
            updated.forEach { (key, value) -> appendLine("$key: $value") }
            appendLine("---")
            if (body.isNotEmpty()) append(body)
        }
    }

    companion object {
        fun parse(content: String): SkillMarkdown? {
            val normalized = content.replace("\r\n", "\n")
            if (!normalized.startsWith("---\n")) return null
            val closing = normalized.indexOf("\n---", startIndex = 4)
            if (closing < 0) return null
            val fields = linkedMapOf<String, String>()
            normalized.substring(4, closing).lineSequence().forEach { line ->
                val separator = line.indexOf(':')
                if (separator > 0) fields[line.substring(0, separator).trim()] = line.substring(separator + 1).trim()
            }
            return SkillMarkdown(fields, normalized.substring(closing + 4).removePrefix("\n"))
        }
    }
}

private fun String.unquote(): String =
    takeIf { length >= 2 && ((first() == '"' && last() == '"') || (first() == '\'' && last() == '\'')) }
        ?.substring(1, length - 1)
        ?: this
