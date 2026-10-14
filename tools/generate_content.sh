#!/usr/bin/env bash
# One-off content generator for TinyWyrlds -- builds real rooms/connectors in the live WyrldPrint
# crafting zone via RCON (fill/setblock), then captures each one through the mod's own real
# WyrldPrintCapture/WyrldPrintStorage pipeline (not hand-authored JSON) -- same precedent
# Blueprynts used for its "Ancestors"/"Guardhouse" reference sets. Throwaway/dev-only.
set -uo pipefail

RCONJAR=/c/Users/benja/Minecraft/tools
HOST=127.0.0.1
PORT=25580
PASS=devgenpass

RC() { java -cp "$RCONJAR" RconClient "$HOST" "$PORT" "$PASS" "execute in tinywyrlds:wyrld_space run $1" > /dev/null; }
RCP() { java -cp "$RCONJAR" RconClient "$HOST" "$PORT" "$PASS" "$1"; }

X0=-60
Z0=0
Y0=512 # floor-level walkable Y (BASELINE_Y)

# box_room W D H floorBlock wallBlock ceilBlock
box_room() {
  local W=$1 D=$2 H=$3 floor=$4 wall=$5 ceil=$6
  local x0=$X0 x1=$((X0+W+1))
  local halfD=$((D/2))
  local z0=$((Z0-halfD-1)) z1=$((Z0+halfD+1))
  local yF=$((Y0-1)) yC=$((Y0+H))
  RC "fill $x0 $yF $z0 $x1 $yF $z1 $floor"
  RC "fill $x0 $yC $z0 $x1 $yC $z1 $ceil"
  RC "fill $x0 $Y0 $z0 $x0 $((yC-1)) $z1 $wall"
  RC "fill $x1 $Y0 $z0 $x1 $((yC-1)) $z1 $wall"
  RC "fill $x0 $Y0 $z0 $x1 $((yC-1)) $z0 $wall"
  RC "fill $x0 $Y0 $z1 $x1 $((yC-1)) $z1 $wall"
  echo "$x0 $x1 $z0 $z1 $yF $yC"
}

wall_conn() {
  local x=$1 y=$2 z=$3 facing=$4 size=$5
  RC "setblock $x $y $z tinywyrlds:connector_point_wall_$size[facing=$facing,half=lower,hinge=left,open=false]"
  RC "setblock $x $((y+1)) $z tinywyrlds:connector_point_wall_$size[facing=$facing,half=upper,hinge=left,open=false]"
}

floor_conn() {
  local x=$1 y=$2 z=$3 size=$4
  RC "setblock $x $y $z tinywyrlds:connector_point_floor_$size[facing=north,half=bottom,open=false]"
}

save_piece() {
  local name="$1" type="$2"
  RCP "tinywyrlds wyrldprint save \"$name\" $type"
  RC "fill $((X0-3)) $((Y0-3)) $((Z0-20)) $((X0+20)) $((Y0+15)) $((Z0+20)) air"
}

echo "=== Force-loading crafting zone chunks ==="
RCP "execute in tinywyrlds:wyrld_space run forceload add -80 -60 80 60"
sleep 3 # give freshly-loaded chunks a moment to fully settle before building on them

# ---------------------------------------------------------------------------
# ROOMS -- 5 per theme x 5 themes = 25
# ---------------------------------------------------------------------------

room_tent() {
  local idx=$1 W=$2 D=$3 H=$4
  read x0 x1 z0 z1 yF yC < <(box_room "$W" "$D" "$H" minecraft:hay_block minecraft:white_wool minecraft:orange_wool)
  local midZ=$Z0 doorY=$Y0
  wall_conn "$x1" "$doorY" "$midZ" east small
  wall_conn "$x0" "$doorY" "$midZ" west small
  # campfire + bedroll + a crate, position varies with idx for a little variety
  RC "setblock $((X0+2)) $Y0 $((Z0-1)) minecraft:campfire[facing=south,lit=true]"
  RC "fill $((X0+2)) $Y0 $((Z0+1)) $((X0+3)) $Y0 $((Z0+1)) minecraft:red_wool"
  RC "setblock $((X0+1)) $Y0 $((Z0+1)) minecraft:white_wool"
  RC "setblock $((X0+4+idx%2)) $Y0 $((Z0+2)) minecraft:barrel[facing=up]"
  RC "setblock $((X0+1)) $Y0 $((Z0-2)) minecraft:torch"
  save_piece "Tent Interior $idx" room
}

