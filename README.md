# Fraud Detection

Sistema de detecção de fraude em tempo real baseado em busca vetorial semântica. Cada transação é convertida em um embedding pelo Google Gemini, armazenado no PostgreSQL com pgvector, e classificada via KNN comparando com transações históricas rotuladas.

---

## Arquitetura

```
┌─────────────────────────────────────────────────────────────────┐
│                          HTTP Client                            │
└────────────────────────────┬────────────────────────────────────┘
                             │ POST /api/v1/transactions/analyze
                             │ POST /api/v1/transactions/ingest
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    TransactionController                        │
│                  (Validação via Jakarta Bean)                   │
└──────────────┬──────────────────────────────┬───────────────────┘
               │ /analyze                     │ /ingest
               ▼                              ▼
┌──────────────────────┐       ┌──────────────────────────────────┐
│  TransactionService  │◄──────│      TransactionBatchService     │
│  (unidade única)     │       │  Itera o lote, captura erros     │
└──────────────────────┘       │  por item sem abortar o batch    │
               │               └──────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                     TransactionService                          │
│  1. Verifica duplicidade (transactionId único)                  │
│  2. Gera embedding via EmbeddingService                         │
│  3. Calcula fraud score via FraudScoringService                 │
│  4. Persiste TransactionEntity + FraudAuditLogEntity            │
│  5. Retorna TransactionResponse                                 │
└──────────┬────────────────────────┬────────────────────────────┘
           │                        │
           ▼                        ▼
┌──────────────────┐    ┌───────────────────────────┐
│ EmbeddingService │    │    FraudScoringService     │
│                  │    │                            │
│ Serializa campos │    │ Busca K vizinhos mais       │
│ da transação em  │    │ próximos no pgvector        │
│ texto estruturado│    │ (cosine similarity)         │
│                  │    │                            │
│ POST Gemini API  │    │ Weighted KNN:              │
│ outputDimension  │    │ BLOCKED  → peso 1.0        │
│ ality=768        │    │ FLAGGED  → peso 0.5        │
│                  │    │ APPROVED → peso 0.0        │
│ → float[768]     │    │                            │
└────────┬─────────┘    │ score ≥ 0.9 → BLOCKED     │
         │              │ score ≥ 0.7 → FLAGGED      │
         │              │ score < 0.7 → APPROVED     │
         │              └────────────┬───────────────┘
         │                           │
         ▼                           ▼
┌─────────────────────────────────────────────────────────────────┐
│                         PostgreSQL                              │
│                                                                 │
│  ┌─────────────────────┐     ┌──────────────────────────────┐  │
│  │    transactions      │     │      fraud_audit_log         │  │
│  │─────────────────────│     │──────────────────────────────│  │
│  │ id (UUID PK)         │◄────│ transaction_id (FK)          │  │
│  │ transaction_id       │     │ fraud_score (DECIMAL 4,3)    │  │
│  │ amount               │     │ decision                     │  │
│  │ merchant_id          │     │ reasoning                    │  │
│  │ merchant_category    │     │ model_version                │  │
│  │ country_code         │     │ created_at                   │  │
│  │ currency_code        │     └──────────────────────────────┘  │
│  │ fraud_score          │                                       │
│  │ decision             │  Índices:                             │
│  │ embedding vector(768)│  • HNSW (embedding, cosine)           │
│  │ created_at           │  • B-tree (merchant_id, decision,     │
│  │ processed_at         │    created_at, transaction_id)        │
│  └─────────────────────┘                                       │
└─────────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Google Gemini API                            │
│         models/gemini-embedding-001 (768 dims)                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 17 |
| Framework | Spring Boot 3.3.4 |
| Banco de dados | PostgreSQL 16 + pgvector |
| ORM | Hibernate 6.5 / Spring Data JPA |
| Migrations | Flyway |
| Embeddings | Google Gemini (`gemini-embedding-001`) |
| Segurança | Spring Security (stateless) |
| Infra local | Docker / Docker Compose |
| Testes | JUnit 5 + Testcontainers |

---

## Algoritmo de Scoring (KNN Vetorial)

O score de fraude é calculado com base na similaridade cosseno entre o embedding da transação atual e os K vizinhos mais próximos já rotulados no banco.

**Pesos por decisão histórica:**
- `BLOCKED` → 1.0
- `FLAGGED` → 0.5
- `APPROVED` → 0.0

**Fórmula:**
```
score = Σ(similaridade_i × peso_decisão_i) / Σ(similaridade_i)
```

**Limiares (configuráveis):**
```
score ≥ 0.9  →  BLOCKED
score ≥ 0.7  →  FLAGGED
score < 0.7  →  APPROVED
```

**Cold start:** quando não há transações históricas suficientes, o score padrão é `0.5`. Na prática isso não ocorre porque o `DataInitializer` popula o banco automaticamente na primeira subida (veja abaixo).

### O que é comparado

O `EmbeddingService` serializa os campos da transação em texto antes de enviar ao Gemini:

```
"amount:250.00 merchant:merchant-001 category:FOOD country:BR currency:BRL"
```

O Gemini devolve um vetor de 768 números representando o significado semântico dessa combinação. Transações com padrões parecidos (valor, merchant, categoria, país) geram vetores próximos no espaço vetorial. A busca no banco usa distância cosseno (`<=>`) e retorna apenas transações que **já possuem decisão** — os dados históricos rotulados que servem de base para o KNN.

### Muitos registros de fraude no banco não garantem score alto

O que importa é a **similaridade** dos vizinhos encontrados, não a quantidade de fraudes existentes no banco.

**Cenário 1 — 5 vizinhos BLOCKED, mas distantes → APPROVED:**
```
vizinho 1: BLOCKED, similaridade 0.12  →  contribui 0.12
vizinho 2: BLOCKED, similaridade 0.09  →  contribui 0.09
vizinho 3: BLOCKED, similaridade 0.07  →  contribui 0.07
vizinho 4: BLOCKED, similaridade 0.06  →  contribui 0.06
vizinho 5: BLOCKED, similaridade 0.05  →  contribui 0.05

