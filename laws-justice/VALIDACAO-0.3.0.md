# Validação — Laws & Justice 0.3.0 Beta

[Execução aprovada](https://github.com/ROCKABITS/Mods/actions/runs/39221369761), código `a9ec0d82aef467b82016e6119d4640f87619d6fa`. JAR e 37 capturas publicados no commit `816727d`. Os testes locais e a repetição independente no GitHub Actions passaram.

| Verificação | Resultado |
| --- | ---: |
| Testes unitários | 106, sem falhas ou ignorados |
| GameTests independentes | 95 |
| GameTests com dez mods companheiros | 95 |
| Cliente Minecraft real integrado | Aprovado, 37 capturas |

A galeria inclui dois enquadramentos aproximados dos postos, além das 35 capturas funcionais verificadas localmente. As validações publicadas das fases [0.1](VALIDACAO-0.1.0.md) e [0.2](VALIDACAO-0.2.0.md) conservam a proveniência das versões anteriores.

## Trabalho e redução de pena

O servidor validou as seis tarefas com blocos e eventos reais: mineração, colheita de trigo maduro, carpintaria, ferraria com minério e combustível finitos, limpeza com escova e transporte entre dois postos separados. A produção da mesa e a limpeza terminaram através dos ticks normais do servidor; o relógio da pena continuou a avançar. Afastar-se interrompeu a produção sem consumir as matérias-primas.

Os testes rejeitaram trabalho sem condenação, modo criativo, alterações de regras por prisioneiros, progresso inventado pelo cliente, materiais externos, contadores falsificados no saco, repetição de recursos, entrega numa zona errada e repetição da mesma entrega. A inspeção não reiniciou pontos consumidos. Só uma reposição física por um administrador em Sobrevivência permitiu a renovação deliberada.

A conclusão, o crédito real de trabalho e a redução da pena ficam no mesmo ficheiro atómico. A falha real de uma escrita não consumiu o saco nem atribuiu metade da recompensa. Reduções sem crédito e créditos sem redução foram rejeitados. A recompensa foi limitada a 50% da pena original e ao tempo restante. Uma entrega que terminou a condenação utilizou a fila e a saída normais de libertação, sem duplicar o registo COMPLETED.

## Persistência e migração

O formato três acrescenta zonas, pontos finitos, regras, tarefas, sacos lógicos e créditos. Os formatos um e dois são migrados sem reiniciar as penas ou apagar processos. Os testes incluem ficheiros comprimidos reais e rejeição de créditos divergentes, objetivos inválidos e recursos com referências inexistentes. O fim da condenação cancela a autorização de trabalho antiga, também depois de reabrir os dados.

A base das prisões e o tribunal continuam testados: relógio online, morte/respawn, contenção, capacidade, decisões atómicas, libertação, dados estritos, mandato empossado, perda imediata de autoridade do Presidente, provas privadas, audiência obrigatória, mandados e algemas. Não foram criados cargos adicionais.

## Cliente e blocos originais

O cliente real carregou as sete novas peças e as 28 orientações correspondentes, além do Saco de Trabalho Selado. Todos os modelos anteriores foram conservados. Os limites e UVs dos 35 modelos JSON foram verificados; os recursos antigos permanecem iguais byte a byte. Os novos blocos têm receitas, criativo/pesquisa e traduções em português e inglês. O saco não tem receita nem guarda contadores editáveis.

Os botões reais registaram uma zona selecionada pelo agrimensor, validaram os recursos e guardaram as regras. Os cinco separadores administrativos e os três do prisioneiro passaram nas verificações de scroll e limites a 320 × 240.

Um anfitrião real, em Sobrevivência, sem operador nem voo, completou os seis trabalhos através de pacotes normais de mineração, colheita, machado, minério/carvão e escova, dos botões temporizados da mesa e de dois postos físicos do armazém. Cada tarefa de demonstração foi configurada para uma unidade e um minuto de recompensa. As seis entregas deram exatamente 7200 ticks de redução à pena real de vinte minutos; o ficheiro reaberto conservou os seis registos e o mesmo crédito. Não foram injetados contadores de progresso ou snapshots S2C.

O mesmo percurso de validação inclui a morte/respawn, o teleporte e o fecho/reabertura real do mundo LAN da base prisional, uma libertação pelo relógio natural, o Presidente sem operador, processos, provas, audiência, absolvição, pergaminho usado através do cliente e perda de autoridade após troca do mandato.

## Limites

Construções, personagens, mandato, processos e regras reduzidas das imagens são demonstrações de teste. O mod não gera prisões nem recursos; as quantidades e recompensas normais são as documentadas no guia. A prova usa um cliente integrado e não estabelece uma sessão autenticada entre dois clientes remotos.

Foram utilizados os dez companheiros publicados, incluindo Chronicles RP, Realm Council, Crown Ledger, Markets, Rewards, Notifications, Titles, Identity, Casino e Contracts, com Origins/Jupiter reais e checksums fixados. Windows e todas as combinações possíveis de mods não foram testados. Conserva cópias de segurança do mundo; um encerramento abrupto pode perder os últimos ticks desde o checkpoint.

Multas, fianças e integrações financeiras continuam na fase 0.4 do [plano completo](PLANO-COMPLETO.md). A fechadura continua a ser um bloco auxiliar de controlo, sem associação de chaves a portas.

## Publicação verificada

JAR, ZIP e 37 PNGs foram verificados por SHA-256 depois de obter o commit publicado. O download HTTP do JAR também corresponde ao checksum.

- [JAR](../mod-pronto/laws-justice-0.3.0.jar): 462 028 bytes; SHA-256 `f9474d2ede74ab859c0d697baa01a6c1ca097b25a74b2ecf44ab3325e96fe43e`.
- [ZIP](../mod-pronto/laws-justice-0.3.0.zip): 434 832 bytes; SHA-256 `b4a9df54a65b1c24cfa0b74e22dac006fc6954276a64763cea2f5760d7ddb107`.
- [Proveniência](../mod-pronto/laws-justice-0.3.0.build.json) · [37 imagens](../media/laws-justice/0.3.0/README.md).
