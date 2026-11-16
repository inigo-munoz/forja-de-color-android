package app.forjadecolor.domain.storage

/**
 * Deletes the given photos unless a stored project still points to one of them (a restored backup can
 * share a photo between projects). Call it after the change that dropped them has been saved.
 */
suspend fun Repository.deletePhotosIfUnused(ids: Collection<String?>) {
    val candidates = ids.filterNotNull().toSet()
    if (candidates.isEmpty()) return
    val stillUsed = listProjects().flatMapTo(mutableSetOf()) { it.blobIds }
    (candidates - stillUsed).forEach { deleteBlob(it) }
}

/** Deletes a project and then the photos only it used; a missing project changes nothing. */
suspend fun Repository.deleteProjectWithPhotos(id: String) {
    val project = getProject(id) ?: return
    deleteProject(id)
    deletePhotosIfUnused(project.blobIds)
}
