package com.shs.calendar.dua

/**
 * Curated collection of well-known, correctly-attributed duas.
 * Sources: The Noble Quran (with surah:ayah) and established hadith collections
 * (Sahih al-Bukhari, Sahih Muslim, Sunan Abu Dawud, Jami' at-Tirmidhi, Sunan Ibn Majah).
 * No invented religious content: every entry is a widely published, verifiable supplication.
 */
data class Dua(
    val title: String,
    val arabic: String,
    val transliteration: String,
    val meaningBengali: String,
    val meaningEnglish: String,
    val reference: String,
    val category: String
)

object DuaCollection {

    val categories: List<String> =
        listOf("Morning & Evening", "Daily Life", "Food", "Sleep", "Travel", "Forgiveness", "Protection", "Knowledge", "Distress", "Mosque")

    val all: List<Dua> = listOf(
        // ---------- Morning & Evening ----------
        Dua(
            "Morning remembrance",
            "اللَّهُمَّ بِكَ أَصْبَحْنَا وَبِكَ أَمْسَيْنَا، وَبِكَ نَحْيَا وَبِكَ نَمُوتُ، وَإِلَيْكَ النُّشُورُ",
            "Allahumma bika asbahna wa bika amsayna, wa bika nahya wa bika namutu, wa ilaykan-nushur",
            "হে আল্লাহ, আপনার নামেই আমরা ভোর করি ও সন্ধ্যা করি, আপনার নামেই জীবন ধারণ করি ও মরি, এবং আপনারই দিকে প্রত্যাবর্তন।",
            "O Allah, by You we enter the morning and the evening, by You we live and die, and to You is the resurrection.",
            "Jami' at-Tirmidhi 3391", "Morning & Evening"
        ),
        Dua(
            "Evening remembrance",
            "اللَّهُمَّ بِكَ أَمْسَيْنَا وَبِكَ أَصْبَحْنَا، وَبِكَ نَحْيَا وَبِكَ نَمُوتُ، وَإِلَيْكَ الْمَصِيرُ",
            "Allahumma bika amsayna wa bika asbahna, wa bika nahya wa bika namutu, wa ilaykal-masir",
            "হে আল্লাহ, আপনার নামেই আমরা সন্ধ্যা করি ও ভোর করি, আপনার নামেই জীবন ধারণ করি ও মরি, এবং আপনারই দিকে প্রত্যাবর্তন।",
            "O Allah, by You we enter the evening and the morning, by You we live and die, and to You is the return.",
            "Jami' at-Tirmidhi 3391", "Morning & Evening"
        ),
        Dua(
            "Protection day and night",
            "أَعُوذُ بِكَلِمَاتِ اللَّهِ التَّامَّاتِ مِنْ شَرِّ مَا خَلَقَ",
            "A'udhu bikalimatillahit-tammati min sharri ma khalaq",
            "আল্লাহর পূর্ণাঙ্গ কালিমাসমূহ দ্বারা আমি তাঁর সৃষ্ট সব কুৎসিত জিনিসের অনিষ্ট থেকে আশ্রয় চাই।",
            "I seek refuge in the perfect words of Allah from the evil of what He created.",
            "Sahih Muslim 2709", "Morning & Evening"
        ),
        Dua(
            "Allah suffices me",
            "حَسْبِيَ اللَّهُ لَا إِلَهَ إِلَّا هُوَ، عَلَيْهِ تَوَكَّلْتُ، وَهُوَ رَبُّ الْعَرْشِ الْعَظِيمِ",
            "Hasbiyallahu la ilaha illa huwa, 'alayhi tawakkaltu, wa huwa rabbul-'arshil-'azim",
            "আল্লাহই আমার জন্য যথেষ্ট, তিনি ছাড়া কোনো উপাস্য নেই; তাঁর উপর আমি ভরসা করলাম, তিনি মহাসিংহাসনের প্রতিপালক।",
            "Allah is sufficient for me; there is no god but Him. In Him I trust, and He is the Lord of the Mighty Throne.",
            "Sunan Abu Dawud 5081; Quran 9:129", "Morning & Evening"
        ),
        // ---------- Daily Life ----------
        Dua(
            "Before eating",
            "بِسْمِ اللَّهِ",
            "Bismillah",
            "আল্লাহর নামে (শুরু করছি)।",
            "In the name of Allah.",
            "Sunan Abu Dawud 3767", "Daily Life"
        ),
        Dua(
            "If one forgets before eating",
            "بِسْمِ اللَّهِ فِي أَوَّلِهِ وَآخِرِهِ",
            "Bismillahi fi awwalihi wa akhirih",
            "শুরুতে ও শেষে আল্লাহর নাম।",
            "In the name of Allah at the beginning and at the end.",
            "Jami' at-Tirmidhi 1858", "Daily Life"
        ),
        Dua(
            "Wearing new clothes",
            "اللَّهُمَّ لَكَ الْحَمْدُ أَنْتَ كَسَوْتَنِيهِ، أَسْأَلُكَ مِنْ خَيْرِهِ وَخَيْرِ مَا صُنِعَ لَهُ",
            "Allahumma lakal-hamdu anta kasawtanihi, as'aluka min khayrihi wa khayri ma suni'a lah",
            "হে আল্লাহ, সব প্রশংসা আপনার; আপনিই আমাকে এটি পরিয়েছেন। আমি এর কল্যাণ ও এটি যার জন্য তৈরি তার কল্যাণ চাই।",
            "O Allah, praise is Yours; You clothed me with it. I ask You for its goodness and the goodness for which it was made.",
            "Sunan Abu Dawud 4020", "Daily Life"
        ),
        Dua(
            "Entering the home",
            "بِسْمِ اللَّهِ وَلَجْنَا، وَبِسْمِ اللَّهِ خَرَجْنَا، وَعَلَى رَبِّنَا تَوَكَّلْنَا",
            "Bismillahi walajna, wa bismillahi kharajna, wa 'ala Rabbina tawakkalna",
            "আল্লাহর নামে আমরা প্রবেশ করলাম, আল্লাহর নামে আমরা বের হব, এবং আমরা আমাদের প্রতিপালকের উপর ভরসা করলাম।",
            "In the name of Allah we enter, in the name of Allah we leave, and upon our Lord we rely.",
            "Sunan Abu Dawud 5096", "Daily Life"
        ),
        Dua(
            "Leaving the home",
            "بِسْمِ اللَّهِ، تَوَكَّلْتُ عَلَى اللَّهِ، وَلَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللَّهِ",
            "Bismillahi, tawakkaltu 'ala Allah, wa la hawla wa la quwwata illa billah",
            "আল্লাহর নামে; আমি আল্লাহর উপর ভরসা করলাম; আল্লাহ ছাড়া কোনো শক্তি নেই।",
            "In the name of Allah; I place my trust in Allah; there is no might nor power except with Allah.",
            "Jami' at-Tirmidhi 3426", "Daily Life"
        ),
        Dua(
            "Goodness in both worlds",
            "رَبَّنَا آتِنَا فِي الدُّنْيَا حَسَنَةً وَفِي الْآخِرَةِ حَسَنَةً وَقِنَا عَذَابَ النَّارِ",
            "Rabbana atina fid-dunya hasanatan wa fil-akhirati hasanatan wa qina 'adhaban-nar",
            "হে আমাদের প্রতিপালক, দুনিয়ায় কল্যাণ দান করুন, আখিরাতে কল্যাণ দান করুন এবং জাহান্নামের আযাব থেকে রক্ষা করুন।",
            "Our Lord, give us good in this world and good in the Hereafter, and protect us from the punishment of the Fire.",
            "Quran 2:201", "Daily Life"
        ),
        // ---------- Food ----------
        Dua(
            "After eating",
            "الْحَمْدُ لِلَّهِ الَّذِي أَطْعَمَنَا وَسَقَانَا وَجَعَلَنَا مُسْلِمِينَ",
            "Alhamdulillahil-ladhi at'amana wa saqana wa ja'alana muslimin",
            "সমস্ত প্রশংসা আল্লাহর, যিনি আমাদের খাওয়ালেন, পান করালেন এবং মুসলিম করেছেন।",
            "Praise be to Allah who fed us, gave us drink, and made us Muslims.",
            "Sunan Abu Dawud 3850", "Food"
        ),
        Dua(
            "Breaking the fast",
            "ذَهَبَ الظَّمَأُ وَابْتَلَّتِ الْعُرُوقُ وَثَبَتَ الْأَجْرُ إِنْ شَاءَ اللَّهُ",
            "Dhahabaz-zama'u wabtallatil-'uruqu wa thabatal-ajru in sha Allah",
            "পিপাসা দূর হলো, শিরাসমূহ সিক্ত হলো এবং সওয়াব নির্ধারিত হলো — আল্লাহ চাইলে।",
            "Thirst is gone, the veins are moistened, and the reward is confirmed, if Allah wills.",
            "Sunan Abu Dawud 2357", "Food"
        ),
        // ---------- Sleep ----------
        Dua(
            "Before sleeping",
            "بِاسْمِكَ اللَّهُمَّ أَمُوتُ وَأَحْيَا",
            "Bismika Allahumma amutu wa ahya",
            "হে আল্লাহ, আপনার নামে আমি মরি (ঘুমাই) এবং জীবিত হই (জাগই)।",
            "In Your name, O Allah, I die and I live.",
            "Sahih al-Bukhari 6324", "Sleep"
        ),
        Dua(
            "Upon waking",
            "الْحَمْدُ لِلَّهِ الَّذِي أَحْيَانَا بَعْدَ مَا أَمَاتَنَا وَإِلَيْهِ النُّشُورُ",
            "Alhamdulillahil-ladhi ahyana ba'da ma amatana wa ilayhin-nushur",
            "সমস্ত প্রশংসা আল্লাহর, যিনি আমাদের মৃত্যু (ঘুম) এর পর জীবিত করেছেন এবং তাঁরই দিকে প্রত্যাবর্তন।",
            "Praise be to Allah who gave us life after death (sleep), and to Him is the resurrection.",
            "Sahih al-Bukhari 6312", "Sleep"
        ),
        Dua(
            "Protection from punishment",
            "اللَّهُمَّ قِنِي عَذَابَكَ يَوْمَ تَبْعَثُ عِبَادَكَ",
            "Allahumma qini 'adhabaka yawma tab'athu 'ibadak",
            "হে আল্লাহ, আপনি যেদিন আপনার বান্দাদের উত্থাপন করবেন সেদিন আমাকে আপনার আযাব থেকে রক্ষা করুন।",
            "O Allah, protect me from Your punishment on the Day You resurrect Your servants.",
            "Sahih al-Bukhari 6306, Sahih Muslim 2719", "Sleep"
        ),
        // ---------- Travel ----------
        Dua(
            "Boarding a vehicle",
            "سُبْحَانَ الَّذِي سَخَّرَ لَنَا هَذَا وَمَا كُنَّا لَهُ مُقْرِنِينَ، وَإِنَّا إِلَى رَبِّنَا لَمُنْقَلِبُونَ",
            "Subhanal-ladhi sakhkhara lana hadha wa ma kunna lahu muqrinin, wa inna ila Rabbina lamunqalibun",
            "পবিত্র সেই সত্তা, যিনি এটিকে আমাদের অধীন করেছেন; আমরা তা দমদম করতে সক্ষম ছিলাম না, আর আমরা আমাদের প্রতিপালকেরই দিকে প্রত্যাবর্তনকারী।",
            "Glory to Him who subjected this to us, and we could never have it by our efforts; and to our Lord we surely return.",
            "Quran 43:13-14; Sahih Muslim 1342", "Travel"
        ),
        Dua(
            "Travel supplication",
            "اللَّهُمَّ إِنَّا نَسْأَلُكَ فِي سَفَرِنَا هَذَا الْبِرَّ وَالتَّقْوَى، وَمِنَ الْعَمَلِ مَا تَرْضَى",
            "Allahumma inna nas'aluka fi safarina hadhal-birra wat-taqwa, wa minal-'amali ma tarda",
            "হে আল্লাহ, আমরা এই সফরে আপনার কাছে সৎকর্ম ও তাকওয়া এবং এমন আমল চাই যা আপনি পছন্দ করেন।",
            "O Allah, we ask You on this journey for righteousness, piety, and deeds that please You.",
            "Sahih Muslim 1342", "Travel"
        ),
        Dua(
            "Arriving at a place",
            "أَعُوذُ بِكَلِمَاتِ اللَّهِ التَّامَّاتِ مِنْ شَرِّ مَا خَلَقَ",
            "A'udhu bikalimatillahit-tammati min sharri ma khalaq",
            "আল্লাহর পূর্ণাঙ্গ কালিমাসমূহ দ্বারা আমি তাঁর সৃষ্ট সব কুৎসিত জিনিসের অনিষ্ট থেকে আশ্রয় চাই।",
            "I seek refuge in the perfect words of Allah from the evil of what He created.",
            "Sahih Muslim 2708", "Travel"
        ),
        // ---------- Forgiveness ----------
        Dua(
            "Sayyidul Istighfar (master of seeking forgiveness)",
            "اللَّهُمَّ أَنْتَ رَبِّي لَا إِلَهَ إِلَّا أَنْتَ، خَلَقْتَنِي وَأَنَا عَبْدُكَ، وَأَنَا عَلَى عَهْدِكَ وَوَعْدِكَ مَا اسْتَطَعْتُ، أَعُوذُ بِكَ مِنْ شَرِّ مَا صَنَعْتُ، أَبُوءُ لَكَ بِنِعْمَتِكَ عَلَيَّ، وَأَبُوءُ بِذَنْبِي، فَاغْفِرْ لِي، فَإِنَّهُ لَا يَغْفِرُ الذُّنُوبَ إِلَّا أَنْتَ",
            "Allahumma anta Rabbi la ilaha illa anta, khalaqtani wa ana 'abduka, wa ana 'ala 'ahdika wa wa'dika mastata'tu, a'udhu bika min sharri ma sana'tu, abu'u laka bini'matika 'alayya, wa abu'u bidhanbi, faghfir li, fa innahu la yaghfirudh-dhunuba illa anta",
            "হে আল্লাহ, আপনি আমার প্রতিপালক, আপনি ছাড়া কোনো উপাস্য নেই; আপনি আমাকে সৃষ্টি করেছেন, আমি আপনার বান্দা; আমি সাধ্যমতো আপনার প্রতিশ্রুতিতে অটল; আমি আমার কৃতকর্মের অনিষ্ট থেকে আপনার আশ্রয় চাই; আমি আপনার নিয়ামত স্বীকার করছি ও আমার পাপ স্বীকার করছি; অতএব আমাকে ক্ষমা করুন — আপনি ছাড়া পাপ ক্ষমা করার কেউ নেই।",
            "O Allah, You are my Lord; there is no god but You. You created me and I am Your servant; I keep Your covenant as much as I can; I seek refuge in You from the evil of what I have done; I acknowledge Your favor upon me and my sin, so forgive me — none forgives sins but You.",
            "Sahih al-Bukhari 6306", "Forgiveness"
        ),
        Dua(
            "Seeking forgiveness and repenting",
            "أَسْتَغْفِرُ اللَّهَ الَّذِي لَا إِلَهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ وَأَتُوبُ إِلَيْهِ",
            "Astaghfirullahal-ladhi la ilaha illa huwal-hayyul-qayyumu wa atubu ilayh",
            "আমি সেই আল্লাহর কাছে ক্ষমা চাই, যিনি ছাড়া কোনো উপাস্য নেই — যিনি চিরঞ্জীব, সর্ব-সংরক্ষণকারী — এবং তাঁর দিকেই ফিরে যাই।",
            "I seek forgiveness from Allah besides whom there is no god, the Ever-Living, the Sustainer, and I turn to Him in repentance.",
            "Sunan Abu Dawud 1517, Jami' at-Tirmidhi 3577", "Forgiveness"
        ),
        Dua(
            "Adam's repentance",
            "رَبَّنَا ظَلَمْنَا أَنْفُسَنَا وَإِنْ لَمْ تَغْفِرْ لَنَا وَتَرْحَمْنَا لَنَكُونَنَّ مِنَ الْخَاسِرِينَ",
            "Rabbana zalamna anfusana wa in lam taghfir lana wa tarhamna lanakunanna minal-khasirin",
            "হে আমাদের প্রতিপালক, আমরা নিজেদের উপর জুলুম করেছি; আপনি যদি আমাদের ক্ষমা না করেন ও দয়া না করেন, তাহলে আমরা ক্ষতিগ্রস্তদের অন্তর্ভুক্ত হব।",
            "Our Lord, we have wronged ourselves; if You do not forgive us and have mercy on us, we will surely be among the losers.",
            "Quran 7:23", "Forgiveness"
        ),
        Dua(
            "Jonah's supplication",
            "لَا إِلَهَ إِلَّا أَنْتَ سُبْحَانَكَ إِنِّي كُنْتُ مِنَ الظَّالِمِينَ",
            "La ilaha illa anta subhanaka inni kuntu minaz-zalimin",
            "আপনি ছাড়া কোনো উপাস্য নেই; আপনি পবিত্র; আমি নিশ্চয়ই জুলুমকারীদের অন্তর্ভুক্ত হয়েছি।",
            "There is no god but You; glory be to You; indeed I have been among the wrongdoers.",
            "Quran 21:87", "Forgiveness"
        ),
        // ---------- Protection ----------
        Dua(
            "In Allah's name no harm",
            "بِسْمِ اللَّهِ الَّذِي لَا يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الْأَرْضِ وَلَا فِي السَّمَاءِ وَهُوَ السَّمِيعُ الْعَلِيمُ",
            "Bismillahil-ladhi la yadurru ma'asmih shay'un fil-ardi wa la fis-sama'i wa huwas-sami'ul-'alim",
            "আল্লাহর নামে, যাঁর নামের সাথে নভোমণ্ডলে বা পৃথিবীতে কোনো কিছু ক্ষতি করতে পারে না; তিনি সর্বশ্রোতা, সর্বজ্ঞ।",
            "In the name of Allah, with whose name nothing on earth or in heaven can cause harm; He is the All-Hearing, All-Knowing.",
            "Jami' at-Tirmidhi 3388, Sunan Abu Dawud 5088", "Protection"
        ),
        Dua(
            "Allah is our sufficient guardian",
            "حَسْبُنَا اللَّهُ وَنِعْمَ الْوَكِيلُ",
            "Hasbunallahu wa ni'mal-wakil",
            "আল্লাহই আমাদের জন্য যথেষ্ট এবং তিনি উত্তম রক্ষক।",
            "Allah is sufficient for us, and He is the best guardian.",
            "Quran 3:173", "Protection"
        ),
        Dua(
            "Refuge from anxiety, laziness, debt",
            "اللَّهُمَّ إِنِّي أَعُوذُ بِكَ مِنَ الْهَمِّ وَالْحَزَنِ، وَالْعَجْزِ وَالْكَسَلِ، وَالْبُخْلِ وَالْجُبْنِ، وَضَلَعِ الدَّيْنِ وَغَلَبَةِ الرِّجَالِ",
            "Allahumma inni a'udhu bika minal-hammi wal-hazan, wal-'ajzi wal-kasal, wal-bukhli wal-jubn, wa dala'id-dayni wa ghalabatir-rijal",
            "হে আল্লাহ, আমি আপনার কাছে দুশ্চিন্তা ও দুঃখ, অক্ষমতা ও অলসতা, কৃপণতা ও ভীরুতা, ঋণের বোঝা ও পরাজিত হওয়া থেকে আশ্রয় চাই।",
            "O Allah, I seek refuge in You from worry and grief, from incapacity and laziness, from miserliness and cowardice, from the burden of debt and from being overpowered.",
            "Sahih al-Bukhari 6369", "Protection"
        ),
        // ---------- Knowledge ----------
        Dua(
            "Increase in knowledge",
            "رَبِّ زِدْنِي عِلْمًا",
            "Rabbi zidni 'ilma",
            "হে আমার প্রতিপালক, আমার জ্ঞান বৃদ্ধি করুন।",
            "My Lord, increase me in knowledge.",
            "Quran 20:114", "Knowledge"
        ),
        Dua(
            "Beneficial knowledge and provision",
            "اللَّهُمَّ إِنِّي أَسْأَلُكَ عِلْمًا نَافِعًا، وَرِزْقًا طَيِّبًا، وَعَمَلًا مُتَقَبَّلًا",
            "Allahumma inni as'aluka 'ilman nafi'an, wa rizqan tayyiban, wa 'amalan mutaqabbalan",
            "হে আল্লাহ, আমি আপনার কাছে উপকারী জ্ঞান, উত্তম রিজিক এবং গ্রহণীয় আমল চাই।",
            "O Allah, I ask You for beneficial knowledge, wholesome provision, and accepted deeds.",
            "Sunan Ibn Majah 925", "Knowledge"
        ),
        Dua(
            "Guidance, piety, chastity, contentment",
            "اللَّهُمَّ إِنِّي أَسْأَلُكَ الْهُدَى وَالتُّقَى وَالْعَفَافَ وَالْغِنَى",
            "Allahumma inni as'alukal-huda wat-tuqa wal-'afafa wal-ghina",
            "হে আল্লাহ, আমি আপনার কাছে হেদায়েত, তাকওয়া, সংযম ও অভাবমুক্তি চাই।",
            "O Allah, I ask You for guidance, piety, chastity, and self-sufficiency.",
            "Sahih Muslim 2721", "Knowledge"
        ),
        // ---------- Distress ----------
        Dua(
            "Distress: refuge from devils",
            "رَبِّ أَعُوذُ بِكَ مِنْ هَمَزَاتِ الشَّيَاطِينِ، وَأَعُوذُ بِكَ رَبِّ أَنْ يَحْضُرُونِ",
            "Rabbi a'udhu bika min hamazatish-shayatin, wa a'udhu bika Rabbi an yahdurun",
            "হে আমার প্রতিপালক, আমি শয়তানের প্ররোচনা থেকে আপনার আশ্রয় চাই এবং যেন তারা আমার সামনে উপস্থিত না হয় সেজন্য আশ্রয় চাই।",
            "My Lord, I seek refuge in You from the incitements of the devils, and I seek refuge in You, my Lord, lest they come near me.",
            "Quran 23:97-98", "Distress"
        ),
        Dua(
            "Hope in Allah's mercy",
            "اللَّهُمَّ رَحْمَتَكَ أَرْجُو فَلَا تَكِلْنِي إِلَى نَفْسِي، وَأَصْلِحْ لِي شَأْنِي كُلَّهُ، لَا إِلَهَ إِلَّا أَنْتَ",
            "Allahumma rahmataka arju fala takilni ila nafsi, wa aslih li sha'ni kullahu, la ilaha illa anta",
            "হে আল্লাহ, আপনার রহমতই আমি প্রত্যাশা করি; আমাকে আমার নিজের উপর ছেড়ে দেবেন না; আমার সব অবস্থা সংশোধন করুন — আপনি ছাড়া কোনো উপাস্য নেই।",
            "O Allah, I hope for Your mercy; do not leave me to myself; rectify all my affairs; there is no god but You.",
            "Sunan Abu Dawud 5090", "Distress"
        ),
        Dua(
            "Hearts find rest",
            "أَلَا بِذِكْرِ اللَّهِ تَطْمَئِنُّ الْقُلُوبُ",
            "Ala bidhikrillahi tatma'innul-qulub",
            "জেনে রাখো, আল্লাহর স্মরণেই অন্তরসমূহ প্রশান্ত হয়।",
            "Truly, in the remembrance of Allah hearts find rest.",
            "Quran 13:28", "Distress"
        ),
        // ---------- Mosque ----------
        Dua(
            "Entering the mosque",
            "اللَّهُمَّ افْتَحْ لِي أَبْوَابَ رَحْمَتِكَ",
            "Allahumma iftah li abwaba rahmatik",
            "হে আল্লাহ, আমার জন্য আপনার রহমতের দরজা খুলে দিন।",
            "O Allah, open for me the doors of Your mercy.",
            "Sahih Muslim 713", "Mosque"
        ),
        Dua(
            "Leaving the mosque",
            "اللَّهُمَّ إِنِّي أَسْأَلُكَ مِنْ فَضْلِكَ",
            "Allahumma inni as'aluka min fadlik",
            "হে আল্লাহ, আমি আপনার কাছে আপনার অনুগ্রহ থেকে চাই।",
            "O Allah, I ask You from Your bounty.",
            "Sahih Muslim 713", "Mosque"
        ),
        Dua(
            "After the adhan",
            "اللَّهُمَّ رَبَّ هَذِهِ الدَّعْوَةِ التَّامَّةِ وَالصَّلَاةِ الْقَائِمَةِ، آتِ مُحَمَّدًا الْوَسِيلَةَ وَالْفَضِيلَةَ",
            "Allahumma rabba hadhihid-da'watit-tammati was-salatil-qa'imah, ati Muhammadanil-wasilata wal-fadilah",
            "হে আল্লাহ, এই পূর্ণ আহ্বান ও প্রতিষ্ঠিত সালাতের প্রতিপালক, আপনি মুহাম্মদ (সা.)-কে ওয়াসিলা ও শ্রেষ্ঠত্ব দান করুন।",
            "O Allah, Lord of this perfect call and established prayer, grant Muhammad the intercession and favor.",
            "Sahih al-Bukhari 614", "Mosque"
        )
    )

    fun byCategory(category: String): List<Dua> = all.filter { it.category == category }

    fun search(query: String): List<Dua> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return all
        return all.filter {
            it.title.lowercase().contains(q) ||
                it.transliteration.lowercase().contains(q) ||
                it.meaningBengali.contains(q) ||
                it.meaningEnglish.lowercase().contains(q)
        }
    }
}
