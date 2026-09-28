"""Genera src/main/resources/descripciones.yml: nombres, descripciones y "aplica a" en español para
los encantamientos que ya existían (los 55 propios, los 360 extra y los 182 de AdvancedEnchantments).

ArkEnchants lo lee desde dentro del jar en cada arranque, así los textos se corrigen también en servidores que
ya tienen sus enchantments-*.yml copiados. Los sets nuevos (arcanos y astrales) ya se generan en español.

python3 tools/generar_descripciones.py   (desde la carpeta arkenchants)
"""
import re
import yaml

OUT = "src/main/resources/descripciones.yml"

# ---------------------------------------------------------------- AdvancedEnchantments (id: nombre, descripción)
AE = {
    "harvest": ("Cosecha Amplia", "Cosecha los cultivos maduros en un área de 3x3 alrededor del que rompes."),
    "autoreel": ("Carrete Automático", "Recoge el sedal solo en cuanto pica un pez: nunca se te escapa ninguno."),
    "gemify": ("Gemificar", "A veces un mineral se convierte directamente en su bloque (diamante en bloque de diamante...)."),
    "planter": ("Sembrador", "Agachado + clic derecho con la azada: siembra semillas en un área de 3x3."),
    "potatoplanter": ("Sembrador de Patatas", "Agachado + clic derecho con la azada: planta patatas en un área de 3x3."),
    "carrotplanter": ("Sembrador de Zanahorias", "Agachado + clic derecho con la azada: planta zanahorias en un área de 3x3."),
    "replanter": ("Replantador", "Al recoger un cultivo maduro, lo vuelve a plantar solo."),
    "strike": ("Relámpago", "A veces hace caer un rayo sobre tu oponente al golpearlo o dispararle."),
    "impact": ("Impacto", "A veces el tridente hace el doble de daño."),
    "twinge": ("Punzada", "Hace sangrar al enemigo cuerpo a cuerpo: lo frena y le quita vida durante unos segundos."),
    "jellylegs": ("Piernas de Gelatina", "A veces anula por completo el daño de caída."),
    "wings": ("Alas", "Mientras llevas las botas puestas puedes volar."),
    "aquatic": ("Acuático", "Respiras bajo el agua mientras llevas el casco."),
    "smelting": ("Fundidor", "A veces los bloques que rompes salen ya fundidos."),
    "experience": ("Experiencia", "A veces los minerales te dan más experiencia."),
    "hasten": ("Apresurar", "A veces ganas prisa minera al romper bloques."),
    "haste": ("Prisa", "Mueves tus herramientas mucho más rápido mientras las empuñas."),
    "rebreather": ("Rerrespirador", "A veces recuperas aire al minar bajo el agua."),
    "glowing": ("Resplandor", "Visión nocturna permanente mientras llevas el casco."),
    "decapitation": ("Decapitación", "A veces tu oponente suelta su cabeza al morir."),
    "forcefield": ("Campo de Fuerza", "A veces empuja lejos a quien te golpea."),
    "epicness": ("Epicidad", "Tus golpes lanzan partículas y sonidos espectaculares."),
    "famine": ("Hambruna", "A veces provoca hambre a tu oponente."),
    "berserk": ("Berserker Salvaje", "A veces te da fuerza a cambio de fatiga minera."),
    "reflect": ("Reflejo", "A veces absorbe parte del daño que recibes y se lo devuelve al atacante."),
    "ward": ("Salvaguarda", "A veces absorbe el daño de un golpe enemigo."),
    "explosive": ("Explosivo", "A veces tus flechas explotan al impactar."),
    "frenzy": ("Furia de Flechas", "A veces tus flechas explotan al impactar."),
    "featherweight": ("Peso Pluma", "A veces te da un arrebato de prisa al golpear."),
    "sharpnesshook": ("Anzuelo Afilado", "El anzuelo hace daño a lo que engancha."),
    "poisonedhook": ("Anzuelo Envenenado", "El anzuelo envenena a lo que engancha."),
    "firehook": ("Anzuelo de Fuego", "El anzuelo prende fuego a lo que engancha."),
    "molten": ("Fundido", "A veces prende fuego a quien te golpea."),
    "ravenous": ("Voraz", "A veces recuperas comida mientras luchas."),
    "veinminer": ("Minero de Vetas", "Rompe la veta de mineral entera de una vez."),
    "telepathy": ("Telepatía", "Los bloques que rompes van directos a tu inventario."),
    "explosivedemise": ("Final Explosivo", "Cuando estás a punto de morir aparecen creepers que te protegen y ciegan a tu atacante."),
    "enderslayer": ("Matador del End", "Más daño contra endermans y el dragón del End."),
    "deathpunch": ("Puño Mortal", "Más daño contra zombis."),
    "bonecrusher": ("Trituradora de Huesos", "Más daño contra esqueletos."),
    "immolate": ("Inmolar", "Más daño contra arañas."),
    "slayer": ("Matarife", "Más daño contra animales pacíficos."),
    "hunter": ("Cazador", "Más daño contra animales pacíficos."),
    "beastslayer": ("Matabestias", "Más daño contra monstruos hostiles."),
    "hook": ("Gancho", "Consigues más experiencia al pescar."),
    "bait": ("Cebo", "A veces pescas el doble."),
    "snap": ("Tirón", "Atrae hacia ti a lo que engancha la caña."),
    "lucky": ("Afortunado", "A veces ganas suerte mientras pescas."),
    "permafrost": ("Permafrost", "A veces ralentiza y hace sangrar a tus oponentes."),
    "soulless": ("Desalmado", "Más daño contra monstruos hostiles."),
    "reaper": ("Parca", "A veces marchita y ciega a tu oponente mientras le haces daño."),
    "netherslayer": ("Matador del Nether", "Más daño contra las criaturas del Nether."),
    "blind": ("Ceguera", "A veces ciega a quien golpeas."),
    "allure": ("Seducción", "Tus golpes atraen a los monstruos hacia ti."),
    "frozen": ("Congelado", "Al defenderte, a veces ralentiza a tu atacante."),
    "paralyze": ("Parálisis", "Lanza un rayo y a veces deja al enemigo lento y torpe al atacar."),
    "poison": ("Veneno", "A veces envenena a quien golpeas."),
    "virus": ("Virus", "A veces envenena a quien golpeas."),
    "poisoned": ("Envenenado", "A veces envenena a quien te golpea."),
    "reforged": ("Reforjado", "Protege la durabilidad de armas y herramientas: tardan mucho más en romperse."),
    "snare": ("Trampa", "Tus proyectiles a veces ralentizan y fatigan a los enemigos."),
    "springs": ("Muelles", "Salto mejorado mientras llevas las botas."),
    "undeadruse": ("Ardid No Muerto", "Al recibir golpes, a veces aparece una horda de zombis que distrae y desorienta a tus oponentes."),
    "voodoo": ("Vudú", "A veces debilita a quien golpeas."),
    "wither": ("Marchitar", "A veces aplica el efecto Wither a quien golpeas."),
    "perish": ("Perecer", "A veces aplica el efecto Wither a quien golpeas."),
    "smokebomb": ("Bomba de Humo", "Cuando estás a punto de morir sueltas una bomba de humo que ciega a tus enemigos."),
    "infernal": ("Infernal", "Tus flechas estallan en fuego al impactar."),
    "extinguish": ("Extinguir", "A veces te apaga cuando estás ardiendo."),
    "shockwave": ("Onda de Choque", "Con poca vida, a veces empuja lejos a tu atacante."),
    "vampire": ("Vampírico", "A veces te cura hasta 3 corazones unos segundos después de golpear."),
    "greatsword": ("Mandoble", "Multiplica tu daño contra jugadores que empuñan un arco."),
    "bowmaster": ("Maestro Arquero", "Multiplica tu daño contra jugadores que empuñan una espada."),
    "hardened": ("Endurecido", "A veces recupera durabilidad al recibir golpes de jugadores."),
    "patch": ("Remiendo", "A veces recupera durabilidad al recibir golpes de monstruos."),
    "rocketescape": ("Huida Cohete", "Con poca vida sales disparado hacia el cielo."),
    "trickster": ("Embaucador", "Al recibir un golpe, a veces te teletransportas justo a la espalda de tu oponente."),
    "timber": ("Tala", "A veces tala el árbol entero de un solo golpe."),
    "distance": ("Distancia", "A veces te aleja de tus enemigos y te da regeneración."),
    "cleave": ("Hendidura", "Daña a los jugadores de alrededor; el radio crece con el nivel."),
    "ambit": ("Ámbito", "Daña a los monstruos de alrededor; el radio crece con el nivel."),
    "angelic": ("Angelical", "Te cura cuando recibes daño."),
    "arrowdeflect": ("Desvío de Flechas", "A veces evita el daño de las flechas enemigas."),
    "arrowbreak": ("Rompeflechas", "Con el arma en la mano, a veces las flechas rebotan en ti."),
    "curse": ("Maleficio", "A veces da fatiga minera a tu enemigo."),
    "diminish": ("Disminuir", "A veces da fatiga minera a tu enemigo."),
    "interrupt": ("Interrumpir", "A veces da fatiga minera a tu enemigo."),
    "exalted": ("Exaltado", "A veces te quita los efectos negativos de pociones."),
    "ragdoll": ("Muñeco de Trapo", "A veces sales despedido hacia atrás al recibir un golpe."),
    "block": ("Bloqueo", "A veces anula un ataque y devuelve hasta 4 de daño."),
    "trench": ("Zanja", "A veces rompe en un área de 3x3."),
    "tunnel": ("Túnel", "A veces excava túneles y escaleras en la dirección en la que miras."),
    "dodge": ("Esquivar", "A veces esquivas un ataque físico; más a menudo si vas agachado."),
    "fumble": ("Torpeza Ajena", "A veces hace explotar al enemigo cuando te alcanza con sus flechas."),
    "turmoil": ("Turbulencia", "A veces impide que aparezcan los guardianes de tus oponentes."),
    "guardians": ("Guardianes de Hierro", "A veces aparecen gólems de hierro que te ayudan y te vigilan."),
    "iceaspect": ("Aspecto Helado", "A veces ralentiza a tu enemigo."),
    "momentum": ("Impulso", "A veces ganas un acelerón volando con cohetes."),
    "reinforced": ("Reforzado", "Reduce el daño que recibes."),
    "restore": ("Restaurar", "Al romperse, a veces pierde este encantamiento y repara la mitad de su durabilidad."),
    "implants": ("Implantes", "A veces te devuelve comida cada pocos segundos."),
    "obsidianshield": ("Escudo de Obsidiana", "Resistencia al fuego permanente mientras lo llevas."),
    "piercing": ("Perforante", "Hace más daño."),
    "archer": ("Arquero", "Más daño con arcos."),
    "marksman": ("Tirador", "Más daño con ballestas."),
    "poseidon": ("Poseidón", "Más daño con tridentes."),
    "safeguard": ("Resguardo", "Al defenderte, a veces ganas resistencia al daño."),
    "disappear": ("Desaparecer", "Con poca vida, a veces te vuelves invisible."),
    "confuse": ("Confundir", "A veces marea a tu enemigo (náusea)."),
    "disintegrate": ("Desintegrar", "A veces desgasta mucho toda la armadura del enemigo con cada ataque."),
    "shatter": ("Quebrar", "A veces desgasta mucho toda la armadura del enemigo con cada ataque."),
    "heavy": ("Pesado", "Reduce el daño de los arcos enemigos un 2% por nivel."),
    "devour": ("Devorar", "A veces recuperas comida al matar monstruos."),
    "replenish": ("Reponer", "A veces recuperas comida mientras minas."),
    "hellfire": ("Fuego Infernal", "Tus flechas se convierten en bolas de fuego."),
    "missile": ("Misil", "Tus flechas se convierten en bolas de fuego."),
    "longbow": ("Arco Largo", "Mucho más daño contra jugadores que tienen un arco en la mano."),
    "tank": ("Tanque", "A veces reduce el daño de las hachas enemigas un 2% por nivel."),
    "swordsman": ("Espadachín", "Empuñando una espada, a veces reduces el daño recibido (hasta un 22% al nivel máximo)."),
    "critical": ("Crítico", "Aumenta el daño de los golpes críticos."),
    "rebound": ("Rebote", "Recuperas algo de vida al matar."),
    "thunderlord": ("Señor del Trueno", "Cada 3 golpes seguidos cae un rayo sobre el monstruo."),
    "netherling": ("Hijo del Nether", "Doble daño a los monstruos en el Nether."),
    "endmaster": ("Maestro del End", "Doble daño a los monstruos en el End."),
    "creeperarmor": ("Armadura Anticreeper", "A veces eres inmune a las explosiones; a niveles altos incluso te curan."),
    "spirits": ("Espíritus", "A veces aparecen blazes guardianes que te protegen."),
    "bleed": ("Desangrar", "Hace sangrar a tu oponente."),
    "lavawalker": ("Paso de Magma", "Caminas sobre la lava."),
    "waterwalker": ("Caminante del Agua", "Caminas sobre el agua."),
    "aqua": ("Aqua", "Doble daño mientras estás en el agua."),
    "judgement": ("Juicio", "A veces envenena a tu oponente y te da regeneración."),
    "divert": ("Desviar", "A veces envenena a tu oponente y te da regeneración."),
    "unholy": ("Impío", "Al defenderte, debilita y marchita a tu atacante."),
    "chaos": ("Caos", "Debilita y marchita a quien golpeas."),
    "convulse": ("Convulsión", "A veces lanza por los aires a quien te ataca."),
    "chunky": ("Robusto", "A veces recibes menos daño."),
    "barbarian": ("Bárbaro", "A veces tus hachazos hacen más daño."),
    "lucid": ("Lúcido", "Al recibir un golpe, a veces te quita la ceguera y te da visión nocturna."),
    "doublestrike": ("Doble Golpe", "A veces golpeas dos veces."),
    "gears": ("Engranajes", "Más velocidad mientras llevas las botas."),
    "inflame": ("Inflamar", "Prende fuego a todos los jugadores de alrededor."),
    "immolation": ("Inmolación", "Prende fuego a todos los monstruos de alrededor."),
    "killaura": ("Aura Asesina", "A veces mata a varios monstruos de alrededor a la vez."),
    "inquisitive": ("Inquisitivo", "A veces los monstruos sueltan más experiencia."),
    "lifesteal": ("Robavidas", "A veces robas vida al atacar."),
    "overload": ("Sobrecarga", "Te da corazones adicionales."),
    "armored": ("Acorazado", "Reduce el daño de las espadas enemigas un 2% por nivel."),
    "blacksmith": ("Herrero", "Repara tu arma a cambio de hacer algo menos de daño."),
    "immortal": ("Inmortal", "A veces evita que tu armadura pierda durabilidad."),
    "unbreakable": ("Irrompible", "Las herramientas con este encantamiento nunca se rompen."),
    "abiding": ("Perdurable", "Las armas con este encantamiento nunca se rompen."),
    "disarmor": ("Desarmadura", "A veces le quita a tu oponente una pieza de armadura al azar."),
    "striker": ("Tormenta de Flechas", "Hace llover flechas sobre tu oponente."),
    "sniper": ("Francotirador Experto", "Los tiros a la cabeza con arco hacen el doble de daño."),
    "deadshot": ("Tiro Mortal", "Los tiros a la cabeza con tridente hacen el doble de daño."),
    "soulbound": ("Vínculo de Alma", "A veces conservas el objeto al morir."),
    "neutralize": ("Neutralizar", "A veces desarma a tu oponente."),
    "disarm": ("Desarmar", "A veces desarma a tu oponente."),
    "phoenix": ("Fénix", "A veces resucitas al morir."),
    "scare": ("Susto", "A veces le cambia el casco a tu oponente por una calabaza durante un rato."),
    "strife": ("Contienda", "Aumenta el daño cuerpo a cuerpo del tridente."),
    "spark": ("Chispa", "Prende fuego a tus oponentes."),
    "bluntforce": ("Fuerza Bruta", "A veces golpeas con una fuerza tremenda."),
    "aegis": ("Égida", "A veces ganas velocidad al recibir daño de caída."),
    "plummet": ("Desplome", "Al recibir daño de caída dañas a los monstruos de alrededor."),
    "suspend": ("Suspender", "A veces tus golpes no empujan a los monstruos."),
    "nightowl": ("Búho Nocturno", "Más daño a los monstruos de noche."),
    "nightwalker": ("Caminante Nocturno", "De noche congela a los monstruos."),
    "launch": ("Lanzamiento", "Clic derecho: sales disparado hacia arriba."),
    "slingshot": ("Tirachinas", "Clic derecho: te impulsas para echar a volar."),
    "nulify": ("Anular", "A veces haces doble daño y dejas ciego al enemigo. Cuesta 40 almas por activación."),
    "rush": ("Acometida", "A veces ganas un gran acelerón al despegar con élitros. Cuesta 10 almas por activación."),
    "diploid": ("Diploide", "A veces multiplica el botín de los monstruos. Cuesta 5 almas por activación."),
    "multiplication": ("Multiplicación", "A veces multiplica lo que sueltan los minerales. Cuesta 5 almas por activación."),
    "fuddle": ("Aturullar", "Desordena la barra de objetos de tu oponente."),
    "spiritmaster": ("Maestro de Espíritus", "A veces consigues más almas al matar jugadores."),
    "axeofspirits": ("Hacha de Espíritus", "A veces consigues más almas al matar jugadores."),
    "soulminer": ("Minero de Almas", "A veces consigues almas al minar."),
    "soulgrind": ("Molienda de Almas", "A veces consigues almas al matar monstruos."),
    "deranged": ("Trastornado", "Hace caer rayos sobre los jugadores cercanos."),
    "magnet": ("Imán", "Tus golpes atraen a los jugadores hacia ti."),
    "callallies": ("Llamada a los Aliados", "Toca el cuerno de cabra: llamas a tus aliados en tu ayuda cuando estás en peligro."),
}
APLICA_AE = {
    "Armor": "Armadura", "Axe": "Hachas", "Axes": "Hachas", "Boots": "Botas", "Bow": "Arcos", "Bow, Crossbow": "Arcos y ballestas",
    "Bow, Crossbow, Trident": "Arcos, ballestas y tridentes", "Bows": "Arcos", "Chestplate": "Pecheras", "Chestplates": "Pecheras",
    "Crossbow": "Ballestas", "Elytra": "Élitros", "Fishing Rod": "Cañas de pescar", "Goat horn": "Cuerno de cabra",
    "Helmet": "Cascos", "Helmets": "Cascos", "Hoes": "Azadas", "Leggings": "Pantalones", "Pickaxe": "Picos", "Pickaxes": "Picos",
    "Pickaxes, Shovels": "Picos y palas", "Sword": "Espadas", "Swords": "Espadas", "Swords, Axes": "Espadas y hachas",
    "Swords, Bow, Crossbow, Trident": "Espadas, arcos, ballestas y tridentes", "Tools": "Herramientas", "Trident": "Tridentes",
    "Weapons": "Armas", "Weapons + Tools + Bows": "Armas, herramientas y arcos", "Weapons and tools": "Armas y herramientas",
}

