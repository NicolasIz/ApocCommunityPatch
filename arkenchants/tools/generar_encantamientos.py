"""Genera src/main/resources/enchantments-extra.yml: 360 encantamientos por familias (incluidas maldiciones).

python3 tools/generar_encantamientos.py   (desde la carpeta arkenchants)
"""
import re
import unicodedata
import yaml

OUT = "src/main/resources/enchantments-extra.yml"
BASE = "src/main/resources/enchantments.yml"


def slug(s):
    s = unicodedata.normalize("NFKD", s).encode("ascii", "ignore").decode()
    return re.sub(r"[^a-z0-9]+", "_", s.lower()).strip("_")


E = {}


def add(name, desc, applies_to, typ, group, applies, levels, settings=None, id_=None):
    i = id_ or slug(name)
    assert i not in E, "repetido: " + i
    d = {"display": "%group-color%" + name, "description": desc, "applies-to": applies_to, "type": typ,
         "group": group, "applies": applies}
    if settings:
        d["settings"] = settings
    d["levels"] = {n + 1: lv for n, lv in enumerate(levels)}
    E[i] = d


def lv(chance, effects, cooldown=0, conditions=None):
    d = {"chance": chance}
    if cooldown:
        d["cooldown"] = cooldown
    if conditions:
        d["conditions"] = conditions
    d["effects"] = effects
    return d


# ---------------------------------------------------------------- armas
ARMAS = {  # clave: (sustantivo, genero, texto, applies, cuerpo a cuerpo)
    "espada": ("Filo", "m", "Espadas", ["ALL_SWORD"], True),
    "hacha": ("Tajo", "m", "Hachas", ["ALL_AXE"], True),
    "maza": ("Golpe", "m", "Mazas", ["MACE"], True),
    "arco": ("Flecha", "f", "Arcos", ["BOW"], False),
    "ballesta": ("Virote", "m", "Ballestas", ["CROSSBOW"], False),
    "tridente": ("Arpon", "m", "Tridentes", ["TRIDENT"], False),
}


def adj(m, f, g):
    return m if g == "m" else f


# A. golpes elementales (9 elementos x 6 armas = 54)
ELEM = [
    ("fuego", "Igneo", "Ignea", "SIMPLE", "Prende fuego al enemigo.",
     lambda l: [f"BURN:{40 + 20 * l} @Victim", "PARTICLE:FLAME:15:0.02 @Victim"], 0),
    ("hielo", "Gelido", "Gelida", "UNIQUE", "Ralentiza al enemigo.",
     lambda l: [f"POTION:SLOW:{l - 1}:{40 + 20 * l} @Victim", "PARTICLE:SNOWFLAKE:15:0.02 @Victim"], 0),
    ("veneno", "Venenoso", "Venenosa", "UNIQUE", "Envenena al enemigo.",
     lambda l: [f"POTION:POISON:{l - 1}:{60 + 20 * l} @Victim", "PARTICLE:ITEM_SLIME:12:0.05 @Victim"], 0),
    ("viento", "Huracanado", "Huracanada", "SIMPLE", "Empuja lejos al enemigo.",
     lambda l: [f"PULL_AWAY:{0.6 + 0.3 * l:.1f} @Victim", "PARTICLE:CLOUD:12:0.05 @Victim"], 0),
    ("sangre", "Sangriento", "Sangrienta", "ELITE", "Hace dano extra y sangrar.",
     lambda l: [f"DO_HARM:{l} @Victim", "BLOOD @Victim"], 0),
    ("sombra", "Sombrio", "Sombria", "ELITE", "Ciega y frena al enemigo.",
     lambda l: [f"POTION:BLINDNESS:0:{20 + 10 * l} @Victim", "POTION:SLOW:0:40 @Victim", "PARTICLE:SQUID_INK:12:0.05 @Victim"], 4),
    ("arcano", "Arcano", "Arcana", "ULTIMATE", "Debilita al enemigo.",
     lambda l: [f"POTION:WEAKNESS:{l - 1}:{60 + 20 * l} @Victim", "PARTICLE:WITCH:15:0.05 @Victim"], 0),
    ("ruina", "Putrido", "Putrida", "ULTIMATE", "Marchita al enemigo.",
     lambda l: [f"POTION:WITHER:{l - 1}:{40 + 20 * l} @Victim", "PARTICLE:SMOKE:15:0.02 @Victim"], 0),
    ("trueno", "Tronante", "Tronante", "LEGENDARY", "Invoca un rayo sobre el enemigo.",
     lambda l: [f"LIGHTNING:{2 + l} @Victim"], 6),
]
for k, (noun, g, txt, applies, melee) in ARMAS.items():
    typ = "ATTACK;ATTACK_MOB" if melee else "SHOOT;SHOOT_MOB"
    base = 4 if melee else 6
    for el, am, af, group, desc, fx, cd in ELEM:
        add(f"{noun} {adj(am, af, g)}", desc, txt, typ, group, applies,
            [lv(base + (2 if melee else 3) * (l - 1), fx(l), cd) for l in (1, 2, 3)], id_=f"{k}_{el}")

