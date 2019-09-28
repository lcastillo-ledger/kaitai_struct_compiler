package io.kaitai.struct.languages

import io.kaitai.struct._
import io.kaitai.struct.datatype.DataType._
import io.kaitai.struct.datatype._
import io.kaitai.struct.exprlang.Ast
import io.kaitai.struct.exprlang.Ast.expr
import io.kaitai.struct.format._
import io.kaitai.struct.languages.components._

import io.kaitai.struct.translators.{AbstractTranslator, NimTranslator, TypeDetector}

class NimCompiler(typeProvider: ClassTypeProvider, config: RuntimeConfig)
  extends LanguageCompiler(typeProvider, config)
    with SingleOutputFile
    with UpperCamelCaseClasses {
  import NimCompiler._

  // Members declared in io.kaitai.struct.languages.components.ExceptionNames
  override def ksErrorName(err: KSError): String = ""

  // Members declared in io.kaitai.struct.languages.components.ExtraAttrs
  override def extraAttrForIO(id: Identifier, rep: RepeatSpec): List[AttrSpec] = List()

  // Members declared in io.kaitai.struct.languages.components.LanguageCompiler
  override def alignToByte(io: String): Unit = ()
  override def attrFixedContentsParse(attrName: Identifier, contents: Array[Byte]): Unit = Array()
  override def attrParse(attr: AttrLikeSpec, id: Identifier, defEndian: Option[Endianness]): Unit = Option()
  override def attrParseHybrid(leProc: () => Unit, beProc: () => Unit): Unit = ()
  override def attrProcess(proc: ProcessExpr, varSrc: Identifier, varDest: Identifier): Unit = ()

  override def attributeDeclaration(attrName: Identifier, attrType: DataType, isNullable: Boolean): Unit = {
    val name = idToStr(attrName)
    out.puts(s"${name}*: ${kaitaiType2NimType(attrType)}")
  }

  override def attributeReader(attrName: Identifier, attrType: DataType, isNullable: Boolean): Unit = ()

  override def classConstructorFooter: Unit = {
    out.puts("stream.seek(streamPos)")
    out.dec
  }

  // The "constructor" is the read() proc
  override def classConstructorHeader(name: List[String], parentType: DataType, rootClassName: List[String], isHybrid: Boolean, params: List[ParamDefSpec]): Unit = {
    val current = types2class(name)
    val root = types2class(rootClassName)
    val parent = kaitaiType2NimType(parentType)
    out.puts
    out.puts(s"proc read*(_: typedesc[${current}], stream: KaitaiStream, root: ${root}, parent: ${parent}): owned ${current} =")
    out.inc
    out.puts(s"result = new(${current})")
    out.puts(s"let root = if root == nil: cast[$root](result) else: root")
    out.puts("result.root = root")
    out.puts("result.parent = parent")
  }

  override def classFooter(name: List[String]): Unit = {
    out.dec
  }

  override def classHeader(name: List[String]): Unit = {
    out.puts(s"${upperCamelCase(name.last)}* = ref object")
    out.inc
  }

  override def condIfFooter(expr: Ast.expr): Unit = ()
  override def condIfHeader(expr: Ast.expr): Unit = ()
  override def condRepeatEosFooter: Unit = ()
  override def condRepeatEosHeader(id: Identifier, io: String, dataType: DataType, needRaw: Boolean): Unit = ()
  override def condRepeatExprFooter: Unit = ()
  override def condRepeatExprHeader(id: Identifier, io: String, dataType: DataType, needRaw: Boolean, repeatExpr: Ast.expr): Unit = ()
  override def condRepeatUntilFooter(id: Identifier, io: String,dataType: DataType,needRaw: Boolean, repeatExpr: Ast.expr): Unit = ()
  override def condRepeatUntilHeader(id: Identifier, io: String,dataType: DataType,needRaw: Boolean, repeatExpr: Ast.expr): Unit = ()
  override def enumDeclaration(curClass: List[String], enumName: String, enumColl: Seq[(Long, EnumValueSpec)]): Unit = ()

  override def fileHeader(topClassName: String): Unit = {
    outHeader.puts(s"# $headerComment")
    importList.add(s"../../../runtime/nim/kaitai")
    out.puts
    out.puts("type")
    out.inc
  }

  override def indent: String = "  "
  override def instanceCalculate(instName: Identifier, dataType: DataType, value: Ast.expr): Unit = ()
  override def instanceCheckCacheAndReturn(instName: InstanceIdentifier, dataType: DataType): Unit = ()
  override def instanceDeclaration(attrName: InstanceIdentifier, attrType: DataType, isNullable: Boolean): Unit = {
    importList.add("options")
    val name = idToStr(attrName)
    out.puts(s"${name}*: Option[${kaitaiType2NimType(attrType)}]")
  }
  override def instanceFooter: Unit = ()
  override def instanceHeader(className: List[String], instName: InstanceIdentifier, dataType: DataType, isNullable: Boolean): Unit = ()
  override def instanceReturn(instName: InstanceIdentifier, attrType: DataType): Unit = ()
  override def normalIO: String = ""
  override def outFileName(topClassName: String): String =
    s"$topClassName.nim"
  override def popPos(io: String): Unit = ()
  override def pushPos(io: String): Unit = ()
  override def readFooter(): Unit = ()
  override def readHeader(endian: Option[FixedEndian], isEmpty: Boolean): Unit = ()
  override def runRead(): Unit = ()
  override def runReadCalc(): Unit = ()
  override def seek(io: String, pos: Ast.expr): Unit = ()

  val importListSrc = new ImportList
  override val translator: AbstractTranslator = new NimTranslator(typeProvider, importListSrc)

  override def useIO(ioEx: Ast.expr): String = ""

  // Members declared in io.kaitai.struct.languages.components.SwitchOps
  override def switchCaseEnd(): Unit = ()
  override def switchCaseStart(condition: Ast.expr): Unit = ()
  override def switchElseStart(): Unit = ()
  override def switchEnd(): Unit = ()
  override def switchStart(id: Identifier, on: Ast.expr): Unit = ()

  // Members declared in io.kaitai.struct.languages.components.SingleOutputFile
  override def outImports(topClass: ClassSpec) =
    "\n" + importList.toList.map((x) => s"import $x").mkString("\n") + "\n"

  def fromFileProc(name: List[String]): Unit = {
    val current = types2class(name)
    out.puts
    out.puts(s"proc fromFile*(_: typedesc[${current}], filename: string): owned ${current} =")
    out.inc
    out.puts("var stream = newKaitaiStream(filename)")
    out.puts(s"result = ${current}.read(stream, nil, nil)")
    out.puts("close(stream)")
    out.dec
  }

  def parseExpr(dataType: DataType, io: String, defEndian: Option[FixedEndian]): String = {
    dataType match {
      case t: ReadableType =>
        s"read${Utils.capitalize(t.apiCall(defEndian))}(stream)"
      case blt: BytesLimitType =>
        s"readBytes($io, int(${translator.translate(blt.size)}))"
      case _: BytesEosType =>
        s"readBytesFull($io)"
      case BytesTerminatedType(terminator, include, consume, eosError, _) =>
        s"readBytesTerm($io, $terminator, $include, $consume, $eosError)"
      case BitsType1 =>
        s"bool(readBitsInt($io, 1))"
      case BitsType(width: Int) =>
        s"readBitsInt($io, $width)"
      case t: UserType =>
        val addArgs = if (t.isOpaque) {
          ""
        } else {
          val parent = t.forcedParent match {
            case Some(USER_TYPE_NO_PARENT) => "nil"
            case Some(fp) => translator.translate(fp)
            case None => "result"
          }
          s", root, $parent"
        }
        s"${types2class(t.name)}.read($io$addArgs)"
    }
  }

  def readType(instName: Identifier, dataType: DataType, isArray: Boolean, endian: Option[FixedEndian]): Unit = {
    if (isArray) {
      out.puts("result." + idToStr(instName) + " = " + s"newSeq[${kaitaiType2NimType(dataType)}]()")
      out.puts("while not eof(stream):")
      out.inc
      out.puts("result." + idToStr(instName) + ".add(" + parseExpr(dataType, "stream", endian) + ")")
      out.dec
    } else {
      out.puts("result." + idToStr(instName) + " = " + parseExpr(dataType, "stream", endian))
    }
  }

  def readInstance(instName: Identifier, dataType: DataType, pos: Ast.expr, endian: Option[FixedEndian]): Unit = {
    out.puts(s"stream.seek(${translator.translate(pos)})")
    out.puts("result." + idToStr(instName) + " = some(" + parseExpr(dataType, "stream", endian) + ")")
  }

  def savePosition(): Unit = {
    out.puts("let streamPos = pos(stream)")
  }

  // Slightly different implementation than io.kaitai.struct.Utils
  // This is necessary because identifiers cannot start with "_" in Nim
  def lowerCamelCase(s: String): String = {
    if (s.startsWith("_")) {
      lowerCamelCase(s.substring(1))
    } else {
      val firstWord :: restWords = s.split("_").toList
      (firstWord :: restWords.map(Utils.capitalize)).mkString
    }
  }
  def upperCamelCase(s: String): String = {
    if (s.startsWith("_")) {
      upperCamelCase(s.substring(1))
    } else {
      s.split("_").map(Utils.capitalize).mkString
    }
  }

  def headerComment = "This is a generated file! Please edit source .ksy file and use kaitai-struct-compiler to rebuild"

  def idToStr(id: Identifier): String = {
    id match {
      case SpecialIdentifier(name) => lowerCamelCase(name)
      case NamedIdentifier(name) => lowerCamelCase(name)
      case NumberedIdentifier(idx) => s"_${NumberedIdentifier.TEMPLATE}$idx"
      case InstanceIdentifier(name) => lowerCamelCase(name)
      case RawIdentifier(innerId) => "raw_" + idToStr(innerId)
    }
  }
}

