# Condição física — Realm Gym 0.7.1

O sistema é opcional e vem **desativado por defeito** nas quatro edições. Os níveis, o XP, as cargas desbloqueadas, os planos e os recordes continuam permanentes. Quando ativado, a condição física de cada um dos seis atributos multiplica o respetivo bónus do Realm Gym.

## Ativar e desativar no Minecraft

Atualiza o servidor e os clientes para a mesma edição 0.7.1. Um operador pode usar:

```text
/gym conditioning on
/gym conditioning off
/gym conditioning status
```

`on` ativa e `off` desativa a condição física para todo o servidor, com aplicação imediata aos jogadores ligados. A alteração é guardada automaticamente e mantém-se após reinícios. **Não é preciso editar ficheiros nem executar `/gym reload`.**

Ativar e desativar exige nível de permissão 2 (operador). Qualquer jogador pode consultar o estado com `/gym conditioning status` ou apenas `/gym conditioning`. Os comandos também funcionam na consola e através do alias `/fitness conditioning`. Repetir `on` quando já está ativo não repõe a condição nem renova o período de tolerância.

## Configuração avançada

Para afinar as regras, o bloco em `config/realm-fitness.json` mantém-se disponível. As outras opções e campos adicionais são preservados quando se usam os comandos:

```json
"conditioning": {
  "enabled": false,
  "countOfflineTime": true,
  "graceDays": 7,
  "setsRequired": 2,
  "lossPerDay": 5.0,
  "minimumPercent": 60.0,
  "recoveryPerDay": 20.0
}
```

Se editares os valores manualmente, usa `/gym reload` para os aplicar.

Os valores de perda e recuperação são **pontos percentuais**. O mínimo é uma percentagem do bónus adquirido através do mod. Por exemplo, um bónus potencial de +10% de dano passa a +8% com condição de 80% e a +6% com condição de 60%. Os atributos base do Minecraft e os modificadores de outros mods conservam os seus próprios valores.

## Perda gradual

Depois de sete dias completos sem manutenção, cada dia adicional completo retira cinco pontos percentuais à condição do respetivo atributo. A condição permanece a 100% até ao fim do sétimo dia, passa a 95% ao completar oito dias, a 80% aos onze dias e atinge o mínimo de 60% aos quinze dias.

Cada atributo tem o seu próprio contador. Supino e tríceps mantêm a força; agachamentos e leg press mantêm as pernas. As outras máquinas mantêm o seu atributo habitual. A edição Forge 1.20.1 tem as seis máquinas originais.

Com `countOfflineTime: true`, conta o tempo real, incluindo saídas, reinícios e períodos em que o mundo está fechado. A perda é calculada quando o perfil volta a ser utilizado. Com `false`, conta apenas o tempo em que o jogador está ligado a esse mundo; o tempo acumulado permanece guardado entre sessões. Não existe uma tarefa a correr continuamente para jogadores offline.

## Manutenção e memória muscular

- São necessárias **duas séries válidas de pelo menos oito repetições** no mesmo dia de treino e atributo.
- Uma série válida mantém os critérios habituais: pelo menos 60% de repetições válidas e 50% de precisão.
- Ao completar a manutenção, recuperam-se **20 pontos percentuais**, até 100%, e começa um novo período de tolerância.
- A recuperação acontece uma única vez por atributo e por dia. Séries adicionais nesse dia não renovam novamente esse prazo.
- O dia usa a hora de renovação `resetMinute` já configurada no servidor. Séries parciais de dias diferentes não se somam.
- A manutenção funciona mesmo depois de atingir o limite diário de XP. Séries falhadas, canceladas ou com menos de oito repetições não contam para ela.

Assim, um jogador a 60% pode voltar aos 80% num dia e aos 100% no seguinte. Cargas desbloqueadas permanecem disponíveis e a dificuldade dos minijogos continua a usar a experiência e a fadiga existentes.

## Perfil e dados

O perfil mostra a condição por atributo e o **bónus atual aplicado**. Ao passar o rato por um atributo, apresenta o bónus potencial, a manutenção do dia, a recuperação, o tempo estimado até à próxima perda e se o tempo offline conta. Os modificadores são atualizados durante o jogo, no início de sessão, depois dos treinos e quando a configuração é recarregada.

Os dados ficam no perfil do atleta em `data/realm_fitness.dat`. Perfis antigos recebem condição de 100% e um período completo de tolerância ao ativar a funcionalidade. Não é cobrada inatividade anterior à ativação.

Desativar a funcionalidade volta a aplicar os bónus completos dos níveis permanentes. A condição guardada fica pausada; reativar conserva esse valor e inicia um novo período de tolerância. Alterar o período de tolerância, a taxa, o mínimo ou a contagem offline também conserva a condição atual e inicia nova tolerância, evitando cobranças retroativas.

## Limites de configuração

`graceDays`: 0–365; `setsRequired`: 1–24; perda, recuperação e mínimo: 0–100, com valores finitos. Configurações inválidas são recusadas por `/gym reload` antes de substituir as regras ativas.

As edições 1.21.1 partilham o núcleo e a apresentação. A edição Java 17 conserva as seis máquinas e usa a mesma lógica de condição, compilada para Java 17. Os testes cobrem os limiares de tempo, o mínimo, a recuperação diária, grupos partilhados, o limite de XP, o relógio a recuar, a migração de perfis, a pausa offline e a persistência. Os cenários Minecraft verificam os modificadores reais, o perfil e a reabertura do mundo.
