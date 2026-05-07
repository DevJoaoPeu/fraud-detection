# Fraud Detection

Sistema de detecção de fraude em tempo real baseado em busca vetorial semântica. Cada transação é convertida em um embedding pelo Google Gemini, armazenado no PostgreSQL com pgvector, e classificada via KNN comparando com transações históricas rotuladas.

---

## Arquitetura

```
┌─────────────────────────────────────────────────────────────────┐
│                          HTTP Client                            │
└────────────────────────────┬────────────────────────────────────┘
                             │ POST /api/v1/transactions/analyze
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    TransactionController                        │
│                  (Validação via Jakarta Bean)                   │
└────────────────────────────┬────────────────────────────────────┘
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

**Cold start:** quando não há transações históricas suficientes, o score padrão é `0.5` (FLAGGED para revisão manual).

---

## Estrutura do Projeto

```
src/
├── main/
│   ├── java/com/frauddetection/
│   │   ├── FraudDetectionApplication.java
│   │   ├── config/
│   │   │   ├── EmbeddingProperties.java   # app.embedding.*
│   │   │   ├── FraudProperties.java       # app.fraud.*
│   │   │   └── SecurityConfig.java
│   │   ├── controller/
│   │   │   ├── TransactionController.java
│   │   │   └── GlobalExceptionHandler.java
│   │   ├── dto/
│   │   │   ├── TransactionRequest.java
│   │   │   └── TransactionResponse.java
│   │   ├── domain/
│   │   │   ├── entity/
│   │   │   │   ├── TransactionEntity.java
│   │   │   │   └── FraudAuditLogEntity.java
│   │   │   ├── enums/
│   │   │   │   └── FraudDecision.java     # APPROVED | FLAGGED | BLOCKED
│   │   │   └── repository/
│   │   │       ├── TransactionRepository.java
│   │   │       └── FraudAuditLogRepository.java
│   │   ├── service/
│   │   │   ├── TransactionService.java    # orquestração principal
│   │   │   ├── FraudScoringService.java   # KNN scoring
│   │   │   ├── EmbeddingService.java      # integração Gemini
│   │   │   ├── DuplicateTransactionException.java
│   │   │   └── EmbeddingException.java
│   │   └── infrastructure/
│   │       └── persistence/type/
│   │           └── VectorUserType.java    # float[] ↔ vector(768)
│   └── resources/
│       ├── application.yml
│       └── db/migration/
│           ├── V1__init.sql               # schema inicial
│           ├── V2__fix_char_to_varchar.sql
│           └── V3__update_embedding_dimension.sql
└── test/
    └── java/com/frauddetection/          # Testcontainers (a implementar)
```

---

## API

### `POST /api/v1/transactions/analyze`

Analisa uma transação e retorna a decisão de fraude.

**Request:**
```json
{
  "transactionId": "txn-abc-123",
  "amount": 1500.00,
  "merchantId": "merchant-456",
  "merchantCategory": "ELECTRONICS",
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

```bash
export DB_POSTGRES_URL=jdbc:postgresql://localhost:5432/frauddetection
export DB_USERNAME=fraud
export DB_PASSWORD=fraud
export GEMINI_API_KEY=sua_chave_aqui
export GEMINI_BASE_URL=https://generativelanguage.googleapis.com/v1beta
```

### 3. Executar a aplicação

```bash
./mvnw spring-boot:run
```

As migrations do Flyway rodam automaticamente na inicialização.

### 4. Testar

```bash
curl -X POST http://localhost:8080/api/v1/transactions/analyze \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "txn-001",
    "amount": 250.00,
    "merchantId": "merchant-001",
    "merchantCategory": "FOOD",
    "countryCode": "BR",
    "currencyCode": "BRL"
  }'
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
| V3 | Atualiza dimensão do vetor de `768` para `3072` (revertível via nova migration) |

> **Atenção:** migrations são imutáveis após aplicadas. Toda alteração de schema requer uma nova versão.

---

## Decisões de Design

- **Embeddings como `float[]`:** tipo primitivo direto, sem wrapper, mapeado por `VectorUserType` (Hibernate 6 `UserType<float[]>`) para evitar dependência do tipo `PGvector` nas entidades de domínio.
- **Audit log obrigatório:** toda decisão é registrada em `fraud_audit_log` com o modelo usado, permitindo rastrear mudanças de comportamento ao atualizar modelos de embedding.
- **Cold start com score neutro:** novas instalações sem histórico retornam `0.5` (FLAGGED), priorizando revisão manual sobre falsos negativos.
- **`outputDimensionality=768`:** o modelo `gemini-embedding-001` nativamente gera 3072 dimensões; o parâmetro trunca para 768 via Matryoshka Representation Learning, mantendo compatibilidade com o schema sem perda significativa de qualidade semântica.
