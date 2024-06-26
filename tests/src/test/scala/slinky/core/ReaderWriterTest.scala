package slinky.core

import slinky.readwrite.{Reader, WithRaw, Writer, ObjectOrWritten}

import scala.scalajs.js
import scala.scalajs.js.|

import org.scalatest.funsuite.AnyFunSuite

// cannot be a local class
class ValueClass(val int: Int) extends AnyVal

sealed trait MySealedTrait
case class SubTypeA(int: Int) extends MySealedTrait
case class SubTypeB(boolean: Boolean) extends MySealedTrait
case object SubTypeC extends MySealedTrait

case class ClassWithVararg(a: Int, bs: String*)

case class ClassWithDefault(a: Int = 5)

object ContainingPrivateType {
  sealed trait ToRead
  private[ContainingPrivateType] object Test extends ToRead

  val TestInstance = Test
}

class ReaderWriterTest extends AnyFunSuite {
  private def readWrittenSame[T](v: T,
                                 isOpaque: Boolean = false,
                                 beSame: Boolean = true,
                                 equality: (T, T) => Boolean = ((a: T, b: T) => a == b))
                                (implicit reader: Reader[T], writer: Writer[T]) = {
    val written = writer.write(v)
    if (!isOpaque) {
      assert(js.isUndefined(written) || (written == null) || js.isUndefined(written.asInstanceOf[js.Dynamic].__))
    } else {
      assert(!js.isUndefined(written.asInstanceOf[js.Dynamic].__))
    }

    if (beSame) {
      assert(equality(reader.read(written), v))
    }
  }

  test("Read/write - byte") {
    readWrittenSame(1.toByte)
  }

  test("Read/write - short") {
    readWrittenSame(1.toShort)
  }

  test("Read/write - int") {
    readWrittenSame(1)
  }

  test("Read/write - char") {
    readWrittenSame('a')
  }

  test("Read/write - long") {
    readWrittenSame(1L)
  }

  test("Read/write - float") {
    readWrittenSame(1F)
  }

  test("Read/write - double") {
    readWrittenSame(1D)
  }

  test("Read/write - js.Dynamic") {
    readWrittenSame(js.Dynamic.literal(a = 1))
  }

  test("Read/write - js.UndefOr") {
    val defined: js.UndefOr[List[Int]] = List(1)
    readWrittenSame(defined)
    val undefined: js.UndefOr[List[Int]] = js.undefined
    readWrittenSame(undefined)
  }

  test("Read/write - Option") {
    readWrittenSame[Option[String]](Some("hello"))
    readWrittenSame[Option[String]](None)
    assert(implicitly[Reader[Option[String]]].read(null).isEmpty)
    assert(implicitly[Reader[Option[String]]].read(js.undefined.asInstanceOf[js.Object]).isEmpty)
    assert(implicitly[Writer[Option[String]]].write(None) == null)
  }

  test("Read/write - Either") {
    readWrittenSame[Either[Int, String]](Left(1))
    readWrittenSame[Either[Int, String]](Right("hello"))
  }

  test("Read/write - tuple") {
    readWrittenSame((1, "hello", "bye"))
  }

  test("Read/write - js.|") {
    readWrittenSame[Int | String](1)
    readWrittenSame[Int | String]("str")
  }

  test("Read/write - js.Array") {
    readWrittenSame[js.Array[String]](js.Array("hello"))
  }

  test("Read/write - case class") {
    case class CaseClass(int: Int, boolean: Boolean)
    readWrittenSame(CaseClass(1, true))
  }

  test("Read/write - recursive case class") {
    case class RecursiveCaseClass(int: Int, recurse: Option[RecursiveCaseClass])
    readWrittenSame(RecursiveCaseClass(1, Some(RecursiveCaseClass(2, None))))
  }

  test("Read/write - case class with default js.undefined") {
    case class CaseClass(int: Int, boolean: js.UndefOr[Boolean] = js.undefined)
    readWrittenSame(CaseClass(1))
    readWrittenSame(CaseClass(1, true))

    // Assert that any undefined property in the input does not map to the written object's property;
    // not even as js.undefined
    val written = implicitly[Writer[CaseClass]].write(CaseClass(1))
    assert(written.asInstanceOf[js.Object].hasOwnProperty("int"))
    assert(!written.asInstanceOf[js.Object].hasOwnProperty("boolean"))
  }

