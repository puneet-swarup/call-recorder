package com.callrecorder.shizuku

import java.io.File
import java.io.RandomAccessFile

object WavWriter {

    fun wrap(raw: File, target: File, sampleRate: Int) {
        val dataLength = raw.length().toInt()
        RandomAccessFile(target, "rw").use { out ->
            out.setLength(0)
            out.writeBytes("RIFF")
            out.writeIntLe(36 + dataLength)
            out.writeBytes("WAVE")
            out.writeBytes("fmt ")
            out.writeIntLe(16)
            out.writeShortLe(1)
            out.writeShortLe(1)
            out.writeIntLe(sampleRate)
            out.writeIntLe(sampleRate * 2)
            out.writeShortLe(2)
            out.writeShortLe(16)
            out.writeBytes("data")
            out.writeIntLe(dataLength)
            raw.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                }
            }
        }
    }

    private fun RandomAccessFile.writeIntLe(value: Int) {
        write(
            byteArrayOf(
                (value and 0xFF).toByte(),
                ((value shr 8) and 0xFF).toByte(),
                ((value shr 16) and 0xFF).toByte(),
                ((value shr 24) and 0xFF).toByte(),
            ),
        )
    }

    private fun RandomAccessFile.writeShortLe(value: Int) {
        write(byteArrayOf((value and 0xFF).toByte(), ((value shr 8) and 0xFF).toByte()))
    }
}
