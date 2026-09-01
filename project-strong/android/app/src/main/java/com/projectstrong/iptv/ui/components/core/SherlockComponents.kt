package com.projectstrong.iptv.ui.components.core

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.projectstrong.iptv.data.AppThemeMode
import com.projectstrong.iptv.data.SettingsManager

@Composable
fun SherlockButton(
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.medium,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    content: @Composable RowScope.() -> Unit
) {
    val theme = SettingsManager.currentTheme
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(stiffness = 400f, dampingRatio = 0.6f),
        label = "buttonScale"
    )

    val applyScale = theme == AppThemeMode.MACOS_LIQUID_GLASS || theme == AppThemeMode.CINEMATIC_DARK
    
    Button(
        onClick = onClick,
        modifier = if (applyScale) modifier.scale(scale) else modifier,
        enabled = enabled,
        border = border,
        contentPadding = contentPadding,
        shape = shape,
        colors = colors,
        interactionSource = if (applyScale) interactionSource else remember { MutableInteractionSource() },
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SherlockCard(
    border: BorderStroke? = null,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val theme = SettingsManager.currentTheme
    
    val blurModifier = if (theme == AppThemeMode.MACOS_LIQUID_GLASS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier.graphicsLayer {
            renderEffect = android.graphics.RenderEffect.createBlurEffect(
                40f, 40f, android.graphics.Shader.TileMode.CLAMP
            ).asComposeRenderEffect()
            alpha = 0.9f
        }
    } else {
        Modifier
    }

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier.then(blurModifier),
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
            content = content
        )
    } else {
        Card(
            modifier = modifier.then(blurModifier),
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
            content = content
        )
    }
}

@Composable
fun SherlockTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    shape: Shape = MaterialTheme.shapes.small,
    colors: TextFieldColors? = null
) {
    val theme = SettingsManager.currentTheme
    
    val finalShape = when (theme) {
        AppThemeMode.ROBINHOOD_NEON -> androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
        AppThemeMode.MACOS_LIQUID_GLASS -> androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
        else -> shape
    }
    
    val finalColors = colors ?: OutlinedTextFieldDefaults.colors()

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        shape = finalShape,
        colors = finalColors
    )
}


@Composable
fun SherlockLinearProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    val theme = SettingsManager.currentTheme
    
    val glowModifier = if (theme == AppThemeMode.CINEMATIC_DARK || theme == AppThemeMode.ROBINHOOD_NEON) {
        Modifier.graphicsLayer {
            shadowElevation = 12f
            spotShadowColor = color
            ambientShadowColor = color
        }
    } else Modifier

    LinearProgressIndicator(
        progress = progress,
        modifier = modifier.then(glowModifier),
        color = color,
        trackColor = trackColor
    )
}

@Composable
fun SherlockLinearProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    val theme = SettingsManager.currentTheme
    
    val glowModifier = if (theme == AppThemeMode.CINEMATIC_DARK || theme == AppThemeMode.ROBINHOOD_NEON) {
        Modifier.graphicsLayer {
            shadowElevation = 12f
            spotShadowColor = color
            ambientShadowColor = color
        }
    } else Modifier

    LinearProgressIndicator(
        progress = progress,
        modifier = modifier.then(glowModifier),
        color = color,
        trackColor = trackColor
    )
}

@Composable
fun SherlockLinearProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    val theme = SettingsManager.currentTheme
    
    val glowModifier = if (theme == AppThemeMode.CINEMATIC_DARK || theme == AppThemeMode.ROBINHOOD_NEON) {
        Modifier.graphicsLayer {
            shadowElevation = 12f
            spotShadowColor = color
            ambientShadowColor = color
        }
    } else Modifier

    LinearProgressIndicator(
        modifier = modifier.then(glowModifier),
        color = color,
        trackColor = trackColor
    )
}

@Composable
fun SherlockCircularProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: androidx.compose.ui.unit.Dp = 4.dp
) {
    val theme = SettingsManager.currentTheme
    
    val glowModifier = if (theme == AppThemeMode.CINEMATIC_DARK || theme == AppThemeMode.ROBINHOOD_NEON) {
        Modifier.graphicsLayer {
            shadowElevation = 16f
            spotShadowColor = color
            ambientShadowColor = color
        }
    } else Modifier

    CircularProgressIndicator(
        progress = progress,
        modifier = modifier.then(glowModifier),
        color = color,
        strokeWidth = strokeWidth
    )
}

@Composable
fun SherlockCircularProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: androidx.compose.ui.unit.Dp = 4.dp
) {
    val theme = SettingsManager.currentTheme
    
    val glowModifier = if (theme == AppThemeMode.CINEMATIC_DARK || theme == AppThemeMode.ROBINHOOD_NEON) {
        Modifier.graphicsLayer {
            shadowElevation = 16f
            spotShadowColor = color
            ambientShadowColor = color
        }
    } else Modifier

    CircularProgressIndicator(
        progress = progress,
        modifier = modifier.then(glowModifier),
        color = color,
        strokeWidth = strokeWidth
    )
}

@Composable
fun SherlockCircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: androidx.compose.ui.unit.Dp = 4.dp
) {
    val theme = SettingsManager.currentTheme
    
    val glowModifier = if (theme == AppThemeMode.CINEMATIC_DARK || theme == AppThemeMode.ROBINHOOD_NEON) {
        Modifier.graphicsLayer {
            shadowElevation = 16f
            spotShadowColor = color
            ambientShadowColor = color
        }
    } else Modifier

    CircularProgressIndicator(
        modifier = modifier.then(glowModifier),
        color = color,
        strokeWidth = strokeWidth
    )
}
