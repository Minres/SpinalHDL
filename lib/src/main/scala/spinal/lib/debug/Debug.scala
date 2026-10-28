package spinal.lib.debug

import spinal.core.{BaseType, Bundle, ClockDomain, SpinalTag}

import scala.collection.mutable

object logicDebug extends SpinalTag
object debugExclude extends SpinalTag

sealed trait DebugElement {
  def getAllDebugSignals: Iterable[DebugSignal]
}

case class DebugSignal(data: BaseType) extends DebugElement {
  override def getAllDebugSignals: Iterable[DebugSignal] = Seq(this)
}

case class DebugGroup(name: String, groups: mutable.Map[Bundle, DebugGroup] = mutable.Map(),
                      signals: mutable.ListBuffer[DebugSignal] = mutable.ListBuffer()) extends DebugElement {
  override def getAllDebugSignals: Iterable[DebugSignal] = signals ++ groups.values.flatMap(_.getAllDebugSignals)
}

case class DebugInformation(debugWidth: Int, clockDomains: Seq[ClockDomain], standalone: Seq[DebugSignal], groups: Map[Bundle, DebugGroup]) {
  def getAllDebugSignals: Iterable[DebugSignal] = {
    standalone ++ groups.values.flatMap(_.getAllDebugSignals)
  }
}