# Realm Council — 0.2.1

Fantasy elections for Minecraft Java 1.21.1, NeoForge 21.1.256+ (21.1 series) and Java 21. Install the JAR on the server and every client. Crónicas RP 0.4.0+ is optional; it supplies character names, races and ages when available. Minecraft identities are used otherwise. The adapter does not modify RP profiles or balances.

Place a Notice Board, Ballot Box and Oath Desk using the creative tab or their crafting recipes. Interact with a Notice Board to read the ledger. There is no O key binding or public opening through `/council` or `/eleicoes`. Voting requires a nearby ballot box; taking office requires an oath desk.

Operators obtain and place a Notice Board, interact with it and open **Manage** to organize the whole election. **Receive the blocks** delivers the notice board, ballot box and oath desk; full inventories drop the items at the operator’s feet. **Create election** opens a two-page form for the office, eligibility, turnout, candidate/round limits and all five durations. Review the settings and seal the notice to create it. Each election stores its chosen rules.

Every menu read, refresh and action requires the original station to remain intact, in the same dimension and within eight blocks. Walking away, changing dimension or breaking the station makes the server reject the request and close the menu on its next update. Sessions expire after ten minutes; interact with the station again. Ordinary players can read, stand for election and participate without operator permissions.

**Electors** supports search, paging and known offline players. Operators may enrol or exclude electors during nominations; exclusion also removes their candidacy after confirmation. **Candidates** allows removal of a candidacy during nominations or campaign while preserving voting eligibility. **Start campaign**, **Open voting**, **Close voting round**, **Cancel election** and **End term** all have confirmations. Stale election/phase/round snapshots and revoked permissions are rejected by the server. The electoral roll is only sent to authorized operators and contains no ballot choices.

Manage requires operator permission level 2. The existing `/council admin …` and `/eleicoes admin …` commands remain available remotely and from the console. New clients and servers must all use 0.2.1 because the network protocol changed. Replace the previous JAR; 0.1.0 and 0.2.0 world data remains compatible.

Players submit a motto, heraldic symbol and programme. Existing candidates may edit or withdraw during the campaign. Each elector gets one final ballot per round, including a blank-ballot option. Counts remain hidden until the round ends. Ties trigger a fresh round between tied candidates; persistent ties close without a winner after the configured round limit.

The winner takes the oath and may publish up to twenty public decisions. The office does not grant operator privileges or control money. Term duration starts when the server declares the winner. Operators call subsequent elections.

Default durations: nominations one real day, campaign two days, voting two days, term fourteen days, runoff one day. Choose all rules and durations in the creation form. The server configuration file supplies its initial values; edit it while the server is stopped to change those defaults. Time is based on the real clock; overdue phases advance after restart. Election settings are captured at creation.

English and Portuguese follow Minecraft’s language settings. Player-authored text stays unchanged. Election data is stored separately in `world/data/realmcouncil_elections.dat`. Ballots are hidden from other players and normal admin menus; the world-file owner can inspect stored data. UUID voting cannot eliminate alternate accounts.

Use Java 21. Development commands: `gradlew.bat runClient`, `test`, `runGameTestServer -PrpGameTests`, then `clean build` without test properties. Linux/macOS uses `./gradlew`. Put only Crónicas RP in `run/mods` to test optional integration. `runClient -PrpClientPreview` runs a temporary native screenshot preview. Test classes are excluded from the release JAR.
