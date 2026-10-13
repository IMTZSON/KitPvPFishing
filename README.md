# KitPvPFishing

Plugin di pesca per server **KitPvP 1.8.x** (client **1.8.9**). A ogni cattura riuscita il giocatore riceve **una sola** ricompensa, scelta tra:

1. **Pesce**: un solo pesce, che sostituisce il bottino vanilla.
2. **Armatura**: un solo pezzo di diamante con Protezione e Indistruttibilità (Unbreaking) a livelli configurabili, anche oltre i limiti vanilla.
3. **Ricompensa a comandi**: comandi eseguiti dalla console, per esempio la key di un plugin di casse.

---

## Compatibilità e scelta delle API

| Voce | Scelta |
|---|---|
| API | `org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT` (scope `provided`) |
| Server supportati | Spigot / CraftBukkit **1.8.8**, compresi i fork 1.8.8 come PaperSpigot |
| Client | 1.8 – 1.8.9 |
| Java | bytecode **Java 8** (major version 52) |
| Build | Maven |

**Perché 1.8.8:** la 1.8.9 è uscita solo per il client e non esiste un server 1.8.9. Tutta la linea 1.8.x usa lo stesso protocollo di rete (47), quindi un client 1.8.9 si collega senza problemi a un server 1.8.8. Spigot 1.8.8 è l'ultima build server della 1.8, per cui le API corrette sono `1.8.8-R0.1-SNAPSHOT`.

Altre scelte:
- Si usano solo API presenti nella 1.8.8. Tutte sono state controllate sul JAR delle API: `PlayerFishEvent.State.CAUGHT_FISH`, `Item#setItemStack`, `Material.RAW_FISH` con data value, `Enchantment.PROTECTION_ENVIRONMENTAL`, `Enchantment.DURABILITY` e `ItemFlag.HIDE_ENCHANTS`.
- Non ci sono dipendenze a runtime. Nel JAR finiscono solo le classi del plugin, `plugin.yml` e `config.yml`.
- `bungeecord-chat:1.8-SNAPSHOT`, una dipendenza transitiva delle API, non è più pubblicata nei repository. Per questo è esclusa nel `pom.xml`. Il plugin non la usa e il server la include comunque.
- `plugin.yml` non contiene `api-version`, perché quel campo esiste solo dalla 1.13.

## Compilazione

Requisiti: JDK 8 o superiore e Maven 3.6 o superiore.

```bash
mvn clean package
```

Il JAR viene creato in `target/KitPvPFishing-1.0.0.jar`. Il JAR già compilato si scarica dalla pagina **Releases** del repository GitHub.
Il `pom.xml` sceglie le opzioni giuste in base al JDK: `source/target 1.8` con JDK 8, `--release 8` con JDK 9 o superiore. Con `--release 8` il compilatore verifica anche che si usino solo API di Java 8. Con JDK 21 compaiono gli avvisi "source/target value 8 is obsolete": sono normali.

## Installazione

1. Copia `KitPvPFishing-1.0.0.jar` nella cartella `plugins/` del server.
2. Avvia il server: viene creato `plugins/KitPvPFishing/config.yml`.
3. Modifica il config e poi usa `/kitpvpfishing reload`.

## Comandi e permessi

| Comando | Descrizione |
|---|---|
| `/kitpvpfishing reload` | Ricarica `config.yml`. Se ci sono errori, resta attiva l'ultima configurazione valida. |
| `/kitpvpfishing chances` | Mostra le probabilità reali: categorie, pesci, pezzi, livelli e ricompense. |

Alias: `/kpfishing`. Entrambi i comandi funzionano anche dalla console.

| Permesso | Default | Descrizione |
|---|---|---|
| `kitpvpfishing.admin` | `op` | Permette di usare `/kitpvpfishing reload` e `chances`. |

Non serve alcun permesso per ricevere le ricompense di pesca.

## Funzionamento della pesca

- Solo lo stato `CAUGHT_FISH` produce ricompense, cioè il recupero dell'amo con un oggetto agganciato.
- Non danno nulla: il lancio (`FISHING`), un'entità agganciata (`CAUGHT_ENTITY`), l'amo a terra (`IN_GROUND`) e il recupero a vuoto (`FAILED_ATTEMPT`).
- La pesca resta **vanilla** in questi casi:
  - plugin disattivato (`enabled: false`);
  - mondo non presente in `worlds`;
  - nessuna categoria valida;
  - config non valido al primo avvio.