# D. recompensas al matar (6 x 6 = 36)
PREMIO = [
    ("sanador", "Sanador", "Sanadora", "UNIQUE", "Al matar recuperas vida.", lambda l: [f"ADD_HEALTH:{l + 1}"], lambda l: 25 + 10 * l),
    ("veloz", "Veloz", "Veloz", "SIMPLE", "Al matar ganas velocidad.", lambda l: [f"POTION:SPEED:{l - 1}:{60 + 20 * l}"], lambda l: 30 + 10 * l),
    ("protector", "Protector", "Protectora", "ELITE", "Al matar ganas corazones extra.", lambda l: [f"POTION:ABSORPTION:{l - 1}:{100 + 20 * l}"], lambda l: 20 + 10 * l),
    ("furioso", "Furioso", "Furiosa", "ULTIMATE", "Al matar ganas fuerza un momento.", lambda l: [f"POTION:INCREASE_DAMAGE:0:{40 + 20 * l}"], lambda l: 15 + 5 * l),
    ("renovador", "Renovador", "Renovadora", "ELITE", "Al matar te regeneras.", lambda l: [f"POTION:REGENERATION:{l - 1}:{60 + 20 * l}"], lambda l: 25 + 5 * l),
    ("gloton", "Gloton", "Glotona", "SIMPLE", "Al matar recuperas comida.", lambda l: [f"ADD_FOOD:{l + 1}"], lambda l: 30 + 10 * l),
]
for k, (noun, g, txt, applies, melee) in ARMAS.items():
    for pid, am, af, group, desc, fx, ch in PREMIO:
        add(f"{noun} {adj(am, af, g)}", desc, txt, "KILL_MOB;KILL_PLAYER", group, applies,
            [lv(ch(l), fx(l)) for l in (1, 2, 3)], id_=f"{k}_{pid}")

# I. matadores de monstruos (15 x 3 = 45)
MOBS = [
    ("zombis", "Zombis", ["ZOMBIE", "HUSK"], "SIMPLE"), ("esqueletos", "Esqueletos", ["SKELETON", "STRAY", "BOGGED"], "SIMPLE"),
    ("aranas", "Aranas", ["SPIDER"], "SIMPLE"), ("creepers", "Creepers", ["CREEPER"], "UNIQUE"),
    ("endermans", "Endermans", ["ENDERMAN", "ENDERMITE"], "UNIQUE"), ("piglins", "Piglins", ["PIGLIN", "HOGLIN", "ZOGLIN"], "UNIQUE"),
    ("blazes", "Blazes", ["BLAZE", "GHAST"], "ELITE"), ("brujas", "Brujas", ["WITCH"], "UNIQUE"),
    ("illagers", "Illagers", ["PILLAGER", "VINDICATOR", "EVOKER", "RAVAGER", "VEX"], "ELITE"),
    ("slimes", "Slimes", ["SLIME", "MAGMA_CUBE"], "SIMPLE"), ("fantasmas", "Fantasmas", ["PHANTOM"], "UNIQUE"),
    ("guardianes", "Guardianes", ["GUARDIAN"], "ELITE"), ("ahogados", "Ahogados", ["DROWNED"], "SIMPLE"),
    ("jefes", "Jefes", ["WITHER", "ENDER_DRAGON", "WARDEN", "ELDER_GUARDIAN"], "LEGENDARY"),
    ("jugadores", "Jugadores", ["PLAYER"], "ULTIMATE"),
]
CAZA = {"espada": ("Azote de", "Espadas", ["ALL_SWORD"], "ATTACK;ATTACK_MOB"),
        "hacha": ("Verdugo de", "Hachas", ["ALL_AXE"], "ATTACK;ATTACK_MOB"),
        "arco": ("Cazador de", "Arcos y ballestas", ["BOW", "CROSSBOW"], "SHOOT;SHOOT_MOB")}
