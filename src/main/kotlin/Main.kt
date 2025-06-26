
import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.time.LocalDate
import java.time.format.DateTimeFormatter

suspend fun main() {
    val articles = mutableListOf<Article>()
    val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    val today = LocalDate.now()

    coroutineScope {
        for (page in 1..Int.MAX_VALUE) {
            val url = if (page == 1) "https://apod.tw/daily/" else "https://apod.tw/daily/page/$page/"
            println("Fetching page: $url")
            val doc = try {
                withContext(Dispatchers.IO) {
                    Jsoup.connect(url).followRedirects(true).get()
                }
            } catch (e: Exception) {
                println("Error fetching page: $url - ${e.message}")
                break
            }

            val links = doc.select("a[href~=https://apod.tw/daily/\\d{8}/]")
            if (links.isEmpty()) {
                println("No more article links found on page $page. Exiting pagination.")
                break
            }

            var shouldBreakArticleFetching = false
            for (link in links) {
                val articleUrl = link.attr("abs:href")
                val dateRegex = Regex("(\\d{8})/?$")
                val dateString = dateRegex.find(articleUrl)?.groupValues?.get(1) ?: ""
                if (dateString.length == 8) {
                    val articleDate = LocalDate.parse(dateString, formatter)
                    if (articleDate.isAfter(today)) {
                        shouldBreakArticleFetching = true
                        break
                    }
                    launch {
                        val articleDoc = try {
                            withContext(Dispatchers.IO) {
                                Jsoup.connect(articleUrl).get()
                            }
                        } catch (e: Exception) {
                            null
                        }
                        articleDoc?.let {
                            articles.add(parseArticle(it, articleUrl))
                        }
                    }
                }
            }
            if (shouldBreakArticleFetching) {
                break
            }
        }
    }

    val rows = mutableListOf<List<String>>()
    rows.add(listOf("DictWordID", "SuBe", "漢羅", "POJ", "KIP", "華語", "English", "LaigoanMia", "LaigoanBangchi"))
    articles.sortByDescending { it.date }
    for (article in articles) {
        var vocabularyIndex = 1
        for (vocabulary in article.vocabulary) {
            val formattedDate = article.date.format(formatter)
            val dictWordId = String.format("APODTW-%s-%03d", formattedDate, vocabularyIndex)
            rows.add(
                listOf(
                    dictWordId,
                    dictWordId,
                    vocabulary.hanLo,
                    vocabulary.poj,
                    vocabulary.kip,
                    vocabulary.mandarin,
                    vocabulary.english,
                    "逐工一幅天文圖 https://apod.tw",
                    article.source
                )
            )
            vocabularyIndex++
        }
    }

    val outputFileName = "apod_tw_vocabulary_${today.format(formatter)}.csv"
    csvWriter().writeAll(rows, outputFileName)
    println("Data saved to $outputFileName")
}

data class Article(val date: LocalDate, val source: String, val vocabulary: List<Vocabulary>)
data class Vocabulary(
    val hanLo: String,
    val poj: String,
    val kip: String,
    val mandarin: String,
    val english: String
)

fun parseArticle(doc: Document, source: String): Article {
    val vocabulary = mutableListOf<Vocabulary>()
    val table = doc.select("h2:contains(詞彙學習)").firstOrNull()?.nextElementSibling()
    table?.select("tbody tr")?.forEach { row ->
        val cells = row.select("td")
        if (cells.size == 5) {
            vocabulary.add(
                Vocabulary(
                    hanLo = cells[0].text(),
                    poj = cells[1].text(),
                    kip = cells[2].text(),
                    mandarin = cells[3].text(),
                    english = cells[4].text()
                )
            )
        }
    }
    val dateRegex = Regex("(\\d{8})/?$")
    val dateString = dateRegex.find(source)?.groupValues?.get(1) ?: ""
    val articleDate = if (dateString.length == 8) LocalDate.parse(dateString, DateTimeFormatter.ofPattern("yyyyMMdd")) else LocalDate.MIN
    return Article(articleDate, source, vocabulary)
}