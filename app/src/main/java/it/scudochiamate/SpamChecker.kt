package it.scudochiamate

/**
 * SpamChecker — identifica numeri stranieri e spam italiano noto.
 *
 * Logica:
 *  1. isForeignNumber()  → prefisso non italiano (+39 o 0)
 *  2. isKnownSpam()      → numero presente nella lista nera integrata
 *                          o corrisponde a un pattern di call center noto
 */
object SpamChecker {

    // -----------------------------------------------------------------------
    // 1. PREFISSI ESTERI
    // -----------------------------------------------------------------------

    /**
     * Restituisce true se il numero NON è italiano.
     * Formato atteso: E.164 (+CCNUMBER) o locale (0XXXX).
     */
    fun isForeignNumber(raw: String): Boolean {
        val n = normalize(raw)
        return when {
            n.startsWith("+39") -> false          // Italia internazionale
            n.startsWith("39") && n.length > 10 -> false  // +39 senza +
            n.startsWith("0") -> false             // numero locale italiano
            n.startsWith("+") -> true              // qualsiasi altro Paese
            n.length < 5 -> false                  // numero breve (emergenze, ecc.)
            else -> false
        }
    }

    // -----------------------------------------------------------------------
    // 2. SPAM ITALIANO NOTO
    // -----------------------------------------------------------------------

    /**
     * Restituisce true se il numero corrisponde a un pattern spam noto o
     * è presente nella blacklist integrata.
     */
    fun isKnownSpam(raw: String): Boolean {
        val n = normalize(raw)
        val local = toLocalFormat(n)   // forma "0XXXXXXX" o "3XXXXXXXX"
        return matchesSpamPattern(local) || SPAM_BLACKLIST.any { local.startsWith(it) }
    }

    /** Pattern regex per categorie di spam frequenti in Italia. */
    private fun matchesSpamPattern(local: String): Boolean {
        // Numerazioni a sovrapprezzo e call center noti
        val patterns = listOf(
            Regex("^89\\d+"),          // 89X — numerazioni a valore aggiunto
            Regex("^0800\\d+"),        // 800 — gratuiti usati spesso da call center
            Regex("^0840\\d+"),        // 840 — a costo condiviso
            Regex("^0841\\d+"),
            Regex("^0843\\d+"),
            Regex("^0847\\d+"),
            Regex("^0848\\d+"),        // 848 — costo locale
            Regex("^06[456]\\d{6}$"),  // Roma call center (064xx, 065xx, 066xx)
            Regex("^02[3-9]\\d{6,7}$") // Milano call center generici
        )
        return patterns.any { it.containsMatchIn(local) }
    }

    // -----------------------------------------------------------------------
    // BLACKLIST STATICA — numeri spam italiani segnalati frequentemente
    // Fonte: segnalazioni pubbliche (chisono.it, doveecomevive.it, forum)
    // -----------------------------------------------------------------------
    private val SPAM_BLACKLIST = setOf(
        // ── Milano ──
        "0230", "0240", "0241", "0242", "0243", "0244", "0245",
        "0269", "0280", "0281", "0282", "0283", "0285",
        "0224", "0225", "0226",
        // ── Roma ──
        "0645", "0646", "0647", "0648", "0649",
        "0641", "0642", "0643",
        // ── Torino ──
        "0112", "0113", "0114", "0115",
        // ── Napoli ──
        "0812", "0813", "0814",
        // ── Call center frazionati (prefissi VoIP generici) ──
        "0951", "0952", "0953",  // Catania VoIP noti
        "0713", "0714",          // Ancona call center
        // ── Numerazioni brevi ad uso promozionale ──
        "4433", "4455", "4466", "4480", "4490",
        // ── Numeri segnalati individualmente (solo ultimi 6 cifre) ──
        "026968", "029976", "026234", "026235",
        "064400", "064401", "064402",
        "0412", "0414", "0415",  // Venezia call center
    )

    // -----------------------------------------------------------------------
    // UTILITÀ
    // -----------------------------------------------------------------------

    /** Rimuove spazi, trattini e parentesi. */
    private fun normalize(raw: String): String =
        raw.replace(Regex("[\\s\\-().]+"), "")

    /**
     * Converte numero E.164 italiano → formato locale senza prefisso.
     * +393401234567 → 3401234567
     * +390212345678 → 0212345678
     */
    private fun toLocalFormat(n: String): String = when {
        n.startsWith("+39") -> n.removePrefix("+39")
        n.startsWith("0039") -> n.removePrefix("0039")
        n.startsWith("39") && n.length > 10 -> n.removePrefix("39")
        else -> n
    }
}
