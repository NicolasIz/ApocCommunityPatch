"""Genera los encantamientos nuevos de ArkEnchants 1.6.0:

  src/main/resources/enchantments-arcanos.yml   encantamientos por familias (buenos, invocaciones y maldiciones)
  src/main/resources/enchantments-astrales.yml  los Astrales (solo admins, vinculados, no se tiran ni tradean)

Entre los dos suman exactamente 3600. Nombres, descripciones y "aplica a" en espanol con acentos.

python3 tools/generar_arcanos.py   (desde la carpeta arkenchants)
"""
import re
import unicodedata
import yaml

OUT = "src/main/resources/enchantments-arcanos.yml"
OUT_ASTRAL = "src/main/resources/enchantments-astrales.yml"
OTROS = ["src/main/resources/enchantments.yml", "src/main/resources/enchantments-extra.yml"]
TOTAL = 3600

GRUPOS = ["SIMPLE", "UNIQUE", "ELITE", "ULTIMATE", "LEGENDARY", "FABLED"]
MALDITO = {"removeable": False, "disable-in-enchanter": True}


def slug(s):
    s = unicodedata.normalize("NFKD", s).encode("ascii", "ignore").decode()
    return re.sub(r"[^a-z0-9]+", "_", s.lower()).strip("_")


def grupo(r):
    return GRUPOS[max(0, min(len(GRUPOS) - 1, r))]


E = {}
A = {}


def add(name, desc, applies_to, typ, group, applies, levels, settings=None, id_=None, dest=None):
    dest = E if dest is None else dest
    i = id_ or slug(name)
    assert i not in E and i not in A, "repetido: " + i
    d = {"display": "%group-color%" + name, "description": desc, "applies-to": applies_to, "type": typ,
         "group": group, "applies": applies}
    if settings:
        d["settings"] = settings
    d["levels"] = {n + 1: lv for n, lv in enumerate(levels)}
    dest[i] = d


def lv(chance, effects, cooldown=0, conditions=None):
    d = {"chance": chance}
    if cooldown:
        d["cooldown"] = cooldown
    if conditions:
        d["conditions"] = conditions
    d["effects"] = effects
    return d


def seg(ticks):
    s = ticks / 20
    return f"{s:g}"


# ====================================================================== objetos
# clave: (del objeto, aplica a, applies, genero del sustantivo corto)
OBJ = {
    "espada": ("de la Espada", "Espadas", ["ALL_SWORD"]),
    "hacha": ("del Hacha", "Hachas", ["ALL_AXE"]),
    "maza": ("de la Maza", "Mazas", ["MACE"]),
    "tridente": ("del Tridente", "Tridentes", ["TRIDENT"]),
    "arco": ("del Arco", "Arcos", ["BOW"]),
    "ballesta": ("de la Ballesta", "Ballestas", ["CROSSBOW"]),
    "yelmo": ("del Yelmo", "Cascos", ["ALL_HELMET"]),
    "coraza": ("de la Coraza", "Pecheras", ["ALL_CHESTPLATE"]),
    "grebas": ("de las Grebas", "Pantalones", ["ALL_LEGGINGS"]),
    "botas": ("de las Botas", "Botas", ["ALL_BOOTS"]),
    "pico": ("del Pico", "Picos", ["ALL_PICKAXE"]),
    "pala": ("de la Pala", "Palas", ["ALL_SHOVEL"]),
    "azada": ("de la Azada", "Azadas", ["ALL_HOE"]),
    "hachuela": ("del Leñador", "Hachas", ["ALL_AXE"]),
    "cana": ("de la Caña", "Cañas de pescar", ["FISHING_ROD"]),
    "elitros": ("de los Élitros", "Élitros", ["ELYTRA"]),
}
MELEE = ["espada", "hacha", "maza", "tridente"]
RANGED = ["arco", "ballesta"]
ARMOR = ["yelmo", "coraza", "grebas", "botas"]
TOOLS = ["pico", "pala", "azada", "hachuela"]

# ====================================================================== elementos
# id, adjetivo m, adjetivo f, rareza base, cooldown extra, particula, verbo (con {X} = a quien afecta),
# frase de ambiente, efectos(l, T, modo) donde modo es one (un enemigo), aoe (varios), atk (quien te pega), self (tu)
ELEM = []


def elem(i, am, af, rar, cd, part, verbo, ambiente, fx, modos=("one", "aoe", "atk", "self")):
    ELEM.append(dict(id=i, m=am, f=af, rar=rar, cd=cd, part=part, verbo=verbo, amb=ambiente, fx=fx, modos=modos))


def pot(p, amp, ticks, T):
    return f"POTION:{p}:{amp}:{ticks} {T}"


elem("fuego", "Ígneo", "Ígnea", 0, 0, "FLAME", "prende fuego {X}",
     "Arde con las brasas del corazón del mundo.",
     lambda l, T, m: [f"BURN:{40 + 20 * l} {T}"])
elem("escarcha", "Gélido", "Gélida", 1, 0, "SNOWFLAKE", "ralentiza {X} con escarcha",
     "Un frío que no se derrite nunca lo recorre.",
     lambda l, T, m: [pot("SLOWNESS", min(l - 1, 2), 40 + 20 * l, T)])
elem("veneno", "Venenoso", "Venenosa", 1, 0, "ITEM_SLIME", "envenena {X}",
     "Gotea una ponzoña verde que corroe la carne.",
     lambda l, T, m: [pot("POISON", min(l - 1, 1), 60 + 20 * l, T)])
elem("ruina", "Pútrido", "Pútrida", 3, 0, "SMOKE", "marchita {X} con el efecto Wither",
     "Todo lo que toca se pudre y se vuelve ceniza.",
     lambda l, T, m: [pot("WITHER", min(l - 1, 1), 40 + 20 * l, T)])
elem("arcano", "Arcano", "Arcana", 2, 0, "WITCH", "debilita {X}",
     "Runas antiguas brillan en su superficie.",
     lambda l, T, m: [pot("WEAKNESS", min(l - 1, 1), 60 + 20 * l, T)])
elem("sombra", "Umbrío", "Umbría", 2, 4, "SQUID_INK", "ciega y frena {X}",
     "Está hecho de la sombra que queda cuando se apaga una vela.",
     lambda l, T, m: [pot("BLINDNESS", 0, 20 + 10 * l, T), pot("SLOWNESS", 0, 40, T)])
elem("trueno", "Tronante", "Tronante", 4, 6, "ELECTRIC_SPARK", "hace caer un rayo sobre {X}",
     "Huele a tormenta y chisporrotea en la oscuridad.",
     lambda l, T, m: [f"LIGHTNING:{2 + l} {T}"])
elem("viento", "Huracanado", "Huracanada", 0, 0, "CLOUD", "empuja lejos {X}",
     "Un vendaval eterno silba a su alrededor.",
     lambda l, T, m: [f"PULL_AWAY:{0.6 + 0.3 * l:.1f} {T}"], modos=("one", "aoe", "atk"))
elem("sangre", "Sanguinario", "Sanguinaria", 2, 0, "DAMAGE_INDICATOR", "hace sangrar {X} (daño directo)",
     "Nunca se limpia del todo: siempre gotea algo rojo.",
     lambda l, T, m: [f"DO_HARM:{l} {T}", f"BLOOD {T}"])
elem("vampiro", "Vampírico", "Vampírica", 3, 0, "HEART", "roba vida {X} y te la da a ti",
     "Late como un corazón cuando tiene sed.",
     lambda l, T, m: [f"DO_HARM:{l} {T}", f"ADD_HEALTH:{l} @Self"] if m == "aoe" else [f"STEAL_HEALTH:{l} @Self"],
     modos=("one", "aoe", "atk"))
elem("caos", "Caótico", "Caótica", 1, 0, "WITCH", "marea {X} (náusea)",
     "Las formas se retuercen cuando lo miras fijamente.",
     lambda l, T, m: [pot("NAUSEA", 0, 60 + 20 * l, T)])
elem("abismo", "Abisal", "Abisal", 4, 5, "REVERSE_PORTAL", "hace levitar {X}",
     "Susurra desde el vacío que hay bajo el Fin.",
     lambda l, T, m: [pot("LEVITATION", 0, 10 + 10 * l, T)])
elem("tierra", "Telúrico", "Telúrica", 1, 0, "ASH", "entorpece {X} con fatiga y lentitud",
     "Pesa como una montaña y retumba al moverse.",
     lambda l, T, m: [pot("MINING_FATIGUE", min(l - 1, 1), 60 + 20 * l, T), pot("SLOWNESS", 0, 40, T)])
elem("luz", "Radiante", "Radiante", 2, 0, "END_ROD", "ilumina {X} para que brille a través de las paredes",
     "Irradia una luz blanca que ahuyenta a los muertos.",
     lambda l, T, m: ([f"INCREASE_DAMAGE:{4 * l}"] if m == "one" else [f"DO_HARM:{l} {T}"]) + [pot("GLOWING", 0, 60 + 20 * l, T)])
