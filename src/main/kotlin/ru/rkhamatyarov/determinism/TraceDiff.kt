package ru.rkhamatyarov.determinism

/** Outcome of comparing two hash traces. */
sealed interface TraceComparison {
    /** Every shared step matched. [lengthDifference] is left minus right step count. */
    data class Agreement(
        val stepsCompared: Int,
        val lengthDifference: Int,
    ) : TraceComparison

    /** [step] is the lowest step whose hashes differ. */
    data class Divergence(
        val step: Long,
        val leftHash: Long,
        val rightHash: Long,
    ) : TraceComparison

    /** The traces describe different simulations and cannot be compared. */
    data class Refused(
        val differences: List<String>,
    ) : TraceComparison
}

data class FieldDifference(
    val path: String,
    val left: DumpedField?,
    val right: DumpedField?,
) {
    fun describe(): String = "$path: ${render(left)} != ${render(right)}"

    private fun render(field: DumpedField?): String =
        if (field == null) "<absent>" else "${field.readable} [${field.exact}]"
}

object TraceDiff {
    fun compare(
        left: Trace,
        right: Trace,
    ): TraceComparison {
        val headerDifferences = identityDifferences(left.header, right.header)
        if (headerDifferences.isNotEmpty()) return TraceComparison.Refused(headerDifferences)

        val shared = minOf(left.entries.size, right.entries.size)
        for (index in 0 until shared) {
            val a = left.entries[index]
            val b = right.entries[index]
            if (a.step != b.step) {
                return TraceComparison.Refused(
                    listOf("step numbering differs at line ${index + 1}: ${a.step} vs ${b.step}"),
                )
            }
            if (a.hash != b.hash) return TraceComparison.Divergence(a.step, a.hash, b.hash)
        }
        return TraceComparison.Agreement(shared, left.entries.size - right.entries.size)
    }

    fun identityDifferences(
        left: Map<String, String>,
        right: Map<String, String>,
    ): List<String> =
        Trace.IDENTITY_KEYS.mapNotNull { key ->
            val a = left[key]
            val b = right[key]
            if (a == b) null else "$key: ${a ?: "<absent>"} vs ${b ?: "<absent>"}"
        }

    /** Fields whose exact value differs, in the left dump's order, then fields only on the right. */
    fun fields(
        left: List<DumpedField>,
        right: List<DumpedField>,
    ): List<FieldDifference> {
        val rightByPath = right.associateBy { it.path }
        val leftPaths = HashSet<String>()
        val differences = ArrayList<FieldDifference>()
        for (field in left) {
            leftPaths += field.path
            val other = rightByPath[field.path]
            if (other == null || other.exact != field.exact) differences += FieldDifference(field.path, field, other)
        }
        right.filter { it.path !in leftPaths }.forEach { differences += FieldDifference(it.path, null, it) }
        return differences
    }

    fun describe(comparison: TraceComparison): String =
        when (comparison) {
            is TraceComparison.Agreement -> {
                val length =
                    when {
                        comparison.lengthDifference > 0 -> "; left has ${comparison.lengthDifference} more steps"
                        comparison.lengthDifference < 0 -> "; right has ${-comparison.lengthDifference} more steps"
                        else -> ""
                    }
                "AGREE: ${comparison.stepsCompared} steps compared$length"
            }

            is TraceComparison.Divergence -> {
                "DIVERGE: first divergent step ${comparison.step} " +
                    "(${hex(comparison.leftHash)} vs ${hex(comparison.rightHash)})"
            }

            is TraceComparison.Refused -> {
                "REFUSED: traces describe different simulations\n" +
                    comparison.differences.joinToString(
                        "\n",
                    ) { "  $it" }
            }
        }
}
