package com.zmr.rent.ui

import androidx.annotation.StringRes
import com.zmr.rent.R
import com.zmr.rent.data.entity.CustomerType
import com.zmr.rent.data.entity.HoldReason
import com.zmr.rent.data.entity.PaymentMethod
import com.zmr.rent.data.entity.PaymentType
import com.zmr.rent.data.entity.RenterStatus
import com.zmr.rent.data.entity.UserRole
import com.zmr.rent.domain.DocumentSlot
import com.zmr.rent.domain.DocumentType
import java.time.DayOfWeek

// The words shown for each stored value. Kept as string resources so they can be translated.

@StringRes
fun DayOfWeek.shortLabelRes(): Int = when (this) {
    DayOfWeek.MONDAY -> R.string.day_mon
    DayOfWeek.TUESDAY -> R.string.day_tue
    DayOfWeek.WEDNESDAY -> R.string.day_wed
    DayOfWeek.THURSDAY -> R.string.day_thu
    DayOfWeek.FRIDAY -> R.string.day_fri
    DayOfWeek.SATURDAY -> R.string.day_sat
    DayOfWeek.SUNDAY -> R.string.day_sun
}

@StringRes
fun CustomerType.labelRes(): Int = when (this) {
    CustomerType.RENTER -> R.string.customer_renter
    CustomerType.LESSEE -> R.string.customer_lessee
}

@StringRes
fun DocumentSlot.labelRes(): Int = when (this) {
    DocumentSlot.PHOTOGRAPH -> R.string.doc_slot_photo
    DocumentSlot.DRIVING_LICENCE -> R.string.doc_slot_licence
    DocumentSlot.ID_PROOF -> R.string.doc_slot_id
    DocumentSlot.ADDRESS_PROOF -> R.string.doc_slot_address
    DocumentSlot.PARENT_ID_PROOF -> R.string.doc_slot_parent_id
    DocumentSlot.PARENT_ADDRESS_PROOF -> R.string.doc_slot_parent_address
}

@StringRes
fun DocumentType.labelRes(): Int = when (this) {
    DocumentType.BIRTH_CERTIFICATE -> R.string.doc_birth_certificate
    DocumentType.SCHOOL_CERTIFICATE -> R.string.doc_school_certificate
    DocumentType.PAN_CARD -> R.string.doc_pan_card
    DocumentType.DRIVING_LICENCE -> R.string.doc_driving_licence
    DocumentType.VOTER_ID -> R.string.doc_voter_id
    DocumentType.WATER_BILL -> R.string.doc_water_bill
    DocumentType.TELEPHONE_BILL -> R.string.doc_telephone_bill
    DocumentType.ELECTRICITY_BILL -> R.string.doc_electricity_bill
    DocumentType.INCOME_TAX_ORDER -> R.string.doc_income_tax
    DocumentType.ELECTION_PHOTO_ID -> R.string.doc_voter_id
    DocumentType.GAS_CONNECTION -> R.string.doc_gas
    DocumentType.EMPLOYER_CERTIFICATE -> R.string.doc_employer
    DocumentType.SPOUSE_PASSPORT -> R.string.doc_spouse_passport
    DocumentType.PARENT_PASSPORT -> R.string.doc_parent_passport
    DocumentType.AADHAAR_CARD -> R.string.doc_aadhaar
    DocumentType.RENT_AGREEMENT -> R.string.doc_rent_agreement
    DocumentType.BANK_PASSBOOK -> R.string.doc_passbook
}

@StringRes
fun RenterStatus.labelRes(): Int = when (this) {
    RenterStatus.ACTIVE -> R.string.status_active
    RenterStatus.ON_HOLD -> R.string.status_on_hold
}

@StringRes
fun UserRole.labelRes(): Int = when (this) {
    UserRole.EMPLOYEE -> R.string.role_employee
    UserRole.MANAGER -> R.string.role_manager
    UserRole.CEO -> R.string.role_ceo
}

@StringRes
fun PaymentMethod.labelRes(): Int = when (this) {
    PaymentMethod.UPI -> R.string.method_upi
    PaymentMethod.BANK_TRANSFER -> R.string.method_bank
    PaymentMethod.CASH -> R.string.method_cash
}

@StringRes
fun PaymentType.labelRes(): Int = when (this) {
    PaymentType.RENT -> R.string.field_weekly_rent
    PaymentType.DEPOSIT -> R.string.payment_deposit
    PaymentType.ADVANCE -> R.string.payment_advance
}

@StringRes
fun HoldReason.labelRes(): Int = when (this) {
    HoldReason.MISSED_PAYMENTS -> R.string.hold_reason_missed_payments
    HoldReason.REPAIR -> R.string.hold_reason_repair
    HoldReason.RETURNED -> R.string.hold_reason_returned
    HoldReason.OTHER -> R.string.hold_reason_other
}