score = (0.12 + 0.09 + 0.07 + 0.06 + 0.05) / (0.12 + 0.09 + 0.07 + 0.06 + 0.05)
score = 0.39 / 0.39 = 1.0 ???
```

> Na prática, se todos os vizinhos são BLOCKED (peso 1.0), o numerador e denominador são iguais e o score seria 1.0. Mas isso só acontece se esses forem os únicos K vizinhos retornados — e com similaridade muito baixa, significa que não há histórico realmente parecido.

**Cenário 2 — 2 BLOCKED muito próximos + 8 APPROVED distantes → BLOCKED:**
```
vizinho 1: BLOCKED,  similaridade 0.97  →  contribui 0.97
vizinho 2: BLOCKED,  similaridade 0.95  →  contribui 0.95
vizinhos 3–10: APPROVED → peso 0.0, contribuem 0.00

score = (0.97 + 0.95) / (0.97 + 0.95 + 0.85 + 0.80 + ... ) ≈ 0.91  →  BLOCKED
```

**Cenário 3 — Mix realista → FLAGGED:**
```
vizinho 1: APPROVED, similaridade 0.92  →  contribui 0.00
vizinho 2: FLAGGED,  similaridade 0.88  →  contribui 0.44
vizinho 3: BLOCKED,  similaridade 0.75  →  contribui 0.75
vizinhos 4–10: APPROVED                →  contribuem 0.00

