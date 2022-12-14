package com.ss.ugc.android.editor.bottom.panel.audio.record

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.ss.ugc.android.editor.base.path.PathConstant
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
private const val FORMAT = AudioFormat.ENCODING_PCM_16BIT
private const val SAMPLE_RATE = 44100
private const val BYTE_PER_MS = SAMPLE_RATE / 1000 * 2
private const val BUFFER_MS = 5

class MicAudioFetcher(private val wavName: String) {
    private val bufferSize = BUFFER_MS * AudioRecord.getMinBufferSize(
        SAMPLE_RATE,
        CHANNEL,
        FORMAT
    )
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    private var pcmCache = "${PathConstant.AUDIO_DIR}/${dateFormat.format(Date())}.pcm"

    @Volatile
    private var stopped = false
    private var bufferCallback: ((shortArray: ShortArray, size: Int) -> Unit)? = null

    private val thread = Thread(
        {
            try {
                val recorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL,
                    FORMAT, bufferSize
                )
                recorder.startRecording()

                val bufferSize = BUFFER_MS * BYTE_PER_MS
                val buffer = ShortArray(bufferSize)
                var byteArray: ByteArray

                val output = FileOutputStream(pcmCache)
                while (!stopped) {
                    val size = recorder.read(buffer, 0, bufferSize)
                    if (size > 0) {
                        byteArray = shortArray2ByteArray(buffer)
                        output.write(byteArray, 0, byteArray.size)
                        bufferCallback?.invoke(buffer, size)
                    }
                }
                recorder.stop()
                recorder.release()

                output.close()
                convertWaveFile()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        },
        "audio_recorder"
    )

//    init {
//        FileUtil.makeDir(pcmCache)
//    }

    fun start(bufferBlock: (shortArray: ShortArray, size: Int) -> Unit) {
        stopped = false
        bufferCallback = bufferBlock
        thread.start()
    }

    fun stop() {
        stopped = true
        thread.join()
    }

    // ????????????????????????????????????
    private fun convertWaveFile() {
        val infile = FileInputStream(pcmCache)
        val out = FileOutputStream(wavName)
        val longSampleRate: Long = SAMPLE_RATE.toLong()
        val channels = 1
        val byteRate: Long = (16 * SAMPLE_RATE * channels / 8).toLong()
        val data = ByteArray(bufferSize)

        val totalAudioLen = infile.channel.size()
        // ???????????????RIFF???WAV
        val totalDataLen: Long = totalAudioLen + 36
        val header = getHeader(totalDataLen, channels, longSampleRate, byteRate, totalAudioLen)
        out.write(header, 0, 44)
        while (infile.read(data) != -1) {
            out.write(data)
        }
        infile.close()
        out.close()

        File(pcmCache).delete()
    }

    // ???????????????????????????????????????????????????????????????????????????????????????????????????
    // wave???RIFF????????????????????????????????????chunk???
    // ???RIFF WAVE chunk, FMT Chunk, Fact chunk, Data chunk
    // ??????Fact chunk??????????????????
    private fun getHeader(
        totalDataLen: Long,
        channels: Int,
        longSampleRate: Long,
        byteRate: Long,
        totalAudioLen: Long
    ): ByteArray {
        val header = ByteArray(44)
        header[0] = 'R'.toByte() // RIFF
        header[1] = 'I'.toByte()
        header[2] = 'F'.toByte()
        header[3] = 'F'.toByte()
        header[4] = (totalDataLen and 0xff).toByte() // ????????????
        header[5] = (totalDataLen shr 8 and 0xff).toByte()
        header[6] = (totalDataLen shr 16 and 0xff).toByte()
        header[7] = (totalDataLen shr 24 and 0xff).toByte()
        header[8] = 'W'.toByte() // WAVE
        header[9] = 'A'.toByte()
        header[10] = 'V'.toByte()
        header[11] = 'E'.toByte()
        // FMT Chunk
        header[12] = 'f'.toByte() // 'fmt '
        header[13] = 'm'.toByte()
        header[14] = 't'.toByte()
        header[15] = ' '.toByte() // ????????????
        // ????????????
        header[16] = 16 // 4 bytes: size of 'fmt ' chunk
        header[17] = 0
        header[18] = 0
        header[19] = 0
        // ???????????? 10H???PCM????????????
        header[20] = 1 // format = 1
        header[21] = 0
        // ?????????
        header[22] = channels.toByte()
        header[23] = 0
        // ???????????????????????????????????????
        header[24] = (longSampleRate and 0xff).toByte()
        header[25] = (longSampleRate shr 8 and 0xff).toByte()
        header[26] = (longSampleRate shr 16 and 0xff).toByte()
        header[27] = (longSampleRate shr 24 and 0xff).toByte()
        // ????????????????????????,?????????*?????????*????????????/8
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        // ???????????????????????????????????????????????????????????????????????????????????????*????????????
        header[32] = (1 * 16 / 8).toByte()
        header[33] = 0
        // ???????????????????????????
        header[34] = 16
        header[35] = 0
        // Data chunk
        header[36] = 'd'.toByte()
        header[37] = 'a'.toByte()
        header[38] = 't'.toByte()
        header[39] = 'a'.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = (totalAudioLen shr 8 and 0xff).toByte()
        header[42] = (totalAudioLen shr 16 and 0xff).toByte()
        header[43] = (totalAudioLen shr 24 and 0xff).toByte()
        return header
    }

    /**
     * ?????????short[]???????????????byte[]????????????
     * @param b
     */
    fun shortArray2ByteArray(b: ShortArray): ByteArray {
        val rebt = ByteArray(b.size * 2)
        var index = 0
        for (i in b.indices) {
            val st = b[i]
            val bt = shortToByte(st)
            rebt[index] = bt[0]
            rebt[index + 1] = bt[1]
            index += 2
        }
        return rebt
    }

    /**
     * short?????????byte[]
     * @param number
     * @return byte[]
     */
    fun shortToByte(number: Short): ByteArray {
        var temp = number.toInt()
        val b = ByteArray(2) // ??????????????????????????????
        b[0] = (temp and 0xff).toByte()
        temp = temp shr 8 // ?????????8???
        b[1] = (temp and 0xff).toByte()
        return b
    }
}
