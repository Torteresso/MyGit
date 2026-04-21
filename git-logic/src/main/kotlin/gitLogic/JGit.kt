package gitLogic

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.Status
import org.eclipse.jgit.lib.IndexDiff
import java.io.File
import java.nio.file.Paths

class JGit : GitCommandsFunctions {
    override fun init(config: InitConfig) {
        Git.init()
            .setDirectory(File(config.path))
            .setInitialBranch(config.initialBranchName)
            .call()
            .close()
    }

    override fun add(config: AddConfig) {
        val filesToAdd = config.filesToAdd.relativizeFilesNames(config.repoDirectory)
        Git.open(File(config.repoDirectory)).use {
            it.add()
                .addFilepatterns(filesToAdd)
                .call()
        }
    }

    override fun commit(config: CommitConfig) {
        Git.open(File(config.repoDirectory)).use {
            it.commit()
                .setMessage(config.message)
                .call()
        }
    }

    // Do not print for CLI-APP
    override fun status(config: StatusConfig): List<FileStatus> {

        val relativePathToCheck = config.filesToCheck?.relativizeFilesNames(config.repoDirectory)
        Git.open(File(config.repoDirectory)).use {
            val status: Status = it.status()
                .apply { relativePathToCheck?.forEach { file -> this.addPath(file) } }
                .call()

            val filesToCheck = relativePathToCheck
                ?: (status.added + status.changed + status.modified +
                        status.removed + status.missing + status.untracked +
                        status.conflicting)

            return filesToCheck.map { filePath ->
                classifyFile(filePath, status)
            }
        }
    }

}

private fun classifyFile(filePath: String, status: Status): FileStatus {
    val inAdded = filePath in status.added
    val inChanged = filePath in status.changed
    val inRemoved = filePath in status.removed
    val inModified = filePath in status.modified
    val inMissing = filePath in status.missing

    if (filePath in status.conflicting) {
        return when (status.conflictingStageState[filePath]) {
            IndexDiff.StageState.BOTH_MODIFIED -> FileStatus.CONFLICT_BOTH_MODIFIED
            IndexDiff.StageState.BOTH_ADDED -> FileStatus.CONFLICT_BOTH_ADDED
            IndexDiff.StageState.BOTH_DELETED -> FileStatus.CONFLICT_BOTH_DELETED
            IndexDiff.StageState.ADDED_BY_US -> FileStatus.CONFLICT_ADDED_BY_US
            IndexDiff.StageState.ADDED_BY_THEM -> FileStatus.CONFLICT_ADDED_BY_THEM
            IndexDiff.StageState.DELETED_BY_US -> FileStatus.CONFLICT_DELETED_BY_US
            IndexDiff.StageState.DELETED_BY_THEM -> FileStatus.CONFLICT_DELETED_BY_THEM
            null -> FileStatus.ERROR
        }
    }

    if (inAdded && inModified) return FileStatus.ADDED_MODIFIED
    if (inAdded && inMissing) return FileStatus.ADDED_DELETED
    if (inChanged && inModified) return FileStatus.MODIFIED_STAGED_UNSTAGED
    if (inChanged && inMissing) return FileStatus.MODIFIED_STAGED_DELETED
    if (inRemoved && inModified) return FileStatus.DELETED_STAGED_MODIFIED

    if (inAdded) return FileStatus.ADDED
    if (inChanged) return FileStatus.MODIFIED_STAGED
    if (inRemoved) return FileStatus.DELETED_STAGED

    if (inModified) return FileStatus.MODIFIED_UNSTAGED
    if (inMissing) return FileStatus.DELETED_UNSTAGED
    if (filePath in status.untracked) return FileStatus.UNTRACKED
    if (filePath in status.ignoredNotInIndex) return FileStatus.IGNORED

    return FileStatus.UNMODIFIED
}


private fun List<String>.relativizeFilesNames(repoDirectory: String): List<String> {
    return this.map { fileName ->
        Paths.get(repoDirectory).relativize(Paths.get(fileName)).toString()
    }
}