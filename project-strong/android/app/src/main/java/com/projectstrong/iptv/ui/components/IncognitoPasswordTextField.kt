package com.projectstrong.iptv.ui.components

import android.content.Context
import android.os.Build
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.doAfterTextChanged
import com.projectstrong.iptv.ui.theme.*

/**
 * Custom native Android EditText that strictly injects IME_FLAG_NO_PERSONALIZED_LEARNING
 * and vendor-specific private IME options into both its property initialization and its
 * onCreateInputConnection hook.
 *
 * This forces Microsoft SwiftKey (as well as Gboard and Samsung Keyboard) to recognize the
 * input field as private/incognito, displaying the SwiftKey incognito mask badge and
 * completely suppressing word learning, cloud dictionary logging, and predictive caching.
 */
class IncognitoEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle
) : EditText(context, attrs, defStyleAttr) {

    private var isPasswordVisible: Boolean = false
    private var customImeAction: Int = EditorInfo.IME_ACTION_DONE
    var onImeActionCallback: (() -> Unit)? = null

    init {
        background = null // Remove default Android underline
        setPadding(0, 0, 0, 0)
        gravity = Gravity.CENTER_VERTICAL
        maxLines = 1
        setLines(1)
        // DO NOT call isSingleLine = true, as Android's setSingleLine() resets
        // transformationMethod to SingleLineTransformationMethod (revealing plain text).

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        }

        // Enforce initial masked state and incognito flags
        setPasswordVisibility(visible = false, force = true)

        setOnEditorActionListener { _, actionId, _ ->
            if (actionId == customImeAction || actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_NULL) {
                onImeActionCallback?.invoke()
                true
            } else {
                false
            }
        }
    }

    private fun applyImeFlags() {
        imeOptions = customImeAction or
                EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or
                EditorInfo.IME_FLAG_NO_EXTRACT_UI
        privateImeOptions = "com.touchtype.swiftkey.incognito=true,incognito=true,nm,com.samsung.android.inputmethod.incognito=true"
    }

    fun setCustomImeAction(action: Int) {
        if (customImeAction != action) {
            customImeAction = action
            applyImeFlags()
        }
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val connection = super.onCreateInputConnection(outAttrs)
        // Must be applied AFTER super.onCreateInputConnection because super can reset flags!
        outAttrs.imeOptions = outAttrs.imeOptions or
                customImeAction or
                EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or
                EditorInfo.IME_FLAG_NO_EXTRACT_UI
        outAttrs.privateImeOptions = "com.touchtype.swiftkey.incognito=true,incognito=true,nm,com.samsung.android.inputmethod.incognito=true"
        return connection
    }

    fun setPasswordVisibility(visible: Boolean, force: Boolean = false) {
        if (!force && isPasswordVisible == visible && (transformationMethod == null) == visible) return
        isPasswordVisible = visible

        val start = selectionStart
        val end = selectionEnd

        if (visible) {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            transformationMethod = null
        } else {
            // Crucial: set inputType FIRST, then transformationMethod SECOND
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            transformationMethod = PasswordTransformationMethod.getInstance()
        }

        // Retain clean sans-serif font instead of monospace typewriter font
        typeface = android.graphics.Typeface.DEFAULT

        // Re-apply IME options to prevent inputType change from resetting them
        applyImeFlags()

        if (start in 0..length() && end in 0..length()) {
            setSelection(start, end)
        }
    }
}

/**
 * Reusable Jetpack Compose password field designed to visually match OutlinedTextField
 * while delegating the underlying text input to a native Android IncognitoEditText.
 *
 * This reliably triggers Microsoft SwiftKey's native Incognito/Masked mode and prevents
 * password leakage into keyboard prediction caches.
 */
@Composable
fun IncognitoPasswordTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    isError: Boolean = false,
    errorMessage: String? = null,
    imeAction: Int = EditorInfo.IME_ACTION_DONE,
    onImeAction: (() -> Unit)? = null,
    enabled: Boolean = true
) {
    val context = LocalContext.current
    var isFocused by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var editTextRef by remember { mutableStateOf<IncognitoEditText?>(null) }

    val textColorArgb = AppTextPrimary.toArgb()
    val hintColorArgb = AppTextMuted.toArgb()
    val primaryColorArgb = AppPrimary.toArgb()
    val highlightColorArgb = AppPrimary.copy(alpha = 0.35f).toArgb()

    val borderColor = if (isError) AppError else if (isFocused) AppPrimary else AppSurfaceBorder
    val borderWidth = if (isFocused) 1.5.dp else 1.dp

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (!label.isNullOrEmpty()) {
            Text(
                text = label,
                color = if (isError) AppError else if (isFocused) AppPrimary else AppTextSecondary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Medium
            )
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = AppSurfaceVariant,
            border = BorderStroke(borderWidth, borderColor),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    editTextRef?.requestFocus()
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.showSoftInput(editTextRef, InputMethodManager.SHOW_IMPLICIT)
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (isError) AppError else if (isFocused) AppPrimary else AppTextMuted,
                    modifier = Modifier.size(18.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxWidth(),
                        factory = { ctx ->
                            IncognitoEditText(ctx).apply {
                                editTextRef = this
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                setTextColor(textColorArgb)
                                setHintTextColor(hintColorArgb)
                                highlightColor = highlightColorArgb
                                textSize = 15f
                                hint = placeholder ?: ""
                                setText(value)
                                if (value.isNotEmpty()) {
                                    setSelection(value.length)
                                }
                                setCustomImeAction(imeAction)
                                onImeActionCallback = onImeAction
                                setPasswordVisibility(passwordVisible, force = true)
                                setOnFocusChangeListener { _, hasFocus ->
                                    isFocused = hasFocus
                                }
                                doAfterTextChanged { s ->
                                    val str = s?.toString() ?: ""
                                    if (str != value) {
                                        onValueChange(str)
                                    }
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    try {
                                        textCursorDrawable?.setTint(primaryColorArgb)
                                    } catch (_: Exception) {}
                                }
                            }
                        },
                        update = { view ->
                            editTextRef = view
                            view.setTextColor(textColorArgb)
                            view.setHintTextColor(hintColorArgb)
                            view.highlightColor = highlightColorArgb
                            view.hint = placeholder ?: ""
                            view.isEnabled = enabled
                            view.setCustomImeAction(imeAction)
                            view.onImeActionCallback = onImeAction
                            if (view.text.toString() != value) {
                                view.setText(value)
                                view.setSelection(value.length)
                            }
                            view.setPasswordVisibility(passwordVisible)
                        }
                    )
                }

                IconButton(
                    onClick = { passwordVisible = !passwordVisible },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                        tint = if (isFocused) AppPrimary else AppTextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (!errorMessage.isNullOrEmpty()) {
            Text(
                text = errorMessage,
                color = AppError,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
