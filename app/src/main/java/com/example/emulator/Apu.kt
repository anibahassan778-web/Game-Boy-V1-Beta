package com.example.emulator

import android.media.AudioFormat
import android.media.AudioTrack
import android.media.AudioManager

class Apu {

    var enabled: Boolean = true
    var isMuted: Boolean = false

    // Frame Sequencer 512 Hz
    private var frameSequencerCounter: Int = 0
    private var frameSequencerStep: Int = 0

    // Wave RAM (16 bytes = 32 x 4-bit samples)
    val waveRam = ByteArray(16)

    // Channel 1 Pulse 1 (Sweep, Duty, Length, Envelope, Frequency)
    var nr10: Int = 0 // Sweep
    var nr11: Int = 0 // Duty & Length
    var nr12: Int = 0 // Envelope
    var nr13: Int = 0 // Freq Low
    var nr14: Int = 0 // Freq High & Control

    // Channel 2 Pulse 2
    var nr21: Int = 0
    var nr22: Int = 0
    var nr23: Int = 0
    var nr24: Int = 0

    // Channel 3 Wave
    var nr30: Int = 0 // DAC enable
    var nr31: Int = 0 // Length
    var nr32: Int = 0 // Output level
    var nr33: Int = 0 // Freq Low
    var nr34: Int = 0 // Freq High

    // Channel 4 Noise
    var nr41: Int = 0 // Length
    var nr42: Int = 0 // Envelope
    var nr43: Int = 0 // Polynomial counter
    var nr44: Int = 0 // Counter & initial

    // Control registers
    var nr50: Int = 0x77
    var nr51: Int = 0xF3
    var nr52: Int = 0x80 // Power status

    // Internal Channel states
    private var ch1Enabled = false
    private var ch1Timer = 0
    private var ch1Freq = 0
    private var ch1Volume = 0
    private var ch1DutyPos = 0

    private var ch2Enabled = false
    private var ch2Timer = 0
    private var ch2Freq = 0
    private var ch2Volume = 0
    private var ch2DutyPos = 0

    private var ch3Enabled = false
    private var ch3Timer = 0
    private var ch3Freq = 0
    private var ch3Pos = 0

    private var ch4Enabled = false
    private var ch4Timer = 0
    private var ch4Lfsr = 0x7FFF
    private var ch4Volume = 0

    // Audio Output Stream via AudioTrack
    private val sampleRate = 44100
    private var audioTrack: AudioTrack? = null
    private val pcmBuffer = ShortArray(512)
    private var pcmBufferIndex = 0
    private var sampleAccumulator = 0.0

    init {
        try {
            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBufferSize > 0) {
                val track = AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minBufferSize, 2048),
                    AudioTrack.MODE_STREAM
                )
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    track.play()
                    audioTrack = track
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            audioTrack = null
        }
    }

    fun step(cycles: Int) {
        if ((nr52 and 0x80) == 0) return // APU Power Off

        // Frame Sequencer runs at 512 Hz (every 8192 T-cycles at 4.194304 MHz)
        frameSequencerCounter += cycles
        if (frameSequencerCounter >= 8192) {
            frameSequencerCounter -= 8192
            stepFrameSequencer()
        }

        // Sample generation for AudioTrack (~44100 Hz = every 95 T-cycles)
        sampleAccumulator += cycles
        if (sampleAccumulator >= 95.0) {
            sampleAccumulator -= 95.0
            generateSample()
        }
    }

    private fun stepFrameSequencer() {
        when (frameSequencerStep) {
            0 -> { clockLength() }
            1 -> { }
            2 -> { clockLength(); clockSweep() }
            3 -> { }
            4 -> { clockLength() }
            5 -> { }
            6 -> { clockLength(); clockSweep() }
            7 -> { clockEnvelope() }
        }
        frameSequencerStep = (frameSequencerStep + 1) and 7
    }

    private fun clockLength() {
        // Decrement channel lengths if enabled
    }

    private fun clockSweep() {
        // Channel 1 frequency sweep
    }

    private fun clockEnvelope() {
        // Channel volume envelope steps
    }

    private fun generateSample() {
        if (!enabled || isMuted) {
            outputSample(0)
            return
        }

        // Mix 4 channels
        var sample = 0

        if (ch1Enabled) {
            sample += ch1Volume * 200
        }
        if (ch2Enabled) {
            sample += ch2Volume * 200
        }
        if (ch3Enabled) {
            sample += 300
        }
        if (ch4Enabled) {
            sample += ch4Volume * 200
        }

        outputSample(sample.coerceIn(-32768, 32767))
    }

    private fun outputSample(sample: Int) {
        if (pcmBufferIndex < pcmBuffer.size) {
            pcmBuffer[pcmBufferIndex++] = sample.toShort()
        }

        if (pcmBufferIndex >= pcmBuffer.size) {
            pcmBufferIndex = 0
            val track = audioTrack
            if (track != null && track.state == AudioTrack.STATE_INITIALIZED) {
                try {
                    track.write(pcmBuffer, 0, pcmBuffer.size)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun triggerChannel1() {
        ch1Enabled = true
        ch1Volume = (nr12 ushr 4) and 0x0F
        ch1Freq = nr13 or ((nr14 and 0x07) shl 8)
    }

    fun triggerChannel2() {
        ch2Enabled = true
        ch2Volume = (nr22 ushr 4) and 0x0F
        ch2Freq = nr23 or ((nr24 and 0x07) shl 8)
    }

    fun triggerChannel3() {
        ch3Enabled = (nr30 and 0x80) != 0
        ch3Freq = nr33 or ((nr34 and 0x07) shl 8)
    }

    fun triggerChannel4() {
        ch4Enabled = true
        ch4Volume = (nr42 ushr 4) and 0x0F
        ch4Lfsr = 0x7FFF
    }

    fun release() {
        try {
            val track = audioTrack
            audioTrack = null
            track?.stop()
            track?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
