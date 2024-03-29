package stocks

import akka.NotUsed
import akka.stream.ThrottleMode
import akka.stream.scaladsl.Source
import yahoofinance.YahooFinance
import yahoofinance.{Stock => YahooStock}
import scala.concurrent.duration._

/**
 * A stock is a source of stock quotes and a symbol.
 */

class Stock(val symbol: StockSymbol) {
  private val stockQuoteGenerator: StockQuoteGenerator = new APIStockQuoteGenerator(symbol) //create StockQuoteGenerator with stock symbol

  private val source: Source[StockQuote, NotUsed] = {
    Source.unfold(stockQuoteGenerator.seed) { (last: StockQuote) =>
      val next = stockQuoteGenerator.seed//.newQuote(last)
      Some(next, next)
    }
  }       //create get price of symbol in Source

  /**
   * Returns a source of stock history, containing a single element.
   */
  def history(n: Int): Source[StockHistory, NotUsed] = {
    source.grouped(n).map(sq => new StockHistory(symbol, sq.map(_.price))).take(1)
  } //create json array of symbol and price history

  /**
   * Provides a source that returns a stock quote every 100 milliseconds.
   */
  def update: Source[StockUpdate, NotUsed] = {
    source
      .throttle(elements = 1, per = 100.millis, maximumBurst = 1, ThrottleMode.shaping)
      .map(sq => new StockUpdate(sq.symbol, sq.price))
  }

  override val toString: String = s"Stock($symbol)"
}

/**
 * StockQuoteGenerator to be inherited
 */
trait StockQuoteGenerator {
  def seed: StockQuote
  //def newQuote(lastQuote: StockQuote): StockQuote
}

/**
 * inherit the trait StockQuoteGenerator
 * init with StockSymbol which is a String ex: APPL
 * there are two methods - seed -> first price & newQuote -> price after Both output StockQuote(symbol: StockSymbol, price: StockPrice)
 * newQuote is only for testing
 * This is probably better done through configuration same as the allowedhosts filter.
 */
class APIStockQuoteGenerator(symbol: StockSymbol) extends StockQuoteGenerator {
  //private def random: Double = scala.util.Random.nextDouble


  def seed: StockQuote = {
    val stockName: String = symbol.toString
    val stock: YahooStock = YahooFinance.get(stockName)
    val stockPriceAPI: Double = stock.getQuote.getPrice.doubleValue()
    StockQuote(symbol, StockPrice(stockPriceAPI))
    //StockQuote(symbol, StockPrice(random * 800))
  }
/*
  def newQuote(lastQuote: StockQuote): StockQuote = {
    val stockName: String = symbol.toString
    val stock: YahooStock = YahooFinance.get(stockName)
    val stockPriceAPI: Double = stock.getQuote.getPrice.doubleValue()
    StockQuote(symbol, StockPrice(stockPriceAPI))
    //StockQuote(symbol, StockPrice(lastQuote.price.raw * (0.95 + (0.1 * random))))
  }
*/
}

case class StockQuote(symbol: StockSymbol, price: StockPrice)

/** Value class for a stock symbol */
class StockSymbol private (val raw: String) extends AnyVal {
  override def toString: String = raw
}

object StockSymbol {
  import play.api.libs.json._ // Combinator syntax

  def apply(raw: String) = new StockSymbol(raw)

  implicit val stockSymbolReads: Reads[StockSymbol] = {
    JsPath.read[String].map(StockSymbol(_))
  }

  implicit val stockSymbolWrites: Writes[StockSymbol] = Writes {
    (symbol: StockSymbol) => JsString(symbol.raw)
  }
}

/** Value class for stock price */
class StockPrice private (val raw: Double) extends AnyVal {
  override def toString: String = raw.toString
}

object StockPrice {
  import play.api.libs.json._ // Combinator syntax

  def apply(raw: Double):StockPrice = new StockPrice(raw)

  implicit val stockPriceWrites: Writes[StockPrice] = Writes {
    (price: StockPrice) => JsNumber(price.raw)
  }
}

// Used for automatic JSON conversion
// https://www.playframework.com/documentation/2.7.x/ScalaJson

// JSON presentation class for stock history
case class StockHistory(symbol: StockSymbol, prices: Seq[StockPrice])

object StockHistory {
  import play.api.libs.json._ // Combinator syntax

  implicit val stockHistoryWrites: Writes[StockHistory] = new Writes[StockHistory] {
    override def writes(history: StockHistory): JsValue = Json.obj(
      "type" -> "stockhistory",
      "symbol" -> history.symbol,
      "history" -> history.prices
    )
  }
}

// JSON presentation class for stock update
case class StockUpdate(symbol: StockSymbol, price: StockPrice)

object StockUpdate {
  import play.api.libs.json._ // Combinator syntax

  implicit val stockUpdateWrites: Writes[StockUpdate] = new Writes[StockUpdate] {
    override def writes(update: StockUpdate): JsValue = Json.obj(
      "type" -> "stockupdate",
      "symbol" -> update.symbol,
      "price" -> update.price
    )
  }
}
