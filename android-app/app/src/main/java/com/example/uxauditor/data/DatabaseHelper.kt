package com.example.uxauditor.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File
import java.util.UUID
import android.net.Uri
import android.webkit.MimeTypeMap
import android.content.ContentResolver

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "ux_auditor.db"
        const val DATABASE_VERSION = 1

        const val TABLE_SESSIONS = "sessions"
        const val TABLE_SCREENS = "screens"

        const val COL_ID = "id"
        const val COL_FLOW_NAME = "flow_name"
        const val COL_DEVICE_MODEL = "device_model"
        const val COL_WIDTH = "screen_width_px"
        const val COL_HEIGHT = "screen_height_px"
        const val COL_STATUS = "status"
        const val COL_CREATED_AT = "created_at"

        const val COL_SCREEN_ID = "id"
        const val COL_SESSION_ID = "session_id"
        const val COL_SEQ_INDEX = "sequence_index"
        const val COL_IMAGE_PATH = "image_path"
        const val COL_TAP_X = "tap_x_pct"
        const val COL_TAP_Y = "tap_y_pct"
        const val COL_TAG = "tag"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createSessions = """
            CREATE TABLE ${TABLE_SESSIONS} (
                ${COL_ID} TEXT PRIMARY KEY,
                ${COL_FLOW_NAME} TEXT,
                ${COL_DEVICE_MODEL} TEXT,
                ${COL_WIDTH} INTEGER,
                ${COL_HEIGHT} INTEGER,
                ${COL_STATUS} TEXT,
                ${COL_CREATED_AT} TEXT
            )
        """.trimIndent()

        val createScreens = """
            CREATE TABLE ${TABLE_SCREENS} (
                ${COL_SCREEN_ID} TEXT PRIMARY KEY,
                ${COL_SESSION_ID} TEXT,
                ${COL_SEQ_INDEX} INTEGER,
                ${COL_IMAGE_PATH} TEXT,
                ${COL_TAP_X} REAL,
                ${COL_TAP_Y} REAL,
                ${COL_TAG} TEXT,
                FOREIGN KEY(${COL_SESSION_ID}) REFERENCES ${TABLE_SESSIONS}(${COL_ID}) ON DELETE CASCADE
            )
        """.trimIndent()

        db.execSQL(createSessions)
        db.execSQL(createScreens)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldV: Int, newV: Int) {
        db.execSQL("DROP TABLE IF EXISTS ${TABLE_SCREENS}")
        db.execSQL("DROP TABLE IF EXISTS ${TABLE_SESSIONS}")
        onCreate(db)
    }

    fun insertSession(id: String, flowName: String, deviceModel: String, w: Int, h: Int) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_ID, id)
            put(COL_FLOW_NAME, flowName)
            put(COL_DEVICE_MODEL, deviceModel)
            put(COL_WIDTH, w)
            put(COL_HEIGHT, h)
            put(COL_STATUS, "active")
            put(COL_CREATED_AT, System.currentTimeMillis().toString())
        }
        db.insert(TABLE_SESSIONS, null, cv)
    }

    fun updateSessionStatus(id: String, status: String) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_STATUS, status)
        }
        db.update(TABLE_SESSIONS, cv, "${COL_ID} = ?", arrayOf(id))
    }

    fun insertScreen(id: String, sessionId: String, seq: Int, path: String, x: Float, y: Float, tag: String?) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_SCREEN_ID, id)
            put(COL_SESSION_ID, sessionId)
            put(COL_SEQ_INDEX, seq)
            put(COL_IMAGE_PATH, path)
            put(COL_TAP_X, x)
            put(COL_TAP_Y, y)
            put(COL_TAG, tag)
        }
        db.insert(TABLE_SCREENS, null, cv)
    }

    fun getLocalScreens(sessionId: String): List<LocalScreen> {
        val list = mutableListOf<LocalScreen>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM ${TABLE_SCREENS} WHERE ${COL_SESSION_ID} = ? ORDER BY ${COL_SEQ_INDEX} ASC",
            arrayOf(sessionId)
        )
        while (cursor.moveToNext()) {
            list.add(
                LocalScreen(
                    id = cursor.getString(cursor.getColumnIndexOrThrow(COL_SCREEN_ID)),
                    sessionId = cursor.getString(cursor.getColumnIndexOrThrow(COL_SESSION_ID)),
                    sequenceIndex = cursor.getInt(cursor.getColumnIndexOrThrow(COL_SEQ_INDEX)),
                    imagePath = cursor.getString(cursor.getColumnIndexOrThrow(COL_IMAGE_PATH)),
                    tapXPct = cursor.getFloat(cursor.getColumnIndexOrThrow(COL_TAP_X)),
                    tapYPct = cursor.getFloat(cursor.getColumnIndexOrThrow(COL_TAP_Y)),
                    tag = cursor.getString(cursor.getColumnIndexOrThrow(COL_TAG))
                )
            )
        }
        cursor.close()
        return list
    }

    fun updateScreenSequence(screenId: String, newSeq: Int) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_SEQ_INDEX, newSeq)
        }
        db.update(TABLE_SCREENS, cv, "${COL_SCREEN_ID} = ?", arrayOf(screenId))
    }

    fun deleteScreen(screenId: String) {
        val db = writableDatabase
        db.delete(TABLE_SCREENS, "${COL_SCREEN_ID} = ?", arrayOf(screenId))
    }
}

data class LocalScreen(
    val id: String,
    val sessionId: String,
    val sequenceIndex: Int,
    val imagePath: String,
    val tapXPct: Float,
    val tapYPct: Float,
    val tag: String?
)

fun copyUriToCache(context: Context, uri: Uri): File? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val extension = getExtensionFromUri(context, uri) ?: "png"
        val file = File(context.cacheDir, "manual_screen_${UUID.randomUUID()}.$extension")
        inputStream.use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        file
    } catch (e: Exception) {
        android.util.Log.e("DatabaseHelper", "Error copying uri to cache", e)
        null
    }
}

fun getExtensionFromUri(context: Context, uri: Uri): String? {
    return if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
        val mimeType = context.contentResolver.getType(uri)
        MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
    } else {
        MimeTypeMap.getFileExtensionFromUrl(uri.toString())
    }
}
