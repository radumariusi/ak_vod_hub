package io.github.radumariusi.akvodhub.parser.title

import java.util.Calendar

/**
 * Parser de nume din playlist-uri IPTV. Din `EN| The Matrix (1999) 4K HDR`, `|DE| Matrix 1999 [MULTI-SUB]`,
 * `RO - Matrix (1999) FHD` sau `Breaking Bad S01E02` scoate titlul curat, anul, sezonul/episodul,
 * calitatea, HDR, codecul, limbile audio și subtitrările ([ParsedTitle]).
 *
 * Cum lucrează:
 * 1. Normalizează textul (litere mici-superscript `ᴴᴰ` → `HD`, bare `┃│` → `|`, nume „scene” cu puncte).
 * 2. Scoate prefixul de furnizor: `EN|`, `|DE|`, `[RO]`, `RO - `, `4K-EN|`, `|NF|`…
 * 3. Caută „ancora”, primul semn clar că titlul s-a terminat: `S01E02`, un an, o etichetă sigură
 *    (`1080p`, `4K`, `HEVC`, `MULTI`…) sau o paranteză care conține doar etichete. Titlul e tot ce e înainte.
 * 4. Fără ancoră, ia de la coadă etichetele cunoscute (ex. `Matrix HD`, `Matrix - EN`).
 * 5. Interpretează etichetele: limbă, subtitrare (`SUB RO`, `VOSTFR`, `MULTI-SUB`), calitate, HDR, codec.
 *
 * Limba și calitatea se iau și din numele categoriei ([parse] cu `groupTitle`, ex. „DE | Filme 4K”),
 * dar numele intrării are prioritate.
 *
 * Kotlin pur, fără alocări mari: rulează la import pentru sute de mii de intrări.
 */
object TitleParser {
    fun parse(
        name: String,
        groupTitle: String? = null,
    ): ParsedTitle {
        val (fromName, nameInfo) = parseName(name, groupTitle)
        val group = groupTitle?.takeIf { it.isNotBlank() }?.let(::parseGroup)
        val (languages, subtitles) = applyRule(CategoryRules.mode(groupTitle), nameInfo, group)
        return fromName.copy(
            languages = languages,
            subtitles = subtitles,
            quality = fromName.quality ?: group?.quality,
            hdr = fromName.hdr ?: group?.hdr,
        )
    }

    /** Regula aleasă pentru categorie; limbile audio scrise explicit în nume au totuși prioritate. */
    private fun applyRule(
        mode: CategoryMode,
        name: Info,
        group: Info?,
    ): Pair<List<String>, List<String>> {
        val auto = resolveLanguages(name, group)
        if (mode == CategoryMode.AUTO || name.explicitLanguages.isNotEmpty()) return auto
        val section =
            (group?.sectionLanguages.orEmpty() + name.sectionLanguages + auto.second)
                .firstOrNull { it != ParsedTitle.MULTI && it != ParsedTitle.ORIGINAL }
        return when (mode) {
            CategoryMode.ORIGINAL -> {
                listOf(ParsedTitle.ORIGINAL) to (auto.second + listOfNotNull(section)).distinct()
            }

            CategoryMode.DUBBED -> {
                if (section == null) auto else listOf(section) to auto.second.filter { it != section }
            }

            CategoryMode.AUTO -> {
                auto
            }
        }
    }

    /** Piețe care subtitrează: secțiunea „RO - ” e de obicei audio original cu subtitrare în română. */
    private val subtitleMarkets = ParsedTitle.SUBTITLE_MARKETS

    /** Piețe unde sufixul de origine (`(TR)`) arată limba audio, iar secțiunea e limba subtitrării. */
    private val originMarkets = subtitleMarkets + "en"