elem("hambre", "Famélico", "Famélica", 0, 0, "ITEM_SLIME", "provoca hambre {X}",
     "Ruge como un estómago vacío.",
     lambda l, T, m: [pot("HUNGER", min(l - 1, 2), 80 + 20 * l, T)])
elem("tinieblas", "Tenebroso", "Tenebrosa", 3, 3, "SCULK_SOUL", "sume en tinieblas {X}",
     "Trae consigo la oscuridad de las ciudades antiguas.",
     lambda l, T, m: [pot("DARKNESS", 0, 60 + 20 * l, T)])
elem("gravedad", "Gravitatorio", "Gravitatoria", 1, 0, "PORTAL", "atrae hacia ti {X}",
     "Las piedras sueltas giran a su alrededor.",
     lambda l, T, m: [f"PULL_CLOSER:{0.5 + 0.3 * l:.1f} {T}"], modos=("one", "aoe", "atk"))
elem("tormenta", "Tempestuoso", "Tempestuosa", 5, 8, "ELECTRIC_SPARK", "lanza un rayo encadenado que salta entre enemigos",
     "Encierra una tormenta que busca salir.",
     lambda l, T, m: [f"CHAIN_LIGHTNING:{1 + l}:{2 + l}"], modos=("one", "atk"))
elem("estelar", "Estelar", "Estelar", 3, 0, "END_ROD", "golpea {X} con fuerza de estrella (daño extra)",
     "Fue forjado con el metal de una estrella caída.",
     lambda l, T, m: [f"INCREASE_DAMAGE:{8 * l}"] if m == "one" else [f"DO_HARM:{1 + l} {T}"],
     modos=("one", "aoe", "atk"))
elem("plaga", "Pestilente", "Pestilente", 4, 0, "SPORE_BLOSSOM_AIR", "infecta {X} con una plaga (veneno, hambre y debilidad)",
     "Una nube de esporas lo sigue a todas partes.",
     lambda l, T, m: [pot("POISON", 0, 60 + 20 * l, T), pot("HUNGER", 0, 100, T), pot("WEAKNESS", 0, 60 + 20 * l, T)])
elem("cristal", "Cristalino", "Cristalina", 3, 4, "WAX_OFF", "cristaliza {X}, que queda casi inmóvil",
     "Suena como el cristal al chocar.",
     lambda l, T, m: [pot("SLOWNESS", 3, 15 + 10 * l, T), pot("MINING_FATIGUE", 2, 20 + 10 * l, T)])
elem("ceniza", "Ceniciento", "Cenicienta", 3, 3, "WHITE_ASH", "cubre de ceniza ardiente {X} (fuego y ceguera)",
     "Deja un rastro de ceniza caliente al moverse.",
     lambda l, T, m: [f"BURN:{30 + 20 * l} {T}", pot("BLINDNESS", 0, 20 + 10 * l, T)])
elem("alma", "Espectral", "Espectral", 4, 4, "SOUL", "arranca el alma {X}: le quita vida y le da miedo (lentitud)",
     "Las almas que ha segado gimen dentro de él.",
     lambda l, T, m: [f"DO_HARM:{l} {T}", pot("SLOWNESS", 0, 40, T)], modos=("one", "aoe", "atk"))
elem("marea", "Marino", "Marina", 1, 0, "NAUTILUS", "arrastra {X} con la marea y lo marea",
     "Huele a sal y a tormenta en alta mar.",
     lambda l, T, m: [f"PULL_AWAY:{0.4 + 0.2 * l:.1f} {T}", pot("NAUSEA", 0, 40 + 20 * l, T)], modos=("one", "aoe", "atk"))


def fx_con_particula(e, l, T, modo):
    efectos = e["fx"](l, T, modo)
    tp = "@Self" if modo == "self" else T
    return efectos + [f"PARTICLE:{e['part']}:12:0.03 {tp}"]


# ====================================================================== 1. armas: mecánica x elemento
# id, sustantivo, genero, tipo, modo, mod rareza, (chance l), cooldown, condiciones, frase de cuando, objetos
AOE = "@Aoe{radius=%d,target=hostile}"
MEC_ARMA = [
    ("golpe", "Golpe", "m", "ATTACK;ATTACK_MOB", "one", 0, lambda l: 5 + 3 * (l - 1), 0, None,
     "Al golpear, a veces", MELEE),
    ("estallido", "Estallido", "m", "ATTACK;ATTACK_MOB", "aoe", 2, lambda l: 3 + 2 * (l - 1), 6, None,
     "Al golpear, a veces libera una onda que", MELEE),
    ("eco", "Eco", "m", "ATTACK;ATTACK_MOB", "one", 1, lambda l: 20 + 10 * (l - 1), 0,
     ["%is critical% = true : %allow%"], "Con cada golpe crítico, a veces", MELEE),
    ("furia", "Furia", "f", "ATTACK;ATTACK_MOB", "one", 1, lambda l: 25 + 10 * (l - 1), 0,
     ["%combo% < 3 : %stop%"], "A partir del tercer golpe seguido sin que te den,", MELEE),
    ("emboscada", "Emboscada", "f", "ATTACK;ATTACK_MOB", "one", 0, lambda l: 20 + 10 * (l - 1), 0,
     ["%player is sneaking% = true : %allow%"], "Atacando agachado,", MELEE),
    ("luna", "Luna", "f", "ATTACK;ATTACK_MOB", "one", 0, lambda l: 8 + 4 * (l - 1), 0,
     ["%is night% = true : %allow%"], "De noche, al golpear, a veces", MELEE),
    ("carga", "Carga", "f", "CHARGED_ATTACK", "one", 1, lambda l: 60 + 20 * (l - 1), 0, None,
     "Con el ataque cargado (mantén clic derecho y golpea),", MELEE),
    ("replica", "Réplica", "f", "DEFENSE;DEFENSE_MOB", "atk", 0, lambda l: 6 + 3 * (l - 1), 0, None,
     "Si te golpean mientras la empuñas, a veces", MELEE),
    ("requiem", "Réquiem", "m", "KILL_MOB;KILL_PLAYER", "aoe", 1, lambda l: 30 + 15 * (l - 1), 0, None,
     "Al matar, el último aliento del caído", MELEE + RANGED),
    ("ira", "Ira", "f", "ATTACK;ATTACK_MOB", "one", 1, lambda l: 15 + 10 * (l - 1), 0,
     ["%player health percent% > 40 : %stop%"], "Con menos del 40% de vida, al golpear", MELEE),
    ("ejecucion", "Ejecución", "f", "ATTACK;ATTACK_MOB;SHOOT;SHOOT_MOB", "one", 1, lambda l: 25 + 10 * (l - 1), 0,
     ["%victim health percent% > 30 : %stop%"], "Contra enemigos con menos del 30% de vida,", MELEE + RANGED),
    ("iniciativa", "Iniciativa", "f", "ATTACK;ATTACK_MOB;SHOOT;SHOOT_MOB", "one", 0, lambda l: 30 + 15 * (l - 1), 0,
     ["%victim health percent% < 95 : %stop%"], "En el primer golpe a un enemigo sano,", MELEE + RANGED),
    ("disparo", "Disparo", "m", "SHOOT;SHOOT_MOB", "one", 0, lambda l: 8 + 4 * (l - 1), 0, None,
     "Al acertar un disparo, a veces", RANGED),
    ("tiro", "Tiro", "m", "SHOOT;SHOOT_MOB", "one", 1, lambda l: 30 + 15 * (l - 1), 0,
     ["%is headshot% = true : %allow%"], "Con cada tiro a la cabeza, a veces", RANGED),
    ("acecho", "Acecho", "m", "SHOOT;SHOOT_MOB", "one", 0, lambda l: 20 + 10 * (l - 1), 0,
     ["%player is sneaking% = true : %allow%"], "Disparando agachado,", RANGED),
    ("ocaso", "Ocaso", "m", "SHOOT;SHOOT_MOB", "one", 0, lambda l: 10 + 5 * (l - 1), 0,
     ["%is night% = true : %allow%"], "De noche, al acertar, a veces", RANGED),
    ("reto", "Reto", "m", "SHOOT", "one", 1, lambda l: 10 + 5 * (l - 1), 0, None,
     "Solo contra jugadores: al acertar, a veces", RANGED),
]
QUIEN = {"one": "al enemigo", "aoe": "a los monstruos cercanos", "atk": "a quien te golpea", "self": "a ti"}


def frase(cuando, verbo_con_x):
    # "Al golpear, a veces" + "prende fuego al enemigo" -> una sola frase
    return f"{cuando} {verbo_con_x}."


for mid, noun, g, typ, modo, mod, ch, cd, conds, cuando, objs in MEC_ARMA:
    for e in ELEM:
        if modo not in e["modos"]:
            continue
        adjetivo = e["m"] if g == "m" else e["f"]
        for o in objs:
            gen, txt, applies = OBJ[o]
            T = (AOE % 4) if modo == "aoe" else "@Attacker" if modo == "atk" else "@Victim"
            desc = frase(cuando, e["verbo"].format(X=QUIEN[modo])) + " " + e["amb"]
            niveles = []
            for l in (1, 2, 3):
                Tl = (AOE % (3 + l)) if modo == "aoe" else T
                niveles.append(lv(ch(l), fx_con_particula(e, l, Tl, modo), cooldown=max(cd, e["cd"]), conditions=conds))
            add(f"{noun} {adjetivo} {gen}", desc, txt, typ, grupo(e["rar"] + mod), applies, niveles,
                id_=f"arc_{mid}_{e['id']}_{o}")

