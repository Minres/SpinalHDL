package spinal.lib.debug

import spinal.core._
import spinal.core.internals.PhaseContext
import spinal.lib.blackbox.nanoxplore.NxScope

import java.nio.file.Path
import scala.collection.mutable
import scala.language.postfixOps

trait DebugImplementation {
  def implementDebug(info: DebugInformation, pc: PhaseContext): Unit
}

object nxDebugTrigger extends SpinalTag

class NxScopeDebugImpl extends DebugImplementation {
  override def implementDebug(info: DebugInformation, pc: PhaseContext): Unit = {
    assert(info.debugWidth <= 240, "NxScope only allows up to 240 debug bits")
    assert(info.clockDomains.size == 1, "NxScope does not support multiple clock domains")

    val offsetMap: mutable.Map[DebugSignal, Int] = mutable.Map()

    val triggers: mutable.ListBuffer[BaseType] = mutable.ListBuffer()

    pc.walkDeclarations {
      case data: BaseType if data.hasTag(nxDebugTrigger) =>
        triggers += data
      case _ =>
    }

    assert(triggers.nonEmpty, "NxScope requires at least one trigger; use addTag(nxDebugTrigger) on some signal(s)")
    assert(triggers.forall(_.clockDomain == info.clockDomains.head), "NxScope: all triggers must use the clock domain")

    pc.topLevel.rework {
      var data_lines = Bits(0 bits)

      for (signal <- info.getAllDebugSignals) {
        offsetMap += signal -> data_lines.getBitsWidth
        data_lines = signal.data.pull() ## data_lines
      }

      val scope = NxScope.generate(triggerWidth = triggers.size bit, dataWidth = info.debugWidth bit)
      scope.io.CLK := info.clockDomains.head.clock
      scope.io.TRIG_LINES := triggers.map(_.pull().asBits).reduce(_ ## _)
      scope.io.DATA_LINES := data_lines
    }

    val gtkw = new GtkWaveSaveFile(Path.of("nxscope.gtkw"))

    def probe(i: Int): String = s"NxScope.line$i"
    def probes(high: Int, low: Int): Seq[String] =
      (high to low by -1).map(probe)

    def addSignal(fullName: Boolean)(signal: DebugSignal): Unit = {
      val offset = offsetMap(signal)
      val width = signal.data.getBitsWidth
      val name = if (fullName) signal.data.getRtlPath() else signal.data.getName()
      if (width == 1) {
        gtkw.signal(probe(offset), name)
      } else {
        gtkw.vector(probes(offset + width - 1, offset), name)
      }
    }

    def handleGroup(group: DebugGroup): Unit = {
      gtkw.group(group.name) {
        group.signals.foreach(addSignal(fullName = false))
        group.groups.values.foreach(handleGroup)
      }
    }

    info.standalone.foreach(addSignal(fullName = true))

    info.groups.values.foreach(handleGroup)

    gtkw.close()
  }
}