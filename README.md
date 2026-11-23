# Gestor de Coleções de Banda Desenhada

Aplicação Android nativa (Kotlin) para colecionadores de banda desenhada registarem os
exemplares que possuem — organizados por pastas/coleções — com editora, número, grau de
conservação, data de aquisição e certificação opcional. Permite consultar a coleção com
pesquisa, filtros, ordenação e estatísticas simples. Funciona totalmente **offline**: todos
os dados ficam no dispositivo.

## Como abrir, compilar e testar

### Windows (Android Studio)

1. Instalar o **Android Studio** (com Android SDK e um JDK 17 — o próprio Android Studio inclui um).
2. Clonar o repositório (GitHub Desktop, ou PowerShell):
   ```powershell
   git clone https://github.com/Francisco-Branco-2/Gestor-BD.git
   cd Gestor-BD
   ```
3. Abrir a pasta do projeto no Android Studio: *File → Open…* e escolher a pasta `Gestor-BD`
   (a que contém `settings.gradle.kts` e `gradlew.bat`).
4. Aguardar a sincronização do Gradle (*Sync Project with Gradle Files*). O Android Studio cria
   automaticamente o `local.properties` com o caminho do SDK no teu PC — **não commits esse ficheiro**.
5. Ligar um telemóvel com depuração USB ou iniciar um emulador (**API 29+** / Android 10+).
6. Carregar em **Run** (▶) na configuração **app**.

Na linha de comandos (PowerShell / CMD), a partir da pasta do projeto:

```bat
gradlew.bat assembleDebug
gradlew.bat testDebugUnitTest
gradlew.bat connectedDebugAndroidTest
```

### Linux / macOS

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
```

Configuração: `minSdk 29`, `targetSdk 35`, `compileSdk 35`, AGP 9.4.1, Gradle 9.6, Java 17.

**Nota Windows:** usa sempre `gradlew.bat` (não `./gradlew`). Não abras só a pasta `app/` —
abre a raiz do projeto. Se o Sync falhar, *File → Invalidate Caches… → Invalidate and Restart*.

**Para testar à mão:** criar uma coleção, adicionar exemplares de editoras e graus diferentes,
experimentar pesquisa/filtros/ordenação, abrir as Estatísticas, exportar CSV, editar e eliminar
um exemplar (e a coleção), mudar o tema e fechar/reabrir a aplicação para confirmar que os dados
se mantêm. Nos formulários, sair com alterações por gravar deve pedir confirmação.

## Funcionalidades

**Obrigatórias (MVP)**
- Pastas/coleções (nome, autores, capa) que agrupam exemplares.
- Adicionar, listar, ver detalhe, editar e eliminar exemplares (eliminação com confirmação).
- Campos do exemplar: título, editora, número/edição, grau (1.0–10.0), data de aquisição,
  certificação (opcional) e notas.
- Validação com mensagens de erro junto a cada campo.
- Persistência local com Room (SQLite); os dados mantêm-se depois de fechar a aplicação.
- Todos os textos visíveis estão em `res/values/strings.xml`.

**Adicionais**
- **Pesquisa**, **filtros** (editora/grau) e **ordenação** (título, grau, data) por pasta.
- **Capa** da pasta e do exemplar (Photo Picker do sistema, sem permissões de armazenamento).
- **Estatísticas** por pasta: total, média, por editora e por grau.
- **Exportação CSV** da coleção (partilha via FileProvider).
- Confirmação ao sair de formulários com alterações por gravar; empty states claros.
- Acessibilidade básica (`contentDescription`, alvos de toque ≥ 48 dp).
- **Modo claro/escuro**, ecrãs multi-largura, testes unitários e instrumentados.

## Interface

Interface minimalista em Material 3: barra superior, listas simples sem cartões, uma única cor
de destaque e só os controlos necessários (FAB +, chips de filtro/ordenação, Editar/Eliminar no
detalhe, Guardar no formulário).

## Armazenamento e estrutura dos dados

Tecnologia: **Room 2.8** sobre SQLite, base de dados `gestor_bd.db` (esquema exportado em
`app/schemas/`). As capas ficam em `filesDir/covers/` e na base de dados guarda-se apenas o nome
do ficheiro. A preferência de tema é a única informação em SharedPreferences.

Tabela `bd_folders` (pastas/coleções):

| Coluna | Tipo | Notas |
|---|---|---|
| `id` | INTEGER PK | autoincremento |
| `name` | TEXT | nome da coleção |
| `authors` | TEXT | autores |
| `cover_file_name` | TEXT | capa opcional |

Tabela `comics` (relação N:1 com pasta; `ON DELETE CASCADE`):

| Coluna | Tipo | Notas |
|---|---|---|
| `id` | INTEGER PK | autoincremento |
| `folder_id` | INTEGER FK | pasta dona do exemplar |
| `title` | TEXT | obrigatório, indexado |
| `publisher` | TEXT | obrigatório, indexado |
| `issue_number` | TEXT | obrigatório |
| `grade` | REAL | 1.0–10.0 |
| `acquisition_date` | INTEGER | epoch-day |
| `notes` | TEXT | opcional |
| `certification` | TEXT | opcional (ex.: CGC 9.8) |
| `cover_file_name` | TEXT | capa opcional |

## Organização do código

```
pt.ubi.di.pdm.gestorbd
├── GestorBdApp
├── data
│   ├── BdFolder, FolderDao, Comic, ComicDao, AppDatabase
│   ├── ComicRepository, CoverImageStorage, ComicFilters, ComicCsvExporter
├── domain
│   ├── ComicValidator, FolderValidator
└── ui
    ├── folders   MainActivity, FolderEditActivity
    ├── list      ComicListActivity (exemplares da pasta)
    ├── detail, edit, stats, common (tema, capas, edge-to-edge, discard)
