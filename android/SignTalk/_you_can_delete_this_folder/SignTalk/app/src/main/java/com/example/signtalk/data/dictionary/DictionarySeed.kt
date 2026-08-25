package com.example.signtalk.data.dictionary

/**
 * Starter content for the 12 FSL signs currently in scope for the capstone
 * (see the `LABELS` list in `ai/dataset/collect_data.py` in the main
 * SignTalk repo). Descriptions here are plain-language glosses meant as
 * placeholder dictionary copy -- swap in reviewed FSL descriptions (and,
 * ideally, short reference clips/images) before shipping.
 */
val seedDictionaryEntries: List<DictionaryEntity> = listOf(
    DictionaryEntity(0, "hello", "Hello", "Greetings", "An open hand raised near the head, often with a small wave. Used to greet someone.", "👋", false),
    DictionaryEntity(0, "thank_you", "Thank You", "Courtesy", "Fingers touch the chin, then move forward and down toward the person being thanked.", "🙏", false),
    DictionaryEntity(0, "yes", "Yes", "Basic Responses", "A closed fist nods up and down at the wrist, like a small nodding head.", "✅", false),
    DictionaryEntity(0, "no", "No", "Basic Responses", "Index and middle finger snap together against the thumb, similar to a head shake.", "❌", false),
    DictionaryEntity(0, "good", "Good", "Descriptions", "An open hand starts at the chin and moves down and out into the other palm.", "👍", false),
    DictionaryEntity(0, "please", "Please", "Courtesy", "A flat hand circles on the chest, palm facing the signer.", "🙌", false),
    DictionaryEntity(0, "sorry", "Sorry", "Courtesy", "A closed fist circles on the chest, similar to rubbing an apology in.", "😔", false),
    DictionaryEntity(0, "goodbye", "Goodbye", "Greetings", "An open hand waves side to side, palm facing outward.", "👋", false),
    DictionaryEntity(0, "help", "Help", "Basic Responses", "One fist rests on the opposite flat palm, then both lift together.", "🤝", false),
    DictionaryEntity(0, "i_love_you", "I Love You", "Expressions", "Thumb, index finger, and pinky extended -- a combined handshape for I, L, and Y.", "❤️", false),
    DictionaryEntity(0, "name", "Name", "Basic Responses", "Index and middle finger of one hand tap across the index and middle finger of the other, twice.", "📛", false),
    DictionaryEntity(0, "water", "Water", "Everyday Needs", "The 'W' handshape (index, middle, ring fingers) taps twice near the chin/mouth.", "💧", false)
)
