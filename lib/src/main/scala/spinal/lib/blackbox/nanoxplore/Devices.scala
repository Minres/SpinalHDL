package spinal.lib.blackbox

import spinal.core.Device

object Devices {
  val NX_NGMEDIUM = Device.XILINX.withFamily("ng_medium")
  val NX_NGLARGE = Device.XILINX.withFamily("ng_large")
  val NX_NGULTRA = Device.XILINX.withFamily("ng_ultra")
  val NX_ULTRA300 = Device.XILINX.withFamily("ultra300")
}
