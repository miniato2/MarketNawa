package marketnawa.util

import com.fasterxml.jackson.databind.ObjectMapper
import marketnawa.elasitcsearch.ElasticsearchProperties
import org.apache.http.HttpEntity
import org.apache.http.util.EntityUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

@Component
class ElasticsearchUtil2 @Autowired constructor(
    private val elasticsearchProperties: ElasticsearchProperties
) {
    private val objectMapper = ObjectMapper()

    fun indexDocument(indexName: String, document: Any, documentId: String): Boolean {
        val jsonDocument = convertObjectToJson(document)
        val uri = URI.create("${elasticsearchProperties.host}:${elasticsearchProperties.port}/$indexName/_doc/$documentId")
        val responseCode = executeHttpRequest("PUT", uri, jsonDocument)
        val responseMap: Map<*, *>? = objectMapper.readValue(responseCode, Map::class.java)
        val result = responseMap?.get("result") as String? ?: ""
        return result == "created" // 예시로 "created"인 경우를 성공으로 가정
    }

    fun bulkIndexDocuments(indexName: String, documents: List<Pair<Any, String>>): Boolean {
        val bulkRequest = StringBuilder()

        documents.forEach { (document, documentId) ->
            val jsonDocument = convertObjectToJson(document)
            bulkRequest.append("{\"index\":{\"_index\":\"$indexName\",\"_id\":\"$documentId\"}}\n")
            bulkRequest.append("$jsonDocument\n")
        }

        val uri = URI.create("${elasticsearchProperties.host}/_bulk")
        val responseCode = executeHttpRequest("POST", uri, bulkRequest.toString())
        val responseMap: Map<*, *>? = objectMapper.readValue(responseCode, Map::class.java)
        val result = responseMap?.get("result") as String? ?: ""
        return result == "created" // 예시로 "created"인 경우를 성공으로 가정
    }

    fun findAll(indexName: String, from: Int = 0, size: Int = 10): List<Map<String, Any>> {
//        val uri = URI.create("${elasticsearchProperties.host}:${elasticsearchProperties.port}/$indexName/_search")
//        val jsonResponse = executeHttpRequest("GET", uri)
//        return parseSearchResponse(jsonResponse)
        val uri = URI.create("${elasticsearchProperties.host}:${elasticsearchProperties.port}/$indexName/_search")
            val query = """
            {
                "from": $from,
                "size": $size,
                "query": {
                    "match_all": {}
                }
            }
        """.trimIndent()

        val jsonResponse = executeHttpRequest("GET", uri, query)
        return parseSearchResponse(jsonResponse)
    }

    fun getTotalHitsCount(indexName: String): Int {
        val uri = URI.create("${elasticsearchProperties.host}:${elasticsearchProperties.port}/$indexName/_count")
        val query = """
            {
                "query": {
                    "match_all": {}
                }
            }
        """.trimIndent()

        val jsonResponse = executeHttpRequest("GET", uri, query)
        val responseMap: Map<*, *>? = objectMapper.readValue(jsonResponse, Map::class.java)
        return responseMap?.get("count") as Int
    }

    private fun executeHttpRequest(method: String, uri: URI, requestBody: String? = null): String {
        val url = URL(uri.toString())
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.setRequestProperty("Content-Type", "application/json")
        connection.connectTimeout = 5000
        connection.readTimeout = 5000

        if (requestBody != null) {
            connection.doOutput = true
            val outputStream = connection.outputStream
            outputStream.write(requestBody.toByteArray())
            outputStream.flush()
            outputStream.close()
        }

        val responseCode = connection.responseCode
        val jsonResponse = if (responseCode in 200..299) {
            val inputStream = connection.inputStream
            val response = inputStream.bufferedReader().use { it.readText() }
            inputStream.close()
            response
        } else {
            val errorStream = connection.errorStream
            val errorResponse = errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            errorStream?.close()
            errorResponse
        }

        connection.disconnect()
        return jsonResponse
    }

    private fun parseSearchResponse(jsonResponse: String): List<Map<String, Any>> {
        val responseMap: Map<*, *>? = objectMapper.readValue(jsonResponse, Map::class.java)

        val hitsList = responseMap?.get("hits") as Map<String, Any>?
            ?: error("No 'hits' found in the response")

        val hitsArray = hitsList["hits"] as List<Map<String, Any>>?
            ?: error("No 'hits' array found in the response")

        return hitsArray
    }

    private fun convertObjectToJson(obj: Any): String {
        return objectMapper.writeValueAsString(obj)
    }
}
