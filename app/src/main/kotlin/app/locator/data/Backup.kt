package app.locator.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class BackupData(val items: List<Item>, val records: List<LocationRecord>, val places: List<Place> = emptyList())

/**
 * JSON format of data.json inside the backup zip. Photos are stored next to it as photos/<name>.
 * Version 3 added each item's usual place; version 2 added places. Older files can still be imported.
 */
object BackupCodec {
    const val VERSION = 3

    fun encode(items: List<Item>, records: List<LocationRecord>, exportedAt: Long, places: List<Place> = emptyList()): String {
        val root = JSONObject()
        root.put("version", VERSION)
        root.put("exportedAt", exportedAt)
        root.put("places", JSONArray().also { array ->
            places.forEach {
                array.put(JSONObject().put("id", it.id).put("name", it.name).put("parentId", it.parentId ?: JSONObject.NULL))
            }
        })
        root.put("items", JSONArray().also { array ->
            items.forEach {
                array.put(
                    JSONObject()
                        .put("id", it.id).put("name", it.name).put("emoji", it.emoji ?: JSONObject.NULL)
                        .put("captureGps", it.captureGps).put("pinned", it.pinned).put("archived", it.archived)
                        .put("usualPlaceId", it.usualPlaceId ?: JSONObject.NULL)
                        .put("usualText", it.usualText ?: JSONObject.NULL)
                        .put("createdAt", it.createdAt),
                )
            }
        })
        root.put("records", JSONArray().also { array ->
            records.forEach {
                array.put(
                    JSONObject()
                        .put("id", it.id).put("itemId", it.itemId)
                        .put("description", it.description ?: JSONObject.NULL)
                        .put("note", it.note ?: JSONObject.NULL)
                        .put("latitude", it.latitude ?: JSONObject.NULL)
                        .put("longitude", it.longitude ?: JSONObject.NULL)
                        .put("accuracyMeters", it.accuracyMeters?.toDouble() ?: JSONObject.NULL)
                        .put("photo", it.photoPath ?: JSONObject.NULL)
                        .put("placeId", it.placeId ?: JSONObject.NULL)
                        .put("createdAt", it.createdAt),
                )
            }
        })
        return root.toString()
    }

    /** Parses and validates; throws [BackupException] on anything unexpected. */
    fun decode(json: String): BackupData {
        try {
            val root = JSONObject(json)
            val version = root.getInt("version")
            if (version !in 1..VERSION) throw BackupException("Unsupported backup version $version")
            val (places, remap) = decodePlaces(root.optJSONArray("places"))
            val placeIds = remap.keys
            val items = root.getJSONArray("items").objects().map {
                val name = it.getString("name").trim()
                if (name.isEmpty()) throw BackupException("Item without a name")
                val usualPlaceId = if (it.isNull("usualPlaceId")) null else it.getLong("usualPlaceId")
                if (usualPlaceId != null && usualPlaceId !in placeIds) throw BackupException("Item with an unknown usual place")
                Item(
                    id = it.getLong("id"),
                    name = name,
                    emoji = it.optNullableString("emoji"),
                    captureGps = it.getBoolean("captureGps"),
                    pinned = it.getBoolean("pinned"),
                    archived = it.getBoolean("archived"),
                    searchText = SearchText.normalize(name),
                    createdAt = it.getLong("createdAt"),
                    usualPlaceId = usualPlaceId?.let(remap::getValue),
                    usualText = it.optNullableString("usualText"),
                )
            }
            val ids = items.map { it.id }.toSet()
            if (ids.size != items.size) throw BackupException("Duplicate item ids")
            val records = root.getJSONArray("records").objects().map {
                val photo = it.optNullableString("photo")
                if (photo != null && !PhotoStore.isValidPath(photo)) throw BackupException("Invalid photo path")
                val itemId = it.getLong("itemId")
                if (itemId !in ids) throw BackupException("Record for an unknown item")
                val placeId = if (it.isNull("placeId")) null else it.getLong("placeId")
                if (placeId != null && placeId !in placeIds) throw BackupException("Record in an unknown place")
                LocationRecord(
                    id = it.getLong("id"),
                    itemId = itemId,
                    description = it.optNullableString("description"),
                    note = it.optNullableString("note"),
                    latitude = if (it.isNull("latitude")) null else it.getDouble("latitude"),
                    longitude = if (it.isNull("longitude")) null else it.getDouble("longitude"),
                    accuracyMeters = if (it.isNull("accuracyMeters")) null else it.getDouble("accuracyMeters").toFloat(),
                    photoPath = photo,
                    placeId = placeId?.let(remap::getValue),
                    createdAt = it.getLong("createdAt"),
                )
            }.withUniquePhotos()
            if (records.map { it.id }.toSet().size != records.size) throw BackupException("Duplicate record ids")
            return BackupData(items, records, places)
        } catch (e: JSONException) {
            throw BackupException("The backup file is damaged", e)
        }
    }

    /** A photo file belongs to one record: if a file claims a path an earlier record already uses, it loses it. */
    private fun List<LocationRecord>.withUniquePhotos(): List<LocationRecord> {
        val used = mutableSetOf<String>()
        return map { if (it.photoPath != null && !used.add(it.photoPath)) it.copy(photoPath = null) else it }
    }

