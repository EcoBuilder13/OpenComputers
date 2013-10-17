package li.cil.oc.common.tileentity

import li.cil.oc.Config
import li.cil.oc.api.network.Message
import li.cil.oc.api.power.Receiver
import li.cil.oc.client.gui
import li.cil.oc.client.{PacketSender => ClientPacketSender}
import li.cil.oc.common.component
import li.cil.oc.server.{PacketSender => ServerPacketSender}
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.common.ForgeDirection
import scala.collection.mutable
import net.minecraft.util.AxisAlignedBB

class Screen extends Rotatable with component.Screen.Environment with Receiver {
  var guiScreen: Option[gui.Screen] = None

  /**
   * Read and reset to false from the tile entity renderer. This is used to
   * keep rendering a little more efficient by compiling the displayed text
   * into an OpenGL display list, and only re-compiling that list when the
   * text/display has actually changed.
   */
  var hasChanged = false

  /**
   * Check for multi-block screen option in next update. We do this in the
   * update to avoid unnecessary checks on chunk unload.
   */
  private var shouldCheckForMultiBlock = true

  var width, height = 1

  var origin = this

  val screens = mutable.Set(this)

  // ----------------------------------------------------------------------- //

  def isOrigin = origin == this

  def localPosition = {
    val (x, y, _) = project(this)
    val (ox, oy, _) = project(origin)
    ((ox - x).abs, (oy - y).abs)
  }

  // ----------------------------------------------------------------------- //

  override def receive(message: Message) =
    if (isOrigin) {
      // Necessary to avoid infinite recursion.
      super.receive(message)
    } else {
      origin.receive(message)
    }

  // ----------------------------------------------------------------------- //

  override def readFromNBT(nbt: NBTTagCompound) = {
    super.readFromNBT(nbt)
    load(nbt.getCompoundTag("node"))
  }

  override def writeToNBT(nbt: NBTTagCompound) = {
    super.writeToNBT(nbt)

    val nodeNbt = new NBTTagCompound
    save(nodeNbt)
    nbt.setCompoundTag("node", nodeNbt)
  }

  override def validate() = {
    super.validate()
    if (worldObj.isRemote)
      ClientPacketSender.sendScreenBufferRequest(this)
  }

  override def invalidate() {
    super.invalidate()
    screens.clone().foreach(_.checkMultiBlock())
  }

  override def onRotationChanged() = screens.clone().foreach(_.checkMultiBlock())

  // ----------------------------------------------------------------------- //

  override def updateEntity() =
    if (shouldCheckForMultiBlock) {
      if (worldObj.isRemote) println("begin merge %d,%d,%d".format(xCoord, yCoord, zCoord))
      while (tryMerge()) {}
      screens.foreach(_.shouldCheckForMultiBlock = false)
      val (tx, ty, tz) = unproject(width, height, 1)
      worldObj.markBlockRangeForRenderUpdate(xCoord, yCoord, zCoord, xCoord + tx, yCoord + ty, zCoord + tz)
    }

  def checkMultiBlock() {
    shouldCheckForMultiBlock = true
    width = 1
    height = 1
    origin = this
    screens.clear()
    screens += this
  }

  private def tryMerge() = {
    val (x, y, z) = project(origin)
    def tryMergeTowards(dx: Int, dy: Int) = {
      val (nx, ny, nz) = unproject(x + dx, y + dy, z)
      //println("neighbor: %d,%d,%d".format(nx, ny, nz))
      worldObj.getBlockTileEntity(nx, ny, nz) match {
        case s: Screen if s.pitch == pitch && s.yaw == yaw && !screens.contains(s) =>
          val (sx, sy, _) = project(s.origin)
          //println("projected: %d,%d".format(sx, sy))
          val canMergeAlongX = sy == y && s.height == height && s.width + width <= Config.maxScreenWidth
          val canMergeAlongY = sx == x && s.width == width && s.height + height <= Config.maxScreenHeight
          if (canMergeAlongX || canMergeAlongY) {
            if (worldObj.isRemote) println("merging with %d,%d,%d".format(nx, ny, nz))
            val (newOrigin) =
              if (canMergeAlongX) {
                if (sx < x) s.origin else origin
              }
              else {
                if (sy < y) s.origin else origin
              }
            val (newWidth, newHeight) =
              if (canMergeAlongX) (width + s.width, height)
              else (width, height + s.height)
            val newScreens = screens ++ s.screens
            for (screen <- newScreens) {
              screen.width = newWidth
              screen.height = newHeight
              screen.origin = newOrigin
              screen.screens ++= newScreens // It's a set, so there won't be duplicates.
            }
            true
          }
          else false // Cannot merge.
        case _ => false
      }
    }
    tryMergeTowards(width, 0) || tryMergeTowards(0, height) || tryMergeTowards(-1, 0) || tryMergeTowards(0, -1)
  }

