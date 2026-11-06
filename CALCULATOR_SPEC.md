# CALCULATOR_SPEC.md

Project: basic_calculator (Android, Kotlin)
Goal: Recreate the iPhone (iOS) Calculator app pixel-for-pixel in look and exactly in behavior, as an Android app.
Reference: the attached screenshot (iOS calculator, dark theme, Brazilian locale so the decimal key shows a comma).

---

## 0. HARD RULES (read first, never break)

1. Work ONLY inside the `basic_calculator` project folder. Do not create, edit, move or delete anything outside it. Do not touch the parent folder, other projects, global gradle files, or system files.
2. DO NOT RUN ANY COMMANDS. No gradle, no adb, no shell, no installs, no tests. Only create/edit files. When something needs to be run, print the exact command for me to run manually (see section 12).
3. Work one phase at a time (section 13). After each phase, stop, summarize what you changed, and wait for my approval before starting the next phase.
4. When you change a file, give me the FULL file content, not a partial snippet or diff. I will replace the whole file.
5. Before writing code, READ the existing project files (settings.gradle.kts, app/build.gradle.kts, gradle/libs.versions.toml, AndroidManifest.xml, MainActivity.kt, theme files). Adapt to what is already there. Do not change Gradle, AGP, Kotlin, Compose BOM or SDK versions unless something is broken, and if you must, tell me why first.
6. Do not add third-party libraries. Use only what the Empty Activity (Jetpack Compose) template already includes.
7. Never invent APIs. If unsure an API exists in the project's current versions, use a simpler well-known alternative.
8. If anything in this spec is ambiguous, pick the iOS Calculator behavior and note the choice in your summary. Do not stop to ask unless it blocks the work.

---

## 1. Tech stack

- Language: Kotlin
- UI: Jetpack Compose (Material3 only as a base; all visuals are custom)
- Min SDK: keep whatever the template uses (24 or higher is fine)
- Architecture: UI (Compose) + a pure Kotlin `CalculatorEngine` with no Android imports, so it can be unit tested on the JVM
- Numbers: use `java.math.BigDecimal`. Never use Double/Float for arithmetic (0.1 + 0.2 must give 0,3).
- Package name: use whatever the project already has. Do not rename it.

Suggested files (all inside app/src/main/java/<package path>/):

- `MainActivity.kt` : sets up edge-to-edge, forced dark system bars, calls `CalculatorScreen()`
- `CalculatorScreen.kt` : layout of display + keypad
- `CalculatorButton.kt` : reusable round/pill button composable
- `CalculatorEngine.kt` : all logic, state, formatting (pure Kotlin)
- `CalculatorState.kt` : immutable state data class (can live inside the engine file)
- `ui/theme/` : keep template files but force a pure dark, non-dynamic color scheme

Tests (JVM only): app/src/test/java/<package path>/CalculatorEngineTest.kt

---

## 2. Visual design (match the screenshot)

Overall
- Background: pure black `#000000`, full screen, edge-to-edge (draw behind status and navigation bars).
- Status bar and navigation bar: transparent/black, with LIGHT (white) icons. Force this regardless of the phone's system theme.
- Portrait only. Lock orientation in the manifest: `android:screenOrientation="portrait"`.
- Force dark. Do NOT use dynamic color. Do NOT follow the phone's light theme.

Keypad layout: 4 columns x 5 rows, in this exact order
- Row 1: `AC` | `+/-` | `%` | `÷`
- Row 2: `7` | `8` | `9` | `×`
- Row 3: `4` | `5` | `6` | `−`
- Row 4: `1` | `2` | `3` | `+`
- Row 5: `0` (double-width pill) | `,` (decimal) | `=`

Colors
- Digit keys and decimal key: background `#333333`, text white `#FFFFFF`
- Function keys (AC/C, +/-, %): background `#A5A5A5`, text black `#000000`
- Operator keys (÷ × − + =): background `#FF9F0A`, text white `#FFFFFF`
- Selected operator (an operator is pending and waiting for the second number): background white `#FFFFFF`, text orange `#FF9F0A`
- Pressed state: a brighter fill while the finger is down
  - digit key pressed: `#737373`
  - function key pressed: `#D9D9D9`
  - operator key pressed: `#FFC77A` (lighter orange)
  - Do not use the default Material ripple. Use the color change above (no ripple, no elevation, no shadow).

