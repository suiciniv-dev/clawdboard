package dev.clawdboard

import android.app.Application
import android.content.Context
import dev.clawdboard.core.Repository

class ClawdApp : Application() {
    lateinit var repo: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        repo = Repository(this)
        repo.startPanel()
    }
}

val Context.repo: Repository get() = (applicationContext as ClawdApp).repo