    /**
     * Limba audio și subtitrările, din nume și categorie:
     * 1. limbi audio explicite în nume (`[EN]`, `DUBLAT`, `AUDIO RO`, după an) → acelea, plus prefixul;
     * 2. versiune originală (`VO`, `VOSTFR`) → audio original;
     * 3. categorie cu subtitrări (`ES - PELÍCULAS SUBTITLES`, `GERMANY ANIME SUBS`) → audio original;
     * 4. o singură limbă de secțiune (prefixul numelui sau categoria): într-o piață care subtitrează devine
     *    subtitrare, iar audio e țara de origine (`RO - Titlu (2022) (TR)` → turcă) sau originalul;
     * 5. altfel limba secțiunii (piețele cu dublaj: DE, FR, ES, IT, PT/BR…).
     */
    private fun resolveLanguages(
        name: Info,
        group: Info?,
    ): Pair<List<String>, List<String>> {
        val subtitles = LinkedHashSet(name.subtitles)
        if (subtitles.isEmpty() && group != null) subtitles += group.subtitles
        val origin = name.origin ?: group?.origin
        val original = ParsedTitle.ORIGINAL
        val audio: List<String> =
            when {
                name.explicitLanguages.isNotEmpty() -> {
                    (name.sectionLanguages + name.explicitLanguages).distinct()
                }

                name.original -> {
                    subtitles += name.sectionLanguages.filter { it != ParsedTitle.MULTI }
                    listOf(origin ?: original)
                }

                name.sectionLanguages.isEmpty() && group != null && group.subtitleMarker -> {
                    val regions = group.sectionLanguages.filter { it != ParsedTitle.MULTI }
                    subtitles += regions
                    when {
                        origin != null -> {
                            listOf(origin)
                        }

                        regions.isNotEmpty() || group.original || group.subtitles.any { it != ParsedTitle.MULTI } -> {
                            listOf(original)
                        }

                        else -> {
                            group.sectionLanguages.filter { it == ParsedTitle.MULTI }
                        }
                    }
                }

                else -> {
                    val section = name.sectionLanguages.ifEmpty { group?.sectionLanguages.orEmpty() }
                    val region = section.singleOrNull()
                    when {
                        section.isEmpty() -> {
                            listOfNotNull(origin ?: original.takeIf { group?.original == true })
                        }

                        region == null -> {
                            section
                        }

                        origin == region -> {
                            listOf(region)
                        }

                        origin != null && region in originMarkets -> {
                            subtitles += region
                            listOf(origin)
                        }

                        origin == null && region in subtitleMarkets -> {
                            subtitles += region
                            listOf(original)
                        }

                        else -> {
                            listOf(region)
                        }
                    }
                }
            }
        return audio to subtitles.toList()
    }

    private val maxYear = Calendar.getInstance().get(Calendar.YEAR) + 2
    private const val MIN_YEAR = 1900

    // ---------------------------------------------------------------- normalizare

    private val superscripts =
        mapOf(
            'ᴬ' to 'A',
            'ᴮ' to 'B',
            'ᶜ' to 'C',
            'ᴰ' to 'D',
            'ᴱ' to 'E',
            'ᶠ' to 'F',
            'ᴳ' to 'G',
            'ᴴ' to 'H',
            'ᴵ' to 'I',
            'ᴶ' to 'J',
            'ᴷ' to 'K',
            'ᴸ' to 'L',
            'ᴹ' to 'M',
            'ᴺ' to 'N',
            'ᴼ' to 'O',
            'ᴾ' to 'P',
            'ᴿ' to 'R',
            'ˢ' to 'S',
            'ᵀ' to 'T',
            'ᵁ' to 'U',
            'ⱽ' to 'V',
            'ᵂ' to 'W',
            'ˣ' to 'X',
            '⁰' to '0',
            '¹' to '1',
            '²' to '2',
            '³' to '3',
            '⁴' to '4',
            '⁵' to '5',
            '⁶' to '6',
            '⁷' to '7',
            '⁸' to '8',
            '⁹' to '9',
            '⁺' to '+',
            'ᵖ' to 'p',
            'ᵃ' to 'a',
            'ᵇ' to 'b',
            'ᵈ' to 'd',
            'ᵉ' to 'e',
            'ᵍ' to 'g',
            'ʰ' to 'h',
            'ⁱ' to 'i',
            'ᶦ' to 'i',
            'ʲ' to 'j',
            'ᵏ' to 'k',
            'ˡ' to 'l',
            'ᵐ' to 'm',
            'ⁿ' to 'n',
            'ᵒ' to 'o',
            'ʳ' to 'r',
            'ᵗ' to 't',
            'ᵘ' to 'u',
            'ᵛ' to 'v',
            'ʷ' to 'w',
            'ʸ' to 'y',
            'ᶻ' to 'z',
        )
    private val pipes = setOf('┃', '│', '▎', '▏', '¦', '‖', '∣', '｜', '❘', '丨')
    private val extension =
        Regex("""(?i)\.(mkv|mp4|avi|m4v|mov|wmv|mpg|mpeg|flv|webm|divx|ts|m3u8)$""")
    private val codecDot = Regex("""(?i)\b([hx])\.(26[45])\b""")
    private val multiSubtitle = Regex("""(?i)\bMULTI[-.]SUBS?\b""")
    private val dolbyVision = Regex("""(?i)\bDOLBY[\s.-]+VISI[OÓ]N\b""")
    private val declaredAudioChannels = Regex("""(?i)\b(EN|ENG)([ .-]+)5\.1\b""")
    private val audioDot = Regex("""\b(\d)\.(\d)\b""")
    private val spaces = Regex("""\s+""")