score = (0.00 + 0.44 + 0.75) / (0.92 + 0.88 + 0.75 + ...) ≈ 0.73  →  FLAGGED
```

O sistema detecta fraude quando as transações históricas **similares** foram bloqueadas — não quando há muitas fraudes no banco em geral.

---

## Estrutura do Projeto

```
fraud-detection/
├── sample-transactions/
│   ├── fraudulent_transaction.json       # exemplo de transação fraudulenta
│   ├── legitimate_transaction.json       # exemplo de transação legítima
│   └── batch_sample.json                 # lote misto para testar o /ingest
├── src/
│   ├── main/
│   │   ├── java/com/frauddetection/
│   │   │   ├── FraudDetectionApplication.java
│   │   │   ├── config/
│   │   │   │   ├── DataInitializer.java       # seed automático no cold start
│   │   │   │   ├── EmbeddingProperties.java   # app.embedding.*
│   │   │   │   ├── FraudProperties.java       # app.fraud.*
│   │   │   │   └── SecurityConfig.java
│   │   │   ├── controller/
│   │   │   │   ├── TransactionController.java
│   │   │   │   └── GlobalExceptionHandler.java
│   │   │   ├── dto/
│   │   │   │   ├── TransactionRequest.java
│   │   │   │   ├── TransactionResponse.java
│   │   │   │   └── TransactionBatchResponse.java
│   │   │   ├── domain/
│   │   │   │   ├── entity/
│   │   │   │   │   ├── TransactionEntity.java
│   │   │   │   │   └── FraudAuditLogEntity.java
│   │   │   │   ├── enums/
│   │   │   │   │   └── FraudDecision.java     # APPROVED | FLAGGED | BLOCKED
│   │   │   │   └── repository/
│   │   │   │       ├── TransactionRepository.java
│   │   │   │       └── FraudAuditLogRepository.java
│   │   │   ├── service/
│   │   │   │   ├── TransactionService.java        # orquestração principal
│   │   │   │   ├── TransactionBatchService.java   # processamento em lote
│   │   │   │   ├── SeedService.java               # persiste seed com decisão explícita
│   │   │   │   ├── FraudScoringService.java       # KNN scoring
│   │   │   │   ├── EmbeddingService.java          # integração Gemini
│   │   │   │   ├── DuplicateTransactionException.java
│   │   │   │   └── EmbeddingException.java
│   │   │   └── infrastructure/
│   │   │       └── persistence/type/
│   │   │           └── VectorUserType.java        # float[] ↔ vector(768)
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/migration/
│   │           ├── V1__init.sql                   # schema inicial
│   │           └── V2__fix_char_to_varchar.sql
│   └── test/
│       └── java/com/frauddetection/
│           └── service/
│               ├── FraudScoringServiceTest.java
│               ├── TransactionServiceTest.java
│               ├── SeedServiceTest.java
│               └── TransactionBatchServiceTest.java
```

---

## API

### `POST /api/v1/transactions/analyze`

Analisa uma transação individual e retorna a decisão de fraude.

**Request:**
```json
{
  "transactionId": "txn-abc-123",
  "amount": 1500.00,
  "merchantId": "merchant-456",
  "merchantCategory": "5732",
  "countryCode": "BR",
  "currencyCode": "BRL"
}
```

**Response `200 OK`:**
```json
{
  "transactionId": "txn-abc-123",
  "amount": 1500.00,
  "fraudScore": 0.823,
  "decision": "FLAGGED",
  "processedAt": "2026-05-06T21:00:00"
}
```

**Respostas de erro:**

| Status | Situação |
|---|---|
| `400` | Campos obrigatórios ausentes ou inválidos |
| `409` | `transactionId` já processado |
| `502` | Falha na chamada à API do Gemini |
| `500` | Erro interno |

---

### `POST /api/v1/transactions/ingest`

Processa um lote de transações de uma vez. Cada item é analisado de forma independente — falhas individuais (ex: duplicata) são capturadas sem abortar o restante do lote.

**Request:**
```json
[
  {
    "transactionId": "txn-001",
    "amount": 45.90,
    "merchantId": "MERCH-SUPERMERCADO-001",
    "merchantCategory": "5411",
    "countryCode": "BR",
    "currencyCode": "BRL"
  },
  {
    "transactionId": "txn-002",
    "amount": 14999.99,
    "merchantId": "MERCH-JEWEL-GH-001",
    "merchantCategory": "5944",
    "countryCode": "GH",
    "currencyCode": "USD"
  }
]
```

**Response `200 OK`:**
```json
{
  "total": 2,
  "approved": 1,
  "flagged": 0,
  "blocked": 1,
  "failed": 0,
  "results": [
    {
      "transactionId": "txn-001",
      "success": true,
      "data": { "fraudScore": 0.05, "decision": "APPROVED", ... },
      "error": null
    },
    {
      "transactionId": "txn-002",
      "success": true,
      "data": { "fraudScore": 0.95, "decision": "BLOCKED", ... },
      "error": null
    }
  ]
}
```

> Os arquivos em `sample-transactions/` já estão no formato correto para uso com ambos os endpoints.

---

## Configuração

### Variáveis de Ambiente

| Variável | Descrição | Exemplo |
|---|---|---|
| `DB_POSTGRES_URL` | JDBC URL do PostgreSQL | `jdbc:postgresql://localhost:5432/frauddetection` |
| `DB_USERNAME` | Usuário do banco | `fraud` |
| `DB_PASSWORD` | Senha do banco | `fraud` |
| `GEMINI_API_KEY` | Chave da API do Google Gemini | `AIzaSy...` |
| `GEMINI_BASE_URL` | URL base da API Gemini | `https://generativelanguage.googleapis.com/v1beta` |

