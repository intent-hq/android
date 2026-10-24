package dev.intent.app.design

enum class AgentAvatarState(val label: String) {
    Idle("Idle"),
    Running("Running"),
    Waiting("Waiting"),
    Failed("Failed"),
    Completed("Completed"),
    Question("Question pending"),
    AttentionDiscussion("Discussion requested"),
    AttentionBlocker("Blocked")
}
