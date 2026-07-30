package com.mohnishraj.goldmineledger

import android.content.Context

class AppContainer(context: Context) {
    val db = LedgerDatabase.create(context)
    val settings = SettingsRepository(context)
    val attachmentStorage = AttachmentStorage(context.applicationContext)
    val repository = LedgerRepository(db, settings, attachmentStorage)
    val portability = DataPortabilityManager(context.applicationContext, db, repository)
}
