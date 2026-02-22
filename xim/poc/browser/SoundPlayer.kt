package xim.poc.browser

import web.audio.*
import xim.poc.FadeParameters
import xim.poc.game.GameState
import xim.resource.ByteReader

private val context by lazy { AudioContext() }

class SoundPlayer(val resourceName: String, var volume: Float, val id: Int, val loop: Boolean = false, val timeSensitive: Boolean = false) {

    private var gainNode: GainNode? = null
    private var panNode: StereoPannerNode? = null
    private var sourceNode: AudioBufferSourceNode? = null

    private var fadeParameters: FadeParameters? = null
    private var ended = false

    fun play(startingPosition: Double = 0.0) {
        DatLoader.load(resourceName, parserContext = ParserContext.optionalResource).onReady {
            val buffer = it.getAsBuffer()
            val loopStart = parseLoopPoint(it)
            val bufferCopy = buffer.slice(0)

            context.decodeAudioData(
                audioData = bufferCopy,
                successCallback = { audioBuffer -> playBuffer(audioBuffer, startingPosition, loopStart) },
                errorCallback = { domException -> println("[$id] Audio decoding failed: $domException") }
            )
        }
    }

    fun update(elapsedFrames: Float) {
        val currentFade = fadeParameters
        currentFade?.update(elapsedFrames)
        if (currentFade != null && currentFade.shouldRemove()) { stop() }

        if (timeSensitive) { sourceNode?.playbackRate?.value = GameState.gameSpeed }
        adjustVolume(volume)
    }

    fun adjustVolume(volume: Float) {
        this.volume = volume
        gainNode?.gain?.value = volume * getFadeMultiplier()
    }

    fun adjustPan(pan: Float) {
        panNode?.pan?.value = pan
    }

    fun applyFade(fadeParameters: FadeParameters) {
        this.fadeParameters = fadeParameters
    }

    fun stopLooping() {
        sourceNode?.loop = false
    }

    fun stop() {
        adjustVolume(0f)
        sourceNode?.stop()
    }

    fun isEnded(): Boolean {
        return ended
    }

    private fun getFadeMultiplier(): Float {
        return fadeParameters?.getOpacity() ?: 1.0f
    }

    private fun playBuffer(buffer: AudioBuffer, startingPosition: Double, loopStart: Double? = null) {
        val gainNode = context.createGain()
        gainNode.gain.value = volume
        gainNode.connect(context.destination)

        val panNode = context.createStereoPanner()
        panNode.connect(gainNode)

        val source = context.createBufferSource()
        source.buffer = buffer
        source.connect(panNode)

        if (loop && loopStart != null) {
            source.loop = true
            source.loopStart = loopStart * buffer.duration
            source.loopEnd = buffer.duration
        }

        source.onended = { ended = true }
        source.start(0.0, startingPosition)

        this.gainNode = gainNode
        this.panNode = panNode
        this.sourceNode = source
    }

    private fun parseLoopPoint(datWrapper: DatWrapper): Double? {
        // TODO - actually parse the tags properly
        val br = datWrapper.getAsBytes()
        br.position = 0x54

        val numPages = br.next8()
        br.position += numPages

        val unk0 = br.next8()

        val vorbis = br.nextString(6)
        if (vorbis != "vorbis") { throw IllegalStateException("Not vorbis; was $vorbis") }

        val name = readLengthAndString(br)

        val numTags = br.next32()
        if (numTags != 3) { throw IllegalStateException("Unexpected number of tags: $numTags") }

        val encoder = readLengthAndString(br)
        if (!encoder.startsWith("encoder")) { throw IllegalStateException("Unexpected first tag: $encoder") }

        val tags = HashMap<String, Int>()

        for (i in 0 until 2) {
            val tag = readLengthAndString(br).split("=")
            tags[tag[0]] = tag[1].toInt()
        }

        val samples = tags["SAMPLES"] ?: return null
        val loop = tags["LOOPSTART"] ?: return null

        if (loop <= 0) { return null }
        return loop.toDouble() / samples.toDouble()
    }

    private fun readLengthAndString(byteReader: ByteReader): String {
        val length = byteReader.next32()
        return byteReader.nextString(length)
    }

}