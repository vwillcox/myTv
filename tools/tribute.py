"""A tribute to Sir Patrick Stewart (13 July 1940 - 28 October 2026), for the LCARS theme's top block.

The Enterprise-D sails away from the camera through the stars, stretches out and goes to warp in a flash where it
went, then his portrait fades in over the stars and his name comes up under it, in Antonio and the panel colours,
with an LCARS bar between.

Usage:
  blender -b --factory-startup --disable-autoexec -P tools/tribute.py -- OUT_DIR \\
      --model enterprise-d.blend --fonts FONT_DIR --photo tools/tribute-portrait.png \\
      [--samples S] [--scale PCT] [--only-frames A,B,...]

  --model  the Enterprise-D the Omarchy LCARS-TNG theme uses ("USS Enterprise - NCC-1701-D" by Joachim Bornemann,
           BlenderKit, free), bow toward -Y, Z up
  --fonts  a folder with antonio-400.ttf and antonio-700.ttf: static weights of app/src/main/res/font/antonio.ttf,
           from fontTools' instantiateVariableFont(font, {"wght": 400}) and so on
  --photo  a cut-out portrait with alpha: tools/tribute-portrait.png is from "Patrick Stewart speaking at the 2019 San
           Diego Comic-Con International" by Gage Skidmore (CC BY-SA 3.0, Wikimedia Commons), cut out with rembg's
           isnet-general-use and faded out at the foot; see licenses/

Frames go to OUT_DIR/frame_####.png. Then, adding bloom (in RGB: blended in YUV, it turns everything purple) and a fade in from black:

  ffmpeg -framerate 30 -i OUT_DIR/frame_%04d.png -filter_complex \\
      "[0]format=gbrp,split[a][b];[b]gblur=sigma=14[g];[a][g]blend=all_mode=screen:all_opacity=0.55,fade=in:0:24,format=yuv420p" \\
      -c:v libx265 -preset slow -crf 20 -tag:v hvc1 -an -movflags +faststart app/src/main/res/raw/tribute.mp4

Units: 1 BU = 100 m, the ship 642 m long.
"""

import bmesh
import bpy
import math
import os
import random
import sys
from mathutils import Matrix, Vector

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
OUT = (argv[0] if argv else "/tmp/tribute").rstrip("/")


def arg(name, default, cast=str):
    return cast(argv[argv.index(name) + 1]) if name in argv else default


MODEL = arg("--model", "")
FONTS = arg("--fonts", "")
SAMPLES = arg("--samples", 64, int)
SCALE = arg("--scale", 100, int)
ONLY = [int(x) for x in arg("--only-frames", "").split(",") if x]
PHOTO = arg("--photo", "")

FPS = 30
FRAMES = 600            # 20 s
CRUISE_END = 240        # sailing away
STRETCH = 270           # it starts to draw out ahead of itself
SNAP = 283              # the rest of it follows into the bow
FLASH = 284             # gone, in a flash where the bow got to
PORTRAIT = 305          # his picture starts to come up
REVEAL = 360            # the words start to come up
K = 9.0                 # how many of its own lengths it's drawn out to
HALF = 3.21             # half the ship's length

# ---------------------------------------------------------------- scene
bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
scene.render.engine = "CYCLES"
prefs = bpy.context.preferences.addons["cycles"].preferences
prefs.compute_device_type = "OPTIX"
prefs.get_devices()
for d in prefs.devices:
    d.use = d.type == "OPTIX"
scene.cycles.device = "GPU"
scene.cycles.samples = SAMPLES
scene.cycles.use_denoising = True
# On the card: on the processor it takes longer than the render.
scene.cycles.denoiser = "OPTIX"
scene.cycles.max_bounces = 6
# The flash is a stack of see-through planes, behind the words' and the portrait's: past the limit, what's further
# back is cut off in boxes.
scene.cycles.transparent_max_bounces = 32
scene.render.resolution_x = 1920
scene.render.resolution_y = 1080
scene.render.resolution_percentage = SCALE
scene.render.fps = FPS
scene.render.image_settings.file_format = "PNG"
scene.render.image_settings.color_mode = "RGB"
scene.render.image_settings.color_depth = "8"
# Standard, so the panel colours come out as themselves; the bloom is added afterwards.
scene.view_settings.view_transform = "Standard"
scene.view_settings.look = "None"
scene.frame_start = 0
scene.frame_end = FRAMES - 1
scene.render.use_persistent_data = True
scene.render.motion_blur_shutter = 0.5