```

## Decisões de implementação

- **Arquitetura MVVM + Repository**: as Activities só tratam da interface; os ViewModels guardam
  o estado e sobrevivem à rotação; o Repository esconde o Room e os ficheiros.
- **Room com `Flow`**: a lista, o detalhe e as estatísticas atualizam-se automaticamente quando os
  dados mudam. As escritas são `suspend` (fora da thread principal).
- **Filtros e estatísticas em SQL** (`WHERE` com parâmetros opcionais, `GROUP BY`, `COUNT`, `AVG`);
  a **ordenação** é aplicada em memória no Repository para manter uma única query de pesquisa.
- **Datas como `LocalDate`** convertidas para epoch-day, evitando problemas de fuso horário.
- **Estado do formulário em `SavedStateHandle`** (data e capa), para não se perder ao rodar o ecrã
  nem quando o sistema termina o processo.
- **Capas**: reduzidas para 1200 px e comprimidas em JPEG; a capa antiga só é apagada depois de a
  alteração estar gravada; capas escolhidas e não gravadas são apagadas ao sair.
- **Sem bibliotecas de imagens ou gráficos**: `ImageDecoder`/`BitmapFactory` + cache `LruCache`;
  estatísticas com barras `LinearProgressIndicator`.
- **Erros tratados** sem fechar a app: falhas na imagem ou na base de dados mostram uma mensagem.

## Bibliotecas utilizadas

| Biblioteca | Justificação |
|---|---|
| AndroidX Core, AppCompat, Activity, RecyclerView | base da interface clássica com Views |
| Material Components 3 | chips, campos, slider, datas, tema claro/escuro |
| Lifecycle (ViewModel, runtime) | ViewModels e recolha de `Flow` com ciclo de vida |
| Room (runtime, ktx, compiler via KSP) | armazenamento local em SQLite |
| Kotlin Coroutines | operações assíncronas |
| JUnit, AndroidX Test | testes |

## Dificuldades

- Modelar pastas e exemplares com FK e `ON DELETE CASCADE`, e apagar também as capas dos
  exemplares quando a pasta é removida.
- Combinar filtros opcionais numa única consulta Room (`:param IS NULL OR …`).
- Manter o formulário consistente ao rodar o ecrã (incluindo o seletor de datas, cujo listener
  tem de ser reassociado).
- Gerir o ciclo de vida dos ficheiros das capas para não deixar ficheiros órfãos.
- Adaptar a interface ao modo edge-to-edge (Android 15) e a insets incompletos em alguns
  fabricantes (FAB tapado pelos gestos).
- Detetar alterações por gravar de forma fiável (texto, grau, data e capa) sem marcar o
  formulário como «dirty» só por o abrir.

## Utilização de ferramentas de IA

Parte do código e desta documentação foi gerada com assistentes de IA (**Cursor** / Claude), a
partir dos requisitos do enunciado e da visão/âmbito definidos pelo estudante. O código foi
revisto, compilado e testado; o estudante deve ser capaz de explicar cada parte.