# ====================================================================== 2. armadura: mecánica x elemento
MEC_ARMOR = [
    ("replica", "Réplica", "f", "DEFENSE;DEFENSE_MOB", "atk", 0, lambda l: 4 + 2 * (l - 1), 0, None,
     "Cuando te golpean, a veces"),
    ("onda", "Onda", "f", "DEFENSE;DEFENSE_MOB;DEFENSE_PROJECTILE", "aoe", 2, lambda l: 2 + 1 * (l - 1), 8, None,
     "Cuando te golpean, a veces sale de ti una onda que"),
    ("aura", "Aura", "f", "REPEATING", "aoe", 2, lambda l: 100, 0, None,
     "Cada pocos segundos, mientras la llevas puesta,"),
    ("aliento", "Aliento", "m", "DEFENSE;DEFENSE_MOB;DEFENSE_PROJECTILE", "aoe", 1, lambda l: 100, 0,
     ["%player health percent% > 30 : %stop%"], "Cuando te queda menos del 30% de vida, tu último aliento"),
    ("castigo", "Castigo", "m", "DEFENSE_PROJECTILE", "atk", 0, lambda l: 10 + 5 * (l - 1), 0, None,
     "Cuando te alcanza un proyectil, a veces"),
    ("sello", "Sello", "m", "ATTACK;ATTACK_MOB", "one", 0, lambda l: 3 + 1 * (l - 1), 0, None,
     "Mientras la llevas puesta, al golpear a veces"),
]
for mid, noun, g, typ, modo, mod, ch, cd, conds, cuando in MEC_ARMOR:
    for e in ELEM:
        if modo not in e["modos"]:
            continue
        if mid == "aura" and e["id"] in ("trueno", "abismo", "tormenta", "cristal"):
            continue  # demasiado fuerte o molesto como aura permanente
        adjetivo = e["m"] if g == "m" else e["f"]
        for o in ARMOR:
            gen, txt, applies = OBJ[o]
            desc = frase(cuando, e["verbo"].format(X=QUIEN[modo])) + " " + e["amb"]
            niveles = []
            for l in (1, 2, 3):
                if modo == "aoe":
                    T = AOE % ((2 + l) if mid == "aura" else (3 + l))
                else:
                    T = "@Attacker" if modo == "atk" else "@Victim"
                cool = max(cd, e["cd"]) if mid != "aliento" else 60 - 10 * l
                niveles.append(lv(ch(l), fx_con_particula(e, l, T, modo), cooldown=cool, conditions=conds))
            add(f"{noun} {adjetivo} {gen}", desc, txt, typ, grupo(e["rar"] + mod), applies, niveles,
                id_=f"arc_{mid}_{e['id']}_{o}")

# ====================================================================== 3. bendiciones: mejora x momento
# id, sustantivo, genero, pocion (None = curación directa), max amplificador, texto, rareza
MEJORA = [
    ("presteza", "Presteza", "f", "SPEED", 2, "corres más rápido", 0),
    ("firmeza", "Firmeza", "f", "RESISTANCE", 1, "recibes menos daño", 3),
    ("renuevo", "Renuevo", "m", "REGENERATION", 1, "te regeneras", 2),
    ("escudo", "Escudo", "m", "ABSORPTION", 2, "ganas corazones de absorción", 2),
    ("fuerza", "Fuerza", "f", "STRENGTH", 1, "pegas más fuerte", 3),
    ("prisa", "Prisa", "f", "HASTE", 2, "golpeas y minas más rápido", 1),
    ("brinco", "Brinco", "m", "JUMP_BOOST", 2, "saltas más alto", 0),
    ("velo", "Velo", "m", "INVISIBILITY", 0, "te vuelves invisible", 3),
    ("temple", "Temple", "m", "FIRE_RESISTANCE", 0, "resistes el fuego y la lava", 1),
    ("pluma", "Pluma", "f", "SLOW_FALLING", 0, "caes despacio como una pluma", 0),
    ("saciedad", "Saciedad", "f", "SATURATION", 0, "se te quita el hambre", 1),
    ("vista", "Vista", "f", "NIGHT_VISION", 0, "ves en la oscuridad", 0),
    ("fortuna", "Fortuna", "f", "LUCK", 1, "tienes más suerte", 0),
    ("gracia", "Gracia", "f", "DOLPHINS_GRACE", 0, "nadas como un delfín", 1),
    ("branquia", "Branquia", "f", "WATER_BREATHING", 0, "respiras bajo el agua", 0),
    ("sanacion", "Sanación", "f", None, 0, "te curas al instante", 2),
    ("heroismo", "Heroísmo", "m", "HERO_OF_THE_VILLAGE", 0, "los aldeanos te hacen descuentos de héroe", 1),
]


def mejora_fx(p, maxamp, l, ticks):
    if p is None:
        return [f"ADD_HEALTH:{1 + l}", "PARTICLE:HEART:4:0.05 @Self"]
    return [f"POTION:{p}:{min(l - 1, maxamp)}:{ticks} @Self"]


# id, adjetivo m, adjetivo f, tipo, (chance l), cooldown(l), condiciones, cuando, duracion, mod rareza, para
MOMENTO_ARMOR = [
    ("defensiva", "Defensivo", "Defensiva", "DEFENSE;DEFENSE_MOB;DEFENSE_PROJECTILE", lambda l: 8 + 4 * (l - 1),
     lambda l: 12, None, "Cuando te golpean, a veces", lambda l: 60 + 20 * l, 0),
    ("desesperada", "Desesperado", "Desesperada", "DEFENSE;DEFENSE_MOB;DEFENSE_PROJECTILE", lambda l: 100,
     lambda l: 70 - 10 * l, ["%player health percent% > 35 : %stop%"], "Cuando te queda menos del 35% de vida,",
     lambda l: 80 + 20 * l, 1),
    ("triunfal", "Triunfal", "Triunfal", "KILL_MOB;KILL_PLAYER", lambda l: 20 + 10 * (l - 1), lambda l: 0, None,
     "Al matar, a veces", lambda l: 60 + 20 * l, 0),
    ("nocturna", "Nocturno", "Nocturna", "EFFECT_STATIC", lambda l: 100, lambda l: 0, ["%is night% = true : %allow%"],
     "De noche, mientras lo llevas puesto,", lambda l: 100, 1),
    ("sigilosa", "Sigiloso", "Sigilosa", "EFFECT_STATIC", lambda l: 100, lambda l: 0,
     ["%player is sneaking% = true : %allow%"], "Mientras vas agachado,", lambda l: 60, 0),
    ("acuatica", "Acuático", "Acuática", "EFFECT_STATIC", lambda l: 100, lambda l: 0,
     ["%is in water% = true : %allow%"], "Mientras estás en el agua,", lambda l: 100, 0),
    ("combativa", "Combativo", "Combativa", "ATTACK;ATTACK_MOB", lambda l: 6 + 3 * (l - 1), lambda l: 10, None,
     "Al golpear a un enemigo, a veces", lambda l: 60 + 20 * l, 0),
]
for mid, noun, g, p, maxamp, texto, rar in MEJORA:
    for tid, am, af, typ, ch, cd, conds, cuando, dur, mod in MOMENTO_ARMOR:
        if p is None and typ == "EFFECT_STATIC":
            continue  # curar sin parar seria regeneracion infinita
        if p in ("NIGHT_VISION",) and typ == "EFFECT_STATIC":
            dur = lambda l: 320
        for o in ARMOR:
            gen, txt, applies = OBJ[o]
            adjetivo = am if g == "m" else af
            desc = f"{cuando} {texto}. Una bendición grabada en la pieza por un herrero rúnico."
            niveles = [lv(ch(l), mejora_fx(p, maxamp, l, dur(l)), cooldown=cd(l), conditions=conds) for l in (1, 2, 3)]
            add(f"{noun} {adjetivo} {gen}", desc, txt, typ, grupo(rar + mod), applies, niveles,
                id_=f"arc_{mid}_{tid}_{o}")

