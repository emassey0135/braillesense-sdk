package dev.emassey0135.braillesense.ui

import dev.emassey0135.braillesense.braille.Translator
import dev.emassey0135.braillesense.edit.TextBox
import dev.emassey0135.braillesense.input.Key
import dev.emassey0135.braillesense.reading.ControlRole
import dev.emassey0135.braillesense.reading.DeviceLine

/** A control of a [DialogFrame], as the app holds it now: built again each time the app is asked. */
public sealed class DialogControl(
    /** What names it across a redraw. */
    public val key: String,
    /** What it is called, said and shown before its value. */
    public val label: String,
) {
    /**
     * An edit box: [box] holds its text and its cursor, kept by the app while the dialog is open so
     * that a redraw goes on where the person was; of several lines where the box is. [hint] is said
     * while it is empty.
     */
    public class Line(key: String, label: String, public val box: TextBox, public val hint: String = "") : DialogControl(key, label)

    /**
     * A combo box of [options], [selected] in hand (-1 for none): Space and Backspace go round them,
     * a letter to the next one [finding] starts with.
     */
    public class Choice(
        key: String,
        label: String,
        /** What each option is said and shown as. */
        public val options: List<String>,
        /** The option it holds, from 0; -1 for none. */
        public val selected: Int,
        /** What a first letter finds each option by. */
        public val finding: List<String> = options,
    ) : DialogControl(key, label)

    /**
     * A list box, a control that is itself a list: Space-1 and Space-4 move within it, stopping at
     * its ends, and Enter takes the option in hand.
     */
    public class ListBox(
        key: String,
        label: String,
        /** What each option is said and shown as. */
        public val options: List<String>,
        /** The option in hand, from 0; -1 for none. */
        public val selected: Int,
        /** What a first letter finds each option by. */
        public val finding: List<String> = options,
    ) : DialogControl(key, label)

    /** A check box: Space, Backspace or Enter turns it over. */
    public class Check(key: String, label: String, public val checked: Boolean) : DialogControl(key, label)

    /** A line to read, a static box. */
    public class Text(key: String, public val value: String) : DialogControl(key, "")

    /** A button: Enter presses it, and Space or Enter with its [letter] from anywhere in the dialog. */
    public class Button(
        key: String,
        label: String,
        /** Its letter, shown as the device shows a shortcut; null for none. */
        public val letter: Char? = null,
        /** Whether it opens a dialog, which the device marks. */
        public val asks: Boolean = false,
    ) : DialogControl(key, label)
}

/**
 * A dialog as the device's own work one, over controls the app keeps ([controls], read again at
 * every key): the app hears each thing the person does ([focusOn], [typed], [chose], [toggled],
 * [pressed], [entered]) and changes what it holds, a refusal or a value the app put back staying
 * on its control, and this works the device's keys and reads the control in hand in its role.
 *
 * Tab (Space-4-5, F3) and Shift-Tab are all that move between controls, going round; a line's keys
 * are its box's (`BoxEditing`, which the stack runs ahead of the frame); Space and Backspace go round
 * a combo box's options, Space-1-2-3 and Space-4-5-6 to its ends, and a letter to the next option
 * starting with it; Space-1 and Space-4 move within a list box, stopping at its ends; Space,
 * Backspace or Enter turn a check box over; Enter presses a button, and Space or Enter with a
 * button's letter presses it from anywhere. A question of buttons alone ([prompt]) is the device's
 * prompt: Space and Backspace step between its answers. Each control is read with its label, its
 * value and, for a combo box or a list box, "2/5".
 *
 * Escape is the screen's ([Screen.back]): what the dialog does as it closes unanswered is the app's.
 */
