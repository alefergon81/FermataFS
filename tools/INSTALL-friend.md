# Fermata Auto (versão modificada): instalação só com o celular

> **Status:** testado apenas no Desktop Head Unit (DHU), ainda não num carro de verdade.
> O Android Auto é mais restritivo com apps de fora da Play Store em centrais reais
> (ver issues #327 e #773 do projeto original). Confirme no carro antes de distribuir.

## Para quem gera o APK

```bash
./gradlew fermata:packageAutoReleaseUniversalApk -PABI=arm64-v8a -PAPP_ID_SFX=.unit
# saída: fermata/build/outputs/apk_from_bundle/autoRelease/fermata-2.0.2-auto-release-universal.apk
```

- APK único, com todos os módulos. `arm64-v8a` cobre praticamente todo celular recente.
- `-PAPP_ID_SFX=.unit` gera o pacote `me.aap.fermata.auto.unit`, que convive com o Fermata oficial.
- **Guarde `res/fermata.jks`** (fora do git): é a chave de assinatura. Sem ela, as próximas
  versões não instalam por cima, e o usuário precisa desinstalar e perder as configurações.

---

## Para quem vai instalar

### O que funciona no Android Auto

- ✅ **Fermata Media Service**: player de músicas e vídeos do celular, com a interface padrão do Android Auto.
- ✅ **FS Fermata Mirror**: espelha a tela do celular **em tela cheia** na central. Os toques na central funcionam.
- ❌ O "Fermata Auto" com interface própria **não aparece** nesta versão; só a oficial tem isso.

### Você vai precisar de

- **Shizuku** (Play Store): ativa permissões especiais **sem PC**, pela depuração sem fio do próprio celular.
- **KingInstaller**: instala o APK de um jeito que o Android Auto aceita. Precisa do Shizuku.

### Passo a passo

1. **Ative as Opções do desenvolvedor:** *Configurações → Sobre o telefone →* toque 7 vezes em
   *Número da versão* (no Xiaomi, em *Versão do HyperOS/MIUI*).
2. **Configure o Shizuku:** abra o app e siga *Iniciar pela depuração sem fio*. Ele ensina a parear
   pela notificação. Faça isso com o Wi-Fi ligado. Depois de reiniciar o celular, é preciso iniciar
   o Shizuku de novo.
3. **Instale o Fermata pelo KingInstaller** (autorize o KingInstaller no Shizuku).
   **Não instale tocando no arquivo**, senão o app não aparece no carro.
4. **Abra o Fermata** uma vez no celular e conceda as permissões que ele pedir.
5. **Libere as permissões do espelhamento:**
   - *Configurações → Apps → Fermata Auto*:
     - **Outras permissões** (só Xiaomi): ative *Exibir janelas pop-up em segundo plano* e
       *Exibir na tela de bloqueio*;
     - **Exibir sobre outros apps**: permitir;
     - **Modificar configurações do sistema**: permitir;
     - **Economia de bateria**: *Sem restrições*.
   - **Acessibilidade:** *Configurações → Acessibilidade → Fermata Auto → ativar*.
     Se der **"acesso negado"** ou "configuração restrita": volte em *Apps → Fermata Auto*, toque
     em **⋮** no canto superior direito, escolha **Permitir configurações restritas** e tente de novo.
6. **No Android Auto** (*Configurações → busque "Android Auto"*):
   - toque 10 vezes em *Versão* para ativar o modo desenvolvedor;
   - em **⋮ → Configurações do desenvolvedor**, ative **Fontes desconhecidas**.
7. **No carro:** abra o **FS Fermata Mirror** com o **celular desbloqueado**.
   - Toda vez o Android pergunta se pode **capturar a tela**: aceite.
   - Enquanto espelha, o Fermata gira o celular para paisagem e apaga o brilho; ao desconectar,
     volta ao normal.

### Se não aparecer no carro

Tente nesta ordem:
- force a parada do Android Auto e apague os dados dele:
  *Apps → Android Auto → Forçar parada → Armazenamento → Limpar dados*;
- depois refaça o passo 6.

Essa dica vem da comunidade do Fermata e às vezes precisa ser repetida.

### Avisos

- O espelhamento esquenta o celular e gasta bateria. Use com o celular carregando.
- Atualizações do Android Auto podem quebrar isso a qualquer momento. É um uso que o Google não
  suporta oficialmente.
- O passo 5 ("Outras permissões") existe só em Xiaomi (HyperOS/MIUI). Em outras marcas, o resto deve bastar.
