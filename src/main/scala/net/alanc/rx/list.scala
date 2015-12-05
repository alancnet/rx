package net.alanc.rx

import rx.lang.scala.subjects.ReplaySubject
import rx.lang.scala.{Notification, Observer, Observable, Subject}
import scala.collection.mutable
import scala.concurrent.Promise
import scala.concurrent.duration.Duration
import scala.reflect.ClassTag

object list {
  // Pimp My Library: https://coderwall.com/p/k_1jzw/scala-s-pimp-my-library-pattern-example
  implicit class PimpIterableAnyVal[T <: AnyVal](val input: Iterable[T]) extends AnyVal {
    def splitBySlice(delimiter: Iterable[T]) = list.splitBySlice(input, delimiter)
    def split(delimiter: T) = list.split(input, delimiter)
  }

  implicit class PimpObservableAnyVal[T <: AnyVal](val input: Observable[T]) extends AnyVal {
    def splitBySlice(delimiter: Iterable[T]) = list.splitBySlice(input, delimiter)
    def split(delimiter: T) = list.split(input, delimiter)
  }

  implicit class PimpIterable[T](val input: Iterable[T]) extends AnyVal {
    def pipe(output:Subject[T]) = list.pipe(input, output)
    def doOnEach(action: T => Unit) = input.map(x => {
      action(x)
      x
    })
    def debug = input.doOnEach(x=>println(x))
    def debug(text:String) = input.doOnEach(x => println(text.replace("$0", x.toString)))
    def debug(text:T=>String) = input.doOnEach(x => println(text(x)))
  }

  implicit class PimpObservable[T](val input: Observable[T]) extends AnyVal {
    def pipe(output:Observer[T]) = list.pipe(input, output)
    def toQueue = list.toQueue(input)
    def ofType[X](implicit tag: ClassTag[X]):Observable[X] = input.filter(x=>tag.runtimeClass == x.getClass).map(x=>x.asInstanceOf[X])
    def debug = input.doOnEach(x=>println(x))
    def debug(text:String) = input.doOnEach(x => println(text.replace("$0", x.toString)))
    def debug(text:T=>String) = input.doOnEach(x => println(text(x)))

    def future = RxFuture(input)
    def toFlush = {
      val subject = FlushSubject[T]()
      input.subscribe(subject)
      subject.observable
    }
    def repipe = {
      def sub = ReplaySubject[T]()
      input.subscribe(sub)
      sub
    }


  }
  implicit class PimpAny[T <: Any](val o:T) extends AnyVal {
    def pimpe[U](action: T => U) = action(o)
  }

  // End Pimp My Library


  private def splitBySlice[T](input: Observable[T], doSplit: (List[T]) => Option[List[T]]): Observable[Iterable[T]] = {
    input
      .scan((false, List.empty[T]))((acc: (Boolean, List[T]), x: T) => {
        val clear = acc._1
        val list = if (clear) List.empty[T] else acc._2
        val newList = x :: list
        doSplit(newList) match {
          case Some(segment) => (true, segment)
          case None => (false, newList)
        }
      })
      .filter(x => x._1)
      .map(x => x._2.reverse)
  }

  def splitBySlice[T <: AnyVal](input: Observable[T], delimiter: Iterable[T]): Observable[Iterable[T]] = {
    val reverseDelimiter = delimiter.toList.reverse
    def test(delimiter: Iterable[T], chunk: List[T]): Option[List[T]] =
      if (delimiter.isEmpty) Some(chunk)
      else if (chunk.isEmpty) None
      else if (chunk.head != delimiter.head) None
      else test(delimiter.tail, chunk.tail)
    splitBySlice(
      input,
      (x: List[T]) => test(reverseDelimiter, x)
    )
  }

