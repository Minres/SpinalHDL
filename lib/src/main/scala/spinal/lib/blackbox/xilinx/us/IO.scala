package spinal.lib.blackbox.xilinx.us

import spinal.core._

object IDDRE1_CLK_EDGE extends Enumeration {
  type IDDRE1_CLK_EDGE = Value
  val OPPOSITE_EDGE, SAME_EDGE, SAME_EDGE_PIPELINED = Value
}

case class IDDRE1(
  cDomain: ClockDomain,
  cbDomain: ClockDomain,
  DDR_CLK_EDGE: IDDRE1_CLK_EDGE.IDDRE1_CLK_EDGE,
  IS_C_INVERTED: Boolean,
  IS_CB_INVERTED: Boolean
) extends BlackBox {
  addGeneric("IS_C_INVERTED", if (IS_C_INVERTED) "1'b1" else "1'b0")
  addGeneric("IS_CB_INVERTED", if (IS_CB_INVERTED) "1'b1" else "1'b0")
  addGeneric("DDR_CLK_EDGE", DDR_CLK_EDGE.toString)

  val io = new Bundle {
    val C = in Bool()
    val CB = in Bool()
    val R = in Bool()
    val D = in Bool()
    val Q1 = out Bool()
    val Q2 = out Bool()
  }

  noIoPrefix()

  mapClockDomain(cDomain, clock = io.C, reset = io.R)
  mapClockDomain(cbDomain, clock = io.CB)
}

object IDDRE1 {
  def apply(DDR_CLK_EDGE: IDDRE1_CLK_EDGE.IDDRE1_CLK_EDGE): IDDRE1 = IDDRE1(
    ClockDomain.current,
    ClockDomain.current,
    DDR_CLK_EDGE,
    IS_C_INVERTED = false,
    IS_CB_INVERTED = true
  )
}

case class ODDRE1(
  IS_C_INVERTED: Boolean = false,
  SRVAL: Boolean = false,
  SIM_DEVICE: String = "ULTRASCALE"
) extends BlackBox {
  addGeneric("IS_C_INVERTED", if (IS_C_INVERTED) "1'b1" else "1'b0")
  addGeneric("SRVAL", if (SRVAL) "1'b1" else "1'b0")
  addGeneric("SIM_DEVICE", SIM_DEVICE)

  val io = new Bundle {
    val C = in Bool()
    val SR = in Bool()
    val D1 = in Bool()
    val D2 = in Bool()
    val Q = out Bool()
  }

  noIoPrefix()

  mapClockDomain(clock = io.C, reset = io.SR)
}