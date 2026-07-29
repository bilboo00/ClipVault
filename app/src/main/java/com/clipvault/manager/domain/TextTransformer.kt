package com.clipvault.manager.domain

import android.util.Base64
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder

sealed class TransformationResult {
    data class Success(val text: String) : TransformationResult()
    data class Failure(val error: String) : TransformationResult()
}

object TextTransformer {

    fun uppercase(text: String) = TransformationResult.Success(text.uppercase())
    fun lowercase(text: String) = TransformationResult.Success(text.lowercase())

    fun titleCase(text: String): TransformationResult.Success {
        val titled = text.split(' ').joinToString(" ") { word ->
            if (word.isEmpty()) word
            else word[0].titlecase() + word.substring(1).lowercase()
        }
        return TransformationResult.Success(titled)
    }

    fun trimWhitespace(text: String) = TransformationResult.Success(text.trim())

    fun removeLineBreaks(text: String) = TransformationResult.Success(
        text.replace(Regex("\\s+"), " ").trim()
    )

    fun addLineBreaks(text: String): TransformationResult.Success {
        val split = text.split(Regex("(?<=[.!?])\\s+")).joinToString("\n\n") { it.trim() }
        return TransformationResult.Success(split)
    }

    fun sentenceCase(text: String): TransformationResult.Success {
        val sentences = text.split(Regex("(?<=[.!?])\\s+")).joinToString(" ") { sentence ->
            if (sentence.isEmpty()) sentence
            else sentence[0].titlecase() + sentence.substring(1).lowercase()
        }
        return TransformationResult.Success(sentences)
    }

    fun urlEncode(text: String): TransformationResult {
        return try {
            TransformationResult.Success(URLEncoder.encode(text, "UTF-8"))
        } catch (e: Exception) {
            TransformationResult.Failure("Failed to encode URL: ${e.message}")
        }
    }

    fun urlDecode(text: String): TransformationResult {
        return try {
            TransformationResult.Success(URLDecoder.decode(text, "UTF-8"))
        } catch (e: Exception) {
            TransformationResult.Failure("Failed to decode URL: ${e.message}")
        }
    }

    fun base64Encode(text: String): TransformationResult {
        return try {
            val encoded = Base64.encodeToString(text.toByteArray(), Base64.NO_WRAP)
            TransformationResult.Success(encoded)
        } catch (e: Exception) {
            TransformationResult.Failure("Failed to encode Base64: ${e.message}")
        }
    }

    fun base64Decode(text: String): TransformationResult {
        return try {
            val decoded = String(Base64.decode(text.trim(), Base64.DEFAULT))
            TransformationResult.Success(decoded)
        } catch (e: Exception) {
            TransformationResult.Failure("Failed to decode Base64: ${e.message}")
        }
    }

    fun jsonFormat(text: String): TransformationResult {
        return try {
            val json = JSONObject(text)
            val formatted = json.toString(2)
            TransformationResult.Success(formatted)
        } catch (e: Exception) {
            TransformationResult.Failure("Invalid JSON: ${e.message}")
        }
    }

    fun jsonMinify(text: String): TransformationResult {
        return try {
            val json = JSONObject(text)
            TransformationResult.Success(json.toString())
        } catch (e: Exception) {
            TransformationResult.Failure("Invalid JSON: ${e.message}")
        }
    }

    enum class Type(val label: String, val transform: (String) -> TransformationResult) {
        UPPERCASE("UPPERCASE", { TextTransformer.uppercase(it) }),
        LOWERCASE("lowercase", { TextTransformer.lowercase(it) }),
        TITLE_CASE("Title Case", { TextTransformer.titleCase(it) }),
        SENTENCE_CASE("Sentence case", { TextTransformer.sentenceCase(it) }),
        TRIM("Trim Whitespace", { TextTransformer.trimWhitespace(it) }),
        REMOVE_LINE_BREAKS("Remove Line Breaks", { TextTransformer.removeLineBreaks(it) }),
        ADD_LINE_BREAKS("Add Line Breaks", { TextTransformer.addLineBreaks(it) }),
        URL_ENCODE("URL Encode", { TextTransformer.urlEncode(it) }),
        URL_DECODE("URL Decode", { TextTransformer.urlDecode(it) }),
        BASE64_ENCODE("Base64 Encode", { TextTransformer.base64Encode(it) }),
        BASE64_DECODE("Base64 Decode", { TextTransformer.base64Decode(it) }),
        JSON_FORMAT("Format JSON", { TextTransformer.jsonFormat(it) }),
        JSON_MINIFY("Minify JSON", { TextTransformer.jsonMinify(it) });
    }
}