    private fun normalize(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (c in raw) {
            when {
                c in pipes -> sb.append('|')
                superscripts.containsKey(c) -> sb.append(superscripts.getValue(c))
                c == '_' || c == ' ' -> sb.append(' ')
                else -> sb.append(c)
            }
        }
        var s = extension.replace(sb.toString().trim(), "")
        s = codecDot.replace(s, "$1$2")
        s = multiSubtitle.replace(s, "MULTISUB")
        s = dolbyVision.replace(s, "DOLBYVISION")
        s = declaredAudioChannels.replace(s) { "${it.groupValues[1].uppercase()}${it.groupValues[2]}AUDIO51" }
        s = audioDot.replace(s, "$1 $2")
        // Nume „scene” (The.Matrix.1999.1080p): punctele sunt separatori.
        if (' ' !in s && s.count { it == '.' } >= 2) s = s.replace('.', ' ')
        return spaces.replace(s, " ").trim()
    }

    // ---------------------------------------------------------------- etichete

    /** Unde a apărut un cuvânt: contează la cât de sigur e că e etichetă și nu parte din titlu. */
    private enum class Context {
        /** Prefixul de furnizor sau o paranteză: orice cod cunoscut e etichetă. */
        TAGGED,

        /** După ancoră: coduri de limbă doar cu majuscule. */
        AFTER_ANCHOR,

        /** La coada unui nume fără ancoră: doar etichete scrise cu majuscule (sau calitate). */
        TAIL,

        /** Numele categoriei. */
        GROUP,
    }

    private sealed interface Tag {
        /** [section] = limba unei secțiuni (prefix, nume de țară, categorie), nu neapărat a pistei audio. */
        data class Language(
            val iso: String,
            val section: Boolean = false,
        ) : Tag

        /** [original] = versiune originală subtitrată (`VOSTFR`, `VOSE`). */
        data class SubtitleLanguage(
            val iso: String,
            val original: Boolean = false,
        ) : Tag

        /** Țara de origine după an, ex. `(TR)`: limba audio originală. */
        data class Origin(
            val iso: String,
        ) : Tag

        /** Versiune originală (`VO`, `OV`, `ORIGINAL`). */
        data object Original : Tag

        data object SubtitleWord : Tag

        data object Multi : Tag

        data object MultiSub : Tag

        data class Quality(
            val value: VideoQuality,
        ) : Tag

        data class Hdr(
            val value: HdrFormat,
        ) : Tag

        data class Codec(
            val value: VideoCodec,
        ) : Tag

        data object Noise : Tag

        data object Platform : Tag
    }

    private val noiseNotInTail = setOf("CUT", "RAY", "VISION", "NEW", "DOLBY", "RAW", "VIP", "DUB", "DL", "TC", "WEB")

