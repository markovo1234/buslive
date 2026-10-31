package com.buslive.app.domain.model

/**
 * Operator ids (operator_ref / agency_id) from /gtfs_agencies/list, with an English name and a map color.
 * Colors are just a distinct palette to tell operators apart on the map (not official branding).
 */
object Operators {
    data class Info(val ref: Int, val nameHe: String, val nameEn: String, val color: Int)

    private val table = listOf(
        Info(2, "רכבת ישראל", "Israel Railways", 0xFF37474F.toInt()),
        Info(3, "אגד", "Egged", 0xFF2E7D32.toInt()),
        Info(4, "אלקטרה אפיקים תחבורה", "Electra Afikim", 0xFF6A1B9A.toInt()),
        Info(5, "דן", "Dan", 0xFF1565C0.toInt()),
        Info(6, "ש.א.מ", "S.A.M", 0xFF8D6E63.toInt()),
        Info(7, "נסיעות ותיירות", "Nesiot VeTayarut", 0xFF5D4037.toInt()),
        Info(8, "גי.בי.טורס", "GB Tours", 0xFF00838F.toInt()),
        Info(14, "נתיב אקספרס", "Nateev Express", 0xFFAD1457.toInt()),
        Info(15, "מטרופולין", "Metropoline", 0xFF0277BD.toInt()),
        Info(16, "סופרבוס", "Superbus", 0xFFC62828.toInt()),
        Info(18, "קווים", "Kavim", 0xFFEF6C00.toInt()),
        Info(20, "כרמלית", "Carmelit", 0xFF4E342E.toInt()),
        Info(21, "כפיר", "Kfir", 0xFF558B2F.toInt()),
        Info(22, "תבל", "Tevel", 0xFF00695C.toInt()),
        Info(23, "גלים", "Galim", 0xFF0097A7.toInt()),
        Info(24, "מועצה אזורית גולן", "Golan Regional Council", 0xFF827717.toInt()),
        Info(25, "אלקטרה אפיקים", "Electra Afikim", 0xFF7B1FA2.toInt()),
        Info(31, "דן בדרום", "Dan BaDarom", 0xFF283593.toInt()),
        Info(32, "דן באר שבע", "Dan Be'er Sheva", 0xFF3949AB.toInt()),
        Info(33, "כבל אקספרס", "Cable Express", 0xFF00796B.toInt()),
        Info(34, "תנופה", "Tnufa", 0xFF9E9D24.toInt()),
        Info(35, "בית שמש אקספרס", "Beit Shemesh Express", 0xFFD84315.toInt()),
        Info(37, "אקסטרה", "Extra", 0xFFF9A825.toInt()),
        Info(38, "אקסטרה ירושלים", "Extra Jerusalem", 0xFFF57F17.toInt()),
        Info(42, "ירושלים-רמאללה איחוד", "Jerusalem-Ramallah United", 0xFF6D4C41.toInt()),
        Info(44, "ירושלים-אבו-תור-ענאתא איחוד", "Jerusalem-Abu Tor-Anata United", 0xFF795548.toInt()),
        Info(45, "ירושלים-אלווסט איחוד", "Jerusalem-Al Wasat United", 0xFF6D4C41.toInt()),
        Info(49, "ירושלים - עיסאוויה מחנה שעפאט איחוד", "Jerusalem-Isawiya United", 0xFF795548.toInt()),
        Info(50, "ירושלים-דרום איחוד", "Jerusalem-South United", 0xFF6D4C41.toInt()),
        Info(51, "ירושלים-צור באהר איחוד", "Jerusalem-Tzur Baher United", 0xFF795548.toInt()),
        Info(135, "דרך אגד עוטף ירושלים", "Derech Egged Jerusalem", 0xFF1B5E20.toInt()),
    ).associateBy { it.ref }

    private const val FALLBACK = 0xFF455A64.toInt()

    fun color(ref: Int): Int = table[ref]?.color ?: FALLBACK

    fun name(ref: Int, hebrew: Boolean, fallback: String? = null): String {
        val info = table[ref] ?: return fallback ?: "#$ref"
        return if (hebrew) info.nameHe else info.nameEn
    }
}