  private def splitBySlice[T](input: Iterable[T], doSplit: (List[T]) => Option[List[T]]): Iterable[Iterable[T]] = {
    input
      .scanLeft((false, List.empty[T]))((acc: (Boolean, List[T]), x: T) => {
        val clear = acc._1
        val list = if (clear) List.empty[T] else acc._2
        val newList = x :: list
        doSplit(newList) match {
          case Some(segment) => (true, segment)
          case None => (false, newList)
        }
      })
      .filter(x => x._1)
      .map(x => x._2.reverse)
  }

  def splitBySlice[T <: AnyVal](input: Iterable[T], delimiter: Iterable[T]): Iterable[Iterable[T]] = {
    val reverseDelimiter = delimiter.toList.reverse
    def test(delimiter: Iterable[T], chunk: List[T]): Option[List[T]] =
      if (delimiter.isEmpty) Some(chunk)
      else if (chunk.isEmpty) None
      else if (chunk.head != delimiter.head) None
      else test(delimiter.tail, chunk.tail)
    splitBySlice(
      input,
      (x: List[T]) => test(reverseDelimiter, x)
    )
  }


  def split[T](input: Observable[T], doSplit: (T) => Boolean): Observable[Iterable[T]] = {
      input
        .scan((false, List.empty[T]))((acc: (Boolean, List[T]), x: T) => {
        val clear = acc._1
        val list = if (clear) List.empty[T] else acc._2
        if (doSplit(x)) (true, list)
        else (false, x :: list)
      })
        .filter(x => x._1)
        .map(x => x._2.reverse)
    }

    def split[T <: AnyVal](input: Observable[T], delimiter: T): Observable[Iterable[T]] = split(input, (x: T) => x == delimiter)

    def split[T](input: Iterable[T], doSplit: (T) => Boolean): Iterable[Iterable[T]] = {
      input
        .scanLeft((false, List.empty[T]))((acc: (Boolean, List[T]), x: T) => {
        val clear = acc._1
        val list = if (clear) List.empty[T] else acc._2
        if (doSplit(x)) (true, list)
        else (false, x :: list)
      })
        .filter(x => x._1)
        .map(x => x._2.reverse)
    }

    def split[T <: AnyVal](input: Iterable[T], delimiter: T): Iterable[Iterable[T]] = split(input, (x: T) => x == delimiter)

    def byLine(input:Iterable[Char]) = splitBySlice(input, "\r\n").map(line => line.mkString(""))
    def byLine(input:Observable[Char]) = splitBySlice(input, "\r\n").map(line => line.mkString(""))

    def readLines(input:Iterable[Char], length:Int) = byLine(input).take(length).toList
    def readLines(input:Observable[Char], length:Int) = byLine(input).take(length).toList

    def toChars(input:Iterable[String]) = input.flatten
    def toChars(input:Observable[String]):Observable[Char] = input.flatMap((str:String) => Observable.from(str))

    def pipe[T](input:Iterable[T], output:Subject[T]) = { input.foreach(x => output.onNext(x)); output }
    def pipe[T](input:Observable[T], output:mutable.Queue[T]) = { input.subscribe(t => output.enqueue(t)); output }
    def pipe[T](input:Observable[T], output:Observer[T]) = { input.subscribe(t => output.onNext(t)); output }

    def join[T](input:Iterable[T], delimiter:T):Iterable[T] = input.scanLeft(List.empty[T])(
        (acc:List[T], v:T) => if (acc.isEmpty) List(v) else List(delimiter, v)
      )
      .flatten

    def afterEach[T](input:Iterable[T], delimiter:T):Iterable[T] =
      input.map(List(_, delimiter)).flatten

    def toQueue[T](input:Observable[T]): mutable.Queue[T] = {
      val q = mutable.Queue[T]()
      pipe(input, q)
      q
    }

    def toIterable[T](input:Observable[T], duration: Duration) = input.toBlocking.toIterable

    def toList[T](input:Observable[T], length:Int, duration: Duration):List[T] = toIterable(input, duration).take(length).toList
    def toList[T](input:Observable[T], length:Int):List[T] = toList(input, length, Duration.Inf)

  }

