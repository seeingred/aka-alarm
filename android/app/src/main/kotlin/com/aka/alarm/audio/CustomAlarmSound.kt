package com.aka.alarm.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import com.aka.alarm.Tuning
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * The user's own alarm sound, kept as a private copy in app storage.
 *
 * A copy rather than a remembered URI: playback must not depend on a content
 * grant that may have lapsed by 7 a.m., on the media store, or on a file the
 * user has since moved. The copy is probed with [MediaPlayer] before it is
 * accepted, so an undecodable pick is rejected at pick time, not at wake time.
 */
class CustomAlarmSound(private val context: Context) {

    class TooLargeException : IOException("larger than ${Tuning.MAX_CUSTOM_SOUND_BYTES} bytes")

    private val file: File get() = File(context.filesDir, FILE_NAME)

    /** The current custom sound, or null to use the built-in tone. */
    fun current(): File? = file.takeIf { it.isFile && it.length() > 0 }

    /** Copies and verifies the sound at [uri]. On success returns its display name. */
    fun import(uri: Uri): Result<String> {
        val tmp = File(context.filesDir, "$FILE_NAME.tmp")
        return runCatching {
            copy(uri, tmp)
            probe(tmp)
            if (!tmp.renameTo(file)) {
                file.delete()
                if (!tmp.renameTo(file)) throw IOException("could not replace ${file.name}")
            }
            displayName(uri)
        }.onFailure { tmp.delete() }
    }

    fun clear() {
        file.delete()
    }

    private fun copy(uri: Uri, into: File) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw FileNotFoundException(uri.toString())
        input.use { source ->
            into.outputStream().use { sink ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = source.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > Tuning.MAX_CUSTOM_SOUND_BYTES) throw TooLargeException()
                    sink.write(buffer, 0, n)
                }
                if (total == 0L) throw IOException("empty file")
            }
        }
    }

    /** Throws if the platform decoder can't open the file. */
    private fun probe(f: File) {
        val mp = MediaPlayer()
        try {
            mp.setDataSource(f.absolutePath)
            mp.prepare()
        } finally {
            mp.release()
        }
    }

    private fun displayName(uri: Uri): String {
        // System sounds carry a proper title through RingtoneManager ("Argon");
        // documents picked through SAF only have a file name.
        runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() && !it.startsWith("content:") && !it.contains('/') }
            ?.let { return it }
        runCatching {
            context.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null,
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it.substringBeforeLast('.') }
        return "Custom sound"
    }

    private companion object {
        const val FILE_NAME = "alarm_sound"
    }
}
