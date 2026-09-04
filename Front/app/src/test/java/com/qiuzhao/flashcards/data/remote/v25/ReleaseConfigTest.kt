package com.qiuzhao.flashcards.data.remote.v25

import com.qiuzhao.flashcards.BuildConfig
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the release environment contract at the configuration level (NV-07 / Task 13):
 * - Release fixes the production base URL at build time and carries no server-edit,
 *   test-mode or fixture switch (the release block's only `buildConfigField` is the fixed URL);
 * - Debug carries an explicit local base-URL override the release cannot reach;
 * - the app version is 2.5.0.
 *
 * JVM unit tests run against the debug variant, so the release facts are asserted on the
 * `build.gradle.kts` configuration text (the stable RED-first source of truth), while the
 * debug facts are asserted on the generated [BuildConfig] class itself.
 */
class ReleaseConfigTest {

    private val releaseBaseUrl = "https://shanka.kbzz1.top"

    @Test
    fun `release fixes the production base URL at build time with no other switch`() {
        val releaseBlock = extractBlock(buildFileText(), "buildTypes", "release")
        assertEquals(
            "Release 只允许固定 API_BASE_URL，不得有服务器编辑/测试/fixture 开关",
            listOf("API_BASE_URL"),
            buildConfigFieldNames(releaseBlock),
        )
        assertEquals(releaseBaseUrl, buildConfigFieldValue(releaseBlock, "API_BASE_URL"))
        assertFalse(
            "Release 块不得出现 fixture/test-mode/mock 开关",
            listOf("FIXTURE", "TEST_MODE", "MOCK").any { releaseBlock.uppercase().contains(it) },
        )
    }

    @Test
    fun `debug carries an explicit local base URL override`() {
        val debugBlock = extractBlock(buildFileText(), "buildTypes", "debug")
        assertTrue(
            "Debug 块必须声明 API_BASE_URL 本地覆盖",
            "API_BASE_URL" in buildConfigFieldNames(debugBlock),
        )
        // 覆盖默认值仍是模拟器 loopback 字面量（property 未传时的回退）。
        assertTrue(
            "Debug 默认地址必须是模拟器 loopback 字面量",
            debugBlock.contains("http://10.0.2.2:8000"),
        )
        assertFalse("Debug 块不得硬编码正式环境", releaseBaseUrl in debugBlock)
    }

    @Test
    fun `the debug property override never leaks into release`() {
        val releaseBlock = extractBlock(buildFileText(), "buildTypes", "release")
        assertFalse(
            "Release 块不得读取 shankaDebugApiBaseUrl（正式地址编译期固定）",
            "shankaDebugApiBaseUrl" in releaseBlock,
        )
        val debugBlock = extractBlock(buildFileText(), "buildTypes", "debug")
        assertTrue(
            "shankaDebugApiBaseUrl 覆盖只应出现在 debug 块",
            "shankaDebugApiBaseUrl" in debugBlock,
        )
    }

    @Test
    fun `the generated debug BuildConfig carries the local override`() {
        // 单测运行在 debug 变体：生成类里必须真的存在本地覆盖字段，且与正式 URL 不同。
        assertNotEquals(releaseBaseUrl, BuildConfig.API_BASE_URL)
        assertTrue(BuildConfig.API_BASE_URL.startsWith("http://"))
    }

    @Test
    fun `version is 2_5_0`() {
        val defaultConfigBlock = extractBlock(buildFileText(), "defaultConfig")
        assertTrue(
            "defaultConfig 必须声明 versionName = \"2.5.0\"",
            defaultConfigBlock.contains("""versionName = "2.5.0""""),
        )
    }

    // --- helpers --------------------------------------------------------------------------------

    private fun buildFileText(): String {
        // 单测工作目录按 AGP 约定是模块目录，但候选路径兜底到仓库相对位置，保证任何环境可定位。
        val candidates = listOf(
            File(System.getProperty("user.dir"), "build.gradle.kts"),
            File(System.getProperty("user.dir"), "app/build.gradle.kts"),
            File(System.getProperty("user.dir"), "Front/app/build.gradle.kts"),
        )
        val file = candidates.firstOrNull(File::exists)
            ?: error("找不到 build.gradle.kts（候选路径 $candidates）")
        return file.readText()
    }

    /** 提取命名块的文本：`name { ... }`（行首锚定，避免匹配字符串内的同名词）。 */
    private fun extractBlock(source: String, blockName: String): String {
        val header = Regex("""(?m)^\s*$blockName\s*\{""").find(source)
            ?: error("build.gradle.kts 中缺少块 '$blockName'")
        return bracedBody(source, header.range.last, "块 '$blockName'")
    }

    /** 先取外层块，再在其内部取子块（如 buildTypes 内的 release）。 */
    private fun extractBlock(source: String, outerName: String, innerName: String): String =
        extractBlock(extractBlock(source, outerName), innerName)

    private fun bracedBody(source: String, openBrace: Int, what: String): String {
        var depth = 0
        for (i in openBrace until source.length) {
            when (source[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(openBrace + 1, i)
                }
            }
        }
        error("$what 未闭合")
    }

    /** 块内全部 buildConfigField 的字段名（按出现顺序）。 */
    private fun buildConfigFieldNames(block: String): List<String> {
        val field = Regex("""buildConfigField\(\s*"String"\s*,\s*"([^"]+)"\s*,""")
        return field.findAll(block).map { it.groupValues[1] }.toList()
    }

    /**
     * 块内指定 buildConfigField 的语义字符串值。kts 中第三个参数是嵌入生成的 Java 字面量
     * 片段（AGP 原样写入生成源码），因此源码里写的是 `"\"...\""`；这里先按 Kotlin 字面量
     * 解转义 `\"` → `"`，再去外层引号得到语义值（如 `https://shanka.kbzz1.top`）。
     */
    private fun buildConfigFieldValue(block: String, name: String): String? {
        val field = Regex("""buildConfigField\(\s*"String"\s*,\s*"$name"\s*,\s*"((?:\\.|[^"\\])*)"\s*\)""")
        val literal = field.find(block)?.groupValues?.get(1) ?: return null
        return literal.replace("\\\"", "\"").removeSurrounding("\"")
    }
}