world = bpy.data.worlds.new("Space")
scene.world = world
world.use_nodes = True
world.node_tree.nodes["Background"].inputs[0].default_value = (0.0006, 0.0008, 0.0016, 1)


def srgb(hex_colour, k=1.0):
    """An LCARS colour as Blender's linear RGBA, times [k]."""
    def lin(c):
        c /= 255
        return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4
    r, g, b = (hex_colour >> 16) & 255, (hex_colour >> 8) & 255, hex_colour & 255
    return (lin(r) * k, lin(g) * k, lin(b) * k, 1.0)


ORANGE, PEACH, LILAC, BLUEBELL, TANGERINE = 0xFF9900, 0xFFCC99, 0xCC99CC, 0x9999CC, 0xFF9966


def nodes(mat):
    mat.use_nodes = True
    t = mat.node_tree
    t.nodes.clear()
    return t, t.nodes.new("ShaderNodeOutputMaterial")


def fading(name, colour):
    """Emission that can be faded in: a mix from fully transparent to glowing. Returns the material and the mix."""
    mat = bpy.data.materials.new(name)
    t, out = nodes(mat)
    em = t.nodes.new("ShaderNodeEmission")
    em.inputs["Color"].default_value = colour
    em.inputs["Strength"].default_value = 1.0
    clear = t.nodes.new("ShaderNodeBsdfTransparent")
    mix = t.nodes.new("ShaderNodeMixShader")
    mix.inputs[0].default_value = 0.0
    t.links.new(clear.outputs[0], mix.inputs[1])
    t.links.new(em.outputs[0], mix.inputs[2])
    t.links.new(mix.outputs[0], out.inputs[0])
    return mat, mix


def key(socket, frames_values):
    for f, v in frames_values:
        socket.default_value = v
        socket.keyframe_insert("default_value", frame=f)


def link(ob):
    scene.collection.objects.link(ob)
    return ob


def curves(ob):
    """The F-curves animating [ob], in Blender 5's layered actions or the old kind."""
    act = ob.animation_data.action if ob.animation_data else None
    if act is None:
        return []
    fcs = getattr(act, "fcurves", None)
    if fcs is None:
        fcs = [fc for layer in act.layers for strip in layer.strips for bag in strip.channelbags for fc in bag.fcurves]
    return list(fcs)


def smoothstep(e0, e1, x):
    t = min(max((x - e0) / (e1 - e0), 0.0), 1.0)
    return t * t * (3 - 2 * t)


# ---------------------------------------------------------------- the ship
ship = link(bpy.data.objects.new("Enterprise", None))
if MODEL:
    with bpy.data.libraries.load(MODEL, link=False) as (src, dst):
        dst.objects = list(src.objects)
    imported = [o for o in dst.objects if o is not None]
    for o in imported:
        scene.collection.objects.link(o)
    bpy.context.view_layer.update()
    lo, hi = Vector((1e9,) * 3), Vector((-1e9,) * 3)
    for o in imported:
        if o.type != "MESH":
            continue
        for c in o.bound_box:
            w = o.matrix_world @ Vector(c)
            lo, hi = Vector(map(min, lo, w)), Vector(map(max, hi, w))
    xf = Matrix.Scale(6.42 / (hi.y - lo.y), 4) @ Matrix.Translation(-(lo + hi) / 2)
    for o in imported:
        if o.parent is None:
            o.matrix_world = xf @ o.matrix_world
            o.parent = ship


def run_up(f):
    """Where the ship's middle is along its course: a slow cruise away, then a run-up."""
    cruise = -0.055 * min(f, CRUISE_END)
    if f <= CRUISE_END:
        return cruise
    t = (f - CRUISE_END) / 44
    return cruise - 0.055 * (f - CRUISE_END) - 40.0 * t ** 3


def bob(f):
    return 0.06 * math.sin(f / FPS * 2 * math.pi / 7)


def stretch(f):
    """How long the ship is, in its own lengths: drawn out faster and faster toward the snap."""
    s = min(max((f - STRETCH) / (SNAP - 1 - STRETCH), 0.0), 1.0)
    return 1 + (K - 1) * s ** 2.2


