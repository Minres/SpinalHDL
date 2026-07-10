package spinal.lib.io

import spinal.core._
import spinal.lib.blackbox.xilinx.us._

trait DDROutput {
  def width: Int

  val io = new Bundle {
    val d1 = in Bits (width bits)
    val d2 = in Bits (width bits)
    val q = out Bits (width bits)
  }
}

object DDROutput {
  def apply(width: Int, usePrimitiveIfAvailable: Boolean = true): DDROutput = Device.matchFamily[DDROutput](usePrimitiveIfAvailable,
    XILINX_US -> (() => XilinxDDROut(width)),
    XILINX_USPLUS -> (() => XilinxDDROut(width))
  )(
    fallback = () => GenericDDROut(width)
  )
}

trait DDRInput {
  def width: Int

  val io = new Bundle {
    val d = in Bits (width bits)
    val q1 = out Bits (width bits)
    val q2 = out Bits (width bits)
  }
}

object DDRInput {
  def apply(width: Int, usePrimitiveIfAvailable: Boolean = true): DDRInput = Device.matchFamily[DDRInput](usePrimitiveIfAvailable,
    XILINX_US -> (() => XilinxDDRIn(width)),
    XILINX_USPLUS -> (() => XilinxDDRIn(width))
  )(
    fallback = () => GenericDDRIn(width)
  )
}

case class GenericDDROut(width: Int) extends Component with DDROutput {

  val risingArea = new Area {
    val d = Reg(Bits(width bits)) init (0)
    d := io.d1
  }

  val fallingArea = new Area {
    val d = Reg(Bits(width bits)) init(0)
    d := io.d2
  }

  io.q := Mux(ClockDomain.readClockWire, risingArea.d, fallingArea.d)
}

case class GenericDDRIn(width: Int) extends Component with DDRInput {

  val risingArea = new Area {
    val sample = Reg(Bits(width bits)) init(0)
    sample := io.d
  }

  val fallingArea = new ClockingArea(ClockDomain.current.withRevertedClockEdge()) {
    val sample = Reg(Bits(width bits)) init(0)
    sample := io.d
  }

  val q1Reg = Reg(Bits(width bits)) init(0)
  val q2Reg = Reg(Bits(width bits)) init(0)

  q1Reg := risingArea.sample
  q2Reg := fallingArea.sample

  io.q1 := q1Reg
  io.q2 := q2Reg
}

case class XilinxDDROut(width: Int) extends Component with DDROutput {

  for (i <- 0 until width) {
    val ddr = ODDRE1()
    ddr.io.D1 := io.d1(i)
    ddr.io.D2 := io.d2(i)
    io.q(i) := ddr.io.Q
  }
}

case class XilinxDDRIn(width: Int, clkEdge: IDDRE1_CLK_EDGE.IDDRE1_CLK_EDGE = IDDRE1_CLK_EDGE.SAME_EDGE_PIPELINED) extends Component with DDRInput {

  for (i <- 0 until width) {
    val ddr = IDDRE1(clkEdge)
    ddr.io.D := io.d(i)
    io.q1(i) := ddr.io.Q1
    io.q2(i) := ddr.io.Q2
  }
}