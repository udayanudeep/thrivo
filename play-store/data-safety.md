# Data safety form answers

**Does your app collect or share any of the required user data types?** Yes (collected = processed on device; see notes).
**Is all user data encrypted in transit?** Yes (HTTPS for the optional AI coach and weather requests).
**Can users request data deletion?** Yes — clear app storage / uninstall; Health Connect data via the Health Connect app.

| Data type | Collected | Shared | Purpose | Notes |
|---|---|---|---|---|
| Health info (steps, sleep, heart, body, cycle) | Yes | Only if AI coach is on (to Anthropic) | App functionality, personalisation | Processed on device; optional, user-initiated |
| Fitness info (exercise, distance) | Yes | No | App functionality | |
| Approximate location | Yes | Yes (Open-Meteo, rounded ~1 km) | App functionality (walk-time forecast) | Optional |
| Precise location | Yes | No | Walk route tracking | Only during a walk you start |
| Name | Yes | Only in share codes you send | Personalisation | Optional |

**Health Connect declaration (Play Console):** list each permission in the manifest with its use:
- READ steps/distance/calories/floors — activity charts, goals, badges, shoes.
- READ/WRITE hydration — water tracking.
- READ sleep — sleep stages, score, consistency, chronotype.
- READ heart rate / resting HR / HRV / SpO₂ / respiratory rate / VO₂max — recovery, strain, stress, cardio fitness.
- READ/WRITE weight, READ height / blood pressure / glucose / body temperature — Body screen.
- READ/WRITE exercise, WRITE exercise route & distance — workouts list and GPS walk tracking.
- READ/WRITE menstruation — cycle tracking.
- READ_HEALTH_DATA_HISTORY — trends and badges older than 30 days.
- READ_HEALTH_DATA_IN_BACKGROUND — home-screen widgets and the morning briefing.