# ---------------------------------------------------------------- los 55 propios, con más detalle
PROPIOS = {
    "sombras_gemelas": ("Sombras Gemelas", "Ataque cargado: invocas clones de sombra con tu aspecto que luchan a tu lado y golpean con parte de tu fuerza."),
    "tajo_del_vacio": ("Tajo del Vacío", "Ataque cargado: te lanzas hacia delante como una sombra, cortando a todo lo que encuentras a tu paso."),
    "torbellino": (None, "Ataque cargado: giras sobre ti mismo y golpeas a todos los enemigos que te rodean."),
    "ejecutor": (None, "Haces muchísimo más daño a los enemigos a los que les queda poca vida. Ideal para rematar."),
    "frenesi": ("Frenesí", "Cada golpe seguido sin que te den suma daño extra. Si te golpean, la cuenta vuelve a empezar."),
    "cadena_electrica": ("Cadena Eléctrica", "Un rayo salta de enemigo en enemigo, dañando a varios a la vez."),
    "marca_mortal": (None, "Marcas al enemigo con un brillo rojo; mientras dure la marca, recibe mucho más daño de ti."),
    "vampiro": (None, "Robas vida con cada golpe y te la quedas tú."),
    "sangrado": (None, "Hace sangrar al enemigo: pierde vida durante unos segundos y se mueve más lento."),
    "congelar": (None, "Congela al enemigo en el sitio durante un instante: no puede moverse."),
    "cegar": (None, "Lanza ceniza a los ojos del rival y lo deja ciego unos segundos."),
    "decapitar": (None, "Probabilidad de que lo que matas suelte su cabeza como trofeo."),
    "sabiduria": ("Sabiduría", "Los monstruos que matas sueltan más experiencia."),
    "devorador": (None, "Al matar, devoras el alma del caído: te curas y ganas corazones de absorción."),
    "terremoto": (None, "Ataque cargado: golpeas el suelo con tanta fuerza que lanzas a todos por los aires."),
    "hacha_bumeran": ("Bumerán", "Agachado + clic derecho: lanzas el hacha como un bumerán; corta a la ida y a la vuelta y vuelve a tu mano."),
    "berserker": (None, "Cuanta menos vida te queda, más daño haces."),
    "rompehuesos": (None, "Tus golpes aturden al enemigo y le quitan fuerza."),
    "lenador": ("Leñador", "Tala el árbol entero de un golpe (agáchate si solo quieres romper un tronco)."),
    "flecha_buscadora": (None, "Tus flechas giran en el aire y persiguen al enemigo más cercano."),
    "abanico": (None, "Disparas varias flechas a la vez en forma de abanico."),
    "flecha_explosiva": (None, "Tus flechas explotan al impactar sin romper bloques."),
    "francotirador": (None, "Los tiros a la cabeza hacen mucho más daño."),
    "lluvia_de_flechas": (None, "Al acertar, cae una lluvia de flechas del cielo sobre el objetivo."),
    "kraken": (None, "El tridente arrastra al enemigo hacia ti entre una nube de tinta."),
    "angel": ("Ángel", "Doble salto: pulsa saltar de nuevo en el aire para impulsarte otra vez."),
    "espejo": (None, "Devuelve parte del daño que recibes a quien te golpea."),
    "fantasma": (None, "Con poca vida te desvaneces en humo y reapareces lejos del peligro."),
    "escudo_de_almas": (None, "Con poca vida te envuelve un escudo de corazones de absorción."),
    "guardianes": (None, "Al recibir golpes, a veces salen gólems de hierro a defenderte."),
    "adrenalina": (None, "Con poca vida corres y saltas muchísimo más."),
    "botas_meteoro": (None, "No recibes daño de caídas grandes y al aterrizar abres un cráter que daña a los de alrededor."),
    "espinas": (None, "Quien te golpea se pincha y recibe daño."),
    "endurecer": (None, "A veces reduce el daño que recibes."),
    "ultimo_aliento": ("Último Aliento", "Te salva de la muerte una vez cada 10 minutos."),
    "alma_eterna": (None, "No pierdes este objeto al morir: vuelve contigo al reaparecer."),
    "vidente": (None, "Los monstruos cercanos brillan y los ves a través de las paredes."),
    "vision": ("Visión Nocturna", "Ves en la oscuridad mientras llevas el casco."),
    "branquias": (None, "Respiras bajo el agua mientras llevas el casco."),
    "pies_ligeros": (None, "Velocidad permanente mientras llevas las botas."),
    "saltarin": ("Saltarín", "Saltas más alto mientras llevas las botas."),
    "caminante": (None, "Caminas sobre la lava: se enfría bajo tus pies y te da resistencia al fuego."),
    "pluma": (None, "Recibes menos daño por caídas."),
    "ignifugo": ("Ignífugo", "Te apaga el fuego cuando te quemas."),
    "excavadora": (None, "Rompe en un área de 3x3 (agáchate para romper solo un bloque)."),
    "veta": (None, "Agachado, rompe la veta de mineral entera de una vez."),
    "fundicion": ("Fundición", "Los minerales salen ya fundidos en lingotes."),
    "riqueza": (None, "Probabilidad de conseguir más botín al minar."),
    "tesoro": (None, "A veces encuentras gemas escondidas en la piedra."),
    "telequinesis": (None, "Lo que rompes va directo a tu inventario."),
    "cosecha": (None, "Replanta los cultivos que recoges."),
    "autorreparacion": ("Autorreparación", "El objeto se repara solo poco a poco."),
    "anzuelo_rapido": ("Anzuelo Rápido", "Recoges el pez solo en cuanto pica."),
    "pesca_doble": (None, "Probabilidad de sacar el doble al pescar."),
    "propulsor": (None, "Volando con élitros, agáchate para darte un impulso."),
}

