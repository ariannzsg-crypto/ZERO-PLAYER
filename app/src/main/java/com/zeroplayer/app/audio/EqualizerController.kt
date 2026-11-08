package com.zeroplayer.app.audio

import com.zeroplayer.app.audio.dsp.DspParams
import com.zeroplayer.app.audio.dsp.EqDsp
import com.zeroplayer.app.data.MusicRepository
import com.zeroplayer.app.data.Prefs
import com.zeroplayer.app.data.db.EqProfileEntity
import com.zeroplayer.app.core.Graph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** UI facing snapshot of the equalizer. */
data class EqState(
    val enabled: Boolean = false,
    val gainsDb: List<Float> = List(10) { 0f },
    val preampDb: Float = 0f,
    val bassBoost: Int = 0,
    val virtualizer: Int = 0,
    val presetName: String = "Flat",
    val bandsHz: List<Float> = DspParams.BANDS_HZ.toList(),
) {
    val isFlat: Boolean get() = gainsDb.all { it == 0f } && bassBoost == 0 && virtualizer == 0 && preampDb == 0f

    fun curve(): List<Float> = gainsDb
}

data class EqPreset(
    val name: String,
    val gainsDb: FloatArray,
    val bass: Int = 0,
    val virtualizer: Int = 0,
    val preamp: Float = 0f,
)

/**
 * Owns the equalizer state and mirrors it into the real time DSP running inside
 * the playback pipeline. Settings are persisted immediately so they survive
 * process death and are re-applied before the next track starts.
 */
object EqualizerController {

    private val _state = MutableStateFlow(EqState())
    val state: StateFlow<EqState> = _state.asStateFlow()

    private var prefs: Prefs? = null
    private var repository: MusicRepository? = null

