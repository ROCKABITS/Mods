# Chronicles RP 0.5.3 — Public RP names

- Created characters use their RP name above the player's head, in TAB, as the author of ordinary Minecraft chat messages, and in the identity shown by the Chronicles RP book.
- Players without a created character, or with an invalid name in a legacy profile, keep their Minecraft account username.
- Chat hover identity and Shift-click insertion use the RP name; clicking for a private message keeps the real recipient.
- Public names synchronize after profile changes and when players join, respawn, change dimension or start tracking each other.
- Name updates share only the public name and UUID. They do not broadcast backstories, signatures, balances or other private character data.
- Existing team visibility and formatting still apply. The original username is not appended to the RP name.
- Account UUIDs, account identity, skins and command targets are unchanged. RP names may be shared by multiple players; use real usernames in commands that select a player.
- Existing profiles, Origins choices, drawn signatures, reputation, missions, medals, starting wealth and balances are preserved. Profile storage format is unchanged.
- Network protocol is now **7**. Replace the previous JAR with **0.5.3 on the server and every client**.

For Minecraft **1.21.1**, NeoForge **21.1.256+** and Java **21**. Requires Origins (NeoForge) **0.3.2** and Jupiter **2.3.7**. Origins: Classes (NeoForge) **0.3** remains optional.

This feature belongs to Chronicles RP and does not require Realm Titles. External mod interfaces that read raw account names may continue to display them. See `VALIDACAO.md` for this release's actual validation results.
