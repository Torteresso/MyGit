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
}
