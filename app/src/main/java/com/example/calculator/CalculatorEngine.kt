package com.example.calculator

import java.io.Serializable
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

sealed interface CalculatorAction {
    data class Digit(val value: Int) : CalculatorAction
    data object Decimal : CalculatorAction
    data class Operator(val op: Op) : CalculatorAction
    data object Equals : CalculatorAction
    data object Percent : CalculatorAction
    data object ToggleSign : CalculatorAction
    data object Clear : CalculatorAction      // AC / C
    data object Backspace : CalculatorAction  // swipe on display
    data object ClearHistory : CalculatorAction
    data class RecallHistory(val value: String) : CalculatorAction
}

enum class Op { ADD, SUBTRACT, MULTIPLY, DIVIDE }

data class HistoryItem(
    val expression: String,
    val result: String
) : Serializable

data class CalculatorState(
    val display: String = "0",
    val accumulator: String? = null,
    val pendingOp: Op? = null,
    val isTyping: Boolean = false,
    val lastOp: Op? = null,
    val lastOperand: String? = null,
    val isError: Boolean = false,
    val history: List<HistoryItem> = emptyList(),
    val completedEquation: String? = null
) : Serializable

object CalculatorEngine {

    fun reduce(state: CalculatorState, action: CalculatorAction): CalculatorState {
        return try {
            reduceInternal(state, action)
        } catch (_: Exception) {
            errorState(state.history)
        }
    }

    private fun reduceInternal(state: CalculatorState, action: CalculatorAction): CalculatorState {
        return when (action) {
            is CalculatorAction.Digit -> handleDigit(state, action.value)
            is CalculatorAction.Decimal -> handleDecimal(state)
            is CalculatorAction.Operator -> handleOperator(state, action.op)
            is CalculatorAction.Equals -> handleEquals(state)
            is CalculatorAction.Percent -> handlePercent(state)
            is CalculatorAction.ToggleSign -> handleToggleSign(state)
            is CalculatorAction.Clear -> handleClear(state)
            is CalculatorAction.Backspace -> handleBackspace(state)
            is CalculatorAction.ClearHistory -> state.copy(history = emptyList())
            is CalculatorAction.RecallHistory -> state.copy(
                display = action.value,
                accumulator = null,
                pendingOp = null,
                isTyping = false,
                lastOp = null,
                lastOperand = null,
                isError = false,
                completedEquation = null
            )
        }
    }

    private fun handleDigit(state: CalculatorState, value: Int): CalculatorState {
        if (state.isError) {
            return CalculatorState(
                display = value.toString(),
                isTyping = true,
                isError = false,
                history = state.history
            )
        }

        if (!state.isTyping) {
            return state.copy(
                display = value.toString(),
                isTyping = true,
                lastOp = null,
                lastOperand = null,
                completedEquation = null
            )
        }

        val digitCount = state.display.count { it.isDigit() }

        return when {
            state.display == "0" -> {
                if (value == 0) state else state.copy(display = value.toString(), completedEquation = null)
            }
            state.display == "-0" -> {
                if (value == 0) state else state.copy(display = "-$value", completedEquation = null)
            }
            digitCount >= 9 -> {
                state // silently ignore extra digits beyond 9
            }
            else -> {
                state.copy(display = state.display + value, completedEquation = null)
            }
        }
    }

    private fun handleDecimal(state: CalculatorState): CalculatorState {
        if (state.isError) {
            return CalculatorState(
                display = "0.",
                isTyping = true,
                isError = false,
                history = state.history
            )
        }

        if (!state.isTyping) {
            return state.copy(
                display = "0.",
                isTyping = true,
                lastOp = null,
                lastOperand = null,
                completedEquation = null
            )
        }

        if (state.display.contains(".")) {
            return state
        }

        return state.copy(display = "${state.display}.", completedEquation = null)
    }

    private fun handleToggleSign(state: CalculatorState): CalculatorState {
        if (state.isError) return state

        if (!state.isTyping) {
            if (state.pendingOp != null) {
                return state.copy(display = "-0", isTyping = true, completedEquation = null)
            }
            if (state.display == "0") return state
            val toggled = if (state.display.startsWith("-")) {
                state.display.substring(1)
            } else {
                "-${state.display}"
            }
            return state.copy(display = toggled, completedEquation = null)
        }

        return when {
            state.display == "0" -> state
            state.display == "-0" -> state.copy(display = "0", completedEquation = null)
            state.display.startsWith("-") -> state.copy(display = state.display.substring(1), completedEquation = null)
            else -> state.copy(display = "-${state.display}", completedEquation = null)
        }
    }

