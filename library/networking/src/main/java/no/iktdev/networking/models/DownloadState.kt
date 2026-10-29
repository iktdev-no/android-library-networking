package no.iktdev.networking.models

enum class DownloadState {
    NotStarted,
    Running,
    Paused,
    Failed,
    Canceled,
    Completed
}