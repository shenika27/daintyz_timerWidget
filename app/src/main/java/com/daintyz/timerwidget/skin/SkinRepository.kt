package com.daintyz.timerwidget.skin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.util.Log
import com.daintyz.timerwidget.model.ButtonStyle
import com.daintyz.timerwidget.model.CharacterStates
import com.daintyz.timerwidget.model.ConditionalFrameSet
import com.daintyz.timerwidget.model.FrameSet
import com.daintyz.timerwidget.model.RunningState
import com.daintyz.timerwidget.model.Skin
import com.daintyz.timerwidget.model.TimerButtons
import com.daintyz.timerwidget.model.TimerFont
import com.daintyz.timerwidget.model.TimerSkin
import org.json.JSONObject
import java.io.File

/**
 * 스킨 로드/캐싱 저장소 (설계 문서 6-3).
 *
 * 스캔 소스 우선순위: filesDir/skins/ (다운로드) > assets/skins/ (내장)
 * 동일 skinId가 양쪽에 있으면 filesDir 버전을 사용한다.
 */
object SkinRepository {

    private const val TAG = "SkinRepository"
    private const val SKINS_DIR = "skins"

    @Volatile
    private var cachedSkins: List<Skin>? = null

    @Volatile
    private var cachedBundledSkins: List<Skin>? = null

    private val bitmapCache = java.util.concurrent.ConcurrentHashMap<String, Bitmap>()
    private val typefaceCache = java.util.concurrent.ConcurrentHashMap<String, Typeface>()

    fun loadAllSkins(context: Context): List<Skin> {
        cachedSkins?.let { return it }
        synchronized(this) {
            cachedSkins?.let { return it }
            val downloaded = loadFromFilesDir(context)
            val downloadedIds = downloaded.map { it.skinId }.toSet()
            val builtin = loadFromAssets(context).filter { it.skinId !in downloadedIds }
            val merged = (downloaded + builtin).sortedByDescending { it.isFree }
            cachedSkins = merged
            return merged
        }
    }

    fun findSkin(context: Context, skinId: String): Skin? =
        loadAllSkins(context).firstOrNull { it.skinId == skinId }

    /**
     * assets/skins 에 내장된 스킨만 로드한다. 다운로드 스킨이 같은 skinId를 덮어써도 여기엔 섞이지 않는다.
     * 권한 회수 시 위젯 fallback은 이 목록의 무료 스킨만 사용해야 한다.
     */
    fun loadBundledSkins(context: Context): List<Skin> {
        cachedBundledSkins?.let { return it }
        synchronized(this) {
            cachedBundledSkins?.let { return it }
            val bundled = loadFromAssets(context)
            cachedBundledSkins = bundled
            return bundled
        }
    }

    /**
     * 프레임 PNG를 Bitmap으로 로드.
     * filesDir(다운로드) → assets(내장) 순으로 탐색. 없으면 null.
     */
    fun loadFrameBitmap(context: Context, skinId: String, fileName: String): Bitmap? {
        val key = "$skinId/$fileName"
        bitmapCache[key]?.let { return it }

        val downloadedFile = File(SkinDownloader.skinsDir(context), "$skinId/$fileName")
        if (downloadedFile.exists()) {
            val bitmap = runCatching { BitmapFactory.decodeFile(downloadedFile.absolutePath) }
                .onFailure { Log.e(TAG, "파일 프레임 로드 실패: $key", it) }
                .getOrNull()
            if (bitmap != null) { bitmapCache[key] = bitmap; return bitmap }
        }

        val bitmap = runCatching {
            context.assets.open("$SKINS_DIR/$skinId/$fileName").use { BitmapFactory.decodeStream(it) }
        }.onFailure { Log.e(TAG, "에셋 프레임 로드 실패: $key", it) }.getOrNull()
        if (bitmap != null) bitmapCache[key] = bitmap
        return bitmap
    }

    /**
     * 커스텀 폰트(.ttf)를 Typeface로 로드. filesDir(다운로드) → assets(내장) 순. 없거나 실패하면 null.
     * 위젯 숫자를 비트맵 렌더링할 때 사용한다.
     */
    fun loadTypeface(context: Context, skinId: String, fileName: String): Typeface? {
        val key = "$skinId/$fileName"
        typefaceCache[key]?.let { return it }

        val downloadedFile = File(SkinDownloader.skinsDir(context), "$skinId/$fileName")
        val typeface = if (downloadedFile.exists()) {
            runCatching { Typeface.createFromFile(downloadedFile) }
                .onFailure { Log.e(TAG, "파일 폰트 로드 실패: $key", it) }.getOrNull()
        } else {
            runCatching { Typeface.createFromAsset(context.assets, "$SKINS_DIR/$skinId/$fileName") }
                .onFailure { Log.e(TAG, "에셋 폰트 로드 실패: $key", it) }.getOrNull()
        }
        if (typeface != null) typefaceCache[key] = typeface
        return typeface
    }

