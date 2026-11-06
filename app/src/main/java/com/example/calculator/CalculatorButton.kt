package com.example.calculator

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calculator.ui.theme.*

enum class ButtonType { DIGIT, FUNCTION, OPERATOR }

private val ButtonShape = RoundedCornerShape(percent = 50)

@Composable
fun CalculatorButton(
    text: String,
    type: ButtonType,
    modifier: Modifier = Modifier,
    diameter: Dp,
    isZeroPill: Boolean = false,
    isSelected: Boolean = false,
    contentDescriptionText: String = text,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var isPressed by remember { mutableStateOf(false) }

    val backgroundColor: Color = when (type) {
        ButtonType.DIGIT -> if (isPressed) DarkGrayKeyPressed else DarkGrayKey
        ButtonType.FUNCTION -> if (isPressed) LightGrayKeyPressed else LightGrayKey
        ButtonType.OPERATOR -> when {
            isSelected -> PureWhite
            isPressed -> OrangeKeyPressed
            else -> OrangeKey
        }
    }

    val textColor: Color = when (type) {
        ButtonType.DIGIT -> PureWhite
        ButtonType.FUNCTION -> PureBlack
        ButtonType.OPERATOR -> if (isSelected) OrangeKey else PureWhite
    }

    val (fontSize, fontWeight) = remember(type) {
        when (type) {
            ButtonType.DIGIT -> 36.sp to FontWeight.Light
            ButtonType.FUNCTION -> 32.sp to FontWeight.Normal
            ButtonType.OPERATOR -> 40.sp to FontWeight.Normal
        }
    }

    Box(
        modifier = modifier
            .clip(ButtonShape)
            .background(backgroundColor, ButtonShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        val released = tryAwaitRelease()
                        isPressed = false
                        if (released) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onClick()
                        }
                    }
                )
            }
            .semantics { contentDescription = contentDescriptionText },
        contentAlignment = if (isZeroPill) Alignment.CenterStart else Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = fontSize,
            fontWeight = fontWeight,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            textAlign = if (isZeroPill) TextAlign.Start else TextAlign.Center,
            modifier = if (isZeroPill) Modifier.padding(start = 28.dp) else Modifier
        )
    }
}