### Parâmetros de Fraude (`application.yml`)

```yaml
app:
  embedding:
    dimension: 768
    model: gemini-embedding-001
  fraud:
    threshold:
      flag: 0.7      # score mínimo para FLAGGED
      block: 0.9     # score mínimo para BLOCKED
    scoring:
      similar-transactions-count: 10   # K do KNN
      cold-start-score: 0.5            # score sem histórico
```

---

## Como Rodar

### Pré-requisitos
- Java 17+
- Docker e Docker Compose
- Chave de API do Google Gemini

### 1. Subir o banco

```bash
docker compose up -d
```

### 2. Configurar variáveis de ambiente

Crie um arquivo `.env` na raiz do projeto (já há um `.env` de exemplo no repositório):

```env
DB_POSTGRES_URL=jdbc:postgresql://localhost:5432/frauddetection
DB_USERNAME=fraud
DB_PASSWORD=fraud
GEMINI_API_KEY=sua_chave_aqui
GEMINI_BASE_URL=https://generativelanguage.googleapis.com/v1beta
```

As variáveis são carregadas automaticamente via `spring-dotenv` — não é necessário exportá-las manualmente.

### 3. Executar a aplicação

```bash
mvn spring-boot:run
```

Na inicialização, o Flyway aplica as migrations e o `DataInitializer` verifica se o banco está vazio. Se estiver, popula automaticamente com transações rotuladas (BLOCKED, FLAGGED, APPROVED) para que o KNN já tenha base de comparação desde a primeira requisição.

### 4. Testar

**Transação única:**
```bash
curl -X POST http://localhost:8080/api/v1/transactions/analyze \
  -H "Content-Type: application/json" \
  -d @sample-transactions/fraudulent_transaction.json
```

**Lote de transações:**
```bash
curl -X POST http://localhost:8080/api/v1/transactions/ingest \
  -H "Content-Type: application/json" \
  -d @sample-transactions/batch_sample.json
```

---

## Banco de Dados

### Índice HNSW

O índice vetorial usa o algoritmo **HNSW** (Hierarchical Navigable Small World) com distância cosseno. É um índice de busca aproximada (ANN) que oferece alta performance em buscas por similaridade em grandes volumes de dados.

```sql
CREATE INDEX idx_transactions_embedding ON transactions
    USING hnsw (embedding vector_cosine_ops);
```

### Histórico de Migrations

| Versão | Descrição |
|---|---|
| V1 | Schema inicial: `transactions` e `fraud_audit_log` com índice HNSW |
| V2 | Corrige tipo das colunas `country_code` e `currency_code` de `CHAR` para `VARCHAR` |

> **Atenção:** migrations são imutáveis após aplicadas. Toda alteração de schema requer uma nova versão.

---

## Decisões de Design

- **Embeddings como `float[]`:** tipo primitivo direto, sem wrapper, mapeado por `VectorUserType` (Hibernate 6 `UserType<float[]>`) para evitar dependência do tipo `PGvector` nas entidades de domínio.
- **Audit log obrigatório:** toda decisão é registrada em `fraud_audit_log` com o modelo usado, permitindo rastrear mudanças de comportamento ao atualizar modelos de embedding.
- **Seed data no lugar de regras:** o cold start é resolvido com dados rotulados reais (gerados via Gemini pelo `DataInitializer`) em vez de regras hard-coded no código. Isso mantém o scoring puramente orientado a dados e facilita ajuste fino sem alterar lógica de negócio.
- **Ingestão em lote tolerante a falhas:** o `/ingest` processa cada transação de forma independente. Erros individuais (duplicata, falha de embedding) são capturados por item — o lote inteiro nunca falha por causa de um único problema.
- **`DataInitializer` idempotente:** verifica `transactionRepository.count() > 0` antes de semear; em restarts normais não executa nenhuma operação no banco.
- **`outputDimensionality=768`:** o modelo `gemini-embedding-001` nativamente gera 3072 dimensões; o parâmetro trunca para 768 via Matryoshka Representation Learning, mantendo compatibilidade com o schema sem perda significativa de qualidade semântica.