  private def project(t: Screen) = {
    def dot(f: ForgeDirection, s: Screen) = f.offsetX * s.xCoord + f.offsetY * s.yCoord + f.offsetZ * s.zCoord
    (dot(toGlobal(ForgeDirection.EAST), t), dot(toGlobal(ForgeDirection.UP), t), dot(toGlobal(ForgeDirection.SOUTH), t))
  }

  private def unproject(x: Int, y: Int, z: Int) = {
    def dot(f: ForgeDirection) = f.offsetX * x + f.offsetY * y + f.offsetZ * z
    (dot(toLocal(ForgeDirection.EAST)), dot(toLocal(ForgeDirection.UP)), dot(toLocal(ForgeDirection.SOUTH)))
  }

  // ----------------------------------------------------------------------- //

  override def getRenderBoundingBox =
    if ((width == 1 && height == 1) || !isOrigin) super.getRenderBoundingBox
    else {
      val (sx, sy, sz) = unproject(width, height, 1)
      val ox = xCoord + (if (sx < 0) 1 else 0)
      val oy = yCoord + (if (sy < 0) 1 else 0)
      val oz = zCoord + (if (sz < 0) 1 else 0)
      val b = AxisAlignedBB.getAABBPool.getAABB(ox, oy, oz, ox + sx, oy + sy, oz + sz)
      b.setBounds(b.minX min b.maxX, b.minY min b.maxY, b.minZ min b.maxZ,
                  b.minX max b.maxX, b.minY max b.maxY, b.minZ max b.maxZ)
      b
    }

  // ----------------------------------------------------------------------- //

  override def onScreenResolutionChange(w: Int, h: Int) = {
    super.onScreenResolutionChange(w, h)
    if (worldObj.isRemote) {
      guiScreen.foreach(_.changeSize(w, h))
      hasChanged = true
    }
    else {
      worldObj.markTileEntityChunkModified(xCoord, yCoord, zCoord, this)
      ServerPacketSender.sendScreenResolutionChange(this, w, h)
    }
  }

  override def onScreenSet(col: Int, row: Int, s: String) = {
    super.onScreenSet(col, row, s)
    if (worldObj.isRemote) {
      guiScreen.foreach(_.updateText())
      hasChanged = true
    }
    else {
      worldObj.markTileEntityChunkModified(xCoord, yCoord, zCoord, this)
      ServerPacketSender.sendScreenSet(this, col, row, s)
    }
  }

  override def onScreenFill(col: Int, row: Int, w: Int, h: Int, c: Char) = {
    super.onScreenFill(col, row, w, h, c)
    if (worldObj.isRemote) {
      guiScreen.foreach(_.updateText())
      hasChanged = true
    }
    else {
      worldObj.markTileEntityChunkModified(xCoord, yCoord, zCoord, this)
      ServerPacketSender.sendScreenFill(this, col, row, w, h, c)
    }
  }

  override def onScreenCopy(col: Int, row: Int, w: Int, h: Int, tx: Int, ty: Int) = {
    super.onScreenCopy(col, row, w, h, tx, ty)
    if (worldObj.isRemote) {
      guiScreen.foreach(_.updateText())
      hasChanged = true
    }
    else {
      worldObj.markTileEntityChunkModified(xCoord, yCoord, zCoord, this)
      ServerPacketSender.sendScreenCopy(this, col, row, w, h, tx, ty)
    }
  }
}