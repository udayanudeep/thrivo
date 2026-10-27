package app.thrivo

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

// ---------------- App icons (activity-alias switching) ----------------
val ICONS = listOf("IconDefault" to "Electric", "IconDark" to "Midnight", "IconYellow" to "Sunshine")

fun setAppIcon(ctx: Context, alias: String) {
    ICONS.forEach { (a, _) ->
        ctx.packageManager.setComponentEnabledSetting(ComponentName(ctx, "app.thrivo.$a"),
            if (a == alias) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
    }
    ctx.getSharedPreferences("thrivo", Context.MODE_PRIVATE).edit().putString("icon", alias).apply()
}

// ---------------- Themes ----------------
val BAR_THEMES = listOf("White" to Color.White, "Sunshine" to Color(0xFFFFF200), "Aqua" to Color(0xFF3FE6FF), "Flame" to Color(0xFFFF6A1A))
var StepsBar by mutableStateOf(Color.White)

/** Focus Mode swaps the electric blue for a calm near-black. */
fun applyTheme(focus: Boolean, bar: Int) {
    Brand = if (focus) Color(0xFF0E0E16) else Color(0xFF2B1EF5)
    BrandDeep = if (focus) Color(0xFF000000) else Color(0xFF1E14C8)
    Section = if (focus) Color(0xFF1E1E2C) else Color(0xFF3A5BFF)
    Faint = if (focus) Color(0xFF34344A) else Color(0xFF4A5CFF)
    StepsBar = BAR_THEMES.getOrElse(bar) { BAR_THEMES[0] }.second
}

// ---------------- Languages ----------------
var Lang by mutableStateOf("en")
val LANGS = listOf("en" to "English", "hi" to "हिन्दी", "te" to "తెలుగు")

/** Exact-match UI label translations; anything not listed stays in English. */
private val HI = mapOf(
    "Day" to "दिन", "Week" to "सप्ताह", "Month" to "महीना", "By hour" to "घंटे के हिसाब से", "By day" to "दिन के हिसाब से", "+ Add steps" to "+ कदम जोड़ें",
    "TODAY" to "आज", "Today" to "आज", "Steps this year" to "इस साल के कदम", "Distance this year" to "इस साल की दूरी", "Share" to "साझा करें",
    "Water goal and reminders" to "पानी का लक्ष्य और रिमाइंडर", "Your plan\nfor today" to "आज की\nआपकी योजना", "Daily Step Goal" to "रोज़ का कदम लक्ष्य",
    "Last 30 days" to "पिछले 30 दिन", "Share your streak" to "अपना सिलसिला साझा करें", "Cardio Fitness" to "कार्डियो फिटनेस", "Sleep Profile" to "नींद प्रोफ़ाइल",
    "Reports" to "रिपोर्ट", "Cycle" to "मासिक चक्र", "Meds" to "दवाइयाँ", "Thrivo Coach AI" to "थ्रिवो कोच AI", "The science" to "विज्ञान", "The Science" to "विज्ञान",
    "Steps Theme" to "कदम थीम", "App Icon" to "ऐप आइकन", "Focus Mode (dark)" to "फोकस मोड (डार्क)", "Notifications" to "सूचनाएँ", "Appearance" to "रूप",
    "Morning briefing" to "सुबह का सारांश", "Briefing time" to "सारांश का समय", "Wind-down reminder" to "सोने से पहले रिमाइंडर", "Medication reminders" to "दवा रिमाइंडर",
    "Step source" to "कदमों का स्रोत", "My Shoes" to "मेरे जूते", "Export data (CSV)" to "डेटा निर्यात (CSV)", "Back up settings" to "सेटिंग्स का बैकअप",
    "Restore settings" to "सेटिंग्स बहाल करें", "Health Connect" to "हेल्थ कनेक्ट", "Weekly & Monthly Reports" to "साप्ताहिक और मासिक रिपोर्ट",
    "Loading your health data…" to "आपका स्वास्थ्य डेटा लोड हो रहा है…", "every step" to "हर कदम", "counts" to "मायने रखता है", "About you" to "आपके बारे में",
    "Your goals" to "आपके लक्ष्य", "Resting HR" to "आराम की हृदय गति", "Breathing" to "साँस", "Weight" to "वज़न", "Avg sleep" to "औसत नींद",
    "Stress monitor" to "तनाव मॉनिटर", "Weekly challenge" to "साप्ताहिक चुनौती", "Share my code" to "मेरा कोड साझा करें", "Add friend" to "दोस्त जोड़ें",
    "Log a drink" to "पेय दर्ज करें", "Track a walk" to "सैर ट्रैक करें", "Back" to "वापस", "Close" to "बंद करें",
    "Steps" to "कदम", "Water" to "पानी", "Plan" to "योजना", "Health" to "स्वास्थ्य", "Settings" to "सेटिंग्स",
    "Add" to "जोड़ें", "Distance" to "दूरी", "Calories" to "कैलोरी", "Floors" to "मंज़िलें", "Completed" to "पूरा",
    "Let's Move!" to "चलिए चलें!", "Your first step awaits." to "आपका पहला कदम इंतज़ार में है।", "Keep Going!" to "चलते रहिए!",
    "Goal crushed!" to "लक्ष्य पूरा!", "Stay Hydrated!" to "पानी पीते रहें!", "Keep the water flowing." to "पानी पीना जारी रखें।",
    "Goal reached!" to "लक्ष्य पूरा हुआ!", "Nicely done today." to "आज बढ़िया किया।", "Streaks" to "सिलसिला", "Current Streak" to "वर्तमान सिलसिला",
    "Longest Streak" to "सबसे लंबा सिलसिला", "Steps to keep streak" to "सिलसिला बनाए रखने के कदम", "Set goal" to "लक्ष्य तय करें",
    "My Goals" to "मेरे लक्ष्य", "Daily Steps" to "रोज़ के कदम", "Water Intake" to "पानी का सेवन", "Smart Walking Plan" to "स्मार्ट वॉकिंग प्लान",
    "Water Progress" to "पानी की प्रगति", "Water Reminder" to "पानी रिमाइंडर", "My Bottle" to "मेरी बोतल", "More Settings" to "और सेटिंग्स",
    "App Settings" to "ऐप सेटिंग्स", "Progress" to "प्रगति", "Badges" to "बैज", "Journal" to "डायरी", "Done" to "हो गया", "Reminders" to "रिमाइंडर",
    "Sleep" to "नींद", "Heart rate" to "हृदय गति", "Workouts" to "वर्कआउट", "Recovery" to "रिकवरी", "Strain" to "स्ट्रेन",
    "Stress" to "तनाव", "Breathe" to "साँस लें", "Breathe in" to "साँस अंदर", "Breathe out" to "साँस बाहर", "Start" to "शुरू", "Stop" to "रोकें",
    "Continue" to "आगे बढ़ें", "Connect Fitbit" to "Fitbit जोड़ें", "Language" to "भाषा", "Unit System" to "इकाई प्रणाली", "Caffeine" to "कैफ़ीन",
    "Body" to "शरीर", "Walk" to "सैर", "Start walk" to "सैर शुरू करें", "Finish walk" to "सैर पूरी करें", "Challenges" to "चुनौतियाँ",
    "Medications" to "दवाइयाँ", "Weekly Summary" to "साप्ताहिक सारांश", "Having trouble with the app?" to "ऐप में दिक्कत है?",
)
private val TE = mapOf(
    "Day" to "రోజు", "Week" to "వారం", "Month" to "నెల", "By hour" to "గంటల వారీగా", "By day" to "రోజుల వారీగా", "+ Add steps" to "+ అడుగులు జోడించు",
    "TODAY" to "ఈరోజు", "Today" to "ఈరోజు", "Steps this year" to "ఈ సంవత్సరం అడుగులు", "Distance this year" to "ఈ సంవత్సరం దూరం", "Share" to "షేర్",
    "Water goal and reminders" to "నీటి లక్ష్యం మరియు రిమైండర్లు", "Your plan\nfor today" to "ఈరోజు\nమీ ప్లాన్", "Daily Step Goal" to "రోజువారీ అడుగుల లక్ష్యం",
    "Last 30 days" to "గత 30 రోజులు", "Share your streak" to "మీ వరుసను షేర్ చేయండి", "Cardio Fitness" to "కార్డియో ఫిట్‌నెస్", "Sleep Profile" to "నిద్ర ప్రొఫైల్",
    "Reports" to "నివేదికలు", "Cycle" to "రుతుచక్రం", "Meds" to "మందులు", "Thrivo Coach AI" to "థ్రివో కోచ్ AI", "The science" to "శాస్త్రం", "The Science" to "శాస్త్రం",
    "Steps Theme" to "అడుగుల థీమ్", "App Icon" to "యాప్ ఐకాన్", "Focus Mode (dark)" to "ఫోకస్ మోడ్ (డార్క్)", "Notifications" to "నోటిఫికేషన్లు", "Appearance" to "రూపం",
    "Morning briefing" to "ఉదయం సారాంశం", "Briefing time" to "సారాంశం సమయం", "Wind-down reminder" to "నిద్రకు ముందు రిమైండర్", "Medication reminders" to "మందుల రిమైండర్లు",
    "Step source" to "అడుగుల మూలం", "My Shoes" to "నా షూస్", "Export data (CSV)" to "డేటా ఎగుమతి (CSV)", "Back up settings" to "సెట్టింగ్‌ల బ్యాకప్",
    "Restore settings" to "సెట్టింగ్‌లు పునరుద్ధరించు", "Health Connect" to "హెల్త్ కనెక్ట్", "Weekly & Monthly Reports" to "వారపు & నెలవారీ నివేదికలు",
    "Loading your health data…" to "మీ ఆరోగ్య డేటా లోడ్ అవుతోంది…", "every step" to "ప్రతి అడుగు", "counts" to "ముఖ్యమే", "About you" to "మీ గురించి",
    "Your goals" to "మీ లక్ష్యాలు", "Resting HR" to "విశ్రాంతి హృదయ స్పందన", "Breathing" to "శ్వాస", "Weight" to "బరువు", "Avg sleep" to "సగటు నిద్ర",
    "Stress monitor" to "ఒత్తిడి మానిటర్", "Weekly challenge" to "వారపు సవాలు", "Share my code" to "నా కోడ్ షేర్ చేయండి", "Add friend" to "స్నేహితుడిని జోడించు",
    "Log a drink" to "పానీయం నమోదు చేయండి", "Track a walk" to "నడకను ట్రాక్ చేయండి", "Back" to "వెనక్కి", "Close" to "మూసివేయి",
    "Steps" to "అడుగులు", "Water" to "నీరు", "Plan" to "ప్లాన్", "Health" to "ఆరోగ్యం", "Settings" to "సెట్టింగ్‌లు",
    "Add" to "జోడించు", "Distance" to "దూరం", "Calories" to "కేలరీలు", "Floors" to "అంతస్తులు", "Completed" to "పూర్తయింది",
    "Let's Move!" to "కదులుదాం!", "Your first step awaits." to "మీ మొదటి అడుగు కోసం ఎదురుచూస్తోంది.", "Keep Going!" to "కొనసాగించండి!",
    "Goal crushed!" to "లక్ష్యం సాధించారు!", "Stay Hydrated!" to "నీరు తాగుతూ ఉండండి!", "Keep the water flowing." to "నీరు తాగడం కొనసాగించండి.",
    "Goal reached!" to "లక్ష్యం చేరుకున్నారు!", "Nicely done today." to "ఈరోజు బాగా చేశారు.", "Streaks" to "వరుస", "Current Streak" to "ప్రస్తుత వరుస",
    "Longest Streak" to "అత్యధిక వరుస", "Steps to keep streak" to "వరుస కొనసాగించడానికి అడుగులు", "Set goal" to "లక్ష్యం పెట్టండి",
    "My Goals" to "నా లక్ష్యాలు", "Daily Steps" to "రోజువారీ అడుగులు", "Water Intake" to "నీటి వినియోగం", "Smart Walking Plan" to "స్మార్ట్ వాకింగ్ ప్లాన్",
    "Water Progress" to "నీటి పురోగతి", "Water Reminder" to "నీటి రిమైండర్", "My Bottle" to "నా బాటిల్", "More Settings" to "మరిన్ని సెట్టింగ్‌లు",
    "App Settings" to "యాప్ సెట్టింగ్‌లు", "Progress" to "పురోగతి", "Badges" to "బ్యాడ్జ్‌లు", "Journal" to "డైరీ", "Done" to "పూర్తి", "Reminders" to "రిమైండర్లు",
    "Sleep" to "నిద్ర", "Heart rate" to "హృదయ స్పందన", "Workouts" to "వ్యాయామాలు", "Recovery" to "రికవరీ", "Strain" to "స్ట్రెయిన్",
    "Stress" to "ఒత్తిడి", "Breathe" to "శ్వాస", "Breathe in" to "శ్వాస పీల్చండి", "Breathe out" to "శ్వాస వదలండి", "Start" to "ప్రారంభించు", "Stop" to "ఆపు",
    "Continue" to "కొనసాగించు", "Connect Fitbit" to "Fitbit కనెక్ట్ చేయండి", "Language" to "భాష", "Unit System" to "యూనిట్ విధానం", "Caffeine" to "కెఫిన్",
    "Body" to "శరీరం", "Walk" to "నడక", "Start walk" to "నడక ప్రారంభించండి", "Finish walk" to "నడక ముగించండి", "Challenges" to "సవాళ్లు",
    "Medications" to "మందులు", "Weekly Summary" to "వారపు సారాంశం", "Having trouble with the app?" to "యాప్‌తో సమస్య ఉందా?",
)

fun tr(s: String): String = when (Lang) { "hi" -> HI[s] ?: s; "te" -> TE[s] ?: s; else -> s }

// ---------------- Units ----------------
var Metric by mutableStateOf(true)
fun dist(m: Double, digits: Int = 1) = if (Metric) "%.${digits}f km".format(m / 1000) else "%.${digits}f mi".format(m / 1609.344)
fun vol(ml: Number) = if (Metric) "${fmtInt(ml)} ml" else "%.0f fl oz".format(ml.toDouble() / 29.5735)
fun volNum(ml: Number) = if (Metric) fmtInt(ml) else "%.0f".format(ml.toDouble() / 29.5735)
val volUnit get() = if (Metric) "ml" else "fl oz"
fun weight(kg: Double) = if (Metric) "%.1f kg".format(kg) else "%.1f lb".format(kg * 2.20462)
