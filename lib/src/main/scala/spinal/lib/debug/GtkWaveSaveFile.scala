package spinal.lib.debug

import java.io.PrintWriter
import java.nio.file.{Files, Path}

class GtkWaveSaveFile(path: Path) extends AutoCloseable {
  private val out = new PrintWriter(Files.newBufferedWriter(path))

  // GTKWave trace flags
  private val HEX       = 1L << 1
  private val DEC       = 1L << 2
  private val BIN       = 1L << 3
  private val BLANK     = 1L << 9
  private val COLLAPSED = 1L << 12
  private val CLOSED    = 1L << 22
  private val GRP_BEGIN = 1L << 23
  private val GRP_END   = 1L << 24

  private def flags(value: Long): Unit =
    out.println(s"@${java.lang.Long.toHexString(value)}")

  // Individual VCD signal, optionally renamed
  def signal(path: String, alias: String = ""): Unit = {
    flags(HEX)
    if (alias.nonEmpty)
      out.println(s"+{$alias} $path")
    else
      out.println(path)
  }

  // Combine scalar signals into a vector.
  // Bits must be supplied MSB first.
  def vector(
    bits: Seq[String],
    name: String,
    format: String = "hex"
  ): Unit = {
    require(bits.nonEmpty)

    val fmt = format match {
      case "hex" => HEX
      case "dec" => DEC
      case "bin" => BIN
      case other => throw new IllegalArgumentException(other)
    }

    flags(fmt | GRP_BEGIN)
    out.println(s"#{$name} ${bits.mkString(" ")}")

    flags(BLANK | GRP_END | COLLAPSED)
    out.println("-group_end")
  }

  // Groups can be nested
  def group(name: String, closed: Boolean = true)
           (body: => Unit): Unit = {
    flags(BLANK | GRP_BEGIN | (if (closed) CLOSED else 0L))
    out.println(s"-$name")

    body

    flags(BLANK | GRP_END |
      (if (closed) CLOSED | COLLAPSED else 0L))
    out.println(s"-$name")
  }

  override def close(): Unit = out.close()
}
