package com.yazan.jetoverlay.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Composable for editing a response.
 * Provides a TextField pre-populated with the selected response or empty for custom.
 * Shows "Cancel" and "Use This" buttons.
 * Keyboard appears automatically when this composable is shown.
 *
 * @param currentText The current text being edited
 * @param onTextChanged Callback when text changes
 * @param onCancel Callback when user cancels editing
 * @param onUseThis Callback when user confirms the edited response
 * @param modifier Modifier for the composable
 */
@Composable
fun ResponseEditor(
    currentText: String,
    onTextChanged: (String) -> Unit,
    onCancel: () -> Unit,
    onUseThis: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Request focus and show keyboard when editor appears
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        delay(120)
        keyboardController?.show()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        Text(
            text = "Edit response:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = currentText,
            onValueChange = onTextChanged,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            placeholder = {
                Text(
                    text = "Type your response...",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            textStyle = MaterialTheme.typography.bodyMedium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(8.dp),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    keyboardController?.hide()
                    if (currentText.isNotBlank()) {
                        onUseThis()
                    }
                }
            ),
            minLines = 2,
            maxLines = 4
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Action buttons row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Cancel button
            OutlinedButton(
                onClick = {
                    keyboardController?.hide()
                    onCancel()
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel")
            }

            // Use This button - enabled only when text is not blank
            Button(
                onClick = {
                    keyboardController?.hide()
                    onUseThis()
                },
                modifier = Modifier.weight(1f),
                enabled = currentText.isNotBlank()
            ) {
                Text("Use This")
            }
        }
    }
}

/**
 * Animated wrapper for ResponseEditor that handles enter/exit animations.
 */
@Composable
fun AnimatedResponseEditor(
    isVisible: Boolean,
    currentText: String,
    onTextChanged: (String) -> Unit,
    onCancel: () -> Unit,
    onUseThis: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier
    ) {
        ResponseEditor(
            currentText = currentText,
            onTextChanged = onTextChanged,
            onCancel = onCancel,
            onUseThis = onUseThis
        )
    }
}
