# Changelog

## 0.1.1 — Beta

- Group rapid notification bursts into one parchment card, including modpack login floods.
- Keep the latest 100 original notices in the session journal (N).
- Update burst totals without restarting animations or repeatedly playing sounds.
- Coalesce identical notices even when other notices arrive between them.
- Expire popup cards after 30 seconds so closing a menu cannot replay an old backlog.
- Reduce the pending queue to three cards and reset burst state between worlds.
- Keep payments, advancement reward rules, the optional API and network protocol unchanged.
- Validate a 200-message burst through the actual server-to-client channel and production HUD.

## 0.1.0 — Beta

- Notificações medievais com pergaminho, selos por categoria e animações.
- Preferências de posição, som e duração por jogador.
- Histórico da sessão e limites para filas de avisos.
- API opcional para outros mods, com envio validado pelo servidor.
- Integrações com Chronicles RP, Realm Rewards, Contracts & Bounties, Realm Council, Crown Ledger e Realm Markets.
- Mensagens de chat mantidas quando a integração de notificações não está disponível.
