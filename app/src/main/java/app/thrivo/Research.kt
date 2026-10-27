package app.thrivo

/**
 * Every threshold the app scores against, with the published source it comes from.
 * Shown to the user in More Settings → The Science.
 */
data class Ref(val id: String, val cite: String, val usedFor: String)

val REFERENCES = listOf(
    Ref("tanaka", "Tanaka H, Monahan KD, Seals DR. Age-predicted maximal heart rate revisited. J Am Coll Cardiol. 2001;37:153–156.",
        "Max heart rate = 208 − 0.7 × age (meta-analysis of 351 studies, 18,712 people)."),
    Ref("karvonen", "Karvonen MJ, Kentala E, Mustala O. The effects of training on heart rate; a longitudinal study. Ann Med Exp Biol Fenn. 1957;35:307–315.",
        "Heart-rate reserve method: intensity = (HR − resting) ÷ (max − resting)."),
    Ref("acsm", "Garber CE et al. ACSM Position Stand: Quantity and quality of exercise for developing and maintaining fitness. Med Sci Sports Exerc. 2011;43:1334–1359.",
        "Zones: moderate 40–59 % HRR, vigorous 60–89 %, near-maximal ≥ 90 %."),
    Ref("banister", "Morton RH, Fitz-Clarke JR, Banister EW. Modeling human performance in running. J Appl Physiol. 1990;69:1171–1177.",
        "Training impulse (TRIMP) = minutes × HRR × 0.64e^(1.92·HRR) (men) or 0.86e^(1.67·HRR) (women) — the basis of Strain."),
    Ref("who", "Bull FC et al. World Health Organization 2020 guidelines on physical activity and sedentary behaviour. Br J Sports Med. 2020;54:1451–1462.",
        "150–300 min/week moderate or 75–150 min vigorous (vigorous counts double) → weekly Active Zone Minutes goal of 150."),
    Ref("paluch", "Paluch AE et al. Daily steps and all-cause mortality: a meta-analysis of 15 international cohorts. Lancet Public Health. 2022;7:e219–e228.",
        "Mortality risk levels off at ~8,000–10,000 steps/day under 60 and ~6,000–8,000 at 60+ — the suggested step goal."),
    Ref("plews", "Plews DJ, Laursen PB, Stanley J, Kilding AE, Buchheit M. Training adaptation and heart rate variability in elite endurance athletes. Sports Med. 2013;43:773–781.",
        "Recovery uses the 7-day rolling mean of ln(rMSSD) compared against your own baseline."),
    Ref("buchheit", "Buchheit M. Monitoring training status with HR measures: do all roads lead to Rome? Front Physiol. 2014;5:73.",
        "Smallest worthwhile change = 0.5 × baseline SD; resting HR rising above baseline signals fatigue."),
    Ref("nunan", "Nunan D, Sandercock GRH, Brodie DA. A quantitative systematic review of normal values for short-term heart rate variability in healthy adults. PACE. 2010;33:1407–1417.",
        "Typical adult rMSSD 42 ± 15 ms (range 19–75 ms) — the HRV reference band."),
    Ref("shaffer", "Shaffer F, Ginsberg JP. An overview of heart rate variability metrics and norms. Front Public Health. 2017;5:258.",
        "rMSSD as the preferred short-term, vagally mediated HRV index."),
    Ref("hirshkowitz", "Hirshkowitz M et al. National Sleep Foundation's sleep time duration recommendations. Sleep Health. 2015;1:40–43.",
        "Sleep need by age: 14–17 y 8–10 h, 18–64 y 7–9 h, 65+ 7–8 h."),
    Ref("watson", "Watson NF et al. Recommended amount of sleep for a healthy adult: AASM and SRS joint consensus. Sleep. 2015;38:843–844.",
        "Adults need ≥ 7 h regularly; under 7 h is flagged as short sleep."),
    Ref("ohayon17", "Ohayon M et al. National Sleep Foundation's sleep quality recommendations: first report. Sleep Health. 2017;3:6–19.",
        "Good quality: efficiency ≥ 85 %, sleep latency ≤ 30 min, wake after sleep onset ≤ 20 min (≤ 40 min at 65+)."),
    Ref("ohayon04", "Ohayon MM, Carskadon MA, Guilleminault C, Vitiello MV. Meta-analysis of quantitative sleep parameters from childhood to old age. Sleep. 2004;27:1255–1273.",
        "Stage norms: deep (N3) ~16–20 % in adults falling with age, REM ~20–25 %."),
    Ref("vandongen", "Van Dongen HPA, Maislin G, Mullington JM, Dinges DF. The cumulative cost of additional wakefulness. Sleep. 2003;26:117–126.",
        "Sleep restriction builds a cumulative debt — the 7-night debt added to tonight's sleep need."),
    Ref("efsa", "EFSA Panel on Dietetic Products, Nutrition and Allergies. Scientific Opinion on Dietary Reference Values for water. EFSA Journal. 2010;8(3):1459.",
        "Adequate total water: 2.0 L/day women, 2.5 L men; ~80 % from drinks → suggested goal 1.6 L / 2.0 L, more on active days."),
    Ref("cretikos", "Cretikos MA et al. Respiratory rate: the neglected vital sign. Med J Aust. 2008;188:657–659.",
        "Normal adult resting breathing rate 12–20 breaths/min."),
    Ref("who_ox", "World Health Organization. Pulse Oximetry Training Manual. 2011.",
        "Normal SpO₂ 95–100 %; below 94 % is flagged."),
    Ref("aha", "American Heart Association. All about heart rate (pulse). 2024.",
        "Normal adult resting heart rate 60–100 bpm (well-trained people often 40–60)."),
    Ref("uth", "Uth N, Sørensen H, Overgaard K, Pedersen PK. Estimation of VO2max from the ratio between HRmax and HRrest — the Heart Rate Ratio Method. Eur J Appl Physiol. 2004;91:111–115.",
        "Cardio Fitness: VO₂max ≈ 15.3 × (max HR ÷ resting HR), used when your watch doesn't report VO₂max."),
    Ref("kaminsky", "Kaminsky LA, Arena R, Myers J. Reference standards for cardiorespiratory fitness measured with cardiopulmonary exercise testing: data from the FRIEND registry. Mayo Clin Proc. 2015;90:1515–1523.",
        "Age- and sex-specific VO₂max medians that rate your Cardio Fitness and set your fitness age."),
    Ref("nes", "Nes BM, Vatten LJ, Nauman J, Janszky I, Wisløff U. A simple nonexercise model of cardiorespiratory fitness predicts long-term mortality. Med Sci Sports Exerc. 2014;46:1159–1165.",
        "Fitness age: the age at which your VO₂max would be the population median."),
    Ref("gabbett", "Gabbett TJ. The training—injury prevention paradox: should athletes be training smarter and harder? Br J Sports Med. 2016;50:273–280.",
        "Cardio Load balance: acute (7-day) ÷ chronic (28-day) load; 0.8–1.3 is the sweet spot, above 1.5 the danger zone."),
    Ref("thayer", "Thayer JF, Åhs F, Fredrikson M, Sollers JJ, Wager TD. A meta-analysis of heart rate variability and neuroimaging studies. Neurosci Biobehav Rev. 2012;36:747–756.",
        "Stress: raised heart rate and lowered HRV at rest reflect sympathetic (stress) activation."),
    Ref("phillips", "Phillips AJK et al. Irregular sleep/wake patterns are associated with poorer academic performance and delayed circadian and sleep/wake timing. Sci Rep. 2017;7:3216.",
        "Sleep Regularity Index: chance you're in the same sleep/wake state at any two times 24 h apart."),
    Ref("windred", "Windred DP et al. Sleep regularity is a stronger predictor of mortality risk than sleep duration. Sleep. 2024;47:zsad253.",
        "Why consistency matters: regular sleepers had 20–48 % lower all-cause mortality risk."),
    Ref("roenneberg", "Roenneberg T, Wirz-Justice A, Merrow M. Life between clocks: daily temporal patterns of human chronotypes. J Biol Rhythms. 2003;18:80–90.",
        "Chronotype from mid-sleep time (MSF): early before 3:00, intermediate 3:00–5:00, late after 5:00."),
    Ref("bull", "Bull JR et al. Real-world menstrual cycle characteristics of more than 600,000 menstrual cycles. NPJ Digit Med. 2019;2:83.",
        "Cycle predictions: average cycle 29.3 days, ovulation ~14 days before the next period, with real person-to-person variation."),
    Ref("who_uv", "World Health Organization. Global Solar UV Index: A Practical Guide. 2002.",
        "Golden Hour: avoid walk slots with UV index 6+ (high) without sun protection; favours early-morning and evening light."),
    Ref("mishra", "Mishra T et al. Pre-symptomatic detection of COVID-19 from smartwatch data. Nat Biomed Eng. 2020;4:1208–1220.",
        "Early-warning alert: resting HR rising (and HRV falling) for days often precedes illness symptoms."),
    Ref("lehrer", "Lehrer PM, Gevirtz R. Heart rate variability biofeedback: how and why does it work? Front Psychol. 2014;5:756.",
        "Guided breathing at ~6 breaths/min (resonance frequency) maximises HRV and calms the stress response."),
    Ref("drake", "Drake C, Roehrs T, Shambroom J, Roth T. Caffeine effects on sleep taken 0, 3, or 6 hours before going to bed. J Clin Sleep Med. 2013;9:1195–1200.",
        "400 mg caffeine even 6 h before bed cut total sleep by over an hour."),
    Ref("gardiner", "Gardiner C et al. The effect of caffeine on subsequent sleep: a systematic review and meta-analysis. Sleep Med Rev. 2023;69:101764.",
        "Coffee should be stopped at least ~8.8 h before bed — the caffeine cutoff time."),
    Ref("efsa_caf", "EFSA NDA Panel. Scientific Opinion on the safety of caffeine. EFSA Journal. 2015;13(5):4102.",
        "Up to 400 mg/day is safe for healthy adults (200 mg in pregnancy); half-life ~4–6 h used for the in-body estimate."),
    Ref("who_bmi", "World Health Organization. Obesity: preventing and managing the global epidemic. WHO Technical Report Series 894. 2000.",
        "BMI categories: < 18.5 underweight, 18.5–24.9 healthy, 25–29.9 overweight, ≥ 30 obesity."),
    Ref("aha_bp", "Whelton PK et al. 2017 ACC/AHA Guideline for the Prevention, Detection, Evaluation, and Management of High Blood Pressure in Adults. Hypertension. 2018;71:e13–e115.",
        "Blood pressure: normal < 120/80, elevated 120–129/< 80, stage 1 130–139 or 80–89, stage 2 ≥ 140/90."),
    Ref("ada", "American Diabetes Association. Standards of Care in Diabetes — 2024: Diagnosis and Classification. Diabetes Care. 2024;47(Suppl 1):S20–S42.",
        "Fasting glucose: 70–99 mg/dL normal, 100–125 prediabetes range, ≥ 126 diabetes range."),
    Ref("bohannon", "Bohannon RW. Comfortable and maximum walking speed of adults aged 20–79 years. Age Ageing. 1997;26:15–19.",
        "Ride detection: comfortable walking is ~4.5–5.3 km/h, so sustained speeds of 13 km/h+ aren't walking or easy running."),
    Ref("keating", "Keating SE et al. Exercise and non-alcoholic fatty liver disease: a systematic review and meta-analysis. J Hepatol. 2012;57:157–166.",
        "Training plan: regular exercise lowers liver fat even with little weight loss — why cardio is on 5 days."),
    Ref("easl", "EASL–EASD–EASO Clinical Practice Guidelines on the management of MASLD. J Hepatol. 2024;81:492–542.",
        "150–240 min/week of moderate aerobic activity plus resistance training for fatty-liver risk."),
    Ref("maillard", "Maillard F, Pereira B, Boisseau N. Effect of high-intensity interval training on total, abdominal and visceral fat mass: a meta-analysis. Sports Med. 2018;48:269–288.",
        "Thursday intervals: HIIT reduces abdominal and visceral fat in less time."),
    Ref("morton", "Morton RW et al. A systematic review, meta-analysis and meta-regression of the effect of protein supplementation on resistance training-induced gains in muscle mass. Br J Sports Med. 2018;52:376–384.",
        "Protein target: gains plateau around 1.6 g/kg/day — the top of the daily protein range."),
    Ref("who_asia", "WHO Expert Consultation. Appropriate body-mass index for Asian populations. Lancet. 2004;363:157–163.",
        "Asian BMI cut-offs: 23 overweight, 25+ obese — used to set fat-loss priority."),
    Ref("lai", "Lipid Association of India. Consensus Statement IV on management of dyslipidaemia. 2024.",
        "LDL/non-HDL targets; lifestyle change for 3 months is first-line in low-risk adults."),
    Ref("acsm_rt", "ACSM. Progression models in resistance training for healthy adults. Med Sci Sports Exerc. 2009;41:687–708.",
        "Phases: 12–15 reps to learn technique, then 8–12 reps with more sets and load for hypertrophy."),
    Ref("mifflin", "Mifflin MD, St Jeor ST et al. A new predictive equation for resting energy expenditure in healthy individuals. Am J Clin Nutr. 1990;51:241–247.",
        "Calorie budget: resting energy = 10×kg + 6.25×cm − 5×age + 5 (men) / −161 (women)."),
    Ref("hall", "Hall KD et al. Quantification of the effect of energy imbalance on bodyweight. Lancet. 2011;378:826–837.",
        "A 500 kcal/day deficit gives roughly 0.5 kg/week at first, slowing as weight falls — the default deficit."),
    Ref("ifct", "Longvah T et al. Indian Food Composition Tables. National Institute of Nutrition, Hyderabad. 2017.",
        "Calories and macros for the Indian foods in the food list (approximate, per typical serving)."),
    Ref("nielsen", "Nielsen RO et al. Training errors and running related injuries: a systematic review. Int J Sports Phys Ther. 2012;7:58–75; and manufacturer guidance on shoe lifespan.",
        "My Shoes: replace running shoes around 500–800 km as cushioning wears down."),
)

