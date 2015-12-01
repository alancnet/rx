package net.alanc.rxio
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets

import rx.lang.scala.Observable

object encoding {
  implicit class PimpCharObservable(val input: Observable[Char]) extends AnyVal {
    def encode(encoding:CharacterEncoding) = encoding.encode(input)
    def encodeASCII = encode(ASCII)
    def encodeUTF8 = encode(UTF8)
  }
  implicit class PimpCharIterable(val input: Iterable[Char]) extends AnyVal {
    def encode(encoding:CharacterEncoding) = encoding.encode(input)
    def encodeASCII = encode(ASCII)
    def encodeUTF8 = encode(UTF8)
  }
  implicit class PimpByteObservable(val input: Observable[Byte]) extends AnyVal {
    def decode(encoding:CharacterEncoding) = encoding.decode(input)
    def decodeASCII = decode(ASCII)
    def decodeUTF8 = decode(UTF8)
  }
  implicit class PimpByteIterable(val input: Iterable[Byte]) extends AnyVal {
    def decode(encoding:CharacterEncoding) = encoding.decode(input)
    def decodeASCII = decode(ASCII)
    def decodeUTF8 = decode(UTF8)
  }

  trait CharacterEncoding {
    def decode(input: Observable[Byte]):Observable[Char]
    def decode(input: Iterable[Byte]):Iterable[Char]
    def encode(input: Observable[Char]):Observable[Byte]
    def encode(input: Iterable[Char]):Iterable[Byte]
  }
  object ASCII extends CharacterEncoding {
    def decode(input: Observable[Byte]) = input.map(_.toChar)
    def decode(input: Iterable[Byte]) = input.map(_.toChar)
    def encode(input: Observable[Char]) = input.map(_.toByte)
    def encode(input: Iterable[Char]) = input.map(_.toByte)
  }
  
  object UTF8 extends CharacterEncoding {
    //* http://stackoverflow.com/a/643713
    // UTF-8 encodes each of the 1,112,064 valid code points in the Unicode code space (1,114,112 code points minus 2,048 surrogate code points) using one to four 8-bit bytes (a group of 8 bits is known as an octet in the Unicode Standard).
    // https://en.wikipedia.org/wiki/UTF-8
    // https://docs.oracle.com/javase/tutorial/i18n/text/characterClass.html
    def decode(input : Observable[Byte]) : Observable[Char] = {
      val highBit = 128
      input
        .map(b => b.toInt)
        .scan((0, -1)){case ((buffer: Int, take: Int), byte) => {
          if (take > 1){
            (((buffer | (byte & 0x3F)) << 6), take -1) // not last byte, so shift and make room for another while your at it.
          } else if (take == 1){
            (buffer | (byte & 0x3F), 0) // last byte, don't shift it
          } else {
            // determine how many to take
            val newTake =
              if ((byte & 0xF0) == 0xF0) 3
              else if ((byte & 0xE0) == 0xE0) 2
              else if ((byte & 0xC0) == 0xC0) 1
              else 0

            // we need to mask off the first few bits and the shift it 6 to make room for the next ones
            if (newTake == 3)
              ((byte & 0x07) << 6, newTake)
            else if (newTake == 2)
              ((byte & 0x0F) << 6, newTake)
            else if (newTake == 1)
              ((byte & 0x1F) << 6, newTake)
            else // we need to mask off the first few bits and but DON'T shift it.. there are no next ones
              ((byte & 0x7F), newTake)
          }
        }}
        .filter {case (codePoint, completeChar) => completeChar == 0 }
        .flatMapIterable {case (codePoint, isValid) => Character.toChars(codePoint) }
    }
    def decode(input : Iterable[Byte]) : Iterable[Char] = {
      val highBit = 128
      input
        .map(b => b.toInt)
        .scanLeft((0, -1)){case ((buffer: Int, take: Int), byte) => {
          if (take > 1){
            (((buffer | (byte & 0x3F)) << 6), take -1) // not last byte, so shift and make room for another while your at it.
          } else if (take == 1){
            (buffer | (byte & 0x3F), 0) // last byte, don't shift it
          } else {
            // determine how many to take
            val newTake =
              if ((byte & 0xF0) == 0xF0) 3
              else if ((byte & 0xE0) == 0xE0) 2
              else if ((byte & 0xC0) == 0xC0) 1
              else 0

            // we need to mask off the first few bits and the shift it 6 to make room for the next ones
            if (newTake == 3)
              ((byte & 0x07) << 6, newTake)
            else if (newTake == 2)
              ((byte & 0x0F) << 6, newTake)
            else if (newTake == 1)
              ((byte & 0x1F) << 6, newTake)
            else // we need to mask off the first few bits and but DON'T shift it.. there are no next ones
              ((byte & 0x7F), newTake)
          }
        }}
        .filter {case (codePoint, completeChar) => completeChar == 0 }
        .flatMap {case (codePoint, isValid) => Character.toChars(codePoint) }
    }

    def encode(input:Observable[Char]) : Observable[Byte] = input.flatMapIterable(_.toString.getBytes(StandardCharsets.UTF_8))
    def encode(input:Iterable[Char]) : Iterable[Byte] = input.flatMap(_.toString.getBytes(StandardCharsets.UTF_8))

  }

}
