# Laws & Justice 0.4.0 — validação da Beta

As quatro fases do [plano aprovado](PLANO-COMPLETO.md) estão implementadas nesta versão: prisões, tribunal do Presidente, mandados e algemas, trabalhos prisionais, multas, fianças e registos públicos. O Crown Ledger 0.2.2 acrescenta os recibos necessários para receber pagamentos judiciais sem duplicação.

## Compilação e proveniência

- Minecraft **1.21.1**, NeoForge **21.1.256**, Java **21**.
- Fonte de ambos os JARs: [`86b9398`](https://github.com/ROCKABITS/Mods/commit/86b93988a7ef5bfe9736f56085c6a861adddb66e).
- [Compilação e validação conjunta concluída](https://github.com/ROCKABITS/Mods/actions/runs/39253815267). Os JARs de produção foram guardados antes dos testes e utilizados como contrapartes novas da mesma revisão.
- Os JARs não contêm classes, estruturas de teste, cenários de demonstração, outros mods ou dependências empacotadas.

| Projeto | Testes unitários | GameTests independente | GameTests com integrações | Imagens no cliente real |
| --- | ---: | ---: | ---: | ---: |
| Laws & Justice 0.4.0 | 130 | 115 | 115 | 47 |
| Crown Ledger 0.2.2 | 52 | 25 | 25 | 3 |

Não houve testes ignorados, falhas ou erros nas suítes publicadas. Os relatórios e checksums completos acompanham os ficheiros `.build.json` em `mod-pronto`.

## Comportamentos verificados

- As penas contam ticks online. Teleporte, morte, renascimento e reabertura do mundo mantêm a custódia; o cumprimento liberta automaticamente uma única vez. A fila de libertação funciona com beneficiários offline.
- Apenas o Presidente eleito, empossado e com mandato atual pode julgar normalmente. A mudança do mandato retira poderes a menus já abertos. As intervenções administrativas de emergência ficam identificadas.
- Acusações e provas ficam restritas às partes e à autoridade. As audiências precedem os vereditos; mandados e algemas dependem dos registos autênticos do servidor.
- Os seis trabalhos foram realizados pelo anfitrião real em Sobrevivência, sem permissões de operador ou voo: mineração, agricultura, carpintaria, limpeza, ferraria e transporte. Houve ações Vanilla reais, produção temporizada, dois postos de armazém e entregas seladas.
- Os recursos são finitos e validados na zona certa. Itens externos, criativos, sacos copiados, recolhas repetidas e entregas duplicadas não autorizam progresso ou recompensas. A pena e os créditos de trabalho ficam consistentes no mesmo ficheiro.
- As multas só surgem após audiência e veredito. A falta de saldo não aplica outra pena. As fianças pertencem à condenação exata e podem ser pagas por amigos, incluindo quando o beneficiário está offline. Uma tentativa sem cobrança não pode debitar uma fiança depois de terminar a pena.
- Foram provocadas falhas reais de gravação: o mesmo recibo retoma uma cobrança aplicada; uma tentativa ainda sem cobrança pode ser cancelada. Os marcadores sobrevivem ao mecanismo Vanilla de cópia após morte. Jogadores offline não são debitados de novo.
- O cliente integrado pagou **20 € de fiança e 25 € de multa** com a carteira real do Chronicles. O saldo passou de **100 € para 55 €**, e a tesouraria real recebeu **45 €**. Repetir os recibos antes e depois de uma segunda paragem/reabertura efetiva do mundo LAN manteve os mesmos saldos.
- O recibo do anfitrião foi verificado em `level.dat`, que o Minecraft utiliza ao reabrir o mundo. Para participantes de servidor dedicado/LAN, foi verificado o ficheiro individual do jogador.
- O Crown Ledger grava o saldo e os recibos em conjunto, na mesma fila de escrita do NeoForge. Os testes cobrem uma gravação antiga já em fila, substituição de ficheiro falhada, recibo alterado/duplicado, confirmação de recibo ausente e recuperação sem voltar a creditar.
- Sem Chronicles, são usadas esmeraldas reais. Sem uma tesouraria compatível, o pagamento confirmado continua guardado no tribunal para transferência posterior.
- A integração com Realm Identity verificou um documento físico emitido pelo mod real e rejeitou documentos falsificados ou apresentados à distância.
- Os modelos originais e texturas resolvem no cliente real. Os menus administrativos, do prisioneiro, do tribunal, dos trabalhos e dos pagamentos foram verificados com deslocamento e limites de **320 × 240**. Atualizações atrasadas não reabrem sessões que o jogador fechou.
- A migração dos formatos anteriores conserva prisões, processos e trabalhos; as novas condenações guardam a duração original no processo mesmo após a rotação do histórico de custódia.

## Alcance da evidência

As capturas mostram o cliente Minecraft real e um servidor integrado efetivamente aberto por LAN, com paragem e reabertura do mundo. **Não foi feita uma ligação autenticada de dois clientes Minecraft independentes.** Os GameTests utilizam jogadores de teste nativos para validar regras, inventários, anexos, persistência e integrações no servidor.

Os edifícios, personagens e o saldo inicial de 100 € são cenários de teste; o mod não gera prisões nem atribui esse saldo. A demonstração dos trabalhos configura uma unidade e um minuto por tarefa, em vez dos valores padrão maiores. As imagens do próprio Crown Ledger usam dados de demonstração nas interfaces de produção; os pagamentos, recibos e saldos reais são comprovados pela sequência integrada do Laws & Justice.

O Laws & Justice foi carregado com os dez mods associados, incluindo a nova contrapartida Crown Ledger e os JARs reais de Chronicles RP, Realm Council, Contracts, Markets, Identity, Notifications, Rewards, Titles e Casino, mais as dependências Origins/Jupiter/Classes. A suíte nativa própria do Crown mantém o seu modo sem Chronicles; a integração financeira com a carteira real foi verificada na suíte completa e no cliente do Laws & Justice.

## Ficheiros publicados

| Ficheiro | SHA-256 |
| --- | --- |
| `laws-justice-0.4.0.jar` | `78c32b0c3acc8930ffd508c9bb44cc98b794a4b87900438b19ab812c21d36f7b` |
| `laws-justice-0.4.0.zip` | `b3cbbd4271f9688c94ac9841fb7ea3208c2b8fec3a00d33bed959ad5dda58a54` |
| `crown-ledger-0.2.2.jar` | `a91afc03da4aae23cb584911bdcd88cf3b5a4fcf91bcc13b683ef6405fe6dab5` |
| `crown-ledger-0.2.2.zip` | `ffacf2862f3b00e9415669f449aa6125e27218a6b05dd681fe005c1dc1cc2e71` |

[Downloads](../mod-pronto/) · [47 imagens do Laws & Justice](../media/laws-justice/0.4.0/) · [3 imagens do Crown Ledger](../media/crown-ledger/0.2.2/).

Validações anteriores: [0.1.0](VALIDACAO-0.1.0.md), [0.2.0](VALIDACAO-0.2.0.md) e [0.3.0](VALIDACAO-0.3.0.md). Os respetivos artefactos são conservados.