room_wizard_tower() {
  local idx=$1 W=$2 D=$3 H=$4
  read x0 x1 z0 z1 yF yC < <(box_room "$W" "$D" "$H" minecraft:purpur_block minecraft:polished_blackstone_bricks minecraft:amethyst_block)
  wall_conn "$x1" "$Y0" "$Z0" east small
  wall_conn "$x0" "$Y0" "$Z0" west small
  RC "fill $((X0+1)) $Y0 $((z0+1)) $((X0+1)) $((Y0+1)) $((z0+1)) minecraft:bookshelf"
  RC "fill $((X0+1)) $Y0 $((z1-1)) $((X0+1)) $((Y0+1)) $((z1-1)) minecraft:bookshelf"
  RC "setblock $((X0+3)) $Y0 $((Z0-1)) minecraft:brewing_stand[has_bottle_0=false,has_bottle_1=false,has_bottle_2=false]"
  RC "setblock $((X0+3)) $Y0 $((Z0+1)) minecraft:enchanting_table"
  RC "setblock $((X0+4+idx%2)) $Y0 $((Z0+1)) minecraft:lectern[facing=south,has_book=true]"
  RC "setblock $((X0+2)) $((Y0+1)) $((Z0-2)) minecraft:purple_candle[candles=1,lit=true]"
  RC "setblock $((X0+2)) $((Y0+1)) $((Z0+2)) minecraft:purple_candle[candles=1,lit=true]"
  save_piece "Wizard Tower Interior $idx" room
}

room_cave() {
  local idx=$1 W=$2 D=$3 H=$4
  read x0 x1 z0 z1 yF yC < <(box_room "$W" "$D" "$H" minecraft:stone minecraft:cobblestone minecraft:mossy_cobblestone)
  wall_conn "$x1" "$Y0" "$Z0" east small
  wall_conn "$x0" "$Y0" "$Z0" west small
  RC "setblock $((X0+2)) $((yC-1)) $((Z0-1)) minecraft:pointed_dripstone[thickness=tip,vertical_direction=down,waterlogged=false]"
  RC "setblock $((X0+3)) $((yC-1)) $((Z0+1)) minecraft:pointed_dripstone[thickness=tip,vertical_direction=down,waterlogged=false]"
  RC "fill $((x0+1)) $Y0 $z0 $((x0+1)) $((Y0+1)) $z1 minecraft:glow_lichen[down=false,east=false,north=false,south=false,up=false,waterlogged=false,west=true]"
  RC "fill $((X0+1+idx%2)) $Y0 $((Z0-1)) $((X0+2+idx%2)) $Y0 $Z0 minecraft:water"
  RC "setblock $((X0+4)) $Y0 $((Z0+2)) minecraft:moss_carpet"
  save_piece "Cave Interior $idx" room
}

room_mine() {
  local idx=$1 W=$2 D=$3 H=$4
  read x0 x1 z0 z1 yF yC < <(box_room "$W" "$D" "$H" minecraft:cobblestone minecraft:deepslate_bricks minecraft:deepslate)
  wall_conn "$x1" "$Y0" "$Z0" east small
  wall_conn "$x0" "$Y0" "$Z0" west small
  RC "fill $x0 $Y0 $((Z0-1)) $x1 $Y0 $((Z0-1)) minecraft:rail"
  RC "setblock $((X0+2)) $Y0 $((Z0-1)) minecraft:minecart"
  RC "setblock $((X0+1)) $Y0 $((Z0+2)) minecraft:oak_fence"
  RC "setblock $((X0+1)) $((Y0+1)) $((Z0+2)) minecraft:oak_fence"
  RC "setblock $((X0+1)) $((Y0+2)) $((Z0+2)) minecraft:lantern[hanging=true,waterlogged=false]"
  RC "setblock $((X0+3+idx%2)) $Y0 $((Z0+1)) minecraft:iron_ore"
  RC "setblock $((X0+4)) $Y0 $Z0 minecraft:cobweb"
  save_piece "Mine Interior $idx" room
}