    private fun handleOperator(state: CalculatorState, op: Op): CalculatorState {
        if (state.isError) return state

        if (state.isTyping) {
            if (state.pendingOp != null && state.accumulator != null) {
                val evalResult = evaluate(state.accumulator, state.pendingOp, state.display)
                    ?: return errorState(state.history)

                return state.copy(
                    accumulator = evalResult,
                    display = evalResult,
                    pendingOp = op,
                    isTyping = false,
                    lastOp = null,
                    lastOperand = null,
                    completedEquation = null
                )
            } else {
                val clean = sanitizeInputNumber(state.display)
                return state.copy(
                    accumulator = clean,
                    display = clean,
                    pendingOp = op,
                    isTyping = false,
                    lastOp = null,
                    lastOperand = null,
                    completedEquation = null
                )
            }
        } else {
            if (state.pendingOp != null) {
                return state.copy(pendingOp = op, completedEquation = null)
            } else {
                val clean = sanitizeInputNumber(state.display)
                return state.copy(
                    accumulator = clean,
                    pendingOp = op,
                    isTyping = false,
                    lastOp = null,
                    lastOperand = null,
                    completedEquation = null
                )
            }
        }
    }

    private fun handleEquals(state: CalculatorState): CalculatorState {
        if (state.isError) return state

        val symbols = DecimalFormatSymbols(Locale.US)

        if (state.pendingOp != null) {
            val left = state.accumulator ?: state.display
            val right = state.display
            val result = evaluate(left, state.pendingOp, right) ?: return errorState(state.history)

            val opChar = opSymbol(state.pendingOp)
            val leftFmt = formatNumberString(left, symbols)
            val rightFmt = formatNumberString(right, symbols)
            val resultFmt = formatNumberString(result, symbols)
            val eqStr = "$leftFmt $opChar $rightFmt ="
            val newHistory = listOf(HistoryItem(expression = eqStr, result = resultFmt)) + state.history

            return state.copy(
                display = result,
                accumulator = null,
                pendingOp = null,
                isTyping = false,
                lastOp = state.pendingOp,
                lastOperand = right,
                completedEquation = eqStr,
                history = newHistory.take(50)
            )
        } else if (state.lastOp != null && state.lastOperand != null) {
            val left = state.display
            val right = state.lastOperand
            val result = evaluate(left, state.lastOp, right) ?: return errorState(state.history)

            val opChar = opSymbol(state.lastOp)
            val leftFmt = formatNumberString(left, symbols)
            val rightFmt = formatNumberString(right, symbols)
            val resultFmt = formatNumberString(result, symbols)
            val eqStr = "$leftFmt $opChar $rightFmt ="
            val newHistory = listOf(HistoryItem(expression = eqStr, result = resultFmt)) + state.history

            return state.copy(
                display = result,
                accumulator = null,
                pendingOp = null,
                isTyping = false,
                completedEquation = eqStr,
                history = newHistory.take(50)
            )
        }

        return state
    }

    private fun handlePercent(state: CalculatorState): CalculatorState {
        if (state.isError) return state

        val displayVal = BigDecimal(sanitizeInputNumber(state.display))
        val hundred = BigDecimal("100")
        val mc15 = MathContext(15, RoundingMode.HALF_UP)

        val pctResult = when (state.pendingOp) {
            Op.ADD, Op.SUBTRACT -> {
                val left = BigDecimal(sanitizeInputNumber(state.accumulator ?: state.display))
                left.multiply(displayVal, mc15).divide(hundred, mc15)
            }
            Op.MULTIPLY, Op.DIVIDE -> {
                displayVal.divide(hundred, mc15)
            }
            null -> {
                displayVal.divide(hundred, mc15)
            }
        }

        return state.copy(
            display = formatBigDecimalResult(pctResult),
            isTyping = false,
            completedEquation = null
        )
    }

    private fun handleClear(state: CalculatorState): CalculatorState {
        return if (clearLabel(state) == "C") {
            state.copy(
                display = "0",
                isTyping = false
            )
        } else {
            CalculatorState(history = state.history)
        }
    }

    private fun handleBackspace(state: CalculatorState): CalculatorState {
        if (!state.isTyping || state.isError) return state

        return if (state.display.length <= 1 || (state.display.startsWith("-") && state.display.length == 2)) {
            state.copy(display = "0", isTyping = true)
        } else {
            state.copy(display = state.display.dropLast(1), isTyping = true)
        }
    }

    fun clearLabel(state: CalculatorState): String {
        if (state.isError) return "AC"
        return if (state.isTyping && state.display != "0") "C" else "AC"
    }

    fun formatExpression(state: CalculatorState, symbols: DecimalFormatSymbols): String {
        if (state.isError) return ""

        if (state.accumulator != null && state.pendingOp != null) {
            val left = formatNumberString(state.accumulator, symbols)
            val op = opSymbol(state.pendingOp)
            return if (state.isTyping) {
                val right = formatForDisplay(state, symbols)
                "$left $op $right"
            } else {
                "$left $op"
            }
        }

        return state.completedEquation ?: ""
    }

    fun formatForDisplay(state: CalculatorState, symbols: DecimalFormatSymbols): String {
        if (state.isError || state.display == "Error") return "Error"
        return formatNumberString(state.display, symbols)
    }