- **Cancellazioni:** il listener lavora a priorità `HIGHEST` con `ignoreCancelled = true`, quindi gli eventi già cancellati da altri plugin vengono ignorati. La consegna avviene solo nella fase `MONITOR`. Se un altro plugin cancella l'evento dopo l'estrazione, non viene consegnato nulla, e nemmeno il bottino vanilla viene generato.

Come viene gestito il bottino originale, cioè l'entità oggetto che il server sta per lanciare verso il giocatore:

| Categoria | Bottino originale | Consegna |
|---|---|---|
| Pesce | **sostituito** con un solo pesce | Vola verso il giocatore come nella pesca vanilla. |
| Armatura | **rimosso** | Va direttamente nell'inventario; se è pieno, viene lasciata a terra nella posizione del giocatore. |
| Comandi | **rimosso** | Ci pensa il plugin che riceve il comando. |

Nessuna categoria può generare due ricompense. L'oggetto lasciato a terra è esattamente il resto restituito da `addItem`, quindi non si creano copie. L'esperienza vanilla della pesca (1–6 XP) resta invariata.

## Probabilità

**I pesi sono relativi e non devono sommare a 100.** La probabilità di una voce è:

```
probabilità = peso della voce / somma dei pesi delle voci valide
```

Per esempio `80 / 15 / 5` dà le stesse probabilità di `16 / 3 / 1`.

L'estrazione avviene in tre passaggi:

1. **Categoria:** fish 80, armor 15, commands 5.
2. **Contenuto:**
   - pesce: cod 60, salmon 25, clownfish 2, pufferfish 13;
   - pezzo di armatura: 25 ciascuno;
   - ricompensa a comandi: tra quelle attive.
3. **Solo per l'armatura:** Protezione e Indistruttibilità vengono estratte con **due estrazioni separate e indipendenti**.

**Esclusioni:** una categoria disattivata, con peso 0 o senza ricompense valide viene esclusa, e le probabilità si ricalcolano sulle altre. Con il config di esempio la key è disattivata, quindi la categoria comandi è esclusa e le probabilità diventano:

| Categoria | Peso | Probabilità |
|---|---|---|
| Pesce | 80 | 80/95 = **84,21%** |
| Armatura | 15 | 15/95 = **15,79%** |
| Comandi | 5 | esclusa |

Quando attivi almeno una ricompensa a comandi, le probabilità tornano a 80% / 15% / 5%.

Esempi con il config di esempio, calcolati all'interno dell'armatura:
- un **elmo**: 15,79% × 25% = **3,95%** per cattura;
- **Protezione 10**: peso 2 su 100, cioè 2%;
- **Indistruttibilità 10**: anche questa 2%;
- **entrambe al livello 10**: 2% × 2% = **0,04%** dei pezzi di armatura.

## Configurazione delle armature

```yaml
armor:
  enchantments:
    protection:
      min: 4            # livello minimo (1–32767)
      max: 10           # livello massimo
      default-weight: 1 # peso dei livelli dell'intervallo non elencati (0 = esclusi)
      levels:
        4:  { enabled: true, weight: 30 }
        10: { enabled: true, weight: 2 }   # livelli alti più rari
```

- Un livello è estraibile solo se rispetta tutte e tre le condizioni:
  - è compreso tra `min` e `max`;
  - ha `enabled: true`;
  - ha `weight` maggiore di 0.
- Per un livello è accettata anche la forma breve `7: 12`, che indica solo il peso.
- I livelli elencati ma fuori dall'intervallo vengono ignorati, con un avviso nel log.
- Se un incantesimo non ha nessun livello estraibile, la categoria armature viene esclusa e compare un avviso.
- Indistruttibilità = **Unbreaking**. L'oggetto ha la normale durabilità e si consuma soltanto più lentamente.
- I livelli oltre 10 funzionano, ma il client 1.8.9 non ha la traduzione dei numeri romani oltre il 10 e mostra `enchantment.level.11`. In quel caso conviene `hide-enchants: true` insieme a una lore con `%protection%` e `%unbreaking%`.
- Nome e lore accettano i codici `&` e questi placeholder: `%piece%`, `%protection%`, `%protection_roman%`, `%unbreaking%`, `%unbreaking_roman%`, `%player%`.

## Configurazione delle key (ricompense a comandi)