for k, (pre, txt, applies, typ) in CAZA.items():
    for mid, nombre, tipos, group in MOBS:
        cond = " || ".join(f"%mob type% contains {t}" for t in tipos) + " : %allow%"
        paso = 5 if mid == "jugadores" else 10
        add(f"{pre} {nombre}", f"Mas dano contra {nombre.lower()}.", txt, typ, group, applies,
            [lv(100, [f"INCREASE_DAMAGE:{paso * l}"], conditions=[cond]) for l in (1, 2, 3)], id_=f"{k}_contra_{mid}")

# J. golpes con condicion (6 x 3 = 18)
COND = [
    ("sigiloso", "Sigiloso", "UNIQUE", "Mas dano atacando agachado.", "%player is sneaking% = true : %allow%", 15),
    ("certero", "Certero", "ELITE", "Mas dano en golpes criticos.", "%is critical% = true : %allow%", 10),
    ("nocturno", "Nocturno", "SIMPLE", "Mas dano de noche.", "%is night% = true : %allow%", 10),
    ("marino", "Marino", "UNIQUE", "Mas dano bajo el agua.", "%is under water% = true : %allow%", 15),
    ("madrugador", "Madrugador", "ELITE", "Mas dano en el primer golpe.", "%victim health percent% < 95 : %stop%", 20),
    ("encadenado", "Encadenado", "ULTIMATE", "Mas dano a partir del tercer golpe seguido.", "%combo% < 3 : %stop%", 10),
]
for k in ("espada", "hacha", "maza"):
    noun, g, txt, applies, _ = ARMAS[k]
    for cid, a, group, desc, cond, base in COND:
        add(f"{noun} {a}", desc, txt, "ATTACK;ATTACK_MOB", group, applies,
            [lv(100, [f"INCREASE_DAMAGE:{base + (base // 2 + 5) * (l - 1)}"], conditions=[cond]) for l in (1, 2, 3)],
            id_=f"{k}_{cid}")

# ---------------------------------------------------------------- armadura
PIEZAS = {"yelmo": ("Yelmo", "Cascos", ["ALL_HELMET"]), "coraza": ("Coraza", "Pecheras", ["ALL_CHESTPLATE"]),
          "grebas": ("Grebas", "Pantalones", ["ALL_LEGGINGS"]), "botas": ("Botas", "Botas", ["ALL_BOOTS"])}

