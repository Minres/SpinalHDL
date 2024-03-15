/*                                                                           *\
**        _____ ____  _____   _____    __                                    **
**       / ___// __ \/  _/ | / /   |  / /   HDL Core                         **
**       \__ \/ /_/ // //  |/ / /| | / /    (c) Dolu, All rights reserved    **
**      ___/ / ____// // /|  / ___ |/ /___                                   **
**     /____/_/   /___/_/ |_/_/  |_/_____/                                   **
**                                                                           **
**      This library is free software; you can redistribute it and/or        **
**    modify it under the terms of the GNU Lesser General Public             **
**    License as published by the Free Software Foundation; either           **
**    version 3.0 of the License, or (at your option) any later version.     **
**                                                                           **
**      This library is distributed in the hope that it will be useful,      **
**    but WITHOUT ANY WARRANTY; without even the implied warranty of         **
**    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU      **
**    Lesser General Public License for more details.                        **
**                                                                           **
**      You should have received a copy of the GNU Lesser General Public     **
**    License along with this library.                                       **
\*                                                                           */
package spinal.core.internals

import spinal.core._

import scala.collection.mutable
import scala.collection.mutable.{ArrayBuffer, ListBuffer}


class PhaseVerilog(pc: PhaseContext, report: SpinalReport[_]) extends PhaseMisc with VerilogBase {
  import pc._
  globalPrefix = pc.config.globalPrefix

  var outFile: java.io.FileWriter = null
  def rtlName = (if(pc.config.netlistFileName == null)(topLevel.definitionName + (if(pc.config.isSystemVerilog) ".sv" else ".v")) else pc.config.netlistFileName)
  def targetPath = pc.config.targetDirectory + "/" + rtlName

  override def impl(pc: PhaseContext): Unit = {

    report.toplevelName = pc.topLevel.definitionName
    if (!pc.config.oneFilePerComponent) {
      report.generatedSourcesPaths += targetPath
      outFile = new java.io.FileWriter(targetPath)
      outFile.write(VhdlVerilogBase.getHeader("//", pc.config.rtlHeader, topLevel, config.headerWithDate, config.headerWithRepoHash))

      outFile.write("`timescale 1ns/1ps")

      emitEnumPackage(outFile)

      val componentsText = ArrayBuffer[() => String]()
      for (c <- sortedComponents) {
        if (!c.isInBlackBoxTree) {
          componentsText += compile(c)
        }
      }

      for(e <- componentsText.reverse){
        outFile.write(e())
      }

      val bbImplStrings = mutable.HashSet[String]()
      sortedComponents.foreach{
        case bb : BlackBox if bb.impl != null => {
          val str = bb.impl.getVerilog()
          if(!bbImplStrings.contains(str)) {
            bbImplStrings += str
            outFile.write("\n")
            outFile.write(str)
            outFile.write("\n")
          }
        }
        case _ =>
      }

      outFile.flush()
      outFile.close()
    }
    else {
      val fileList: mutable.LinkedHashSet[String] = new mutable.LinkedHashSet()
      // dump Enum define to define.v instead attach that on every .v file
      if(enums.nonEmpty){
        val defineFileName = pc.config.targetDirectory + "/enumdefine" + (if(pc.config.isSystemVerilog) ".sv" else ".v")
        val defineFile = new java.io.FileWriter(defineFileName)
        emitEnumPackage(defineFile)
        defineFile.flush()
        defineFile.close()
        fileList += defineFileName
      }

      val bbImplStrings = mutable.HashSet[String]()
      for (c <- sortedComponents) {
        val moduleContent = compile(c)()
        val targetFilePath = pc.config.targetDirectory + "/" +  (if(pc.config.netlistFileName == null)(c.definitionName + (if(pc.config.isSystemVerilog) ".sv" else ".v")) else pc.config.netlistFileName)
        report.generatedSourcesPaths += targetFilePath

        if (!moduleContent.contains("replaced by")) {
          if (!c.isInBlackBoxTree) {
            outFile = new java.io.FileWriter(targetFilePath)
            outFile.write(VhdlVerilogBase.getHeader("//", pc.config.rtlHeader, c, config.headerWithDate, config.headerWithRepoHash))
            outFile.write("`timescale 1ns/1ps ")
//            emitEnumPackage(outFile)
            outFile.write(moduleContent)
            outFile.flush()
            outFile.close()
            fileList += targetFilePath
          }
          c match {
            case bb: BlackBox =>
              fileList ++= bb.listRTLPath
              if (bb.impl != null) {
                val str = bb.impl.getVerilog()
                if(!bbImplStrings.contains(str)) {
                  outFile = new java.io.FileWriter(targetFilePath)
                  outFile.write(VhdlVerilogBase.getHeader("//", pc.config.rtlHeader, c, config.headerWithDate, config.headerWithRepoHash))
                  outFile.write("`timescale 1ns/1ps ")
                  outFile.write(str)
                  outFile.flush()
                  outFile.close()
                  fileList += targetFilePath
                  bbImplStrings += str
                }
              }
            case _ =>
          }
        }
      }

      val fileListFile = new java.io.FileWriter(pc.config.targetDirectory + topLevel.definitionName + ".lst")
      fileList.foreach(file => fileListFile.write(file.replace("//", "/") + "\n"))
      fileListFile.flush()
      fileListFile.close()
    }
  }

