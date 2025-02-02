package com.hexagonkt

import com.hexagonkt.core.require
import com.hexagonkt.core.media.ApplicationMedia.JSON
import com.hexagonkt.core.media.TextMedia.HTML
import com.hexagonkt.core.media.TextMedia.PLAIN
import com.hexagonkt.http.model.ContentType
import com.hexagonkt.http.server.handlers.HttpServerContext
import com.hexagonkt.http.server.handlers.PathHandler
import com.hexagonkt.http.server.handlers.path
import com.hexagonkt.serialization.jackson.json.Json
import com.hexagonkt.serialization.serialize
import com.hexagonkt.store.BenchmarkStore
import com.hexagonkt.templates.TemplatePort

import java.net.URL
import java.util.concurrent.ThreadLocalRandom

import kotlin.text.Charsets.UTF_8

class Controller(
    settings: Settings,
    stores: Map<String, BenchmarkStore>,
    templateEngines: Map<String, TemplatePort>,
) {
    private val queriesParam: String = settings.queriesParam
    private val cachedQueriesParam: String = settings.cachedQueriesParam
    private val worldRows: Int = settings.worldRows

    private val plain: ContentType = ContentType(PLAIN)
    private val json: ContentType = ContentType(JSON)
    private val html: ContentType = ContentType(HTML, charset = UTF_8)

    private val templates: Map<String, URL> = mapOf(
        "pebble" to URL("classpath:fortunes.pebble.html")
    )

    internal val path: PathHandler by lazy {
        path {
            get("/plaintext") { ok(settings.textMessage, contentType = plain) }
            get("/json") { ok(Message(settings.textMessage).serialize(Json.raw), contentType = json) }

            stores.forEach { (storeEngine, store) ->
                path("/$storeEngine") {
                    templateEngines.forEach { (templateEngineId, templateEngine) ->
                        get("/${templateEngineId}/fortunes") { listFortunes(store, templateEngineId, templateEngine) }
                    }

                    get("/db") { dbQuery(store) }
                    get("/query") { getWorlds(store) }
                    get("/cached") { getCachedWorlds(store) }
                    get("/update") { updateWorlds(store) }
                }
            }
        }
    }

    private fun HttpServerContext.listFortunes(
        store: BenchmarkStore, templateKind: String, templateAdapter: TemplatePort
    ): HttpServerContext {

        val fortunes = store.findAllFortunes() + Fortune(0, "Additional fortune added at request time.")
        val sortedFortunes = fortunes.sortedBy { it.message }
        val context = mapOf("fortunes" to sortedFortunes)
        val body = templateAdapter.render(templates.require(templateKind), context)

        return ok(body, contentType = html)
    }

    private fun HttpServerContext.dbQuery(store: BenchmarkStore): HttpServerContext {
        val ids = listOf(randomWorld())
        val worlds = store.findWorlds(ids)
        val world = worlds.first()

        return sendJson(world)
    }

    private fun HttpServerContext.getWorlds(store: BenchmarkStore): HttpServerContext {
        val worldsCount = getWorldsCount(queriesParam)
        val ids = (1..worldsCount).map { randomWorld() }
        val worlds = store.findWorlds(ids)

        return sendJson(worlds)
    }

    private fun HttpServerContext.getCachedWorlds(store: BenchmarkStore): HttpServerContext {
        val worldsCount = getWorldsCount(cachedQueriesParam)
        val ids = (1..worldsCount).map { randomWorld() }
        val worlds = store.findCachedWorlds(ids)

        return sendJson(worlds)
    }

    private fun HttpServerContext.updateWorlds(store: BenchmarkStore): HttpServerContext {
        val worldsCount = getWorldsCount(queriesParam)
        val worlds = (1..worldsCount).map { World(randomWorld(), randomWorld()) }

        store.replaceWorlds(worlds)

        return sendJson(worlds)
    }

    private fun HttpServerContext.sendJson(body: Any): HttpServerContext =
        ok(body.serialize(Json.raw), contentType = json)

    private fun HttpServerContext.getWorldsCount(parameter: String): Int =
        request.queryParameters[parameter]?.toIntOrNull().let {
            when {
                it == null -> 1
                it < 1 -> 1
                it > 500 -> 500
                else -> it
            }
        }

    private fun randomWorld(): Int =
        ThreadLocalRandom.current().nextInt(worldRows) + 1
}
