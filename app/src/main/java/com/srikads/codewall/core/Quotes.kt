package com.srikads.codewall.core

enum class QuoteKind { QUOTES, JOKES, COMMITS, MIXED }

/** Bundled offline text snippets. Nothing is fetched from the network. */
object Quotes {
    val quotes = listOf(
        "Simplicity is prerequisite for reliability. - Dijkstra",
        "Talk is cheap. Show me the code. - Torvalds",
        "Programs must be written for people to read. - Abelson",
        "Make it work, make it right, make it fast. - Beck",
        "First, solve the problem. Then, write the code. - Johnson",
        "The best error message is the one that never shows up. - Fuchs",
        "Premature optimization is the root of all evil. - Knuth",
        "Code is like humor. When you have to explain it, it's bad. - House",
        "Deleted code is debugged code. - Raskin",
        "Any fool can write code a computer understands. - Fowler",
        "Fix the cause, not the symptom. - McConnell",
        "Small steps, shipped daily.",
    )
    val jokes = listOf(
        "There are 10 kinds of people: those who get binary and those who don't.",
        "It works on my machine. Ship the machine.",
        "A SQL query walks into a bar and asks two tables: may I join you?",
        "!false - it's funny because it's true.",
        "I would tell you a UDP joke, but you might not get it.",
        "Debugging: being the detective in a crime movie where you're also the murderer.",
        "There's no place like 127.0.0.1",
        "Why do Java devs wear glasses? They can't C#.",
        "99 little bugs in the code. Patch one, 127 little bugs in the code.",
        "Weeks of coding can save hours of planning.",
    )
    val commits = listOf(
        "fix: it was DNS",
        "chore: remove console.log (again)",
        "feat: add coffee",
        "refactor: rename things until they make sense",
        "fix: off-by-one error in off-by-one fix",
        "wip: do not merge (merged)",
        "docs: explain the magic number",
        "perf: make it go brrr",
        "test: assert true == true",
        "revert: revert \"revert: the thing\"",
        "fix: typo in fix for typo",
        "style: tabs vs spaces ceasefire",
    )

    fun pool(kind: QuoteKind): List<String> = when (kind) {
        QuoteKind.QUOTES -> quotes
        QuoteKind.JOKES -> jokes
        QuoteKind.COMMITS -> commits
        QuoteKind.MIXED -> quotes + jokes + commits
    }

    /** Deterministic pick that changes every [rotateMinutes]. */
    fun pick(kind: QuoteKind, epochMillis: Long, rotateMinutes: Int): String {
        val list = pool(kind)
        val bucket = epochMillis / (60_000L * rotateMinutes.coerceAtLeast(1))
        // Spread consecutive buckets across the list instead of walking it in order.
        val idx = ((bucket * 2654435761L) ushr 8).mod(list.size.toLong()).toInt()
        return list[idx]
    }
}
