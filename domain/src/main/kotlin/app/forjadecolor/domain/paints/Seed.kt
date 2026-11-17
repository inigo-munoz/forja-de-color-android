package app.forjadecolor.domain.paints

// The Vallejo Game Color BSL set the user owns: 12 families × 4, each listed lightest → darkest as
// printed on the box. Same data as the PWA (`src/paints/seed.ts`); display colours are approximate.
// BSL roles (base / shadow / light / fourth) come from the user's BSL book.
private val FAMILIES: List<Pair<String, List<Triple<String, String, String>>>> = listOf(
    "BSL Warm Flesh" to listOf(Triple("72.003", "Carne Pálida", "#c9a47f"), Triple("72.100", "Carne Rosada", "#e58a6b"), Triple("72.107", "Piel de Atenea", "#c85c53"), Triple("72.108", "Piel de Súcubo", "#923d45")),
    "BSL Flesh" to listOf(Triple("72.098", "Carne Élfica", "#ebd7b9"), Triple("72.099", "Piel", "#e3ac86"), Triple("72.004", "Piel de Elfo", "#cb8854"), Triple("72.071", "Piel de Bárbaro", "#926640")),
    "BSL Yellow" to listOf(Triple("72.109", "Amarillo Tóxico", "#e9d457"), Triple("72.005", "Amarillo Lunar", "#eeb800"), Triple("72.006", "Amarillo Soleado", "#f5a300"), Triple("72.007", "Amarillo Dorado", "#d47618")),
    "BSL Orange" to listOf(Triple("72.110", "Naranja Atardecer", "#f4983a"), Triple("72.008", "Naranja Fuego", "#ea5b1e"), Triple("72.009", "Naranja Tostado", "#e23a1e"), Triple("72.106", "Sangre Escarlata", "#c02321")),
    "BSL Magenta" to listOf(Triple("72.013", "Rosa Pulpo", "#c779a5"), Triple("72.014", "Púrpura", "#882451"), Triple("72.113", "Magenta Profundo", "#632543"), Triple("72.112", "Rojo Maligno", "#461e27")),
    "BSL Violet" to listOf(Triple("72.114", "Púrpura Lujurioso", "#b88bbd"), Triple("72.076", "Púrpura Alienígena", "#7160a8"), Triple("72.015", "Púrpura Hechicero", "#4b2751"), Triple("72.116", "Púrpura Medianoche", "#2c1f33")),
    "BSL Blue" to listOf(Triple("72.118", "Azul Amanecer", "#73c9ec"), Triple("72.021", "Azul Mágico", "#016ca7"), Triple("72.020", "Azul Imperial", "#272948"), Triple("72.019", "Azul Negro", "#1f2430")),
    "BSL Turquoise" to listOf(Triple("72.096", "Verdín", "#86c8a9"), Triple("72.119", "Aguamarina", "#027b74"), Triple("72.024", "Halcón Milenario", "#006261"), Triple("72.120", "Turquesa Abisal", "#1d3e55")),
    "BSL Cold Green" to listOf(Triple("72.121", "Verde Espectral", "#83c193"), Triple("72.025", "Verde Malicioso", "#02a37d"), Triple("72.026", "Verde Jade", "#007254"), Triple("72.027", "Verde Casposo", "#1f4e45")),
    "BSL Green" to listOf(Triple("72.122", "Verde Bilioso", "#d5d900"), Triple("72.032", "Verde Escorpena", "#4e8437"), Triple("72.123", "Verde Angelical", "#31502a"), Triple("72.028", "Verde Oscuro", "#1e3b26")),
    "BSL Black" to listOf(Triple("72.001", "Blanco Calavera", "#ffffff"), Triple("72.049", "Gris Muralla", "#9c9382"), Triple("72.155", "Gris Carbón", "#373639"), Triple("72.051", "Negro", "#000000")),
    "BSL Multipurpose" to listOf(Triple("72.011", "Rojo Visceral", "#7d201f"), Triple("72.042", "Piel de Parásitos", "#a35e1c"), Triple("72.124", "Marrón Gargona", "#592f22"), Triple("72.045", "Marrón Carbonizado", "#442b26")),
)

// English names from Vallejo's official Game Color chart (CC266 Rev03, Sep 2025), verified 2026-11-17. Same as the PWA.
private val ENGLISH_NAMES: Map<String, String> = mapOf(
    "72.003" to "Pale Flesh",
    "72.100" to "Rosy Flesh",
    "72.107" to "Athena Skin",
    "72.108" to "Succubus Skin",
    "72.098" to "Elfic Flesh",
    "72.099" to "Skin Tone",
    "72.004" to "Elf Skin Tone",
    "72.071" to "Barbarian Skin",
    "72.109" to "Toxic Yellow",
    "72.005" to "Moon Yellow",
    "72.006" to "Sun Yellow",
    "72.007" to "Gold Yellow",
    "72.110" to "Sunset Orange",
    "72.008" to "Orange Fire",
    "72.009" to "Hot Orange",
    "72.106" to "Scarlet Blood",
    "72.013" to "Squid Pink",
    "72.014" to "Warlord Purple",
    "72.113" to "Deep Magenta",
    "72.112" to "Evil Red",
    "72.114" to "Lustful Purple",
    "72.076" to "Alien Purple",
    "72.015" to "Hexed Lichen",
    "72.116" to "Midnight Purple",
    "72.118" to "Sunrise Blue",
    "72.021" to "Magic Blue",
    "72.020" to "Imperial Blue",
    "72.019" to "Night Blue",
    "72.096" to "Verdigris",
    "72.119" to "Aquamarine",
    "72.024" to "Turquoise",
    "72.120" to "Abyssal Turquoise",
    "72.121" to "Ghost Green",
    "72.025" to "Foul Green",
    "72.026" to "Jade Green",
    "72.027" to "Scurvy Green",
    "72.122" to "Bile Green",
    "72.032" to "Scorpy Green",
    "72.123" to "Angel Green",
    "72.028" to "Dark Green",
    "72.001" to "Dead White",
    "72.049" to "Stonewall Grey",
    "72.155" to "Charcoal",
    "72.051" to "Black",
    "72.011" to "Gory Red",
    "72.042" to "Parasite Brown",
    "72.124" to "Gorgon Brown",
    "72.045" to "Charred Brown",
)

// Roles by position (lightest → darkest). BSL Green has no shadow paint; BSL Multipurpose has no roles.
private val USUAL_ROLES = listOf(BslRole.FOURTH, BslRole.LIGHT, BslRole.BASE, BslRole.SHADOW)
private val ROLES: Map<String, List<BslRole>> = mapOf(
    "BSL Green" to listOf(BslRole.FOURTH, BslRole.LIGHT, BslRole.LIGHT, BslRole.BASE),
    "BSL Multipurpose" to emptyList(),
)

val SEED_INVENTORY: Inventory = Inventory(
    paints = FAMILIES.flatMap { (family, entries) ->
        entries.mapIndexed { i, (code, name, hex) ->
            Paint(
                brand = "Vallejo",
                code = code,
                name = name,
                range = "Game Color",
                type = PaintType.OPAQUE,
                hex = hex,
                family = PaintFamily(name = family, order = i + 1, role = (ROLES[family] ?: USUAL_ROLES).getOrNull(i)),
                nameEn = ENGLISH_NAMES[code],
            )
        }
    },
    brushes = listOf(
        Brush("B02001", "Detail núm. 1"),
        Brush("B02002", "Detail núm. 2"),
        Brush("B07001", "Dry Brush Small"),
    ),
)
