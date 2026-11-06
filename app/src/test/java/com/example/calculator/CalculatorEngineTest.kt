package com.example.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.DecimalFormatSymbols
import java.util.Locale

class CalculatorEngineTest {

    private val ptBrSymbols = DecimalFormatSymbols(Locale("pt", "BR"))
    private val enUsSymbols = DecimalFormatSymbols(Locale.US)

    private fun press(state: CalculatorState, vararg actions: CalculatorAction): CalculatorState {
        return actions.fold(state) { current, action ->
            CalculatorEngine.reduce(current, action)
        }
    }

    private fun press(vararg actions: CalculatorAction): CalculatorState {
        return press(CalculatorState(), *actions)
    }

    private fun typeNumber(number: String): List<CalculatorAction> {
        return number.map { c ->
            when (c) {
                '.' -> CalculatorAction.Decimal
                in '0'..'9' -> CalculatorAction.Digit(c.digitToInt())
                else -> throw IllegalArgumentException("Unsupported char: $c")
            }
        }
    }

    @Test
    fun testOnePlusTwoEqualsThree() {
        val state = press(
            CalculatorAction.Digit(1),
            CalculatorAction.Operator(Op.ADD),
            CalculatorAction.Digit(2),
            CalculatorAction.Equals
        )
        assertEquals("3", state.display)
        assertEquals("3", CalculatorEngine.formatForDisplay(state, ptBrSymbols))
        assertEquals("3", CalculatorEngine.formatForDisplay(state, enUsSymbols))
    }

    @Test
    fun testPointOnePlusPointTwoEqualsPointThree() {
        val actions = mutableListOf<CalculatorAction>().apply {
            addAll(typeNumber("0.1"))
            add(CalculatorAction.Operator(Op.ADD))
            addAll(typeNumber("0.2"))
            add(CalculatorAction.Equals)
        }
        val state = press(*actions.toTypedArray())
        assertEquals("0.3", state.display)
        assertEquals("0,3", CalculatorEngine.formatForDisplay(state, ptBrSymbols))
        assertEquals("0.3", CalculatorEngine.formatForDisplay(state, enUsSymbols))
    }

    @Test
    fun testLeftToRightChainingWithoutPrecedence() {
        // 2 + 3 × 4 = -> 20 (not 14)
        val state = press(
            CalculatorAction.Digit(2),
            CalculatorAction.Operator(Op.ADD),
            CalculatorAction.Digit(3),
            CalculatorAction.Operator(Op.MULTIPLY),
            CalculatorAction.Digit(4),
            CalculatorAction.Equals
        )
        assertEquals("20", state.display)
        assertEquals("20", CalculatorEngine.formatForDisplay(state, ptBrSymbols))
    }

    @Test
    fun testRepeatEqualsAddition() {
        // 5 + 2 = = = -> 7, 9, 11
        var state = press(
            CalculatorAction.Digit(5),
            CalculatorAction.Operator(Op.ADD),
            CalculatorAction.Digit(2),
            CalculatorAction.Equals
        )
        assertEquals("7", state.display)

        state = CalculatorEngine.reduce(state, CalculatorAction.Equals)
        assertEquals("9", state.display)

        state = CalculatorEngine.reduce(state, CalculatorAction.Equals)
        assertEquals("11", state.display)
    }

    @Test
    fun testRepeatEqualsMultiplication() {
        // 3 × 2 = = -> 6, 12
        var state = press(
            CalculatorAction.Digit(3),
            CalculatorAction.Operator(Op.MULTIPLY),
            CalculatorAction.Digit(2),
            CalculatorAction.Equals
        )
        assertEquals("6", state.display)

        state = CalculatorEngine.reduce(state, CalculatorAction.Equals)
        assertEquals("12", state.display)
    }

    @Test
    fun testTenDividedByFour() {
        // 10 ÷ 4 = -> 2,5
        val actions = mutableListOf<CalculatorAction>().apply {
            addAll(typeNumber("10"))
            add(CalculatorAction.Operator(Op.DIVIDE))
            add(CalculatorAction.Digit(4))
            add(CalculatorAction.Equals)
        }
        val state = press(*actions.toTypedArray())
        assertEquals("2.5", state.display)
        assertEquals("2,5", CalculatorEngine.formatForDisplay(state, ptBrSymbols))
        assertEquals("2.5", CalculatorEngine.formatForDisplay(state, enUsSymbols))
    }

