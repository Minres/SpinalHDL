package spinal.lib.bus.tilelink.coherent

import spinal.core._
import spinal.core.fiber._
import spinal.lib.bus.misc.{AddressMapping, SizeMapping}
import spinal.lib.bus.tilelink._
import spinal.lib.bus.tilelink.fabric._
import spinal.lib.system.tag._


//TODO remove probe on IO regions
class DirectoryFiber() extends Area{
  val up = Node.slave()
  val down = Node.master()

  var parameter = DirectoryParam(
    unp = null, //Unknown yet
    downPendingMax = 4,
    cacheWays = 4,
    cacheBytes = 4096,
    blockSize = -1, //Unknown yet
    probeCount = 4,
    aBufferCount = 4,
    probeRegion = null
  )


  val mappingLock = Lock().retain()
  new MemoryConnection{
    override def m = up
    override def s = down
    override def transformers = Nil
    override def mapping = {
      mappingLock.get //Ensure that the parameter is final
      SizeMapping(0, BigInt(1) << parameter.addressWidth)
    }
    override def sToM(down: MemoryTransfers, args: MappedNode) = {
      down match{
        case t : M2sTransfers => {
          val canGet = t.get.contains(parameter.blockSize)
          val canPut = t.putFull.contains(parameter.blockSize)
          val isMain = args.node.hasTag(PMA.MAIN)
          t.copy(
            acquireT = if(isMain && canGet && canPut) SizeRange(parameter.blockSize) else SizeRange.none,
            acquireB = if(isMain && canGet) SizeRange(parameter.blockSize) else SizeRange.none
          )
        }
      }
    }
    populate()
  }

  val logic = Fiber build new Area{
    parameter.blockSize = up.m2s.proposed.transfers.acquireB.getSingleSize().get
    up.m2s.proposed.transfers.acquireT.getSingleSize().foreach(size => assert(size == parameter.blockSize))

    down.m2s.proposed.load(
      up.m2s.proposed.copy(
        transfers = M2sTransfers(
          get = SizeRange.upTo(parameter.blockSize),
          putFull = SizeRange.upTo(parameter.blockSize),
          putPartial = SizeRange.upTo(parameter.blockSize)
        )
      )
    )

    up.m2s.supported.load(
      down.m2s.supported.copy(
        transfers = M2sTransfers(
          acquireT = SizeRange(parameter.blockSize),
          acquireB = SizeRange(parameter.blockSize),
          get = SizeRange.upTo(parameter.blockSize),
          putFull = SizeRange.upTo(parameter.blockSize),
          putPartial = SizeRange.upTo(parameter.blockSize)
        )
      )
    )

    down.m2s.parameters.load(
      Directory.downM2s(
        name           = down,
        addressWidth   = up.m2s.parameters.addressWidth,
        dataWidth      = up.m2s.parameters.dataWidth,
        blockSize      = parameter.blockSize,
        generalSlotCount = parameter.generalSlotCount
      )
    )
    down.s2m.supported.load(S2mSupport.none)

    up.s2m.parameters.load(
      Directory.upS2m(
        name = up,
        blockSize = parameter.blockSize,
        generalSlotCount = parameter.generalSlotCount
      )
    )
    up.s2m.setProposedFromParameters()

    parameter.unp = up.bus.p.node
    mappingLock.release()

    val transferSpec = MemoryConnection.getMemoryTransfers(up)
    val probeSpec = transferSpec.filter(_.transfers.asInstanceOf[M2sTransfers].withBCE)
    val ioSpec = transferSpec.filter(!_.transfers.asInstanceOf[M2sTransfers].withBCE)

    parameter.probeRegion = { addr =>
      AddressMapping.decode(addr.asBits, probeSpec.map(_.mapping), ioSpec.map(_.mapping))
//      equivalent to probeSpec.map(_.where.mapping.hit(addr)).orR
    }
    val directory = new Directory(parameter)
    directory.io.up << up.bus
    directory.io.down >> down.bus
  }
}
