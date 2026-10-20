# Quests diárias — Contracts & Bounties 0.4.0

As quests são uma terceira atividade no mesmo mod. Os contratos particulares continuam financiados pelos autores; os públicos, pelo reino. As quests diárias criam dinheiro novo, com limite próprio. Não é preciso fabricar outro bloco: interage com qualquer um dos três murais e escolhe **Quests Diárias**.

## Jogar

Cada UUID recebe cinco quests: duas fáceis, duas médias e uma difícil. Todas podem ser aceites em simultâneo. Apenas atividades posteriores à aceitação contam. Não há cancelamento ou botão para renovar. O conjunto fica guardado no servidor e renova às 00:00 UTC; mudar o tempo do Minecraft não altera esta hora.

| Dificuldade | Recompensa bruta inicial | Reputação pretendida |
| --- | --- | --- |
| Fácil × 2 | 10–25 € | 0 |
| Média × 2 | 30–60 € | +1 |
| Difícil × 1 | 75–120 € | +2 |

O limite é **300 € brutos por conjunto diário**, incluindo incentivos e o eventual bónus das 5/5. A geração reserva espaço para as cinco recompensas e para esse bónus. Quando necessário, ajusta a parte variável ao orçamento antes de apresentar as missões. Depois de geradas, objetivo, quantidade, dificuldade, valor bruto e incentivo ficam congelados.

Mineração exige partir blocos válidos com a ferramenta apropriada; agricultura exige uma colheita madura; combate exige a morte atribuída diretamente ao jogador; pesca exige capturas reais com cana. Exploração conta a primeira descoberta de um bioma ou tipo de estrutura ainda não registado. Entregas exigem todos os materiais comuns, sem componentes personalizados, nos 36 espaços principais do inventário e são consumidas presencialmente no mural. Equipamento e mão secundária não são utilizados.

O pagamento é automático depois da confirmação no servidor. Se houver um problema com o perfil, espaço para esmeraldas, armazenamento ou tesouro, a recompensa permanece **pendente**. Não é necessário repetir a atividade. O servidor tenta recuperar o pagamento enquanto o jogador está online; o mural também permite tentar novamente. A celebração só aparece depois da confirmação financeira.

Missões não concluídas expiram na renovação e perdem o progresso parcial. Créditos de missões já concluídas continuam devidos, mesmo depois da renovação. Um pagamento atrasado pertence ao orçamento do conjunto que o originou; não abre espaço para ganhar outra vez nesse conjunto. O diário apresenta conjuntos anteriores e créditos pendentes.

## Carteira, reputação e tesouro

Com **Chronicles RP 0.5.4**, o crédito usa a carteira existente, o nome RP, a reputação e o contador de missões. A reputação respeita o menor limite diário configurado nas quests e nos contratos públicos. Os dois tipos de atividade partilham o teto UTC; atingir 100 de reputação não interrompe a recompensa monetária nem o contador de missões. A animação apresenta a reputação efetivamente recebida.

Sem Chronicles, o administrador pode ativar `emeraldFallback`. Neste modo, **1 esmeralda corresponde a 1 € de recompensa**. Os valores e o imposto são arredondados por defeito a esmeraldas inteiras; os limites monetários de configuração devem ser múltiplos de 100 cêntimos. Não se cria uma carteira paralela. O modo da moeda fica guardado no conjunto diário; instalar/remover Chronicles a meio de um conjunto pode deixar créditos pendentes até a integração original voltar a estar disponível.

Para cobrar imposto destas quests, instala **Crown Ledger 0.2.6+**. A taxa inicial é zero e o limite inicial é 25%. O Presidente com mandato ativo e posse concluída, ou um administrador autorizado, pode alterar a taxa no separador **Governo** do mural. Sem Realm Council, apenas a administração tem esse poder. Sem Presidente, mantém-se a última política. A autoridade é consultada em cada pedido, incluindo após sucessão e expiração do mandato.

O imposto é fixado na conclusão, apresentado na prévia e guardado no recibo. Em euros, é `floor(bruto × taxa / 10000)` em cêntimos. Uma recompensa de 100 € com 10% distribui **90 € ao jogador + 10 € ao tesouro**. O imposto nunca vai para a carteira pessoal do governante. Sem Crown, a taxa efetiva é zero; um Crown instalado mas incompatível não recebe créditos por uma via alternativa nem é silenciosamente ignorado para um imposto já reservado.

Incentivos por categoria começam a zero e têm limite inicial de 20%. Alterações só afetam a próxima geração; não alteram conjuntos já existentes. O Presidente não tem acesso a pagamentos manuais, limites administrativos ou reparações de progresso.

## Animações e avisos

As celebrações usam pergaminhos, selos, partículas e quatro fanfarras originais. Fácil usa prata; média, ouro; difícil, ouro e púrpura. As 5/5 têm uma apresentação própria com coroa, total líquido do conjunto e 5/5. O bónus monetário adicional está desligado por defeito.

