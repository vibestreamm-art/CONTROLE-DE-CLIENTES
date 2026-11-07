# Compilar o APK

O projeto usa Kotlin nativo, Android Gradle Plugin e ferramentas Android gratuitas.
O aplicativo requer Android 10 (API 29) ou superior.

## Requisitos

- JDK 17 ou superior.
- Android SDK com a plataforma 35 e Build Tools 35.0.0.
- Gradle Wrapper incluído no projeto.

## Gerar um APK de teste

Na raiz do projeto, execute:

```bash
./gradlew assembleDebug
```

O APK instalável será criado em:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Esse APK de depuração pode ser instalado para testes. Uma versão de distribuição
precisa ser compilada como `release` e assinada com uma chave própria, guardada em
local seguro. O projeto não envia dados para serviços externos; os dados ficam no
banco SQLite do aparelho. Desinstalar o app pode apagar esses dados.
