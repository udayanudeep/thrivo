package app.thrivo

/** Mifflin-St Jeor resting energy (Mifflin 1990), kcal/day — used to estimate calories burned when the tracker reports none. */
fun bmr(kg: Double, cm: Double, age: Int, female: Boolean) = 10 * kg + 6.25 * cm - 5 * age + if (female) -161 else 5
