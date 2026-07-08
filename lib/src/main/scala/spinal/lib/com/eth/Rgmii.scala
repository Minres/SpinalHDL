package spinal.lib.com.eth

import spinal.core._
import spinal.lib._
import spinal.lib.io.{DDRInput, DDROutput}

case class Rgmii() extends Bundle with IMasterSlave {
  val tx = RgmiiTx()
  val rx = RgmiiRx()

  override def asMaster(): Unit = {
    master(tx)
    slave(rx)
  }
}

case class RgmiiTx() extends Bundle with IMasterSlave {
  val clk = Bool()
  val ctl = Bool()
  val d = Bits(4 bits)

  override def asMaster(): Unit = {
    in(clk)
    out(ctl, d)
  }

  def fromTxStream() = new Area {
    val interframe = MacTxInterFrame(8)

    val o = interframe.io.output
    val dataDDR = DDROutput(4)
    val ctlDDR = DDROutput(1)

    dataDDR.io.d1 := o.data(3 downto 0)
    dataDDR.io.d2 := o.data(7 downto 4)

    ctlDDR.io.d1(0) := o.valid
    ctlDDR.io.d2(0) := o.valid ^ False

    d := dataDDR.io.q
    ctl := ctlDDR.io.q(0)
    val input = interframe.io.input
  }
}

case class RgmiiRx() extends Bundle with IMasterSlave {
  val clk = Bool()
  val d = Bits(4 bits)
  val ctl = Bool()

  override def asMaster(): Unit = {
    out(clk, d, ctl)
  }

  def toRxFlow() = {
    val dataDDR = DDRInput(4)
    val ctlDDR = DDRInput(1)

    dataDDR.io.d := d
    ctlDDR.io.d(0) := ctl

    val EN = ctlDDR.io.q1(0)
    val ER = ctlDDR.io.q1(0) ^ ctlDDR.io.q2(0)

    val unbuffered = Flow(PhyRx(8))
    unbuffered.valid := EN
    unbuffered.data := dataDDR.io.q2 ## dataDDR.io.q1
    unbuffered.error := ER

    val buffered = unbuffered.stage()
    val ret = Flow(Fragment(PhyRx(8)))
    ret.valid := buffered.valid
    ret.fragment := buffered.payload
    ret.last := !unbuffered.valid
    ret
  }
}