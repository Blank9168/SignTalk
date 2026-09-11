package com.example.signtalk.data.dictionary

/**
 * Starter content for the full 50-sign vocabulary from the 2026-09-05
 * redesign (25 FSL + 25 ASL -- see proposal-notes.md's "Sign vocabulary"
 * section for the full audit and "Reference videos" section for sourcing).
 *
 * IMPORTANT naming fix (2026-09-06): the FSL greetings (Magandang Umaga/
 * Hapon/Gabi) and their ASL counterparts (Good Morning/Afternoon/Evening)
 * used to share the same English slug (good_morning, etc.), which meant
 * only ONE video could ever be bundled under that name even though FSL and
 * ASL sign them completely differently. Fixed by giving the FSL entries
 * their own Filipino slugs (magandang_umaga/hapon/gabi) so both languages
 * get their own dictionary entry and their own video. Every other FSL word
 * already had a distinct Filipino slug, so this was the only collision.
 *
 * Videos are resolved by DictionaryDetailFragment purely from the `label`
 * field -- it looks for a bundled asset at `sign_videos/<label>.mp4` before
 * falling back to a user-attached `videoUri`. So adding/replacing a video
 * for any entry below is just a matter of dropping the right file into
 * app/src/main/assets/sign_videos/ under that entry's label; no DB/seed
 * change is needed to "wire up" a clip once it exists.
 *
 * VOCABULARY SWAP (2026-09-06): the original 22 FSL words with no open
 * dataset (question words, transportation, magkano, paalam, ingat ka,
 * calendar/weather) were replaced with FSL Numbers and Colors instead of
 * being recorded from scratch. Reason: FSL-105 (Mendeley/DLSU-DOST) has
 * real Filipino-signer video AND already-extracted MediaPipe landmarks for
 * its full 105-class vocabulary, but only 3 of those classes (the
 * greetings) were used in the original 50-word redesign -- the other ~80
 * classes (numbers, colors, days, months, family, food, drink) sat
 * completely unused in both `sign_videos/` and `ai/dataset/raw/`. Numbers
 * (isa-sampu) and Colors (asul-maliwanag, 12 of FSL-105's 13 colors) were
 * picked to fill the 22 slots -- both are real, complete, and immediately
 * usable with zero new recording or landmark extraction. The FSL-105
 * source videos/landmarks (English-labelled: one.mp4, blue.mp4, etc.) were
 * copied to their own Filipino-labelled slugs (isa.mp4, asul.mp4, etc.) in
 * both `sign_videos/` and `ai/dataset/raw/`, matching how the greetings
 * naming fix above was done -- see proposal-notes.md for the full mapping
 * table and the research trail that led here (five research passes across
 * Roboflow, Zenodo, IEEE Dataport, GitHub, government/academic sources
 * found no open dataset for the original 22 words; the team chose this
 * swap over recording new clips).
 *
 * DATA STATUS -- ALL 50 WORDS NOW HAVE REAL VIDEO + REAL TRAINING DATA:
 *
 * ASL (25/25 -- real ASL Citizen footage):
 * - 19 are single ASL Citizen clips copied in directly: hello (HELLO),
 *   im_fine (FINE1), thank_you (THANKYOU), youre_welcome (WELCOME2),
 *   understand (UNDERSTAND), dont_understand (NOTUNDERSTAND), know (KNOW),
 *   dont_know (DONTKNOW), yes (YES), no (NO), wrong (WRONG), correct
 *   (RIGHT1 -- ASL has no literal "CORRECT" gloss, RIGHT is the standard
 *   sign), slow (SLOW), fast (FAST), father (FATHER), mother (MOTHER),
 *   boy (BOY), girl (GIRL), married (MARRY -- base verb, no separate
 *   "MARRIED" form).
 * - 6 have no single-sign ASL Citizen gloss (they're phrases in real ASL),
 *   so their clips are ffmpeg-concatenations of the component ASL Citizen
 *   signs: good_morning (GOOD+MORNING), good_afternoon (GOOD+AFTERNOON),
 *   good_evening (GOOD+NIGHT -- ASL has no distinct "evening" sign),
 *   how_are_you (HOW+YOU), nice_to_meet_you (NICE+MEET+YOU),
 *   see_you_tomorrow (SEE+YOU+TOMORROW). Each component clip came from
 *   whichever ASL Citizen participant was picked first for that gloss, so
 *   the signer may visibly change between words within one phrase clip --
 *   fine as a reference/placeholder, but re-record with one signer before
 *   shipping if that matters.
 *
 * FSL (25/25 -- real FSL-105 Filipino signer footage):
 * - magandang_umaga, magandang_hapon, magandang_gabi: real FSL-105
 *   footage (the original good_morning/afternoon/evening footage from the
 *   old 105-class seed, moved to its correct Filipino slug).
 * - isa, dalawa, tatlo, apat, lima, anim, pito, walo, siyam, sampu
 *   (Numbers 1-10): real FSL-105 footage, copied from the dataset's
 *   one/two/three/.../ten classes.
 * - asul, berde, pula, kayumanggi, itim, puti, dilaw, kahel, abo, rosas,
 *   lila, maliwanag (Colors): real FSL-105 footage, copied from the
 *   dataset's blue/green/red/brown/black/white/yellow/orange/gray/pink/
 *   violet/light classes (dark was the one FSL-105 color left unused).
 */
