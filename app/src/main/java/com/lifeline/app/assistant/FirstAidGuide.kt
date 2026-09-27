package com.lifeline.app.assistant

data class AssistantReply(
    val title: String,
    val steps: List<String>,
    /** False when no topic matched and the reply lists what the assistant can help with. */
    val matched: Boolean = true
)

/** Anything that can answer an emergency question. Swap in an on-device LLM later. */
interface EmergencyAssistant {
    suspend fun reply(question: String): AssistantReply
}

private data class GuideTopic(
    val title: String,
    val keywords: List<String>,
    val steps: List<String>
)

/**
 * Built-in first-aid and disaster guidance, answered by keyword matching. Fully offline and
 * deterministic, so the demo works with no model download. Content follows standard lay
 * first-aid guidance (e.g. Red Cross / resuscitation council basics).
 */
object FirstAidGuide : EmergencyAssistant {

    private const val CLOSING = "If you need help from people nearby, send an SOS from the SOS tab."

    private val topics = listOf(
        GuideTopic(
            "Unconscious person",
            listOf("unconscious", "unresponsive", "passed out", "fainted", "collapsed", "not waking", "won't wake", "not breathing", "no pulse", "cpr"),
            listOf(
                "Check for danger to yourself first, then shout and gently shake their shoulders.",
                "Call for help. Ask someone to find an AED (defibrillator) if there is one.",
                "Open the airway: one hand on the forehead, tilt the head back, lift the chin.",
                "Look, listen and feel for normal breathing for up to 10 seconds.",
                "Breathing normally: roll them into the recovery position (on their side, top knee bent, head tilted back) and keep checking breathing.",
                "Not breathing normally: start CPR. Push hard and fast in the centre of the chest, 5–6 cm deep, 100–120 per minute. Give 30 compressions then 2 rescue breaths, or keep doing compressions only.",
                "Use the AED as soon as it arrives and follow its voice prompts. Don't stop until help takes over or they start breathing."
            )
        ),
        GuideTopic(
            "Severe bleeding",
            listOf("bleeding", "blood", "bleed", "cut", "wound", "laceration", "stab", "gash"),
            listOf(
                "Protect yourself if possible (gloves or a plastic bag over your hand).",
                "Press firmly and directly on the wound with a clean cloth or dressing.",
                "Keep pressing. If blood soaks through, add more cloth on top; don't lift the first layer.",
                "If an arm or leg is bleeding heavily and pressure isn't stopping it, apply a tourniquet 5–7 cm above the wound (not on a joint). Tighten until bleeding stops and note the time.",
                "Help them lie down and keep them warm to reduce shock.",
                "Don't remove objects stuck in a wound; pad around them instead."
            )
        ),
        GuideTopic(
            "Fire",
            listOf("fire", "smoke", "flames", "burning building", "on fire"),
            listOf(
                "Alert everyone nearby and get out. Don't stop to collect belongings.",
                "Stay low under smoke, where the air is cleaner.",
                "Before opening a door, feel it with the back of your hand. If it's hot, use another way out.",
                "Don't use lifts/elevators.",
                "If clothes catch fire: stop, drop to the ground, cover your face and roll.",
                "If trapped, close doors between you and the fire, seal gaps with cloth, and signal from a window.",
                "Once out, stay out. Go to a safe meeting point and count people."
            )
        ),
        GuideTopic(
            "Burns",
            listOf("burn", "burned", "burnt", "scald", "scalded", "hot water"),
            listOf(
                "Cool the burn under cool running water for 20 minutes.",
                "Remove jewellery and clothing near the burn unless it's stuck to the skin.",
                "Cover loosely with cling film or a clean, non-fluffy dressing.",
                "Don't use ice, butter, toothpaste or creams, and don't burst blisters.",
                "Get medical help for large burns, burns to the face, hands, feet or genitals, or chemical and electrical burns.",
                "Keep the person warm; cooling a large burn can make them cold."
            )
        ),
        GuideTopic(
            "Choking",
            listOf("choking", "choke", "can't breathe", "cannot breathe", "something stuck", "throat blocked"),
            listOf(
                "If they can cough, encourage them to keep coughing.",
                "If they can't cough, speak or breathe: give up to 5 sharp back blows between the shoulder blades with the heel of your hand.",
                "Then give up to 5 abdominal thrusts: stand behind, fist just above the belly button, pull sharply in and up.",
                "Keep alternating 5 back blows and 5 abdominal thrusts.",
                "If they become unresponsive, lower them to the ground and start CPR."
            )
        ),
        GuideTopic(
            "Heart attack",
            listOf("heart attack", "chest pain", "chest pressure", "heart"),
            listOf(
                "Help them sit down in a comfortable half-sitting position with knees bent.",
                "Call for medical help.",
                "If they're an adult, not allergic and able to swallow, give 300 mg aspirin to chew slowly.",
                "Keep them calm and monitor breathing.",
                "If they become unresponsive and stop breathing normally, start CPR."
            )
        ),
        GuideTopic(
            "Stroke",
            listOf("stroke", "face drooping", "slurred", "can't speak", "numb", "weak arm"),
            listOf(
                "Use FAST: Face drooping? Arm weakness? Speech slurred?",
                "Time to get help. Note the time symptoms started.",
                "Keep them comfortable. Don't give food or drink.",
                "If they become unresponsive but breathe, put them in the recovery position."
            )
        ),
        GuideTopic(
            "Seizure",
            listOf("seizure", "fit", "convulsion", "epilepsy", "shaking"),
            listOf(
                "Don't restrain them. Clear hard or sharp objects away and cushion their head.",
                "Don't put anything in their mouth.",
                "Time the seizure.",
                "When it stops, put them in the recovery position and stay with them.",
                "Get help if it lasts more than 5 minutes, repeats, or it's their first seizure."
            )
        ),
        GuideTopic(
            "Severe allergic reaction",
            listOf("allergic", "allergy", "anaphylaxis", "swelling", "epipen", "bee sting", "nut"),
            listOf(
                "If they have an adrenaline auto-injector (e.g. EpiPen), help them use it in the outer thigh.",
                "Call for help.",
                "If breathing is hard, let them sit up. Otherwise lie them down with legs raised.",
                "If there's no improvement after 5 minutes and a second injector is available, give it.",
                "Start CPR if they become unresponsive and stop breathing normally."
            )
        ),
        GuideTopic(
            "Broken bone",
            listOf("broken", "fracture", "bone", "sprain", "dislocated"),
            listOf(
                "Don't move them unless they're in danger.",
                "Support the injured part in the position you found it, using padding, clothing or a sling.",
                "Apply a cold pack wrapped in cloth to reduce swelling.",
                "Check fingers or toes beyond the injury stay warm and pink.",
                "Watch for signs of shock: pale, cold, sweaty skin or fast breathing."
            )
        ),
        GuideTopic(
            "Shock",
            listOf("shock", "pale", "clammy", "cold sweat", "dizzy"),
            listOf(
                "Help them lie down. Raise their legs if this doesn't cause pain.",
                "Keep them warm with a coat or blanket.",
                "Loosen tight clothing.",
                "Don't give food or drink.",
                "Reassure them and keep checking breathing."
            )
        ),
        GuideTopic(
            "Earthquake",
            listOf("earthquake", "quake", "tremor", "aftershock", "building collapse", "collapsed building"),
            listOf(
                "During shaking: drop, cover under sturdy furniture, and hold on.",
                "Stay away from windows and things that can fall.",
                "If outside, move to open ground away from buildings and power lines.",
                "After shaking: check yourself and others for injuries. Expect aftershocks.",
                "If you smell gas, leave the building and don't use flames or switches.",
                "If trapped, tap on pipes or walls and cover your mouth from dust. Shout only as a last resort."
            )
        ),
        GuideTopic(
            "Flood",
            listOf("flood", "flooding", "water rising", "flash flood"),
            listOf(
                "Move to higher ground immediately.",
                "Never walk or drive through floodwater. 15 cm of moving water can knock you over and 30 cm can float a car.",
                "Stay away from power lines and electrical equipment.",
                "If trapped in a building, go to the highest level (not a closed attic) and signal for help.",
                "Don't drink floodwater. Use boiled or treated water."
            )
        ),
        GuideTopic(
            "Heat stroke",
            listOf("heat stroke", "heatstroke", "overheated", "heat exhaustion", "too hot"),
            listOf(
                "Move them somewhere cool and shaded.",
                "Remove excess clothing.",
                "Cool them fast: wet the skin and fan them, or put cold packs in the armpits and neck.",
                "If they're alert, give sips of water.",
                "Get help urgently if they're confused or unresponsive."
            )
        ),
        GuideTopic(
            "Hypothermia",
            listOf("hypothermia", "freezing", "too cold", "shivering", "frostbite"),
            listOf(
                "Move them out of the cold and wind.",
                "Replace wet clothing with dry layers and cover the head.",
                "Warm them gradually with blankets or body heat. Don't rub the skin or use direct high heat.",
                "If alert, give warm (not hot) sweet drinks. No alcohol.",
                "Handle gently and get help if they're drowsy or confused."
            )
        )
    )

    val suggestions = listOf(
        "Someone is unconscious",
        "How to purify water",
        "Severe bleeding",
        "How to signal for rescue",
        "Build an emergency shelter",
        "Someone is choking",
        "How to start a fire safely",
        "Earthquake safety"
    )

    override suspend fun reply(question: String): AssistantReply = answer(question)

    // Keywords match at the start of a word, so "burn" finds "burned" but "nut" skips "minute".
    private val patterns: Map<GuideTopic, List<Regex>> = topics.associateWith { topic ->
        topic.keywords.map { Regex("\\b" + Regex.escape(it)) }
    }

    fun answer(question: String): AssistantReply {
        val text = question.lowercase()
        val best = topics
            .map { topic -> topic to patterns.getValue(topic).count { it.containsMatchIn(text) } }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.first
        return if (best != null) {
            AssistantReply(best.title, best.steps + CLOSING)
        } else {
            AssistantReply(
                title = "I can help with",
                steps = listOf(topics.joinToString(", ") { it.title }) +
                    "Try describing what's happening, e.g. \"someone is bleeding\" or \"there's smoke\".",
                matched = false
            )
        }
    }
}
