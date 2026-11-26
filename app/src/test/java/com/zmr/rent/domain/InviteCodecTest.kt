package com.zmr.rent.domain

import com.zmr.rent.data.entity.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InviteCodecTest {

    private val invite = Invite(
        role = UserRole.EMPLOYEE,
        member = Contact("Ravi Kumar", "EMP-102", "9876543210", "ravi@example.com"),
        area = "Lucknow",
        reportsTo = Contact("Suresh Yadav", "MGR-7", "9123456780", "suresh@example.com"),
        payment = PaymentSettings(upiId = "zmrtest@okaxis", payeeName = "ZMR Test & Co", depositPaise = 250_000),
    )

    @Test
    fun `an invite survives being turned into a code and back`() {
        val code = InviteCodec.encode(invite)
        assertTrue(code, code.startsWith("ZMR1."))
        assertEquals(invite, InviteCodec.decode(code))
    }

    @Test
    fun `the code is found inside a whole pasted message, even with line breaks`() {
        val code = InviteCodec.encode(invite)
        val message = "Hello Ravi, you have been added.\n\n${code.substring(0, 20)}\n${code.substring(20)}\nThanks"
        assertEquals(invite, InviteCodec.decode(message))
    }

    @Test
    fun `Hindi names travel safely`() {
        val hindi = invite.copy(member = invite.member.copy(name = "रवि कुमार"))
        assertEquals(hindi, InviteCodec.decode(InviteCodec.encode(hindi)))
    }

    @Test
    fun `a changed or cut code is refused`() {
        val code = InviteCodec.encode(invite)
        val changed = code.replaceRange(10, 11, if (code[10] == 'A') "B" else "A")
        assertNull(InviteCodec.decode(changed))
        assertNull(InviteCodec.decode(code.dropLast(3)))
        assertNull(InviteCodec.decode("hello"))
        assertNull(InviteCodec.decode(""))
    }

    @Test
    fun `an invite link carries the code and download link, and the app reads the code back from it`() {
        val withLinks = invite.copy(
            payment = invite.payment.copy(appDownloadLink = "https://drive.google.com/file/d/abc/view?usp=sharing", invitePageLink = "https://example.github.io/zmr-rent/"),
        )
        val code = InviteCodec.encode(withLinks)
        val link = InviteLink.build(withLinks.payment.invitePageLink, code, withLinks.payment.appDownloadLink)

        assertTrue(link, link.startsWith("https://example.github.io/zmr-rent/#c=ZMR1."))
        assertTrue(link, link.endsWith("&d=https%3A%2F%2Fdrive.google.com%2Ffile%2Fd%2Fabc%2Fview%3Fusp%3Dsharing"))
        assertEquals(withLinks, InviteCodec.decode(link))
        // The invite page opens the app with zmrrent://invite?c=<code>.
        assertEquals(withLinks, InviteCodec.decode("zmrrent://invite?c=$code"))
    }
}
