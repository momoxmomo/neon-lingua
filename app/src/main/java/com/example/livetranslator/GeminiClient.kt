package com.example.livetranslator

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// Gemini API で、配信の話し言葉らしい口語の日本語に訳す
// 失敗(無料枠の上限など)したら null を返し、DeepL か ML Kit で翻訳
class GeminiClient {

    companion object {
        // 軽くて速いモデル。名前が変わって動かないときはここを書き換える
        const val MODEL = "gemini-flash-lite-latest"

        private const val BASE =
            "あなたは英語のライブ配信をリアルタイムで日本語字幕にする翻訳者です。" +
            "入力は音声認識の結果なので、句読点がなく、聞き間違いが混ざることがあります。" +
            "直訳ではなく日本人が実際に言いそうな言い回しにします。" +
            "聞き間違いらしい単語は文脈から自然に補ってかまいません。" +
            "出力は訳文の1文だけにし、説明や引用符は付けないでください。"

        // 口調ごとの指示(設定の「訳の口調」)
        private val TONES = listOf(
            // まじめ
            "ニュースや公式の字幕のような、丁寧で落ち着いた日本語に訳してください。です・ます調を使います。",
            // ふつう
            "話し手が友達と喋っているような、自然でくだけた話し言葉の日本語に訳してください。です・ます調は使いません。",
            // ワル
            "サイバーパンクの街のチンピラや傭兵のような、荒っぽくてワルい口調の日本語に訳してください。" +
            "一人称は「俺」、語尾は「〜だろ」「〜じゃねえ」「〜ぜ」「〜しろよ」のように乱暴にし、です・ます調は使いません。" +
            "ただし意味は変えず、原文にない悪口や差別的な言葉は足さないでください。",
        )
        private fun instruction(tone: Int) = BASE + TONES[tone.coerceIn(0, TONES.lastIndex)]
    }

    fun translate(apiKey: String, text: String, context: String, tone: Int): String? {
        return try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 3000
            conn.readTimeout = 6000
            conn.doOutput = true
            conn.setRequestProperty("x-goog-api-key", apiKey.trim())
            conn.setRequestProperty("Content-Type", "application/json")

            val prompt = buildString {
                if (context.isNotBlank()) append("直前の発言(参考、訳さない):\n").append(context).append("\n\n")
                append("訳す文:\n").append(text)
            }
            val body = JSONObject()
                .put("systemInstruction", JSONObject().put("parts",
                    JSONArray().put(JSONObject().put("text", instruction(tone)))))
                .put("contents", JSONArray().put(JSONObject()
                    .put("role", "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
                .put("generationConfig", JSONObject().put("temperature", 0.3))

            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            if (conn.responseCode != 200) return null
            val res = conn.inputStream.bufferedReader().use { it.readText() }
            JSONObject(res).getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
                .getString("text").trim().ifBlank { null }
        } catch (e: Exception) {
            null
        }
    }
}
