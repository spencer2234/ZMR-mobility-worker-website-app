package com.zmr.rent.ui.login

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.zmr.rent.R
import com.zmr.rent.data.repository.AccountError
import com.zmr.rent.domain.IndianPhone
import com.zmr.rent.ui.components.FormTextField

/** The details typed for a person: name, employee ID, mobile number and email, and optionally an area. */
data class PersonForm(
    val name: String = "",
    val employeeCode: String = "",
    val phone: String = "",
    val email: String = "",
    val area: String = "",
)

/** The shared name / employee ID / phone / email boxes, used for the CEO and for invites. */
@Composable
fun PersonFields(
    form: PersonForm,
    errors: Set<AccountError>,
    onChange: (PersonForm) -> Unit,
    showArea: Boolean,
) {
    val next = KeyboardOptions(imeAction = ImeAction.Next)
    FormTextField(
        value = form.name,
        onValueChange = { onChange(form.copy(name = it.take(40))) },
        labelRes = R.string.field_person_name,
        errorRes = errors.messageFor(AccountError.NAME_REQUIRED, AccountError.NAME_TAKEN),
        keyboardOptions = next.copy(capitalization = KeyboardCapitalization.Words),
    )
    FormTextField(
        value = form.employeeCode,
        onValueChange = { onChange(form.copy(employeeCode = it.uppercase().take(20))) },
        labelRes = R.string.field_employee_code,
        errorRes = errors.messageFor(AccountError.CODE_INVALID, AccountError.CODE_TAKEN),
        keyboardOptions = next.copy(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
    )
    FormTextField(
        value = form.phone,
        onValueChange = { onChange(form.copy(phone = IndianPhone.normalize(it))) },
        labelRes = R.string.field_phone,
        errorRes = errors.messageFor(AccountError.PHONE_INVALID),
        prefix = IndianPhone.COUNTRY_CODE + " ",
        keyboardOptions = next.copy(keyboardType = KeyboardType.Number),
    )
    FormTextField(
        value = form.email,
        onValueChange = { onChange(form.copy(email = it.trim().take(100))) },
        labelRes = R.string.field_email,
        errorRes = errors.messageFor(AccountError.EMAIL_INVALID),
        keyboardOptions = next.copy(keyboardType = KeyboardType.Email, autoCorrectEnabled = false),
    )
    if (showArea) {
        FormTextField(
            value = form.area,
            onValueChange = { onChange(form.copy(area = it.take(40))) },
            labelRes = R.string.field_area,
            errorRes = errors.messageFor(AccountError.AREA_REQUIRED),
            keyboardOptions = next.copy(capitalization = KeyboardCapitalization.Words),
        )
    }
}
