package com.example.livetranslator

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// DeepL API で1文を翻訳する(失敗したら null を返し、ML Kitの訳がそのまま残る)
class DeepLClient {

    fun translate(apiKey: String, text: String, context: String, tone: Int): String? {
        // 無料版のキーは末尾が「:fx」で、接続先が違う
        val host = if (apiKey.trim().endsWith(":fx")) "api-free.deepl.com" else "api.deepl.com"
        return try {
            val conn = URL("https://$host/v2/translate").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 3000
            conn.readTimeout = 5000
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "DeepL-Auth-Key ${apiKey.trim()}")
            conn.setRequestProperty("Content-Type", "application/json")

            val body = JSONObject()
                .put("text", JSONArray().put(text))
                .put("source_lang", "EN")
                .put("target_lang", "JA")
                // 「まじめ」は丁寧語、それ以外はくだけた口調(DeepLにワル口調はないので、ふつうと同じ)
                .put("formality", if (tone == Prefs.TONE_SERIOUS) "prefer_more" else "prefer_less")
            // 文脈は翻訳されず、訳の参考にだけ使われる
            if (context.isNotBlank()) body.put("context", context)

            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            if (conn.responseCode != 200) return null
            val res = conn.inputStream.bufferedReader().use { it.readText() }
            JSONObject(res).getJSONArray("translations").getJSONObject(0).getString("text")
        } catch (e: Exception) {
            null
        }
    }
}
