package dev.emassey0135.braillesense.files

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.StrictMode
import java.io.File

/**
 * Choosing a file or a folder through the BrailleSense's own File Manager, as its built-in apps
 * (the Notepad, the web browser) do: its picker works with the braille keys and reads itself on the
 * cells and in speech, as every other screen of the device does.
 *
 * Start the intent for a result, through [launch], and read what comes back with [picked]:
 *
 * ```kotlin
 * private val choose = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
 *     val file = FilePicker.picked(result.resultCode, result.data) ?: return@registerForActivityResult
 *     thread { file.openInput(this).use { … } }
 * }
 *
 * FilePicker.launch(FileRequest(PickKind.OPEN_FILE, extensions = listOf("txt"))) { choose.launch(it) }
 * ```
 *
 * **The File Manager answers with a path alone**, never a Uri it grants: reading or writing what was
 * chosen outside the app's own folders needs all-files access (`MANAGE_EXTERNAL_STORAGE`, which the
 * device's own apps hold), else Android lets the app see only files it made itself. Start it through
 * [launch], not on its own: the folder it opens in goes as a `file:` Uri, as the device's apps send
 * it, which Android refuses to send from an app unless [launch] lets it for that one start.
 *
 * The same shape as the Orbit SDK's `FilePicker`, so an app asks both devices alike.
 */
public object FilePicker {
    /** The File Manager's package. */
    public const val PACKAGE: String = "com.selvashc.filemanager"

    /** The File Manager's picker, which the device's apps name rather than find by its actions. */
    public val COMPONENT: ComponentName get() = ComponentName(PACKAGE, ACTIVITY)

    internal const val ACTIVITY = "com.selvashc.filemanager.picker.PickerActivity"
    internal const val PICK_FILE = "com.selvashc.ACTION_PICK_FILE"
    internal const val SAVE_FILE = "com.selvashc.ACTION_SAVE_FILE"
    internal const val PICK_FOLDER = "com.selvashc.ACTION_PICK_FOLDER"
    internal const val FILE_TYPE_AND_NAME = "com.selvashc.category.FILE_TYPE_AND_NAME"
    internal const val EXTENSIONS = "exts"
    internal const val FOCUSED_FILE = "focused_file"
    internal const val FILE_TYPE = "current_type"
    internal const val ANY_FILE = "*.*"
    internal const val TITLE = "title"
    internal const val PATH = "path"
    internal const val PATH_LIST = "path_list"

    /** Where the File Manager opens when nothing else is said: where the Notepad opens it too. */
    internal const val DOCUMENTS = "/storage/emulated/0/Documents"

    /**
     * The intent that asks for [request], to start for a result through [launch] (see this object's
     * description for why it must be).
     */
    public fun intent(request: FileRequest): Intent = call(request).let { call ->
        Intent(call.action)
            .setComponent(COMPONENT)
            .addFlags(call.flags)
            .setData(call.folder?.let { Uri.fromFile(File(it)) })
            .apply { call.categories.forEach(::addCategory) }
            .putAll(call.extras)
    }

    /**
     * Starts [request]'s intent with [start] (an `ActivityResultLauncher`'s `launch`, or
     * `startActivityForResult`), letting a `file:` Uri go for that one start, and says whether it
     * started: false where the File Manager is not there, or does not let this app start its picker.
     */
    public fun launch(request: FileRequest, start: (Intent) -> Unit): Boolean {
        val intent = intent(request)
        // An app targeting Android 7 or later dies sending a `file:` Uri; this start is the File
        // Manager's own convention, and the policy is put back as soon as it has gone.
        val policy = StrictMode.getVmPolicy()
        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())
        return try {
            start(intent)
            true
        } catch (missing: ActivityNotFoundException) {
            false
        } catch (closed: SecurityException) {
            false
        } finally {
            StrictMode.setVmPolicy(policy)
        }
    }

    /** Whether the File Manager is on this device to answer (its package is in the SDK's `<queries>`). */
    public fun isAvailable(context: Context): Boolean =
        context.packageManager.resolveActivity(Intent().setComponent(COMPONENT), 0) != null

    /**
     * What the person chose, from the result the File Manager gave: null when they cancelled, or
     * when the answer holds no path.
     */
    public fun picked(resultCode: Int, data: Intent?): PickedFile? {
        if (resultCode != Activity.RESULT_OK || data == null) return null
        return picked(data.getStringExtra(PATH), data.getStringArrayListExtra(PATH_LIST))
    }

    /** What was chosen, from the answer's parts: the first of its list, else its path. */
    internal fun picked(path: String?, paths: List<String>?): PickedFile? {
        val chosen = paths?.firstOrNull { it.isNotBlank() } ?: path?.takeIf { it.isNotBlank() } ?: return null
        return PickedFile(chosen, null)
    }

    /** [request] as the File Manager reads it, as the device's own apps put it. */
    internal fun call(request: FileRequest): PickerCall {
        val offered = request.offered
        val extras = buildMap<String, Extra> {
            request.title?.takeIf { it.isNotBlank() }?.let { put(TITLE, Extra.Text(it)) }
            when (request.kind) {
                PickKind.OPEN_FILE -> {
                    put(EXTENSIONS, Extra.TextArray(offered.ifEmpty { listOf(ANY_FILE) }))
                    put(FILE_TYPE, Extra.Text(ANY_FILE))
                }
                PickKind.SAVE_FILE -> {
                    if (offered.isNotEmpty()) put(EXTENSIONS, Extra.TextArray(offered))
                    request.suggestedName?.takeIf { it.isNotBlank() }?.let { put(FOCUSED_FILE, Extra.Text(it)) }
                }
                PickKind.OPEN_FOLDER -> {}
            }
        }
        return PickerCall(
            action = when (request.kind) {
                PickKind.OPEN_FILE -> PICK_FILE
                PickKind.SAVE_FILE -> SAVE_FILE
                PickKind.OPEN_FOLDER -> PICK_FOLDER
            },
            folder = request.startIn?.takeIf { it.isNotBlank() }?.trimEnd('/') ?: DOCUMENTS,
            // A file is picked by its type and name, as the Notepad asks; a folder is not.
            categories = if (request.kind == PickKind.OPEN_FOLDER) emptyList() else listOf(FILE_TYPE_AND_NAME),
            // Gone from the recent apps once it answers, as the device's apps start it.
            flags = Intent.FLAG_ACTIVITY_NO_HISTORY,
            extras = extras,
        )
    }
}