    private fun classify(
        token: String,
        context: Context,
    ): Tag? {
        val upper = token.uppercase()
        val up = if (upper.endsWith('+') && upper !in TitleTags.hdr) upper.trimEnd('+') else upper
        val hasLower = token.any { it.isLowerCase() }
        val isDigits = token.all { it.isDigit() }
        if (isDigits && context == Context.TAIL) return null
        if (up == "AUDIO51") return Tag.Noise
        if (token.first().isDigit() && ('X' in up || '×' in up)) {
            VideoResolution.declared(up)?.let { return Tag.Quality(it) }
        }
        TitleTags.quality[up]?.let {
            if (!isDigits || context != Context.GROUP) return Tag.Quality(it)
        }
        TitleTags.hdr[up]?.let { if (up != "DV" || !hasLower) return Tag.Hdr(it) }
        TitleTags.codec[up]?.let { return Tag.Codec(it) }
        if (up in TitleTags.multiSub) return Tag.MultiSub
        if (up in TitleTags.multiAudio) return Tag.Multi
        if (up in TitleTags.originalWithSubtitles) {
            return Tag.SubtitleLanguage(TitleTags.subtitleLanguage.getValue(up), original = true)
        }
        TitleTags.subtitleLanguage[up]?.let { return Tag.SubtitleLanguage(it) }
        val strictCase = context == Context.TAIL && hasLower
        if (up in TitleTags.subtitleWords && !strictCase) return Tag.SubtitleWord
        if (up in TitleTags.originalAudio && (context == Context.TAGGED || !hasLower)) return Tag.Original
        compoundSubtitle(up)?.let { return Tag.SubtitleLanguage(it) }
        val codesNeedUpper = context != Context.TAGGED
        // În categorii, codurile de 2 litere contează doar ca prefix („ES - …”): „DE” e și „de” în spaniolă.
        val codeAllowed = context != Context.GROUP || up.length > 2
        TitleTags.languageCodes[up]?.let {
            if (codeAllowed &&
                (!codesNeedUpper || !hasLower)
            ) {
                return Tag.Language(it, section = context == Context.TAGGED)
            }
        }
        if (context == Context.TAGGED) TitleTags.prefixOnlyCodes[up]?.let { return Tag.Language(it, section = true) }
        TitleTags.countryNames[up]?.let { if (!strictCase) return Tag.Language(it, section = true) }
        TitleTags.languageNames[up]?.let {
            if (!strictCase) {
                return Tag.Language(
                    it,
                    section = context == Context.GROUP,
                )
            }
        }
        if (up in TitleTags.noise) {
            if (context != Context.TAIL) return Tag.Noise
            if (!hasLower && !isDigits && up !in noiseNotInTail) return Tag.Noise
        }
        if (context == Context.TAGGED && up in TitleTags.multilingualPrefixes) return Tag.Platform
        if (context == Context.TAGGED && (up in TitleTags.platforms || upper in TitleTags.platforms)) {
            return Tag.Platform
        }
        return null
    }

    /** `ENGSUB`, `ROSUB`, `NLSUBS`, `SUBRO`, `SUBENG` → limba subtitrării. */
    private fun compoundSubtitle(up: String): String? {
        if (up.length < 5) return null
        for (word in arrayOf("SUBS", "SUB")) {
            if (up.endsWith(word)) TitleTags.languageCodes[up.removeSuffix(word)]?.let { return it }
            if (up.startsWith(word)) TitleTags.languageCodes[up.removePrefix(word)]?.let { return it }
        }
        return null
    }

    private fun isStrong(token: String): Boolean = token.uppercase() in TitleTags.strong

    // ---------------------------------------------------------------- tokenizare

    private class Token(
        val text: String,
        val start: Int,
        val end: Int,
    )

    private val wordRegex = Regex("""[\p{L}\p{N}]+(?:['’][\p{L}\p{N}]+)*\+?""")

    private fun tokens(
        s: String,
        from: Int = 0,
        to: Int = s.length,
    ): List<Token> =
        wordRegex
            .findAll(s.substring(from, to))
            .map {
                Token(
                    it.value,
                    from + it.range.first,
                    from + it.range.last + 1,
                )
            }.toList()

    private fun year(token: String): Int? {
        if (token.length != 4 || !token.all { it.isDigit() }) return null
        return token.toInt().takeIf { it in MIN_YEAR..maxYear }
    }

    // ---------------------------------------------------------------- prefix

    private class TagGroup(
        val tags: List<Tag>,
        val ranked: Boolean = false,
        /** Prefixul de furnizor (`RO - `, `EN|`): limba lui e a secțiunii. */
        val prefix: Boolean = false,
    )

    private val dashPrefix = Regex("""^([A-Z0-9+]{2,6}(?:[ /+-][A-Z0-9+]{2,6}){0,2})\s*[-:–—»•★☆●►▶◆■♦]\s+""")
    private val rankedCategory = Regex("""(?i)\bTOP\s+\d{1,3}\b""")
    private val rankingPosition = Regex("""^\d{1,3}\.\s+(?=\p{L})""")

