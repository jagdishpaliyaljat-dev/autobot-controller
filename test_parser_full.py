import json

raw_json = '''{
  "reply": "Opening WhatsApp and messaging Om.",
  "steps": [
    {
      "action": "open_app",
      "app": "WhatsApp"
    },
    {
      "action": "search_contact",
      "query": "Om"
    },
    {
      "action": "send_message",
      "message": "❤️"
    }
  ]
}'''

prompt = "whatsapp open massage om ❤️"

def simulate(raw_json, prompt):
    root = json.loads(raw_json)
    steps_arr = root.get("steps", [])
    result = []
    for obj in steps_arr:
        raw_action = obj.get("action", "").upper()
        if "OPEN" in raw_action or "LAUNCH" in raw_action:
            result.append(("launch", "com.whatsapp"))
        elif "SEARCH" in raw_action:
            result.append(("click_text", "Search"))
            result.append(("type_text", "Om"))
            result.append(("click_xy", "500, 500"))
        elif "SEND" in raw_action or "MESSAGE" in raw_action:
            result.append(("type_text", "❤️"))
            result.append(("click_text", "Send"))
            result.append(("click_xy", "1000, 2250"))
    return result

print("Steps generated:", simulate(raw_json, prompt))
