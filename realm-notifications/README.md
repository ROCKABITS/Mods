# Realm Notifications

Notificações medievais para os mods de RP de ROCKABITS, com pergaminho, verde profundo, dourado e selos próprios para cada evento.

**Minecraft 1.21.1 · NeoForge 21.1.256+ · Java 21 · 0.1.0 Beta**

O servidor envia apenas avisos de ações já realizadas. Este mod não cria dinheiro, não altera impostos e não decide pagamentos.

## Instalação

Instala `realm-notifications-0.1.0.jar` no servidor e nos clientes que querem os avisos. Os jogadores sem este mod continuam a receber as mensagens de chat dos mods compatíveis. O cliente pode desativar os avisos nas suas preferências.

Para ativar as integrações, atualiza também os JARs usados no servidor e nos respetivos clientes:

| Mod | Versão com integração |
| --- | --- |
| Chronicles RP | 0.5.4 |
| Realm Rewards | 0.1.2 |
| Contracts & Bounties | 0.3.2 |
| Realm Council | 0.2.2 |
| Crown Ledger | 0.2.1 |
| Realm Markets | 0.2.1 |

Cada integração é opcional. Não é necessário instalar toda a coleção. O Chronicles continua a exigir Origins e Jupiter.

## Avisos

- Transferências e dinheiro atribuído no Chronicles, com o motivo.
- Recompensas por conquistas e pagamentos pendentes do Realm Rewards.
- Publicação, entrega e recolha de recompensas dos contratos.
- Vendas e recolhas de ganhos das bancas.
- Salários e operações da tesouraria.
- Fases das eleições, resultados e confirmação privada de voto.

Uma recompensa guardada no mural ou um lucro guardado na banca são apresentados como valores por recolher. O aviso de entrada na carteira só aparece quando o pagamento acontece. Os votos continuam secretos.

## Preferências

As opções pertencem a cada jogador: posição, duração, som, avisos ativos e histórico. Os menus de jogo continuam a abrir nos respetivos blocos; as preferências de notificações não abrem contratos, lojas ou tesouraria.

Prime **N** para abrir o diário e usa o separador **Preferências**. Podes mudar a tecla nos Controlos ou abrir as preferências pela configuração do mod na lista de Mods. Há cinco posições, duração de 3 a 15 segundos e opções para sons, animações e histórico. O botão de experimentar mostra um exemplo visual e não altera dinheiro.

O canto superior esquerdo é a posição inicial, para deixar livre o espaço dos avisos Vanilla. Aparecem até três avisos em simultâneo, com menos cartões em ecrãs pequenos; a fila guarda até 32 e o diário até 100 entradas. O histórico pertence à sessão e é limpo ao sair do mundo. Não existe uma caixa de correio para jogadores offline.

## Desenvolvimento

A [API pública](API.md) permite enviar avisos a partir de outros mods sem dependência obrigatória. Os resultados efetivos de compilação, testes e capturas ficam em [VALIDACAO.md](VALIDACAO.md).