V0 = run_up(STRETCH) - run_up(STRETCH - 1)     # how fast it's going when it starts to stretch


def stern(f):
    """Where its stern is: from the stretch, it eases to a stop while the bow draws out."""
    if f < STRETCH:
        return run_up(f) + HALF
    t = min(f, SNAP - 1) - STRETCH
    return run_up(STRETCH) + HALF + V0 * 3.0 * (1 - math.exp(-t / 3.0))


TIP = stern(SNAP - 1) - 2 * HALF * K     # the far end of the stretch, where it goes


def pose(f):
    """The ship's middle along its course, and its scale. Stretching, it holds its stern and draws its bow out ahead,
    down toward where the course vanishes; at the snap the rest follows into the bow; then it's gone."""
    if f < STRETCH:
        return run_up(f), (1, 1, 1)
    if f < SNAP:
        k = stretch(f)
        return stern(f) - HALF * k, (1, k, 1)
    if f == SNAP:
        return TIP + HALF * 1.4, (1, 1.4, 1)
    return TIP, (0.02, 0.02, 0.02)


for f in range(FRAMES):
    y, scale = pose(f)
    ship.location = (0.0, y, bob(f))
    ship.rotation_euler = (0.0, math.radians(1.5 * math.sin(f / FPS * 2 * math.pi / 11)), 0.0)
    ship.scale = scale
    for path in ("location", "rotation_euler", "scale"):
        ship.keyframe_insert(path, frame=f)
for fc in curves(ship):
    for kp in fc.keyframe_points:
        # Linear, as motion blur samples between keys, so the stretch and the snap are drawn as movement.
        kp.interpolation = "CONSTANT" if kp.co.x >= FLASH else "LINEAR"


def light(name, kind, travel, energy, colour):
    ld = bpy.data.lights.new(name, kind)
    ld.energy = energy
    ld.color = colour
    ld.angle = math.radians(1.5)
    ob = link(bpy.data.objects.new(name, ld))
    ob.rotation_euler = Vector(travel).to_track_quat("-Z", "Y").to_euler()
    return ob


# From over the camera's shoulder onto the saucer top; a cold rim from ahead for the edges; a faint warm lift below.
light("Key", "SUN", (-0.35, -0.55, -0.76), 2.2, (1.0, 0.95, 0.88))
light("Rim", "SUN", (0.25, 0.85, -0.45), 2.6, (0.55, 0.70, 1.0))
light("Under", "SUN", (0.4, -0.3, 0.86), 0.35, (1.0, 0.75, 0.6))

# ---------------------------------------------------------------- camera
cam_d = bpy.data.cameras.new("Cam")
cam_d.lens = 50
cam_d.sensor_width = 36
cam_d.clip_end = 8000
cam = link(bpy.data.objects.new("Cam", cam_d))
scene.camera = cam
FAR = Vector((0.0, -400.0, 0.0))
for f in range(FRAMES):
    g = min(f, STRETCH)
    cam.location = Vector((3.4, 15.0 - 0.012 * min(g, CRUISE_END), 3.3))
    follow = Vector((0.0, run_up(min(g, CRUISE_END)) - 4.0, -0.3))
    aim = follow.lerp(FAR, smoothstep(200, 275, g))
    cam.rotation_euler = (aim - cam.location).to_track_quat("-Z", "Y").to_euler()
    cam.keyframe_insert("location", frame=f)
    cam.keyframe_insert("rotation_euler", frame=f)

# ---------------------------------------------------------------- stars
random.seed(1701)
stars = bpy.data.meshes.new("Stars")
bm = bmesh.new()
tint = bm.verts.layers.float_color.new("tint")
for _ in range(2600):
    # A cone round the way the ship goes, which is all the camera ever sees.
    while True:
        d = Vector((random.uniform(-1, 1), random.uniform(-1, 1), random.uniform(-1, 1)))
        if 0 < d.length <= 1 and d.normalized().dot(Vector((0, -1, 0))) > math.cos(math.radians(42)):
            break
    d.normalize()
    r = random.choice((1.0, 1.0, 1.2, 1.4, 1.8, 2.4))
    level = random.random() ** 3 * 3.5 + 0.25
    hue = random.choice(((1.0, 1.0, 1.0), (0.8, 0.88, 1.0), (1.0, 0.9, 0.78), (0.88, 0.9, 1.0)))
    made = bmesh.ops.create_icosphere(bm, subdivisions=1, radius=r, matrix=Matrix.Translation(d * 4000))
    for v in made["verts"]:
        v[tint] = (hue[0] * level, hue[1] * level, hue[2] * level, 1.0)