# B. represalias al ser golpeado (9 x 4 = 36)
REPRESALIA = [
    ("brasas", "de Brasas", "SIMPLE", "Quema a quien te golpea.", lambda l: [f"BURN:{40 + 20 * l} @Attacker"]),
    ("escarcha", "de Escarcha", "SIMPLE", "Ralentiza a quien te golpea.", lambda l: [f"POTION:SLOW:{l - 1}:60 @Attacker"]),
    ("puas", "de Puas", "SIMPLE", "Pincha a quien te golpea.", lambda l: [f"CACTUS:{0.5 + 0.5 * l:.1f} @Attacker"]),
    ("toxina", "de Toxina", "UNIQUE", "Envenena a quien te golpea.", lambda l: [f"POTION:POISON:{l - 1}:60 @Attacker"]),
    ("niebla", "de Niebla", "ELITE", "Ciega a quien te golpea.", lambda l: [f"POTION:BLINDNESS:0:{20 + 10 * l} @Attacker"]),
    ("vendaval", "de Vendaval", "UNIQUE", "Aparta a quien te golpea.", lambda l: [f"PULL_AWAY:{0.5 + 0.3 * l:.1f} @Attacker"]),
    ("plomo", "de Plomo", "ELITE", "Debilita a quien te golpea.", lambda l: [f"POTION:WEAKNESS:{l - 1}:80 @Attacker"]),
    ("ruina", "de Ruina", "ULTIMATE", "Marchita a quien te golpea.", lambda l: [f"POTION:WITHER:0:{40 + 20 * l} @Attacker"]),
    ("vacio", "del Vacio", "LEGENDARY", "Hace levitar a quien te golpea.", lambda l: [f"POTION:LEVITATION:0:{10 + 10 * l} @Attacker"]),
]
for k, (noun, txt, applies) in PIEZAS.items():
    for rid, suf, group, desc, fx in REPRESALIA:
        add(f"{noun} {suf}", desc, txt, "DEFENSE;DEFENSE_MOB", group, applies,
            [lv(4 + 3 * (l - 1), fx(l)) for l in (1, 2, 3)], id_=f"{k}_{rid}")

# F. con poca vida (6 x 4 = 24)
RECURSO = [
    ("tenacidad", "de Tenacidad", "ELITE", "Con poca vida resistes mas.", lambda l: [f"POTION:DAMAGE_RESISTANCE:{min(l - 1, 1)}:{60 + 20 * l}"]),
    ("vitalidad", "de Vitalidad", "ELITE", "Con poca vida te regeneras.", lambda l: [f"POTION:REGENERATION:{l - 1}:{60 + 20 * l}"]),
    ("huida", "de Huida", "UNIQUE", "Con poca vida corres mas.", lambda l: [f"POTION:SPEED:{l}:{60 + 20 * l}"]),
    ("egida", "de Egida", "ULTIMATE", "Con poca vida te cubre un escudo.", lambda l: [f"POTION:ABSORPTION:{l - 1}:{160}"]),
    ("penumbra", "de Penumbra", "LEGENDARY", "Con poca vida te vuelves invisible.", lambda l: [f"POTION:INVISIBILITY:0:{40 + 20 * l}", "PARTICLE:LARGE_SMOKE:20:0.02 @Self"]),
    ("furia", "de Furia", "LEGENDARY", "Con poca vida pegas mas fuerte.", lambda l: [f"POTION:INCREASE_DAMAGE:0:{60 + 20 * l}"]),
]
for k, (noun, txt, applies) in PIEZAS.items():
    for rid, suf, group, desc, fx in RECURSO:
        add(f"{noun} {suf}", desc, txt, "DEFENSE;DEFENSE_MOB;DEFENSE_PROJECTILE", group, applies,
            [lv(100, fx(l), cooldown=70 - 10 * l, conditions=[f"%player health percent% > {25 + 5 * l} : %stop%"]) for l in (1, 2, 3)],
            id_=f"{k}_{rid}")

# K. resistencias (4 x 4 = 16)
RESIST = [
    ("muralla", "de Muralla", "DEFENSE_PROJECTILE", "Menos dano de proyectiles.", 5),
    ("bunker", "de Bunker", "EXPLOSION", "Menos dano de explosiones.", 8),
    ("salamandra", "de Salamandra", "FIRE", "Menos dano de fuego y lava.", 8),
    ("pluma", "de Pluma", "FALL_DAMAGE", "Menos dano de caidas.", 8),
]
for k, (noun, txt, applies) in PIEZAS.items():
    for rid, suf, typ, desc, step in RESIST:
        add(f"{noun} {suf}", desc, txt, typ, "SIMPLE" if step == 5 else "UNIQUE", applies,
            [lv(100, [f"DECREASE_DAMAGE:{step * l}"]) for l in (1, 2, 3)], id_=f"{k}_{rid}")