    /** Taie prefixul de furnizor; întoarce poziția de unde începe titlul și etichetele găsite. */
    private fun stripPrefix(
        s: String,
        groups: MutableList<TagGroup>,
    ): Int {
        var pos = 0
        loop@ while (pos < s.length) {
            val c = s[pos]
            when {
                c == ' ' || c == '|' -> {
                    pos++
                }

                c == '[' || c == '(' || c == '{' -> {
                    val close = s.indexOf(closing(c), pos + 1)
                    if (close < 0) break@loop
                    val tags = prefixTags(s, pos + 1, close) ?: break@loop
                    if (!hasTitleAfter(s, close + 1)) break@loop
                    groups += tags
                    pos = close + 1
                }

                !c.isLetterOrDigit() && c != '\'' && c != '#' && c != '¡' && c != '¿' -> {
                    // Decor: ★, •, ►, ●…
                    pos++
                }

                else -> {
                    val pipe = s.indexOf('|', pos)
                    if (pipe in (pos + 1)..(pos + 20)) {
                        val tags = prefixTags(s, pos, pipe)
                        if (tags != null && hasTitleAfter(s, pipe + 1)) {
                            groups += tags
                            pos = pipe + 1
                            continue@loop
                        }
                    }
                    val m = dashPrefix.find(s.substring(pos)) ?: break@loop
                    val tags = prefixTags(s, pos, pos + m.groups[1]!!.range.last + 1) ?: break@loop
                    if (!hasTitleAfter(s, pos + m.range.last + 1)) break@loop
                    groups += tags
                    pos += m.range.last + 1
                }
            }
        }
        return pos
    }

    private fun closing(open: Char) =
        when (open) {
            '[' -> ']'
            '(' -> ')'
            else -> '}'
        }

    private fun hasTitleAfter(
        s: String,
        from: Int,
    ): Boolean = (from until s.length).any { s[it].isLetterOrDigit() }

    /** Etichetele din [from, to) dacă toate cuvintele sunt etichete cunoscute, altfel null. */
    private fun prefixTags(
        s: String,
        from: Int,
        to: Int,
    ): TagGroup? {
        val words = tokens(s, from, to)
        if (words.isEmpty()) return null
        // Unele categorii Netflix folosesc NF-DO pentru Dolby Audio. Nu îl interpreta ca limbă sau HDR.
        if (s.regionMatches(from, "NF-DO", 0, 5) && to - from == 5) return TagGroup(listOf(Tag.Platform))
        return TagGroup(
            tags = words.map { classify(it.text, Context.TAGGED) ?: return null },
            ranked = words.any { it.text == "TOP" },
            prefix = true,
        )
    }

    // ---------------------------------------------------------------- sezon / episod

    private val seasonEpisode =
        listOf(
            Regex("""(?i)\bS(\d{1,2})\s?[-.]?\s?E[Pp]?(\d{1,4})\b"""),
            Regex("""(?i)\b(\d{1,2})x(\d{2,3})\b"""),
        )
    private val seasonWord =
        Regex("""(?i)\b(?:season|sezonul|sezon|staffel|saison|temporada|stagione|sezona|seizoen)\s*(\d{1,2})\b""")
    private val episodeWord =
        Regex("""(?i)\b(?:episode|episodul|episod|episodio|épisode|folge|afl|aflevering|ep)\s*\.?\s*(\d{1,4})\b""")
    private val bareEpisode = Regex("""(?i)\bE(\d{1,4})\b""")
    private val seasonOnly = Regex("""(?i)\bS(\d{1,2})\b""")

    private class SeasonEpisode(
        val season: Int?,
        val episode: Int?,
        val start: Int,
        val ranges: List<IntRange>,
    )

    private fun findSeasonEpisode(s: String): SeasonEpisode? {
        for (regex in seasonEpisode) {
            val m = regex.find(s) ?: continue
            if (m.range.first == 0) continue
            return SeasonEpisode(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.range.first, listOf(m.range))
        }
        val season = seasonWord.find(s)?.takeIf { it.range.first > 0 }
        val seasonMatch = season ?: seasonOnly.find(s)?.takeIf { it.range.first > 0 }
        val afterSeason = seasonMatch?.range?.last ?: 0
        val episode =
            episodeWord.findAll(s).firstOrNull { it.range.first > maxOf(0, afterSeason) }
                ?: if (seasonMatch != null) bareEpisode.find(s, afterSeason + 1) else null
        if (seasonMatch == null && episode == null) return null
        val ranges = listOfNotNull(seasonMatch?.range, episode?.range)
        return SeasonEpisode(
            season = seasonMatch?.groupValues?.get(1)?.toInt(),
            episode = episode?.groupValues?.get(1)?.toInt(),
            start = ranges.minOf { it.first },
            ranges = ranges,
        )
    }