Shape and size (reference: 360dp wide screen; compute from the real width, do not hardcode)
- Keys are perfect circles. Diameter = (screenWidth - 2 * sidePadding - 3 * gap) / 4
- Side padding ~ 14dp, gap between keys ~ 12dp (horizontal and vertical identical)
- The `0` key is a pill: width = 2 * diameter + gap, same height as the others, rounded fully (corner radius = half height). The `0` text is LEFT aligned with about 28dp start padding (like iOS), not centered.
- Keypad sits at the bottom with bottom padding ~ 16dp plus the navigation bar inset.

Typography
- Font: default system sans (Roboto is fine), weight Light (`FontWeight.Light`) for digits and display, Normal for operators
- Digit keys: ~ 36sp, function key labels (AC, +/-, %): ~ 32sp, operators: ~ 40sp
- Use real symbols: `÷` (U+00F7), `×` (U+00D7), `−` (U+2212 minus sign, not a hyphen), `+`, `=`, `%`, `+/-` shown as `+/−`
- Do not let system font scaling break the layout: key text must not wrap. Use `maxLines = 1` and cap font scale for key labels (use `sp` converted via density without fontScale, or `TextUnit` from `.dp.toSp()` style approach).

Display (top area)
- Fills all space above the keypad
- Content aligned to bottom-end (right aligned, sitting just above the first key row)
- Right padding ~ 28dp, bottom padding ~ 16dp
- White text, Light weight, starts at 88sp
- Single line. Auto-shrink: when the text is too wide, reduce font size step by step down to a minimum of 36sp so the number always fits. Never wrap, never clip, never overflow off screen.
- Default content when app opens: `0`

---

## 3. Calculator behavior (iOS Calculator rules)

State to track
- `display`: current entry/result as a plain string of digits (using `.` internally, `-` sign if negative)
- `accumulator`: left operand (BigDecimal?)
- `pendingOperator`: one of ADD, SUBTRACT, MULTIPLY, DIVIDE, or null
- `isTyping`: true while the user is entering a number, false right after an operator or equals
- `lastOperator` and `lastOperand`: used to repeat the last operation when `=` is pressed again
- `isError`: true when showing `Error`

Digit entry
- Pressing a digit while not typing starts a new number (replaces display).
- Leading zeros: pressing `0` on `0` stays `0`. Pressing `5` on `0` gives `5`.
- Maximum 9 digits in an entry (digits only, not counting the decimal point, sign or grouping separators). Extra digits are ignored silently.
- Decimal key: if not typing, starts `0,` (shows `0,`). If already contains a decimal point, ignore. Display must show the trailing decimal separator immediately (`5,` before the next digit).
- Display shows thousands grouping while typing (1.234.567 in pt-BR, 1,234,567 in en-US). The decimal point and trailing zeros typed by the user must be preserved while typing (`1,50` stays `1,50`, `0,0` stays `0,0`).

Locale
- Decimal separator and grouping separator come from the device locale (`DecimalFormatSymbols.getInstance()`). The decimal key label must show that separator (`,` on a Brazilian phone, `.` on a US phone).
- Internally always use `.`; only format for display.

Operators (+ − × ÷)
- If the user is typing a second number and an operator is already pending, evaluate the pending operation first (iOS chains left to right, NO operator precedence: 2 + 3 × 4 = 20), show the result, then set the new operator.
- If the user presses an operator right after another operator (no number between), just replace the pending operator (the highlight moves to the new key).
- Highlight (white key, orange text) the pending operator while `isTyping` is false after pressing it. Remove the highlight as soon as the user starts typing a digit, presses `=`, or `AC`.
- Pressing an operator with an `Error` showing: ignore.

