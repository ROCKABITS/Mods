# Validação — Realm Progression 0.1.0

Minecraft 1.21.1, NeoForge 21.1.256 e Java 21. **Validação local e publicação aprovadas no [workflow 40563579557](https://github.com/ROCKABITS/Mods/actions/runs/40563579557)**. O JAR, ZIP e as 14 capturas reais estão disponíveis no repositório.

| Suite | Execuções aprovadas |
| --- | ---: |
| JUnit: domínio e armazenamento | 25 |
| GameTests com Realm Treasury | 26 |
| GameTests com Create 6.0.10 e os companheiros reais | 26 |
| Total de lógica/servidor | **77** |

Além destas suites, passou o cenário com **dois clientes Minecraft ligados por TCP LAN**, incluindo uma doação real, fabrico real do Create e encerramento/reabertura do mundo. A configuração de um segundo mod foi enviada pelo menu de produção e a tentativa de configuração de um convidado sem OP foi recusada pelo servidor.

Código validado: `39c142b737a8dbf374c43be978c121caac61da7b`. JAR SHA-256: `ad2b7e8b72a5a5a3f0b28dcde9e165a047f02f4769a92805b55d2d216c126d69`. O JAR publicado coincide com o snapshot local; os downloads JAR/ZIP/media e o JAR contido no ZIP foram verificados após a publicação. [Metadados](../mod-pronto/realm-progression-0.1.0.build.json) · [Galeria](../media/realm-progression/0.1.0/README.md).

## Suites executáveis

- JUnit: metas em cêntimos, desbloqueios independentes e permanentes, alterações de configuração, proteção de componentes essenciais, colisões de namespaces, gravação/releitura real, JSON inválido, UTF-8 inválido e falhas de substituição atómica.
- GameTests nativos com o JAR real do Realm Treasury: receitas, resultados alternativos de datapacks, fabrico 2×2/3×3, resultados antigos, cliques normais, Shift, crafter/caches, permissões, nonces, revisões, fundos em euros, esmeraldas, persistência e falhas de gravação.
- A mesma suite com Create 6.0.10 e os cinco companheiros reais fixados no publicador.
- Dois processos Minecraft ligados por TCP LAN: menu administrativo configura Realm Economy, convidado sem OP tenta um pacote não autorizado, receita real do Create permanece bloqueada, uma doação real pela carteira do Realm Origins atinge os 8.000 €, o Create desbloqueia nos dois clientes e Shift fabrica exatamente duas unidades consumindo os ingredientes. Uma despesa pública baixa o saldo, o mundo é realmente encerrado/reaberto e a aprovação e os itens mantêm-se.

As capturas são produzidas pelo cliente real a 1600×900. Os layouts são verificados a 320×240, 480×270 e 800×450. Os fundos e materiais iniciais são preparados para demonstração; os menus e os crafts executam o código de produção. As identidades LAN são de teste offline. Não existem duas contas Microsoft autenticadas neste cenário.

## Limites

A validação não cobre máquinas próprias, produção automática por workers ou todas as versões do Create/outros mods. O âmbito suportado está no README. Não há um Realm Admin executável neste repositório; existe um ponto de integração de permissões que recusa administração quando esse fornecedor é necessário e não está registado.

Os testes de falha injetam erros de escrita deliberadamente. Mensagens desses cenários não significam que uma compilação passou: o publicador exige a contagem nativa completa, os marcadores financeiros, ausência de falha fatal, relatórios dos clientes com sucesso e um JAR de produção sem fixtures.

O workflow guarda o commit de origem, hashes dos JARs, relatórios e imagens. A publicação verifica que o JAR não foi alterado pelas suites e que `main` ainda corresponde ao código testado.
