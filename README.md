# BrailleSense SDK (unofficial)

An Android library for apps that speak and write braille themselves on the **HIMS BrailleSense 7**.
With it an app takes the braille keys and the snap-on QWERTY keyboard while it is in front, speaks
in the person's own voice, and puts cells on the display in their braille table. Its lists, menus,
edit boxes and dialogs read, move and echo like the device's own, following the person's Global
Options.

It was worked out from the device, its manual and its built-in apps. HIMS did not write it and does
not support it. The device's services are reached through interfaces written here to the device's
own descriptors; none of HIMS' code is included.

Everything in it follows the BrailleSense's own apps: nothing in its API is there for another
device's sake (see [Beside the Orbit SDK](#beside-the-orbit-sdk)).

## What an app needs

- **A package named `com.extra.han.*`.** The device's screen reader yields the braille keys only to
  such a package, by its name alone, and the launcher lists such apps in its Programs menu. Under
  any other name an app gets no braille keys.
- **Android 15, arm64**: every BrailleSense 7. The library's `minSdk` is 28.
- **One APK for every display.** The SDK measures how many cells the display has as it takes the
  output (a long line wrapped by the device's service and its lines counted, `BrailleDisplay.cellsWide`)
  and pans to fit, so nothing is built per model; until the service answers it lays out 40. Measured
  on a 40-cell unit; a 32-cell (H732B) or 20-cell one is still to be tried.
- **The Android NDK**, to build liblouis. The library's `build-louis.sh` downloads liblouis' release,
  checks its hash and builds `libbraillesense-louis.so` with the NDK's clang, and gathers its tables
  into the library's assets; Gradle runs it before each build, and your APK merges both. It finds
  the newest NDK under your SDK, or the one `ANDROID_NDK_HOME` names (CI uses 30.0.16248370).

## Adding it to a build

There is no published artifact yet. Add the repository to your project as a git submodule (or copy
it), and include it as a module:

```kotlin
// settings.gradle.kts
include(":braillesense-sdk")
project(":braillesense-sdk").projectDir = file("braillesense-sdk")

// app/build.gradle.kts
dependencies {
    implementation(project(":braillesense-sdk"))
}
```

Its build file takes two plugin aliases from your version catalog, `android-library` (the Android
Gradle plugin, 9.x) and the `junit` library for its tests; add them if your catalog lacks them (see
this repository's `gradle/libs.versions.toml`). It applies Dokka by its own version.

On its own the repository is a Gradle build too: the library at the root and `sample/`.

## Quick start

`sample/` is this app, built by CI: a list of fruit, with Add on the screen's menu and Delete on
each row's.

```kotlin
class MainActivity : Activity() {
    private val session by lazy { BrailleSenseSession(this, BuildConfig.VERSION_NAME) }
    private lateinit var ui: BrailleSenseUi
    private val fruit = mutableListOf("Apple", "Banana", "Cherry")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = BrailleSenseUi.on(session)
        ui.onEmpty = ::finish
        session.created()
        ui.list(
            "Fruit",
            items = {
                fruit.map { name ->
                    Item(name, name, open = { ui.message(name) }, actions = listOf(Action("Delete", 'd', asks = true) { remove(name) }))
                }
            },
            actions = { listOf(Action("Add", 'a', asks = true) { add() }) },
        )
    }

    private fun add() = ui.launch {
        val name = ui.askLine("Name") ?: return@launch
        val size = ui.choose("Size", listOf("Small", "Large")) ?: return@launch
        val added = "$name, ${if (size == 0) "small" else "large"}"
        fruit += added
        ui.land(added)                 // moved to what was made, and said
    }

    private fun remove(name: String) = ui.launch {
        if (ui.confirm("Delete $name?")) {
            fruit.remove(name)
            ui.announce("Deleted")
            ui.refresh()
        }
    }

    override fun onResume() { super.onResume(); session.resumed() }
    override fun onPause() { session.paused(); super.onPause() }   // first thing
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); session.windowFocus(hasFocus) }
    override fun onDestroy() { session.destroyed(); super.onDestroy() }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean = session.keyEvent(event) || super.dispatchKeyEvent(event)
}
```

`BrailleSenseSession` is the device for one activity: it reads the person's Global Options and
follows them, reaches the output service, installs liblouis' tables, and turns both keyboards into
`Key`s on the main thread, all held only while the activity is resumed and its window has focus.
`BrailleSenseUi.on(session)` puts the screen stack over it. The SDK depends on nothing but Android:
`launch` runs the `suspend` asks without a coroutine library (use your own scope if you have one).

## The screen stack

`BrailleSenseUi` is a stack of screens that takes the keys and does all the speaking and showing,
much as BTSpeak's `dialogs` module does for a BTSpeak app: open a list, then ask with a menu, a
line, a yes-or-no question, a choice or a form. Each ask gives its answer to a callback, or returns
it from a `suspend` twin, exactly once, after its screen has closed: so what the app does next is
said after the screen under it.

| | |
|---|---|
| `list(title, items, empty, actions, moved, find)` | A screen of `Item`s, read again from `items` at each `refresh`. `actions` is the screen's menu (Space-M or F2). `moved` hears the person's moves; `find` takes a first letter for an app that finds rows itself. Returns the `ListScreen`. |
| `menu(items)` | The device's menu of `MenuItem`s (submenus, letters, hot keys, the dialog sign). Gives the chosen item's `value`, or null after saying Cancel. |
| `askLine(prompt, initial, hint, multiLine)` | An edit box with the stock box's keys. Gives its text at Enter, or null at Escape. Several lines are a form of one field with OK and Cancel. |
| `confirm(question, default)` | Yes and No as the device's prompt buttons; Space and Backspace step between them. Escape is No. |
| `choose(title, options, selected)` | A list to pick one of with Enter. Gives its place, or null. |
| `form(title, fields)` | A dialog of `Field.Line`, `Field.Choice`, `Field.Check` and `Field.Text`, then OK and Cancel, said "… dialog" as it opens. Tab and Shift-Tab move between controls, each read in its role. Gives `FormValues` at OK (`text(key)`, `choice(key)`, `checked(key)`, which throw for a key no field has), or null. |
| `refresh()` | Your data changed: screens that no longer `stand` close, and the screen in front is read again, the row in hand kept by its key. Nothing is said unless the person's own key asked for the change (a check box, folding), so a sync never cuts off what is being read. |
| `land(key)` | You moved what is in hand yourself (a row made, a row gone to): a refresh that puts `key` in hand and says the line. |
| `announce(text)` | What you have to say of a change: said with the line that follows it, as one thing, and kept on the cells for the person's Message Display Time, since someone reading braille alone hears none of it. |
| `batch { … }` | Pushes and closes that say nothing until the block ends; then only where the person ends up is said. |
| `push`, `close`, `top`, `screens`, `onEmpty` | The stack itself. `onEmpty` is called as the last screen closes (Escape on the first): finish the activity. Exit (Space-Z) calls `onExit`, by default the same. |

An `Item` is `Item(key, text, open, actions, letter, detail, expanded, fold, toggle, delete, title)`
and then this device's own `braille`, `finding`, `command`, `role` and `asks`:

- **Enter** or a routing key runs `open`; **Enter-I** or the Application key opens its `actions`;
  Space or Enter with its **`letter`** runs `open` from anywhere in the list.
- **Space** runs `toggle` (a check box) and **Space-D** `delete`.
- A row that folds says whether it is `expanded`: **Space-6** unfolds a folded row and **Space-3**
  folds an unfolded one, through `fold(open)`. What `toggle` and `fold` change is said once your
  `refresh` shows it.
- `detail` is read after the text, ahead of the row's number ("3/12"), which the device adds while
  Index Reporting is on.

What every screen shares is the stack's, as the device's own apps have it: Space-R repeats the line;
the scroll keys pan, and move on to the next row at a line's end; a touch with a Cursor button
routes (in an edit box it only moves the cursor); an edit box takes the stock box's keys, and asks
"Delete selected text?" as a prompt; Space-M or F2 opens the screen's menu; Escape closes the screen
in front, or answers an ask with nothing.

`UiWords` holds the few words the stack says itself (OK, Cancel, Yes, No, "No items", "Nothing on
x"…), in the device's English; pass your own for another language. A screen of your own subclasses
`Screen`: its `frame()`, and where it wants them `heading`, `menu()`, `back()` (Escape), `letter()`
(Space or Enter with a letter nothing took), `stands()` (closed at the next refresh when false) and
`closed()`.

### A dialog of your own

`form` keeps what the person makes of its fields and gives it all at OK. A dialog that has to
answer as the person goes (a value refused and put back, a field that changes another, buttons of
its own) subclasses `DialogFrame` instead, and its screen's `frame()` returns it. You keep every
value; the frame asks for `controls` (and `focus`, the place of the one in hand) at every key and
tells you what the person did, and you change what you hold:

| | |
|---|---|
| `DialogControl.Line(key, label, box, hint)` | An edit box: `box` is a `TextBox` you keep while the dialog is open, of several lines where it is. `typed(line)` as its keys change it. |
| `DialogControl.Choice(key, label, options, selected, finding)` | A combo box: Space and Backspace go round its options, a letter to the next one `finding` starts with. `chose(control, at)`. |
| `DialogControl.ListBox(key, label, options, selected, finding)` | A control that is itself a list: Space-1 and Space-4 move in it, stopping at its ends. `chose(control, at)`, and Enter is `entered`. |
| `DialogControl.Check(key, label, checked)` | A check box: Space, Backspace or Enter is `toggled(check)`. |
| `DialogControl.Text(key, value)` | A line to read. |
| `DialogControl.Button(key, label, letter, asks)` | Enter, a routing key or Space or Enter with its letter is `pressed(button)`. |

Tab and Shift-Tab are `focusOn(at)`, going round. Enter in a line of one, a combo box or a list box
is `entered(control)`, which presses whatever the dialog does with it and says whether it pressed
anything (a combo box pressing nothing goes on to the next control). Space-4 in a line of one, which
its box leaves, is `below(line)`, for what you offer at its cursor. `prompt` makes a question of
buttons alone the device's prompt buttons, stepped with Space and Backspace. Escape is the screen's
`back()`, so what closing the dialog does is yours. Each control is read in the device's role, with
its label, its value and, in a combo box or a list box, "2/5".

## Beside the Orbit SDK

The Orbit SDK, for the Orbit Speak and Orbit Player, is built the same way from its own device.
Where the two devices agree, the two stacks use the same names (`list`, `menu`, `askLine`,
`confirm`, `choose`, `form`, `refresh`, `land`, `announce`, `Item`, `Action`, `MenuItem`, `Field`,
`FormValues`), so an app for both reads alike; where they differ, each follows its own device, and
neither is kept in step with the other.

## Choosing a file or a folder

`FilePicker` (`dev.emassey0135.braillesense.files`) asks the device's own File Manager to choose, as
its built-in apps (the Notepad, the web browser) do: a file to open, where to save one, or a folder.
Its picker works with the braille keys and reads itself as every other screen of the device does.

```kotlin
private val choose = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
    val file = FilePicker.picked(result.resultCode, result.data) ?: return@registerForActivityResult
    thread { file.openInput().use { input -> … } }
}

FilePicker.launch(FileRequest(PickKind.OPEN_FILE, title = "Open", extensions = listOf("txt"))) { choose.launch(it) }
```

- **`FileRequest`**: `kind` (`OPEN_FILE`, `SAVE_FILE`, `OPEN_FOLDER`), `title`, `extensions` (without
  their dots; opening lists only those, every file when there are none; saving offers them, or the
  suggested name's own), `suggestedName` for a save, and `startIn`, the folder to open in
  (`/storage/emulated/0/Documents` when none is given, as the Notepad opens it).
- **`launch(request, start)`** builds the intent and starts it with `start`, and says whether it
  started (false where the File Manager is not there or does not let the app in). Start it through
  `launch`: the folder goes as a `file:` Uri, as the device's apps send it, which Android lets an
  app send only while `launch` allows it. `intent(request)` is the intent alone, and
  `isAvailable(context)` whether the File Manager is there.
- **`picked(resultCode, data)`** is what was chosen, or null when the person cancelled: a
  `PickedFile` with its `path`. `name` and `parent` give its name and folder, the folder to start the
  next request in, and `openInput()` and `openOutput()` read and write it.
- **The File Manager answers with a path alone**, never a Uri it grants. Outside the app's own
  folders Android lets an app read only the files it made itself, and make files only in shared
  folders such as Documents and Download, unless it holds all-files access
  (`MANAGE_EXTERNAL_STORAGE`, which the device's own apps hold; the person allows it under Settings,
  Apps, Special app access). An app that opens files the person brought to the device needs it.

## Alarms, reminders and notifications

An app that tells the person something at a time (an alarm, a reminder) has three things to get
past on this device, and the SDK has a piece for each.

1. **Android must let the alarm go off.** The BrailleSense runs MediaTek's DuraSpeed, which
   restricts every app not switched on in its own list; Android then holds the app's alarms back
   until it is opened, so nothing goes off. `Background.restricted(context)` says whether it is so,
   and `Background.openSettings(context)` opens the app's own battery page, where Run in Background
   allows it (DuraSpeed's own list is not open to other apps). Ask once, as the app first finds
   itself restricted, where the person hears it, then say it where the app's settings are.
2. **A notification is posted as on any phone**, on a channel of the app's own, with its buttons. On
   this device it only sounds and vibrates: the device reads it once the person opens the
   notification list.
3. **So it comes to the front and is said there**, as the Schedule Manager rings.
   `Foreground.open(context, activity, extras)` wakes the device and starts the app's activity over
   whatever is shown, which Android allows only an app that may display over other apps:
   `Foreground.allowed(context)` says whether it may, and `Foreground.openSettings(context)` opens
   where the person allows it, the app's own page where Android lets it, else the list of every
   app. No root is needed. Say it as a `DialogFrame`: its message as a `Text`, then its buttons with
   their letters, and let it ring no more than its notification did. Once it is dealt with (or
   Escape pressed on it), go back to where the person was with `Foreground.returnToPrevious(activity)`,
   but only when `Foreground.openedFromBackground(intent)` says the app came to the front for it, and
   not where a button carries on in the app.

```kotlin
// As the alarm goes off, beside its notification:
notificationManager.notify(id, notification)
if (!Foreground.open(context, MainActivity::class.java, mapOf("alarm" to id))) {
    // Not allowed to come to the front: the notification is all the person gets.
}

// In the activity, from onCreate and onNewIntent:
val cameForIt = Foreground.openedFromBackground(intent)
Foreground.heard(intent)
// … show the alarm's dialog; once it is dealt with:
if (cameForIt) Foreground.returnToPrevious(this)
```

- **`Foreground.open`** is false, starting nothing, where the app may not come to the front; a
  `singleTask` activity already running gets the extras in `onNewIntent`. **`wake`** wakes the device
  alone, for a moment, from its deeper sleep too.
- **`openedFromBackground(intent)`** is whether `open` started this intent; **`heard(intent)`** takes
  the mark out once read, so the activity made again does not go back of itself.
- **`returnToPrevious(activity)`** puts the app's task behind whatever was in front, its state kept.

## Below the stack

An app can work the frames and the display itself:

| Package | |
|---|---|
| `session` | `BrailleSenseSession`, and `Claim` and `KeyDelivery`: holding the keys and display only while resumed and focused, and moving a key from the binder thread to the app's. |
| `input` | `Key`, what a press means on any screen, named by the manual's commands; `Chords.decode` (a braille-key response, the touch cursor's routing included) and `Chords.letter(dots)`; `Qwerty` and `QwertyKeyboard`, the snap-on keyboard; `HanInputClient`. |
| `output` | `HanOutputClient` (an `Output`): speech in the person's own voice settings and raw cells; `BrailleDisplay`: `say`, `silence`, `show` a `DisplayLine` in the person's table, panning with word wrap, `routed`, `selecting`, `cursorLine`. |
| `braille` | `BrailleSettings`, the person's Global Options; `BrailleTables`, `Louis` and its `Translator`. |
| `reading` | `deviceReading(DeviceLine, LineSettings, BrailleSigns)`: a control as the device reads it, its role's word and symbol (`ControlRole`), letter, hot key, sign and place, where the person's settings put them. |
| `ui` | `Frame`s that take their keys and give what to say and show (`Rendered`): `ListFrame` of `Row`s, `MenuFrame` of `MenuEntry`s, `LineFrame`, `DialogFrame` over `DialogControl`s an app keeps; `Letters` and `DeviceLetters`; `Readings`; `Messages`. |
| `edit` | `TextBox` and `LineEditor`, an edit box held in cells as typed; `BoxEditing`, the stock box's keys and what it says for each; `CellEcho`, `EditWords`, `Clipboard`, `SystemClipboard`. |

A list without the stack, drawn and said by the app itself:

```kotlin
val frame = ListFrame(
    "fruit", "Fruit", listOf(Row("a", "Apple"), Row("b", "Banana")), selected = null,
    empty = "Nothing here", letters = DeviceLetters, readings = SettingsReadings(session.settings),
)
session.onKey = { key ->
    if (frame.key(key)) {
        session.display.show(frame.rendered)
        session.display.say(frame.rendered.speech)
    }
}
```

## Testing

The JVM tests (`./gradlew test`) cover the chords, the claim, panning, the cell echo, reading a
line, the edit box, the lists and menus, the letters, and the screen stack and its asks. Test your
own screens the same way: build `BrailleSenseUi` over a `BrailleDisplay` of an `Output` that records
what is said, a `Translator` of your own (liblouis is native), and `speechDelay = 0`, then `press`
keys and look at what was said. `src/test/.../UiTest.kt` does exactly this.

On a device:

- **Inject a chord** from the shell: `adb shell service call han_brailleinput 1 i32 <chord> i32 0 i32 0`,
  the chord as `Chords` has its bits (Space is `0x2000`, dot 1 `0x1000`…).
- **See what was said and shown**: `BrailleSenseSession(activity, version, trace = true, tag = "MyOutput")`
  logs every line sent to the output service at info level, under the tag.
- **See what a key really sends**, in a debuggable build: `adb logcat -s BrailleSenseKeys` has each
  chord the input service hands over as it came (its code, value and data, and whether it was
  taken), what `Chords` made of it, and each key event the window got.

[docs/DEVICE.md](docs/DEVICE.md) holds what the device does that an app has to know: its keys, its
Global Options and how it reads a control.

## API documentation

`./gradlew dokkaGenerate` writes it to `build/dokka/html`; CI keeps it as an artifact.

## Licence

Apache License 2.0: see [LICENSE](LICENSE) and [NOTICE](NOTICE). liblouis, which the SDK builds and
loads as a separate shared library, is under the LGPL 2.1 or later, and its tables under their own
licences.
