package spinal.lib.debug

import spinal.core._
import spinal.core.internals.{Phase, PhaseContext}
import spinal.lib._

import scala.collection.mutable

class DebugExtractionPhase extends Phase {
  override def impl(pc: PhaseContext): Unit = {

    val groups = mutable.Map[Bundle, DebugGroup]()
    val standalone = DebugGroup("")
    val clockDomains = mutable.Set[ClockDomain]()
    var debugWidth = 0

    def findParentIMasterSlaves(data: Data): Seq[IMasterSlave with Bundle] = {
      data match {
        case group: (IMasterSlave with Bundle) => findParentIMasterSlaves(group.parent) :+ group
        case null => Seq()
        case other => findParentIMasterSlaves(other.parent)
      }
    }

    pc.walkDeclarations {
      case data: BaseType if data.hasTag(logicDebug) && !data.hasTag(debugExclude) =>
        val parents = findParentIMasterSlaves(data.parent)
        clockDomains += data.clockDomain
        var elementMap = groups
        var group = standalone
        for ((parent, idx) <- parents.zipWithIndex) {
          val name = if (idx == 0) parent.getRtlPath() else parent.getName()
          group = elementMap.getOrElseUpdate(parent, DebugGroup(name))
          elementMap = group.groups
        }
        group.signals += DebugSignal(data)
        debugWidth += data.getBitsWidth
      case _ =>
    }

    val impl = Device.matchDevice(
      Device.NX -> (() => new NxScopeDebugImpl)
    )
    impl.implementDebug(DebugInformation(debugWidth, clockDomains.toSeq, standalone.signals.toSeq, groups.toMap), pc)
  }

  override def hasNetlistImpact: Boolean = true
}