  test("Read/write - case class with raw") {
    case class CaseClassWithRaw(int: Int, boolean: Boolean) extends WithRaw
    val inObj = js.Dynamic.literal(int = 1, boolean = true)
    val read = implicitly[Reader[CaseClassWithRaw]].read(inObj)
    assert(read == CaseClassWithRaw(1, true) && read.raw == inObj)
  }

  test("Read/write - sealed trait with case objects") {
    readWrittenSame[MySealedTrait](SubTypeA(-1))
    readWrittenSame[MySealedTrait](SubTypeB(true))
    readWrittenSame[MySealedTrait](SubTypeC)
  }

  test("Read/write - case class with shared type in reader and writer position") {
    case class TypeA()
    case class ComplexClass(a: TypeA, b: TypeA => Int)

    readWrittenSame(ComplexClass(TypeA(), _ => 1), beSame = false)
  }

  test("Read/write - case class with varargs") {
    readWrittenSame(ClassWithVararg(1, "hi", "hi", "bye"))
  }


  test("Read/write - value class") {
    readWrittenSame(new ValueClass(1))

    // directly writes the inner value without wrapping it in an object
    assert(implicitly[Writer[ValueClass]].write(new ValueClass(1)).asInstanceOf[Int] == 1)
  }

  test("Read/write - sequences") {
    readWrittenSame(List(1, 2))
  }

  test("Read/write - arrays") {
    readWrittenSame(Array(1, 2), equality = ((a: Array[Int], b: Array[Int]) => a.toList == b.toList))
  }

  test("Read/write - maps") {
    readWrittenSame(Map(1 -> 2, 3 -> 4))
  }

  test("Read/write - ranges (inclusive)") {
    readWrittenSame(1 to 10)
  }

  test("Read/write - ranges (exclusive)") {
    readWrittenSame(1 until 10)
  }

  test("Read/write - opaque class") {
    class OpaqueClass(int: Int)
    readWrittenSame(new OpaqueClass(1), true)
  }

  test("Read/write - option of opaque class") {
    class OpaqueClass(int: Int)
    assert(implicitly[Reader[Option[OpaqueClass]]].read(
      implicitly[Writer[Option[OpaqueClass]]].write(Some(new OpaqueClass(1)))
    ).get != null)
  }

  test("Read/write - private type defaults to opaque") {
    readWrittenSame[ContainingPrivateType.ToRead](ContainingPrivateType.TestInstance, true)
  }

  test("Read/write - any") {
    readWrittenSame[Any](123, true)
  }

  test("Reading empty object uses default parameter values when available") {
    assert(implicitly[Reader[ClassWithDefault]].read(js.Dynamic.literal()).a == 5)
  }

  test("Can convert Scala instance into ObjectOrWritten") {
    assert((SubTypeA(int = 123): ObjectOrWritten[SubTypeA])
      .asInstanceOf[js.Dynamic].int.asInstanceOf[Int] == 123)
  }

  test("Can convert Scala instance into js.UndefOr[ObjectOrWritten]") {
    assert((SubTypeA(int = 123): js.UndefOr[ObjectOrWritten[SubTypeA]])
      .asInstanceOf[js.Dynamic].int.asInstanceOf[Int] == 123)
  }

  test("Can convert js.Object into ObjectOrWritten") {
    assert((js.Dynamic.literal(int = 123): ObjectOrWritten[SubTypeA])
      .asInstanceOf[js.Dynamic].int.asInstanceOf[Int] == 123)
  }

  test("Can convert js.Object into js.UndefOr[ObjectOrWritten]") {
    assert((js.Dynamic.literal(int = 123): js.UndefOr[ObjectOrWritten[SubTypeA]])
      .asInstanceOf[js.Dynamic].int.asInstanceOf[Int] == 123)
  }

  // compilation test: can use derivation macro with type parameter when typeclass is available
  def deriveReaderTypeclass[T: Reader]: Reader[T] = {
    Reader.deriveReader[T]
  }

  def deriveWriterTypeclass[T: Writer]: Writer[T] = {
    Writer.deriveWriter[T]
  }
}