object Science {
    fun maxHr(age: Int) = 208 - 0.7 * age                                   // Tanaka 2001
    val MODERATE = 0.40; val VIGOROUS = 0.60; val NEAR_MAX = 0.90         // ACSM 2011, %HRR
    const val WEEKLY_AZM = 150                                            // WHO 2020
    fun trimpWeight(hrr: Double, female: Boolean) =                       // Banister / Morton 1990
        if (female) 0.86 * kotlin.math.exp(1.67 * hrr) else 0.64 * kotlin.math.exp(1.92 * hrr)
    fun stepGoal(age: Int) = if (age >= 60) 7000 else 9000                // Paluch 2022 plateau midpoints
    fun waterGoalMl(female: Boolean) = if (female) 1600 else 2000         // EFSA 2010 × 80 % from drinks
    /** NSF 2015 midpoints: teens 9 h, adults 8 h, 65+ 7.5 h */
    fun baseSleepMin(age: Int) = when { age < 18 -> 540; age >= 65 -> 450; else -> 480 }
    const val MIN_SLEEP_MIN = 420                                         // AASM/SRS 2015
    const val GOOD_EFFICIENCY = 85.0                                      // Ohayon 2017
    const val GOOD_LATENCY_MIN = 30
    fun goodWasoMin(age: Int) = if (age >= 65) 40 else 20
    fun deepTarget(age: Int) = if (age >= 60) 10.0..20.0 else 16.0..23.0  // Ohayon 2004 (declines with age)
    val REM_TARGET = 20.0..25.0
    const val SWC = 0.5                                                   // Buchheit 2014
    val HRV_TYPICAL = 19.0..75.0                                          // Nunan 2010
    val RHR_NORMAL = 60L..100L; val SPO2_NORMAL = 95.0..100.0; val RESP_NORMAL = 12.0..20.0
}
