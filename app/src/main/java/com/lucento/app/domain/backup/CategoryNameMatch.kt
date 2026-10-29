package com.lucento.app.domain.backup

/**
 * Categories created on two installs share a name but not a [BackupCategory.id]. Merge folds those
 * when the normalized name and the kind both match, and leaves a same-name pair of different kinds
 * as two categories.
 */
internal object CategoryNameMatch {
    data class Plan(
        /** Incoming category id → the local category it folds into. */
        val remap: Map<String, String>,
        val conflicts: List<CategoryKindConflict>,
    ) {
        val matched: Int get() = remap.size
    }

    fun plan(local: List<BackupCategory>, incomingOnly: List<BackupCategory>): Plan {
        val remap = LinkedHashMap<String, String>()
        val conflicts = ArrayList<CategoryKindConflict>()
        for (row in incomingOnly) {
            val sameName = local.filter { normalize(it.name) == normalize(row.name) && it.id != row.id }
            val sameKind = sameName.filter { it.kind == row.kind }
            val keeper = sameKind.firstOrNull { !it.archived } ?: sameKind.firstOrNull()
            if (keeper != null) {
                remap[row.id] = keeper.id
            } else if (sameName.isNotEmpty()) {
                conflicts += CategoryKindConflict(
                    name = row.name.trim(),
                    fileKind = row.kind,
                    localKinds = sameName.map { it.kind }.distinct(),
                )
            }
        }
        return Plan(remap, conflicts)
    }

    /** Trimmed, case-folded, with runs of whitespace collapsed. " Food " and "food" are one name. */
    fun normalize(name: String): String = name.trim().lowercase().replace(Regex("\\s+"), " ")
}
