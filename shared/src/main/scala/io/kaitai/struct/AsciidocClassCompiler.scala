package io.kaitai.struct

import io.kaitai.struct.datatype.DataType
import io.kaitai.struct.datatype.DataType.{BitsType, BytesEosType, BytesLimitType, BytesTerminatedType, BytesType, EnumType, ReadableType, StrFromBytesType, SwitchType, UserType, UserTypeFromBytes}
import io.kaitai.struct.exprlang.Ast
import io.kaitai.struct.exprlang.Ast.expr.EnumByLabel
import io.kaitai.struct.format.{AttrSpec, ClassSpec, ClassSpecs, DynamicSized, EnumSpec, FixedSized, NotCalculatedSized, ParseInstanceSpec, Sized, StartedCalculationSized, ValidationEq, ValueInstanceSpec}
import io.kaitai.struct.languages.components.{LanguageCompiler, LanguageCompilerStatic}
import io.kaitai.struct.precompile.CalculateSeqSizes

import scala.collection.mutable.ListBuffer

class AsciidocClassCompiler(classSpecs: ClassSpecs, topClass: ClassSpec) extends DocClassCompiler(classSpecs, topClass) {
  import AsciidocClassCompiler._

  override def outFileName(topClass: ClassSpec): String = s"${topClass.nameAsStr}.adoc"

  override def indent: String = ""

  override def fileHeader(topClass: ClassSpec): Unit = {
    out.puts(s"= ${type2str(topClass.name.last)} format specification")
    out.puts
    // TODO: parse & output meta/title, meta/file-extensions, etc
  }

  override def fileFooter(topClass: ClassSpec): Unit = {
    //out.puts()
  }

  override def classHeader(classSpec: ClassSpec): Unit = {
    out.inc
    out.puts(s"[[${classSpec2Anchor(classSpec)}]]")
    out.puts(s"${headerByIndent} Type: ${type2str(classSpec.name.last)}")
    out.puts

    classSpec.doc.summary.foreach(summary =>
      out.puts(s"$summary")
    )
    out.puts
  }

  override def classFooter(classSpec: ClassSpec): Unit = {
    out.dec
  }

  override def seqHeader(classSpec: ClassSpec): Unit = {
    out.puts(s".Table ${type2str(classSpec.name.last)}")
    out.puts("""[cols="^.^1,^.^2,^.^2m,^.^4m,^.^8"]
      ||===
      || pos | size | id | type | description
    """.stripMargin)
    out.puts

  }

  override def seqFooter(classSpec: ClassSpec): Unit = {
    out.puts("|===")
    out.puts
  }

  val END_OF_STREAM = "⇲"
  val UNKNOWN = "..."

  /**
    * Determine visual interpretation of data type's size to be used in
    * a displayed table.
    *
    * @param dataType data type to analyze
    * @param attrName attribute name to be used to generate port name for affected vars linking
    * @return a String that should be displayed in table's column "size"
    */
  def dataTypeSizeAsString(dataType: DataType, attrName: String): String = {
    dataType match {
      case _: BytesEosType => END_OF_STREAM
      case blt: BytesLimitType => translator.translate(blt.size)
      case StrFromBytesType(basedOn, _) => dataTypeSizeAsString(basedOn, attrName)
      case utb: UserTypeFromBytes => dataTypeSizeAsString(utb.bytes, attrName)
      case EnumType(_, basedOn) => dataTypeSizeAsString(basedOn, attrName)
      case _ =>
        CalculateSeqSizes.dataTypeBitsSize(dataType) match {
          case FixedSized(n) =>
            if (n % 8 == 0) {
              s"${n / 8}"
            } else {
              s"${n}b"
            }
          case DynamicSized => UNKNOWN
          case NotCalculatedSized | StartedCalculationSized =>
            throw new RuntimeException("Should never happen: problems with CalculateSeqSizes")
        }
    }
  }
  def compileSwitch(st: SwitchType): Unit = {
    out.puts(
      s"""a|
         |[cols="^.^1m,^.^1m",frame="none",grid="rows"]
         |!===
         |! #${translator.translate(st.on)} value! format""".stripMargin)
    st.cases.foreach { case (caseExpr, caseType) =>
      caseType match {
        case ut: UserType =>
          out.puts(s"! ${caseExpr2String(caseExpr)} ! ${kaitaiType2NativeType(caseType)}")
        case _ =>
        // ignore, no links
      }
    }
    out.puts("!===")
  }

  override def compileSeqAttr(classSpec: ClassSpec, attr: AttrSpec, seqPos: Option[Int], sizeElement: Sized, sizeContainer: Sized): Unit = {
    out.puts(s"| ${GraphvizClassCompiler.seqPosToStr(seqPos).getOrElse("???")} | ${dataTypeSizeAsString(attr.dataType,attr.id.humanReadable)} | ${attr.id.humanReadable}")

    attr.dataType match {
      case st: SwitchType =>
        compileSwitch(st)
      case _ =>
        out.puts(s"| ${kaitaiType2NativeType(attr.dataType)}${attrConstraints2String(attr)}")
    }

    out.puts(s"| ${attr.doc.summary.getOrElse("")}")
    out.puts
  }