    @Test
    fun testOneDividedByThree() {
        // 1 ÷ 3 = -> 0,333333333
        val state = press(
            CalculatorAction.Digit(1),
            CalculatorAction.Operator(Op.DIVIDE),
            CalculatorAction.Digit(3),
            CalculatorAction.Equals
        )
        assertEquals("0.333333333", state.display)
        assertEquals("0,333333333", CalculatorEngine.formatForDisplay(state, ptBrSymbols))
    }

    @Test
    fun testDivisionByZeroShowsErrorAndRecovers() {
        // 5 ÷ 0 = -> Error, then digit 7 -> 7
        var state = press(
            CalculatorAction.Digit(5),
            CalculatorAction.Operator(Op.DIVIDE),
            CalculatorAction.Digit(0),
            CalculatorAction.Equals
        )
        assertTrue(state.isError)
        assertEquals("Error", state.display)
        assertEquals("Error", CalculatorEngine.formatForDisplay(state, ptBrSymbols))

        state = CalculatorEngine.reduce(state, CalculatorAction.Digit(7))
        assertFalse(state.isError)
        assertEquals("7", state.display)
        assertTrue(state.isTyping)
    }

    @Test
    fun testZeroDividedByZeroShowsError() {
        // 0 ÷ 0 = -> Error
        val state = press(
            CalculatorAction.Digit(0),
            CalculatorAction.Operator(Op.DIVIDE),
            CalculatorAction.Digit(0),
            CalculatorAction.Equals
        )
        assertTrue(state.isError)
        assertEquals("Error", state.display)
    }

    @Test
    fun testPercentWithPendingAddition() {
        // 200 + 10 % -> 20, then = -> 220
        val actions = mutableListOf<CalculatorAction>().apply {
            addAll(typeNumber("200"))
            add(CalculatorAction.Operator(Op.ADD))
            addAll(typeNumber("10"))
            add(CalculatorAction.Percent)
        }
        var state = press(*actions.toTypedArray())
        assertEquals("20", state.display)

        state = CalculatorEngine.reduce(state, CalculatorAction.Equals)
        assertEquals("220", state.display)
    }

    @Test
    fun testPercentWithPendingMultiplication() {
        // 200 × 10 % -> 0,1, then = -> 20
        val actions = mutableListOf<CalculatorAction>().apply {
            addAll(typeNumber("200"))
            add(CalculatorAction.Operator(Op.MULTIPLY))
            addAll(typeNumber("10"))
            add(CalculatorAction.Percent)
        }
        var state = press(*actions.toTypedArray())
        assertEquals("0.1", state.display)
        assertEquals("0,1", CalculatorEngine.formatForDisplay(state, ptBrSymbols))

        state = CalculatorEngine.reduce(state, CalculatorAction.Equals)
        assertEquals("20", state.display)
    }

    @Test
    fun testFiftyPercentWithoutPendingOperator() {
        // 50 % -> 0,5
        val actions = mutableListOf<CalculatorAction>().apply {
            addAll(typeNumber("50"))
            add(CalculatorAction.Percent)
        }
        val state = press(*actions.toTypedArray())
        assertEquals("0.5", state.display)
        assertEquals("0,5", CalculatorEngine.formatForDisplay(state, ptBrSymbols))
    }

    @Test
    fun testToggleSign() {
        // 5 +/- -> -5, again -> 5
        var state = press(CalculatorAction.Digit(5), CalculatorAction.ToggleSign)
        assertEquals("-5", state.display)

        state = CalculatorEngine.reduce(state, CalculatorAction.ToggleSign)
        assertEquals("5", state.display)
    }

    @Test
    fun testToggleSignOnZero() {
        // 0 +/- -> 0
        var state = CalculatorEngine.reduce(CalculatorState(), CalculatorAction.ToggleSign)
        assertEquals("0", state.display)

        state = press(CalculatorAction.Digit(0), CalculatorAction.ToggleSign)
        assertEquals("0", state.display)
    }

    @Test
    fun testLeadingZeroRules() {
        // 000 -> 0 ; 0 then 5 -> 5
        val zerosState = press(
            CalculatorAction.Digit(0),
            CalculatorAction.Digit(0),
            CalculatorAction.Digit(0)
        )
        assertEquals("0", zerosState.display)

        val zeroThenFive = press(
            CalculatorAction.Digit(0),
            CalculatorAction.Digit(5)
        )
        assertEquals("5", zeroThenFive.display)
    }