    // ---------------------------------------------------------------- nume

    private val bracketRegex = Regex("""[\[({]([^\[\](){}]*)[\])}]""")
    private val trimChars = charArrayOf(' ', '-', '–', '—', ':', '|', ',', '.', ';', '/', '~', '•', '*', '+')

    private fun parseName(raw: String, groupTitle: String?): Pair<ParsedTitle, Info> {
        val s = normalize(raw)
        val groups = mutableListOf<TagGroup>()
        val start = stripPrefix(s, groups)
        val titleAndTags = s.substring(start)
        val rest =
            if (groups.any { it.ranked } && groupTitle != null && rankedCategory.containsMatchIn(groupTitle)) {
                rankingPosition.replace(titleAndTags, "")
            } else {
                titleAndTags
            }

        val consumed = mutableListOf<IntRange>()
        val se = findSeasonEpisode(rest)
        se?.ranges?.let(consumed::addAll)

        // Paranteze: an, etichete sau text necunoscut (care rămâne în titlu).
        var bracketYear: Pair<Int, Int>? = null // (an, poziție)
        var bracketAnchor = Int.MAX_VALUE
        val bracketRanges = mutableListOf<IntRange>()
        for (m in bracketRegex.findAll(rest)) {
            if (consumed.any { it.first >= m.range.first && it.last <= m.range.last }) {
                bracketRanges += m.range
                consumed += m.range
                continue
            }
            val inner = tokens(rest, m.range.first + 1, m.range.last)
            if (inner.isEmpty()) {
                consumed += m.range
                continue
            }
            // `Titlu (2022) (TR)`: un cod de țară după an = țara de origine (limba audio originală).
            val yearBefore = bracketYear
            val single = inner.singleOrNull()?.text
            val origin =
                if (yearBefore != null && m.range.first > yearBefore.second && single != null &&
                    single.length == 2 && single.all { it.isUpperCase() }
                ) {
                    TitleTags.countryLanguage[single]
                } else {
                    null
                }
            if (origin != null) {
                bracketRanges += m.range
                consumed += m.range
                groups += TagGroup(listOf(Tag.Origin(origin)))
                bracketAnchor = minOf(bracketAnchor, m.range.first)
                continue
            }
            val years = inner.mapNotNull { year(it.text) }
            val tags = inner.filter { year(it.text) == null }.map { classify(it.text, Context.TAGGED) }
            if (tags.any { it == null }) continue
            bracketRanges += m.range
            consumed += m.range
            if (years.isNotEmpty() && (bracketYear == null || m.range.first < (se?.start ?: Int.MAX_VALUE))) {
                if (m.range.first > 0) bracketYear = years.first() to m.range.first
            }
            if (tags.isNotEmpty()) groups += TagGroup(tags.filterNotNull())
            if (m.range.first > 0) bracketAnchor = minOf(bracketAnchor, m.range.first)
        }

        val words =
            tokens(rest).filter { t -> consumed.none { t.start >= it.first && t.end - 1 <= it.last } }
        val firstWordStart = words.firstOrNull()?.start ?: 0
        val strongAnchor =
            words.indices
                .firstOrNull { index ->
                    val token = words[index]
                    token.start > firstWordStart &&
                        (
                            isStrong(token.text) ||
                                (
                                    (token.text == "EN" || token.text == "ENG") &&
                                        words.getOrNull(index + 1)?.text == "AUDIO51"
                                )
                        )
                }?.let { words[it].start } ?: Int.MAX_VALUE
        var anchor = minOf(strongAnchor, bracketAnchor, se?.start ?: Int.MAX_VALUE)

        // Anul: preferăm unul din paranteză; altfel ultimul an liber dinaintea ancorei (nu primul cuvânt).
        var year: Int? = null
        var yearToken: Token? = null
        if (bracketYear != null) {
            year = bracketYear.first
            anchor = minOf(anchor, bracketYear.second)
        } else {
            val candidates = words.filter { it.start > firstWordStart && year(it.text) != null }
            val beforeAnchor = candidates.lastOrNull { it.start < anchor }
            val chosen = beforeAnchor ?: candidates.firstOrNull { it.start > anchor }
            if (chosen != null) {
                year = year(chosen.text)
                yearToken = chosen
                if (chosen.start < anchor) anchor = chosen.start
            }
        }

        val titleEnd: Int
        if (anchor != Int.MAX_VALUE) {
            titleEnd = anchor
            val after =
                words
                    .filter { it.start >= anchor && it !== yearToken }
                    .mapNotNull { classify(it.text, Context.AFTER_ANCHOR) }
            if (after.isNotEmpty()) groups += TagGroup(after)
        } else {
            // Fără ancoră: etichete cunoscute la coadă (ex. „Matrix HD”, „Matrix - EN”).
            var cut = words.size
            val tail = ArrayList<Tag>()
            while (cut > 1) {
                val tag = classify(words[cut - 1].text, Context.TAIL) ?: break
                tail.add(0, tag)
                cut--
            }
            if (tail.isNotEmpty()) groups += TagGroup(tail)
            titleEnd = if (cut < words.size) words[cut].start else rest.length
        }

        val title =
            cleanTitle(rest, titleEnd, bracketRanges, consumed).ifEmpty {
                cleanTitle(rest, rest.length, emptyList(), emptyList())
            }
        val info = interpret(groups)
        return ParsedTitle(
            title = title.ifEmpty { raw.trim() },
            year = year,
            season = se?.season,
            episode = se?.episode,
            quality = info.quality,
            hdr = info.hdr,
            codec = info.codec,
            languages = info.sectionLanguages + info.explicitLanguages,
            subtitles = info.subtitles,
        ) to info
    }