MOMENTO_ARMA = [
    ("combativa", "Combativo", "Combativa", lambda o: "SHOOT;SHOOT_MOB" if o in RANGED else "ATTACK;ATTACK_MOB",
     lambda l: 6 + 3 * (l - 1), lambda l: 10, lambda o: None, "Al acertar a un enemigo, a veces", lambda l: 60 + 20 * l, 0),
    ("triunfal", "Triunfal", "Triunfal", lambda o: "KILL_MOB;KILL_PLAYER", lambda l: 20 + 10 * (l - 1), lambda l: 0,
     lambda o: None, "Al matar, a veces", lambda l: 60 + 20 * l, 0),
    ("certera", "Certero", "Certera", lambda o: "SHOOT;SHOOT_MOB" if o in RANGED else "ATTACK;ATTACK_MOB",
     lambda l: 35 + 15 * (l - 1), lambda l: 8,
     lambda o: ["%is headshot% = true : %allow%"] if o in RANGED else ["%is critical% = true : %allow%"],
     "Con un golpe crítico o un tiro a la cabeza, a veces", lambda l: 60 + 20 * l, 1),
    ("empunada", "Empuñado", "Empuñada", lambda o: "HELD", lambda l: 100, lambda l: 0, lambda o: None,
     "Mientras la llevas en la mano,", lambda l: 60, 1),
]
for mid, noun, g, p, maxamp, texto, rar in MEJORA:
    if mid in ("gracia", "branquia", "heroismo", "pluma"):
        continue
    for tid, am, af, typ, ch, cd, conds, cuando, dur, mod in MOMENTO_ARMA:
        if p is None and tid == "empunada":
            continue
        for o in MELEE + RANGED:
            gen, txt, applies = OBJ[o]
            adjetivo = am if g == "m" else af
            d = (lambda l: 320) if p == "NIGHT_VISION" and tid == "empunada" else dur
            desc = f"{cuando} {texto}. El arma comparte contigo la fuerza de sus victorias."
            niveles = [lv(ch(l), mejora_fx(p, maxamp, l, d(l)), cooldown=cd(l), conditions=conds(o)) for l in (1, 2, 3)]
            add(f"{noun} {adjetivo} {gen}", desc, txt, typ(o), grupo(rar + mod), applies, niveles,
                id_=f"arc_{mid}_{tid}_{o}")

# herramientas: bendiciones al trabajar
for mid, noun, g, p, maxamp, texto, rar in MEJORA:
    if mid in ("velo", "gracia", "branquia", "pluma", "fortuna", "heroismo", "brinco"):
        continue
    for o in TOOLS:
        gen, txt, applies = OBJ[o]
        adjetivo = "Laborioso" if g == "m" else "Laboriosa"
        desc = f"Al romper bloques, a veces {texto}. Una herramienta bien cuidada devuelve el cariño."
        niveles = [lv(4 + 3 * (l - 1), mejora_fx(p, maxamp, l, 80 + 20 * l), cooldown=6) for l in (1, 2, 3)]
        add(f"{noun} {adjetivo} {gen}", desc, txt, "MINING", grupo(rar), applies, niveles, id_=f"arc_{mid}_laboriosa_{o}")

# élitros: bendiciones al volar
for mid, noun, g, p, maxamp, texto, rar in MEJORA:
    if mid in ("velo", "gracia", "branquia", "heroismo", "brinco", "pluma", "prisa", "saciedad"):
        continue
    gen, txt, applies = OBJ["elitros"]
    adjetivo = "Aéreo" if g == "m" else "Aérea"
    desc = f"Mientras vuelas con élitros, {texto}. El viento sopla a tu favor."
    niveles = [lv(100 if p else 30 + 10 * l, mejora_fx(p, maxamp, l, 60), cooldown=0 if p else 6) for l in (1, 2, 3)]
    add(f"{noun} {adjetivo} {gen}", desc, txt, "ELYTRA_FLY", grupo(rar + 1), applies, niveles, id_=f"arc_{mid}_aerea_elitros")

# cañas de pescar: elemento al enganchar
for e in ELEM:
    if "one" not in e["modos"] or e["id"] in ("tormenta", "estelar", "luz"):
        continue
    gen, txt, applies = OBJ["cana"]
    desc = f"Lo que engancha tu caña queda afectado: {e['verbo'].format(X='a la presa')}. {e['amb']}"
    niveles = [lv(40 + 20 * (l - 1), fx_con_particula(e, l, "@Victim", "one"), cooldown=max(3, e["cd"])) for l in (1, 2, 3)]
    add(f"Anzuelo {e['m']}", desc, txt, "HOOK_ENTITY", grupo(e["rar"]), applies, niveles, id_=f"arc_anzuelo_{e['id']}")

# ====================================================================== 4. hallazgos (herramientas)
HALLAZGO = {
    "pico": [("DIAMOND", "Diamantes", 0.4, 3), ("EMERALD", "Esmeraldas", 0.5, 3), ("GOLD_INGOT", "Oro", 1.0, 2),
             ("IRON_INGOT", "Hierro", 1.5, 1), ("LAPIS_LAZULI", "Lapislázuli", 2.0, 0), ("REDSTONE", "Redstone", 2.0, 0),
             ("AMETHYST_SHARD", "Amatista", 1.5, 1), ("QUARTZ", "Cuarzo", 2.0, 0), ("COPPER_INGOT", "Cobre", 2.0, 0),
             ("COAL", "Carbón", 3.0, 0), ("NETHERITE_SCRAP", "Netherita", 0.05, 5), ("ECHO_SHARD", "Ecos", 0.1, 4)],
    "pala": [("FLINT", "Pedernal", 3.0, 0), ("CLAY_BALL", "Arcilla", 3.0, 0), ("GOLD_NUGGET", "Pepitas", 2.0, 1),
             ("BONE", "Huesos", 2.5, 0), ("GUNPOWDER", "Pólvora", 1.5, 1), ("SLIME_BALL", "Limo", 1.0, 1),
             ("IRON_NUGGET", "Chatarra", 2.0, 0), ("ARCHER_POTTERY_SHERD", "Reliquias", 0.2, 3)],
    "hachuela": [("APPLE", "Manzanas", 3.0, 0), ("GOLDEN_APPLE", "Manzanas Doradas", 0.2, 3), ("STICK", "Ramas", 4.0, 0),
                 ("HONEYCOMB", "Panales", 1.0, 1), ("COCOA_BEANS", "Cacao", 1.5, 0), ("GLOW_BERRIES", "Bayas Brillantes", 1.0, 1),
                 ("SWEET_BERRIES", "Bayas Dulces", 2.0, 0), ("ENCHANTED_GOLDEN_APPLE", "Manzanas Encantadas", 0.02, 5)],
    "azada": [("WHEAT_SEEDS", "Semillas", 5.0, 0), ("CARROT", "Zanahorias", 3.0, 0), ("POTATO", "Patatas", 3.0, 0),
              ("BEETROOT_SEEDS", "Remolachas", 3.0, 0), ("MELON_SEEDS", "Sandías", 2.0, 1), ("PUMPKIN_SEEDS", "Calabazas", 2.0, 1),
              ("GOLDEN_CARROT", "Zanahorias Doradas", 0.5, 2), ("GLISTERING_MELON_SLICE", "Sandías Relucientes", 0.4, 2),
              ("TORCHFLOWER_SEEDS", "Flores Antorcha", 0.3, 3), ("PITCHER_POD", "Jarras", 0.3, 3)],
}
for o, lista in HALLAZGO.items():
    gen, txt, applies = OBJ[o]
    for mat, nombre, base, rar in lista:
        desc = (f"Al romper bloques naturales, a veces encuentras {nombre.lower()} escondidos. "
                f"Cuanto más nivel, más a menudo aparecen.")
        niveles = [lv(round(base * l, 3), [f"DROP_ITEM:{mat}:1 @Block"], conditions=["%block natural% = false : %stop%"])
                   for l in (1, 2, 3)]
        add(f"Hallazgo de {nombre} {gen}", desc, txt, "MINING", grupo(rar), applies, niveles, id_=f"arc_hallazgo_{slug(mat)}_{o}")

