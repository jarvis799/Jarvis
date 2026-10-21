package com.jarvis.assistant.plugins

/** Built-in catalog - one tap installs these JSON manifests; they hot-reload instantly. */
object BundledPlugins {

    val catalog: List<Pair<String, String>> = listOf(
        "Dice & Coin" to """
{
  "id": "dice-roller",
  "name": "Dice & Coin",
  "version": 1,
  "description": "Roll dice, roll a d20 or flip a coin - instant offline answers.",
  "author": "Ayan Sir",
  "commands": [
    { "pattern": "roll (a )?dice|roll (a )?d6", "isRegex": true, "reply": "Rolling for you, Sir...", "action": { "type": "random", "value": "1..6" } },
    { "pattern": "roll (a )?d20", "isRegex": true, "reply": "Rolling a d20, Sir...", "action": { "type": "random", "value": "1..20" } },
    { "pattern": "flip a coin", "isRegex": false, "reply": "The coin says...", "action": { "type": "pick", "value": "Heads|Tails" } }
  ]
}
""".trimIndent(),
        "Daily Quote" to """
{
  "id": "daily-quote",
  "name": "Daily Quote",
  "version": 1,
  "description": "Motivational quotes pulled live from the web.",
  "author": "Ayan Sir",
  "commands": [
    { "pattern": "quote of the day|motivate me|inspire me|give me a quote", "isRegex": false, "reply": "", "action": { "type": "call_api", "value": "https://zenquotes.io/api/random|0.q" } }
  ]
}
""".trimIndent(),
        "IP Lookup" to """
{
  "id": "ip-lookup",
  "name": "IP Lookup",
  "version": 1,
  "description": "Answers with the device's public IP address.",
  "author": "Ayan Sir",
  "commands": [
    { "pattern": "what is my ip|my ip address|show my ip", "isRegex": false, "reply": "", "action": { "type": "call_api", "value": "https://api.ipify.org" } }
  ]
}
""".trimIndent(),
        "Device Doctor" to """
{
  "id": "device-doctor",
  "name": "Device Doctor",
  "version": 1,
  "description": "Real device reports through the built-in terminal.",
  "author": "Ayan Sir",
  "commands": [
    { "pattern": "battery report", "isRegex": false, "reply": "", "action": { "type": "terminal", "value": "dumpsys battery | head -n 10" } },
    { "pattern": "device info|system info", "isRegex": false, "reply": "", "action": { "type": "terminal", "value": "getprop ro.product.model; getprop ro.build.version.release" } }
  ]
}
""".trimIndent()
    )
}