    @Test
    fun testMaxNineDigitsEntry() {
        // 1234567890 -> only first 9 digits kept
        val actions = typeNumber("1234567890")
        val state = press(*actions.toTypedArray())
        assertEquals("123456789", state.display)
    }

    @Test
    fun testSecondDecimalPointIgnored() {
        // 5 , , 5 -> 5,5
        val state = press(
            CalculatorAction.Digit(5),
            CalculatorAction.Decimal,
            CalculatorAction.Decimal,
            CalculatorAction.Digit(5)
        )
        assertEquals("5.5", state.display)
        assertEquals("5,5", CalculatorEngine.formatForDisplay(state, ptBrSymbols))
        assertEquals("5.5", CalculatorEngine.formatForDisplay(state, enUsSymbols))
    }

    @Test
    fun testOperatorReplacement() {
        // 5 + then × -> operator replaced, 5 + × 3 = -> 15
        val state = press(
            CalculatorAction.Digit(5),
            CalculatorAction.Operator(Op.ADD),
            CalculatorAction.Operator(Op.MULTIPLY),
            CalculatorAction.Digit(3),
            CalculatorAction.Equals
        )
        assertEquals("15", state.display)
    }

    @Test
    fun testClearAndAllClearReset() {
        // 5 + 3 then AC -> everything reset
        var state = press(
            CalculatorAction.Digit(5),
            CalculatorAction.Operator(Op.ADD),
            CalculatorAction.Digit(3)
        )
        assertEquals("C", CalculatorEngine.clearLabel(state))

        // First clear clears entry to 0 (C)
        state = CalculatorEngine.reduce(state, CalculatorAction.Clear)
        assertEquals("0", state.display)
        assertEquals("AC", CalculatorEngine.clearLabel(state))

        // Second clear resets everything (AC)
        state = CalculatorEngine.reduce(state, CalculatorAction.Clear)
        assertEquals("0", state.display)
        assertNull(state.accumulator)
        assertNull(state.pendingOp)
        assertFalse(state.isError)
    }

    @Test
    fun testClearLabelRules() {
        var state = CalculatorState()
        assertEquals("AC", CalculatorEngine.clearLabel(state))

        state = CalculatorEngine.reduce(state, CalculatorAction.Digit(5))
        assertEquals("C", CalculatorEngine.clearLabel(state))

        state = CalculatorEngine.reduce(state, CalculatorAction.Clear)
        assertEquals("AC", CalculatorEngine.clearLabel(state))

        val errorState = CalculatorState(isError = true, display = "Error")
        assertEquals("AC", CalculatorEngine.clearLabel(errorState))
    }

    @Test
    fun testBackspaceGesture() {
        // 123 -> 12 -> 1 -> 0 ; backspace on a result does nothing
        var state = press(*typeNumber("123").toTypedArray())
        assertEquals("123", state.display)

        state = CalculatorEngine.reduce(state, CalculatorAction.Backspace)
        assertEquals("12", state.display)

        state = CalculatorEngine.reduce(state, CalculatorAction.Backspace)
        assertEquals("1", state.display)

        state = CalculatorEngine.reduce(state, CalculatorAction.Backspace)
        assertEquals("0", state.display)

        // Backspace on a computed result does nothing
        val resultState = press(
            CalculatorAction.Digit(5),
            CalculatorAction.Operator(Op.ADD),
            CalculatorAction.Digit(2),
            CalculatorAction.Equals
        )
        assertEquals("7", resultState.display)
        assertFalse(resultState.isTyping)

        val afterBackspaceOnResult = CalculatorEngine.reduce(resultState, CalculatorAction.Backspace)
        assertEquals("7", afterBackspaceOnResult.display)

        // Backspace on Error does nothing
        val errState = CalculatorState(display = "Error", isError = true)
        val afterBackspaceOnError = CalculatorEngine.reduce(errState, CalculatorAction.Backspace)
        assertEquals("Error", afterBackspaceOnError.display)
    }

    @Test
    fun testLargeResultScientificNotation() {
        // 999999999 × 999999999 -> scientific notation, no crash
        val actions = mutableListOf<CalculatorAction>().apply {
            addAll(typeNumber("999999999"))
            add(CalculatorAction.Operator(Op.MULTIPLY))
            addAll(typeNumber("999999999"))
            add(CalculatorAction.Equals)
        }
        val state = press(*actions.toTypedArray())
        assertFalse(state.isError)
        assertTrue(state.display.contains("e+"))
        val formatted = CalculatorEngine.formatForDisplay(state, ptBrSymbols)
        assertTrue(formatted.contains("e+"))
    }