# ====================================================================== 5. cazadores y baluartes contra criaturas
CRIATURAS = [
    ("zombis", "Zombis", ["ZOMBIE", "HUSK", "ZOMBIE_VILLAGER", "DROWNED"], 0),
    ("esqueletos", "Esqueletos", ["SKELETON", "STRAY", "BOGGED", "WITHER_SKELETON"], 0),
    ("aranas", "Arañas", ["SPIDER"], 0),
    ("creepers", "Creepers", ["CREEPER"], 1),
    ("endermans", "Endermans", ["ENDERMAN", "ENDERMITE"], 1),
    ("piglins", "Piglins", ["PIGLIN", "HOGLIN", "ZOGLIN"], 1),
    ("blazes", "Blazes", ["BLAZE"], 2),
    ("ghasts", "Ghasts", ["GHAST"], 2),
    ("brujas", "Brujas", ["WITCH"], 1),
    ("saqueadores", "Saqueadores", ["PILLAGER", "VINDICATOR", "EVOKER", "RAVAGER", "VEX", "ILLUSIONER"], 2),
    ("limos", "Limos", ["SLIME", "MAGMA_CUBE"], 0),
    ("fantasmas", "Fantasmas", ["PHANTOM"], 1),
    ("guardianes", "Guardianes", ["GUARDIAN", "ELDER_GUARDIAN"], 2),
    ("wardens", "Wardens", ["WARDEN"], 4),
    ("withers", "Withers", ["WITHER"], 4),
    ("dragones", "Dragones", ["ENDER_DRAGON"], 4),
    ("shulkers", "Shulkers", ["SHULKER"], 2),
    ("lepismas", "Lepismas", ["SILVERFISH", "ENDERMITE"], 0),
    ("breezes", "Breezes", ["BREEZE"], 2),
    ("golems", "Gólems", ["IRON_GOLEM", "SNOW_GOLEM"], 1),
    ("bestias", "Bestias", ["WOLF", "POLAR_BEAR", "PANDA", "LLAMA", "GOAT"], 0),
    ("brutos", "Brutos", ["PIGLIN_BRUTE", "ZOMBIFIED_PIGLIN"], 2),
]
for cid, nombre, tipos, rar in CRIATURAS:
    cond_v = " || ".join(f"%mob type% contains {t}" for t in tipos) + " : %allow%"
    cond_a = " || ".join(f"%attacker type% contains {t}" for t in tipos) + " : %allow%"
    for o in MELEE + RANGED:
        gen, txt, applies = OBJ[o]
        typ = "SHOOT;SHOOT_MOB" if o in RANGED else "ATTACK;ATTACK_MOB"
        desc = f"Haces mucho más daño a los {nombre.lower()}. Fue templada en la sangre de cientos de ellos."
        add(f"Perdición de {nombre} {gen}", desc, txt, typ, grupo(rar), applies,
            [lv(100, [f"INCREASE_DAMAGE:{8 * l}"], conditions=[cond_v]) for l in (1, 2, 3)], id_=f"arc_perdicion_{cid}_{o}")
    for o in ARMOR:
        gen, txt, applies = OBJ[o]
        desc = f"Recibes menos daño de los {nombre.lower()}. Conoce todos sus trucos."
        add(f"Baluarte contra {nombre} {gen}", desc, txt, "DEFENSE_MOB;DEFENSE_PROJECTILE", grupo(rar), applies,
            [lv(100, [f"DECREASE_DAMAGE:{5 * l}"], conditions=[cond_a]) for l in (1, 2, 3)], id_=f"arc_baluarte_{cid}_{o}")

# ====================================================================== 6. invocaciones aliadas (MythicMobs)
# clave en config.yml summons.creatures, nombre, genero ("el"/"la"/"los"), rareza
ALIADOS = [
    ("esqueleto_guerrero", "Guerrero Esqueleto", 2), ("esqueleto_lancero", "Lancero Esqueleto", 2),
    ("esqueleto_arquero", "Arquero Esqueleto", 2), ("esqueleto_ballestero", "Ballestero Esqueleto", 2),
    ("esqueleto_mago", "Mago Esqueleto", 3), ("esqueleto_tanque", "Coloso Esqueleto", 4),
    ("esqueleto_asesino", "Asesino Esqueleto", 3), ("caballero_esqueletico", "Caballero Esquelético", 3),
    ("esbirro_oseo", "Esbirro Óseo", 1), ("sabueso_infernal", "Sabueso Infernal", 2),
    ("diablillo_igneo", "Diablillo Ígneo", 1), ("diablillo_oscuro", "Diablillo Oscuro", 2),
    ("alma_perdida", "Alma Perdida", 1), ("esbirro_blaze", "Esbirro Blaze", 2),
    ("entling_roble", "Entling de Roble", 1), ("entling_roble_oscuro", "Entling de Roble Oscuro", 1),
    ("entling_alamo", "Entling de Álamo", 1), ("entling_infernal", "Entling Infernal", 2),
    ("ent_roble", "Ent de Roble", 4), ("bestia_corteza", "Bestia de Corteza", 4),
    ("espectro_glacial", "Espectro Glacial", 3), ("engendro_abisal", "Engendro Abisal", 2),
    ("cangrejo_sculk", "Cangrejo de Sculk", 1), ("esbirro_fuego", "Esbirro de Fuego", 3),
    ("esbirro_tormenta", "Esbirro de Tormenta", 3), ("esbirro_hielo", "Esbirro de Hielo", 3),
    ("esbirro_sombra", "Esbirro de Sombra", 3), ("esbirro_magma", "Esbirro de Magma", 3),
    ("esbirro_veneno", "Esbirro de Veneno", 3), ("lobo_espectral", "Lobo Espectral", 0),
    ("golem_guardian", "Gólem Guardián", 2),
]
ALIADO_TXT = ("Luchan a tu lado, te siguen, atacan a lo que golpeas o a lo que te ataca y jamás te hacen daño. "
              "No dejan botín y se desvanecen al acabarse el tiempo.")
for key, nombre, rar in ALIADOS:
    add(f"Pacto con {nombre}", f"Al golpear, a veces acude un {nombre} aliado. {ALIADO_TXT}",
        "Espadas, hachas, mazas y tridentes", "ATTACK;ATTACK_MOB", grupo(rar), ["ALL_SWORD", "ALL_AXE", "MACE", "TRIDENT"],
        [lv(3 + 2 * (l - 1), [f"SUMMON:{key}:{15 + 5 * l}:{1 if l < 3 else 2} @Victim"], cooldown=60 - 10 * (l - 1))
         for l in (1, 2, 3)], id_=f"arc_pacto_{key}")
    add(f"Llamada del {nombre}" if not nombre.startswith(("Alma", "Bestia")) else f"Llamada de la {nombre}",
        f"Cuando te golpean, a veces acude un {nombre} a defenderte. {ALIADO_TXT}",
        "Pecheras", "DEFENSE;DEFENSE_MOB;DEFENSE_PROJECTILE", grupo(rar), ["ALL_CHESTPLATE"],
        [lv(5 + 3 * (l - 1), [f"SUMMON:{key}:{15 + 5 * l}:{1 if l < 3 else 2} @Attacker"], cooldown=70 - 10 * (l - 1))
         for l in (1, 2, 3)], id_=f"arc_llamada_{key}")
    add(f"Socorro del {nombre}" if not nombre.startswith(("Alma", "Bestia")) else f"Socorro de la {nombre}",
        f"Cuando te queda menos del 30% de vida, acuden {nombre}s a salvarte. {ALIADO_TXT}",
        "Pantalones", "DEFENSE;DEFENSE_MOB;DEFENSE_PROJECTILE", grupo(rar + 1), ["ALL_LEGGINGS"],
        [lv(100, [f"SUMMON:{key}:{20 + 5 * l}:{1 + l // 2} @Attacker"], cooldown=150 - 30 * (l - 1),
            conditions=["%player health percent% > 30 : %stop%"]) for l in (1, 2, 3)], id_=f"arc_socorro_{key}")

# ====================================================================== 7. maldiciones (permanentes)
CUR_OBJ = MELEE + RANGED + ARMOR + ["pico", "pala", "azada"]


def cur_tipo(o):
    if o in MELEE:
        return "ATTACK;ATTACK_MOB", "Al golpear, a veces te afecta a ti"
    if o in RANGED:
        return "SHOOT;SHOOT_MOB", "Al disparar, a veces te afecta a ti"
    if o in ARMOR:
        return "DEFENSE;DEFENSE_MOB", "Cuando te golpean, a veces te afecta a ti"
    return "MINING", "Al romper bloques, a veces te afecta a ti"


NO_SELF = ("viento", "vampiro", "gravedad", "tormenta", "estelar", "alma", "marea")
for e in ELEM:
    if e["id"] in NO_SELF:
        continue
    for o in CUR_OBJ:
        gen, txt, applies = OBJ[o]
        typ, cuando = cur_tipo(o)
        verbo = e["verbo"].format(X="a ti").replace("atrae hacia ti a ti", "te atrae")
        desc = f"Maldición: {cuando.lower()[0:1]}{cuando[1:]}: {verbo}. Nada puede quitarla; solo un administrador."
        desc = desc.replace("te afecta a ti: ", "")
        niveles = [lv(3 + 2 * (l - 1), fx_con_particula(e, l, "@Self", "self")) for l in (1, 2)]
        add(f"Maldición {e['f']} {gen}", desc, txt, typ, "CURSE", applies, niveles, settings=MALDITO,
            id_=f"arc_maldicion_{e['id']}_{o}")

