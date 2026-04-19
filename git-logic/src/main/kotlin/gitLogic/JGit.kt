package gitLogic

import org.eclipse.jgit.api.Git
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
}
