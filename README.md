<div align="center">

# ✍️ CommitGen - AI-powered commit messages

**Gerador inteligente de mensagens de commit alimentado por IA**

Cole seu `git diff`, escolha o estilo e receba sugestões prontas para usar.

![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-6DB33F?logo=springboot&logoColor=white)
![GroqCloud](https://img.shields.io/badge/GroqCloud-LLaMA%203.3%2070B-F55036?logo=meta&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white)

Link oficial: https://otavio2704.github.io/CommitGen-AI/

</div>

## ⚠️ Aviso Importante

Devido a um incidente recente envolvendo a plataforma de deploy, a chave de API utilizada para integração com o serviço de IA foi revogada por segurança.

Como resultado, a funcionalidade de geração automática de commits está temporariamente indisponível.

Em breve restabelecerei o serviço com segurança.

Obrigado pela atenção;)

---

## 📋 Índice

- [Visão Geral](#-visão-geral)
- [Funcionalidades](#-funcionalidades)
- [Arquitetura](#-arquitetura)
- [Tech Stack](#-tech-stack)
- [Pré-requisitos](#-pré-requisitos)
- [Como Rodar](#-como-rodar)
- [Documentação da API](#-documentação-da-api)
- [Estrutura do Projeto](#-estrutura-do-projeto)
- [Configuração](#%EF%B8%8F-configuração)
- [Desenvolvimento Local](#-desenvolvimento-local)

---

## 🎯 Visão Geral

O **Commit Message Generator** é uma aplicação web que utiliza o modelo **LLaMA 3.3 70B** via **GroqCloud** para gerar mensagens de commit semânticas e de alta qualidade. Basta colar a saída do `git diff` (ou descrever as mudanças em texto livre) e a IA analisa as alterações, retornando sugestões que seguem boas práticas como Conventional Commits e Gitmoji.

---

## ✨ Funcionalidades

| Recurso | Descrição |
|---|---|
| 🧠 **Geração por IA** | Analisa diffs reais ou descrições livres e gera mensagens contextualmente precisas |
| 🌐 **Multilíngue** | Mensagens em **Inglês** ou **Português (BR)** |
| 🎨 **3 estilos** | **Conventional Commits** · **Emoji (Gitmoji)** · **Simple** |
| 🔢 **Quantidade configurável** | Escolha de **1 a 5** sugestões por requisição |
| 📋 **Copiar com 1 clique** | Cada sugestão possui botão de cópia |
| 📜 **Histórico local** | Armazena as últimas **20 gerações** no `localStorage` |
| 🛡️ **Rate limiting** | 10 requisições/hora por IP (Bucket4j + Caffeine) |
| 🔒 **Proteção anti-injection** | Sanitização do diff para mitigar prompt injection |

---

## 🏗️ Arquitetura

```
┌─────────────┐        ┌─────────────────┐        ┌──────────────┐
│   Browser    │──:80──▶│  Nginx (Front)  │──/api──▶│  Spring Boot │──▶  GroqCloud API
│  HTML/JS/CSS │        │  Static Files   │         │  Java 21     │    (LLaMA 3.3 70B)
└─────────────┘        └─────────────────┘        └──────────────┘
                        commitgen-web               commitgen-api
```

O **Nginx** serve os arquivos estáticos do frontend e atua como **proxy reverso** encaminhando chamadas `/api/*` ao backend Spring Boot. O backend se comunica com a API da GroqCloud para gerar as mensagens.

---

## 🧰 Tech Stack

### Backend
| Tecnologia | Uso |
|---|---|
| **Java 21** | Linguagem principal |
| **Spring Boot 3.4** | Framework web + REST |
| **Spring RestClient** | Comunicação com a API da GroqCloud |
| **Bean Validation** | Validação dos DTOs de entrada |
| **Bucket4j** | Rate limiting por IP (token bucket) |
| **Caffeine** | Cache em memória com TTL para buckets |
| **Lombok** | Redução de boilerplate |

### Frontend
| Tecnologia | Uso |
|---|---|
| **HTML / CSS / JS** | Interface (vanilla, sem frameworks) |
| **Nginx 1.25** | Servidor estático + proxy reverso |

### Infraestrutura
| Tecnologia | Uso |
|---|---|
| **Docker** | Containers com multi-stage build |
| **Docker Compose** | Orquestração dos 2 serviços |

---

## 📦 Pré-requisitos

- [Docker](https://docs.docker.com/get-docker/) e [Docker Compose](https://docs.docker.com/compose/install/) instalados
- Uma **API Key** da GroqCloud — obtenha gratuitamente em [console.groq.com/keys](https://console.groq.com/keys)

---

## 🚀 Como Rodar

```bash
# 1. Clone o repositório
git clone https://github.com/seu-usuario/commit-message-generator.git
cd commit-message-generator

# 2. Configure a API Key
cp .env.example .env
# Edite o arquivo .env e insira sua GROQ_API_KEY

# 3. Suba os containers
docker compose up --build -d

# 4. Acesse a aplicação
# http://localhost
```

### Comandos úteis

```bash
# Ver logs do backend em tempo real
docker compose logs -f backend

# Verificar saúde da API
curl http://localhost/api/health

# Parar os containers
docker compose down

# Reconstruir após alterações
docker compose up --build -d
```

---

## 📡 Documentação da API

### `POST /api/generate`

Gera sugestões de mensagens de commit.

**Request Body:**

```json
{
  "diff": "diff --git a/src/app.js b/src/app.js\n--- a/src/app.js\n+++ b/src/app.js\n+ function handleAuth() { ... }",
  "language": "en",
  "style": "conventional",
  "quantity": 3
}
```

| Campo | Tipo | Obrigatório | Default | Descrição |
|---|---|---|---|---|
| `diff` | `string` | ✅ | — | Diff do `git diff` ou descrição livre (máx 10.000 chars) |
| `language` | `string` | ❌ | `"en"` | Idioma: `"en"` ou `"pt-br"` |
| `style` | `string` | ❌ | `"conventional"` | Estilo: `"conventional"`, `"emoji"` ou `"simple"` |
| `quantity` | `integer` | ❌ | `3` | Número de sugestões (1–5) |

**Response `200 OK`:**

```json
{
  "suggestions": [
    {
      "message": "feat(auth): add user authentication handler",
      "type": "feat",
      "scope": "auth",
      "description": "add user authentication handler"
    }
  ],
  "model": "llama-3.3-70b-versatile",
  "processingTimeMs": 1247
}
```

**Headers de resposta:**

| Header | Descrição |
|---|---|
| `X-RateLimit-Remaining` | Requisições restantes na janela atual |
| `Retry-After` | Segundos até a próxima janela (apenas em respostas `429`) |

**Erros comuns:**

| Status | Cenário |
|---|---|
| `400` | Campo `diff` vazio ou excede 10.000 caracteres |
| `429` | Rate limit excedido (10 req/hora por IP) |
| `502` | Falha na comunicação com a GroqCloud API |

---

### `GET /api/health`

Verifica se a API está operacional.

**Response `200 OK`:**

```json
{
  "status": "UP",
  "service": "commit-message-generator"
}
```

---

## 📁 Estrutura do Projeto

```
commit-message-generator/
├── docker-compose.yml           # Orquestração dos containers
├── .env.example                 # Template de variáveis de ambiente
├── .gitignore                   # Arquivos ignorados pelo Git
│
├── backend/                     # API Spring Boot
│   ├── Dockerfile               # Multi-stage build (Maven → JRE Alpine)
│   ├── pom.xml
│   └── src/main/java/com/commitgen/
│       ├── CommitGenApplication.java
│       ├── config/
│       │   ├── RestClientConfig.java    # Configuração do RestClient (GroqCloud)
│       │   └── WebConfig.java           # CORS
│       ├── controller/
│       │   └── CommitController.java    # Endpoints REST
│       ├── dto/
│       │   ├── CommitRequest.java       # DTO de entrada (validado)
│       │   └── CommitResponse.java      # DTO de saída
│       ├── exception/
│       │   ├── GlobalExceptionHandler.java
│       │   ├── GroqApiException.java
│       │   └── ErrorResponse.java
│       └── service/
│           ├── CommitService.java       # Lógica de geração + parsing
│           ├── GroqApiService.java      # Client da API GroqCloud
│           └── RateLimitService.java    # Rate limiting por IP
│
└── frontend/                    # Interface web
    ├── Dockerfile               # Nginx Alpine
    ├── nginx.conf               # Proxy reverso + gzip
    ├── index.html
    ├── assets/                  # Favicon e assets estáticos
    ├── css/
    │   └── style.css
    └── js/
        ├── app.js               # Lógica principal da UI
        ├── api.js               # Chamadas ao backend
        └── history.js           # Gerenciamento do histórico local
```

---

## ⚙️ Configuração

### Variáveis de ambiente

| Variável | Obrigatória | Descrição |
|---|---|---|
| `GROQ_API_KEY` | ✅ | Chave de API da GroqCloud |
| `CORS_ALLOWED_ORIGINS` | ❌ | Origens permitidas para CORS (default: `*`) |

### Propriedades do backend (`application.yml`)

| Propriedade | Default | Descrição |
|---|---|---|
| `groq.model` | `llama-3.3-70b-versatile` | Modelo LLM utilizado |
| `groq.base-url` | `https://api.groq.com/openai/v1` | Base URL da API GroqCloud |
| `cors.allowed-origins` | `*` | Origens permitidas para CORS |

### Rate Limiting

- **10 requisições por hora** por IP
- Implementado com **Bucket4j** (Token Bucket) + cache **Caffeine** com TTL de 1h
- Máximo de 10.000 buckets simultâneos em memória
- Header `Retry-After` retornado em respostas `429`

---

## 💻 Desenvolvimento Local

Para rodar o backend fora do Docker (útil para debug):

```bash
# Pré-requisitos: Java 21 + Maven

cd backend

# Defina a API key
export GROQ_API_KEY=gsk_sua_chave_aqui

# Rode o backend
mvn spring-boot:run

# O backend estará acessível em http://localhost:8080
```

Para o frontend, basta servir os arquivos estáticos (ex.: Live Server no VS Code) e apontar as chamadas `/api` para `localhost:8080`, ou simplesmente usar o Docker Compose.

### Testes

O projeto conta com testes unitários para controller, services e exception handler:

```bash
cd backend
mvn test
```

---

<div align="center">

**Feito por Otavio2007**

</div>
