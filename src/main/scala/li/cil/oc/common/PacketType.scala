package li.cil.oc.common

object PacketType extends Enumeration {
  val
  // Server -> Client
  Analyze,
  ChargerState,
  ColorChange,
  ComputerState,
  ComputerUserList,
  DisassemblerActiveChange,
  FileSystemActivity,
  FloppyChange,
  HologramClear,
  HologramColor,
  HologramPowerChange,
  HologramScale,
  HologramSet,
  HologramTranslation,
  PetVisibility, // Goes both ways.
  PowerState,
  RaidStateChange,
  RedstoneState,
  RobotAnimateSwing,
  RobotAnimateTurn,
  RobotAssemblingState,
  RobotInventoryChange,
  RobotLightChange,
  RobotMove,
  RobotSelectedSlotChange,
  RotatableState,
  SwitchActivity,
  TextBufferMultiColorChange,
  TextBufferMultiCopy,
  TextBufferMultiDepthChange,
  TextBufferMultiFill,
  TextBufferInit, // Goes both ways.
  TextBufferMulti,
  TextBufferMultiPaletteChange,
  TextBufferPowerChange,
  TextBufferMultiResolutionChange,
  TextBufferMultiMaxResolutionChange,
  TextBufferMultiSet,
  ScreenTouchMode,
  ServerPresence,
  Sound,
  SoundPattern,

  // Client -> Server
  ComputerPower,
  DronePower,
  KeyDown,
  KeyUp,
  Clipboard,
  MouseClickOrDrag,
  MouseScroll,
  MouseUp,
  MultiPartPlace,
  RobotAssemblerStart,
  RobotStateRequest,
  ServerSide,
  ServerSwitchMode,

  EndOfList = Value
}