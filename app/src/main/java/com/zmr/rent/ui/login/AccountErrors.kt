package com.zmr.rent.ui.login

import androidx.annotation.StringRes
import com.zmr.rent.R
import com.zmr.rent.data.repository.AccountError

/** The message shown for each account problem. */
@StringRes
fun AccountError.messageRes(): Int = when (this) {
    AccountError.NAME_REQUIRED -> R.string.error_user_name_required
    AccountError.NAME_TAKEN -> R.string.error_user_name_taken
    AccountError.CODE_INVALID -> R.string.error_code_invalid
    AccountError.CODE_TAKEN -> R.string.error_code_taken
    AccountError.PHONE_INVALID -> R.string.error_phone_invalid
    AccountError.EMAIL_INVALID -> R.string.error_email_invalid
    AccountError.AREA_REQUIRED -> R.string.error_area_required
    AccountError.PIN_FORMAT -> R.string.error_pin_format
    AccountError.PIN_MISMATCH -> R.string.error_pin_mismatch
    AccountError.PIN_WRONG -> R.string.error_pin_wrong
    AccountError.INVITE_INVALID -> R.string.error_invite_invalid
    AccountError.CEO_EXISTS -> R.string.error_ceo_exists
    AccountError.NOT_ALLOWED -> R.string.error_save_failed
}

/** The message for the first of [errors] that belongs to a field, or null. */
@StringRes
fun Set<AccountError>.messageFor(vararg field: AccountError): Int? = field.firstOrNull { it in this }?.messageRes()
