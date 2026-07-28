package com.spmisha134.skillops.insights.parser

import com.google.gson.JsonElement
import com.google.gson.JsonObject

internal fun JsonElement.stringOrNull(): String? =
    takeIf { isJsonPrimitive && asJsonPrimitive.isString }?.asString

internal fun JsonElement.objectOrNull(): JsonObject? =
    takeIf(JsonElement::isJsonObject)?.asJsonObject

internal fun JsonElement.longOrNull(): Long? {
    if (!isJsonPrimitive) return null
    val primitive = asJsonPrimitive
    return when {
        primitive.isNumber -> runCatching { primitive.asLong }.getOrNull()
        primitive.isString -> primitive.asString.toLongOrNull()
        else -> null
    }
}

internal fun JsonElement.doubleOrNull(): Double? {
    if (!isJsonPrimitive) return null
    val primitive = asJsonPrimitive
    return when {
        primitive.isNumber -> runCatching { primitive.asDouble }.getOrNull()
        primitive.isString -> primitive.asString.toDoubleOrNull()
        else -> null
    }
}

internal fun JsonObject.stringAt(key: String): String? = get(key)?.stringOrNull()

internal fun JsonObject.objectAt(key: String): JsonObject? = get(key)?.objectOrNull()

internal fun JsonObject.longAt(key: String): Long? = get(key)?.longOrNull()

internal fun JsonObject.doubleAt(key: String): Double? = get(key)?.doubleOrNull()