val seedDictionaryEntries: List<DictionaryEntity> = listOf(
    // ---- FSL: Numbers (real FSL-105 footage, copied from one..ten) ----
    DictionaryEntity(0, "isa", "Isa", "FSL - Numbers", "Real FSL sign \"Isa\" (One), from the FSL-105 dataset's actual Filipino signer footage (gloss ONE).", "1️⃣", false),
    DictionaryEntity(0, "dalawa", "Dalawa", "FSL - Numbers", "Real FSL sign \"Dalawa\" (Two), from the FSL-105 dataset's actual Filipino signer footage (gloss TWO).", "2️⃣", false),
    DictionaryEntity(0, "tatlo", "Tatlo", "FSL - Numbers", "Real FSL sign \"Tatlo\" (Three), from the FSL-105 dataset's actual Filipino signer footage (gloss THREE).", "3️⃣", false),
    DictionaryEntity(0, "apat", "Apat", "FSL - Numbers", "Real FSL sign \"Apat\" (Four), from the FSL-105 dataset's actual Filipino signer footage (gloss FOUR).", "4️⃣", false),
    DictionaryEntity(0, "lima", "Lima", "FSL - Numbers", "Real FSL sign \"Lima\" (Five), from the FSL-105 dataset's actual Filipino signer footage (gloss FIVE).", "5️⃣", false),
    DictionaryEntity(0, "anim", "Anim", "FSL - Numbers", "Real FSL sign \"Anim\" (Six), from the FSL-105 dataset's actual Filipino signer footage (gloss SIX).", "6️⃣", false),
    DictionaryEntity(0, "pito", "Pito", "FSL - Numbers", "Real FSL sign \"Pito\" (Seven), from the FSL-105 dataset's actual Filipino signer footage (gloss SEVEN).", "7️⃣", false),
    DictionaryEntity(0, "walo", "Walo", "FSL - Numbers", "Real FSL sign \"Walo\" (Eight), from the FSL-105 dataset's actual Filipino signer footage (gloss EIGHT).", "8️⃣", false),
    DictionaryEntity(0, "siyam", "Siyam", "FSL - Numbers", "Real FSL sign \"Siyam\" (Nine), from the FSL-105 dataset's actual Filipino signer footage (gloss NINE).", "9️⃣", false),
    DictionaryEntity(0, "sampu", "Sampu", "FSL - Numbers", "Real FSL sign \"Sampu\" (Ten), from the FSL-105 dataset's actual Filipino signer footage (gloss TEN).", "🔟", false),

    // ---- FSL: Colors (real FSL-105 footage, copied from FSL-105's COLOR category) ----
    DictionaryEntity(0, "asul", "Asul", "FSL - Colors", "Real FSL sign \"Asul\" (Blue), from the FSL-105 dataset's actual Filipino signer footage (gloss BLUE).", "🔵", false),
    DictionaryEntity(0, "berde", "Berde", "FSL - Colors", "Real FSL sign \"Berde\" (Green), from the FSL-105 dataset's actual Filipino signer footage (gloss GREEN).", "🟢", false),
    DictionaryEntity(0, "pula", "Pula", "FSL - Colors", "Real FSL sign \"Pula\" (Red), from the FSL-105 dataset's actual Filipino signer footage (gloss RED).", "🔴", false),
    DictionaryEntity(0, "kayumanggi", "Kayumanggi", "FSL - Colors", "Real FSL sign \"Kayumanggi\" (Brown), from the FSL-105 dataset's actual Filipino signer footage (gloss BROWN).", "🟤", false),
    DictionaryEntity(0, "itim", "Itim", "FSL - Colors", "Real FSL sign \"Itim\" (Black), from the FSL-105 dataset's actual Filipino signer footage (gloss BLACK).", "⚫", false),
    DictionaryEntity(0, "puti", "Puti", "FSL - Colors", "Real FSL sign \"Puti\" (White), from the FSL-105 dataset's actual Filipino signer footage (gloss WHITE).", "⚪", false),
    DictionaryEntity(0, "dilaw", "Dilaw", "FSL - Colors", "Real FSL sign \"Dilaw\" (Yellow), from the FSL-105 dataset's actual Filipino signer footage (gloss YELLOW).", "🟡", false),
    DictionaryEntity(0, "kahel", "Kahel", "FSL - Colors", "Real FSL sign \"Kahel\" (Orange), from the FSL-105 dataset's actual Filipino signer footage (gloss ORANGE).", "🟠", false),
    DictionaryEntity(0, "abo", "Abo", "FSL - Colors", "Real FSL sign \"Abo\" (Gray), from the FSL-105 dataset's actual Filipino signer footage (gloss GRAY).", "🔘", false),
    DictionaryEntity(0, "rosas", "Rosas", "FSL - Colors", "Real FSL sign \"Rosas\" (Pink), from the FSL-105 dataset's actual Filipino signer footage (gloss PINK).", "💗", false),
    DictionaryEntity(0, "lila", "Lila", "FSL - Colors", "Real FSL sign \"Lila\" (Violet), from the FSL-105 dataset's actual Filipino signer footage (gloss VIOLET).", "🟣", false),
    DictionaryEntity(0, "maliwanag", "Maliwanag", "FSL - Colors", "Real FSL sign \"Maliwanag\" (Light), from the FSL-105 dataset's actual Filipino signer footage (gloss LIGHT).", "✨", false),

    // ---- FSL: Greetings (all 3 have real FSL-105 footage) ----
    DictionaryEntity(0, "magandang_umaga", "Magandang Umaga", "FSL - Greetings", "Real FSL sign \"Magandang Umaga\" (Good Morning), from the FSL-105 dataset's actual Filipino signer footage.", "🌅", false),
    DictionaryEntity(0, "magandang_hapon", "Magandang Hapon", "FSL - Greetings", "Real FSL sign \"Magandang Hapon\" (Good Afternoon), from the FSL-105 dataset's actual Filipino signer footage.", "🌤️", false),
    DictionaryEntity(0, "magandang_gabi", "Magandang Gabi", "FSL - Greetings", "Real FSL sign \"Magandang Gabi\" (Good Evening), from the FSL-105 dataset's actual Filipino signer footage.", "🌆", false),

    // ---- ASL: Greetings (all backed by real ASL Citizen footage) ----
    DictionaryEntity(0, "good_morning", "Good Morning", "ASL - Greetings", "Real ASL sign for \"Good Morning\", built from ASL Citizen's GOOD + MORNING clips (ASL has no single atomic sign for this phrase).", "🌅", false),
    DictionaryEntity(0, "good_afternoon", "Good Afternoon", "ASL - Greetings", "Real ASL sign for \"Good Afternoon\", built from ASL Citizen's GOOD + AFTERNOON clips (ASL has no single atomic sign for this phrase).", "🌤️", false),
    DictionaryEntity(0, "good_evening", "Good Evening", "ASL - Greetings", "Real ASL sign for \"Good Evening\", built from ASL Citizen's GOOD + NIGHT clips (ASL has no distinct \"evening\" sign, so NIGHT is used).", "🌆", false),
    DictionaryEntity(0, "hello", "Hello", "ASL - Greetings", "Real ASL sign \"Hello\", from Microsoft's ASL Citizen dataset (gloss HELLO).", "👋", false),
    DictionaryEntity(0, "how_are_you", "How Are You", "ASL - Greetings", "Real ASL sign for \"How Are You\", built from ASL Citizen's HOW + YOU clips (ASL doesn't sign \"are\" as a separate verb).", "🤔", false),
    DictionaryEntity(0, "im_fine", "I'm Fine", "ASL - Greetings", "Real ASL sign \"I'm Fine\", from Microsoft's ASL Citizen dataset (gloss FINE).", "🙂", false),
    DictionaryEntity(0, "nice_to_meet_you", "Nice To Meet You", "ASL - Greetings", "Real ASL sign for \"Nice To Meet You\", built from ASL Citizen's NICE + MEET + YOU clips.", "🤝", false),
    DictionaryEntity(0, "thank_you", "Thank You", "ASL - Greetings", "Real ASL sign \"Thank You\", from Microsoft's ASL Citizen dataset (gloss THANKYOU).", "🙏", false),
    DictionaryEntity(0, "youre_welcome", "You're Welcome", "ASL - Greetings", "Real ASL sign \"You're Welcome\", from Microsoft's ASL Citizen dataset (gloss WELCOME).", "😊", false),
    DictionaryEntity(0, "see_you_tomorrow", "See You Tomorrow", "ASL - Greetings", "Real ASL sign for \"See You Tomorrow\", built from ASL Citizen's SEE + YOU + TOMORROW clips.", "👋", false),

    // ---- ASL: Basic Responses (all backed by real ASL Citizen footage) ----
    DictionaryEntity(0, "understand", "Understand", "ASL - Basic Responses", "Real ASL sign \"Understand\", from Microsoft's ASL Citizen dataset (gloss UNDERSTAND).", "💡", false),
    DictionaryEntity(0, "dont_understand", "Don't Understand", "ASL - Basic Responses", "Real ASL sign \"Don't Understand\", from Microsoft's ASL Citizen dataset (gloss NOTUNDERSTAND).", "❓", false),
    DictionaryEntity(0, "know", "Know", "ASL - Basic Responses", "Real ASL sign \"Know\", from Microsoft's ASL Citizen dataset (gloss KNOW).", "🧠", false),
    DictionaryEntity(0, "dont_know", "Don't Know", "ASL - Basic Responses", "Real ASL sign \"Don't Know\", from Microsoft's ASL Citizen dataset (gloss DONTKNOW).", "🤷", false),
    DictionaryEntity(0, "yes", "Yes", "ASL - Basic Responses", "Real ASL sign \"Yes\", from Microsoft's ASL Citizen dataset (gloss YES).", "✅", false),
    DictionaryEntity(0, "no", "No", "ASL - Basic Responses", "Real ASL sign \"No\", from Microsoft's ASL Citizen dataset (gloss NO).", "❌", false),
    DictionaryEntity(0, "wrong", "Wrong", "ASL - Basic Responses", "Real ASL sign \"Wrong\", from Microsoft's ASL Citizen dataset (gloss WRONG).", "🚫", false),
    DictionaryEntity(0, "correct", "Correct", "ASL - Basic Responses", "Real ASL sign \"Correct\", from Microsoft's ASL Citizen dataset (gloss RIGHT -- ASL has no separate literal \"CORRECT\" sign).", "✔️", false),
    DictionaryEntity(0, "slow", "Slow", "ASL - Basic Responses", "Real ASL sign \"Slow\", from Microsoft's ASL Citizen dataset (gloss SLOW).", "🐢", false),
    DictionaryEntity(0, "fast", "Fast", "ASL - Basic Responses", "Real ASL sign \"Fast\", from Microsoft's ASL Citizen dataset (gloss FAST).", "⚡", false),

    // ---- ASL: Family ----
    DictionaryEntity(0, "father", "Father", "ASL - Family", "Real ASL sign \"Father\", from Microsoft's ASL Citizen dataset (gloss FATHER).", "👨", false),
    DictionaryEntity(0, "mother", "Mother", "ASL - Family", "Real ASL sign \"Mother\", from Microsoft's ASL Citizen dataset (gloss MOTHER).", "👩", false),

    // ---- ASL: People & Relationships ----
    DictionaryEntity(0, "boy", "Boy", "ASL - People & Relationships", "Real ASL sign \"Boy\", from Microsoft's ASL Citizen dataset (gloss BOY).", "👦", false),
    DictionaryEntity(0, "girl", "Girl", "ASL - People & Relationships", "Real ASL sign \"Girl\", from Microsoft's ASL Citizen dataset (gloss GIRL).", "👧", false),
    DictionaryEntity(0, "married", "Married", "ASL - People & Relationships", "Real ASL sign \"Married\", from Microsoft's ASL Citizen dataset (gloss MARRY -- base verb form, ASL has no separate \"MARRIED\" adjective sign).", "💍", false)
)
