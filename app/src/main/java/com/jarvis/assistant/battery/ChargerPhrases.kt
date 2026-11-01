package com.jarvis.assistant.battery

import java.util.Locale

object ChargerPhrases {
    val languages = mapOf("English" to "en-US", "Hindi" to "hi-IN", "Hinglish" to "hi-IN",
        "Bengali" to "bn-BD", "Telugu" to "te-IN", "Tamil" to "ta-IN", "Kannada" to "kn-IN",
        "Malayalam" to "ml-IN", "Marathi" to "mr-IN", "Gujarati" to "gu-IN", "Bhojpuri" to "bho-IN",
        "Punjabi" to "pa-IN", "Odia" to "or-IN", "Urdu" to "ur-IN")
    private val phrases = mapOf(
        "en" to listOf("Charger connected. Charging.", "Battery full. Please unplug the charger.", "Charger disconnected.", "Charger connected."),
        "hi" to listOf("चार्जर लगा दिया गया है। चार्ज हो रहा है।", "बैटरी पूरी चार्ज हो गई है। चार्जर निकाल दीजिए।", "चार्जर निकाल दिया गया है।", "चार्जर लगा दिया गया है।"),
        "bn" to listOf("চার্জার লাগানো হয়েছে। চার্জ হচ্ছে।", "চার্জ পূর্ণ হয়েছে। চার্জার খুলে ফেলুন।", "চার্জার খুলে ফেলা হয়েছে।", "চার্জার লাগানো হয়েছে।"),
        "te" to listOf("ఛార్జర్ కనెక్ట్ అయింది. ఛార్జింగ్ అవుతోంది.", "బ్యాటరీ పూర్తిగా ఛార్జ్ అయింది. ఛార్జర్ తీసేయండి.", "ఛార్జర్ తీసివేయబడింది.", "ఛార్జర్ కనెక్ట్ అయింది."),
        "ta" to listOf("சார்ஜர் இணைக்கப்பட்டது. சார்ஜ் ஆகிறது.", "பேட்டரி முழுமையாக சார்ஜ் ஆனது. சார்ஜரை அகற்றுங்கள்.", "சார்ஜர் அகற்றப்பட்டது.", "சார்ஜர் இணைக்கப்பட்டது."),
        "kn" to listOf("ಚಾರ್ಜರ್ ಸಂಪರ್ಕಿಸಲಾಗಿದೆ. ಚಾರ್ಜ್ ಆಗುತ್ತಿದೆ.", "ಬ್ಯಾಟರಿ ಪೂರ್ಣ ಚಾರ್ಜ್ ಆಗಿದೆ. ಚಾರ್ಜರ್ ತೆಗೆಯಿರಿ.", "ಚಾರ್ಜರ್ ತೆಗೆಯಲಾಗಿದೆ.", "ಚಾರ್ಜರ್ ಸಂಪರ್ಕಿಸಲಾಗಿದೆ."),
        "ml" to listOf("ചാർജർ കണക്റ്റ് ചെയ്തു. ചാർജ് ചെയ്യുന്നു.", "ബാറ്ററി പൂർണ്ണമായി ചാർജ് ആയി. ചാർജർ ഊരുക.", "ചാർജർ ഊരിയിരിക്കുന്നു.", "ചാർജർ കണക്റ്റ് ചെയ്തു."),
        "mr" to listOf("चार्जर जोडला आहे. चार्ज होत आहे.", "बॅटरी पूर्ण चार्ज झाली आहे. चार्जर काढा.", "चार्जर काढला आहे.", "चार्जर जोडला आहे."),
        "gu" to listOf("ચાર્જર જોડાયું છે. ચાર્જ થઈ રહ્યું છે.", "બેટરી સંપૂર્ણ ચાર્જ થઈ ગઈ છે. ચાર્જર કાઢો.", "ચાર્જર કાઢવામાં આવ્યું છે.", "ચાર્જર જોડાયું છે."),
        "bho" to listOf("चार्जर लाग गइल बा. चार्ज हो रहल बा.", "बैटरी पूरा चार्ज हो गइल बा. चार्जर निकाल दीं.", "चार्जर निकल गइल बा.", "चार्जर लाग गइल बा."),
        "pa" to listOf("ਚਾਰਜਰ ਲੱਗ ਗਿਆ ਹੈ। ਚਾਰਜ ਹੋ ਰਿਹਾ ਹੈ।", "ਬੈਟਰੀ ਪੂਰੀ ਚਾਰਜ ਹੋ ਗਈ ਹੈ। ਚਾਰਜਰ ਕੱਢ ਦਿਓ।", "ਚਾਰਜਰ ਕੱਢ ਦਿੱਤਾ ਗਿਆ ਹੈ।", "ਚਾਰਜਰ ਲੱਗ ਗਿਆ ਹੈ।"),
        "or" to listOf("ଚାର୍ଜର ଲଗାଯାଇଛି। ଚାର୍ଜ ହେଉଛି।", "ବ୍ୟାଟେରୀ ପୂରା ଚାର୍ଜ ହୋଇଛି। ଚାର୍ଜର କାଢ଼ନ୍ତୁ।", "ଚାର୍ଜର କାଢ଼ି ଦିଆଯାଇଛି।", "ଚାର୍ଜର ଲଗାଯାଇଛି।"),
        "ur" to listOf("چارجر لگا دیا گیا ہے۔ چارج ہو رہا ہے۔", "بیٹری مکمل چارج ہو گئی ہے۔ چارجر نکال دیں۔", "چارجر نکال دیا گیا ہے۔", "چارجر لگا دیا گیا ہے۔")
    )
    fun locale(setting: String): Locale = Locale.forLanguageTag(languages[setting] ?: "hi-IN")
    fun all(locale: Locale): Map<String, String> {
        val text = phrases[locale.language] ?: return emptyMap()
        return listOf("charging", "full", "unplugged", "connected").zip(text).toMap()
    }
}