room_basement() {
  local idx=$1 W=$2 D=$3 H=$4
  read x0 x1 z0 z1 yF yC < <(box_room "$W" "$D" "$H" minecraft:oak_planks minecraft:cracked_stone_bricks minecraft:stone_bricks)
  wall_conn "$x1" "$Y0" "$Z0" east small
  wall_conn "$x0" "$Y0" "$Z0" west small
  RC "setblock $((X0+2)) $Y0 $((Z0-1)) minecraft:barrel[facing=up]"
  RC "setblock $((X0+2)) $Y0 $((Z0+1)) minecraft:furnace[facing=south,lit=false]"
  RC "setblock $((X0+1)) $Y0 $Z0 minecraft:cobweb"
  RC "setblock $((X0+4+idx%2)) $Y0 $((Z0+2)) minecraft:coal_block"
  RC "setblock $((X0+1)) $((Y0+1)) $((Z0-2)) minecraft:ladder[facing=south,waterlogged=false]"
  save_piece "Basement Interior $idx" room
}

echo "=== Generating 25 rooms ==="
SIZES=("5 5 4" "6 6 4" "7 5 4" "7 7 5" "9 6 5")
for i in 1 2 3 4 5; do
  read w d h <<< "${SIZES[$((i-1))]}"
  room_tent "$i" "$w" "$d" "$h"
done
for i in 1 2 3 4 5; do
  read w d h <<< "${SIZES[$((i-1))]}"
  room_wizard_tower "$i" "$w" "$d" "$h"
done
for i in 1 2 3 4 5; do
  read w d h <<< "${SIZES[$((i-1))]}"
  room_cave "$i" "$w" "$d" "$h"
done
for i in 1 2 3 4 5; do
  read w d h <<< "${SIZES[$((i-1))]}"
  room_mine "$i" "$w" "$d" "$h"
done
for i in 1 2 3 4 5; do
  read w d h <<< "${SIZES[$((i-1))]}"
  room_basement "$i" "$w" "$d" "$h"
done

# ---------------------------------------------------------------------------
# CONNECTORS -- 20 total, mix of horizontal/vertical/mixed, varying mechanisms
# ---------------------------------------------------------------------------

straight_hallway() {
  local name=$1 len=$2 size=$3 wallBlock=$4
  local x1=$((X0+len+1))
  RC "fill $X0 $((Y0-1)) $((Z0-1)) $x1 $((Y0-1)) $((Z0+1)) $wallBlock"
  RC "fill $X0 $((Y0+2)) $((Z0-1)) $x1 $((Y0+2)) $((Z0+1)) $wallBlock"
  RC "fill $X0 $Y0 $((Z0-1)) $x1 $((Y0+1)) $((Z0-1)) $wallBlock"
  RC "fill $X0 $Y0 $((Z0+1)) $x1 $((Y0+1)) $((Z0+1)) $wallBlock"
  RC "setblock $((X0+len/2)) $((Y0+1)) $Z0 minecraft:wall_torch[facing=south]"
  wall_conn "$X0" "$Y0" "$Z0" west "$size"
  wall_conn "$x1" "$Y0" "$Z0" east "$size"
  save_piece "$name" connector
}

doorway() {
  local name=$1 size=$2
  RC "fill $X0 $((Y0-1)) $((Z0-1)) $((X0+1)) $((Y0-1)) $((Z0+1)) minecraft:stone_bricks"
  RC "fill $X0 $((Y0+2)) $((Z0-1)) $((X0+1)) $((Y0+2)) $((Z0+1)) minecraft:stone_bricks"
  wall_conn "$X0" "$Y0" "$Z0" west "$size"
  wall_conn "$((X0+1))" "$Y0" "$Z0" east "$size"
  save_piece "$name" connector
}

ladder_shaft() {
  local name=$1 height=$2
  local yTop=$((Y0+height))
  RC "fill $((X0-1)) $Y0 $((Z0-2)) $((X0+2)) $yTop $((Z0+2)) minecraft:stone_bricks"
  RC "fill $X0 $Y0 $((Z0-1)) $((X0+1)) $yTop $((Z0+1)) air"
  RC "setblock $X0 $Y0 $((Z0-1)) minecraft:ladder[facing=south,waterlogged=false]"
  RC "fill $X0 $((Y0+1)) $((Z0-1)) $X0 $((yTop-1)) $((Z0-1)) minecraft:ladder[facing=south,waterlogged=false]"
  floor_conn "$X0" "$Y0" "$Z0" small
  floor_conn "$X0" "$yTop" "$Z0" small
  save_piece "$name" connector
}

