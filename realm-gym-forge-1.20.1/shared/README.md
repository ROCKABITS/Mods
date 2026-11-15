# Base de seis máquinas — Realm Gym Forge 1.20.1

Cópia dos ficheiros de domínio, geometria, apresentação, testes e recursos da edição NeoForge 0.5.1 verificada no commit `574625ec558c96d541ae55681a383dfc5f8ccdbd`. Os ficheiros iniciais são idênticos aos originais em `realm-gym/src/` nesse commit.

A edição Forge 1.20.1 aplica as suas adaptações Java 17 através de `build.gradle`. Esta base local permite validar e manter o port independentemente das expansões posteriores para NeoForge 1.21.1. Alterações futuras devem incluir os testes da edição Forge e uma nova versão antes de substituir downloads publicados.

A atualização 0.7.0 acrescenta a condição física opcional, os indicadores do perfil e os respetivos testes. O núcleo `Conditioning.java` e `ConditioningTest.java` é partilhado com a edição 1.21.1 e compilado para Java 17. As seis máquinas e a geometria desta base continuam independentes das expansões 1.21.1.