MALD_EXTRA = [
    ("quebradizo", "Quebradiza", "Se desgasta el doble al usarla.", lambda o: cur_tipo(o)[0],
     lambda l: [f"ADD_DURABILITY_ITEM:-{l}"], lambda l: 40, CUR_OBJ),
    ("olvido", "del Olvido", "Cuando te golpean, a veces se te desordena la barra de objetos.", lambda o: "DEFENSE;DEFENSE_MOB",
     lambda l: ["SHUFFLE_HOTBAR @Victim"], lambda l: 2 * l, ARMOR),
    ("espantajo", "del Espantajo", "Cuando te golpean, a veces te ponen una calabaza en la cabeza.",
     lambda o: "DEFENSE;DEFENSE_MOB", lambda l: ["PUMPKIN:5 @Victim"], lambda l: 2 * l, ARMOR),
    ("desnudo", "del Desnudo", "Cuando te golpean, a veces se te cae una pieza de armadura al inventario.",
     lambda o: "DEFENSE;DEFENSE_MOB", lambda l: ["REMOVE_RANDOM_ARMOR @Victim"], lambda l: 0.5 * l, ARMOR),
    ("cobardia", "de la Cobardía", "Con menos de la mitad de vida haces mucho menos daño.",
     lambda o: cur_tipo(o)[0], lambda l: [f"DECREASE_DAMAGE:{20 * l}"], lambda l: 100, MELEE + RANGED),
    ("sangria", "de la Sangría", "Cada pocos segundos pierdes un poco de vida mientras lo llevas.",
     lambda o: "REPEATING", lambda l: ["DO_HARM:1 @Self"], lambda l: 10 * l, ARMOR),
    ("eco", "del Eco", "Cada pocos segundos haces un ruido que atrae a los monstruos y brillas.",
     lambda o: "REPEATING", lambda l: ["PLAY_SOUND_OUTLOUD:ENTITY_WARDEN_HEARTBEAT:1:1", "POTION:GLOWING:0:60 @Self"],
     lambda l: 15 * l, ARMOR),
    ("avaricia", "de la Avaricia", "Al matar, pierdes experiencia en vez de ganarla.",
     lambda o: "KILL_MOB;KILL_PLAYER", lambda l: [f"EXP:-{2 * l}"], lambda l: 50, MELEE + RANGED),
    ("gula", "de la Gula", "Cada pocos segundos te entra un hambre voraz.",
     lambda o: "REPEATING", lambda l: [f"POTION:HUNGER:{l}:60 @Self"], lambda l: 25 * l, ARMOR),
    ("torpor", "del Torpor", "Mientras la empuñas, tus brazos pesan como plomo.",
     lambda o: "HELD", lambda l: [f"POTION:MINING_FATIGUE:{l - 1}:60 @Self"], lambda l: 100, MELEE + ["pico", "pala", "azada"]),
    ("miopia", "de la Miopía", "Mientras la empuñas, a veces se te nubla la vista.",
     lambda o: "HELD", lambda l: ["POTION:BLINDNESS:0:40 @Self"], lambda l: 8 * l, RANGED),
]
for mid, titulo, desc, typ, fx, ch, objs in MALD_EXTRA:
    for o in objs:
        gen, txt, applies = OBJ[o]
        conds = ["%player health percent% > 50 : %stop%"] if mid == "cobardia" else None
        add(f"Maldición {titulo} {gen}", "Maldición: " + desc + " Nada puede quitarla; solo un administrador.", txt, typ(o),
            "CURSE", applies, [lv(ch(l), fx(l), conditions=conds) for l in (1, 2)], settings=MALDITO,
            id_=f"arc_maldicion_{mid}_{o}")

# ====================================================================== 8. astrales
AST = {"removeable": False, "disable-in-enchanter": True}


def astral(name, desc, applies_to, typ, applies, levels, id_=None):
    add(name, desc, applies_to, typ, "ASTRAL", applies, levels, settings=AST, id_=id_ or "astral_" + slug(name), dest=A)


ARMAS_TODAS = ["ALL_SWORD", "ALL_AXE", "MACE", "TRIDENT"]
astral("Juicio Celestial", "Los cielos castigan a tus enemigos: rayos encadenados y un daño enorme en cada golpe.",
       "Espadas, hachas, mazas y tridentes", "ATTACK;ATTACK_MOB", ARMAS_TODAS,
       [lv(100, [f"INCREASE_DAMAGE:{30 + 15 * l}"]) for l in (1, 2, 3)])
astral("Supernova", "Al matar, el enemigo estalla como una estrella moribunda y arrasa a todos los monstruos cercanos.",
       "Armas", "KILL_MOB;KILL_PLAYER", ARMAS_TODAS + ["BOW", "CROSSBOW"],
       [lv(100, [f"DO_HARM:{6 + 3 * l} @Aoe{{radius={5 + l},target=hostile}}", f"BURN:100 @Aoe{{radius={5 + l},target=hostile}}",
                 "PARTICLE:EXPLOSION_EMITTER:1:0 @Self", "PLAY_SOUND:ENTITY_GENERIC_EXPLODE:1:1"]) for l in (1, 2, 3)])
astral("Tormenta Divina", "Cada golpe desata una cadena de rayos que salta entre todos los enemigos cercanos.",
       "Espadas, hachas, mazas y tridentes", "ATTACK;ATTACK_MOB", ARMAS_TODAS,
       [lv(25 + 10 * l, [f"CHAIN_LIGHTNING:{3 + l}:{4 + 2 * l}"], cooldown=3) for l in (1, 2, 3)])
astral("Sed del Cosmos", "Robas muchísima vida con cada golpe: el cosmos se alimenta de tus enemigos a través de ti.",
       "Espadas, hachas, mazas y tridentes", "ATTACK;ATTACK_MOB", ARMAS_TODAS,
       [lv(100, [f"STEAL_HEALTH:{1 + l} @Self", "PARTICLE:HEART:3:0.05 @Self"], cooldown=1) for l in (1, 2, 3)])
astral("Agujero Negro", "A veces abres un agujero negro: atrae, marchita y aplasta a todo lo que te rodea.",
       "Mazas y hachas", "ATTACK;ATTACK_MOB", ["MACE", "ALL_AXE"],
       [lv(15 + 5 * l, [f"PULL_CLOSER:1.2 @Aoe{{radius={6 + l},target=hostile}}",
                        f"POTION:WITHER:2:{60 + 20 * l} @Aoe{{radius={6 + l},target=hostile}}",
                        f"DO_HARM:{4 + 2 * l} @Aoe{{radius={6 + l},target=hostile}}", "PARTICLE:REVERSE_PORTAL:120:0.4 @Self",
                        "PLAY_SOUND:ENTITY_WARDEN_SONIC_BOOM:1:0.6"], cooldown=12 - 2 * l) for l in (1, 2, 3)])
astral("Legión Astral", "Al golpear, a veces se abren portales y acude una legión de guerreros esqueleto a luchar por ti.",
       "Espadas, hachas, mazas y tridentes", "ATTACK;ATTACK_MOB", ARMAS_TODAS,
       [lv(15, ["SUMMON:esqueleto_guerrero:30:2 @Victim", "SUMMON:esqueleto_arquero:30:1 @Victim",
                f"SUMMON:esqueleto_mago:30:{l // 2} @Victim"], cooldown=75 - 15 * l) for l in (1, 2, 3)])
astral("Jinete de Dragones", "Al golpear, a veces un Dragón Flamígero desciende del cielo y lucha a tu lado.",
       "Espadas, hachas, mazas y tridentes", "ATTACK;ATTACK_MOB", ARMAS_TODAS,
       [lv(8, [f"SUMMON:dragon_flamigero:{25 + 10 * l}:1 @Victim"], cooldown=200 - 40 * l) for l in (1, 2, 3)])
astral("Pacto del Wyvern", "Cuando te golpean, a veces un Wyvern Celeste baja a protegerte.",
       "Pecheras", "DEFENSE;DEFENSE_MOB;DEFENSE_PROJECTILE", ["ALL_CHESTPLATE"],
       [lv(10, [f"SUMMON:wyvern_celeste:{25 + 10 * l}:1 @Attacker"], cooldown=180 - 30 * l) for l in (1, 2, 3)])
astral("Furia del Draco", "Con poca vida, un Draco Terravore surge de la tierra y aplasta a tus enemigos.",
       "Pantalones", "DEFENSE;DEFENSE_MOB;DEFENSE_PROJECTILE", ["ALL_LEGGINGS"],
       [lv(100, [f"SUMMON:draco_terravore:{30 + 10 * l}:1 @Attacker"], cooldown=240 - 40 * l,
           conditions=["%player health percent% > 40 : %stop%"]) for l in (1, 2, 3)])
astral("Bosque Viviente", "Al matar, a veces brotan entlings y un ent de roble que te protegen durante un rato.",
       "Hachas", "KILL_MOB;KILL_PLAYER", ["ALL_AXE"],
       [lv(20 + 5 * l, [f"SUMMON:entling_roble:30:{1 + l}", f"SUMMON:ent_roble:30:{1 if l == 3 else 0}"], cooldown=60)
        for l in (1, 2, 3)])
astral("Sombras del Vacío", "Ataque cargado: te acompañan clones sombra que golpean con tu fuerza.",
       "Espadas", "CHARGED_ATTACK", ["ALL_SWORD"],
       [lv(100, [f"CLONES:{2 + l}:{10 + 5 * l}:{50 + 15 * l} @Victim"], cooldown=40 - 10 * l) for l in (1, 2, 3)])
astral("Cometa", "Tus flechas llegan como cometas: explotan en fuego y rayos sin romper bloques.",
       "Arcos y ballestas", "SHOOT;SHOOT_MOB", ["BOW", "CROSSBOW"],
       [lv(100, [f"INCREASE_DAMAGE:{30 + 15 * l}", "BURN:100 @Victim", "LIGHTNING:4 @Victim",
                 "PARTICLE:FLAME:40:0.2 @Victim"], cooldown=2) for l in (1, 2, 3)])
astral("Lluvia de Estrellas", "Al acertar, cae del cielo una lluvia de flechas sobre el objetivo.",
       "Arcos y ballestas", "SHOOT;SHOOT_MOB", ["BOW", "CROSSBOW"],
       [lv(30 + 10 * l, [f"SPAWN_ARROWS:{6 + 3 * l} @Victim"], cooldown=5) for l in (1, 2, 3)])