    val presets: List<EqPreset> = listOf(
        EqPreset("Flat", floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
        EqPreset("Signature", floatArrayOf(4.5f, 3.5f, 1.5f, -0.5f, -1.0f, 0.5f, 2.5f, 3.5f, 2.5f, 1.0f), bass = 220, preamp = -2f),
        EqPreset("Bass Boost", floatArrayOf(7f, 6f, 5f, 3f, 0f, -1f, -1f, 0f, 1f, 1f), bass = 500, preamp = -4f),
        EqPreset("Vocal", floatArrayOf(-2f, -1f, 0f, 1.5f, 4f, 4.5f, 3f, 1f, 0f, -1f), bass = 0, preamp = -2f),
        EqPreset("Rock", floatArrayOf(5f, 4f, 2f, -1f, -2f, 0f, 3f, 4.5f, 4f, 3f), bass = 300, preamp = -3f),
        EqPreset("Jazz", floatArrayOf(3f, 2f, 1f, 2f, -1f, -1f, 0f, 1.5f, 3f, 3.5f), bass = 150, preamp = -1.5f),
        EqPreset("Electronic", floatArrayOf(6f, 5f, 3f, 0f, -2f, 1f, 2f, 4f, 5f, 5.5f), bass = 420, virtualizer = 260, preamp = -3.5f),
        EqPreset("Classical", floatArrayOf(3.5f, 3f, 2f, 1f, -1f, -1f, 0f, 2f, 3f, 3.5f), bass = 100, preamp = -1.5f),
        EqPreset("Acoustic", floatArrayOf(2f, 2.5f, 3f, 2f, 1f, 1.5f, 3f, 3.5f, 2.5f, 1.5f), bass = 120, preamp = -1f),
        EqPreset("Night", floatArrayOf(-4f, -3f, -1f, 1f, 2.5f, 2.5f, 1f, -1f, -2f, -3f), bass = 0, preamp = 0f),
    )

    fun restore(prefs: Prefs, repository: MusicRepository) {
        if (this.prefs != null) return
        this.prefs = prefs
        this.repository = repository

        val gains = prefs.eqGains.toList().let { if (it.size == 10) it else List(10) { 0f } }
        _state.value = EqState(
            enabled = prefs.eqEnabled,
            gainsDb = gains,
            preampDb = prefs.eqPreamp,
            bassBoost = prefs.eqBass,
            virtualizer = prefs.eqVirtualizer,
            presetName = prefs.eqPresetName,
        )
        publish()

        Graph.appScope.launch {
            repository.ensureDefaultEqProfiles(
                presets.map {
                    EqProfileEntity(
                        name = it.name,
                        levels = it.gainsDb.joinToString(","),
                        preampDb = it.preamp,
                        bassBoost = it.bass,
                        virtualizer = it.virtualizer,
                        builtin = true,
                    )
                },
            )
        }
    }

    fun setEnabled(enabled: Boolean) = update { it.copy(enabled = enabled) }

    fun setBand(index: Int, gainDb: Float) = update {
        val bands = it.gainsDb.toMutableList()
        if (index in bands.indices) bands[index] = gainDb.coerceIn(-12f, 12f)
        it.copy(gainsDb = bands, presetName = if (it.presetName == "Flat") "Custom" else it.presetName)
    }

    fun setGains(gains: List<Float>, presetName: String = "Custom") = update {
        it.copy(
            gainsDb = List(10) { index -> (gains.getOrNull(index) ?: 0f).coerceIn(-12f, 12f) },
            presetName = presetName,
        )
    }

    fun setPreamp(db: Float) = update { it.copy(preampDb = db.coerceIn(-12f, 6f)) }

    fun setBassBoost(value: Int) = update { it.copy(bassBoost = value.coerceIn(0, 1_000)) }

    fun setVirtualizer(value: Int) = update { it.copy(virtualizer = value.coerceIn(0, 1_000)) }

    fun applyPreset(preset: EqPreset) = update {
        it.copy(
            gainsDb = preset.gainsDb.map { gain -> gain.coerceIn(-12f, 12f) },
            bassBoost = preset.bass,
            virtualizer = preset.virtualizer,
            preampDb = preset.preamp,
            presetName = preset.name,
            enabled = true,
        )
    }

    fun applyProfile(profile: EqProfileEntity) {
        val gains = profile.levels.split(',').mapNotNull { it.toFloatOrNull() }
        update {
            it.copy(
                gainsDb = List(10) { index -> gains.getOrNull(index) ?: 0f },
                preampDb = profile.preampDb,
                bassBoost = profile.bassBoost,
                virtualizer = profile.virtualizer,
                presetName = profile.name,
                enabled = true,
            )
        }
    }

    fun reset() = update { EqState() }

    fun saveProfile(name: String, onSaved: (Long) -> Unit = {}) {
        val repo = repository ?: return
        val snapshot = _state.value
        Graph.appScope.launch {
            val id = repo.saveEqProfile(
                EqProfileEntity(
                    name = name.trim().ifBlank { "Custom" },
                    levels = snapshot.gainsDb.joinToString(","),
                    preampDb = snapshot.preampDb,
                    bassBoost = snapshot.bassBoost,
                    virtualizer = snapshot.virtualizer,
                ),
            )
            onSaved(id)
        }
    }

    fun deleteProfile(id: Long) {
        repository?.let { repo -> Graph.appScope.launch { repo.deleteEqProfile(id) } }
    }

    private fun update(transform: (EqState) -> EqState) {
        val next = transform(_state.value)
        _state.value = next
        persist(next)
        publish()
    }

    private fun persist(state: EqState) {
        val store = prefs ?: return
        store.eqEnabled = state.enabled
        store.eqGains = state.gainsDb.toFloatArray()
        store.eqPreamp = state.preampDb
        store.eqBass = state.bassBoost
        store.eqVirtualizer = state.virtualizer
        store.eqPresetName = state.presetName
    }

    private fun publish() {
        val state = _state.value
        EqDsp.publish(
            enabled = state.enabled,
            gainsDb = state.gainsDb.toFloatArray(),
            preampDb = state.preampDb,
            bassBoost = state.bassBoost,
            virtualizer = state.virtualizer,
        )
    }
}