    private fun cleanTitle(
        rest: String,
        end: Int,
        removed: List<IntRange>,
        consumed: List<IntRange>,
    ): String {
        val sb = StringBuilder(end)
        for (i in 0 until end) {
            if (removed.any { i in it } || consumed.any { i in it }) {
                sb.append(' ')
            } else {
                sb.append(rest[i])
            }
        }
        var t = spaces.replace(sb.toString(), " ").trim(*trimChars)
        // O paranteză lăsată deschisă de tăietură, ex. „Titlu (” sau „Titlu [”.
        t = t.trimEnd('(', '[', '{', ' ', '-', ':')
        return t.trim(*trimChars)
    }

    // ---------------------------------------------------------------- interpretare

    private class Info(
        /** Limbile secțiunii: prefixul numelui, numele de țară, limbile din categorie. */
        val sectionLanguages: List<String>,
        /** Limbi audio declarate explicit (paranteze, după an, `DUBLAT`, `AUDIO RO`). */
        val explicitLanguages: List<String>,
        val subtitles: List<String>,
        val quality: VideoQuality?,
        val hdr: HdrFormat?,
        val codec: VideoCodec?,
        /** Limba țării de origine (`(TR)` după an). */
        val origin: String? = null,
        /** `VO`, `VOSTFR`…: audio original. */
        val original: Boolean = false,
        /** Apare un cuvânt de subtitrare (`SUB`, `SUBS`, `VOSTFR`, `MULTI-SUBS`). */
        val subtitleMarker: Boolean = false,
    )