# ---------------------------------------------------------------- acentos y palabras en inglés del set extra
ACENTOS = [
    ("dano", "daño"), ("Dano", "Daño"), ("Aranas", "Arañas"), ("aranas", "arañas"), ("Canas", "Cañas"),
    ("Maldicion", "Maldición"), ("Igneo", "Ígneo"), ("Ignea", "Ígnea"), ("Gelido", "Gélido"), ("Gelida", "Gélida"),
    ("Sombrio", "Sombrío"), ("Sombria", "Sombría"), ("Putrido", "Pútrido"), ("Putrida", "Pútrida"), ("Arpon", "Arpón"),
    ("Gloton", "Glotón"), ("Egida", "Égida"), ("Vacio", "Vacío"), ("Puas", "Púas"), ("Bunker", "Búnker"),
    ("Delfin", "Delfín"), ("Heroe", "Héroe"), ("Trebol", "Trébol"), ("Titan", "Titán"), ("Corazon", "Corazón"),
    ("Agil", "Ágil"), ("Frenetica", "Frenética"), ("Halcon", "Halcón"), ("Elitros", "Élitros"), ("elitros", "élitros"),
    ("Toxica", "Tóxica"), ("Punteria", "Puntería"), ("Autolesion", "Autolesión"), ("Combustion", "Combustión"),
    ("Nausea", "Náusea"), ("Mas ", "Más "), (" mas ", " más "), ("caidas", "caídas"), ("criticos", "críticos"),
    ("util", "útil"), ("estomago", "estómago"), ("Pescador Hambriento", "Pescador Hambriento"),
]
POCIONES = {
    "speed": "velocidad", "jump": "salto mejorado", "slow falling": "caída lenta", "dolphins grace": "gracia del delfín",
    "fast digging": "prisa minera", "increase damage": "fuerza", "damage resistance": "resistencia al daño",
    "regeneration": "regeneración", "fire resistance": "resistencia al fuego", "luck": "suerte",
    "hero of the village": "héroe de la aldea", "conduit power": "poder del conducto", "water breathing": "respiración acuática",
}


