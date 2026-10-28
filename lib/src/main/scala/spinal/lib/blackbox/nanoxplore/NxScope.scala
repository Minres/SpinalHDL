package spinal.lib.blackbox.nanoxplore

import spinal.core._

import scala.language.postfixOps

case class NxScope(
  blackBoxName: String = "NxChipscopeV2_wrapper",
  triggerWidth: BitCount,
  dataWidth: BitCount
) extends BlackBox {
  require(Device.get.matches(Device.NX))
  require(dataWidth.value <= 240)
  require(triggerWidth.value <= 32)

  val io = new Bundle {
    val CLK = in Bool()
    val ENA = in Bool()

    val TRIG_LINES = in Bits(triggerWidth)
    val DATA_LINES = in Bits(dataWidth)

    val TRIG_IMMEDIATE_EXT = in Bool()
    val SIMU_TRIG_ARMED_PLS = in Bool()

    val TRIG_ARMED = out Bool()
    val FIRST_LEVEL_TRIG_OK = out Bool()
    val CURRENT_CAPTURE_SET = out Bits(4 bit)
    val DONE = out Bool()
  }

  noIoPrefix()

  setBlackBoxName(blackBoxName)
}

object NxScope {
  def generate(blackBoxName: String = "NxChipscopeV2_wrapper",
               triggerWidth: BitCount,
               dataWidth: BitCount): NxScope = {
    SpinalWarning(s"Please generate a NxScope with name \"$blackBoxName\", ${triggerWidth.value} Trigger Lines and ${dataWidth.value} Input Lines")
    val comp = NxScope(blackBoxName, triggerWidth, dataWidth)
    comp.io.ENA.default(True)
    comp.io.TRIG_IMMEDIATE_EXT.default(False)
    comp.io.SIMU_TRIG_ARMED_PLS.default(True)
    comp
  }
}