# C. bendiciones permanentes (24)
BENDICION = [
    ("SPEED", "botas", "Zancada Veloz", 2, "UNIQUE"), ("SPEED", "grebas", "Trote Ligero", 1, "SIMPLE"),
    ("SPEED", "coraza", "Sangre Agil", 1, "UNIQUE"), ("JUMP", "botas", "Brinco", 2, "SIMPLE"),
    ("JUMP", "grebas", "Resorte", 1, "SIMPLE"), ("SLOW_FALLING", "botas", "Descenso Suave", 1, "ELITE"),
    ("DOLPHINS_GRACE", "botas", "Aleta de Delfin", 1, "ULTIMATE"), ("DOLPHINS_GRACE", "grebas", "Corriente Marina", 1, "ELITE"),
    ("FAST_DIGGING", "coraza", "Brazo Minero", 2, "ELITE"), ("FAST_DIGGING", "yelmo", "Mente Obrera", 1, "UNIQUE"),
    ("FAST_DIGGING", "grebas", "Ritmo Minero", 1, "SIMPLE"), ("INCREASE_DAMAGE", "coraza", "Vigor del Titan", 1, "LEGENDARY"),
    ("DAMAGE_RESISTANCE", "coraza", "Piel de Piedra", 1, "LEGENDARY"), ("DAMAGE_RESISTANCE", "grebas", "Muslos de Hierro", 1, "ULTIMATE"),
    ("REGENERATION", "coraza", "Latido Eterno", 1, "LEGENDARY"), ("REGENERATION", "yelmo", "Mente Serena", 1, "ULTIMATE"),
    ("FIRE_RESISTANCE", "grebas", "Piel de Salamandra", 1, "ELITE"), ("FIRE_RESISTANCE", "botas", "Pasos de Ceniza", 1, "ELITE"),
    ("FIRE_RESISTANCE", "yelmo", "Corona de Brasas", 1, "ELITE"), ("LUCK", "yelmo", "Trebol de Cuatro Hojas", 2, "SIMPLE"),
    ("HERO_OF_THE_VILLAGE", "yelmo", "Heroe del Pueblo", 1, "UNIQUE"), ("CONDUIT_POWER", "yelmo", "Corazon del Mar", 1, "ULTIMATE"),
    ("WATER_BREATHING", "coraza", "Pulmones de Pez", 1, "SIMPLE"), ("WATER_BREATHING", "grebas", "Agallas", 1, "SIMPLE"),
]
for pot, pieza, nombre, maxl, group in BENDICION:
    noun, txt, applies = PIEZAS[pieza]
    dur = 400 if pot == "NIGHT_VISION" else 120
    add(nombre, f"Efecto permanente mientras lo llevas puesto ({pot.lower().replace('_', ' ')}).", txt, "EFFECT_STATIC",
        group, applies, [lv(100, [f"POTION:{pot}:{l - 1}:{dur}"]) for l in range(1, maxl + 1)])

# M. auras (6)
AURA = [
    ("Aura Ignea", "Quema a los monstruos cercanos.", "LEGENDARY", lambda r: [f"BURN:40 @Aoe{{radius={r},target=hostile}}", "PARTICLE:FLAME:10:0.02 @Self"]),
    ("Aura Gelida", "Frena a los monstruos cercanos.", "LEGENDARY", lambda r: [f"POTION:SLOW:0:60 @Aoe{{radius={r},target=hostile}}", "PARTICLE:SNOWFLAKE:10:0.02 @Self"]),
    ("Aura Toxica", "Envenena a los monstruos cercanos.", "FABLED", lambda r: [f"POTION:POISON:0:60 @Aoe{{radius={r},target=hostile}}", "PARTICLE:ITEM_SLIME:8:0.02 @Self"]),
    ("Aura Debilitante", "Debilita a los monstruos cercanos.", "ULTIMATE", lambda r: [f"POTION:WEAKNESS:0:60 @Aoe{{radius={r},target=hostile}}"]),
    ("Aura Repelente", "Aparta a los monstruos cercanos.", "ULTIMATE", lambda r: [f"PULL_AWAY:0.5 @Aoe{{radius={r},target=hostile}}"]),
    ("Aura Sagrada", "Te cura poco a poco.", "FABLED", lambda r: ["ADD_HEALTH:1", "PARTICLE:HEART:2:0.05 @Self"]),
]
for nombre, desc, group, fx in AURA:
    add(nombre, desc, "Pecheras", "REPEATING", group, ["ALL_CHESTPLATE"], [lv(100, fx(2 + l)) for l in (1, 2, 3)],
        settings={"not-applyable-with": [slug(n) for n, *_ in AURA if n != nombre]})