public abstract class DialogFrame(
    id: String,
    private val letters: Letters,
    private val readings: Readings,
) : Frame(id) {
    /** The controls in tab order, as the app holds them now. */
    public abstract val controls: List<DialogControl>

    /** The place in [controls] of the one in hand. */
    public abstract val focus: Int

    /** Whether it is a question of buttons alone, its answers the device's prompt buttons. */
    public open val prompt: Boolean get() = false

    /** Tab, Shift-Tab or a prompt's Space: the control at [at] is now in hand. */
    protected abstract fun focusOn(at: Int)

    /** [line]'s box was changed by its keys: what it holds now is [DialogControl.Line.box]'s text. */
    protected abstract fun typed(line: DialogControl.Line)

    /** [control], a combo box or a list box, now holds option [at]. */
    protected abstract fun chose(control: DialogControl, at: Int)

    /** [check] turned over. */
    protected abstract fun toggled(check: DialogControl.Check)

    /** [button] pressed: by Enter, its letter or a routing key. */
    protected abstract fun pressed(button: DialogControl.Button)

    /**
     * Enter in a line of one, a combo box or a list box: what the dialog does with it (its default
     * button, or the button that takes what it holds). Whether anything was pressed: a combo box
     * pressing nothing goes on to the next control.
     */
    protected abstract fun entered(control: DialogControl): Boolean

    /** Space-4 in a line of one, which its box leaves: what the app offers at its cursor (a completion). Whether it took the key. */
    protected open fun below(line: DialogControl.Line): Boolean = false

    /** The control in hand. */
    public val current: DialogControl? get() = controls.getOrNull(focus)

    /** The buttons' letters: each one's own, else the first free one of its name, as a menu's. */
    private fun buttonLetters(): Map<String, Char> {
        val buttons = controls.filterIsInstance<DialogControl.Button>()
        return buttons.zip(letters.shortcuts(buttons.map { it.label to it.letter })).mapNotNull { (button, letter) -> letter?.let { button.key to it } }.toMap()
    }

    override fun editBox(): TextBox? = (current as? DialogControl.Line)?.box

    override fun editBoxes(): List<TextBox> = controls.filterIsInstance<DialogControl.Line>().map { it.box }

    override fun edited() {
        (current as? DialogControl.Line)?.let(::typed)
    }

    override val rendered: Rendered
        get() {
            val control = current ?: return Rendered("")
            val reading = readings.read(lineOf(control))
            if (control is DialogControl.Line) {
                val shown = control.box.line.shown()
                return reading.rendered(shown.cells, shown.cursor)
            }
            return reading.rendered()
        }

    private fun lineOf(control: DialogControl): DeviceLine = when (control) {
        is DialogControl.Line -> {
            val box = control.box
            if (box.multiLine) line(ControlRole.MULTI_LINE_EDIT_BOX, box.line.text.ifEmpty { if (box.text.isEmpty()) control.hint else "" }, box.line.text, label = control.label)
            else line(ControlRole.EDIT_BOX, box.text.ifEmpty { control.hint }, box.text, label = control.label)
        }
        is DialogControl.Choice -> {
            val at = control.selected.coerceAtLeast(0)
            line(ControlRole.COMBO_BOX, control.options.getOrNull(at).orEmpty(), label = control.label, position = at + 1, count = control.options.size)
        }
        is DialogControl.ListBox -> {
            val at = control.selected.coerceAtLeast(0)
            line(ControlRole.LIST_ITEM, control.options.getOrNull(at).orEmpty(), label = control.label, position = at + 1, count = control.options.size)
        }
        is DialogControl.Check -> line(if (control.checked) ControlRole.CHECK_BOX_CHECKED else ControlRole.CHECK_BOX_UNCHECKED, control.label)
        is DialogControl.Text -> line(ControlRole.STATIC_BOX, control.value)
        is DialogControl.Button -> line(if (prompt) ControlRole.PROMPT_BUTTON else ControlRole.BUTTON, control.label, letter = buttonLetters()[control.key], asks = control.asks)
    }

    override fun pressLetter(letter: Char): Boolean {
        val key = buttonLetters().entries.firstOrNull { it.value == letter.lowercaseChar() }?.key ?: return false
        val button = controls.firstOrNull { it.key == key } as? DialogControl.Button ?: return false
        pressed(button)
        return true
    }

    override fun routed(at: Int) {
        when (val control = current) {
            is DialogControl.Line -> control.box.line.route(at)
            is DialogControl.Button -> pressed(control)
            else -> {}
        }
    }

    override fun key(key: Key): Boolean {
        when (key) {
            Key.Tab -> return move(+1)
            Key.ShiftTab -> return move(-1)
            else -> {}
        }
        return when (val control = current) {
            // Enter in a line of one presses what it is for; a multi-line box's Enter is its own new line.
            is DialogControl.Line -> when {
                control.box.multiLine -> false
                key == Key.Enter -> true.also { entered(control) }
                key == Key.Down -> below(control)
                else -> false
            }
            is DialogControl.Choice -> choice(control, key)
            is DialogControl.ListBox -> list(control, key)
            is DialogControl.Check -> when (key) {
                Key.Space, Key.Backspace, Key.Enter -> true.also { toggled(control) }
                else -> false
            }
            is DialogControl.Button -> when {
                key == Key.Enter -> true.also { pressed(control) }
                prompt && key == Key.Space -> answer(+1)
                prompt && key == Key.Backspace -> answer(-1)
                else -> false
            }
            is DialogControl.Text, null -> false
        }
    }

    private fun move(by: Int): Boolean {
        if (controls.isNotEmpty()) focusOn(Math.floorMod(focus + by, controls.size))
        return true
    }

    /** Space and Backspace among a prompt's answers: to the next or last of them, going round. */
    private fun answer(by: Int): Boolean {
        val answers = controls.withIndex().filter { it.value is DialogControl.Button }.map { it.index }
        val at = answers.indexOf(focus)
        if (at >= 0 && answers.size > 1) focusOn(answers[Math.floorMod(at + by, answers.size)])
        return true
    }

    /** A combo box goes round its options, as the device's own does, and to its ends. */
    private fun choice(control: DialogControl.Choice, key: Key): Boolean {
        val count = control.options.size
        val step = { by: Int ->
            if (count > 0) {
                val at = control.selected
                val to = if (at < 0) (if (by > 0) 0 else count - 1) else Math.floorMod(at + by, count)
                if (to != at) chose(control, to)
            }
            true
        }
        return when (key) {
            Key.Up, Key.Backspace -> step(-1)
            Key.Down, Key.Space -> step(+1)
            Key.First -> true.also { if (count > 0 && control.selected != 0) chose(control, 0) }
            Key.Last -> true.also { if (count > 0 && control.selected != count - 1) chose(control, count - 1) }
            Key.Enter -> true.also { if (!entered(control)) move(+1) }
            is Key.Cell, is Key.Typed -> true.also { find(control, control.finding, control.selected, key) }
            else -> false
        }
    }

    /** A list box moves in place, stopping at its ends, and Enter takes the one in hand. */
    private fun list(control: DialogControl.ListBox, key: Key): Boolean {
        val count = control.options.size
        val to = { at: Int ->
            if (count > 0) {
                val now = at.coerceIn(0, count - 1)
                if (now != control.selected) chose(control, now)
            }
            true
        }
        val at = control.selected
        return when (key) {
            Key.Up -> to(if (at < 0) count - 1 else at - 1)
            Key.Down -> to(if (at < 0) 0 else at + 1)
            Key.First -> to(0)
            Key.Last -> to(count - 1)
            Key.Enter -> entered(control)
            is Key.Cell, is Key.Typed -> true.also { find(control, control.finding, at, key) }
            else -> false
        }
    }

    /** First-letter moving among a combo box's or a list box's options, round them. */
    private fun find(control: DialogControl, finding: List<String>, from: Int, key: Key) {
        val letter = letters.letter(key) ?: return
        // With none held, from the first.
        val at = letters.find(finding, if (from < 0) finding.size - 1 else from, letter) ?: return
        if (at != from) chose(control, at)
    }
}