```yaml
command-rewards:
  key_comune:                       # ID libero e univoco
    enabled: false
    weight: 100                     # peso relativo tra le ricompense a comandi
    display-name: "&aKey Comune"    # usato come %reward% nel messaggio
    message: "&7Hai pescato una %reward%&7!"   # "" = nessun messaggio
    commands:
      - "SOSTITUISCI-CON-IL-COMANDO-DEL-TUO-PLUGIN %player% comune 1"
```

**Il comando dell'esempio è un segnaposto, non un comando reale.** Ogni plugin di casse o key ha la propria sintassi.

Per configurare una key:
1. Cerca nella documentazione del **tuo** plugin di casse il comando che dà una key a un giocatore.
2. Provalo dalla console del server con il nome di un giocatore reale.
3. Inseriscilo in `commands`, sostituendo il nome del giocatore con `%player%`.
4. Imposta `enabled: true` e usa `/kitpvpfishing reload`.

Altri dettagli:
- **Placeholder nei comandi:** `%player%`, `%uuid%`, `%world%`. La `/` iniziale è facoltativa e viene rimossa.
- **Esecuzione:** si possono aggiungere quante ricompense si vuole. Quando esce la categoria comandi viene scelta **una** ricompensa e ne vengono eseguiti **tutti** i comandi, dalla console e nell'ordine scritto.
- **Avvisi nel log:**
  - quando una ricompensa attiva contiene ancora il segnaposto `SOSTITUISCI`;
  - quando il server non riconosce un comando.
- **Inventario pieno:** se l'inventario del giocatore è pieno, la gestione spetta al plugin che riceve il comando.

## Validazione e reload

- Ogni problema viene registrato nel log con il **percorso esatto**, per esempio:
  - `[armor.enchantments.protection] min (12) e' maggiore di max (10)`;
  - `[command-rewards.vip.commands[1]] comando vuoto`.
- **Errori** (configurazione rifiutata):
  - sintassi YAML;
  - tipi sbagliati;
  - pesi negativi o non numerici;
  - `min > max`;
  - livelli fuori da 1–32767 o non interi;
  - pezzi o pesci sconosciuti;
  - ricompense attive senza comandi;
  - comandi vuoti.

  Vengono raccolti tutti insieme.
- **Avvisi** (configurazione accettata):
  - chiavi sconosciute;
  - categorie escluse;
  - livelli fuori dall'intervallo;
  - livelli oltre 10;
  - comando segnaposto non sostituito.
- **Reload con errori:** resta attiva l'**ultima configurazione valida**. In chat compaiono i primi errori, in console l'elenco completo.
- **Errori al primo avvio:** la pesca resta vanilla finché il file non viene corretto e ricaricato.
- **Codifica:** il file viene letto sempre in UTF-8, anche sui server Windows. In questo modo le lettere accentate restano corrette.

## Struttura del codice

```
it.kitpvpfishing
├── KitPvPFishingPlugin        avvio e registrazioni
├── config/                    gestione della configurazione
│   ├── ConfigManager          lettura del file, ultima configurazione valida
│   ├── SettingsLoader         parsing e validazione con i percorsi
│   ├── PluginSettings         istantanea immutabile
│   ├── ValidationReport       errori e avvisi
│   └── Messages, MessageKey   messaggi personalizzabili
├── reward/                    estrazione e consegna delle ricompense
│   ├── WeightedTable          estrazione a pesi relativi
│   ├── RewardRoller           categoria → contenuto → livelli indipendenti
│   ├── ArmorItemFactory       creazione del pezzo incantato
│   └── RewardDelivery         inventario/drop, comandi, messaggi
├── listener/FishingListener   eventi di pesca (HIGHEST + MONITOR)
└── command/AdminCommand       /kitpvpfishing reload | chances
```

## Verifiche eseguite

**Compilato e testato in questo ambiente**, con JDK 21 e `--release 8` sulle API Spigot 1.8.8. La build dà 60 test JUnit, tutti superati, e il bytecode è Java 8 (major 52).