As animações são uma camada do HUD: não abrem um menu nem bloqueiam a câmara ou os movimentos. Com o HUD visível e sem um menu aberto, a fila apresenta uma celebração de cada vez; a das 5/5 fica depois das individuais. **Backspace**, reconfigurável nos controlos, ignora a animação atual. A recompensa já está confirmada.

O separador **Efeitos** permite desativar animações, reduzir partículas, usar modo simples, alterar o volume e a duração normal entre 3 e 5 segundos. A celebração das 5/5 dura mais 1,5 segundos. As preferências e os últimos identificadores visuais ficam em `config/contracts-daily-visuals.json`, apenas no cliente. Desligar animações usa um aviso curto, sem alterar o pagamento.

Realm Notifications é opcional. Recebe o aviso de novas quests e, se ativados, avisos de progresso com intervalo mínimo de dez segundos. O aviso de progresso está desligado por defeito. Conclusões com animação não criam uma segunda notificação genérica.

## Configuração e modelos

Os valores iniciais são escritos em `config/contracts-daily-quests/settings.json`. São usados quando um mundo inicializa o sistema pela primeira vez. A configuração corrente de cada mundo pertence aos seus dados guardados e é editada no mural, em **Administração**; editar o ficheiro inicial não substitui uma política existente. A distribuição é configurável desde que totalize exatamente cinco quests. A hora é expressa por `resetMinute`, de 0 a 1439 minutos depois de 00:00 UTC.

O catálogo inclui **57 modelos originais**. Pode ser ampliado de três formas, por ordem de precedência: datapack, ficheiro JSON em `config/contracts-daily-quests/` e substituição guardada no editor do mundo. Um mesmo `id` substitui o modelo anterior. O editor permite criar, procurar, alterar e desativar modelos; não modifica missões já geradas. A administração pode recarregar os ficheiros pelo mural. Um `/reload` administrativo também recarrega os datapacks.

Exemplo de `data/meupack/daily_quest/cobre.json` num datapack:

```json
{
  "id": "meupack:cobre_medio",
  "title": "Cobre para o reino",
  "category": "MINING",
  "difficulty": "MEDIUM",
  "target": "#minecraft:copper_ores",
  "quantity": 32,
  "reward": -1,
  "weight": 10,
  "mods": [],
  "dimensions": ["minecraft:overworld"],
  "biomes": [],
  "enabled": true,
  "validation": "NATURAL_BLOCK"
}
```

`reward: -1` escolhe o valor no intervalo da dificuldade; um valor positivo é uma recompensa fixa **em cêntimos**, ainda sujeita ao orçamento e aos limites. O editor mostra euros. `weight` é um peso inteiro de 1 a 10000. `mods` exige identificadores de mods instalados; dimensões e biomas exigem IDs existentes. Os alvos aceitam IDs e tags não vazias do registo correspondente. Os requisitos não substituem um validador real.

| Categoria | Registo do alvo | Validação |
| --- | --- | --- |
| `MINING` | Bloco / tag de blocos | `NATURAL_BLOCK` |
| `COMBAT` | Entidade / tag de entidades / `*hostile` | `NATURAL_KILL` |
| `FARMING` | Cultura / tag de blocos | `MATURE_CROP` |
| `FISHING` | Item / tag / `*fish` / `*treasure` | `ROD_CATCH` |
| `EXPLORATION` | Bioma / tag / `*new_biome` | `NEW_BIOME` |
| `EXPLORATION` | Estrutura / tag / `*new_structure` | `NEW_STRUCTURE` |
| `DELIVERY` | Item / tag de itens | `CONSUME_ITEMS` |

Para exploração, indica a validação explicitamente. A geração resolve tags e seletores para um alvo concreto ainda não descoberto, presente na geração das dimensões que o jogador já visitou. Mundos sem estruturas não recebem missões de estruturas. Se o catálogo instalado/configurado não conseguir fornecer cinco objetivos distintos na distribuição pedida, o sistema apresenta um erro e não inventa objetivos inválidos. Reativar modelos suficientes permite gerar o conjunto ainda não criado.

O catálogo e os ficheiros têm limites de tamanho. São rejeitados IDs inválidos, quantidades negativas, configurações que excedam os limites e combinações incompatíveis de categoria/validador. Desativar modelos ou categorias não cancela quests já geradas.

## Administração e auditoria

O mural disponibiliza configuração, política, editor RNG, histórico, créditos com erro e repetição de pagamentos. Alterações registam UUID, data, operação e detalhes. A reparação excecional exige jogador online, motivo com pelo menos oito caracteres e quest ativa ainda não paga. Apenas ajusta o progresso dentro da quantidade original: não cria saldo, não renova conjuntos, não altera objetivos/recompensas e não dispensa materiais de entregas.

Sem Realm Admin, operadores de nível 2 ou superior administram o sistema. A integração futura usa `DailyPermissions.VERSION = 1` e `DailyPermissions.Provider.canAdminister(ServerPlayer)`. Um fornecedor regista-se com `register(provider)` e apenas a mesma instância se pode remover com `unregister(provider)`. Se `realmadmin` estiver instalado sem fornecedor compatível, o acesso administrativo falha fechado. O contrato desta API foi testado; não existe uma versão executável de Realm Admin neste repositório para validar a sua interface real.

