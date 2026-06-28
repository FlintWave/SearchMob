package org.searchmob.data.prefs

import org.searchmob.engine.rank.PersonalizationModel
import org.searchmob.engine.rank.Personalizer

/**
 * Persists the learned click-personalization [PersonalizationModel] as a single JSON blob in the
 * DEK-encrypted preferences store, alongside the ranking rules. The model encodes which sites the
 * owner tends to click, so it is private personalization and AES-256-GCM-encrypted at rest like
 * every other preference. Reads are fail-soft: a missing or corrupt value yields an empty model, so
 * personalization is simply absent when it cannot be loaded (for example, while the vault is locked).
 * The stored JSON is the portable `beta_bernoulli_v1` document shared with the desktop app, so a
 * backup or a model moved from another device is just the same file.
 */
class PersonalizationPreferences(private val store: PreferencesStore) {
    suspend fun load(): PersonalizationModel {
        val raw = runCatching { store.get(KEY) }.getOrNull() ?: return PersonalizationModel()
        return Personalizer.fromJson(raw)
    }

    suspend fun save(model: PersonalizationModel) {
        store.put(KEY, Personalizer.toJson(model))
    }

    /** Forget everything learned, keeping the current config. */
    suspend fun reset() {
        save(Personalizer.reset(load()))
    }

    suspend fun exportJson(): String = Personalizer.toJson(load())

    /** Replace the model from an exported JSON document. Returns false if it cannot be saved. */
    suspend fun importJson(text: String): Boolean {
        return runCatching { save(Personalizer.fromJson(text)) }.isSuccess
    }

    private companion object {
        const val KEY = "ranking.personalization"
    }
}