Equals
- Computes `accumulator (pendingOperator) display` and shows the result. Stores `lastOperator` and `lastOperand`.
- Pressing `=` repeatedly repeats the last operation on the result (5 + 2 = 7, = 9, = 11). Same for 3 × 2 = 6, = 12, = 24.
- Pressing `=` with no pending operator and no last operation: does nothing.
- After `=`, typing a digit starts a brand new calculation (accumulator cleared).
- After `=`, pressing an operator uses the result as the left operand.

Percent `%`
- No pending operator: display = display / 100 (50 % -> 0,5).
- Pending `+` or `−`: operand = accumulator * display / 100 (200 + 10 % -> shows 20, then = gives 220).
- Pending `×` or `÷`: operand = display / 100 (200 × 10 % -> shows 0,1, then = gives 20).
- The result of % becomes the current display entry (not typing; the next digit starts fresh, pending operator stays).

Plus/minus `+/-`
- Toggles the sign of the current display (typing or result).
- On `0` do nothing.
- If the user has typed a number it stays in typing mode (so `5,` then `+/-` gives `-5,`).
- If an operator was just pressed (not typing), `+/-` makes the display `-0` ... to keep it simple and safe: start a new number as `-0` and keep typing mode on, so the next digit gives `-7`. Show `-0` on the display.

Clear AC / C
- Label is `AC` when there is nothing to clear (display is 0 and nothing typed), and `C` when the user has typed something (isTyping true and display is not 0).
- `C`: clears only the current entry to `0` (keeps accumulator and pending operator), label goes back to `AC`.
- `AC`: resets everything: display `0`, accumulator null, operator null, lastOperator null, error false.

Backspace (hidden gesture, like iOS)
- Swipe left or right on the display deletes the last typed digit. Only while typing. On the last digit it becomes `0`. Never works on results or `Error`.

Errors (professional behavior, app must NEVER crash)
- Division by zero (x ÷ 0, 0 ÷ 0): display `Error`, clear accumulator and operator. The next digit, decimal or AC starts fresh. Operators, `%`, `+/-` and `=` are ignored while `Error` is showing.
- Result too large or too small: use scientific notation (see below), never show `Infinity`, `NaN` or `null`.
- Non-terminating division (1 ÷ 3): use `MathContext` with 15 significant digits for the calculation, then round the displayed result to fit 9 significant digits.
- Wrap every engine entry point in try/catch (ArithmeticException, NumberFormatException, any Exception). On failure: show `Error` and reset state. Never let an exception reach the UI thread.
- Ignore any invalid input silently (extra digits, second decimal point, etc.). No toasts, no dialogs.

Result formatting
- Strip trailing zeros from results (2,50 -> 2,5; 6,000 -> 6). While typing, do NOT strip.
- Round to at most 9 significant digits for display (0,1 + 0,2 -> 0,3; 1 ÷ 3 -> 0,333333333).
- Use grouping separators for the integer part.
- If the absolute value is >= 1e9 (cannot fit in 9 digits) or non-zero and < 1e-8, show scientific notation like iOS: `1,23457e+10`, `1e-9` (decimal separator per locale, up to 6 significant digits in mantissa, trailing zeros stripped, `e+` / `e-` exponent).
- Negative numbers use a leading `-` (hyphen-minus is fine in the display).
- Never show `-0` as a result. If a result is zero, show `0`.

---

## 4. State preservation

- Keep state in an immutable data class made only of Strings, Booleans and simple values so it can be saved easily.
- Survive configuration changes (dark mode toggle, font scale change, language change, multi-window resize) using `rememberSaveable` with a custom `Saver`, or a `ViewModel` if the template already includes lifecycle-viewmodel-compose. Do not add a new dependency just for this.
- Handle process death gracefully: if state cannot be restored, start clean at `0`. Never crash on restore.
- Add `android:configChanges` in the manifest only if really needed. Prefer proper state saving.

---

## 5. Interaction details

- Haptic feedback on every key press (`HapticFeedbackType.TextHandleMove` or `LongPress`-free light tap; pick the lightest available and keep it safe).
- Prevent double-tap crashes or duplicated input: each tap is handled once, state updates are synchronous and on the main thread.
- Multi-touch: pressing two keys at once must not crash or corrupt state. Process taps one at a time.
- The app must feel instant. No animations on the digits. Only the press color change and the operator highlight change.
- Long press on the display copies the current value to the clipboard (plain number with `.` or locale format, use locale format) and shows nothing intrusive. Wrap in try/catch. If this is risky in the template's versions, skip it and tell me.