trapdoor_shaft() {
  local name=$1 height=$2
  local yTop=$((Y0+height))
  RC "fill $((X0-1)) $Y0 $((Z0-2)) $((X0+2)) $yTop $((Z0+2)) minecraft:cobblestone"
  RC "fill $X0 $((Y0+1)) $((Z0-1)) $((X0+1)) $((yTop-1)) $((Z0+1)) air"
  RC "setblock $X0 $Y0 $Z0 tinywyrlds:connector_point_floor_small[facing=north,half=bottom,open=false]"
  RC "setblock $X0 $yTop $Z0 tinywyrlds:connector_point_floor_small[facing=north,half=bottom,open=false]"
  RC "setblock $((X0+1)) $((Y0+1)) $Z0 minecraft:ladder[facing=west,waterlogged=false]"
  RC "fill $((X0+1)) $((Y0+1)) $Z0 $((X0+1)) $((yTop-1)) $Z0 minecraft:ladder[facing=west,waterlogged=false]"
  save_piece "$name" connector
}

l_bend_hallway() {
  local name=$1
  RC "fill $X0 $((Y0-1)) $((Z0-1)) $((X0+6)) $((Y0-1)) $((Z0+5)) minecraft:cobblestone"
  RC "fill $X0 $((Y0+2)) $((Z0-1)) $((X0+6)) $((Y0+2)) $((Z0+5)) minecraft:cobblestone"
  RC "fill $X0 $Y0 $((Z0-1)) $((X0+6)) $((Y0+1)) $((Z0-1)) minecraft:cobblestone"
  RC "fill $((X0+6)) $Y0 $((Z0-1)) $((X0+6)) $((Y0+1)) $((Z0+5)) minecraft:cobblestone"
  RC "fill $X0 $Y0 $((Z0+5)) $((X0+6)) $((Y0+1)) $((Z0+5)) minecraft:cobblestone"
  RC "fill $((X0+1)) $Y0 $((Z0+1)) $((X0+5)) $((Y0+1)) $((Z0+1)) minecraft:cobblestone"
  wall_conn "$X0" "$Y0" "$Z0" west small
  wall_conn "$((X0+6))" "$Y0" "$((Z0+4))" east small
  save_piece "$name" connector
}

hidden_piston_door() {
  local name=$1
  # A short hallway with a sticky-piston mechanism flanking it and a lever nearby, suggesting a
  # closeable hidden door. Redesigned (2026-10-14, found live + confirmed by WyrldPrintValidator):
  # the original version placed the pistons "extended" with a separately hand-placed static block
  # standing in for the pushed block -- raw /setblock never actually runs a real piston push, so
  # that block just permanently blocked the passage with no way to open it. This version captures
  # the pistons mounted sideways into the wall plane itself (retracted, cosmetic only) so the
  # passage is genuinely open by default; a real, actuated push/pull mechanism needs a true
  # redstone event to trigger, not just placed blockstates, and is flagged follow-up work.
  RC "fill $X0 $((Y0-1)) $((Z0-1)) $((X0+6)) $((Y0-1)) $((Z0+1)) minecraft:cobblestone"
  RC "fill $X0 $((Y0+2)) $((Z0-1)) $((X0+6)) $((Y0+2)) $((Z0+1)) minecraft:cobblestone"
  RC "fill $X0 $Y0 $((Z0-1)) $((X0+6)) $((Y0+1)) $((Z0-1)) minecraft:cobblestone"
  RC "fill $X0 $Y0 $((Z0+1)) $((X0+6)) $((Y0+1)) $((Z0+1)) minecraft:cobblestone"
  RC "setblock $((X0+5)) $Y0 $((Z0-1)) minecraft:sticky_piston[facing=south,extended=false]"
  RC "setblock $((X0+5)) $((Y0+1)) $((Z0-1)) minecraft:sticky_piston[facing=south,extended=false]"
  RC "setblock $((X0+4)) $((Y0+1)) $((Z0-1)) minecraft:lever[face=wall,facing=south,powered=false]"
  wall_conn "$X0" "$Y0" "$Z0" west small
  wall_conn "$((X0+6))" "$Y0" "$Z0" east small
  save_piece "$name" connector
}