astral("Ojo del Cazador Astral", "Tus flechas persiguen a su presa y los tiros a la cabeza hacen un daño devastador.",
       "Arcos y ballestas", "BOW_FIRE", ["BOW", "CROSSBOW"],
       [lv(100, [f"HOMING:{0.4 + 0.1 * l:.1f}"]) for l in (1, 2, 3)])
astral("Salva Celestial", "Cada disparo sale acompañado de varias flechas en abanico.",
       "Arcos y ballestas", "BOW_FIRE", ["BOW", "CROSSBOW"], [lv(100, [f"MULTISHOT:{1 + l}:12"]) for l in (1, 2, 3)])
astral("Marea Cósmica", "El tridente arrastra al enemigo, lo ahoga en tinta y lo golpea con la fuerza del océano.",
       "Tridentes", "ATTACK;ATTACK_MOB;SHOOT;SHOOT_MOB", ["TRIDENT"],
       [lv(100, [f"INCREASE_DAMAGE:{35 + 15 * l}", "PULL_CLOSER:1 @Victim", "POTION:BLINDNESS:0:40 @Victim",
                 "PARTICLE:NAUTILUS:40:0.3 @Victim"], cooldown=1) for l in (1, 2, 3)])
astral("Égida del Firmamento", "A veces el firmamento te protege: anula el golpe por completo y te cura.",
       "Pecheras", "DEFENSE;DEFENSE_MOB;DEFENSE_PROJECTILE", ["ALL_CHESTPLATE"],
       [lv(15 + 5 * l, ["NEGATE_DAMAGE", f"ADD_HEALTH:{2 + l}", "PARTICLE:END_ROD:30:0.1 @Self"]) for l in (1, 2, 3)])
astral("Renacer Estelar", "Si recibes un golpe mortal, renaces entre estrellas con la mitad de la vida.",
       "Pecheras", "DEATH", ["ALL_CHESTPLATE"], [lv(100, ["REVIVE"], cooldown=600 - 150 * l) for l in (1, 2, 3)])
astral("Corona Estelar", "Mientras la llevas: visión nocturna, regeneración, resistencia y resistencia al fuego.",
       "Cascos", "EFFECT_STATIC", ["ALL_HELMET"],
       [lv(100, ["POTION:NIGHT_VISION:0:320", f"POTION:REGENERATION:{l - 1}:100", f"POTION:RESISTANCE:{min(l - 1, 1)}:100",
                 "POTION:FIRE_RESISTANCE:0:100"]) for l in (1, 2, 3)])
astral("Mente Cósmica", "Ves a todos los monstruos a través de las paredes y te sobra experiencia.",
       "Cascos", "REPEATING", ["ALL_HELMET"], [lv(100, [f"REVEAL:{24 + 8 * l}:3", f"EXP:{l}"]) for l in (1, 2, 3)])
astral("Alas del Cosmos", "Puedes volar libremente mientras las llevas puestas.",
       "Botas", "EFFECT_STATIC", ["ALL_BOOTS"], [lv(100, ["FLY"])])
astral("Gravedad Cero", "Doble salto, velocidad y nunca recibes daño de caída.",
       "Botas", "EFFECT_STATIC;FALL_DAMAGE", ["ALL_BOOTS"],
       [lv(100, [f"DOUBLE_JUMP:{1 + 0.2 * l:.1f}", f"POTION:SPEED:{l - 1}:100", "NEGATE_DAMAGE"]) for l in (1, 2, 3)])
astral("Pasos de Nebulosa", "Caminas sobre el agua y la lava, y dejas una estela de polvo de estrellas.",
       "Botas", "REPEATING", ["ALL_BOOTS"],
       [lv(100, ["WATER_WALKER:3", "LAVA_WALKER:3", "PARTICLE:END_ROD:4:0.02 @Self"])])
astral("Titán Estelar", "Mientras la llevas: fuerza, absorción y corazones que se regeneran.",
       "Pantalones", "EFFECT_STATIC", ["ALL_LEGGINGS"],
       [lv(100, [f"POTION:STRENGTH:{min(l - 1, 1)}:100", f"POTION:ABSORPTION:{l}:100", "POTION:REGENERATION:0:100"])
        for l in (1, 2, 3)])
astral("Reflejo del Cosmos", "Devuelves gran parte del daño que recibes a quien te golpea.",
       "Pecheras", "DEFENSE;DEFENSE_MOB;DEFENSE_PROJECTILE", ["ALL_CHESTPLATE"],
       [lv(100, [f"REFLECT:{30 + 20 * l}"]) for l in (1, 2, 3)])
astral("Guardianes del Éter", "Cuando te golpean, a veces acuden gólems guardianes y lobos espectrales a defenderte.",
       "Armadura", "DEFENSE;DEFENSE_MOB", ["ALL_ARMOR"],
       [lv(10, [f"SUMMON:golem_guardian:30:{1 + l // 3}", f"SUMMON:lobo_espectral:30:{1 + l} @Attacker"], cooldown=90 - 15 * l)
        for l in (1, 2, 3)])
astral("Paso Estelar", "Cuando te golpean, a veces te teletransportas a la espalda de tu atacante.",
       "Botas", "DEFENSE;DEFENSE_MOB", ["ALL_BOOTS"], [lv(15 + 5 * l, ["TELEPORT_BEHIND @Attacker"], cooldown=6) for l in (1, 2, 3)])
astral("Big Bang", "Rompe en un área enorme de 5x5, funde los minerales y te da más botín.",
       "Picos", "MINING", ["ALL_PICKAXE"],
       [lv(100, [f"BREAK_BLOCK @Trench{{radius={3 + 2 * (l // 2)}}}", "SMELT", f"MORE_DROPS:{l}", "TP_DROPS"],
           conditions=["%player is sneaking% = true : %stop%"]) for l in (1, 2, 3)])
astral("Veta de Estrellas", "Rompe la veta entera, triplica el botín y todo va directo al inventario.",
       "Picos", "MINING", ["ALL_PICKAXE"],
       [lv(100, ["BREAK_BLOCK @Veinmine{limit=64}", f"MORE_DROPS:{1 + l}", "TP_DROPS"],
           conditions=["%block type% !contains ORE : %stop%"]) for l in (1, 2, 3)])
astral("Árbol Cósmico", "Tala el árbol entero de un golpe y el botín va directo al inventario.",
       "Hachas", "MINING", ["ALL_AXE"], [lv(100, ["BREAK_TREE", "TP_DROPS", f"MORE_DROPS:{l}"],
                                         conditions=["%player is sneaking% = true : %stop%"]) for l in (1, 2, 3)])
astral("Cosecha Infinita", "Cosecha en un área enorme, replanta sola y multiplica la cosecha.",
       "Azadas", "MINING", ["ALL_HOE"],
       [lv(100, [f"BREAK_BLOCK @Trench{{radius={3 + 2 * (l // 2)}}}", "PLANT_SEEDS:3", f"MORE_DROPS:{l}", "TP_DROPS"],
           conditions=["%is crop% = false : %stop%"]) for l in (1, 2, 3)])
astral("Excavación Astral", "Rompe en un área enorme y todo va directo al inventario.",
       "Palas", "MINING", ["ALL_SHOVEL"],
       [lv(100, [f"BREAK_BLOCK @Trench{{radius={3 + 2 * (l // 2)}}}", "TP_DROPS", f"MORE_DROPS:{l - 1}"],
           conditions=["%player is sneaking% = true : %stop%"]) for l in (1, 2, 3)])
astral("Anzuelo de las Estrellas", "Triplica lo que pescas, recoges solo y ganas mucha experiencia.",
       "Cañas de pescar", "CATCH_FISH;BITE_HOOK", ["FISHING_ROD"],
       [lv(100, ["AUTO_REEL", "DOUBLE_CATCH:3", f"EXP:{4 * l}"]) for l in (1, 2, 3)])
astral("Estela de Andrómeda", "Volando con élitros te impulsas, te curas y los élitros se reparan solos.",
       "Élitros", "ELYTRA_FLY", ["ELYTRA"],
       [lv(100, ["ADD_HEALTH:1", f"ADD_DURABILITY_ITEM:{l}", "PARTICLE:END_ROD:6:0.02 @Self"]) for l in (1, 2, 3)])
astral("Forja Eterna", "El objeto se repara solo sin parar y nunca se pierde al morir.",
       "Armas, herramientas y armadura", "REPEATING;DEATH", ["ALL_WEAPONS", "ALL_TOOLS", "ALL_ARMOR", "BOW", "CROSSBOW"],
       [lv(100, [f"ADD_DURABILITY_ITEM:{2 * l}", "KEEP_ON_DEATH"]) for l in (1, 2, 3)])
astral("Aniquilación", "Contra enemigos con menos del 40% de vida, el golpe los remata al instante (no jefes).",
       "Espadas y hachas", "ATTACK_MOB", ["ALL_SWORD", "ALL_AXE"],
       [lv(10 + 10 * l, ["KILL @Victim", "PARTICLE:SOUL:40:0.2 @Victim"], cooldown=4,
           conditions=["%victim health percent% > 40 : %stop%",
                       "%mob type% contains WITHER || %mob type% contains ENDER_DRAGON || %mob type% contains WARDEN : %stop%"])
        for l in (1, 2, 3)])
