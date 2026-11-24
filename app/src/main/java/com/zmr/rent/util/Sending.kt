package com.zmr.rent.util

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import com.zmr.rent.data.repository.ReportRepository
import com.zmr.rent.domain.IndianPhone

/**
 * Sends files and messages through WhatsApp or email. Nothing goes through a ZMR server: the
 * phone's own WhatsApp and email apps do the sending, so the person sees and confirms each send.
 */
object Sending {

    private val WHATSAPP_PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b")

    /**
     * Opens WhatsApp with the Excel file attached. When [toPhone] is set, WhatsApp is asked to
     * open that person's chat directly; if that is not possible (no WhatsApp, or a WhatsApp that
     * ignores the request), the person picks the chat or app themselves.
     */
    fun fileByWhatsApp(context: Context, files: List<Uri>, toPhone: String?, message: String, chooserTitle: String) {
        val send = fileIntent(files, message)
        if (toPhone != null && IndianPhone.isValid(toPhone)) {
            // Not an official WhatsApp feature, but widely supported: opens the given chat.
            send.putExtra("jid", "91$toPhone@s.whatsapp.net")
        }
        for (pkg in WHATSAPP_PACKAGES) {
            try {
                context.startActivity(Intent(send).setPackage(pkg))
                return
            } catch (e: ActivityNotFoundException) {
                // Try the next WhatsApp, then the normal share sheet.
            }
        }
        send.removeExtra("jid")
        context.startActivity(Intent.createChooser(send, chooserTitle))
    }

    /** Opens an email app with [to], the subject and message filled in and the Excel file attached. */
    fun fileByEmail(context: Context, files: List<Uri>, to: String?, subject: String, message: String, chooserTitle: String) {
        val send = fileIntent(files, message).putExtra(Intent.EXTRA_SUBJECT, subject)
        if (!to.isNullOrEmpty()) send.putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
        context.startActivity(Intent.createChooser(send, chooserTitle))
    }

    /** Opens WhatsApp on [toPhone]'s chat with [message] typed in (WhatsApp's official link). */
    fun textByWhatsApp(context: Context, toPhone: String, message: String) {
        val link = ("https://wa.me/91$toPhone?text=" + Uri.encode(message)).toUri()
        context.startActivity(Intent(Intent.ACTION_VIEW, link))
    }

    /** Opens an email app with a new message to [to]. */
    fun textByEmail(context: Context, to: String, subject: String, message: String, chooserTitle: String) {
        val email = Intent(Intent.ACTION_SENDTO, "mailto:".toUri())
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, message)
        context.startActivity(Intent.createChooser(email, chooserTitle))
    }

    /** One attachment, or several (the Excel plus the documents zip). */
    private fun fileIntent(files: List<Uri>, message: String): Intent {
        val send = if (files.size == 1) {
            Intent(Intent.ACTION_SEND).setType(ReportRepository.XLSX_MIME).putExtra(Intent.EXTRA_STREAM, files.single())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(files))
        }
        val clip = ClipData.newRawUri("", files.first())
        files.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        return send.putExtra(Intent.EXTRA_TEXT, message).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply { clipData = clip }
    }
}