bm.to_mesh(stars)
bm.free()
star_ob = link(bpy.data.objects.new("Stars", stars))
star_mat = bpy.data.materials.new("Star")
t, out = nodes(star_mat)
attr = t.nodes.new("ShaderNodeAttribute")
attr.attribute_name = "tint"
em = t.nodes.new("ShaderNodeEmission")
t.links.new(attr.outputs["Color"], em.inputs["Color"])
t.links.new(em.outputs[0], out.inputs[0])
stars.materials.append(star_mat)

# ---------------------------------------------------------------- the jump to warp
# All of it on planes facing the lens, in the camera's space, which is still from the stretch on.
scene.frame_set(STRETCH)
view = cam.matrix_world.inverted()


def op(t, kind, a, b=0.0):
    m = t.nodes.new("ShaderNodeMath")
    m.operation = kind
    for sock, v in zip(m.inputs, (a, b)):
        if isinstance(v, bpy.types.NodeSocket):
            t.links.new(v, sock)
        else:
            sock.default_value = v
    return m.outputs[0]


def radius(t, p):
    v = t.nodes.new("ShaderNodeVectorMath")
    v.operation = "LENGTH"
    t.links.new(p, v.inputs[0])
    return v.outputs["Value"]


def soft(power):
    """Brightest in the middle of the plane and gone at its edge."""
    return lambda t, p: op(t, "POWER", op(t, "MAXIMUM", op(t, "SUBTRACT", 1.0, radius(t, p)), 0.0), power)


def rays(count, width):
    """[count] thin spikes out from the middle, as from a star filter, [width] of the plane's half wide all the way
    out and fading to their tips. One points straight up."""
    def shape(t, p):
        xyz = t.nodes.new("ShaderNodeSeparateXYZ")
        t.links.new(p, xyz.inputs[0])
        angle = op(t, "ARCTAN2", xyz.outputs["Y"], xyz.outputs["X"])
        # How far off the nearest spike, across it: near enough the distance, close in to one.
        off = op(t, "MULTIPLY", op(t, "ABSOLUTE", op(t, "COSINE", op(t, "MULTIPLY", angle, count / 2))), radius(t, p))
        across = op(t, "MAXIMUM", op(t, "SUBTRACT", 1.0, op(t, "DIVIDE", off, width * count / 2)), 0.0)
        return op(t, "MULTIPLY", op(t, "POWER", across, 2.0), soft(1.2)(t, p))
    return shape


def beam(t, p):
    """Soft all round, and brightest toward its front end, along +x."""
    xyz = t.nodes.new("ShaderNodeSeparateXYZ")
    t.links.new(p, xyz.inputs[0])
    lean = op(t, "POWER", op(t, "MULTIPLY", op(t, "ADD", xyz.outputs["X"], 1.0), 0.5), 1.5)
    return op(t, "MULTIPLY", soft(1.3)(t, p), lean)


def flare(name, colour, shape):
    """A light on a plane, shaped by [shape] over the plane's own -1 to 1 square and added over what's behind it.
    Returns the plane and the socket for its strength."""
    mat = bpy.data.materials.new(name)
    t, out = nodes(mat)
    coords = t.nodes.new("ShaderNodeTexCoord")
    level = t.nodes.new("ShaderNodeMath")
    level.operation = "MULTIPLY"
    t.links.new(shape(t, coords.outputs["Object"]), level.inputs[0])
    level.inputs[1].default_value = 0.0
    em = t.nodes.new("ShaderNodeEmission")
    em.inputs["Color"].default_value = colour
    t.links.new(level.outputs[0], em.inputs["Strength"])
    add = t.nodes.new("ShaderNodeAddShader")
    t.links.new(t.nodes.new("ShaderNodeBsdfTransparent").outputs[0], add.inputs[0])
    t.links.new(em.outputs[0], add.inputs[1])
    t.links.new(add.outputs[0], out.inputs[0])
    me = bpy.data.meshes.new(name)
    me.from_pydata([(-1, -1, 0), (1, -1, 0), (1, 1, 0), (-1, 1, 0)], [], [(0, 1, 2, 3)])
    me.materials.append(mat)
    ob = link(bpy.data.objects.new(name, me))
    ob.parent = cam
    ob.visible_shadow = False
    return ob, level.inputs[1]


