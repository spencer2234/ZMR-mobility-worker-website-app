package com.zmr.rent.domain

import com.zmr.rent.data.entity.UserRole
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Base64
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Everything a new person's phone needs to set up their account: who they are, who they send
 * reports to, and where renters pay. A manager makes one for each employee, the CEO for each
 * manager, and it travels as a short code in a WhatsApp message or email.
 */
data class Invite(
    val role: UserRole,
    val member: Contact,
    /** City or area the person works in. */
    val area: String,
    /** The manager (for an employee) or CEO (for a manager) who receives their reports. */
    val reportsTo: Contact,
    val payment: PaymentSettings,
)

/**
 * Turns an [Invite] into text like "ZMR1.eJx...Q.1a2b3c4d" and back. The text is compressed
 * and carries a checksum, so a code cut short or mistyped is refused instead of creating a
 * wrong account. It is not encrypted or signed: anyone holding the code can set up the account,
 * so it should only be sent to the person it is for.
 */
object InviteCodec {

    private const val PREFIX = "ZMR1"
    private const val MAX_DECODED_BYTES = 16 * 1024
    private val CODE = Regex("""ZMR1\.([A-Za-z0-9_-]+)\.([0-9a-f]{8})""")

    fun encode(invite: Invite): String {
        val fields = linkedMapOf(
            "role" to invite.role.name,
            "name" to invite.member.name,
            "code" to invite.member.employeeCode,
            "phone" to invite.member.phone,
            "email" to invite.member.email,
            "area" to invite.area,
            "toName" to invite.reportsTo.name,
            "toCode" to invite.reportsTo.employeeCode,
            "toPhone" to invite.reportsTo.phone,
            "toEmail" to invite.reportsTo.email,
            "upi" to invite.payment.upiId,
            "payee" to invite.payment.payeeName,
            "bankName" to invite.payment.bankAccountName,
            "bankNo" to invite.payment.bankAccountNumber,
            "ifsc" to invite.payment.bankIfsc,
            "bank" to invite.payment.bankName,
            "deposit" to invite.payment.depositPaise.toString(),
            "advance" to invite.payment.advancePaise.toString(),
        )
        val text = fields.entries.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, "UTF-8")}" }
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(deflate(text.toByteArray(Charsets.UTF_8)))
        return "$PREFIX.$payload.${checksum(payload)}"
    }

    /**
     * Finds an invite code anywhere in [text], so the whole WhatsApp message can be pasted, and
     * reads it. Returns null when there is no valid code.
     */
    fun decode(text: String): Invite? {
        val match = CODE.find(text.filterNot { it.isWhitespace() }) ?: return null
        val (payload, sum) = match.destructured
        if (checksum(payload) != sum) return null
        return try {
            val bytes = inflate(Base64.getUrlDecoder().decode(payload)) ?: return null
            val fields = String(bytes, Charsets.UTF_8).split("&").associate { pair ->
                val key = pair.substringBefore("=")
                key to URLDecoder.decode(pair.substringAfter("=", ""), "UTF-8")
            }
            fun field(key: String) = fields[key].orEmpty()
            Invite(
                role = UserRole.valueOf(field("role")),
                member = Contact(field("name"), field("code"), field("phone"), field("email")),
                area = field("area"),
                reportsTo = Contact(field("toName"), field("toCode"), field("toPhone"), field("toEmail")),
                payment = PaymentSettings(
                    upiId = field("upi"),
                    payeeName = field("payee"),
                    bankAccountName = field("bankName"),
                    bankAccountNumber = field("bankNo"),
                    bankIfsc = field("ifsc"),
                    bankName = field("bank"),
                    depositPaise = field("deposit").toLongOrNull() ?: PaymentSettings.DEFAULT_DEPOSIT_PAISE,
                    advancePaise = field("advance").toLongOrNull() ?: PaymentSettings.DEFAULT_ADVANCE_PAISE,
                ),
            ).takeIf { it.member.name.isNotBlank() && it.member.employeeCode.isNotBlank() }
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun checksum(payload: String): String =
        CRC32().apply { update(payload.toByteArray(Charsets.US_ASCII)) }.value.toString(16).padStart(8, '0')

    private fun deflate(bytes: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        try {
            deflater.setInput(bytes)
            deflater.finish()
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
            return out.toByteArray()
        } finally {
            deflater.end()
        }
    }

    /** Returns null for damaged data or anything implausibly large. */
    private fun inflate(bytes: ByteArray): ByteArray? {
        val inflater = Inflater()
        try {
            inflater.setInput(bytes)
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) return null
                out.write(buffer, 0, count)
                if (out.size() > MAX_DECODED_BYTES) return null
            }
            return out.toByteArray()
        } catch (e: java.util.zip.DataFormatException) {
            return null
        } finally {
            inflater.end()
        }
    }
}