    private fun interpret(
        groups: List<TagGroup>,
        allSection: Boolean = false,
    ): Info {
        val section = LinkedHashSet<String>()
        val explicit = LinkedHashSet<String>()
        val subtitles = LinkedHashSet<String>()
        var origin: String? = null
        var original = false
        var subtitleMarker = false
        var quality: VideoQuality? = null
        var hdr: HdrFormat? = null
        var codec: VideoCodec? = null
        for (group in groups) {
            val tags = group.tags
            val languages = if (group.prefix || allSection) section else explicit
            if (tags.any { it == Tag.SubtitleWord || it == Tag.MultiSub || it is Tag.SubtitleLanguage }) {
                subtitleMarker = true
            }
            val claimed = BooleanArray(tags.size)
            // `SUB RO`, `RO SUB`, `MULTI-SUB`: limba lipită de cuvântul „SUB” e a subtitrării.
            for (i in tags.indices) {
                if (tags[i] != Tag.SubtitleWord) continue
                claimed[i] = true
                val next = tags.getOrNull(i + 1)
                val prev = tags.getOrNull(i - 1)
                when {
                    next is Tag.Language && !claimed[i + 1] -> {
                        subtitles += next.iso
                        claimed[i + 1] = true
                    }

                    prev is Tag.Language && !claimed[i - 1] -> {
                        subtitles += prev.iso
                        claimed[i - 1] = true
                    }

                    prev == Tag.Multi && !claimed[i - 1] -> {
                        subtitles += ParsedTitle.MULTI
                        claimed[i - 1] = true
                    }
                }
            }
            for (i in tags.indices) {
                if (claimed[i]) continue
                when (val tag = tags[i]) {
                    is Tag.Language -> {
                        if (tag.section) section += tag.iso else languages += tag.iso
                    }

                    is Tag.SubtitleLanguage -> {
                        subtitles += tag.iso
                        if (tag.original) original = true
                    }

                    is Tag.Origin -> {
                        origin = origin ?: tag.iso
                    }

                    Tag.Original -> {
                        original = true
                    }

                    Tag.Multi -> {
                        languages += ParsedTitle.MULTI
                    }

                    Tag.MultiSub -> {
                        subtitles += ParsedTitle.MULTI
                    }

                    is Tag.Quality -> {
                        if (quality == null || tag.value > quality) quality = tag.value
                    }

                    is Tag.Hdr -> {
                        if (hdr == null || tag.value > hdr) hdr = tag.value
                    }

                    is Tag.Codec -> {
                        if (codec == null) codec = tag.value
                    }

                    Tag.SubtitleWord, Tag.Noise, Tag.Platform -> {
                        Unit
                    }
                }
            }
        }
        return Info(
            sectionLanguages = section.toList(),
            explicitLanguages = (explicit - section).toList(),
            subtitles = subtitles.toList(),
            quality = quality,
            hdr = hdr,
            codec = codec,
            origin = origin,
            original = original,
            subtitleMarker = subtitleMarker,
        )
    }

    // ---------------------------------------------------------------- categorie

    /** Coduri la începutul categoriei, fără liniuță: „PT/BR SERIE”, „UK SERIES”. */
    private val leadingCodes = Regex("""^([A-Z]{2}(?:[/+][A-Z]{2})*)\s""")

    private fun parseGroup(group: String): Info {
        val s = normalize(group)
        val tags = ArrayList<Tag>()
        // Prefixul de limbă: înaintea primei bare („DE | Filme 4K”, „EN|VOD”) sau „ES - PELÍCULAS”.
        val pipe = s.indexOf('|')
        val prefixEnd =
            when {
                pipe in 0..20 -> {
                    pipe
                }

                else -> {
                    (dashPrefix.find(s) ?: leadingCodes.find(s))
                        ?.takeIf { it.range.first == 0 }
                        ?.groups
                        ?.get(1)
                        ?.range
                        ?.last
                        ?.plus(1) ?: -1
                }
            }
        // `(TR)`, `(NL)` într-o categorie: țara de origine / limba secțiunii.
        val brackets = bracketRegex.findAll(s).map { it.range }.toList()
        val words = tokens(s)
        // Un cod singur între bare („VOD | EN | 4K”) sau la coada categoriei („Seriale RO”) e tot limba secțiunii.
        val pipeSegments =
            s
                .split('|')
                .runningFold(0) { start, part -> start + part.length + 1 }
                .zip(s.split('|'))
                .filter { (_, part) -> part.trim().length in 2..5 }
                .map { (start, part) -> start + part.indexOf(part.trim()) }
                .toSet()
        for ((index, w) in words.withIndex()) {
            val inBracket = brackets.any { w.start > it.first && w.end <= it.last }
            if (inBracket && w.text.length == 2 && w.text.all { it.isUpperCase() }) {
                TitleTags.countryLanguage[w.text]?.let {
                    tags += Tag.Origin(it)
                    continue
                }
            }
            val alone = w.start in pipeSegments || (index == words.lastIndex && index > 0)
            val context =
                if ((prefixEnd >= 0 && w.end <= prefixEnd) || (alone && w.text.length == 2)) {
                    Context.TAGGED
                } else {
                    Context.GROUP
                }
            classify(w.text, context)?.let(tags::add)
        }
        return interpret(listOf(TagGroup(tags)), allSection = true)
    }
}