  val allocateAlgoIncrementaleBase = globalData.allocateAlgoIncrementale()
  val usedDefinitionNames = mutable.HashSet[String]()


  val romCache = mutable.HashMap[String, String]()
  def compile(component: Component): () => String = {
    val componentBuilderVerilog = new ComponentEmitterVerilog(
      c                           = component,
      systemVerilog               = pc.config.isSystemVerilog,
      verilogBase                 = this,
      algoIdIncrementalBase       = allocateAlgoIncrementaleBase,
      mergeAsyncProcess           = config.mergeAsyncProcess,
      asyncResetCombSensitivity   = config.asyncResetCombSensitivity,
      anonymSignalPrefix          = if(pc.config.anonymSignalUniqueness) globalData.anonymSignalPrefix + "_" + component.definitionName else globalData.anonymSignalPrefix,
      nativeRom                   = config.inlineRom,
      nativeRomFilePrefix         = rtlName,
      emitedComponentRef          = emitedComponentRef,
      emitedRtlSourcesPath        = report.generatedSourcesPaths,
      spinalConfig                = pc.config,
      pc                          = pc,
      romCache                    = romCache
    )

    if(component.parentScope == null && pc.config.dumpWave != null) {
      componentBuilderVerilog.logics ++=
        s"""
  initial begin
    $$dumpfile("${pc.config.dumpWave.vcdPath}");
    $$dumpvars(${pc.config.dumpWave.depth}, ${component.definitionName});
  end
"""
    }

    val trace = componentBuilderVerilog.getTrace()
    val oldComponent = emitedComponent.getOrElse(trace, null)

    if (oldComponent == null || component.definitionNameNoMerge && component.definitionName != oldComponent.definitionName) {
      assert(!usedDefinitionNames.contains(component.definitionName) || component.isInBlackBoxTree, s"Component '${component}' with definition name '${component.definitionName}' was already used once for a different layout\n${component.getScalaLocationLong}")
      usedDefinitionNames += component.definitionName
      emitedComponent += (trace -> component)
      () => componentBuilderVerilog.result
    } else {
      emitedComponentRef.put(component, oldComponent)
      val originalName = component.definitionName
      component match{
        case x: BlackBox =>
        case _ => component.definitionName = oldComponent.definitionName
      }
      () => s"\n//${originalName} replaced by ${oldComponent.definitionName}\n"
    }
  }

  val emitedComponent    = mutable.Map[ComponentEmitterTrace, Component]()
  val emitedComponentRef = new java.util.concurrent.ConcurrentHashMap[Component,Component]()

  def emitEnumPackage(out: java.io.FileWriter): Unit = {
    val ret = new StringBuilder()

    ret ++= "\n"
    for ((enumDef, encodings) <- enums) {
      val enumName = enumDef.getName()
      for (encoding <- encodings) {
        val encodingName = encoding.getName()
        val bitCount     = encoding.getWidth(enumDef)
        val vhdlEnumType = emitEnumType(enumDef, encoding, "")

//        ret ++= s"`define $vhdlEnumType [${bitCount - 1}:0]\n"
        if(enumDef.isGlobalEnable) {
          for (element <- enumDef.elements) {
            ret ++= s"`define ${emitEnumLiteral(element, encoding, "")} ${idToBits(element, encoding)}\n"
          }

          ret ++= "\n"
        }
      }
    }

    ret ++= "\n"
    for ((name, interface) <- svInterface) {
      emitInterface(interface, ret)
    }

    def idToBits[T <: SpinalEnum](senum: SpinalEnumElement[T], encoding: SpinalEnumEncoding): String = {
      val str    = encoding.getValue(senum).toString(2)
      val length = encoding.getWidth(senum.spinalEnum)
      length.toString + "'b" + ("0" * (length - str.length)) + str
    }

    out.write(ret.result())
  }


