package marketnawa.crawling.product.gmarket

import co.elastic.clients.elasticsearch._types.query_dsl.MatchAllQuery
import co.elastic.clients.elasticsearch._types.query_dsl.Query
import marketnawa.domain.Category
import marketnawa.domain.MarketFood
import marketnawa.elasitcsearch.EsSearchService
import org.openqa.selenium.By
import org.openqa.selenium.NoSuchElementException
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.firefox.FirefoxOptions
import org.springframework.data.domain.PageRequest
import org.springframework.data.elasticsearch.client.elc.NativeQuery
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.data.elasticsearch.core.SearchHitsIterator
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates
import java.util.*


data class Item(
    val itemName: String,
    val itemPrice: Int,
    val itemImg: String,
    val itemId: String,
    val itemLink: String
)

fun extractPrice(text: String): String {
    val regex = Regex("[^0-9]")
    return regex.replace(text, "")
}

class GmarketCrawler(
    val category: Category,
//    private val elasticsearchOperations: ElasticsearchOperations
    private val esSearch: EsSearchService
) {

    fun execute() {

        val url = "https://www.gmarket.co.kr"

        val webDriverID = "webdriver.gecko.driver"
        val webDriverPath = "/Users/seung/Downloads/geckodriver"
        System.setProperty(webDriverID, webDriverPath)

        val options = FirefoxOptions()
        options.addArguments("--headless") // 브라우저를 화면에 표시하지 않고 실행 (옵션)

        val driver: WebDriver = FirefoxDriver(options)

        try {
            val searchText = category.searchText
            println(searchText.toString())

            val itemList: MutableList<MarketFood> = mutableListOf()

            for (j in 1..10) {
                val searchUrl = "/n/search?keyword=$searchText&k=53&p=$j"
                driver.get(url + searchUrl)

                val products = try {
                    driver.findElements(By.cssSelector(".box__item-container"))
                } catch (e: NoSuchElementException) {
                    break
                }

                if (products.isEmpty()) {
                    break
                }

                for (product in products) {
                    try {
                        val itemNameElement = product.findElement(By.cssSelector(".text__item-title"))
                        val itemPriceElement = product.findElement(By.cssSelector(".text__value"))
                        val itemImgElement = product.findElement(By.cssSelector("img"))
                        val itemLinkElement = product.findElement(By.cssSelector(".link__item"))

                        val itemName = itemNameElement?.text?.replace("\n", "") ?: "Unknown"
                        val itemPriceText = extractPrice(itemPriceElement?.text ?: "0")
                        val itemPrice = if (itemPriceText.isNotEmpty()) itemPriceText.toInt() else 0
                        val itemImg = itemImgElement?.getAttribute("src") ?: ""
                        val itemId = product.getAttribute("data-montelena-goodscode") ?: ""
                        val itemLink = itemLinkElement?.getAttribute("href") ?: ""

                        if (itemName.isNotEmpty() && itemPriceText.isNotEmpty()) {

                            var marketFood: MarketFood? = MarketFood(
                                foodId = UUID.randomUUID().toString(),
                                foodRealId = itemId,
                                foodName = itemName,
                                foodPrice = itemPrice,
//                        foodInfoCreatedDate = "",
                                foodInfo = "",
                                foodLink = itemLink,
                                foodImg = itemImg,
                                firstCategory = category.firstCategory,
                                secondCategory = category.secondCategory,
                                lastCategory = category.lastCategory,
                                detailCategory = category.desscription,
                                representativeName = "",
                                foodMarketBrand = "Gmarket"
                            )

                            if (marketFood != null) {
//                                elasticsearchOperations.save(marketFood)
                                esSearch.indexDocument("market_food", marketFood)
                            }
//                                val item = Item(itemName, itemPrice, itemImg, itemId, itemLink)
                            if (marketFood != null) {
                                itemList.add(marketFood)
                            }

//                                println(item)
                        }
                    } catch (e: NoSuchElementException) {
                        e.printStackTrace()
                    }
                }
                driver.manage().deleteAllCookies()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            driver.quit()
        }
    }
}