---

## 6. Accessibility

- Every key has a `contentDescription` / semantics label: "zero" ... "nine", "decimal point", "plus", "minus", "multiply", "divide", "equals", "percent", "plus minus", "all clear" / "clear".
- Display has semantics that announce the current value.
- Minimum touch target is already satisfied by the large circles.
- Contrast is already high (white on black).

---

## 7. Manifest and app setup

- App label (`android:label`): `Calculator`
- `android:screenOrientation="portrait"` on the main activity
- `android:windowSoftInputMode` not needed
- `android:supportsRtl="true"` is fine. Layout must not mirror: force `LayoutDirection.Ltr` for the keypad and display so the keys never flip on RTL languages.
- Keep the template's default launcher icon (do not generate or edit icon assets) unless I ask later.
- Theme: a plain dark NoActionBar theme, window background black, so there is no white flash on app start.
- No permissions required. Do not add any.

---

## 8. Engine API (suggested)

```kotlin
sealed interface CalculatorAction {
    data class Digit(val value: Int) : CalculatorAction
    data object Decimal : CalculatorAction
    data class Operator(val op: Op) : CalculatorAction
    data object Equals : CalculatorAction
    data object Percent : CalculatorAction
    data object ToggleSign : CalculatorAction
    data object Clear : CalculatorAction      // AC / C
    data object Backspace : CalculatorAction  // swipe on display
}

enum class Op { ADD, SUBTRACT, MULTIPLY, DIVIDE }

data class CalculatorState(
    val display: String = "0",
    val accumulator: String? = null,
    val pendingOp: Op? = null,
    val isTyping: Boolean = false,
    val lastOp: Op? = null,
    val lastOperand: String? = null,
    val isError: Boolean = false
)

object CalculatorEngine {
    fun reduce(state: CalculatorState, action: CalculatorAction): CalculatorState
    fun formatForDisplay(state: CalculatorState, symbols: DecimalFormatSymbols): String
    fun clearLabel(state: CalculatorState): String   // "AC" or "C"
}
```

`reduce` must be a pure function: same input gives same output, never throws.

---

## 9. Unit tests (JVM, no emulator needed)

Create `CalculatorEngineTest.kt` using the JUnit already in the template. Cover at least:

- 1 + 2 = -> 3
- 0,1 + 0,2 = -> 0,3
- 2 + 3 × 4 = -> 20 (left to right chaining)
- 5 + 2 = = = -> 7, 9, 11 (repeat equals)
- 3 × 2 = = -> 6, 12
- 10 ÷ 4 = -> 2,5
- 1 ÷ 3 = -> 0,333333333
- 5 ÷ 0 = -> Error, then digit 7 -> 7
- 0 ÷ 0 = -> Error
- 200 + 10 % -> 20, then = -> 220
- 200 × 10 % -> 0,1, then = -> 20
- 50 % -> 0,5
- 5 +/- -> -5, again -> 5
- 0 +/- -> 0
- 000 -> 0 ; 0 then 5 -> 5
- 1234567890 -> only first 9 digits kept
- 5 , , 5 -> 5,5 (second decimal ignored)
- 5 + then × -> operator replaced, 5 + × 3 = -> 15
- 5 + 3 then AC -> everything reset
- C vs AC label rules
- Backspace: 123 -> 12 -> 1 -> 0 ; backspace on a result does nothing
- Large result: 999999999 × 999999999 -> scientific notation, no crash
- Small result: 1 ÷ 3000000000 -> scientific notation
- Operators/%/+/-/= ignored while Error is showing
- Negative formatting and grouping with pt-BR and en-US symbols

Tests must only use the engine (no Android classes), so they run with a plain gradle unit test.

---

## 10. Avoiding common errors (checklist for you before finishing each phase)