# ---------------------------------------------------------------- herramientas (E: 6 x 4 = 24)
HERR = {"pico": ("Punta", "Picos", ["ALL_PICKAXE"], "DIAMOND,EMERALD,GOLD_INGOT,LAPIS_LAZULI,REDSTONE"),
        "hacha": ("Hoja", "Hachas", ["ALL_AXE"], "APPLE,STICK,GOLDEN_APPLE,OAK_SAPLING"),
        "pala": ("Pala", "Palas", ["ALL_SHOVEL"], "FLINT,CLAY_BALL,GOLD_NUGGET,BONE"),
        "azada": ("Azada", "Azadas", ["ALL_HOE"], "WHEAT_SEEDS,CARROT,POTATO,BEETROOT_SEEDS,MELON_SEEDS")}
for k, (noun, txt, applies, loot) in HERR.items():
    add(f"{noun} Frenetica", "Al romper bloques a veces te da prisa minera.", txt, "MINING", "UNIQUE", applies,
        [lv(5 + 3 * l, [f"POTION:FAST_DIGGING:{l - 1}:{60 + 20 * l} @Self"]) for l in (1, 2, 3)])
    add(f"{noun} Erudita", "Experiencia extra al romper bloques.", txt, "MINING", "SIMPLE", applies,
        [lv(10 + 5 * l, [f"EXP:{l}"]) for l in (1, 2, 3)])
    add(f"{noun} Buscadora", "A veces encuentra algo util.", txt, "MINING", "ELITE", applies,
        [lv(0.5 * l, [f"DROP_ITEM:<random word>{loot}</random word>:1 @Block"], conditions=["%block natural% = false : %stop%"]) for l in (1, 2, 3)])
    add(f"{noun} Viva", "Se repara sola al trabajar.", txt, "MINING", "ULTIMATE", applies,
        [lv(10 * l, [f"ADD_DURABILITY_ITEM:{l}"]) for l in (1, 2, 3)])
    add(f"{noun} Ligera", "Al trabajar a veces corres mas.", txt, "MINING", "SIMPLE", applies,
        [lv(8 + 4 * l, [f"POTION:SPEED:{l - 1}:60 @Self"]) for l in (1, 2, 3)])
    add(f"{noun} Nutritiva", "Al trabajar a veces recuperas comida.", txt, "MINING", "UNIQUE", applies,
        [lv(3 + 2 * l, ["ADD_FOOD:1"]) for l in (1, 2, 3)])

# ---------------------------------------------------------------- pesca y elitros (N + O = 9)
add("Cebo Dorado", "Experiencia extra al pescar.", "Canas de pescar", "CATCH_FISH", "SIMPLE", ["FISHING_ROD"],
    [lv(100, [f"EXP:{2 * l}"]) for l in (1, 2, 3)])
add("Sedal Curativo", "Pescar te cura.", "Canas de pescar", "CATCH_FISH", "UNIQUE", ["FISHING_ROD"],
    [lv(40 + 20 * l, [f"ADD_HEALTH:{l + 1}"]) for l in (1, 2)])
add("Pescador Hambriento", "Pescar te llena el estomago.", "Canas de pescar", "CATCH_FISH", "SIMPLE", ["FISHING_ROD"],
    [lv(50, [f"ADD_FOOD:{l + 1}"]) for l in (1, 2)])
add("Marea de Suerte", "A veces pescas un tesoro extra.", "Canas de pescar", "CATCH_FISH", "LEGENDARY", ["FISHING_ROD"],
    [lv(2 * l, ["DROP_ITEM:<random word>NAUTILUS_SHELL,PRISMARINE_CRYSTALS,GOLD_INGOT,EMERALD,DIAMOND</random word>:1 @Self"]) for l in (1, 2, 3)])
