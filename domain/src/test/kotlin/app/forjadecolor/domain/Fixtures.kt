package app.forjadecolor.domain

/** Reads a contract fixture generated from the PWA's own code (see tools/gen-pwa-fixtures.ts). */
fun pwaFixture(name: String): String =
    checkNotNull(object {}.javaClass.getResource("/pwa/$name")) { "missing fixture $name" }.readText()
