package spinal.lib.blackbox.xilinx

import spinal.core.Device

package object us {
  val XILINX_US = Device.XILINX.withFamily("ultrascale")
  val XILINX_USPLUS = Device.XILINX.withFamily("ultrascale+")
}
