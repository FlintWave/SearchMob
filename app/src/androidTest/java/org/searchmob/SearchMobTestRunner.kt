package org.searchmob

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/**
 * Instrumentation runner that swaps the production [SearchMobApplication] for a plain [Application]
 * during tests. The real Application opens the encrypted DataStore and SQLCipher history on fixed file
 * paths at startup; letting that run alongside the storage instrumented tests (which open the same
 * files) causes "multiple DataStores active for the same file" and wrong-key DB errors. The tests do
 * not need the production Application (UI tests use a generic ComponentActivity; storage tests build
 * their own stores), so a stock Application keeps them isolated.
 */
class SearchMobTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application = super.newApplication(cl, Application::class.java.name, context)
}
