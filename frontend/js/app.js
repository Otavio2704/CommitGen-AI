import { generateCommitMessages, fetchRateLimit } from './api.js';
import { getHistory, saveToHistory, clearHistory, formatDate } from './history.js';

// ===== DOM Elements =====
const diffInput        = document.getElementById('diff-input');
const languageSelect   = document.getElementById('language-select');
const styleSelect      = document.getElementById('style-select');
const quantitySelect   = document.getElementById('quantity-select');
const generateBtn      = document.getElementById('generate-btn');
const loadingSection   = document.getElementById('loading-section');
const resultsSection   = document.getElementById('results-section');
const resultsMeta      = document.getElementById('results-meta');
const suggestionsContainer = document.getElementById('suggestions-container');
const historySection   = document.getElementById('history-section');
const historyContainer = document.getElementById('history-container');
const clearHistoryBtn  = document.getElementById('clear-history-btn');
const rateLimitBadge   = document.getElementById('rate-limit-badge');

// ===== Rate Limit State (server-driven) =====
let rateLimitState = { remaining: null, limit: null, resetAt: null };
let countdownInterval = null;

// ===== Event Listeners =====
generateBtn.addEventListener('click', handleGenerate);
clearHistoryBtn.addEventListener('click', handleClearHistory);

// ===== Init =====
renderHistory();
loadRateLimitFromServer();

// ===== Helpers =====

/** Generate a short fake commit hash for visual flair */
function fakeHash() {
    return [...Array(7)].map(() => Math.floor(Math.random() * 16).toString(16)).join('');
}

/** Map a commit type to a badge CSS class */
function badgeClass(type) {
    if (!type) return 'badge--default';
    const t = type.toLowerCase();
    if (t === 'feat' || t === 'feature') return 'badge--feat';
    if (t === 'fix' || t === 'bugfix') return 'badge--fix';
    if (t === 'refactor') return 'badge--refactor';
    if (t === 'docs') return 'badge--docs';
    if (t === 'style') return 'badge--style';
    if (t === 'chore') return 'badge--chore';
    if (t === 'test' || t === 'tests') return 'badge--test';
    return 'badge--default';
}

// ===== Handlers =====

async function handleGenerate() {
    const diff = diffInput.value.trim();

    if (!diff) {
        diffInput.focus();
        diffInput.classList.add('error');
        setTimeout(() => { diffInput.classList.remove('error'); }, 2000);
        return;
    }

    const style    = styleSelect.value;
    const language = languageSelect.value;
    const quantity = parseInt(quantitySelect.value, 10);

    setLoading(true);

    try {
        const data = await generateCommitMessages(diff, style, language, quantity);
        renderResults(data);
        updateRateLimitFromResponse(data._rateLimit);

        // Salvar no histórico
        const messages = data.suggestions.map(s => s.message);
        saveToHistory(messages, style, language);
        renderHistory();

    } catch (err) {
        if (err.status === 429) {
            renderError('⏳ ' + err.message, true);
            if (err.rateLimit) {
                updateRateLimitFromResponse(err.rateLimit);
            }
        } else {
            renderError(err.message, false);
        }
    } finally {
        setLoading(false);
    }
}

function handleClearHistory() {
    clearHistory();
    renderHistory();
}

// ===== Render Functions =====

function setLoading(isLoading) {
    generateBtn.disabled = isLoading;
    loadingSection.classList.toggle('hidden', !isLoading);
    if (isLoading) {
        resultsSection.classList.add('hidden');
    }
}

