# SignTalk: all 50 supported signs

Generated from `ai/dataset/sign_metadata.json` by `ai/dataset/export_sign_metadata.py`.

These are **50 distinct signs: 25 Filipino Sign Language (FSL) signs and 25 American Sign Language (ASL) signs.**
They are not English and Filipino labels for the same 25 concepts. The three greetings exist in both languages as
separate signs (for example FSL *Magandang Umaga* and ASL *Good Morning*), each with its own video and model class.

Sign type and hands are the team's reading of the reference videos and are marked *unverified* until an FSL
validator confirms them.

| # | Sign (label) | Language | Category | Gloss | Meaning (English) | Meaning (Filipino) | Hands | Type | Reference video source | Validation |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | Isa (`isa`) | FSL | Numbers | ONE | One | Isa | one | static | FSL-105 dataset (DLSU / DOST, CC BY 4.0) | unverified |
| 2 | Dalawa (`dalawa`) | FSL | Numbers | TWO | Two | Dalawa | one | static | FSL-105 dataset (DLSU / DOST, CC BY 4.0) | unverified |
| 3 | Tatlo (`tatlo`) | FSL | Numbers | THREE | Three | Tatlo | one | static | FSL-105 dataset (DLSU / DOST, CC BY 4.0) | unverified |
| 4 | Apat (`apat`) | FSL | Numbers | FOUR | Four | Apat | one | static | FSL-105 dataset (DLSU / DOST, CC BY 4.0) | unverified |
| 5 | Lima (`lima`) | FSL | Numbers | FIVE | Five | Lima | one | static | FSL-105 dataset (DLSU / DOST, CC BY 4.0) | unverified |
| 6 | Anim (`anim`) | FSL | Numbers | SIX | Six | Anim | one | static | FSL-105 dataset (DLSU / DOST, CC BY 4.0) | unverified |
| 7 | Pito (`pito`) | FSL | Numbers | SEVEN | Seven | Pito | one | static | FSL-105 dataset (DLSU / DOST, CC BY 4.0) | unverified |
| 8 | Walo (`walo`) | FSL | Numbers | EIGHT | Eight | Walo | one | static | FSL-105 dataset (DLSU / DOST, CC BY 4.0) | unverified |
| 9 | Siyam (`siyam`) | FSL | Numbers | NINE | Nine | Siyam | one | static | FSL-105 dataset (DLSU / DOST, CC BY 4.0) | unverified |
| 10 | Sampu (`sampu`) | FSL | Numbers | TEN | Ten | Sampu | one | dynamic | FSL-105 dataset (DLSU / DOST, CC BY 4.0) | unverified |
| 11 | Asul (`asul`) | FSL | Colors | BLUE | Blue | Asul | one | dynamic | FSL-105 dataset (DLSU / DOST, CC BY 4.0) | unverified |
| 12 | Berde (`berde`) | FSL | Colors | GREEN | Green | Berde | one | dynamic | FSL-105 dataset (DLSU / DOST, CC BY 4.0) | unverified |
| 13 | Pula (`pula`) | FSL | Colors | RED | Red | Pula | one | dynamic | FSL-105 dataset (DLSU / DOST, CC BY 4.0) | unverified |
| 14 | Kayumanggi (`kayumanggi`) | FSL | Colors | BROWN | Brown | Kayumanggi | two | dynamic | FSL-105 dataset (DLSU / DOST, CC BY 4.0) | unverified |
| 15 | Itim (`itim`) | FSL | Colors | BLACK | Black | Itim | one | dynamic | FSL-105 dataset (DLSU / DOST, CC BY 4.0) | unverified |
| 16 | Puti (`puti`) | FSL | Colors | WHITE | White | Puti | one | dynamic | FSL-105 dataset (DLSU / DOST, CC BY 4.0) | unverified |
| 17 | Dilaw (`dilaw`) | FSL | Colors | YELLOW | Yellow | Dilaw | one | dynamic | FSL-105 dataset (DLSU / DOST, CC BY 4.0) | unverified |
| 18 | Kahel (`kahel`) | FSL | Colors | ORANGE | Orange | Kahel | one | dynamic | FSL-105 dataset (DLSU / DOST, CC BY 4.0) | unverified |
| 19 | Abo (`abo`) | FSL | Colors | GRAY | Gray | Abo | two | dynamic | FSL-105 dataset (DLSU / DOST, CC BY 4.0) | unverified |
| 20 | Rosas (`rosas`) | FSL | Colors | PINK | Pink | Rosas | one | dynamic | FSL-105 dataset (DLSU / DOST, CC BY 4.0) | unverified |
| 21 | Lila (`lila`) | FSL | Colors | VIOLET | Violet | Lila | two | dynamic | FSL-105 dataset (DLSU / DOST, CC BY 4.0) | unverified |
| 22 | Maliwanag (`maliwanag`) | FSL | Colors | LIGHT | Light (color) | Maliwanag | two | dynamic | FSL-105 dataset (DLSU / DOST, CC BY 4.0) | unverified |
| 23 | Magandang Umaga (`magandang_umaga`) | FSL | Greetings | GOOD MORNING | Good morning | Magandang umaga | one | dynamic | FSL-105 dataset (DLSU / DOST, CC BY 4.0) | unverified |
| 24 | Magandang Hapon (`magandang_hapon`) | FSL | Greetings | GOOD AFTERNOON | Good afternoon | Magandang hapon | one | dynamic | FSL-105 dataset (DLSU / DOST, CC BY 4.0) | unverified |
| 25 | Magandang Gabi (`magandang_gabi`) | FSL | Greetings | GOOD EVENING | Good evening | Magandang gabi | one | dynamic | FSL-105 dataset (DLSU / DOST, CC BY 4.0) | unverified |
| 26 | Good Morning (`good_morning`) | ASL | Greetings | GOOD MORNING | Good morning | Magandang umaga | two | dynamic | ASL Citizen (Microsoft Research), joined from separate word clips | unverified |
| 27 | Good Afternoon (`good_afternoon`) | ASL | Greetings | GOOD AFTERNOON | Good afternoon | Magandang hapon | two | dynamic | ASL Citizen (Microsoft Research), joined from separate word clips | unverified |
| 28 | Good Evening (`good_evening`) | ASL | Greetings | GOOD NIGHT | Good evening | Magandang gabi | two | dynamic | ASL Citizen (Microsoft Research), joined from separate word clips | unverified |
| 29 | Hello (`hello`) | ASL | Greetings | HELLO | Hello | Hello / Kumusta | one | dynamic | ASL Citizen (Microsoft Research) | unverified |
| 30 | How Are You (`how_are_you`) | ASL | Greetings | HOW YOU | How are you? | Kumusta ka? | two | dynamic | ASL Citizen (Microsoft Research), joined from separate word clips | unverified |
| 31 | I'm Fine (`im_fine`) | ASL | Greetings | FINE | I'm fine | Mabuti naman ako | one | dynamic | ASL Citizen (Microsoft Research) | unverified |
| 32 | Nice To Meet You (`nice_to_meet_you`) | ASL | Greetings | NICE MEET YOU | Nice to meet you | Ikinagagalak kitang makilala | two | dynamic | ASL Citizen (Microsoft Research), joined from separate word clips | unverified |
| 33 | Thank You (`thank_you`) | ASL | Greetings | THANKYOU | Thank you | Salamat | one | dynamic | ASL Citizen (Microsoft Research) | unverified |
| 34 | You're Welcome (`youre_welcome`) | ASL | Greetings | WELCOME | You're welcome | Walang anuman | one | dynamic | ASL Citizen (Microsoft Research) | unverified |
| 35 | See You Tomorrow (`see_you_tomorrow`) | ASL | Greetings | SEE YOU TOMORROW | See you tomorrow | Kita tayo bukas | one | dynamic | ASL Citizen (Microsoft Research), joined from separate word clips | unverified |
| 36 | Understand (`understand`) | ASL | Basic Responses | UNDERSTAND | Understand | Naiintindihan | one | dynamic | ASL Citizen (Microsoft Research) | unverified |
| 37 | Don't Understand (`dont_understand`) | ASL | Basic Responses | NOT-UNDERSTAND | Don't understand | Hindi naiintindihan | one | dynamic | ASL Citizen (Microsoft Research) | unverified |
| 38 | Know (`know`) | ASL | Basic Responses | KNOW | Know | Alam | one | dynamic | ASL Citizen (Microsoft Research) | unverified |
| 39 | Don't Know (`dont_know`) | ASL | Basic Responses | DONT-KNOW | Don't know | Hindi alam | one | dynamic | ASL Citizen (Microsoft Research) | unverified |
| 40 | Yes (`yes`) | ASL | Basic Responses | YES | Yes | Oo | one | dynamic | ASL Citizen (Microsoft Research) | unverified |
| 41 | No (`no`) | ASL | Basic Responses | NO | No | Hindi | one | dynamic | ASL Citizen (Microsoft Research) | unverified |
| 42 | Wrong (`wrong`) | ASL | Basic Responses | WRONG | Wrong | Mali | one | dynamic | ASL Citizen (Microsoft Research) | unverified |
| 43 | Correct (`correct`) | ASL | Basic Responses | RIGHT | Correct | Tama | two | dynamic | ASL Citizen (Microsoft Research) | unverified |
| 44 | Slow (`slow`) | ASL | Basic Responses | SLOW | Slow | Mabagal | two | dynamic | ASL Citizen (Microsoft Research) | unverified |
| 45 | Fast (`fast`) | ASL | Basic Responses | FAST | Fast | Mabilis | two | dynamic | ASL Citizen (Microsoft Research) | unverified |
| 46 | Father (`father`) | ASL | Family | FATHER | Father | Ama / Tatay | one | dynamic | ASL Citizen (Microsoft Research) | unverified |
| 47 | Mother (`mother`) | ASL | Family | MOTHER | Mother | Ina / Nanay | one | dynamic | ASL Citizen (Microsoft Research) | unverified |
| 48 | Boy (`boy`) | ASL | People & Relationships | BOY | Boy | Batang lalaki | one | dynamic | ASL Citizen (Microsoft Research) | unverified |
| 49 | Girl (`girl`) | ASL | People & Relationships | GIRL | Girl | Batang babae | one | dynamic | ASL Citizen (Microsoft Research) | unverified |
| 50 | Married (`married`) | ASL | People & Relationships | MARRY | Married | Kasal / May asawa | two | dynamic | ASL Citizen (Microsoft Research) | unverified |
