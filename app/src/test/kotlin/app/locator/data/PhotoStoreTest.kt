package app.locator.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PhotoStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun generatedPathsAreValid() {
        val store = PhotoStore(tmp.root)
        assertTrue(PhotoStore.isValidPath(store.newPath()))
    }

    @Test
    fun rejectsTraversalAndForeignPaths() {
        listOf("../x.jpg", "photos/../x.jpg", "photos/a/b.jpg", "/etc/passwd", "other/x.jpg", "photos/", "photos/.hidden/..").forEach {
            assertFalse(it, PhotoStore.isValidPath(it))
        }
    }

    @Test
    fun deleteRemovesTheFileAndIgnoresInvalidPaths() {
        val store = PhotoStore(tmp.root)
        store.prepare()
        val path = store.newPath()
        store.file(path).writeText("x")
        store.delete("../../etc/passwd") // must not throw
        store.delete(path)
        assertFalse(store.file(path).exists())
    }
}