mixed_bend_shaft() {
  local name=$1
  # Enter horizontally, climb a ladder up a genuinely clear shaft, exit horizontally again --
  # a real horizontal+vertical mix. Redesigned (2026-10-14) after the first version's climb led
  # into a solid wall (found live, confirmed by WyrldPrintValidator): that version's "force a
  # detour" wall also covered the one column the ladder actually climbed. This version keeps the
  # climbing column (shaftX, Z0) and the corridor it connects to completely open from floor to
  # ceiling -- walls only ever go on the three OTHER sides of the shaft.
  local shaftX=$((X0+3))
  local topY=$((Y0+6))
  RC "fill $X0 $((Y0-1)) $((Z0-1)) $shaftX $((Y0-1)) $((Z0+1)) minecraft:andesite"
  RC "fill $X0 $Y0 $((Z0-1)) $((shaftX-1)) $((Y0+1)) $((Z0-1)) minecraft:andesite"
  RC "fill $X0 $Y0 $((Z0+1)) $((shaftX-1)) $((Y0+1)) $((Z0+1)) minecraft:andesite"
  RC "fill $X0 $((Y0+2)) $((Z0-1)) $((shaftX-1)) $((Y0+2)) $((Z0+1)) minecraft:andesite"
  RC "fill $shaftX $Y0 $((Z0-1)) $shaftX $topY $((Z0-1)) minecraft:andesite"
  RC "fill $shaftX $Y0 $((Z0+1)) $shaftX $topY $((Z0+1)) minecraft:andesite"
  RC "fill $((shaftX+1)) $Y0 $((Z0-1)) $((shaftX+1)) $topY $((Z0+1)) minecraft:andesite"
  RC "fill $shaftX $((Y0+1)) $Z0 $shaftX $((topY-1)) $Z0 minecraft:ladder[facing=south,waterlogged=false]"
  wall_conn "$X0" "$Y0" "$Z0" west small
  wall_conn "$((shaftX+1))" "$topY" "$Z0" east small
  save_piece "$name" connector
}

big_archway() {
  local name=$1 len=$2
  local x1=$((X0+len+1))
  RC "fill $X0 $((Y0-1)) $((Z0-2)) $x1 $((Y0-1)) $((Z0+2)) minecraft:smooth_stone"
  RC "fill $X0 $((Y0+4)) $((Z0-2)) $x1 $((Y0+4)) $((Z0+2)) minecraft:smooth_stone"
  RC "fill $X0 $Y0 $((Z0-2)) $x1 $((Y0+3)) $((Z0-2)) minecraft:smooth_stone"
  RC "fill $X0 $Y0 $((Z0+2)) $x1 $((Y0+3)) $((Z0+2)) minecraft:smooth_stone"
  wall_conn "$X0" "$Y0" "$Z0" west large
  wall_conn "$x1" "$Y0" "$Z0" east large
  save_piece "$name" connector
}

echo "=== Generating 20 connectors ==="
straight_hallway "Simple Hallway 1" 5 small minecraft:cobblestone
straight_hallway "Simple Hallway 2" 8 small minecraft:stone_bricks
straight_hallway "Simple Hallway 3" 10 medium minecraft:mossy_cobblestone
straight_hallway "Simple Hallway 4" 6 small minecraft:deepslate_bricks
doorway "Plain Doorway 1" small
doorway "Plain Doorway 2" medium
ladder_shaft "Ladder Shaft 1" 5
ladder_shaft "Ladder Shaft 2" 8
ladder_shaft "Ladder Shaft 3" 4
trapdoor_shaft "Trapdoor Shaft 1" 5
trapdoor_shaft "Trapdoor Shaft 2" 7
l_bend_hallway "L-Bend Hallway 1"
l_bend_hallway "L-Bend Hallway 2"
hidden_piston_door "Hidden Piston Door 1"
hidden_piston_door "Hidden Piston Door 2"
mixed_bend_shaft "Mixed Bend Shaft 1"
mixed_bend_shaft "Mixed Bend Shaft 2"
big_archway "Grand Archway 1" 6
big_archway "Grand Archway 2" 9
straight_hallway "Simple Hallway 5" 12 medium minecraft:bricks

echo "=== Done. Listing all WyrldPrints ==="
RCP "tinywyrlds wyrldprint list"