astral("Desarme Cósmico", "Contra jugadores: a veces les arrancas el arma de las manos.",
       "Espadas", "ATTACK", ["ALL_SWORD"], [lv(5 + 5 * l, ["DISARM @Victim"], cooldown=15) for l in (1, 2, 3)])
astral("Eclipse", "De noche eres casi imparable: fuerza, velocidad y te vuelves invisible al agacharte.",
       "Pecheras", "EFFECT_STATIC", ["ALL_CHESTPLATE"],
       [lv(100, [f"POTION:STRENGTH:{min(l - 1, 1)}:100", f"POTION:SPEED:{l - 1}:100"],
           conditions=["%is night% = true : %allow%"]) for l in (1, 2, 3)])
astral("Aura de Supernova", "Quema, marchita y ciega a todos los monstruos que se acercan a ti.",
       "Pecheras", "REPEATING", ["ALL_CHESTPLATE"],
       [lv(100, [f"BURN:60 @Aoe{{radius={4 + l},target=hostile}}", f"POTION:WITHER:0:60 @Aoe{{radius={4 + l},target=hostile}}",
                 f"POTION:BLINDNESS:0:40 @Aoe{{radius={4 + l},target=hostile}}", "PARTICLE:FLAME:20:0.05 @Self"])
        for l in (1, 2, 3)])
astral("Ira del Titán", "Ataque cargado: golpeas el suelo y lanzas por los aires a todo lo que te rodea.",
       "Mazas y hachas", "CHARGED_ATTACK", ["MACE", "ALL_AXE"],
       [lv(100, [f"SLAM:{5 + l}:{8 + 4 * l}:1.2"], cooldown=10 - 2 * l) for l in (1, 2, 3)])
astral("Relámpago Errante", "Ataque cargado: te lanzas como un rayo atravesando a todos tus enemigos.",
       "Espadas", "CHARGED_ATTACK", ["ALL_SWORD"], [lv(100, [f"DASH:{8 + 2 * l}:{8 + 4 * l}"], cooldown=8 - 2 * l) for l in (1, 2, 3)])
astral("Salto Cuántico", "Clic derecho: te teletransportas hacia donde miras.",
       "Espadas", "RIGHT_CLICK", ["ALL_SWORD"], [lv(100, [f"BLINK:{10 + 5 * l}"], cooldown=12 - 3 * l) for l in (1, 2, 3)])
astral("Bumerán Estelar", "Agachado + clic derecho: lanzas el arma, que corta a la ida y a la vuelta.",
       "Hachas y tridentes", "RIGHT_CLICK", ["ALL_AXE", "TRIDENT"],
       [lv(100, [f"THROW_WEAPON:{10 + 4 * l}:{14 + 2 * l}"], cooldown=6 - l, conditions=["%player is sneaking% = false : %stop%"])
        for l in (1, 2, 3)])
astral("Bendición de la Galaxia", "Mientras la llevas: prisa, suerte y saturación permanentes.",
       "Cascos", "EFFECT_STATIC", ["ALL_HELMET"],
       [lv(100, [f"POTION:HASTE:{min(l, 2)}:100", f"POTION:LUCK:{l}:100", "POTION:SATURATION:0:40"]) for l in (1, 2, 3)])
astral("Juramento del Guardián Astral", "Con poca vida, llamas a toda tu guardia: esqueletos colosos y un gólem guardián.",
       "Pecheras", "DEFENSE;DEFENSE_MOB;DEFENSE_PROJECTILE", ["ALL_CHESTPLATE"],
       [lv(100, ["SUMMON:esqueleto_tanque:40:1 @Attacker", f"SUMMON:esqueleto_guerrero:40:{1 + l} @Attacker",
                 "SUMMON:golem_guardian:40:1 @Attacker"], cooldown=300 - 60 * l,
           conditions=["%player health percent% > 35 : %stop%"]) for l in (1, 2, 3)])

astral("Filo del Horizonte", "Cada golpe corta también a todos los monstruos que te rodean.",
       "Espadas", "ATTACK;ATTACK_MOB", ["ALL_SWORD"],
       [lv(100, [f"DO_HARM:{2 + l} @Aoe{{radius=3,target=hostile}}", "PARTICLE:SWEEP_ATTACK:3:0 @Self"], cooldown=1) for l in (1, 2, 3)])
astral("Sangre de Estrella", "Al matar te curas de golpe y ganas corazones de absorción.",
       "Armas", "KILL_MOB;KILL_PLAYER", ARMAS_TODAS + ["BOW", "CROSSBOW"],
       [lv(100, [f"ADD_HEALTH:{4 + 2 * l}", f"POTION:ABSORPTION:{l}:200 @Self"]) for l in (1, 2, 3)])
astral("Muro de Orión", "Muchas flechas y proyectiles no te hacen nada.",
       "Pecheras", "DEFENSE_PROJECTILE", ["ALL_CHESTPLATE"], [lv(40 + 15 * l, ["NEGATE_DAMAGE"]) for l in (1, 2, 3)])
astral("Pulso Gravitatorio", "Cuando te golpean, una onda de gravedad lanza lejos a todos los que te rodean.",
       "Botas", "DEFENSE;DEFENSE_MOB", ["ALL_BOOTS"],
       [lv(100, [f"PULL_AWAY:{1.5 + 0.5 * l:.1f} @Aoe{{radius=5,target=all}}", "PARTICLE:SONIC_BOOM:1:0 @Self"], cooldown=12 - 2 * l)
        for l in (1, 2, 3)])
astral("Mirada de Medusa", "Quien te golpea a veces queda petrificado unos segundos.",
       "Cascos", "DEFENSE;DEFENSE_MOB", ["ALL_HELMET"],
       [lv(15 + 5 * l, [f"POTION:SLOWNESS:5:{40 + 20 * l} @Attacker", f"POTION:MINING_FATIGUE:4:{40 + 20 * l} @Attacker",
                        "PARTICLE:WAX_OFF:30:0.1 @Attacker"], cooldown=8) for l in (1, 2, 3)])
astral("Cosecha de Almas", "Cada muerte te da almas y mucha experiencia.",
       "Espadas", "KILL_MOB;KILL_PLAYER", ["ALL_SWORD"], [lv(100, [f"ADD_SOULS:{l}", f"EXP:{5 * l}"]) for l in (1, 2, 3)])
astral("Enjambre Infernal", "Al golpear, a veces acuden sabuesos infernales y diablillos ígneos a luchar por ti.",
       "Espadas y hachas", "ATTACK;ATTACK_MOB", ["ALL_SWORD", "ALL_AXE"],
       [lv(12, [f"SUMMON:sabueso_infernal:30:{1 + l // 2} @Victim", f"SUMMON:diablillo_igneo:30:{l} @Victim"], cooldown=80 - 15 * l)
        for l in (1, 2, 3)])

# ====================================================================== ajuste a 3600 y comprobaciones
existentes = {}
for f in OTROS:
    for k, v in (yaml.safe_load(open(f, encoding="utf-8")) or {}).items():
        existentes[k] = v["display"]
todos = {**E, **A}
dup = set(existentes) & set(todos)
assert not dup, "ids que chocan: " + str(sorted(dup)[:10])
nombres = {slug(v.replace("%group-color%", "")): k for k, v in existentes.items()}
for k, v in todos.items():
    n = slug(v["display"].replace("%group-color%", ""))
    assert n not in nombres, f"nombre repetido {v['display']}: {k} y {nombres[n]}"
    nombres[n] = k

assert len(E) + len(A) == TOTAL, (len(E), len(A))

malditas = sum(1 for v in E.values() if v["group"] == "CURSE")
invoc = sum(1 for k in E if k.startswith(("arc_pacto_", "arc_llamada_", "arc_socorro_")))
print(f"arcanos: {len(E)} ({malditas} maldiciones, {invoc} invocaciones) + astrales: {len(A)} = {len(E) + len(A)}")


class SinAnclas(yaml.SafeDumper):
    def ignore_aliases(self, data):
        return True


with open(OUT, "w", encoding="utf-8") as f:
    f.write(f"# Generado por tools/generar_arcanos.py: {len(E)} encantamientos ({malditas} maldiciones permanentes,\n"
            f"# {invoc} invocaciones de aliados de MythicMobs). Puedes editarlo; si ejecutas el script se sobrescribe.\n")
    yaml.dump(E, f, Dumper=SinAnclas, allow_unicode=True, sort_keys=False, width=250)
with open(OUT_ASTRAL, "w", encoding="utf-8") as f:
    f.write(f"# Generado por tools/generar_arcanos.py: {len(A)} encantamientos Astrales.\n"
            "# Solo los da un admin (/ake give). Quedan vinculados a quien los recibe: no se tiran ni se tradean.\n")
    yaml.dump(A, f, Dumper=SinAnclas, allow_unicode=True, sort_keys=False, width=250)