    fun formatNumberString(numberStr: String, symbols: DecimalFormatSymbols): String {
        if (numberStr == "Error") return "Error"

        if (numberStr.contains("e", ignoreCase = true)) {
            val parts = numberStr.split(Regex("[eE]"), limit = 2)
            val mantissa = parts[0]
            val exp = parts.getOrElse(1) { "" }
            val formattedMantissa = formatMantissa(mantissa, symbols)
            return "${formattedMantissa}e$exp"
        }

        val isNeg = numberStr.startsWith("-")
        val withoutSign = if (isNeg) numberStr.substring(1) else numberStr

        return if (withoutSign.contains(".")) {
            val dotIndex = withoutSign.indexOf('.')
            val intPart = withoutSign.substring(0, dotIndex)
            val fracPart = withoutSign.substring(dotIndex + 1)
            val groupedInt = groupIntegerPart(intPart, symbols.groupingSeparator)
            (if (isNeg) "-" else "") + groupedInt + symbols.decimalSeparator + fracPart
        } else {
            val groupedInt = groupIntegerPart(withoutSign, symbols.groupingSeparator)
            (if (isNeg) "-" else "") + groupedInt
        }
    }

    private fun opSymbol(op: Op): String = when (op) {
        Op.ADD -> "+"
        Op.SUBTRACT -> "−"
        Op.MULTIPLY -> "×"
        Op.DIVIDE -> "÷"
    }

    private fun formatMantissa(mantissa: String, symbols: DecimalFormatSymbols): String {
        val isNeg = mantissa.startsWith("-")
        val withoutSign = if (isNeg) mantissa.substring(1) else mantissa
        return if (withoutSign.contains(".")) {
            val dotIndex = withoutSign.indexOf('.')
            val intPart = withoutSign.substring(0, dotIndex)
            val fracPart = withoutSign.substring(dotIndex + 1)
            val groupedInt = groupIntegerPart(intPart, symbols.groupingSeparator)
            (if (isNeg) "-" else "") + groupedInt + symbols.decimalSeparator + fracPart
        } else {
            val groupedInt = groupIntegerPart(withoutSign, symbols.groupingSeparator)
            (if (isNeg) "-" else "") + groupedInt
        }
    }

    private fun groupIntegerPart(intStr: String, groupingSeparator: Char): String {
        if (intStr.isEmpty()) return ""
        val sb = StringBuilder()
        val len = intStr.length
        for (i in 0 until len) {
            if (i > 0 && (len - i) % 3 == 0) {
                sb.append(groupingSeparator)
            }
            sb.append(intStr[i])
        }
        return sb.toString()
    }

    private fun sanitizeInputNumber(input: String): String {
        var s = input
        if (s == "-0" || s == "-") return "0"
        if (s.endsWith(".")) s = s.dropLast(1)
        if (s.isEmpty()) return "0"
        return s
    }

    private fun evaluate(leftStr: String, op: Op, rightStr: String): String? {
        val a = BigDecimal(sanitizeInputNumber(leftStr))
        val b = BigDecimal(sanitizeInputNumber(rightStr))
        val mc15 = MathContext(15, RoundingMode.HALF_UP)
        val result = when (op) {
            Op.ADD -> a.add(b, mc15)
            Op.SUBTRACT -> a.subtract(b, mc15)
            Op.MULTIPLY -> a.multiply(b, mc15)
            Op.DIVIDE -> {
                if (b.compareTo(BigDecimal.ZERO) == 0) return null
                a.divide(b, mc15)
            }
        }
        return formatBigDecimalResult(result)
    }

    private fun formatBigDecimalResult(bd: BigDecimal): String {
        if (bd.compareTo(BigDecimal.ZERO) == 0) return "0"

        val stripped = bd.stripTrailingZeros()
        val absVal = stripped.abs()
        val maxNormal = BigDecimal("1e9")
        val minNormal = BigDecimal("1e-8")

        val isScientific = absVal.compareTo(maxNormal) >= 0 || absVal.compareTo(minNormal) < 0

        if (!isScientific) {
            val rounded9 = stripped.round(MathContext(9, RoundingMode.HALF_UP)).stripTrailingZeros()
            val roundedAbs = rounded9.abs()
            if (roundedAbs.compareTo(maxNormal) < 0 && roundedAbs.compareTo(minNormal) >= 0) {
                return rounded9.toPlainString()
            }
        }

        val rounded6 = stripped.round(MathContext(6, RoundingMode.HALF_UP))
        val usSymbols = DecimalFormatSymbols(Locale.US)
        val df = DecimalFormat("0.#####E0", usSymbols)
        val formatted = df.format(rounded6)
        val eIndex = formatted.indexOf('E')
        val mantissa = formatted.substring(0, eIndex)
        val expInt = formatted.substring(eIndex + 1).toInt()
        val expStr = if (expInt >= 0) "+$expInt" else "$expInt"
        return "${mantissa}e$expStr"
    }

    private fun errorState(existingHistory: List<HistoryItem> = emptyList()): CalculatorState = CalculatorState(
        display = "Error",
        accumulator = null,
        pendingOp = null,
        isTyping = false,
        lastOp = null,
        lastOperand = null,
        isError = true,
        history = existingHistory,
        completedEquation = null
    )
}