    fun clearCache() {
        cachedSkins = null
        cachedBundledSkins = null
        bitmapCache.clear()
        typefaceCache.clear()
    }

    // ---- 소스별 로드 ----

    private fun loadFromAssets(context: Context): List<Skin> {
        val dirs = runCatching { context.assets.list(SKINS_DIR) }.getOrNull().orEmpty()
        return dirs.mapNotNull { dir ->
            runCatching { parseSkinFromAssets(context, dir) }
                .onFailure { Log.e(TAG, "에셋 스킨 파싱 실패: $dir", it) }
                .getOrNull()
        }
    }

    private fun loadFromFilesDir(context: Context): List<Skin> {
        val root = SkinDownloader.skinsDir(context)
        if (!root.exists()) return emptyList()
        return root.listFiles()?.mapNotNull { dir ->
            if (!dir.isDirectory) return@mapNotNull null
            runCatching { parseSkinFromDir(dir) }
                .onFailure { Log.e(TAG, "다운로드 스킨 파싱 실패: ${dir.name}", it) }
                .getOrNull()
        } ?: emptyList()
    }

    // ---- 파싱 ----

    private fun parseSkinFromAssets(context: Context, dir: String): Skin {
        val json = context.assets.open("$SKINS_DIR/$dir/skin.json").use { stream ->
            JSONObject(stream.bufferedReader().readText())
        }
        return parseSkinJson(json, bundled = true)
    }

    private fun parseSkinFromDir(dir: File): Skin {
        val json = JSONObject(File(dir, "skin.json").readText())
        return parseSkinJson(json, bundled = false)
    }

    private fun parseSkinJson(json: JSONObject, bundled: Boolean): Skin {
        val character = json.getJSONObject("character")
        return Skin(
            skinId = json.getString("skinId"),
            name = json.getString("name"),
            isFree = json.optBoolean("isFree", false),
            prestige = json.optBoolean("prestige", false),
            description = json.optString("description").ifBlank { null },
            createdAt = json.optString("createdAt").ifBlank { null },
            bundled = bundled,
            character = CharacterStates(
                stop = parseFrameSet(character.getJSONObject("stop")),
                running = parseRunningState(character.getJSONObject("running")),
                pause = character.optJSONObject("pause")?.let { parseFrameSet(it) },
                complete = parseFrameSet(character.getJSONObject("complete"))
            ),
            // timer 블록 생략 시 기본 스킨(내장 박스/구분선/기호 버튼)으로 폴백.
            timer = parseTimerSkin(json.optJSONObject("timer") ?: JSONObject())
        )
    }

    private fun parseTimerSkin(obj: JSONObject): TimerSkin = TimerSkin(
        background = obj.optString("background").ifBlank { null },
        showBox = obj.optBoolean("showBox", true),
        buttonStyle = ButtonStyle.fromKey(obj.optString("buttonStyle", "default")),
        buttons = obj.optJSONObject("buttons")?.let { b ->
            TimerButtons(
                minus = b.optString("minus").ifBlank { null },
                plus = b.optString("plus").ifBlank { null },
                play = b.optString("play").ifBlank { null },
                pause = b.optString("pause").ifBlank { null },
                stop = b.optString("stop").ifBlank { null }
            )
        },
        font = obj.optJSONObject("font")?.let { f ->
            TimerFont(
                family = f.optString("family").ifBlank { null },
                color = f.optString("color").ifBlank { null },
                sizeSp = if (f.has("sizeSp")) f.optDouble("sizeSp").toFloat() else null,
                file = f.optString("file").ifBlank { null }
            )
        }
    )

    private fun parseRunningState(obj: JSONObject): RunningState {
        val default = parseFrameSet(obj.getJSONObject("default"))
        val conditional = obj.keys().asSequence()
            .filter { it != "default" }
            .mapNotNull { key ->
                val child = obj.optJSONObject(key) ?: return@mapNotNull null
                if (!child.has("frames")) return@mapNotNull null
                ConditionalFrameSet(
                    key = key,
                    frameSet = parseFrameSet(child),
                    condition = child.optJSONObject("condition")?.let { jsonToMap(it) } ?: emptyMap()
                )
            }.toList()
        return RunningState(default = default, conditional = conditional)
    }

    private fun parseFrameSet(obj: JSONObject): FrameSet {
        val framesArray = obj.getJSONArray("frames")
        val frames = (0 until framesArray.length()).map { framesArray.getString(it) }
        return FrameSet(frames = frames, frameDurationMs = obj.optLong("frameDurationMs", 1000L))
    }

    private fun jsonToMap(obj: JSONObject): Map<String, Any?> =
        obj.keys().asSequence().associateWith { obj.opt(it) }
}
