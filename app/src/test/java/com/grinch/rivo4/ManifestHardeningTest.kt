package com.grinch.rivo4

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Guards the phone-app hardening rules from Tasks 9/10: backup disabled,
 * single launcher entry point, no telephony dialing surface, non-exported
 * components, and the debug-only credential receiver gated behind DUMP.
 * Parses the raw manifests (before placeholder merging) from the module dir.
 */
class ManifestHardeningTest {

    @Test
    fun backupDisabledAndSingleLauncherEntryPoint() {
        val app = MAIN.documentElement.findChild("application")
        assertEquals("allowBackup must stay false", "false", app.attr("android:allowBackup"))

        val launchers = MAIN.documentElement.filterDescendants("intent-filter").filter { f ->
            f.childActions().contains("android.intent.action.MAIN") &&
                f.childCategories().contains("android.intent.category.LAUNCHER")
        }
        assertEquals("exactly one MAIN/LAUNCHER filter expected", 1, launchers.size)
    }

    @Test
    fun dangerousAndRemovedPermissionsAbsent() {
        val banned = setOf(
            "android.permission.CALL_PHONE",
            "android.permission.READ_CALL_LOG",
            "android.permission.WRITE_CALL_LOG",
            "android.permission.ANSWER_PHONE_CALLS",
            "android.permission.SEND_SMS",
            "android.permission.RECEIVE_SMS",
            "android.permission.READ_SMS",
            "android.permission.RECEIVE_BOOT_COMPLETED",
            "android.permission.PROCESS_OUTGOING_CALLS"
        )
        val declared = MAIN.documentElement
            .children("uses-permission")
            .map { it.attr("android:name") }
            .toSet()
        val hits = declared.intersect(banned)
        assertTrue("banned permissions declared: $hits", hits.isEmpty())
    }

    @Test
    fun noDialCallIntentsOrTelSchemes() {
        val bannedActions = setOf(
            "android.intent.action.CALL",
            "android.intent.action.CALL_BUTTON",
            "android.intent.action.DIAL",
            "android.intent.action.NEW_OUTGOING_CALL"
        )
        val actions = MAIN.documentElement.filterDescendants("action").map { it.attr("android:name") }
        val actionHits = actions.intersect(bannedActions)
        assertTrue("dial/call actions declared: $actionHits", actionHits.isEmpty())

        val schemes = MAIN.documentElement.filterDescendants("data").map { it.attr("android:scheme") }
        assertTrue("tel scheme declared", "tel" !in schemes)
    }

    @Test
    fun servicesAndReceiversAreNotExported() {
        for (tag in listOf("service", "receiver")) {
            val components = MAIN.documentElement.filterDescendants(tag)
            assertTrue("expected at least one <$tag>", components.isNotEmpty())
            for (el in components) {
                assertEquals(
                    "<$tag ${el.attr("android:name")}> must be exported=false",
                    "false",
                    el.attr("android:exported")
                )
            }
        }
    }

    @Test
    fun noBootCompletedComponents() {
        val bootActions = setOf(
            "android.intent.action.BOOT_COMPLETED",
            "android.intent.action.LOCKED_BOOT_COMPLETED",
            "android.intent.action.QUICKBOOT_POWERON"
        )
        val actions = MAIN.documentElement.filterDescendants("action").map { it.attr("android:name") }
        val hits = actions.intersect(bootActions)
        assertTrue("boot-completed actions declared: $hits", hits.isEmpty())
    }

    @Test
    fun debugCredentialReceiverGuardedByDumpPermission() {
        val receiver = DEBUG.documentElement
            .filterDescendants("receiver")
            .firstOrNull { it.attr("android:name").endsWith("SipCredentialReceiver") }
        assertTrue("SipCredentialReceiver missing from debug manifest", receiver != null)
        assertEquals("android.permission.DUMP", receiver!!.attr("android:permission"))
        assertEquals("true", receiver.attr("android:exported"))
        val actions = receiver.filterDescendants("action").map { it.attr("android:name") }
        assertTrue(
            "credential action missing",
            actions.any { it.endsWith(".SET_SIP_CREDENTIALS") }
        )
    }
}

private val MAIN: Document by lazy {
    parseManifest("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml")
}

private val DEBUG: Document by lazy {
    parseManifest("src/debug/AndroidManifest.xml", "app/src/debug/AndroidManifest.xml")
}

private fun parseManifest(vararg relativePaths: String): Document {
    var dir: File? = File(System.getProperty("user.dir"))
    while (dir != null) {
        for (rel in relativePaths) {
            val candidate = File(dir, rel)
            if (candidate.isFile) {
                return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(candidate)
            }
        }
        dir = dir.parentFile
    }
    throw IllegalStateException(
        "Manifest not found from ${System.getProperty("user.dir")}: ${relativePaths.joinToString()}"
    )
}

private fun Element.attr(name: String): String = getAttribute(name)

private fun Element.children(tag: String): List<Element> {
    val nodes = childNodes
    return (0 until nodes.length)
        .map { nodes.item(it) }
        .filterIsInstance<Element>()
        .filter { it.tagName == tag }
}

private fun Element.findChild(tag: String): Element =
    children(tag).firstOrNull() ?: error("no <$tag> inside <tagName=$tagName>")

private fun Element.descendants(): Sequence<Element> = sequence {
    val nodes = childNodes
    for (i in 0 until nodes.length) {
        val node = nodes.item(i)
        if (node is Element) {
            yield(node)
            yieldAll(node.descendants())
        }
    }
}

private fun Element.filterDescendants(tag: String): List<Element> =
    descendants().filter { it.tagName == tag }.toList()

private fun Element.childActions(): List<String> =
    filterDescendants("action").map { it.attr("android:name") }

private fun Element.childCategories(): List<String> =
    filterDescendants("category").map { it.attr("android:name") }
