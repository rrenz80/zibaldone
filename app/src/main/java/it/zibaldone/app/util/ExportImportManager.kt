package it.zibaldone.app.util

import android.content.Context
import android.net.Uri
import it.zibaldone.app.model.BoardElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Serializable manifest stored inside every .zib package.
 * It describes the complete board state (camera + elements),
 * so a board imported on another tablet restores exactly the same view.
 */
@Serializable
data class BoardManifest(
    val version: Int = 1,
    val name: String = "Untitled board",
    val panX: Float = 0f,
    val panY: Float = 0f,
    val zoom: Float = 1f,
    val elements: List<BoardElement> = emptyList()
)

/**
 * Result of loading a .zib package.
 * [mediaFiles] maps the package-relative name of each reference image
 * (e.g. "media/photo1.jpg") to the file it was extracted to.
 */
data class LoadedBoard(
    val manifest: BoardManifest,
    val mediaFiles: Map<String, File>,
    val packageDir: File
)

/**
 * Outcome of an export. [missingMedia] counts the reference images that
 * could not be found on disk: they are reported instead of being dropped in
 * silence, which used to produce a .zib whose manifest pointed at images
 * the archive did not contain.
 */
data class SavedBoard(
    val missingMedia: Int
)

/**
 * Manages the portable .zib package format.
 *
 * The package is a ZIP archive with a stable layout:
 *
 *   manifest.json   board state (camera + element list, JSON)
 *   media/          reference images used on the board
 *
 * Because the reference images are embedded, a board travels with
 * its inspiration sources and can be opened on any device with the app.
 */
object ExportImportManager {
    private val json =
        Json {
            ignoreUnknownKeys = true
        }

    /**
     * Saves [manifest] (plus the reference images in [mediaSources])
     * as a .zib archive at [outputUri].
     *
     * Runs on [Dispatchers.IO]: zipping a board full of photos is heavy file
     * work, and the callers launch it from `viewModelScope` (a Main
     * dispatcher), so without this the whole export froze the UI thread.
     */
    suspend fun saveBoard(
        context: Context,
        outputUri: Uri,
        manifest: BoardManifest,
        mediaSources: Map<String, File>
    ): SavedBoard =
        withContext(Dispatchers.IO) {
            val output =
                context.contentResolver.openOutputStream(outputUri)
                    ?: throw IllegalStateException("Could not open writable stream for: $outputUri")

            var missing = 0
            output.use { raw ->
                ZipOutputStream(raw.buffered()).use { zipOut ->
                    zipOut.putNextEntry(ZipEntry("manifest.json"))
                    val manifestJson = json.encodeToString(BoardManifest.serializer(), manifest)
                    zipOut.write(manifestJson.encodeToByteArray())
                    zipOut.closeEntry()

                    for ((entryName, file) in mediaSources) {
                        if (file.exists() && file.length() > 0) {
                            zipOut.putNextEntry(ZipEntry(entryName))
                            file.inputStream().use { it.copyTo(zipOut) }
                            zipOut.closeEntry()
                        } else {
                            missing++
                        }
                    }
                }
            }
            SavedBoard(missingMedia = missing)
        }

    /**
     * Reads a .zib package from [inputUri], extracts it into PERSISTENT
     * app storage and parses the manifest.
     *
     * The extraction target is `filesDir/imports/<ts>/`, not `cacheDir`:
     * imported boards keep pointing at these files for the rest of their
     * life, and Android may wipe the cache directory at any moment when it
     * needs space — which silently broke every reference image of an
     * imported board. Older import directories are pruned on success (the
     * board they belonged to has just been replaced), so storage does not
     * grow without bound.
     *
     * Runs on [Dispatchers.IO] for the same reason as [saveBoard].
     */
    suspend fun loadBoard(
        context: Context,
        inputUri: Uri
    ): LoadedBoard =
        withContext(Dispatchers.IO) {
            val importsRoot = File(context.filesDir, "imports")
            val packageDir = File(importsRoot, System.currentTimeMillis().toString())
            packageDir.mkdirs()
            val safeRoot = packageDir.canonicalPath + File.separator

            context.contentResolver.openInputStream(inputUri)?.use { raw ->
                ZipInputStream(raw.buffered()).use { zipIn ->
                    var entry: ZipEntry? = zipIn.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val outFile = File(packageDir, entry.name)
                            // Zip-slip protection. The trailing separator
                            // matters: a bare prefix check also accepted
                            // sibling directories sharing the prefix
                            // (".../imports/123" vs ".../imports/1234evil").
                            if (!outFile.canonicalFile.path.startsWith(safeRoot)) {
                                throw SecurityException("Blocked unsafe zip entry: ${entry.name}")
                            }
                            outFile.parentFile?.mkdirs()
                            outFile.outputStream().use { out -> zipIn.copyTo(out) }
                        }
                        entry = zipIn.nextEntry
                    }
                }
            } ?: throw IllegalStateException("Could not open input stream for: $inputUri")

            val manifestFile = File(packageDir, "manifest.json")
            if (!manifestFile.exists()) {
                throw IllegalStateException("Invalid .zib package: manifest.json is missing")
            }
            val manifest = json.decodeFromString(BoardManifest.serializer(), manifestFile.readText())
            if (manifest.version > CURRENT_FORMAT_VERSION) {
                throw IllegalStateException(
                    "Unsupported .zib version ${manifest.version} " +
                        "(this app reads up to $CURRENT_FORMAT_VERSION)"
                )
            }

            val mediaDir = File(packageDir, "media")
            val mediaFiles =
                if (mediaDir.exists()) {
                    mediaDir.walkTopDown()
                        .filter { it.isFile }
                        .associate { "media/" + it.name to it }
                } else {
                    emptyMap()
                }

            // The previously imported board is no longer on screen.
            importsRoot.listFiles()
                ?.filter { it.isDirectory && it.name != packageDir.name }
                ?.forEach { it.deleteRecursively() }

            LoadedBoard(
                manifest = manifest,
                mediaFiles = mediaFiles,
                packageDir = packageDir
            )
        }

    /** Highest .zib format version this build knows how to read. */
    const val CURRENT_FORMAT_VERSION = 1
}
