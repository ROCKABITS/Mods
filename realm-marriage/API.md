# Realm Marriage API v1

All three APIs are optional server-side Java integration contracts. Check the mod presence and `VERSION == 1`, then use a separate integration class or reflection so the calling mod also loads without Realm Marriage. Calls that touch world state must run on the owning Minecraft server thread. Never accept a receipt, account UUID, consumer identity or payment result from the client without validating it against your own server-side transaction.

## Account payments

`MarriagePayments.register(String id)` returns a consumer token. Keep the original instance: constructing a record with the same text does not authorize calls. Registration is unique for the process. Registered server mods are trusted consumers, not a security boundary against arbitrary server code.

```java
var consumer = MarriagePayments.register("myshop"); // once at initialization
long available = MarriagePayments.balance(player);
// Persist a unique purchase ID and the expected product/price in your own journal first.
String state = MarriagePayments.reserve(consumer, player, purchaseId, unionId, cents);
// HELD means those cents are persistently reserved, no longer spendable by the couple.
// Deliver exactly once using your own durable product-delivery receipt.
MarriagePayments.commit(consumer, server, purchaseId);
// If delivery is cancelled and definitely did not happen:
MarriagePayments.refund(consumer, server, purchaseId);
```

`reserve` binds the receipt to the exact player, marriage, consumer and positive cent amount. Repeating the same request returns its persisted state; changing any field fails. Only active non-SEPARATE marriages accept new reservations. `commit` and `refund` are terminal and idempotent for the same outcome; a conflicting outcome fails. `DONE` denotes a committed purchase; `CANCELLED` denotes a refunded one. A previously held purchase can be finalized after divorce. A refund after divorce is split into personal pending credits according to the original agreement, including cumulative cent rounding.

Do not retry product delivery merely because `reserve` succeeded again. Your mod must persist and recover its own delivery intent. If a crash leaves the delivery ambiguous, keep the money held and resolve the delivery receipt; do not guess or use an automatic timeout refund. The account API cannot make another mod's unjournalled item creation atomic. It does not deduct from personal wallets and never transparently substitutes for Chronicles wallet calls. This release contains no automatic shop adapter.

## Civil status and documents

`MarriageApi.statusJson(MinecraftServer server, UUID owner, UUID viewer)` returns civil status and, when permitted, spouse identity. Respect the actual viewer UUID. Missing marriages use SINGLE; ENGAGED, MARRIED and DIVORCED are distinct. A private spouse is omitted for other viewers. Do not put private fields into a permanent public identity snapshot.

`MarriageApi.documentJson(ServerPlayer holder, ItemStack stack)` authenticates an actual registered ring/certificate against the server's opaque reference. Forged or mismatched references fail. The resulting certificate includes recorded names, date, mode, witnesses and the current historical marriage phase. It grants no financial permissions. Copies of an authentic reference refer to the same historical marriage, not another union.

Realm Identity 0.1.2 calls these contracts dynamically when displaying an identity and when inspecting a marriage certificate with Shift. The certificate details are supplied by the server, not by editable item display text.

## Administrative permissions

Register an identity-owned `MarriagePermissions.Provider` with `canAdminister(ServerPlayer)`. The provider is authoritative: a denial also denies an operator. Unregister using the same provider instance. Without a provider, installed `realmadmin` denies access; otherwise operator level 2 is the fallback. Provider exceptions deny access. This is an integration point for a future Realm Admin implementation, not a claim that such a mod has been tested.

## Treasury receipt

Crown Ledger 0.2.7 supplies `CrownMarriageApi.VERSION == 1`, `preflight(...)` and `credit(...)`. The Marriage service uses one deterministic wedding-fee receipt and records intent before attempting credit. The amount is the fee already removed from the spouses; it is not new income. Crown records it as public revenue with a marriage-fee label. Older Crown versions can still run alongside Marriage, but cannot receive a positive wedding fee through this API.