def acentuar(s):
    for a, b in ACENTOS:
        s = re.sub(r"\b" + re.escape(a.strip()) + r"\b", b.strip(), s) if a.strip() == a else s.replace(a, b)
    return s


CUANDO = {"ATTACK;ATTACK_MOB": "Al golpear", "SHOOT;SHOOT_MOB": "Al acertar un disparo",
          "DEFENSE;DEFENSE_MOB": "Cuando te golpean", "MINING": "Al romper bloques", "CATCH_FISH": "Al pescar",
          "HOOK_ENTITY": "Al enganchar algo con la caña", "ELYTRA_FLY": "Mientras vuelas con élitros"}
APLICA_MALDICION = {"Espada": "Espadas", "Hacha": "Hachas", "Maza": "Mazas", "Arco": "Arcos", "Ballesta": "Ballestas",
                    "Tridente": "Tridentes", "Pico": "Picos", "Pala": "Palas", "Azada": "Azadas", "Yelmo": "Cascos",
                    "Coraza": "Pecheras", "Grebas": "Pantalones", "Botas": "Botas"}


def texto_extra(v):
    d = v["description"]
    cuando = CUANDO.get(v.get("type"))
    niveles = v.get("levels", {})
    azar = any(float(l.get("chance", 100)) < 100 for l in niveles.values())
    if cuando and not d.startswith(("Al ", "Con ", "Mientras", "Maldici", "Mas ", "Más ", "Menos", "A veces", "Pescar",
                                     "Volar", "Los ", "Dejas", "Desde", "Atrae", "Experiencia", "Se ")):
        d = f"{cuando}, {'a veces ' if azar else ''}{d[0].lower()}{d[1:]}"
    if d.startswith("Maldicion: "):
        d = "Maldicion: " + d[11].lower() + d[12:]
    m = re.match(r"Efecto permanente mientras lo llevas puesto \((.+)\)\.", d)
    if m:
        d = f"Mientras lo llevas puesto tienes {POCIONES.get(m.group(1), m.group(1))} permanente."
    d = acentuar(d)
    if d.startswith("Maldición: "):
        d += " Nada puede quitarla; solo un administrador."
    return d


