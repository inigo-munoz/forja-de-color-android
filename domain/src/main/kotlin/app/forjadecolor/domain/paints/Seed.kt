package app.forjadecolor.domain.paints

// The Vallejo Game Color BSL set the user owns: 12 families × 4, each listed lightest → darkest as
// printed on the box. Same data as the PWA (`src/paints/seed.ts`); display colours are approximate.
// BSL roles (base / shadow / light / fourth) stay unset until confirmed from the user's book.
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
                family = PaintFamily(name = family, order = i + 1),
            )
        }
    },
    brushes = listOf(
        Brush("B02001", "Detail núm. 1"),
        Brush("B02002", "Detail núm. 2"),
        Brush("B07001", "Dry Brush Small"),
    ),
)
