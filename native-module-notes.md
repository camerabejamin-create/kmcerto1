# Notas de implementação nativa do KmCerto

A documentação oficial do Expo confirma que, em um projeto existente, a forma recomendada de adicionar código nativo é criar um **módulo local** dentro do diretório `modules/` usando `create-expo-module --local`. Esse fluxo gera a estrutura `modules/<nome>/android`, `modules/<nome>/ios`, `modules/<nome>/src`, `expo-module.config.json` e `index.ts`.

A mesma documentação confirma que **config plugins** são a forma adequada de modificar o `AndroidManifest.xml`, inserir metadados, registrar componentes nativos e aplicar customizações durante o `prebuild`. O padrão recomendado é criar um plugin síncrono com `withAndroidManifest` e helpers de `AndroidConfig` para alterar o manifesto.

Para o KmCerto, isso orienta a seguinte arquitetura técnica:

| Tema | Direção de implementação |
|---|---|
| Módulo nativo | Criar módulo local `modules/kmcerto-native` |
| Ponte JS | Exportar métodos do módulo para permissões, monitoramento e overlay |
| Plugin | Registrar serviços Android, permissões e recursos XML no manifesto e em `res/xml` |
| Leitura de configuração | Ler metadados do manifesto no código Kotlin quando necessário |
| Build | O usuário poderá gerar o APK depois via Expo/EAS, após publicar o código no GitHub |

Essas notas serão a base da implementação nativa Android nas próximas etapas.
