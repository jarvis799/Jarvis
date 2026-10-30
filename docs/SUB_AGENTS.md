# Jarvis 2.0.1 — Sub Agents

A sub agent is a specialist Jarvis hands a task to. The showcase lives at
**Settings → Sub Agent** (`subagent/SubAgentActivity`).

## What is implemented (real)

| Agent | What it really does | Requirement |
| --- | --- | --- |
| WhatsApp Agent | Reads incoming WhatsApp notifications and replies with WhatsApp's own remote-input reply action. No WhatsApp opening, no password. | Notification access + at least one rule |
| Email Agent | Checks unread Gmail over IMAP and sends an HTML auto reply over SMTP. | Gmail address + 16-character App Password |
| Web / Code / App / File / Research / Screen / Build / Shopping / Python | Open the installed Jarvis tool that performs the work (Browser Agent, Terminal, Hands-Free, File Organizer, GitHub Research, Screen Context, Dev Studio). | The tool's own permissions |

Status is never faked: `SubAgentStateResolver` reads the stores behind each agent
(`WhatsAppAutoReplyManager`, `GmailStore`) and reports SETUP / PAUSED / ACTIVE.

## Email HTML replies

`HtmlReplyTemplate` supports `{name}`, `{email}`, `{subject}` and `{reply}`.
Everything interpolated is HTML-escaped; the AI draft is inserted as escaped
paragraphs, so a hostile email can never inject markup into the reply.
Replies are sent as `multipart/alternative` with a plain-text fallback.

Limits (enforced in `GmailStore`): 10 replies per hour, 60 per day, 90 seconds
apart. Scans run through WorkManager roughly every 15 minutes; Android may delay
them, and nothing is sent while the phone is off or offline.

## Conversation routing

`SubAgentRouter` lets the assistant answer "whatsapp agent status" or open the
right screen. It answers with real values and never claims to have switched an
agent on by itself.