object NimCompiler extends LanguageCompilerStatic
  with UpperCamelCaseClasses {
  override def getCompiler(
    tp: ClassTypeProvider,
    config: RuntimeConfig
  ): LanguageCompiler = new NimCompiler(tp, config)

  def kaitaiType2NimType(attrType: DataType): String = {
    attrType match {
      case Int1Type(false) => "uint8"
      case IntMultiType(false, Width2, _) => "uint16"
      case IntMultiType(false, Width4, _) => "uint32"
      case IntMultiType(false, Width8, _) => "uint64"

      case Int1Type(true) => "int8"
      case IntMultiType(true, Width2, _) => "int16"
      case IntMultiType(true, Width4, _) => "int32"
      case IntMultiType(true, Width8, _) => "int64"

      case FloatMultiType(Width4, _) => "float32"
      case FloatMultiType(Width8, _) => "float64"

      case BitsType(_) => "uint64"

      case _: BooleanType => "bool"
      case CalcIntType => "int"
      case CalcFloatType => "float64"

      case _: StrType => "string"
      case _: BytesType => "seq[byte]"

      case KaitaiStructType | CalcKaitaiStructType => "ref RootObj"

      case t: UserType => types2class(t.name)
      case EnumType(name, _) => types2class(name)

      case ArrayType(inType) => s"seq[${kaitaiType2NimType(inType)}]"

      case st: SwitchType => kaitaiType2NimType(st.combinedType)
    }
  }

  def types2class(names: List[String]) = Utils.upperCamelCase(names.last)
}
