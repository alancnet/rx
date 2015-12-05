import org.scalatest.{Matchers, FlatSpec}
import rx.lang.scala.Observable
import net.alanc.rx.encoding._

class UTF8Test extends FlatSpec with Matchers {
  val testString = "♈ ♉ ♊ ♋ ♌ ♍ ♎ ♏ ♐ ♑ ♒ ♓ "
  val testBytes = testString.getBytes("UTF8")

  "UTF8" should "encode streams of characters" in {
    Observable.from(testString).encodeUTF8.toBlocking.toList should be (testString.getBytes("UTF8").toList)
  }
  "UTF8" should "decode streams of characters" in {
    Observable.from(testBytes).decodeUTF8.toBlocking.toList should be (testString.toList)
  }
  "UTF8" should "encode iterables of characters" in {
    testString.toList.encodeUTF8.toList should be (testString.getBytes("UTF8").toList)
  }
  "UTF8" should "decode iterables of characters" in {
    testBytes.toList.decodeUTF8.toList should be (testString.toList)
  }

}