## Persistência e recuperação

O ficheiro `world/data/contracts_daily_quests.dat` guarda UUIDs, relógio máximo observado, conjuntos, progresso, descobertas, política, auditoria, recibos e intenções financeiras. É separado dos dados dos contratos antigos. Não apagues este ficheiro nem recibos para resolver erros de pagamento.

Cada entrega e cada pagamento têm uma intenção durável anterior à alteração do inventário ou perfil. Um recibo acompanha o estado guardado do jogador; o anfitrião LAN também o grava em `level.dat`. A parte fiscal usa o mesmo UUID na API idempotente do Crown. A recuperação confirma os comprovativos antes de dar o pagamento por concluído. Uma falha ambígua conserva o crédito para inspeção, em vez de repetir dinheiro ou materiais. Uma falha de escrita suspende as novas operações diárias até o armazenamento ser corrigido e o mundo reiniciado; os contratos anteriores continuam separados.

O relógio máximo observado impede voltar a uma geração anterior se a hora do sistema recuar. Uma mudança administrativa da hora de renovação não renova imediatamente um conjunto que ainda não expirou. Fechar o mundo durante a renovação cria o conjunto seguinte quando o servidor voltar a abrir. O servidor não precisa de estar sempre ligado.

## Limitações verificáveis

- A mineração mantém posições colocadas/consumidas, mudanças de fluidos e deslocações por pistões. Uma posição natural só pode contar uma vez, mesmo após reiniciar. Blocos colocados antes da instalação ou antes de adicionar o modelo correspondente, ou modificados por comandos/mods sem eventos observáveis não têm origem recuperável. Não se promete distingui-los retroativamente.
- Combate conta apenas mobs marcados como gerados naturalmente ou na geração do chunk, com golpe mortal direto do jogador ou projétil seu e distância válida. Spawners, ovos, comandos, armadilhas, quedas e mortes automáticas não contam. Mobs antigos sem marca são ignorados. Uma farm de mobs naturalmente gerados com morte manual não é universalmente distinguível de combate legítimo.
- Atividades em criativo são rejeitadas. O Minecraft não guarda uma origem universal de cada item: materiais previamente criados em criativo/comandos e transferidos para sobrevivência não podem ser identificados com certeza a partir de uma pilha normal.
- Culturas Vanilla, abóboras/melões e culturas com propriedade inteira `age` são suportadas. Culturas personalizadas ou colheitas que não emitam os eventos normais podem precisar de compatibilidade adicional. A marcação impede repetir a mesma abóbora/melão colocado. Culturas renováveis maduras podem ser cultivadas e colhidas normalmente.
- Pesca utiliza o resultado real da cana; tesouros exigem pesca em águas abertas e a tag `contracts:daily_fishing_treasures`, extensível por datapack. Espécies adicionais podem usar modelos com ID ou tag próprios.
- Descobertas anteriores à instalação só podem ser recuperadas das informações existentes, incluindo biomas de avanços Vanilla e algumas estruturas com avanços. Não existe um histórico universal retroativo de exploração para todos os mods. A presença no registo não prova que um minério/mob de outro mod seja alcançável num mundo personalizado: o autor do modpack deve ajustar o catálogo à geração instalada.
- Há limites conservadores de 10000 jogadores, 65536 recibos e 200000 posições acompanhadas. O histórico corrente mostra sete conjuntos anteriores; conjuntos com créditos pendentes são preservados dentro do limite de retenção. A auditoria mantém as últimas 1000 entradas. Atingir limites não elimina recibos financeiros para libertar espaço; as operações afetadas param de forma segura.
- O cliente foi testado com servidor integrado realmente aberto à LAN, gravação dos dois ficheiros do anfitrião e fecho/reabertura do mundo. Os testes nativos também usam servidor dedicado. Esta validação não equivale a dois clientes autenticados ligados por LAN, nem a todos os modpacks possíveis.

## Reproduzir os testes

Java 21 e o Gradle Wrapper fixado no projeto:

```bash
./gradlew --console=plain build
./gradlew --console=plain -PcontractGameTests runGameTestServer
./gradlew --console=plain -PdailyGameTests runGameTestServer
./gradlew --console=plain -PdailyGameTests -PdailyIntegration runGameTestServer
./gradlew --console=plain -PdailyVisualTests -PdailyIntegration runClient
```

A integração exige os JARs publicados e verificados em `daily-compat-libs/`; o workflow [build-daily-quests.yml](../.github/workflows/build-daily-quests.yml) prepara-os a partir de versões fixadas e do novo Crown compilado. As três dependências de Origins são resolvidas pelo Gradle. As fixtures só entram com as propriedades de teste e são excluídas do JAR de produção. `result.json` do cliente deve indicar `success: true`, além do processo terminar sem erro. Não distribuir JARs de desenvolvimento ou dados do mundo de teste.
