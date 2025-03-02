package scadla.backends.obj

import scadla._
import scala.util.parsing.combinator._
import dzufferey.utils._
import dzufferey.utils.LogLevel._
import java.io._
import squants.space.{Length, Millimeters, LengthUnit}

// https://en.wikipedia.org/wiki/Wavefront_.obj_file
// We assume that the faces are oriented and only triangles

object Parser extends Parser(Millimeters) {
}

class Parser(unit: LengthUnit = Millimeters) extends JavaTokenParsers {

  sealed abstract class ObjCmd
  case object Skip extends ObjCmd
  case class VertexDef(p: Point) extends ObjCmd
  case class FaceDef(a: Int, b: Int, c: Int) extends ObjCmd
  case class ObjectDef(name: String) extends ObjCmd
  case class GroupDef(name: String) extends ObjCmd


  def nonWhite: Parser[String] = """[^\s]+""".r

  def comment: Parser[String] = """#[^\n]*""".r

  def parseVertex: Parser[Point] =
    "v" ~> repN(3, floatingPointNumber) ~ opt(floatingPointNumber) ^^ {
      case List(a, b,c) ~ None => Point(Millimeters(a.toDouble), Millimeters(b.toDouble), Millimeters(c.toDouble))
      case List(a, b,c) ~ Some(w) => Point(Millimeters(a.toDouble / w.toDouble), Millimeters(b.toDouble / w.toDouble), Millimeters(c.toDouble / w.toDouble))
    }



  def vertexIdx: Parser[Int] =
    wholeNumber ~ opt( "/" ~> opt(wholeNumber)) ~ opt("/" ~> wholeNumber) ^^ {
      case idx ~ _ ~ _ => idx.toInt
    }

  def parseFace: Parser[FaceDef] =
    "f" ~> repN(3, vertexIdx) ^^ {
      case List(a, b,c) =>
        assert(a >= 0 && b >= 0 && c >= 0, "obj parser only supports absolute indices")
        FaceDef(a, b, c)
    }

  def parseCmd: Parser[ObjCmd] = (
    parseVertex ^^ ( v => VertexDef(v) )
  | parseFace
  | "g" ~> ident ^^ ( id => GroupDef(id) )
  | "o" ~> ident ^^ ( id => ObjectDef(id) )
  | "vt" ~> repN(2, floatingPointNumber) ~ opt(floatingPointNumber) ^^^ Skip
  | "vn" ~> repN(3, floatingPointNumber) ^^^ Skip
  | "mtllib" ~> nonWhite ^^^ Skip
  | "usemtl" ~> ident ^^^ Skip
  | "s" ~> (wholeNumber | ident) ^^^ Skip
  | comment ^^^ Skip
  )
  
  def parseCmds: Parser[List[ObjCmd]] = rep(parseCmd)

  def apply(reader: java.io.Reader): Polyhedron = {
    val result = parseAll(parseCmds, reader)
    if (result.successful) {
      val commands = result.get
      assert(commands.count{ case ObjectDef(_) | GroupDef(_) => true; case _ => false} == 1, "expected 1 group/object")
      val points: Array[Point] = commands.collect{ case VertexDef(v) => v }.toArray
      val faces = commands.collect{ case FaceDef(a,b,c) => Face(points(a-1), points(b-1), points(c-1)) }
      Polyhedron(faces)
    } else {
      Logger.logAndThrow("obj.Parser", dzufferey.utils.LogLevel.Error, "parsing error: " + result.toString)
    }
  }
  
  def apply(fileName: String): Polyhedron = {
    val reader = new BufferedReader(new FileReader(fileName))
    apply(reader)
  }

}