  def emitFunctions(component: Component, ret: StringBuilder): Unit = {
    val alreadyEmitted = mutable.Set[String]()
    component.dslBody.walkStatements(s => s.walkExpression {
      case node: CastEnumToEnum =>
        val encodingSrc = node.input.getEncoding
        val enumDef     = node.getDefinition
        val encodingDst = node.getEncoding
        val fName       = getReEncodingFuntion(enumDef, encodingSrc, encodingDst)

        if (!alreadyEmitted.contains(fName)) {
          alreadyEmitted += fName
          ret ++= s"  function $fName(${emitEnumType(enumDef, encodingSrc)} that);\n"
          ret ++= "  begin\n"
          ret ++= "    case(that) \n"
          for (e <- enumDef.elements) {
            ret ++= s"      ${emitEnumLiteral(e, encodingSrc)} : $fName =  ${emitEnumLiteral(e, encodingDst)};\n"
          }
          ret ++= s"      default : $fName =  ${emitEnumLiteral(enumDef.elements.head, encodingDst)};\n"
          ret ++= "    endcase\n"
          ret ++= "  end\n"
          ret ++= "  endfunction\n\n"
        }
      case _ =>
    })
  }

  def emitInterface(interface: Interface, ret: StringBuilder): Unit = {
    val theme = new Tab2 //TODO add into SpinalConfig
    ret ++= s"interface ${interface.getClass().getSimpleName()} () ;\n\n"
    for ((name, elem) <- interface.elementsCache) {
      val size = elem match {
        case _: Bool => ""
        case node: WidthProvider => s"[${node.getWidth - 1}:0]"
        case _ => LocatedPendingError("The SystemVerilog interface feature is still an experimental feature. In interface, only BaseType is supported yet")
      }
      ret ++= f"${theme.porttab}logic  ${size}%-8s ${name} ;\n"
    }
    ret ++= "\n"
    interface.allModPort
      .foreach{case x =>
        var modportString = ""
        modportString += s"${theme.porttab}modport ${x} (\n"

        val toplevel = globalData.toplevel
        val phase = globalData.phaseContext.topLevel
        globalData.toplevel = null
        globalData.phaseContext.topLevel = null
        val c = new Component {
          val y = interface.clone().asInstanceOf[interface.type]
          y.callModPort(x)
        }
        globalData.toplevel = toplevel
        globalData.phaseContext.topLevel = phase

        for ((name, elem) <- c.y.elementsCache) {
          val dir = elem.dir match {
            case `in`    => "input "
            case `out`   => "output"
            case `inout` => "inout "
            case _       => throw new Exception(s"Unknown direction in interface ${interface}: ${elem}"); ""
          }
          modportString += s"${theme.porttab}${theme.porttab}${dir}  ${name},\n"
        }
        modportString = modportString.stripSuffix(",\n") + "\n"
        modportString += s"${theme.porttab});\n\n"
        ret ++= modportString
      }
    ret ++= "endinterface\n\n"
  }
}

class PhaseInterface(pc: PhaseContext) extends PhaseNetlist{

  override def impl(pc: PhaseContext): Unit = {
    import pc._

    //rename myif_a to myif.a
    walkDeclarations {
      case node: BaseType if(node.hasTag(IsInterface)) => {
        svInterface += node.parent.getClass().getSimpleName() -> node.parent.asInstanceOf[Interface]
        if(node.parent.getName() == null || node.parent.getName() == "") {
          PendingError(s"INTERFACE SHOULD HAVE NAME: ${node.toStringMultiLine} at \n${node.getScalaLocationLong}")
        }
        val p = node.parent.getName()
        val p_rename = p//node.component.localNamingScope.allocateName(p)
        val newName = node.getName().replace(s"${p}_", s"${p_rename}.")//TODO:
        node.name = newName
      }
      case _ =>
    }
  }
}
