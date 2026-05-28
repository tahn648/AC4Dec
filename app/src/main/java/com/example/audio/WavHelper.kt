package com.example.audio

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavHelper {

    /**
     * Writes standard 44-byte WAV header.
     */
    fun writeWavHeader(
        channelCount: Int,
        sampleRate: Int,
        bitsPerSample: Int,
        dataSize: Long,
        outputStream: java.io.OutputStream
    ) {
        val header = ByteBuffer.allocate(44).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            
            // RIFF chunk descriptor
            put("RIFF".toByteArray())
            putInt((36 + dataSize).toInt()) // ChunkSize
            put("WAVE".toByteArray())
            
            // "fmt " sub-chunk
            put("fmt ".toByteArray())
            putInt(16) // Subchunk1Size for PCM
            putShort(1.toShort()) // AudioFormat: 1 for PCM
            putShort(channelCount.toShort())
            putInt(sampleRate)
            putInt(sampleRate * channelCount * (bitsPerSample / 8)) // ByteRate
            putShort((channelCount * (bitsPerSample / 8)).toShort()) // BlockAlign
            putShort(bitsPerSample.toShort()) // BitsPerSample
            
            // "data" sub-chunk
            put("data".toByteArray())
            putInt(dataSize.toInt()) // Subchunk2Size
        }
        outputStream.write(header.array())
    }

    /**
     * Updates WAV header data size dynamically (useful if total size is unknown beforehand).
     */
    fun updateWavHeaderSizes(file: File, dataSize: Long) {
        RandomAccessFile(file, "rw").use { raf ->
            // ChunkSize at byte offset 4: 36 + dataSize
            raf.seek(4)
            val chunkSize = 36 + dataSize
            raf.write(
                ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(chunkSize.toInt()).array()
            )
            
            // Subchunk2Size at byte offset 40: dataSize
            raf.seek(40)
            raf.write(
                ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(dataSize.toInt()).array()
            )
        }
    }

    /**
     * Splits multi-channel interleaved 16-bit PCM data into multiple mono PCM files.
     * Returns a list of split mono WAV files, encoded at the chosen bitsPerSample.
     */
    fun splitMultichannelWav(
        inputFile: File,
        outputDir: File,
        baseName: String,
        channelCount: Int,
        sampleRate: Int,
        bitsPerSample: Int
    ): List<File> {
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        val channelNames = when (channelCount) {
            1 -> listOf("Mono")
            2 -> listOf("Left", "Right")
            6 -> listOf("Left(L)", "Right(R)", "Center(C)", "LFE", "SurroundLeft(Ls)", "SurroundRight(Rs)")
            8 -> listOf("Left", "Right", "Center", "LFE", "SurroundLeft", "SurroundRight", "BackLeft", "BackRight")
            else -> (1..channelCount).map { "Channel_$it" }
        }

        // Input cache file is always 16-bit PCM, i.e., 2 bytes per sample
        val inputSampleSize = 2
        val inputFrameSize = channelCount * inputSampleSize
        
        val files = channelNames.map { name ->
            File(outputDir, "${baseName}_$name.wav")
        }
        
        val fileStreams = files.map { file ->
            val fos = FileOutputStream(file)
            // Write placeholder header, update later
            writeWavHeader(1, sampleRate, bitsPerSample, 0, fos)
            fos
        }

        val outputSampleSize = bitsPerSample / 8

        try {
            inputFile.inputStream().buffered().use { fis ->
                // Skip the header of the input file (assuming it has a 44-byte WAV header)
                fis.skip(44)
                
                val buffer = ByteArray(inputFrameSize * 1024)
                var bytesRead: Int
                val dataSizes = LongArray(channelCount) { 0L }

                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    val framesRead = bytesRead / inputFrameSize
                    for (f in 0 until framesRead) {
                        val frameOffset = f * inputFrameSize
                        for (c in 0 until channelCount) {
                            val sampleOffset = frameOffset + (c * inputSampleSize)
                            if (sampleOffset + 1 < bytesRead) {
                                val low = buffer[sampleOffset].toInt() and 0xFF
                                val high = buffer[sampleOffset + 1].toInt()
                                val sample16 = ((high shl 8) or low).toShort()
                                
                                writeSample(fileStreams[c], sample16, bitsPerSample)
                                dataSizes[c] += outputSampleSize
                            }
                        }
                    }
                }
                
                // Update final wav headers with correct values
                for (c in 0 until channelCount) {
                    fileStreams[c].close()
                    updateWavHeaderSizes(files[c], dataSizes[c])
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            fileStreams.forEach { try { it.close() } catch (ex: Exception) {} }
        }

        return files
    }

    /**
     * Helper to write a 16-bit PCM short sample scaled to desired target bits per sample
     */
    private fun writeSample(os: java.io.OutputStream, sample: Short, bitsPerSample: Int) {
        when (bitsPerSample) {
            16 -> {
                os.write(sample.toInt() and 0xFF)
                os.write((sample.toInt() shr 8) and 0xFF)
            }
            24 -> {
                // Scale 16-bit to 24-bit PCM (shift left by 8 bits)
                val val24 = sample.toInt() shl 8
                os.write(val24 and 0xFF)
                os.write((val24 shr 8) and 0xFF)
                os.write((val24 shr 16) and 0xFF)
            }
            32 -> {
                // Scale 16-bit to 32-bit PCM (shift left by 16 bits)
                val val32 = sample.toInt() shl 16
                os.write(val32 and 0xFF)
                os.write((val32 shr 8) and 0xFF)
                os.write((val32 shr 16) and 0xFF)
                os.write((val32 shr 24) and 0xFF)
            }
            else -> {
                os.write(sample.toInt() and 0xFF)
                os.write((sample.toInt() shr 8) and 0xFF)
            }
        }
    }

    /**
     * Downmixes a 5.1/7.1 or multi-channel WAV file into Stereo/Binaural WAV file.
     */
    fun downmixToStereWav(
        inputFile: File,
        outputFile: File,
        sourceChannelCount: Int,
        sampleRate: Int,
        bitsPerSample: Int
    ): File {
        // Input cache file is always 16-bit PCM (sample size = 2 bytes)
        val inputSampleSize = 2
        val inputFrameSize = sourceChannelCount * inputSampleSize
        
        val fos = FileOutputStream(outputFile)
        writeWavHeader(2, sampleRate, bitsPerSample, 0, fos)
        
        var outputDataSize = 0L
        val outputSampleSize = bitsPerSample / 8

        try {
            inputFile.inputStream().buffered().use { fis ->
                fis.skip(44) // Skip WAV header
                
                val frameBuffer = ByteArray(inputFrameSize)
                
                while (fis.read(frameBuffer) == inputFrameSize) {
                    // Read 16-bit PCM values
                    val channels = ShortArray(sourceChannelCount)
                    for (c in 0 until sourceChannelCount) {
                        val offset = c * inputSampleSize
                        val low = frameBuffer[offset].toInt() and 0xFF
                        val high = frameBuffer[offset + 1].toInt()
                        channels[c] = ((high shl 8) or low).toShort()
                    }

                    val l: Short
                    val r: Short
                    
                    if (sourceChannelCount >= 6) {
                        // 5.1 layout indices: L=0, R=1, C=2, LFE=3, Ls=4, Rs=5
                        val leftVal = channels[0].toFloat()
                        val rightVal = channels[1].toFloat()
                        val centerVal = channels[2].toFloat()
                        val lsVal = channels[4].toFloat()
                        val rsVal = channels[5].toFloat()

                        // ITU-R standard surround-to-stereo downmix:
                        // L_stereo = (L + C * 0.707 + Ls * 0.707) * 0.707
                        // R_stereo = (R + C * 0.707 + Rs * 0.707) * 0.707
                        val mixedL = (leftVal + (centerVal * 0.707f) + (lsVal * 0.707f)) * 0.707f
                        val mixedR = (rightVal + (centerVal * 0.707f) + (rsVal * 0.707f)) * 0.707f

                        l = mixedL.coerceIn(-32768f, 32767f).toInt().toShort()
                        r = mixedR.coerceIn(-32768f, 32767f).toInt().toShort()
                    } else if (sourceChannelCount == 1) {
                        l = channels[0]
                        r = channels[0]
                    } else {
                        l = channels[0]
                        r = channels[1]
                    }

                    // Write Stereo frame
                    writeSample(fos, l, bitsPerSample)
                    writeSample(fos, r, bitsPerSample)
                    outputDataSize += (outputSampleSize * 2)
                }
            }
        } finally {
            fos.close()
            updateWavHeaderSizes(outputFile, outputDataSize)
        }

        return outputFile
    }
}
