package com.zmr.rent.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/**
 * The app's standard one-line text box: large text, a label, and an error shown underneath
 * in red when [errorRes] is set (otherwise the optional [hintRes]).
 */
@Composable
fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes labelRes: Int,
    @StringRes errorRes: Int?,
    keyboardOptions: KeyboardOptions,
    modifier: Modifier = Modifier,
    prefix: String? = null,
    @StringRes hintRes: Int? = null,
) {
    val supportingRes = errorRes ?: hintRes
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(labelRes)) },
        prefix = prefix?.let { { Text(it, style = MaterialTheme.typography.bodyLarge) } },
        isError = errorRes != null,
        supportingText = supportingRes?.let { { Text(stringResource(it)) } },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = keyboardOptions,
        modifier = modifier.fillMaxWidth(),
    )
}
