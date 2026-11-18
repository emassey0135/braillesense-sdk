package dev.emassey0135.braillesense

import android.content.Intent
import dev.emassey0135.braillesense.files.Extra
import dev.emassey0135.braillesense.files.FilePicker
import dev.emassey0135.braillesense.files.FileRequest
import dev.emassey0135.braillesense.files.PickKind
import dev.emassey0135.braillesense.files.PickedFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FilePickerTest {
    @Test fun openingAFileAsksByTypeAndNameInDocumentsByDefault() {
        val call = FilePicker.call(FileRequest(PickKind.OPEN_FILE, title = "Import", extensions = listOf(".JSON", "csv")))
        assertEquals("com.selvashc.ACTION_PICK_FILE", call.action)
        assertEquals("/storage/emulated/0/Documents", call.folder)
        assertEquals(listOf("com.selvashc.category.FILE_TYPE_AND_NAME"), call.categories)
        assertEquals(Intent.FLAG_ACTIVITY_NO_HISTORY, call.flags)
        assertEquals(Extra.TextArray(listOf("json", "csv")), call.extras["exts"])
        assertEquals(Extra.Text("*.*"), call.extras["current_type"])
        assertEquals(Extra.Text("Import"), call.extras["title"])
    }

    @Test fun openingAnyFileOffersEveryType() {
        val call = FilePicker.call(FileRequest(PickKind.OPEN_FILE))
        assertEquals(Extra.TextArray(listOf("*.*")), call.extras["exts"])
    }

    @Test fun savingFocusesTheSuggestedNameAndOffersItsExtension() {
        val call = FilePicker.call(FileRequest(PickKind.SAVE_FILE, suggestedName = "tasks.csv", startIn = "/storage/emulated/0/Download/"))
        assertEquals("com.selvashc.ACTION_SAVE_FILE", call.action)
        assertEquals("/storage/emulated/0/Download", call.folder)
        assertEquals(Extra.TextArray(listOf("csv")), call.extras["exts"])
        assertEquals(Extra.Text("tasks.csv"), call.extras["focused_file"])
        assertNull(call.extras["current_type"])
    }

    @Test fun aFolderIsPickedWithoutTheFileCategory() {
        val call = FilePicker.call(FileRequest(PickKind.OPEN_FOLDER, startIn = "/storage/emulated/0/Backups"))
        assertEquals("com.selvashc.ACTION_PICK_FOLDER", call.action)
        assertEquals("/storage/emulated/0/Backups", call.folder)
        assertEquals(emptyList<String>(), call.categories)
        assertNull(call.extras["exts"])
    }

    @Test fun theAnswerIsTheFirstOfItsListElseItsPath() {
        assertEquals(PickedFile("/storage/emulated/0/a.json", null), FilePicker.picked("/storage/emulated/0/b.json", listOf("/storage/emulated/0/a.json")))
        assertEquals(PickedFile("/storage/emulated/0/b.json", null), FilePicker.picked("/storage/emulated/0/b.json", null))
        assertNull(FilePicker.picked("", emptyList()))
    }

    @Test fun aCancelledPickIsNothing() {
        assertNull(FilePicker.picked(0, null))
    }

    @Test fun aPickedFileNamesItselfAndItsFolder() {
        val file = PickedFile("/storage/emulated/0/Documents/tasks.csv", null)
        assertEquals("tasks.csv", file.name)
        assertEquals("/storage/emulated/0/Documents", file.parent)
    }
}
