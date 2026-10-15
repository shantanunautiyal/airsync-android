package com.sameerasw.airsync.utils

import android.content.Context
import android.os.Environment
import android.util.Log
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLPathPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.request.receiveStream
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.method
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import java.io.File
import java.net.ServerSocket
import java.net.URI
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class WebDavServer(private val context: Context) {
    private var engine: ApplicationEngine? = null
    private val port = 9081
    private val TAG = "WebDavServer"

    private val storageRoot = Environment.getExternalStorageDirectory()

    fun start() {
        if (engine != null) {
            Log.d(TAG, "WebDAV server already initialized")
            return
        }

        if (!isPortAvailable(port)) {
            Log.e(TAG, "WebDAV server cannot start: Port $port is already in use")
            return
        }

        try {
            engine = embeddedServer(CIO, port = port, host = "0.0.0.0") {
                install(StatusPages) {
                    exception<Throwable> { call, cause ->
                        Log.e(TAG, "Unhandled exception in route", cause)
                        call.respond(HttpStatusCode.InternalServerError, "Internal Server Error")
                    }
                }

                routing {
                    // Catch-all: matches any path including root
                    route("{...}") {
                        method(HttpMethod.parse("PROPFIND")) {
                            handle { handlePropfind(call) }
                        }
                        method(HttpMethod.parse("PROPPATCH")) {
                            handle { handleProppatch(call) }
                        }
                        method(HttpMethod.parse("MKCOL")) {
                            handle { handleMkcol(call) }
                        }
                        method(HttpMethod.parse("LOCK")) {
                            handle { handleLock(call) }
                        }
                        method(HttpMethod.parse("UNLOCK")) {
                            handle { handleUnlock(call) }
                        }
                        method(HttpMethod.parse("MOVE")) {
                            handle { handleMove(call) }
                        }
                        method(HttpMethod.parse("COPY")) {
                            handle { handleCopy(call) }
                        }
                        method(HttpMethod.Put) {
                            handle { handlePut(call) }
                        }
                        method(HttpMethod.Delete) {
                            handle { handleDelete(call) }
                        }
                        get { handleGet(call) }
                        head { handleHead(call) }
                        method(HttpMethod.Options) {
                            handle {
                                call.response.header("Allow", "GET, HEAD, OPTIONS, PROPFIND, PUT, MKCOL, DELETE, MOVE, COPY, PROPPATCH, LOCK, UNLOCK")
                                call.response.header("DAV", "1, 2")
                                call.response.header("MS-Author-Via", "DAV")
                                call.respond(HttpStatusCode.OK)
                            }
                        }
                    }
                }
            }
            engine?.start(wait = false)
            Log.i(TAG, "WebDAV server started on port $port")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start WebDAV server on port $port", e)
            engine = null
        }
    }

    private fun isPortAvailable(port: Int): Boolean {
        return try {
            ServerSocket(port).use { true }
        } catch (e: Exception) {
            false
        }
    }

    fun stop() {
        try {
            engine?.stop(500, 1000)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping WebDAV server", e)
        } finally {
            engine = null
            Log.i(TAG, "WebDAV server stopped")
        }
    }

    /**
     * Extracts the filesystem-relative path from the request URI.
     * URL-decodes the path and strips the leading slash so it can be
     * joined with storageRoot via File(storageRoot, relativePath).
     * Trailing slashes are removed before file resolution.
     */
    private fun resolveRequestPath(call: ApplicationCall): String {
        val raw = call.request.path() // e.g. "/", "/DCIM/", "/DCIM/Camera/IMG_001.jpg"
        val decoded = URLDecoder.decode(raw, "UTF-8")
        return decoded.trimStart('/').trimEnd('/')
    }

    private suspend fun handlePropfind(call: ApplicationCall) {
        val relativePath = resolveRequestPath(call)
        val file = File(storageRoot, relativePath)

        Log.d(TAG, "PROPFIND: raw=${call.request.path()} -> resolved=${file.absolutePath}")

        if (!file.exists()) {
            Log.w(TAG, "PROPFIND 404: ${file.absolutePath}")
            call.respond(HttpStatusCode.NotFound)
            return
        }

        val depth = call.request.headers["Depth"] ?: "1"
        val xml = buildPropfindXml(file, relativePath, depth)

        call.respondText(
            xml,
            ContentType.Text.Xml.withParameter("charset", "utf-8"),
            HttpStatusCode.MultiStatus
        )
    }

    private fun buildPropfindXml(file: File, relativePath: String, depth: String): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n")
        sb.append("<d:multistatus xmlns:d=\"DAV:\">\n")

        appendFileEntry(sb, file, relativePath)

        if (file.isDirectory && depth != "0") {
            file.listFiles()
                ?.filter { !it.name.startsWith(".") }
                ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                ?.forEach { child ->
                    val childRelPath =
                        if (relativePath.isEmpty()) child.name else "$relativePath/${child.name}"
                    appendFileEntry(sb, child, childRelPath)
                }
        }

        sb.append("</d:multistatus>")
        return sb.toString()
    }

    private fun appendFileEntry(sb: StringBuilder, file: File, relativePath: String) {
        val displayName = if (relativePath.isEmpty()) "Android" else file.name

        // Build the href: each path segment is percent-encoded individually,
        // but the "/" separators are preserved. Root is always "/".
        val href = if (relativePath.isEmpty()) {
            "/"
        } else {
            val encodedSegments = relativePath.split("/").joinToString("/") { segment ->
                segment.encodeURLPathPart()
            }
            // Directories must have trailing slash for WebDAV clients to recognise them
            if (file.isDirectory) "/$encodedSegments/" else "/$encodedSegments"
        }

        val rfc1123Format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }
        val lastModified = rfc1123Format.format(Date(file.lastModified()))

        sb.append("  <d:response>\n")
        sb.append("    <d:href>$href</d:href>\n")
        sb.append("    <d:propstat>\n")
        sb.append("      <d:prop>\n")
        sb.append("        <d:displayname>$displayName</d:displayname>\n")
        if (file.isDirectory) {
            sb.append("        <d:resourcetype><d:collection/></d:resourcetype>\n")
        } else {
            sb.append("        <d:resourcetype/>\n")
            sb.append("        <d:getcontentlength>${file.length()}</d:getcontentlength>\n")
            val contentType = java.net.URLConnection.guessContentTypeFromName(file.name)
                ?: "application/octet-stream"
            sb.append("        <d:getcontenttype>$contentType</d:getcontenttype>\n")
        }
        sb.append("        <d:getlastmodified>$lastModified</d:getlastmodified>\n")
        sb.append("      </d:prop>\n")
        sb.append("      <d:status>HTTP/1.1 200 OK</d:status>\n")
        sb.append("    </d:propstat>\n")
        sb.append("  </d:response>\n")
    }

    private suspend fun handleGet(call: ApplicationCall) {
        val relativePath = resolveRequestPath(call)
        val file = File(storageRoot, relativePath)

        Log.d(TAG, "GET: raw=${call.request.path()} -> resolved=${file.absolutePath}")

        if (!file.exists()) {
            call.respond(HttpStatusCode.NotFound)
            return
        }

        if (file.isDirectory) {
            call.respond(HttpStatusCode.MethodNotAllowed, "Cannot GET a directory")
            return
        }

        call.respondFile(file)
    }

    private suspend fun handleHead(call: ApplicationCall) {
        val relativePath = resolveRequestPath(call)
        val file = File(storageRoot, relativePath)

        if (!file.exists()) {
            call.respond(HttpStatusCode.NotFound)
            return
        }

        if (!file.isDirectory) {
            call.response.header(HttpHeaders.ContentLength, file.length().toString())
            val contentType = java.net.URLConnection.guessContentTypeFromName(file.name)
                ?: "application/octet-stream"
            call.response.header(HttpHeaders.ContentType, contentType)
        }
        val rfc1123Format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }
        call.response.header(
            HttpHeaders.LastModified,
            rfc1123Format.format(Date(file.lastModified()))
        )
        call.respond(HttpStatusCode.OK)
    }

    private suspend fun handlePut(call: ApplicationCall) {
        val relativePath = resolveRequestPath(call)
        val file = File(storageRoot, relativePath)

        Log.d(TAG, "PUT: raw=${call.request.path()} -> resolved=${file.absolutePath}")
        try {
            file.parentFile?.mkdirs()
            val existed = file.exists()
            call.receiveStream().use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (existed) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.Created)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling PUT for ${file.absolutePath}", e)
            call.respond(HttpStatusCode.InternalServerError, "Error saving file: ${e.message}")
        }
    }

    private suspend fun handleMkcol(call: ApplicationCall) {
        val relativePath = resolveRequestPath(call)
        val file = File(storageRoot, relativePath)

        Log.d(TAG, "MKCOL: raw=${call.request.path()} -> resolved=${file.absolutePath}")
        if (file.exists()) {
            call.respond(HttpStatusCode.MethodNotAllowed, "Resource already exists")
            return
        }

        val parent = file.parentFile
        if (parent != null && !parent.exists()) {
            call.respond(HttpStatusCode.Conflict, "Parent collection does not exist")
            return
        }

        if (file.mkdir()) {
            call.respond(HttpStatusCode.Created)
        } else {
            call.respond(HttpStatusCode.InternalServerError, "Failed to create collection")
        }
    }

    private suspend fun handleDelete(call: ApplicationCall) {
        val relativePath = resolveRequestPath(call)
        val file = File(storageRoot, relativePath)

        Log.d(TAG, "DELETE: raw=${call.request.path()} -> resolved=${file.absolutePath}")
        if (!file.exists()) {
            call.respond(HttpStatusCode.NotFound)
            return
        }

        val deleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
        if (deleted) {
            call.respond(HttpStatusCode.NoContent)
        } else {
            call.respond(HttpStatusCode.InternalServerError, "Failed to delete resource")
        }
    }

    private suspend fun handleMove(call: ApplicationCall) {
        val relativePath = resolveRequestPath(call)
        val source = File(storageRoot, relativePath)
        val destHeader = call.request.headers["Destination"]

        if (destHeader == null) {
            call.respond(HttpStatusCode.BadRequest, "Missing Destination header")
            return
        }

        try {
            val destUri = URI(destHeader)
            val destPath = URLDecoder.decode(destUri.path, "UTF-8").trimStart('/').trimEnd('/')
            val target = File(storageRoot, destPath)
            target.parentFile?.mkdirs()

            Log.d(TAG, "MOVE: ${source.absolutePath} -> ${target.absolutePath}")
            val success = source.renameTo(target) || (source.copyRecursively(target, overwrite = true) && source.deleteRecursively())
            if (success) {
                call.respond(HttpStatusCode.Created)
            } else {
                call.respond(HttpStatusCode.InternalServerError, "Failed to move resource")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in MOVE", e)
            call.respond(HttpStatusCode.InternalServerError, e.message ?: "Move error")
        }
    }

    private suspend fun handleCopy(call: ApplicationCall) {
        val relativePath = resolveRequestPath(call)
        val source = File(storageRoot, relativePath)
        val destHeader = call.request.headers["Destination"]

        if (destHeader == null) {
            call.respond(HttpStatusCode.BadRequest, "Missing Destination header")
            return
        }

        try {
            val destUri = URI(destHeader)
            val destPath = URLDecoder.decode(destUri.path, "UTF-8").trimStart('/').trimEnd('/')
            val target = File(storageRoot, destPath)
            target.parentFile?.mkdirs()

            Log.d(TAG, "COPY: ${source.absolutePath} -> ${target.absolutePath}")
            val success = source.copyRecursively(target, overwrite = true)
            if (success) {
                call.respond(HttpStatusCode.Created)
            } else {
                call.respond(HttpStatusCode.InternalServerError, "Failed to copy resource")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in COPY", e)
            call.respond(HttpStatusCode.InternalServerError, e.message ?: "Copy error")
        }
    }

    private suspend fun handleProppatch(call: ApplicationCall) {
        val rawPath = call.request.path()
        Log.d(TAG, "PROPPATCH: raw=$rawPath")
        val xml = """
            <?xml version="1.0" encoding="utf-8" ?>
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>$rawPath</d:href>
                <d:propstat>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()
        call.respondText(xml, ContentType.Text.Xml.withParameter("charset", "utf-8"), HttpStatusCode.MultiStatus)
    }

    private suspend fun handleLock(call: ApplicationCall) {
        val rawPath = call.request.path()
        Log.d(TAG, "LOCK: raw=$rawPath")
        val lockToken = "urn:uuid:airsync-${System.currentTimeMillis()}"
        call.response.header("Lock-Token", "<$lockToken>")
        val xml = """
            <?xml version="1.0" encoding="utf-8" ?>
            <d:prop xmlns:d="DAV:">
              <d:lockdiscovery>
                <d:activelock>
                  <d:locktype><d:write/></d:locktype>
                  <d:lockscope><d:exclusive/></d:lockscope>
                  <d:depth>Infinity</d:depth>
                  <d:timeout>Second-3600</d:timeout>
                  <d:locktoken><d:href>$lockToken</d:href></d:locktoken>
                  <d:lockroot><d:href>$rawPath</d:href></d:lockroot>
                </d:activelock>
              </d:lockdiscovery>
            </d:prop>
        """.trimIndent()
        call.respondText(xml, ContentType.Text.Xml.withParameter("charset", "utf-8"), HttpStatusCode.OK)
    }

    private suspend fun handleUnlock(call: ApplicationCall) {
        Log.d(TAG, "UNLOCK: raw=${call.request.path()}")
        call.respond(HttpStatusCode.NoContent)
    }
}
