package gitLogic

import org.eclipse.jgit.api.Git
import java.io.File

class JGit : GitCommands {
    override fun init(path: String) {
        Git.init()
            .setDirectory(File(path))
            .call()
            .close()
    }
}