def at_depth(world, depth):
    """Where [world] is seen, on the plane [depth] in front of the lens, in the camera's space."""
    p = view @ world
    return p * (depth / -p.z)


def px(n, depth):
    """[n] pixels across, at [depth], in the scene's units."""
    return n * depth * (cam_d.sensor_width / cam_d.lens) / 1920


# The warp streak: a light along the ship from its stern to its drawn-out bow, in front of it so the hull can't
# hide it, gathering toward the bow and brightening as it stretches. At the snap it runs into the bow.
NEAR = 20.0
streak, streak_level = flare("WarpStreak", (0.62, 0.78, 1.0, 1), beam)
for f in range(STRETCH, FLASH + 1):
    y, scale = pose(f)
    back = Vector((0.0, y + HALF * scale[1], bob(f)))
    front = Vector((0.0, y - HALF * scale[1], bob(f)))
    a, b = at_depth(back, NEAR), at_depth(front, NEAR)
    mid, d = (a + b) / 2, b - a
    streak.location = (mid.x, mid.y, -NEAR)
    streak.rotation_euler = (0.0, 0.0, math.atan2(d.y, d.x))
    streak.scale = (max(d.length / 2 * 1.15, px(30, NEAR)), px(9 + 12 * (f - STRETCH) / (SNAP - STRETCH), NEAR), 1)
    for path in ("location", "rotation_euler", "scale"):
        streak.keyframe_insert(path, frame=f)
key(streak_level, [(STRETCH, 0.0), (STRETCH + 5, 2.0), (SNAP - 2, 9.0), (SNAP - 1, 18.0), (SNAP, 27.0), (FLASH, 0.0)])

# The hull lights up as it's drawn out: a glow added to each of its own materials.
seen = set()
for o in ship.children_recursive:
    for slot in getattr(o, "material_slots", []):
        m = slot.material
        if m is None or m.name in seen or not m.use_nodes:
            continue
        seen.add(m.name)
        t = m.node_tree
        out = next((n for n in t.nodes if n.type == "OUTPUT_MATERIAL" and n.is_active_output), None)
        if out is None or not out.inputs["Surface"].is_linked:
            continue
        surface = out.inputs["Surface"].links[0].from_socket
        em = t.nodes.new("ShaderNodeEmission")
        em.inputs["Color"].default_value = (0.45, 0.62, 1.0, 1)
        add = t.nodes.new("ShaderNodeAddShader")
        t.links.new(surface, add.inputs[0])
        t.links.new(em.outputs[0], add.inputs[1])
        t.links.new(add.outputs[0], out.inputs["Surface"])
        key(em.inputs["Strength"], [(STRETCH, 0.0), (STRETCH + 6, 0.04), (SNAP - 3, 0.14), (SNAP - 2, 0.26), (SNAP - 1, 0.6), (SNAP, 3.0)])

# The flash, on the far end of the stretch where the ship went.
centre = view @ Vector((0.0, TIP, bob(SNAP)))
depth = -centre.z


bursts = []


def burst(name, colour, shape, w, h, levels, sizes=None, spin=0.0):
    ob, level = flare(name, colour, shape)
    # Each a little nearer the lens than the last: planes in the same place cross-hatch where they overlap.
    ob.location = centre * (1 - 0.003 * len(bursts))
    bursts.append(ob)
    key(level, levels)
    for f, k in (sizes or [(FLASH, 1.0)]):
        ob.scale = (px(w, depth) * k, px(h, depth) * k, 1)
        ob.keyframe_insert("scale", frame=f)
    if spin:
        for f, a in ((SNAP, 0.0), (FLASH + 30, spin)):
            ob.rotation_euler = (0.0, 0.0, math.radians(a))
            ob.keyframe_insert("rotation_euler", frame=f)
    return ob