out = {}
adv = yaml.safe_load(open("src/main/resources/enchantments-advanced.yml", encoding="utf-8"))
for k, v in adv.items():
    assert k in AE, "falta traducir " + k
    nombre, desc = AE[k]
    out[k] = {"display": nombre, "description": desc, "applies-to": APLICA_AE.get(v.get("applies-to", ""), v.get("applies-to", ""))}
faltan = set(AE) - set(adv)
assert not faltan, faltan

base = yaml.safe_load(open("src/main/resources/enchantments.yml", encoding="utf-8"))
for k, v in base.items():
    nombre, desc = PROPIOS[k]
    e = {"description": desc, "applies-to": acentuar(v.get("applies-to", ""))}
    if nombre:
        e = {"display": nombre, **e}
    out[k] = e

extra = yaml.safe_load(open("src/main/resources/enchantments-extra.yml", encoding="utf-8"))
for k, v in extra.items():
    aplica = v.get("applies-to", "")
    if v.get("group") == "CURSE":
        aplica = APLICA_MALDICION.get(aplica, aplica)
    out[k] = {"display": acentuar(v["display"].replace("%group-color%", "")), "description": texto_extra(v),
              "applies-to": acentuar(aplica)}

# nombres únicos en todo el plugin (arcanos y astrales incluidos)
vistos = {}
todos = {}
for f in ("enchantments.yml", "enchantments-extra.yml", "enchantments-advanced.yml", "enchantments-arcanos.yml",
          "enchantments-astrales.yml"):
    for k, v in yaml.safe_load(open("src/main/resources/" + f, encoding="utf-8")).items():
        todos[k] = out.get(k, {}).get("display", v["display"].replace("%group-color%", ""))
repes = []
for k, n in todos.items():
    key = re.sub(r"[^a-z0-9áéíóúñü]", "", n.lower())
    if key in vistos:
        repes.append(f"{n}: {k} y {vistos[key]}")
    vistos[key] = k
assert not repes, "nombres repetidos:\n" + "\n".join(repes)


class SinAnclas(yaml.SafeDumper):
    def ignore_aliases(self, data):
        return True


with open(OUT, "w", encoding="utf-8") as f:
    f.write("# Generado por tools/generar_descripciones.py: nombres y descripciones en español de los encantamientos\n"
            "# que ya existían (propios, extra y los de AdvancedEnchantments). Para cambiar un texto en tu servidor,\n"
            "# crea plugins/ArkEnchants/descripciones.yml con el mismo formato: lo que pongas ahí manda.\n")
    yaml.dump(out, f, Dumper=SinAnclas, allow_unicode=True, sort_keys=False, width=250)
print(len(out), "textos")