    @Test
    fun testSmallResultScientificNotation() {
        // 1 ÷ 3000000000 -> scientific notation
        val initial = CalculatorState(
            accumulator = "1",
            pendingOp = Op.DIVIDE,
            display = "3000000000",
            isTyping = true
        )
        val state = CalculatorEngine.reduce(initial, CalculatorAction.Equals)
        assertFalse(state.isError)
        assertEquals("3.33333e-10", state.display)
        assertEquals("3,33333e-10", CalculatorEngine.formatForDisplay(state, ptBrSymbols))
        assertEquals("3.33333e-10", CalculatorEngine.formatForDisplay(state, enUsSymbols))
    }

    @Test
    fun testActionsIgnoredWhileErrorShowing() {
        val errorState = CalculatorState(display = "Error", isError = true)

        val withOp = CalculatorEngine.reduce(errorState, CalculatorAction.Operator(Op.ADD))
        assertEquals(errorState, withOp)

        val withPct = CalculatorEngine.reduce(errorState, CalculatorAction.Percent)
        assertEquals(errorState, withPct)

        val withSign = CalculatorEngine.reduce(errorState, CalculatorAction.ToggleSign)
        assertEquals(errorState, withSign)

        val withEquals = CalculatorEngine.reduce(errorState, CalculatorAction.Equals)
        assertEquals(errorState, withEquals)

        val withBackspace = CalculatorEngine.reduce(errorState, CalculatorAction.Backspace)
        assertEquals(errorState, withBackspace)
    }

    @Test
    fun testNegativeFormattingAndGrouping() {
        // Result: 2 - 5 = -3
        val subState = press(
            CalculatorAction.Digit(2),
            CalculatorAction.Operator(Op.SUBTRACT),
            CalculatorAction.Digit(5),
            CalculatorAction.Equals
        )
        assertEquals("-3", subState.display)
        assertEquals("-3", CalculatorEngine.formatForDisplay(subState, ptBrSymbols))
        assertEquals("-3", CalculatorEngine.formatForDisplay(subState, enUsSymbols))

        // Large number grouping
        val largeState = CalculatorState(display = "-1234567.89", isTyping = false)
        assertEquals("-1.234.567,89", CalculatorEngine.formatForDisplay(largeState, ptBrSymbols))
        assertEquals("-1,234,567.89", CalculatorEngine.formatForDisplay(largeState, enUsSymbols))

        // Typing negative zero
        val negZero = CalculatorState(display = "-0", isTyping = true)
        assertEquals("-0", CalculatorEngine.formatForDisplay(negZero, ptBrSymbols))
        assertEquals("-0", CalculatorEngine.formatForDisplay(negZero, enUsSymbols))
    }

    @Test
    fun testExpressionDisplayAndHistory() {
        // 1. Enter 12: expression is empty
        var state = press(*typeNumber("12").toTypedArray())
        assertEquals("", CalculatorEngine.formatExpression(state, enUsSymbols))

        // 2. Press +: expression shows "12 +"
        state = CalculatorEngine.reduce(state, CalculatorAction.Operator(Op.ADD))
        assertEquals("12 +", CalculatorEngine.formatExpression(state, enUsSymbols))

        // 3. Enter 5: expression shows "12 + 5"
        state = CalculatorEngine.reduce(state, CalculatorAction.Digit(5))
        assertEquals("12 + 5", CalculatorEngine.formatExpression(state, enUsSymbols))

        // 4. Press =: result is 17, expression shows "12 + 5 =", history has 1 item
        state = CalculatorEngine.reduce(state, CalculatorAction.Equals)
        assertEquals("17", state.display)
        assertEquals("12 + 5 =", CalculatorEngine.formatExpression(state, enUsSymbols))
        assertEquals(1, state.history.size)
        assertEquals("12 + 5 =", state.history[0].expression)
        assertEquals("17", state.history[0].result)

        // 5. Recall history item
        state = CalculatorEngine.reduce(state, CalculatorAction.RecallHistory("17"))
        assertEquals("17", state.display)

        // 6. Clear history
        state = CalculatorEngine.reduce(state, CalculatorAction.ClearHistory)
        assertTrue(state.history.isEmpty())
    }
}
