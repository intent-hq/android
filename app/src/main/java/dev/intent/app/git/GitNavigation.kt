package dev.intent.app.git

import dev.intent.core.protocol.models.GitCommit

data class GitNavigation(val onRoot: (String?) -> Unit = {}, val onCommit: (GitCommit) -> Unit = {})