WHITE, BLUE, PALE = (1.0, 1.0, 1.0, 1), (0.55, 0.72, 1.0, 1), (0.78, 0.88, 1.0, 1)
burst("Hot", WHITE, soft(2.0), 34, 34, [(SNAP - 1, 0.0), (SNAP, 8.0), (FLASH, 60.0), (FLASH + 2, 22.0), (FLASH + 6, 5.0), (FLASH + 16, 0.0)])
burst("Glow", PALE, soft(3.5), 360, 360, [(SNAP - 1, 0.0), (SNAP, 0.8), (FLASH, 3.0), (FLASH + 3, 1.3), (FLASH + 12, 0.4), (FLASH + 32, 0.0)],
      sizes=[(SNAP, 0.45), (FLASH + 2, 1.1), (FLASH + 30, 0.8)])
burst("Anamorphic", BLUE, soft(1.8), 2300, 16, [(SNAP - 1, 0.0), (FLASH, 4.0), (FLASH + 4, 1.4), (FLASH + 18, 0.0)],
      sizes=[(SNAP, 0.25), (FLASH + 3, 1.0), (FLASH + 18, 1.2)])
burst("Hairline", PALE, soft(1.2), 3600, 5, [(SNAP - 1, 0.0), (FLASH, 2.5), (FLASH + 10, 0.0)],
      sizes=[(SNAP, 0.3), (FLASH + 4, 1.0)])
burst("Rays", PALE, rays(6, 4 / 900), 900, 900, [(SNAP - 1, 0.0), (FLASH, 4.0), (FLASH + 5, 1.5), (FLASH + 20, 0.0)],
      sizes=[(SNAP, 0.5), (FLASH + 3, 1.0), (FLASH + 20, 1.15)], spin=9.0)
burst("FineRays", PALE, rays(16, 2.5 / 560), 560, 560, [(SNAP - 1, 0.0), (FLASH, 2.0), (FLASH + 12, 0.0)], spin=-6.0)


# ---------------------------------------------------------------- the words
DIST = 12.0
ANTONIO = {w: bpy.data.fonts.load(os.path.join(FONTS, "antonio-%d.ttf" % w)) for w in (400, 700)} if FONTS else {}


def words(name, body, size, weight, colour, y, spacing=1.0):
    cu = bpy.data.curves.new(name, "FONT")
    cu.body = body
    if ANTONIO:
        cu.font = ANTONIO[weight]
    cu.size = size
    cu.align_x = "CENTER"
    cu.align_y = "CENTER"
    cu.space_character = spacing
    cu.resolution_u = 24
    mat, mix = fading(name, colour)
    cu.materials.append(mat)
    ob = link(bpy.data.objects.new(name, cu))
    ob.parent = cam
    ob.location = (0.0, y, -DIST)
    return ob, mix.inputs[0]


def rise(ob, socket, start, length=50):
    """Fades in over [length] frames from [start], drifting up into place as it does."""
    x, y, z = ob.location
    ob.location = (x, y - 0.12, z)
    ob.keyframe_insert("location", frame=start)
    ob.location = (x, y, z)
    ob.keyframe_insert("location", frame=start + length)
    key(socket, [(start, 0.0), (start + length, 1.0)])


rip, rip_fade = words("Rip", "R.I.P.", 0.30, 400, srgb(LILAC), -0.33, spacing=1.25)
name, name_fade = words("Name", "PATRICK STEWART", 0.86, 700, srgb(ORANGE), -0.76, spacing=1.04)
dates, dates_fade = words("Dates", "13 JULY 1940 \u2013 28 OCTOBER 2026", 0.29, 400, srgb(PEACH), -1.48, spacing=1.12)
rise(rip, rip_fade, REVEAL)
rise(name, name_fade, REVEAL + 32, 60)
rise(dates, dates_fade, REVEAL + 104)

# An LCARS bar under the name, as wide as it: a capped orange end, a long lilac run and a short peach end, with
# black slices between. It runs out from the middle.
bpy.context.view_layer.update()
width = name.dimensions.x
H, SLICE = 0.12, 0.04


def pill(x0, x1, cap_left, cap_right, segs=16):
    """The outline of a bar piece from [x0] to [x1], round at either end that has a cap, anticlockwise."""
    r = H / 2

    def arc(cx, a0, a1):
        return [(cx + r * math.cos(math.radians(a0 + (a1 - a0) * i / segs)), r * math.sin(math.radians(a0 + (a1 - a0) * i / segs)))
                for i in range(segs + 1)]
    return (arc(x0 + r, 90, 270) if cap_left else [(x0, r), (x0, -r)]) + (arc(x1 - r, -90, 90) if cap_right else [(x1, -r), (x1, r)])


