# Contracts & Bounties — 0.1.1

Mod de contratos de materiais para Minecraft **1.21.1**, **NeoForge 21.1.256** e **Java 21**. Deve estar instalado no cliente e no servidor.

**Estado desta entrega:** JAR 0.1.1 compilado no GitHub Actions; 23 testes unitários e todos os 17 testes obrigatórios de servidor aprovados, incluindo presença do mural no criativo e na pesquisa. A integração com o Crónicas RP e a interface ainda precisam de validação dentro do jogo. Ver [VALIDACAO.md](VALIDACAO.md).

[Descarregar o mod compilado](https://github.com/ROCKABITS/Mods/actions/runs/38718980393/artifacts/11964257195) com sessão iniciada no GitHub. Extrair o ZIP e usar o ficheiro `contracts-bounties-0.1.1.jar`. O artefacto é conservado durante 30 dias; futuras compilações podem ser iniciadas na página Actions através do workflow `Build Contracts & Bounties`.

Um jogador publica um pedido, reserva a recompensa e recebe os materiais quando outro jogador faz a entrega. A interface usa um mural de madeira e pergaminho. As decisões sobre inventários e pagamentos são tomadas no servidor.

## Instalar e abrir

1. Colocar o JAR de `build/libs/` na pasta `mods` do cliente e do servidor.
2. Para usar dinheiro, instalar também **Crónicas RP 0.5.2** nos dois lados. Sem esse mod, as recompensas usam esmeraldas.
3. Abrir o mural com o botão direito ou usar `/contracts`.

No criativo, o **Mural de contratos** aparece na aba **Contracts & Bounties** e na pesquisa. A versão 0.1.1 corrige a ausência do item nessas listas na 0.1.0. Para atualizar, fechar o jogo/servidor e substituir o JAR antigo pelo novo, mantendo apenas uma versão instalada.

O mural tem o identificador `contracts:contract_board`. A receita usa **3 papéis na linha superior e 6 tábuas de carvalho nas duas linhas inferiores**:

```text
P P P    P = papel
T T T    T = tábuas de carvalho
T T T
```

Um operador também pode usar `/contracts board` para obter o bloco.

## Usar os contratos

- Publicar um pedido de um item pelo seu identificador, quantidade e recompensa. A recompensa é retirada no momento da publicação e fica reservada.
- Outro jogador entrega a quantidade completa a partir do seu inventário. O autor não pode concluir o próprio contrato.
- Depois da entrega, a recompensa fica disponível para o trabalhador levantar e o autor pode recolher os materiais no mural. Créditos e materiais pendentes permanecem guardados até serem recolhidos.
- O autor pode cancelar um contrato ainda aberto para recuperar a recompensa reservada.

Os pedidos aceitam itens comuns, identificados por ID, como `minecraft:oak_log`. Não aceitam objetos personalizados com nome, encantamentos ou outros dados específicos. Não há entregas parciais.

São usados apenas os 36 espaços do inventário principal, sem consumir a mão secundária ou equipamento. Se uma recolha não couber por inteiro, o restante fica guardado. Cada autor pode ter até 16 contratos abertos; a quantidade por contrato vai de 1 a 4096. O arquivo mantém até 2048 contratos e substitui o histórico mais antigo já concluído/cancelado quando necessário, preservando os créditos e entregas pendentes.

Na integração com Crónicas RP, os montantes têm duas casas decimais e são guardados em cêntimos. Com esmeraldas, a recompensa é um número inteiro de esmeraldas. Os créditos de jogadores offline ficam guardados neste mod para recolha quando voltarem a entrar; o mod não altera os ficheiros offline do Crónicas RP.

Esta versão não inclui expiração, recompensas por mortes, tesouraria, contratos de grupo ou integração política com Realm Council.

## Compilar e testar

Com o JDK 21 disponível:

```bash
./gradlew build
```

O JAR do mod é gerado em `build/libs/`. Para executar os testes de servidor:

```bash
./gradlew -PcontractGameTests runGameTestServer
```

Os testes da lógica de contratos também podem correr sem descarregar o Minecraft:

```bash
python3 tools/test_domain.py
```

Neste ambiente cloud, o JDK e a configuração segura do proxy são preparados por:

```bash
python3 tools/bootstrap_cloud.py
python3 tools/cloud_gradle.py build --console=plain
python3 tools/cloud_gradle.py -PcontractGameTests runGameTestServer --console=plain
```

Para testar a integração real, colocar o JAR fornecido do Crónicas RP e as suas dependências Origins/Jupiter compatíveis em `compat-libs/` e acrescentar `-PcontractsCompat` ao comando de GameTests. Esses ficheiros não são incluídos nem redistribuídos neste projeto.

Para desenvolvimento, os ambientes habituais do NeoForge estão disponíveis por `./gradlew runClient` e `./gradlew runServer`. A primeira execução do servidor requer aceitar o EULA do Minecraft em `run/eula.txt`.

A validação visual e os testes com vários jogadores devem ser feitos num mundo de teste: publicar, entregar, recolher, cancelar e reiniciar o servidor com entregas pendentes. Repetir com esmeraldas e, separadamente, com Crónicas RP instalado.

Os dados são guardados em `data/contracts_ledger.dat` do mundo, pelos salvamentos normais do Minecraft. Não há transação atómica de disco entre esse ficheiro e os dados de jogador; uma interrupção abrupta durante a gravação exige verificar os backups do mundo. Um arquivo de contratos ilegível bloqueia o carregamento dos contratos, em vez de ser substituído silenciosamente por um arquivo vazio.

## Recursos

A textura do mural foi desenhada para este projeto, sem imagens externas. O mod utiliza texturas de carvalho fornecidas pelo próprio Minecraft nas restantes faces do bloco.
