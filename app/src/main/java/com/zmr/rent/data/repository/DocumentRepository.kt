package com.zmr.rent.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.zmr.rent.data.dao.DocumentDao
import com.zmr.rent.data.entity.RenterDocument
import com.zmr.rent.data.session.Session
import com.zmr.rent.domain.DocumentSlot
import com.zmr.rent.domain.DocumentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Clock

/** Copies customers' documents into the app's private storage, which other apps cannot read. */
class DocumentRepository(
    private val context: Context,
    private val documentDao: DocumentDao,
    private val session: Session,
    private val clock: Clock,
) {
    fun observe(renterId: Long): Flow<List<RenterDocument>> = documentDao.observeForRenter(renterId)

    /** Returns false if the file could not be read or is larger than [MAX_BYTES]. */
    suspend fun attach(renterId: Long, slot: DocumentSlot, type: DocumentType?, uri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            val name = displayName(uri)
            val target = File(context.filesDir, "documents/$renterId/$slot.${name.substringAfterLast('.', "bin")}")
            try {
                target.parentFile?.mkdirs()
                val input = context.contentResolver.openInputStream(uri) ?: return@withContext false
                val size = input.use { from -> target.outputStream().use { to -> copyLimited(from, to) } }
                if (size > MAX_BYTES) {
                    target.delete()
                    return@withContext false
                }
            } catch (e: IOException) {
                return@withContext false
            } catch (e: SecurityException) {
                return@withContext false
            }
            documentDao.save(RenterDocument(renterId, slot, type, name, target.path, session.userName, clock.millis()))
            true
        }

    private fun copyLimited(from: java.io.InputStream, to: java.io.OutputStream): Long {
        val buffer = ByteArray(8192)
        var total = 0L
        while (total <= MAX_BYTES) {
            val count = from.read(buffer)
            if (count < 0) break
            to.write(buffer, 0, count)
            total += count
        }
        return total
    }

    private fun displayName(uri: Uri): String =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: uri.lastPathSegment.orEmpty()

    companion object {
        const val MAX_BYTES = 10L * 1024 * 1024
    }
}