add("Arponero", "Atrae hacia ti lo que enganchas.", "Canas de pescar", "HOOK_ENTITY", "UNIQUE", ["FISHING_ROD"],
    [lv(100, [f"PULL_CLOSER:{0.6 + 0.4 * l:.1f} @Victim"]) for l in (1, 2)])
add("Vuelo Sanador", "Volar con elitros te cura.", "Elitros", "ELYTRA_FLY", "ULTIMATE", ["ELYTRA"],
    [lv(50 + 25 * l, ["ADD_HEALTH:1"]) for l in (1, 2)])
add("Ala Dorada", "Los elitros se reparan mientras vuelas.", "Elitros", "ELYTRA_FLY", "LEGENDARY", ["ELYTRA"],
    [lv(100, [f"ADD_DURABILITY_ITEM:{l}"]) for l in (1, 2)])
add("Estela Luminosa", "Dejas una estela de chispas al volar.", "Elitros", "ELYTRA_FLY", "SIMPLE", ["ELYTRA"],
    [lv(100, ["PARTICLE:FIREWORK:10:0.05 @Self"])])
add("Ojo de Halcon", "Desde el aire ves a los monstruos brillar.", "Elitros", "ELYTRA_FLY", "ELITE", ["ELYTRA"],
    [lv(100, [f"REVEAL:{20 + 10 * l}:3"]) for l in (1, 2)])

# ---------------------------------------------------------------- maldiciones (68)
OBJ = {"espada": ("de la Espada", ["ALL_SWORD"]), "hacha": ("del Hacha", ["ALL_AXE"]), "maza": ("de la Maza", ["MACE"]),
       "arco": ("del Arco", ["BOW"]), "ballesta": ("de la Ballesta", ["CROSSBOW"]), "tridente": ("del Tridente", ["TRIDENT"]),
       "pico": ("del Pico", ["ALL_PICKAXE"]), "pala": ("de la Pala", ["ALL_SHOVEL"]), "azada": ("de la Azada", ["ALL_HOE"]),
       "yelmo": ("del Yelmo", ["ALL_HELMET"]), "coraza": ("de la Coraza", ["ALL_CHESTPLATE"]),
       "grebas": ("de las Grebas", ["ALL_LEGGINGS"]), "botas": ("de las Botas", ["ALL_BOOTS"])}
