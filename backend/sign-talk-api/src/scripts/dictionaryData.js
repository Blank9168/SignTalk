// Transcribed directly from
// android/SignTalk/app/src/main/java/com/example/signtalk/data/dictionary/DictionarySeed.kt
// (2026-09-06 vocabulary-swap version) so the backend's dictionary collection
// matches the app's bundled seed exactly. See proposal-notes.md's "Sign
// vocabulary" section for the full history/sourcing of every entry below.
//
// hasVideo/hasTrainingData are both true for all 50 -- confirmed on-disk
// (sign_videos/<slug>.mp4 and ai/dataset/raw/<slug>/*.npy exist for every
// one of these) as of the 2026-09-06 vocabulary swap + retraining.

const dictionaryData = [
  // ---- FSL: Numbers ----
  { slug: "isa", label: "Isa", language: "FSL", category: "FSL - Numbers", description: "Real FSL sign \"Isa\" (One), from the FSL-105 dataset's actual Filipino signer footage (gloss ONE).", emoji: "1️⃣" },
  { slug: "dalawa", label: "Dalawa", language: "FSL", category: "FSL - Numbers", description: "Real FSL sign \"Dalawa\" (Two), from the FSL-105 dataset's actual Filipino signer footage (gloss TWO).", emoji: "2️⃣" },
  { slug: "tatlo", label: "Tatlo", language: "FSL", category: "FSL - Numbers", description: "Real FSL sign \"Tatlo\" (Three), from the FSL-105 dataset's actual Filipino signer footage (gloss THREE).", emoji: "3️⃣" },
  { slug: "apat", label: "Apat", language: "FSL", category: "FSL - Numbers", description: "Real FSL sign \"Apat\" (Four), from the FSL-105 dataset's actual Filipino signer footage (gloss FOUR).", emoji: "4️⃣" },
  { slug: "lima", label: "Lima", language: "FSL", category: "FSL - Numbers", description: "Real FSL sign \"Lima\" (Five), from the FSL-105 dataset's actual Filipino signer footage (gloss FIVE).", emoji: "5️⃣" },
  { slug: "anim", label: "Anim", language: "FSL", category: "FSL - Numbers", description: "Real FSL sign \"Anim\" (Six), from the FSL-105 dataset's actual Filipino signer footage (gloss SIX).", emoji: "6️⃣" },
  { slug: "pito", label: "Pito", language: "FSL", category: "FSL - Numbers", description: "Real FSL sign \"Pito\" (Seven), from the FSL-105 dataset's actual Filipino signer footage (gloss SEVEN).", emoji: "7️⃣" },
  { slug: "walo", label: "Walo", language: "FSL", category: "FSL - Numbers", description: "Real FSL sign \"Walo\" (Eight), from the FSL-105 dataset's actual Filipino signer footage (gloss EIGHT).", emoji: "8️⃣" },
  { slug: "siyam", label: "Siyam", language: "FSL", category: "FSL - Numbers", description: "Real FSL sign \"Siyam\" (Nine), from the FSL-105 dataset's actual Filipino signer footage (gloss NINE).", emoji: "9️⃣" },
  { slug: "sampu", label: "Sampu", language: "FSL", category: "FSL - Numbers", description: "Real FSL sign \"Sampu\" (Ten), from the FSL-105 dataset's actual Filipino signer footage (gloss TEN).", emoji: "🔟" },

  // ---- FSL: Colors ----
  { slug: "asul", label: "Asul", language: "FSL", category: "FSL - Colors", description: "Real FSL sign \"Asul\" (Blue), from the FSL-105 dataset's actual Filipino signer footage (gloss BLUE).", emoji: "🔵" },
  { slug: "berde", label: "Berde", language: "FSL", category: "FSL - Colors", description: "Real FSL sign \"Berde\" (Green), from the FSL-105 dataset's actual Filipino signer footage (gloss GREEN).", emoji: "🟢" },
  { slug: "pula", label: "Pula", language: "FSL", category: "FSL - Colors", description: "Real FSL sign \"Pula\" (Red), from the FSL-105 dataset's actual Filipino signer footage (gloss RED).", emoji: "🔴" },
  { slug: "kayumanggi", label: "Kayumanggi", language: "FSL", category: "FSL - Colors", description: "Real FSL sign \"Kayumanggi\" (Brown), from the FSL-105 dataset's actual Filipino signer footage (gloss BROWN).", emoji: "🟤" },
  { slug: "itim", label: "Itim", language: "FSL", category: "FSL - Colors", description: "Real FSL sign \"Itim\" (Black), from the FSL-105 dataset's actual Filipino signer footage (gloss BLACK).", emoji: "⚫" },
  { slug: "puti", label: "Puti", language: "FSL", category: "FSL - Colors", description: "Real FSL sign \"Puti\" (White), from the FSL-105 dataset's actual Filipino signer footage (gloss WHITE).", emoji: "⚪" },
  { slug: "dilaw", label: "Dilaw", language: "FSL", category: "FSL - Colors", description: "Real FSL sign \"Dilaw\" (Yellow), from the FSL-105 dataset's actual Filipino signer footage (gloss YELLOW).", emoji: "🟡" },
  { slug: "kahel", label: "Kahel", language: "FSL", category: "FSL - Colors", description: "Real FSL sign \"Kahel\" (Orange), from the FSL-105 dataset's actual Filipino signer footage (gloss ORANGE).", emoji: "🟠" },
  { slug: "abo", label: "Abo", language: "FSL", category: "FSL - Colors", description: "Real FSL sign \"Abo\" (Gray), from the FSL-105 dataset's actual Filipino signer footage (gloss GRAY).", emoji: "🔘" },
  { slug: "rosas", label: "Rosas", language: "FSL", category: "FSL - Colors", description: "Real FSL sign \"Rosas\" (Pink), from the FSL-105 dataset's actual Filipino signer footage (gloss PINK).", emoji: "💗" },
  { slug: "lila", label: "Lila", language: "FSL", category: "FSL - Colors", description: "Real FSL sign \"Lila\" (Violet), from the FSL-105 dataset's actual Filipino signer footage (gloss VIOLET).", emoji: "🟣" },
  { slug: "maliwanag", label: "Maliwanag", language: "FSL", category: "FSL - Colors", description: "Real FSL sign \"Maliwanag\" (Light), from the FSL-105 dataset's actual Filipino signer footage (gloss LIGHT).", emoji: "✨" },

  // ---- FSL: Greetings ----
  { slug: "magandang_umaga", label: "Magandang Umaga", language: "FSL", category: "FSL - Greetings", description: "Real FSL sign \"Magandang Umaga\" (Good Morning), from the FSL-105 dataset's actual Filipino signer footage.", emoji: "🌅" },
  { slug: "magandang_hapon", label: "Magandang Hapon", language: "FSL", category: "FSL - Greetings", description: "Real FSL sign \"Magandang Hapon\" (Good Afternoon), from the FSL-105 dataset's actual Filipino signer footage.", emoji: "🌤️" },
  { slug: "magandang_gabi", label: "Magandang Gabi", language: "FSL", category: "FSL - Greetings", description: "Real FSL sign \"Magandang Gabi\" (Good Evening), from the FSL-105 dataset's actual Filipino signer footage.", emoji: "🌆" },

  // ---- ASL: Greetings ----
  { slug: "good_morning", label: "Good Morning", language: "ASL", category: "ASL - Greetings", description: "Real ASL sign for \"Good Morning\", built from ASL Citizen's GOOD + MORNING clips (ASL has no single atomic sign for this phrase).", emoji: "🌅" },
  { slug: "good_afternoon", label: "Good Afternoon", language: "ASL", category: "ASL - Greetings", description: "Real ASL sign for \"Good Afternoon\", built from ASL Citizen's GOOD + AFTERNOON clips (ASL has no single atomic sign for this phrase).", emoji: "🌤️" },
  { slug: "good_evening", label: "Good Evening", language: "ASL", category: "ASL - Greetings", description: "Real ASL sign for \"Good Evening\", built from ASL Citizen's GOOD + NIGHT clips (ASL has no distinct \"evening\" sign, so NIGHT is used).", emoji: "🌆" },
  { slug: "hello", label: "Hello", language: "ASL", category: "ASL - Greetings", description: "Real ASL sign \"Hello\", from Microsoft's ASL Citizen dataset (gloss HELLO).", emoji: "👋" },
  { slug: "how_are_you", label: "How Are You", language: "ASL", category: "ASL - Greetings", description: "Real ASL sign for \"How Are You\", built from ASL Citizen's HOW + YOU clips (ASL doesn't sign \"are\" as a separate verb).", emoji: "🤔" },
  { slug: "im_fine", label: "I'm Fine", language: "ASL", category: "ASL - Greetings", description: "Real ASL sign \"I'm Fine\", from Microsoft's ASL Citizen dataset (gloss FINE).", emoji: "🙂" },
  { slug: "nice_to_meet_you", label: "Nice To Meet You", language: "ASL", category: "ASL - Greetings", description: "Real ASL sign for \"Nice To Meet You\", built from ASL Citizen's NICE + MEET + YOU clips.", emoji: "🤝" },
  { slug: "thank_you", label: "Thank You", language: "ASL", category: "ASL - Greetings", description: "Real ASL sign \"Thank You\", from Microsoft's ASL Citizen dataset (gloss THANKYOU).", emoji: "🙏" },
  { slug: "youre_welcome", label: "You're Welcome", language: "ASL", category: "ASL - Greetings", description: "Real ASL sign \"You're Welcome\", from Microsoft's ASL Citizen dataset (gloss WELCOME).", emoji: "😊" },
  { slug: "see_you_tomorrow", label: "See You Tomorrow", language: "ASL", category: "ASL - Greetings", description: "Real ASL sign for \"See You Tomorrow\", built from ASL Citizen's SEE + YOU + TOMORROW clips.", emoji: "👋" },

  // ---- ASL: Basic Responses ----
  { slug: "understand", label: "Understand", language: "ASL", category: "ASL - Basic Responses", description: "Real ASL sign \"Understand\", from Microsoft's ASL Citizen dataset (gloss UNDERSTAND).", emoji: "💡" },
  { slug: "dont_understand", label: "Don't Understand", language: "ASL", category: "ASL - Basic Responses", description: "Real ASL sign \"Don't Understand\", from Microsoft's ASL Citizen dataset (gloss NOTUNDERSTAND).", emoji: "❓" },
  { slug: "know", label: "Know", language: "ASL", category: "ASL - Basic Responses", description: "Real ASL sign \"Know\", from Microsoft's ASL Citizen dataset (gloss KNOW).", emoji: "🧠" },
  { slug: "dont_know", label: "Don't Know", language: "ASL", category: "ASL - Basic Responses", description: "Real ASL sign \"Don't Know\", from Microsoft's ASL Citizen dataset (gloss DONTKNOW).", emoji: "🤷" },
  { slug: "yes", label: "Yes", language: "ASL", category: "ASL - Basic Responses", description: "Real ASL sign \"Yes\", from Microsoft's ASL Citizen dataset (gloss YES).", emoji: "✅" },
  { slug: "no", label: "No", language: "ASL", category: "ASL - Basic Responses", description: "Real ASL sign \"No\", from Microsoft's ASL Citizen dataset (gloss NO).", emoji: "❌" },
  { slug: "wrong", label: "Wrong", language: "ASL", category: "ASL - Basic Responses", description: "Real ASL sign \"Wrong\", from Microsoft's ASL Citizen dataset (gloss WRONG).", emoji: "🚫" },
  { slug: "correct", label: "Correct", language: "ASL", category: "ASL - Basic Responses", description: "Real ASL sign \"Correct\", from Microsoft's ASL Citizen dataset (gloss RIGHT -- ASL has no separate literal \"CORRECT\" sign).", emoji: "✔️" },
  { slug: "slow", label: "Slow", language: "ASL", category: "ASL - Basic Responses", description: "Real ASL sign \"Slow\", from Microsoft's ASL Citizen dataset (gloss SLOW).", emoji: "🐢" },
  { slug: "fast", label: "Fast", language: "ASL", category: "ASL - Basic Responses", description: "Real ASL sign \"Fast\", from Microsoft's ASL Citizen dataset (gloss FAST).", emoji: "⚡" },

  // ---- ASL: Family ----
  { slug: "father", label: "Father", language: "ASL", category: "ASL - Family", description: "Real ASL sign \"Father\", from Microsoft's ASL Citizen dataset (gloss FATHER).", emoji: "👨" },
  { slug: "mother", label: "Mother", language: "ASL", category: "ASL - Family", description: "Real ASL sign \"Mother\", from Microsoft's ASL Citizen dataset (gloss MOTHER).", emoji: "👩" },

  // ---- ASL: People & Relationships ----
  { slug: "boy", label: "Boy", language: "ASL", category: "ASL - People & Relationships", description: "Real ASL sign \"Boy\", from Microsoft's ASL Citizen dataset (gloss BOY).", emoji: "👦" },
  { slug: "girl", label: "Girl", language: "ASL", category: "ASL - People & Relationships", description: "Real ASL sign \"Girl\", from Microsoft's ASL Citizen dataset (gloss GIRL).", emoji: "👧" },
  { slug: "married", label: "Married", language: "ASL", category: "ASL - People & Relationships", description: "Real ASL sign \"Married\", from Microsoft's ASL Citizen dataset (gloss MARRY -- base verb form, ASL has no separate \"MARRIED\" adjective sign).", emoji: "💍" },
].map((entry) => ({ ...entry, hasVideo: true, hasTrainingData: true }));

module.exports = dictionaryData;
