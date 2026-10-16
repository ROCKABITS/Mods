# Validação — Realm Markets

## Resultados aprovados — 0.1.1

A versão **0.1.1 Beta** foi compilada, testada e publicada pela [execução 38887747972](https://github.com/ROCKABITS/Mods/actions/runs/38887747972), sobre o commit `6e8f8ab186f9ba1ba6eace7af3dab1cfb0475633`. A publicação automatizada terminou depois de todas as verificações exigidas. Esta atualização corrige o ângulo de apresentação dos dois modelos de item para mostrar a frente do balcão, a placa e o toldo às riscas; a geometria das bancas colocadas e as operações de comércio mantêm-se.

| Verificação | Resultado |
| --- | --- |
| Compilação e empacotamento | Java 21, Minecraft 1.21.1 e NeoForge 21.1.256; aprovados |
| Testes JUnit | 32 aprovados, sem falhas, erros ou testes ignorados |
| Testes nativos independentes | 37 testes obrigatórios aprovados |
| Testes nativos com os quatro mods companheiros | Os mesmos 37 testes obrigatórios aprovados |
| Cliente Minecraft real | Aprovado; dois modelos de item e os 32 estados de bloco validados |
| Interface de produção | Modos de proprietário/comprador, preços, pedidos dos botões e deslocação a 320×240 aprovados |
| Capturas | Três PNG do cliente Minecraft real |
| Inspeção do JAR | Versão e recursos obrigatórios presentes; classes e estruturas de teste ausentes |

Os testes nativos verificam colocação nas quatro direções, cancelamento e obstrução da colocação, propriedade, stock real, pagamentos em esmeraldas, ganhos de um proprietário offline, recolha parcial, inventários cheios, compras concorrentes sequenciais, limites de valores e preços alterados após a consulta. Verificam também pedidos malformados, distância e validade da sessão, remoção protegida, explosões, suporte do terreno, rejeição de itens personalizados ou contentores e persistência NBT estrita.

A execução de coexistência carregou os JARs publicados de **Contracts & Bounties 0.3.1**, **Realm Council 0.2.1**, **Crown Ledger 0.1.1** e **Realm Identity 0.1.0**. Cada JAR foi verificado contra o seu SHA-256 conhecido e a sua proveniência. Esta execução verifica coexistência; não contém Chronicles RP.

As [três imagens 0.1.1](../media/realm-markets/0.1.1/README.md) mostram os modelos de item registados pelo Minecraft e os menus de gestão e compra. A validação do cliente também verificou os modelos compilados dos 32 estados de bloco: duas variantes, quatro partes e quatro direções. Os menus usam artigos e saldos de demonstração, identificados como tal. Os pedidos dos botões foram registados localmente, sem os enviar a um servidor nesse teste visual; as operações de servidor são verificadas separadamente pelos testes nativos.

## Ficheiros publicados — 0.1.1

- [JAR](../mod-pronto/realm-markets-0.1.1.jar): **137 949 bytes**.
- [ZIP com JAR, instruções, changelog e licença](../mod-pronto/realm-markets-0.1.1.zip): **127 960 bytes**.
- [Checksums](../mod-pronto/realm-markets-0.1.1.sha256).
- [Proveniência da compilação, resultados e hashes dos companheiros](../mod-pronto/realm-markets-0.1.1.build.json).
- [Resultado da validação do cliente](../media/realm-markets/0.1.1/validation.json).

```text
0a5a3efeabcfa1d1d53fa00bb2633885cc3970297f90966d0e05d3c4f2024bb4  realm-markets-0.1.1.jar
55a74d679be588f6173b4b6c97964e25df1858880abc48dfa79df0acbec45a41  realm-markets-0.1.1.zip
```

## Histórico — 0.1.0

A [execução 38887031968](https://github.com/ROCKABITS/Mods/actions/runs/38887031968), sobre o commit `43654c451d32cf5e11cdbe8da943f3a0d406dcbe`, aprovou também 32 testes JUnit, 37 testes nativos independentes, 37 com os quatro companheiros e a validação do cliente. Os [artefactos e hashes 0.1.0](../mod-pronto/realm-markets-0.1.0.build.json) e as suas imagens mantêm-se preservados; usa a versão 0.1.1 para o ângulo de apresentação corrigido.

## Limites

A integração completa de **Chronicles RP 0.5.2, Origins e Jupiter** dentro do Minecraft não está incluída neste CI. Não se redistribuem os JARs originais para simular essa cobertura. A coexistência com os restantes mods, a rejeição de uma moeda Chronicles indisponível e os preços em cêntimos da interface não equivalem à execução do conjunto completo.

Uma sessão com dois clientes reais e um reinício manual do mundo constituem validação adicional. Os testes automatizados usam jogadores de teste do servidor e verificações de persistência; as capturas de menus não demonstram transferências entre contas reais do Chronicles.

Os registos do mundo, os inventários e os perfis usam a persistência normal do Minecraft. As verificações no thread principal não criam uma transação de disco única entre ficheiros numa falha abrupta.

## Ambiente cloud

A preparação do ambiente reutiliza o JDK 21, o Gradle e o truststore verificados dos outros projetos. A última tentativa local de descarregar NeoForm 2.0.31 recebeu HTTP 403 no Maven NeoForge antes da compilação Minecraft. Os 32 testes independentes do domínio passaram localmente; a compilação Minecraft e as verificações completas acima passaram no GitHub Actions. Esse resultado não demonstra que o acesso local ao Maven foi desbloqueado. Não se desativou TLS nem se alteraram checksums para contornar o bloqueio.
