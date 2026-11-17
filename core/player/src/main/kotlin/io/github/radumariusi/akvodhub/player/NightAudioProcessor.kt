package io.github.radumariusi.akvodhub.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import io.github.radumariusi.akvodhub.parser.player.NightCompressor
import java.nio.ByteBuffer
import kotlin.math.abs

/** Media3 convertește PCM în 16 biți înaintea procesoarelor personalizate. */
class NightAudioProcessor : BaseAudioProcessor() {
    private var compressor = NightCompressor(48_000)

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat =
        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) inputAudioFormat else AudioFormat.NOT_SET

    override fun onFlush() {
        compressor = NightCompressor(inputAudioFormat.sampleRate)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val output = replaceOutputBuffer(inputBuffer.remaining())
        val channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        while (inputBuffer.remaining() >= channels * 2) {
            val start = inputBuffer.position()
            var peak = 0.0
            repeat(channels) { peak = maxOf(peak, abs(inputBuffer.short.toDouble() / 32768)) }
            val gain = compressor.gain(peak)
            inputBuffer.position(start)
            repeat(channels) {
                output.putShort((inputBuffer.short * gain).toInt().coerceIn(-32768, 32767).toShort())
            }
        }
        output.flip()
    }
}