/**
 * A form of [Field]s, then OK and Cancel ([BrailleSenseUi.form]): the SDK's own [DialogFrame],
 * holding what the person makes of each field until OK gives it, or Cancel or Escape gives null.
 */
internal class FieldsDialog(
    id: String,
    private val fields: List<Field>,
    translator: Translator,
    private val words: UiWords,
    letters: Letters,
    readings: Readings,
    /** OK, with what the form holds, or Cancel, with null. */
    private val done: (FormValues?) -> Unit,
) : DialogFrame(id, letters, readings) {
    private val boxes = fields.filterIsInstance<Field.Line>().associate { it.key to TextBox(it.value, translator, it.multiLine) }
    private val choices = fields.filterIsInstance<Field.Choice>().associate { it.key to it.selected.coerceIn(0, (it.options.size - 1).coerceAtLeast(0)) }.toMutableMap()
    private val checks = fields.filterIsInstance<Field.Check>().associate { it.key to it.checked }.toMutableMap()
    private var finished = false

    override var focus: Int = 0
        private set

    override val controls: List<DialogControl>
        get() = fields.map { field ->
            when (field) {
                is Field.Line -> DialogControl.Line(field.key, field.label, boxes.getValue(field.key), field.hint)
                is Field.Choice -> DialogControl.Choice(field.key, field.label, field.options, choices.getValue(field.key))
                is Field.Check -> DialogControl.Check(field.key, field.label, checks.getValue(field.key))
                is Field.Text -> DialogControl.Text(field.key, field.value)
            }
        } + DialogControl.Button(OK, words.ok) + DialogControl.Button(CANCEL, words.cancel)

    /** What the form holds now. */
    fun values(): FormValues = FormValues(boxes.mapValues { it.value.text }, choices.toMap(), checks.toMap())

    /** OK or Cancel, once. */
    private fun finish(ok: Boolean) {
        if (finished) return
        finished = true
        done(if (ok) values() else null)
    }

    override fun focusOn(at: Int) {
        focus = at
    }

    override fun typed(line: DialogControl.Line) {}

    override fun chose(control: DialogControl, at: Int) {
        choices[control.key] = at
    }

    override fun toggled(check: DialogControl.Check) {
        checks[check.key] = !check.checked
    }

    override fun pressed(button: DialogControl.Button) = finish(button.key == OK)

    override fun entered(control: DialogControl): Boolean = true.also { finish(true) }

    private companion object {
        const val OK = "\u0000ok"
        const val CANCEL = "\u0000cancel"
    }
}
