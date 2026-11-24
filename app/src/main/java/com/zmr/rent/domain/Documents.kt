package com.zmr.rent.domain

/**
 * The document types ZMR accepts, from the Passport Seva lists: ID proof uses items 1, 2, 3, 5
 * and 6 of the date-of-birth list; address proof uses all 12 items of the address list.
 * Stored in the database by name; do not rename entries.
 */
enum class DocumentType {
    BIRTH_CERTIFICATE,
    SCHOOL_CERTIFICATE,
    PAN_CARD,
    DRIVING_LICENCE,
    VOTER_ID,

    WATER_BILL,
    TELEPHONE_BILL,
    ELECTRICITY_BILL,
    INCOME_TAX_ORDER,
    ELECTION_PHOTO_ID,
    GAS_CONNECTION,
    EMPLOYER_CERTIFICATE,
    SPOUSE_PASSPORT,
    PARENT_PASSPORT,

    /** Attach a masked copy only (last 4 digits visible). The app never stores the number. */
    AADHAAR_CARD,
    RENT_AGREEMENT,
    BANK_PASSBOOK,
    ;

    companion object {
        val ID_PROOFS = listOf(BIRTH_CERTIFICATE, SCHOOL_CERTIFICATE, PAN_CARD, DRIVING_LICENCE, VOTER_ID)
        val ADDRESS_PROOFS = entries - ID_PROOFS.toSet()
    }
}

/** The six documents a customer can attach. [choices] is empty when there is only one kind. */
enum class DocumentSlot(val choices: List<DocumentType>) {
    PHOTOGRAPH(emptyList()),
    DRIVING_LICENCE(emptyList()),
    ID_PROOF(DocumentType.ID_PROOFS),
    ADDRESS_PROOF(DocumentType.ADDRESS_PROOFS),
    PARENT_ID_PROOF(DocumentType.ID_PROOFS),
    PARENT_ADDRESS_PROOF(DocumentType.ADDRESS_PROOFS),
}