bar = bpy.data.meshes.new("Bar")
bm = bmesh.new()
left, right = -width / 2, width / 2
pieces = [(left, left + 0.5, True, False, ORANGE), (left + 0.5 + SLICE, right - 0.34 - SLICE, False, False, LILAC),
          (right - 0.34, right, False, True, PEACH)]
bar_mats = []
for i, (x0, x1, cl, cr, colour) in enumerate(pieces):
    outline = pill(x0, x1, cl, cr)
    face = bm.faces.new([bm.verts.new((x, y, 0.0)) for x, y in outline])
    face.material_index = i
    mat, mix = fading("Bar%d" % i, srgb(colour))
    bar.materials.append(mat)
    bar_mats.append(mix.inputs[0])
bm.to_mesh(bar)
bm.free()
bar_ob = link(bpy.data.objects.new("Bar", bar))
bar_ob.parent = cam
bar_ob.location = (0.0, -1.16, -DIST)
start = REVEAL + 80
bar_ob.scale = (0.02, 1, 1)
bar_ob.keyframe_insert("scale", frame=start)
bar_ob.scale = (1, 1, 1)
bar_ob.keyframe_insert("scale", frame=start + 34)
for s in bar_mats:
    key(s, [(start, 0.0), (start + 10, 1.0)])

# ---------------------------------------------------------------- the portrait
# A plane above the words giving off the photo's own colours rather than lit, its cut-out's alpha letting the stars
# through round him.
if PHOTO:
    photo = bpy.data.images.load(PHOTO)
    mat = bpy.data.materials.new("Portrait")
    t, out = nodes(mat)
    coords = t.nodes.new("ShaderNodeTexCoord")
    tex = t.nodes.new("ShaderNodeTexImage")
    tex.image = photo
    tex.interpolation = "Cubic"
    tex.extension = "CLIP"
    t.links.new(coords.outputs["Generated"], tex.inputs["Vector"])
    em = t.nodes.new("ShaderNodeEmission")
    t.links.new(tex.outputs["Color"], em.inputs["Color"])
    # A little under full, as the bloom brightens what's already bright.
    em.inputs["Strength"].default_value = 0.85
    shown = t.nodes.new("ShaderNodeMath")
    shown.operation = "MULTIPLY"
    t.links.new(tex.outputs["Alpha"], shown.inputs[0])
    clear = t.nodes.new("ShaderNodeBsdfTransparent")
    mix = t.nodes.new("ShaderNodeMixShader")
    t.links.new(shown.outputs[0], mix.inputs[0])
    t.links.new(clear.outputs[0], mix.inputs[1])
    t.links.new(em.outputs[0], mix.inputs[2])
    t.links.new(mix.outputs[0], out.inputs[0])
    tall = 1.85
    me = bpy.data.meshes.new("Portrait")
    me.from_pydata([(-1, -1, 0), (1, -1, 0), (1, 1, 0), (-1, 1, 0)], [], [(0, 1, 2, 3)])
    me.materials.append(mat)
    portrait = link(bpy.data.objects.new("Portrait", me))
    portrait.parent = cam
    portrait.location = (0.0, 0.82, -DIST)
    portrait.scale = (tall / 2 * photo.size[0] / photo.size[1], tall / 2, 1)
    portrait.visible_shadow = False
    rise(portrait, shown.inputs[1], PORTRAIT, 80)

# ---------------------------------------------------------------- render
os.makedirs(OUT, exist_ok=True)
hull = [ship] + list(ship.children_recursive)
for f in (ONLY or range(FRAMES)):
    scene.render.filepath = OUT + "/frame_%04d.png" % f
    if os.path.exists(scene.render.filepath) and not ONLY:
        continue
    # Motion blur costs three looks at the scene a frame, and only the jump to warp moves fast enough to need it.
    scene.render.use_motion_blur = STRETCH <= f <= FLASH
    for o in hull:
        o.hide_render = f > FLASH
    scene.frame_set(f)
    bpy.ops.render.render(write_still=True)