    /**
     * Validates the place tree and returns the places with parents before children (foreign keys need that
     * order), plus a map from every id in the file to the id it ends up with: siblings with the same name
     * (accent- and case-insensitive) are merged into the first one, as the app itself never creates them.
     */
    private fun decodePlaces(array: JSONArray?): Pair<List<Place>, Map<Long, Long>> {
        if (array == null) return emptyList<Place>() to emptyMap()
        val places = array.objects().map {
            val name = it.getString("name").trim()
            if (name.isEmpty()) throw BackupException("Place without a name")
            Place(id = it.getLong("id"), name = name, parentId = if (it.isNull("parentId")) null else it.getLong("parentId"))
        }
        val byId = places.associateBy { it.id }
        if (byId.size != places.size) throw BackupException("Duplicate place ids")
        fun depth(place: Place): Int {
            var depth = 0
            var current = place
            val seen = mutableSetOf(place.id)
            while (true) {
                val parent = current.parentId ?: return depth
                current = byId[parent] ?: throw BackupException("Place inside an unknown place")
                if (!seen.add(current.id)) throw BackupException("Places contain a loop")
                depth++
            }
        }
        // Compute every depth up front: sortedBy skips its selector for a single element, which would skip validation.
        val depths = places.associateWith { depth(it) }
        val ordered = places.sortedBy { depths.getValue(it) }
        val canonical = mutableMapOf<Pair<Long?, String>, Long>()
        val remap = mutableMapOf<Long, Long>()
        val kept = mutableListOf<Place>()
        for (place in ordered) {
            val parent = place.parentId?.let(remap::getValue)
            val key = parent to SearchText.normalize(place.name)
            val existing = canonical[key]
            if (existing != null) {
                remap[place.id] = existing
            } else {
                canonical[key] = place.id
                remap[place.id] = place.id
                kept += place.copy(parentId = parent)
            }
        }
        return kept to remap
    }

    private fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }

    private fun JSONObject.optNullableString(key: String): String? = if (isNull(key)) null else getString(key)
}

data class ImportResult(val items: Int, val records: Int, val photos: Int, val places: Int = 0)

/** Export and import of everything as one zip (data.json plus photos/). */
class BackupService(
    private val db: AppDatabase,
    private val photos: PhotoStore,
    private val reminders: ReminderScheduling? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun export(out: OutputStream) = withContext(Dispatchers.IO) {
        // One transaction, so a save from the widget in between cannot produce a record without its item.
        val (items, records, places) = db.withTransaction {
            Triple(db.itemDao().all(), db.locationDao().all(), db.placeDao().all())
        }
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(DATA_ENTRY))
            zip.write(BackupCodec.encode(items, records, clock(), places).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            records.mapNotNull { it.photoPath }.distinct().forEach { path ->
                val file = photos.file(path)
                if (file.isFile) {
                    zip.putNextEntry(ZipEntry(path))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    /**
     * Replaces all data with the backup. Everything is read and validated into a staging folder first, so a
     * damaged file leaves the current data untouched.
     */
    suspend fun import(input: InputStream): ImportResult = withContext(Dispatchers.IO) {
        val staging = File(photos.root, "import-staging").also { it.deleteRecursively(); it.mkdirs() }
        try {
            var json: String? = null
            var total = 0L
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    when {
                        entry.name == DATA_ENTRY -> {
                            val bytes = zip.readLimited(MAX_JSON_BYTES)
                            json = bytes.toString(Charsets.UTF_8)
                        }
                        PhotoStore.isValidPath(entry.name) -> {
                            val target = File(staging, entry.name.substringAfter('/'))
                            target.outputStream().use { out ->
                                val buffer = ByteArray(8192)
                                while (true) {
                                    val n = zip.read(buffer)
                                    if (n < 0) break
                                    total += n
                                    if (total > MAX_TOTAL_BYTES) throw BackupException("The backup is too large")
                                    out.write(buffer, 0, n)
                                }
                            }
                        }
                        // anything else is ignored, which also rules out path traversal entries
                    }
                }
            }
            val data = BackupCodec.decode(json ?: throw BackupException("The backup has no data.json"))
            val available = staging.list()?.toSet().orEmpty()
            // A record whose photo is not in the zip keeps its text but loses the photo reference.
            val records = data.records.map {
                if (it.photoPath != null && it.photoPath.substringAfter('/') !in available) it.copy(photoPath = null) else it
            }
            // Record ids are reused by the backup, so alarms of the old records must not outlive them.
            val staleAlarms = db.locationDao().withReminders().map { it.id }
            db.withTransaction {
                db.locationDao().deleteAll()
                db.itemDao().deleteAll()
                db.placeDao().deleteAll()
                db.placeDao().insertAll(data.places)
                db.itemDao().insertAll(data.items)
                db.locationDao().insertAll(records)
            }
            staleAlarms.forEach { reminders?.cancel(it) }
            photos.deleteAll()
            val dir = photos.prepare()
            // Move (not copy) the staged files: same filesystem, so no extra disk space is needed at this point.
            available.forEach { name ->
                val source = File(staging, name)
                val target = File(dir, name)
                if (!source.renameTo(target)) source.copyTo(target, overwrite = true)
            }
            ImportResult(data.items.size, records.size, available.size, data.places.size)
        } catch (e: IOException) {
            throw BackupException("The backup could not be read", e)
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun ZipInputStream.readLimited(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = read(buffer)
            if (n < 0) break
            if (out.size() + n > limit) throw BackupException("data.json is too large")
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private companion object {
        const val DATA_ENTRY = "data.json"
        const val MAX_JSON_BYTES = 32 * 1024 * 1024
        const val MAX_TOTAL_BYTES = 1024L * 1024 * 1024
    }
}
