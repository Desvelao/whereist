package app.locator.data

import java.io.File
import java.util.UUID

/** Photos live in app-private storage; the database keeps paths relative to [root] (the app files dir). */
class PhotoStore(val root: File) {
    private val dir = File(root, DIR)

    /** A new, unused relative path such as photos/<uuid>.jpg. The file is not created. */
    fun newPath(): String = "$DIR/${UUID.randomUUID()}.jpg"

    /** Resolves a relative path, refusing anything that is not a plain file name inside photos/. */
    fun file(relative: String): File {
        require(isValidPath(relative)) { "Invalid photo path" }
        return File(root, relative)
    }

    fun prepare(): File {
        dir.mkdirs()
        return dir
    }

    fun delete(relative: String?) {
        if (relative != null && isValidPath(relative)) File(root, relative).delete()
    }

    fun deleteAll() {
        dir.listFiles()?.forEach { it.delete() }
    }

    companion object {
        const val DIR = "photos"
        private val VALID = Regex("$DIR/[A-Za-z0-9][A-Za-z0-9._-]{0,100}")

        fun isValidPath(relative: String): Boolean = VALID.matches(relative) && !relative.contains("..")
    }
}