  override def compileParseInstance(classSpec: ClassSpec, inst: ParseInstanceSpec): Unit = {
    out.puts(s"*Parse instance:*: ${inst.id.humanReadable}")
    out.puts("[cols=\"^.^1,^.^2,^.^2m,^.^4m,^.^8\"]")
    out.puts("|===")
    out.puts("| pos | size | id | type | description")
    out.puts

    out.puts(s"| ${expression(inst.pos)} | ${dataTypeSizeAsString(inst.dataType,inst.id.humanReadable)} | ${inst.id.humanReadable}")

    inst.dataType match {
      case st: SwitchType =>
        compileSwitch(st)
      case _ =>
        out.puts(s"| ${kaitaiType2NativeType(inst.dataType)}")
    }

    out.puts(s"| ${inst.doc.summary.getOrElse("")}")
    out.puts("|===")
    out.puts
  }

  override def compileValueInstance(vis: ValueInstanceSpec): Unit = {
    out.puts(s"value instance: ${vis}")
    out.puts
  }

  override def compileEnum(enumName: String, enumColl: EnumSpec): Unit = {
    out.inc
    out.puts(s"[[${enumSpec2Anchor(enumColl)}]]")
    out.puts(s"${headerByIndent} Enum: ${type2str(enumName)}")
    out.puts

    out.puts("[cols=\"^.^1m,^.^1m,^.^4\"]")
    out.puts("|===")
    out.puts("| value | id | description")
    out.puts

    enumColl.sortedSeq.foreach { case (id, value) =>
      out.puts(s"| ${value.name} | $id | ${value.doc.summary.getOrElse("")}")
    }

    out.puts("|===")
    out.puts
    out.dec
  }

  def headerByIndent: String = "=" * (out.indentLevel + 1)

  def expression(exOpt: Option[Ast.expr]): String = {
    exOpt match {
      case Some(ex) => translator.translate(ex)
      case None => ""
    }
  }

  def caseExpr2String(expr: Ast.expr): String = expr match {
    case EnumByLabel(enumName, label, inType) =>
      label.name
    case _ =>
      translator.translate(expr)
  }
}

object AsciidocClassCompiler extends LanguageCompilerStatic {
  // FIXME: Unused, should be probably separated from LanguageCompilerStatic
  override def getCompiler(
                            tp: ClassTypeProvider,
                            config: RuntimeConfig
                          ): LanguageCompiler = ???

  def type2str(name: String): String = Utils.upperCamelCase(name)

  def classSpec2Anchor(spec: ClassSpec): String = "type-" + spec.name.mkString("-")

  def enum2Anchor(name: List[String]): String = "enum-" + name.mkString("-")

  def enumSpec2Anchor(spec: EnumSpec): String = enum2Anchor(spec.name)

  def type2display(name: List[String]) = name.map(Utils.upperCamelCase).mkString("::")

  def dataTypeName(dataType: DataType): String = {
    dataType match {
      case rt: ReadableType => rt.apiCall(None) // FIXME
      case ut: UserType => type2display(ut.name)
      case BytesTerminatedType(terminator, include, consume, eosError, _) =>
        val args = ListBuffer[String]()
        if (terminator != 0)
          args += s"term=$terminator"
        if (include)
          args += "include"
        if (!consume)
          args += "don't consume"
        if (!eosError)
          args += "ignore EOS"
        args.mkString(", ")
      case _: BytesType => "bytes"
      case StrFromBytesType(basedOn, encoding) =>
        val bytesStr = dataTypeName(basedOn)
        val comma = if (bytesStr.isEmpty) "" else ", "
        s"str($bytesStr$comma$encoding)"
      case enum: EnumType =>
        s"${dataTypeName(enum.basedOn)} → <<${enumSpec2Anchor(enum.enumSpec.get)},${type2display(enum.name)}>>"
      case BitsType(width, bitEndian) => s"b$width"
      case _ => dataType.toString
    }
  }

  def kaitaiType2NativeType(attrType: DataType): String = attrType match {
      case ut: UserType =>
        s"<<${classSpec2Anchor(ut.classSpec.get)},${type2str(ut.name.last)}>>"
      case _ => dataTypeName(attrType)
    }

  def attrConstraints2String(attr: AttrSpec) = attr.valid match {
      case Some(ValidationEq(contents: Ast.expr.List)) =>
        "={" + contents.elts.map(_.evaluateIntConst.getOrElse("").formatted("0x%02X")).mkString(",")  + "}"
      case _ => ""
    }
}