function renderResults(data) {
    suggestionsContainer.innerHTML = '';

    data.suggestions.forEach((suggestion, index) => {
        const card = document.createElement('div');
        card.className = 'suggestion-card';
        card.style.animationDelay = `${index * 0.08}s`;

        const content = document.createElement('div');
        content.className = 'suggestion-content';

        // Commit hash line
        const hashLine = document.createElement('div');
        hashLine.className = 'suggestion-hash';
        hashLine.innerHTML = `<svg viewBox="0 0 16 16" width="12" height="12" fill="currentColor"><path d="M11.93 8.5a4.002 4.002 0 0 1-7.86 0H.75a.75.75 0 0 1 0-1.5h3.32a4.002 4.002 0 0 1 7.86 0h3.32a.75.75 0 0 1 0 1.5Zm-1.43-.75a2.5 2.5 0 1 0-5 0 2.5 2.5 0 0 0 5 0Z"/></svg> ${fakeHash()}`;
        content.appendChild(hashLine);

        // Message line
        const messageEl = document.createElement('div');
        messageEl.className = 'suggestion-message';

        if (suggestion.type) {
            const badge = document.createElement('span');
            badge.className = `suggestion-type-badge ${badgeClass(suggestion.type)}`;
            badge.textContent = suggestion.type;
            messageEl.appendChild(badge);
        }

        messageEl.appendChild(document.createTextNode(suggestion.message));
        content.appendChild(messageEl);
        card.appendChild(content);

        // Copy button
        const actions = document.createElement('div');
        actions.className = 'suggestion-actions';

        const copyBtn = document.createElement('button');
        copyBtn.className = 'btn--copy';
        copyBtn.innerHTML = `<svg viewBox="0 0 16 16" width="12" height="12" fill="currentColor"><path d="M0 6.75C0 5.784.784 5 1.75 5h1.5a.75.75 0 0 1 0 1.5h-1.5a.25.25 0 0 0-.25.25v7.5c0 .138.112.25.25.25h7.5a.25.25 0 0 0 .25-.25v-1.5a.75.75 0 0 1 1.5 0v1.5A1.75 1.75 0 0 1 9.25 16h-7.5A1.75 1.75 0 0 1 0 14.25ZM5 1.75C5 .784 5.784 0 6.75 0h7.5C15.216 0 16 .784 16 1.75v7.5A1.75 1.75 0 0 1 14.25 11h-7.5A1.75 1.75 0 0 1 5 9.25Zm1.75-.25a.25.25 0 0 0-.25.25v7.5c0 .138.112.25.25.25h7.5a.25.25 0 0 0 .25-.25v-7.5a.25.25 0 0 0-.25-.25Z"/></svg> Copiar`;
        copyBtn.addEventListener('click', () => copyToClipboard(suggestion.message, copyBtn));
        actions.appendChild(copyBtn);
        card.appendChild(actions);

        suggestionsContainer.appendChild(card);
    });

    resultsMeta.textContent = `${data.model} · ${data.processingTimeMs}ms`;
    resultsSection.classList.remove('hidden');
    resultsSection.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

function renderError(message, isRateLimit = false) {
    const color = isRateLimit ? 'var(--color-yellow)' : 'var(--color-red)';
    suggestionsContainer.innerHTML = `
        <div style="color: ${color}; padding: 1rem; text-align: center; font-family: var(--font-mono); font-size: 0.85rem;">
            ${isRateLimit ? '' : '✕ '}${message}
        </div>
    `;
    resultsMeta.textContent = '';
    resultsSection.classList.remove('hidden');
}

function renderHistory() {
    const history = getHistory();

    if (history.length === 0) {
        historySection.classList.add('hidden');
        return;
    }

    historySection.classList.remove('hidden');
    historyContainer.innerHTML = '';

    history.forEach(entry => {
        entry.messages.forEach(message => {
            const item = document.createElement('div');
            item.className = 'history-item';
            item.title = 'Clique para copiar para o campo de entrada';

            const text = document.createElement('span');
            text.className = 'history-item__text';
            text.textContent = message;

            const time = document.createElement('span');
            time.className = 'history-item__time';
            time.textContent = formatDate(entry.timestamp);

            item.appendChild(text);
            item.appendChild(time);

            item.addEventListener('click', () => {
                diffInput.value = message;
                diffInput.focus();
                window.scrollTo({ top: 0, behavior: 'smooth' });
            });

            historyContainer.appendChild(item);
        });
    });
}

function updateRateLimitFromResponse(rateLimit) {
    if (!rateLimit) return;
    rateLimitState = { ...rateLimitState, ...rateLimit };
    renderRateLimitBadge();
}

async function loadRateLimitFromServer() {
    try {
        const data = await fetchRateLimit();
        rateLimitState = {
            remaining: data.remaining,
            limit: data.limit,
            resetAt: data.resetAt,
        };
        renderRateLimitBadge();
    } catch {
        // silencioso — badge fica oculto até o primeiro request
    }
}

function renderRateLimitBadge() {
    if (!rateLimitBadge || rateLimitState.remaining === null) return;

    const { remaining, limit, resetAt } = rateLimitState;

    // Limpa countdown anterior
    if (countdownInterval) {
        clearInterval(countdownInterval);
        countdownInterval = null;
    }

    if (remaining <= 0 && resetAt) {
        // Sem tokens — mostra countdown
        startCountdown(resetAt);
    } else {
        rateLimitBadge.textContent = `⬤ ${remaining}/${limit} requests restantes`;
        rateLimitBadge.style.color = remaining <= 2
            ? 'var(--color-red)'
            : remaining <= 5
                ? 'var(--color-yellow)'
                : 'var(--color-green)';
    }
    rateLimitBadge.classList.remove('hidden');
}

function startCountdown(resetAtEpoch) {
    const update = () => {
        const now = Math.floor(Date.now() / 1000);
        const diff = resetAtEpoch - now;

        if (diff <= 0) {
            clearInterval(countdownInterval);
            countdownInterval = null;
            // recarrega status do servidor
            loadRateLimitFromServer();
            return;
        }

        const minutes = Math.floor(diff / 60);
        const seconds = diff % 60;
        rateLimitBadge.textContent = `⏳ Limite atingido — tokens em ${minutes}m ${String(seconds).padStart(2, '0')}s`;
        rateLimitBadge.style.color = 'var(--color-yellow)';
    };

    update();
    countdownInterval = setInterval(update, 1000);
}

async function copyToClipboard(text, btn) {
    const originalHTML = btn.innerHTML;
    try {
        await navigator.clipboard.writeText(text);
        btn.innerHTML = `<svg viewBox="0 0 16 16" width="12" height="12" fill="currentColor"><path d="M13.78 4.22a.75.75 0 0 1 0 1.06l-7.25 7.25a.75.75 0 0 1-1.06 0L2.22 9.28a.751.751 0 0 1 .018-1.042.751.751 0 0 1 1.042-.018L6 10.94l6.72-6.72a.75.75 0 0 1 1.06 0Z"/></svg> Copiado!`;
        btn.classList.add('copied');
        setTimeout(() => {
            btn.innerHTML = originalHTML;
            btn.classList.remove('copied');
        }, 2000);
    } catch {
        const ta = document.createElement('textarea');
        ta.value = text;
        ta.style.position = 'fixed';
        ta.style.opacity = '0';
        document.body.appendChild(ta);
        ta.select();
        document.execCommand('copy');
        document.body.removeChild(ta);
        btn.innerHTML = `<svg viewBox="0 0 16 16" width="12" height="12" fill="currentColor"><path d="M13.78 4.22a.75.75 0 0 1 0 1.06l-7.25 7.25a.75.75 0 0 1-1.06 0L2.22 9.28a.751.751 0 0 1 .018-1.042.751.751 0 0 1 1.042-.018L6 10.94l6.72-6.72a.75.75 0 0 1 1.06 0Z"/></svg> Copiado!`;
        btn.classList.add('copied');
        setTimeout(() => {
            btn.innerHTML = originalHTML;
            btn.classList.remove('copied');
        }, 2000);
    }
}
