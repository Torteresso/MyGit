package gitLogic

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.Status
import java.io.File

class JGit : GitCommandsFunctions {
    override fun init(config: InitConfig) {
        Git.init()
            .setDirectory(File(config.path))
            .setInitialBranch(config.initialBranchName)
            .call()
            .close()
    }

    override fun add(config: AddConfig) {
        Git.open(File(config.repoDirectory)).use {
            it.add()
                .addFilepatterns(config.filesToAdd)
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

        Git.open(File(config.repoDirectory)).use {
            val status: Status = it.status()
                .apply { config.filesToCheck?.forEach { file -> this.addPath(file) } }
                .call()

            val filesToCheck = config.filesToCheck
                ?: (status.added + status.changed + status.modified +
                        status.removed + status.missing + status.untracked +
                        status.conflicting)

            return filesToCheck.map { filePath ->
                when (filePath) {
                    in status.added -> FileStatus.ADDED
                    in status.changed -> FileStatus.MODIFIED_STAGED
                    in status.modified -> FileStatus.MODIFIED_UNSTAGED
                    in status.removed -> FileStatus.DELETED_STAGED
                    in status.missing -> FileStatus.DELETED_UNSTAGED
                    in status.untracked -> FileStatus.UNTRACKED
                    in status.conflicting -> FileStatus.CONFLICT
                    else -> FileStatus.UNMODIFIED
                }
            }
        }
    }
}