ARMOR = ["yelmo", "coraza", "grebas", "botas"]
MANO = ["espada", "hacha", "pico"]
MALD = [  # id, titulo, descripcion, objetos, (tipo por objeto), niveles
    ("pesadez", "Pesadez", "Te vuelve lento.", ARMOR + MANO,
     lambda o: "HELD" if o in MANO else "EFFECT_STATIC", lambda l: [f"POTION:SLOW:{l - 1}:120"], lambda l: 100),
    ("hambre", "Hambre", "Te da hambre constantemente.", ARMOR + MANO,
     lambda o: "HELD" if o in MANO else "EFFECT_STATIC", lambda l: [f"POTION:HUNGER:{l - 1}:120"], lambda l: 100),
    ("fragilidad", "Fragilidad", "Se desgasta solo.", ["espada", "hacha", "arco", "pico", "pala", "coraza", "botas", "tridente"],
     lambda o: "REPEATING", lambda l: [f"ADD_DURABILITY_ITEM:-{l}"], lambda l: 50),
    ("torpeza", "Torpeza", "Haces menos dano.", ["espada", "hacha", "maza"],
     lambda o: "ATTACK;ATTACK_MOB", lambda l: [f"DECREASE_DAMAGE:{10 * l}"], lambda l: 100),
    ("punteria", "Mala Punteria", "Tus disparos hacen menos dano.", ["arco", "ballesta"],
     lambda o: "SHOOT;SHOOT_MOB", lambda l: [f"DECREASE_DAMAGE:{15 * l}"], lambda l: 100),
    ("vulnerabilidad", "Vulnerabilidad", "Recibes mas dano.", ARMOR,
     lambda o: "DEFENSE;DEFENSE_MOB;DEFENSE_PROJECTILE", lambda l: [f"INCREASE_DAMAGE:{5 * l}"], lambda l: 100),
    ("resbaladizo", "Resbaladizo", "A veces se te escapa de la mano.", ["espada", "hacha", "maza"],
     lambda o: "ATTACK;ATTACK_MOB", lambda l: ["DISARM @Attacker", "MESSAGE:&c¡Se te resbalo el arma! @Attacker"], lambda l: 1.5 * l),
    ("autolesion", "Autolesion", "A veces te hieres tu.", ["espada", "hacha", "maza", "tridente"],
     lambda o: "ATTACK;ATTACK_MOB", lambda l: [f"DO_HARM:{l} @Attacker"], lambda l: 3 * l),
    ("nausea", "Nausea", "Al recibir golpes te mareas.", ARMOR,
     lambda o: "DEFENSE;DEFENSE_MOB", lambda l: [f"POTION:CONFUSION:0:{60 + 40 * l} @Victim"], lambda l: 3 * l),
    ("debilidad", "Debilidad", "Te deja sin fuerzas.", ARMOR,
     lambda o: "EFFECT_STATIC", lambda l: [f"POTION:WEAKNESS:{l - 1}:120"], lambda l: 100),
    ("fatiga", "Fatiga", "Al trabajar te cansas.", ["pico", "pala", "hacha", "azada"],
     lambda o: "MINING", lambda l: [f"POTION:SLOW_DIGGING:{l - 1}:100 @Self"], lambda l: 5 * l),
    ("infortunio", "Infortunio", "Te da mala suerte.", ARMOR,
     lambda o: "EFFECT_STATIC", lambda l: [f"POTION:UNLUCK:{l - 1}:120"], lambda l: 100),
    ("combustion", "Combustion", "Al recibir golpes a veces ardes.", ARMOR,
     lambda o: "DEFENSE;DEFENSE_MOB", lambda l: [f"BURN:{40 * l} @Victim"], lambda l: 3 * l),
    ("ingravidez", "Ingravidez", "Al recibir golpes a veces flotas.", ARMOR,
     lambda o: "DEFENSE;DEFENSE_MOB", lambda l: [f"POTION:LEVITATION:0:{20 + 10 * l} @Victim"], lambda l: 2 * l),
    ("faro", "Faro", "Brillas y todos te ven.", ARMOR,
     lambda o: "EFFECT_STATIC", lambda l: ["POTION:GLOWING:0:120"], lambda l: 100),
    ("tinieblas", "Tinieblas", "De noche se te nubla la vista.", ["yelmo", "coraza"],
     lambda o: "EFFECT_STATIC", lambda l: [f"POTION:DARKNESS:0:{60 + 40 * l}"], lambda l: 100),
]
for mid, titulo, desc, objs, tipo, fx, ch in MALD:
    for o in objs:
        gen, applies = OBJ[o]
        conds = ["%is night% = true : %allow%"] if mid == "tinieblas" else None
        add(f"{titulo} {gen}", "Maldicion: " + desc, gen.split(" ", 2)[-1].capitalize(), tipo(o), "CURSE", applies,
            [lv(ch(l), fx(l), conditions=conds) for l in (1, 2)],
            settings={"removeable": False, "disable-in-enchanter": True}, id_=f"maldicion_{mid}_{o}")

# ---------------------------------------------------------------- comprobaciones
base = yaml.safe_load(open(BASE, encoding="utf-8"))
dup = set(base) & set(E)
assert not dup, "choca con enchantments.yml: " + str(dup)
nombres = {}
for k, v in E.items():
    n = v["display"]
    assert n not in nombres, f"nombre repetido {n}: {k} y {nombres[n]}"
    nombres[n] = k
print(len(E), "encantamientos,", sum(1 for v in E.values() if v["group"] == "CURSE"), "maldiciones")
with open(OUT, "w", encoding="utf-8") as f:
    f.write("# Generado por tools/generar_encantamientos.py: 360 encantamientos por familias (incluye maldiciones).\n"
            "# Puedes editarlo a mano; si vuelves a ejecutar el script se sobrescribe.\n")
    class SinAnclas(yaml.SafeDumper):
        def ignore_aliases(self, data):
            return True
    yaml.dump(E, f, Dumper=SinAnclas, allow_unicode=True, sort_keys=False, width=250)