- Every import used is present; no unused or wrong imports (check `androidx.compose.*` package names carefully)
- No deprecated or removed Compose APIs for the project's BOM version. If unsure, use the simplest stable API (`Box`, `Row`, `Column`, `Text`, `Modifier.clickable`/`pointerInput`)
- `Modifier.weight` only inside `Row`/`Column` scopes
- Use `BoxWithConstraints` to compute key size from the real screen width
- Text uses `maxLines = 1`, `softWrap = false`, and `TextOverflow.Clip` on keys and display
- No `!!` operators. No unchecked casts. No `lateinit` that can be uninitialized
- All BigDecimal division uses an explicit `MathContext` (never plain `divide()` without it)
- Manifest: only one launcher activity, `android:exported="true"` on it
- Do not leave TODOs, placeholder code or fake data
- Code compiles against the existing `compileSdk` / `targetSdk`. Do not use APIs newer than that
- Resource names are valid (lowercase, underscores)

---

## 11. Definition of done

- App opens straight to a black screen with `0` at the bottom right of the display and the keypad exactly like the screenshot
- All behaviors in section 3 work, verified by the unit tests in section 9
- No crash on any key sequence, including rapid tapping and rotating the phone (portrait locked) or changing the system font size or dark mode
- Works on Android 7.0 (API 24) and up, on small (360dp) and large (420dp+) screen widths
- Debug APK builds and installs with the commands in section 12

---

## 12. Commands I will run manually (you must NOT run them; print them filled in with the real package name)

I am on Windows. Run all commands from the `basic_calculator` project root folder (the folder that contains `gradlew.bat`).

Find the package name (applicationId) if needed:

```
findstr /C:"applicationId" app\build.gradle.kts
```

Clean build (only if something looks stuck):

```
.\gradlew.bat clean
```

Run the unit tests:

```
.\gradlew.bat testDebugUnitTest
```

Build the debug APK:

```
.\gradlew.bat assembleDebug
```

The APK will be at:

```
app\build\outputs\apk\debug\app-debug.apk
```

Check the phone is connected (USB debugging on, authorize the computer on the phone):

```
adb devices
```

Install (or reinstall over the old version):

```
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Launch the app from the computer (replace PACKAGE with the real applicationId):

```
adb shell am start -n PACKAGE/.MainActivity
```

If the install fails with a signature or "already exists" error:

```
adb uninstall PACKAGE
adb install app\build\outputs\apk\debug\app-debug.apk
```

If the app crashes, capture the log right after reproducing the crash:

```
adb logcat -d -s AndroidRuntime:E
```

Fix "adb is not recognized": use the full path to adb from Android Studio's SDK folder, for example `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`.

Fix Gradle JDK errors: in Android Studio go to File > Settings > Build, Execution, Deployment > Build Tools > Gradle and set "Gradle JDK" to the embedded JDK (jbr).

---

## 13. Phases (stop after each one and wait for my approval)

Phase 1: Inspect the project. List the files you read, tell me the package name, SDK versions, Compose BOM version and whether the template matches the assumptions in this spec. Propose the final file list. No code yet.

Phase 2: `CalculatorEngine.kt` (+ state and action types). Full file content only.

Phase 3: `CalculatorEngineTest.kt`. Full file content. Give me the test command to run and wait for my result.

Phase 4: UI: theme changes, `CalculatorButton.kt`, `CalculatorScreen.kt`, `MainActivity.kt`. Full file content for each file.

Phase 5: `AndroidManifest.xml` (and `themes.xml` / `colors.xml` if needed). Full file content.

Phase 6: Final review against sections 2, 3, 10 and 11. List anything that deviates from the screenshot or from iOS behavior, then give me the build and install commands with the real package name filled in.

---

## 14. Extras added beyond the screenshot

- Locale-aware decimal and grouping separators
- Scientific notation for very large and very small results
- Repeat-equals behavior, percent rules, operator chaining without precedence (all as on iOS)
- Swipe-to-delete on the display, long-press copy
- Auto-shrinking display text
- Haptic feedback, accessibility labels, forced portrait, forced dark, RTL-safe layout
- State saved across configuration changes
- JVM unit tests for the engine