| Area | Cosa viene verificato |
|---|---|
| Estrazioni | Distribuzione 80/15/5 su 300.000 estrazioni (±0,5%). Pesi che non sommano a 100. Pesi 0, negativi o NaN mai estratti. Valori casuali estremi. |
| Indipendenza | Protezione e Indistruttibilità: P(p,u) ≈ P(p)·P(u) su 400.000 estrazioni. Le combinazioni 4/10 e 10/3 escono. |
| Filtri sui livelli | Vengono esclusi i livelli: disattivati, con peso 0, sotto `min`, sopra `max`. Funzionano `default-weight` e la forma breve. Senza livelli validi la categoria viene esclusa. |
| Config non valide | Oltre 20 casi, ognuno con il percorso atteso nel messaggio. Tutti gli errori vengono raccolti insieme. Sintassi YAML errata. |
| Reload | Dopo un reload errato resta l'ultima configurazione valida. Un errore al primo avvio lascia la pesca vanilla. Funzionano UTF-8 e BOM. |
| Listener | Gli stati diversi da `CAUGHT_FISH` vengono ignorati. Per categoria: il pesce sostituisce il bottino; armatura e comandi lo rimuovono. |
| Inventario pieno | Il pezzo viene lasciato a terra una sola volta, senza copie, con il messaggio dedicato. |
| Nessun duplicato | Su 3.000 catture miste, ognuna produce esattamente un effetto: 1 pesce, oppure 1 pezzo, oppure 1 esecuzione dei comandi. |
| Cancellazioni | Un evento cancellato prima viene ignorato. Una cancellazione dopo l'estrazione non consegna nulla. |
| Filtri | Mondi (senza distinzione tra maiuscole e minuscole), plugin disattivato, nessuna categoria (avviso una sola volta). |
| Comandi admin | Permesso, reload valido o non valido, output di `chances`, tab completion. |

Il server Bukkit è simulato con proxy Java (`src/test/java/.../testutil`). Il dispatch degli eventi riproduce le regole di Bukkit: ordine di priorità e `ignoreCancelled`. Come controllo dei test sono state introdotte due mutazioni volute: bottino non rimosso e livelli correlati. Entrambe sono state rilevate dai test.

**Non verificato: richiede un server Minecraft reale.**
- Il caricamento effettivo su Spigot/PaperSpigot 1.8.8 e il ciclo completo con un client 1.8.9.
- Il comportamento interno di CraftBukkit 1.8.8. Si presume che rimuovere l'entità del bottino durante l'evento ne impedisca la comparsa: CraftBukkit non aggiunge al mondo un oggetto già "morto". Va confermato con la checklist qui sotto.
- L'aspetto in gioco di nome, lore e livelli oltre 10.
- I comandi reali del plugin di casse in uso.

## Checklist per la prova sul server

1. [ ] All'avvio il log mostra `Configurazione caricata` e l'avviso sulla categoria comandi esclusa, senza errori.
2. [ ] `/kitpvpfishing chances` mostra Pesce 84,21% e Armatura 15,79%.
3. [ ] Lancia e ritira l'amo a vuoto, aggancia un mob o un giocatore, lascia l'amo a terra: non arriva nessuna ricompensa e nessun messaggio.
4. [ ] Imposta `debug: true`, pesca più volte e confronta ogni riga `[debug]` con ciò che ricevi.
5. [ ] **Pesce:** arriva un solo pesce (mai spazzatura o tesori) con il messaggio relativo.
6. [ ] **Armatura** (metti temporaneamente `fish` e `commands` a `enabled: false`):
   - [ ] arriva un solo pezzo nell'inventario;
   - [ ] **nessun oggetto vanilla** compare a terra o vola verso di te;
   - [ ] gli incantesimi sono Protezione e Indistruttibilità nell'intervallo configurato;
   - [ ] nome e lore sono corretti.
7. [ ] **Inventario pieno:** il pezzo compare a terra ai tuoi piedi, una sola volta, e ricevi il messaggio "Inventario pieno".
8. [ ] **Key:**
   - [ ] sostituisci il segnaposto con il comando reale del tuo plugin e attiva la ricompensa;
   - [ ] dopo il reload e una cattura della categoria comandi, ricevi la key una sola volta;
   - [ ] nessun oggetto vanilla compare.
9. [ ] Scrivi un comando inesistente: compare l'avviso "comando non riconosciuto" nel log.
10. [ ] **`worlds`:**
    - [ ] in un mondo non elencato la pesca è vanilla;
    - [ ] con `enabled: false` la pesca è vanilla.
11. [ ] Inserisci un errore (es. `weight: abc`) e usa `/kitpvpfishing reload`: compare l'errore con il percorso e la pesca continua con la configurazione precedente.
12. [ ] Un giocatore non OP che usa `/kitpvpfishing reload` riceve il messaggio di permesso negato.
13. [ ] Livelli oltre 10 (es. `max: 15`): il pezzo funziona. Verifica come appare nel tooltip, con e senza `hide-enchants`.
14. [ ] Se usi plugin di protezione delle aree che bloccano la pesca, nelle aree protette non arriva nessuna ricompensa.
