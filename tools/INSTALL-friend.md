# Fermata Auto (versão modificada): instalação só com o celular

> **Status:** testado apenas no Desktop Head Unit (DHU), ainda não num carro de verdade.
> O Android Auto é mais restritivo com apps de fora da Play Store em centrais reais
> (ver issues #327 e #773 do projeto original). Confirme no carro antes de distribuir.

## Para quem gera o APK

```bash
./gradlew fermata:packageAutoReleaseUniversalApk -PABI=arm64-v8a -PAPP_ID_SFX=.unit
# saída: fermata/build/outputs/apk_from_bundle/autoRelease/fermata-<versão>-auto-release-universal.apk
```

- APK único, com todos os módulos. `arm64-v8a` cobre praticamente todo celular recente.
- `-PAPP_ID_SFX=.unit` gera o pacote `me.aap.fermata.auto.unit`, que convive com o Fermata oficial.
- **Guarde `res/fermata.jks`** (fora do git): é a chave de assinatura. Sem ela, as próximas
  versões não instalam por cima, e o usuário precisa desinstalar e perder as configurações.

---

## Para quem vai instalar

### O que aparece no Android Auto

- **UNI-TV**: a interface do próprio Fermata **em tela cheia** na central, abrindo no YouTube.
  Os toques na central funcionam. Não precisa de permissões especiais.
- **Fermata Media Service**: player de músicas e vídeos do celular, com a interface padrão
  do Android Auto.

### Você vai precisar de

- **Shizuku** (Play Store): necessário para o KingInstaller funcionar **sem PC**, pela depuração
  sem fio do próprio celular.
- **KingInstaller**: instala o APK de um jeito que o Android Auto aceita.

### Passo a passo

1. **Ative as Opções do desenvolvedor:** *Configurações → Sobre o telefone →* toque 7 vezes em
   *Número da versão* (no Xiaomi, em *Versão do HyperOS/MIUI*).
2. **Configure o Shizuku:** abra o app e siga *Iniciar pela depuração sem fio*. Ele ensina a parear
   pela notificação. Faça isso com o Wi-Fi ligado. Depois de reiniciar o celular, é preciso iniciar
   o Shizuku de novo (só para instalar ou atualizar o Fermata).
3. **Instale o Fermata pelo KingInstaller** (autorize o KingInstaller no Shizuku).
   **Não instale tocando no arquivo**, senão o app não aparece no carro.
4. **Abra o Fermata** uma vez no celular e conceda as permissões que ele pedir.
   Para a busca por voz no carro, permita o **Microfone** (*Configurações → Apps → Fermata Auto →
   Permissões*). Sem ele, a busca usa o teclado da central.
5. **No Android Auto** (*Configurações → busque "Android Auto"*):
   - toque 10 vezes em *Versão* para ativar o modo desenvolvedor;
   - em **⋮ → Configurações do desenvolvedor**, ative **Fontes desconhecidas**.
6. **No carro:** abra o **UNI-TV**.

### Se não aparecer no carro

- Confira se o passo 5 está feito e se o Fermata foi instalado pelo KingInstaller.
- Force a parada do Android Auto e apague os dados dele
  (*Apps → Android Auto → Forçar parada → Armazenamento → Limpar dados*), depois refaça o passo 5.
  Essa dica vem da comunidade do Fermata e às vezes precisa ser repetida.

### Avisos

- Assistir vídeos no UNI-TV esquenta o celular e gasta bateria. Use com o celular carregando.
- Atualizações do Android Auto podem quebrar isso a qualquer momento. É um uso que o Google não
  suporta oficialmente.
