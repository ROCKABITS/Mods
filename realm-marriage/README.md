# Realm Marriage 0.1.0

Casamentos medievais para **Minecraft 1.21.1, NeoForge 21.1.256+ e Java 21**. Instala o mesmo JAR no servidor e em todos os clientes. O altar, os anéis, as texturas, as interfaces e os quatro sons foram criados para este projeto.

[Descarregar o pacote com Marriage 0.1.0, Crown Ledger 0.2.7 e Realm Identity 0.1.2](https://github.com/ROCKABITS/Mods/raw/refs/heads/main/mod-pronto/realm-marriage-0.1.0-suite.zip). Extrai o ZIP e substitui as versões anteriores nos clientes e no servidor. O Chronicles e as suas dependências não estão incluídos.

## Preparar o casamento

1. Fabrica um **Anel de Noivado** e usa-o no outro jogador. O destinatário aceita ou rejeita; qualquer noivo pode cancelar depois.
2. Coloca um **Altar de Casamento** sobre um bloco sólido, com dois blocos de altura livres. Não são geradas igrejas.
3. No altar, escolhe o celebrante, até duas testemunhas, o regime financeiro e a percentagem de divisão no divórcio. Guarda o acordo. **Os dois noivos têm de aprovar exatamente o mesmo acordo**, incluindo a taxa anunciada.
4. O celebrante e as testemunhas aceitam os seus convites. Qualquer jogador pode ser celebrante; não precisa de cargo ou permissões administrativas.
5. Com os participantes presentes, o celebrante inicia a abertura e a introdução. Depois abre as objeções: os convidados têm **10 segundos**, controlados pelo servidor, para carregar em **G**. A tecla pode ser alterada nos controlos do Minecraft.
6. Uma objeção suspende a cerimónia e permite escrever uma justificação. O celebrante dá a palavra ao convidado e aos noivos. Só retoma depois de **ambos** confirmarem que querem continuar. Qualquer noivo pode desistir.
7. O celebrante pede os votos individualmente. Cada noivo responde «Aceito»; as testemunhas escolhidas assinam. Só depois pode ser selada a união e confirmado o pagamento.
8. Os noivos recolhem a aliança e a certidão no altar. Precisam de dois espaços livres no inventário. A emissão é única e persistente.

O anel abre também a consulta pessoal. As operações de organização, emissão e conta comum exigem o altar; o cancelamento e o divórcio podem ser solicitados na consulta pessoal. Não é necessário usar comandos. `/marriage` apresenta ajuda; `/marriage altar` é uma ferramenta de operador para obter o bloco.

O mod carrega em singleplayer, LAN e servidor dedicado. Uma cerimónia precisa de **três jogadores distintos**: dois noivos e um celebrante. As testemunhas são opcionais.

## Receitas

Anel: ` D ` / `G G` / ` G `, com diamante D e lingote de ouro G.

Altar: `GRG` / `WBW` / `P P`, com ouro G, roseira R, lã branca W, livro B e tábuas P. Aliança e certidão são emitidas pelo registo, sem receita que possa fabricar uma união.

## Os três regimes

| Regime | No casamento | Depois |
| --- | --- | --- |
| Dinheiro conjunto | Consolida os saldos das duas carteiras, descontando a taxa acordada, numa conta comum. | Ambos podem depositar, levantar e consolidar novos rendimentos no altar. |
| Dinheiro separado | Mantém as carteiras individuais; cobra apenas a taxa acordada, se existir. | Cada jogador paga as suas despesas. Não existe saldo comum. |
| Conta conjunta | Mantém as carteiras individuais; a conta comum começa vazia. | Ambos podem depositar e levantar quantias da conta comum. |

Os regimes financeiros usam euros da **carteira real do Chronicles RP**. Sem Chronicles, é possível casar gratuitamente com dinheiro separado; não é criada outra moeda ou carteira pessoal.

**Compatibilidade de compras:** os mods existentes continuam a usar as suas carteiras pessoais. Para gastar nelas, levanta dinheiro no altar. Os rendimentos futuros não são desviados automaticamente: em Dinheiro conjunto usa «Consolidar» para os juntar. Outros mods podem aderir explicitamente à [API de pagamentos](API.md). Esta versão não intercepta silenciosamente lojas, casino, recompensas ou salários.

Qualquer cônjuge pode divorciar-se, mesmo com o outro offline. O saldo comum é bloqueado e dividido segundo o acordo aceite antes do casamento. O padrão é 50/50; o cêntimo indivisível inicial fica para o segundo noivo. Reembolsos posteriores são repartidos cumulativamente para impedir que múltiplos cêntimos beneficiem sempre a mesma pessoa. Os créditos ficam por UUID, são recolhidos uma única vez e não impedem novo casamento. As carteiras pessoais do outro jogador nunca são retiradas pelo divórcio.

## Integrações opcionais

| Mod | Utilização |
| --- | --- |
| Chronicles RP 0.5.4 | Nomes RP e carteira digital, com os requisitos Origins/Jupiter desse mod. |
| Crown Ledger **0.2.7+** | Recebe a taxa de casamento por operação idempotente. Nunca é paga à carteira do Presidente. |
| Realm Council | Apenas o Presidente com mandato ativo e juramento concluído altera a taxa, dentro do limite administrativo. Sem Council, fica reservada aos administradores. |
| Realm Identity **0.1.2+** | Estado civil e cônjuge, respeitando a privacidade. Usa a certidão com Shift para abrir a validação no Identity. Documentos anteriores de identidade continuam válidos. |
| Realm Notifications | Avisos pessoais; o HUD da cerimónia e a objeção funcionam também sem este mod. |
| Realm Admin | Contrato de permissões disponível para um fornecedor autorizado. Ainda não existe um adaptador para um Realm Admin publicado neste repositório. |

A certidão contém uma referência opaca validada pelo servidor. Alterar nomes ou tags não cria um casamento. Depois do divórcio, o documento continua a identificar o casamento histórico e mostra o seu estado atual. A consulta do cônjuge em documentos de identidade pode ser tornada privada no menu pessoal; o próprio titular continua a vê-la.

## Configuração e segurança

No altar, a área administrativa permite configurar os limites, consultar registos e repetir operações financeiras já preparadas. As intervenções ficam no histórico e não permitem forçar um casamento nem levantar dinheiro de terceiros. Sem Realm Admin, o acesso administrativo exige nível de operador 2. Se Realm Admin estiver instalado mas não registar um fornecedor, o acesso falha de forma fechada.

| Valor inicial | Configuração |
| --- | --- |
| 10 segundos | Objeções, ajustáveis entre 3 e 60 segundos. |
| 20 blocos | Raio dos convidados. |
| 12 blocos | Presença dos noivos, celebrante e testemunhas. |
| 2 | Máximo de testemunhas. |
| 15 minutos | Validade do pedido. |
| 30 minutos | Duração máxima de uma cerimónia. |
| 0 € / 500 € | Taxa inicial / limite administrativo inicial. |
| 30 segundos | Intervalo mínimo entre pedidos, persistente por jogador. |

A taxa é fixada no acordo e repartida entre os noivos. Mudanças posteriores da política não alteram um acordo já aprovado. Uma falha financeira mantém a operação pendente e não emite um casamento ou documentos incompletos. Antes de tentar a entrega fiscal, cancelar restitui as contribuições aos seus donos. Depois de a entrega fiscal ter entrado em liquidação, cancelar liberta imediatamente os jogadores e divide o saldo comum, mas **não reverte a taxa já autorizada**: esta continua reservada até confirmar a entrega ao Crown, sem cobrança adicional.

Desconexões, afastamento dos participantes, perda do altar ou reinício suspendem a cerimónia. Para retomar são necessárias as presenças e a concordância de ambos. Os dados ficam no `data/realm_marriage.dat` do mundo, por UUID. Intenções financeiras, recibos nos perfis e gravações atómicas permitem recuperar uma interrupção. O anfitrião LAN tem verificação dos ficheiros do jogador e do `level.dat`. Falhas de gravação bloqueiam novas operações até corrigir a causa e reiniciar o mundo; não apagues o registo para desbloquear dinheiro.

Conserva cópias conjuntas do mundo e dos perfis. Restaurar apenas um ficheiro financeiro pode criar uma situação ambígua que requer recuperação administrativa. Um servidor ou mod com acesso direto aos ficheiros continua a ser uma entidade de confiança.

## Efeitos e desenvolvimento

A aba Efeitos permite desligar animações, reduzir partículas e ajustar o volume; as preferências são locais. O aviso essencial de objeção continua visível. Os convidados mantêm movimento e câmara durante o HUD. Os controlos pessoais de consentimento usam interfaces próprias.

```bash
./gradlew build
./gradlew -PmarriageGameTests runGameTestServer
./gradlew -PmarriageGameTests -PintegrationTests runGameTestServer
./gradlew -PmarriageVisualTests -PintegrationTests runClient
```

A integração exige os JARs verificados em `compat-libs/`; o guião de publicação instala-os a partir dos artefactos do repositório. Os recursos podem ser recriados com `python3 tools/generate_art.py` e `python3 tools/generate_lang.py` (Pillow necessário para a arte). Os cenários e automações de teste são excluídos do JAR de produção. Resultados efetivos e limitações em [VALIDACAO.md](VALIDACAO.md).
