package com.example.calculator

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calculator.ui.theme.DarkGrayKey
import com.example.calculator.ui.theme.LightGrayKey
import com.example.calculator.ui.theme.OrangeKey
import com.example.calculator.ui.theme.PureBlack
import com.example.calculator.ui.theme.PureWhite
import java.text.DecimalFormatSymbols
import kotlin.math.abs

val CalculatorStateSaver: Saver<CalculatorState, Any> = listSaver(
    save = { state ->
        val historyList = state.history.map { "${it.expression}|||${it.result}" }
        listOf(
            state.display,
            state.accumulator ?: "",
            state.pendingOp?.name ?: "",
            state.isTyping,
            state.lastOp?.name ?: "",
            state.lastOperand ?: "",
            state.isError,
            state.completedEquation ?: "",
            historyList
        )
    },
    restore = { list ->
        @Suppress("UNCHECKED_CAST")
        val rawHistory = list.getOrNull(8) as? List<String> ?: emptyList()
        val historyItems = rawHistory.mapNotNull {
            val parts = it.split("|||")
            if (parts.size == 2) HistoryItem(parts[0], parts[1]) else null
        }

        CalculatorState(
            display = list[0] as String,
            accumulator = (list[1] as String).ifEmpty { null },
            pendingOp = (list[2] as String).ifEmpty { null }?.let { Op.valueOf(it) },
            isTyping = list[3] as Boolean,
            lastOp = (list[4] as String).ifEmpty { null }?.let { Op.valueOf(it) },
            lastOperand = (list[5] as String).ifEmpty { null },
            isError = list[6] as Boolean,
            completedEquation = (list.getOrNull(7) as? String)?.ifEmpty { null },
            history = historyItems
        )
    }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(modifier: Modifier = Modifier) {
    val activity = LocalContext.current as? ComponentActivity

    var state by rememberSaveable(stateSaver = CalculatorStateSaver) {
        mutableStateOf(CalculatorState())
    }

    var showHistory by rememberSaveable { mutableStateOf(false) }

    // Single back button press cleanly finishes the activity or dismisses the history sheet
    BackHandler {
        if (showHistory) {
            showHistory = false
        } else {
            activity?.finish()
        }
    }

    val symbols = remember { DecimalFormatSymbols.getInstance() }
    val formattedDisplay = remember(state.display, state.isError, symbols) {
        CalculatorEngine.formatForDisplay(state, symbols)
    }
    val expressionDisplay = remember(state.accumulator, state.pendingOp, state.isTyping, state.completedEquation, symbols) {
        CalculatorEngine.formatExpression(state, symbols)
    }
    val clearLabel = remember(state.display, state.isTyping, state.isError) {
        CalculatorEngine.clearLabel(state)
    }
    val decimalLabel = remember(symbols) {
        symbols.decimalSeparator.toString()
    }

    val clipboardManager = LocalClipboardManager.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
    ) {
        val screenWidth = maxWidth
        val sidePadding = 14.dp
        val gap = 12.dp
        val diameter = (screenWidth - (sidePadding * 2) - (gap * 3)) / 4
        val zeroPillWidth = (diameter * 2) + gap

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top Bar with History Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(DarkGrayKey)
                        .clickable { showHistory = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .semantics { contentDescription = "calculation history" },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "History",
                        color = PureWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }

            // Display area (Expression line + Main Active number)
            var dragDistance by remember { mutableFloatStateOf(0f) }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 12.dp)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { dragDistance = 0f },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                dragDistance += dragAmount
                                if (abs(dragDistance) > 40f) {
                                    dragDistance = 0f
                                    state = CalculatorEngine.reduce(state, CalculatorAction.Backspace)
                                }
                            }
                        )
                    }
                    .pointerInput(formattedDisplay) {
                        detectTapGestures(
                            onLongPress = {
                                try {
                                    clipboardManager.setText(AnnotatedString(formattedDisplay))
                                } catch (_: Exception) {}
                            }
                        )
                    }
                    .semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = formattedDisplay
                    },
                contentAlignment = Alignment.BottomEnd
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Selected operator & operand expression line
                    if (expressionDisplay.isNotEmpty()) {
                        Text(
                            text = expressionDisplay,
                            color = LightGrayKey,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Main display number with auto-shrink
                    AutoShrinkText(
                        text = formattedDisplay,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Keypad area (Forced LTR and capped font scaling)
            val density = LocalDensity.current
            val fixedDensity = remember(density) {
                Density(density.density, fontScale = 1f)
            }

            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Ltr,
                LocalDensity provides fixedDensity
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = sidePadding, end = sidePadding, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(gap)
                ) {
                    val isDivideSelected = state.pendingOp == Op.DIVIDE && !state.isTyping
                    val isMultiplySelected = state.pendingOp == Op.MULTIPLY && !state.isTyping
                    val isSubtractSelected = state.pendingOp == Op.SUBTRACT && !state.isTyping
                    val isAddSelected = state.pendingOp == Op.ADD && !state.isTyping

                    // Row 1: AC/C | +/- | % | ÷
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        CalculatorButton(
                            text = clearLabel,
                            type = ButtonType.FUNCTION,
                            diameter = diameter,
                            modifier = Modifier.size(diameter),
                            contentDescriptionText = if (clearLabel == "AC") "all clear" else "clear"
                        ) {
                            state = CalculatorEngine.reduce(state, CalculatorAction.Clear)
                        }
                        CalculatorButton(
                            text = "+/−",
                            type = ButtonType.FUNCTION,
                            diameter = diameter,
                            modifier = Modifier.size(diameter),
                            contentDescriptionText = "plus minus"
                        ) {
                            state = CalculatorEngine.reduce(state, CalculatorAction.ToggleSign)
                        }
                        CalculatorButton(
                            text = "%",
                            type = ButtonType.FUNCTION,
                            diameter = diameter,
                            modifier = Modifier.size(diameter),
                            contentDescriptionText = "percent"
                        ) {
                            state = CalculatorEngine.reduce(state, CalculatorAction.Percent)
                        }
                        CalculatorButton(
                            text = "÷",
                            type = ButtonType.OPERATOR,
                            diameter = diameter,
                            isSelected = isDivideSelected,
                            modifier = Modifier.size(diameter),
                            contentDescriptionText = "divide"
                        ) {
                            state = CalculatorEngine.reduce(state, CalculatorAction.Operator(Op.DIVIDE))
                        }
                    }

                    // Row 2: 7 | 8 | 9 | ×
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        CalculatorButton(
                            text = "7",
                            type = ButtonType.DIGIT,
                            diameter = diameter,
                            modifier = Modifier.size(diameter),
                            contentDescriptionText = "seven"
                        ) {
                            state = CalculatorEngine.reduce(state, CalculatorAction.Digit(7))
                        }
                        CalculatorButton(
                            text = "8",
                            type = ButtonType.DIGIT,
                            diameter = diameter,
                            modifier = Modifier.size(diameter),
                            contentDescriptionText = "eight"
                        ) {
                            state = CalculatorEngine.reduce(state, CalculatorAction.Digit(8))
                        }
                        CalculatorButton(
                            text = "9",
                            type = ButtonType.DIGIT,
                            diameter = diameter,
                            modifier = Modifier.size(diameter),
                            contentDescriptionText = "nine"
                        ) {
                            state = CalculatorEngine.reduce(state, CalculatorAction.Digit(9))
                        }
                        CalculatorButton(
                            text = "×",
                            type = ButtonType.OPERATOR,
                            diameter = diameter,
                            isSelected = isMultiplySelected,
                            modifier = Modifier.size(diameter),
                            contentDescriptionText = "multiply"
                        ) {
                            state = CalculatorEngine.reduce(state, CalculatorAction.Operator(Op.MULTIPLY))
                        }
                    }

                    // Row 3: 4 | 5 | 6 | −
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        CalculatorButton(
                            text = "4",
                            type = ButtonType.DIGIT,
                            diameter = diameter,
                            modifier = Modifier.size(diameter),
                            contentDescriptionText = "four"
                        ) {
                            state = CalculatorEngine.reduce(state, CalculatorAction.Digit(4))
                        }
                        CalculatorButton(
                            text = "5",
                            type = ButtonType.DIGIT,
                            diameter = diameter,
                            modifier = Modifier.size(diameter),
                            contentDescriptionText = "five"
                        ) {
                            state = CalculatorEngine.reduce(state, CalculatorAction.Digit(5))
                        }
                        CalculatorButton(
                            text = "6",
                            type = ButtonType.DIGIT,
                            diameter = diameter,
                            modifier = Modifier.size(diameter),
                            contentDescriptionText = "six"
                        ) {
                            state = CalculatorEngine.reduce(state, CalculatorAction.Digit(6))
                        }
                        CalculatorButton(
                            text = "−",
                            type = ButtonType.OPERATOR,
                            diameter = diameter,
                            isSelected = isSubtractSelected,
                            modifier = Modifier.size(diameter),
                            contentDescriptionText = "minus"
                        ) {
                            state = CalculatorEngine.reduce(state, CalculatorAction.Operator(Op.SUBTRACT))
                        }
                    }

                    // Row 4: 1 | 2 | 3 | +
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        CalculatorButton(
                            text = "1",
                            type = ButtonType.DIGIT,
                            diameter = diameter,
                            modifier = Modifier.size(diameter),
                            contentDescriptionText = "one"
                        ) {
                            state = CalculatorEngine.reduce(state, CalculatorAction.Digit(1))
                        }
                        CalculatorButton(
                            text = "2",
                            type = ButtonType.DIGIT,
                            diameter = diameter,
                            modifier = Modifier.size(diameter),
                            contentDescriptionText = "two"
                        ) {
                            state = CalculatorEngine.reduce(state, CalculatorAction.Digit(2))
                        }
                        CalculatorButton(
                            text = "3",
                            type = ButtonType.DIGIT,
                            diameter = diameter,
                            modifier = Modifier.size(diameter),
                            contentDescriptionText = "three"
                        ) {
                            state = CalculatorEngine.reduce(state, CalculatorAction.Digit(3))
                        }
                        CalculatorButton(
                            text = "+",
                            type = ButtonType.OPERATOR,
                            diameter = diameter,
                            isSelected = isAddSelected,
                            modifier = Modifier.size(diameter),
                            contentDescriptionText = "plus"
                        ) {
                            state = CalculatorEngine.reduce(state, CalculatorAction.Operator(Op.ADD))
                        }
                    }

                    // Row 5: 0 (pill) | , (decimal) | =
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        CalculatorButton(
                            text = "0",
                            type = ButtonType.DIGIT,
                            diameter = diameter,
                            isZeroPill = true,
                            modifier = Modifier.size(width = zeroPillWidth, height = diameter),
                            contentDescriptionText = "zero"
                        ) {
                            state = CalculatorEngine.reduce(state, CalculatorAction.Digit(0))
                        }
                        CalculatorButton(
                            text = decimalLabel,
                            type = ButtonType.DIGIT,
                            diameter = diameter,
                            modifier = Modifier.size(diameter),
                            contentDescriptionText = "decimal point"
                        ) {
                            state = CalculatorEngine.reduce(state, CalculatorAction.Decimal)
                        }
                        CalculatorButton(
                            text = "=",
                            type = ButtonType.OPERATOR,
                            diameter = diameter,
                            modifier = Modifier.size(diameter),
                            contentDescriptionText = "equals"
                        ) {
                            state = CalculatorEngine.reduce(state, CalculatorAction.Equals)
                        }
                    }
                }
            }
        }

        // History Modal Bottom Sheet
        if (showHistory) {
            ModalBottomSheet(
                onDismissRequest = { showHistory = false },
                containerColor = Color(0xFF1C1C1E),
                scrimColor = PureBlack.copy(alpha = 0.6f),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "History",
                            color = PureWhite,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (state.history.isNotEmpty()) {
                            TextButton(onClick = {
                                state = CalculatorEngine.reduce(state, CalculatorAction.ClearHistory)
                            }) {
                                Text(text = "Clear", color = OrangeKey, fontSize = 16.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (state.history.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No calculations yet",
                                color = LightGrayKey,
                                fontSize = 16.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(state.history) { item ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF2C2C2E))
                                        .clickable {
                                            state = CalculatorEngine.reduce(
                                                state,
                                                CalculatorAction.RecallHistory(item.result)
                                            )
                                            showHistory = false
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(
                                        text = item.expression,
                                        color = LightGrayKey,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Normal,
                                        textAlign = TextAlign.End
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = item.result,
                                        color = PureWhite,
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Light,
                                        textAlign = TextAlign.End
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun AutoShrinkText(
    text: String,
    modifier: Modifier = Modifier
) {
    // Fast O(1) font size lookup to eliminate multi-pass layout lag on low-end CPUs
    val predictedSize = remember(text.length) {
        when {
            text.length <= 6 -> 88.sp
            text.length == 7 -> 76.sp
            text.length == 8 -> 66.sp
            text.length == 9 -> 56.sp
            text.length == 10 -> 48.sp
            text.length <= 12 -> 42.sp
            else -> 36.sp
        }
    }
    var fontSize by remember(text) { mutableStateOf(predictedSize) }

    Text(
        text = text,
        color = PureWhite,
        fontWeight = FontWeight.Light,
        fontSize = fontSize,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        textAlign = TextAlign.End,
        onTextLayout = { textLayoutResult ->
            if (textLayoutResult.didOverflowWidth && fontSize > 36.sp) {
                fontSize = (fontSize.value - 4).coerceAtLeast(36f).sp
            }
        },
        modifier = modifier
    )
}
