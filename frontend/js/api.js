// Em produção no Render, troque pela URL do seu backend:
// const API_BASE = 'https://SEU-BACKEND.onrender.com/api';
const API_BASE = window.location.hostname === 'localhost'
    ? '/api'
    : 'https://SEU-BACKEND.onrender.com/api';

/**
 * Extrai informações de rate limit dos headers da resposta.
 * @param {Response} response
 * @returns {{ remaining: number|null, limit: number|null, resetAt: number|null }}
 */
function extractRateLimitHeaders(response) {
    const remaining = response.headers.get('X-RateLimit-Remaining');
    const limit = response.headers.get('X-RateLimit-Limit');
    const resetAt = response.headers.get('X-RateLimit-Reset');
    return {
        remaining: remaining !== null ? parseInt(remaining, 10) : null,
        limit: limit !== null ? parseInt(limit, 10) : null,
        resetAt: resetAt !== null ? parseInt(resetAt, 10) : null,
    };
}

/**
 * Gera sugestões de commit message via backend.
 * @param {string} diff
 * @param {string} style
 * @param {string} language
 * @param {number} quantity
 * @returns {Promise<import('./app.js').CommitResponse>}
 */
export async function generateCommitMessages(diff, style, language, quantity = 3) {
    const response = await fetch(`${API_BASE}/generate`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ diff, style, language, quantity }),
    });

    const rateLimit = extractRateLimitHeaders(response);

    if (!response.ok) {
        let errorMessage = `HTTP ${response.status}`;
        try {
            const error = await response.json();
            errorMessage = error.message || errorMessage;
        } catch {
            // ignora erro de parse
        }
        const err = new Error(errorMessage);
        err.status = response.status;
        err.rateLimit = rateLimit;
        throw err;
    }

    const data = await response.json();
    data._rateLimit = rateLimit;
    return data;
}

/**
 * Consulta o status do rate limit sem consumir tokens.
 * @returns {Promise<{ remaining: number, limit: number, resetAt: number }>}
 */
export async function fetchRateLimit() {
    const response = await fetch(`${API_BASE}/rate-limit`);
    if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
    }
    return response.json();
}

/**
 * Verifica saúde da API.
 * @returns {Promise<{status: string, service: string}>}
 */
export async function checkHealth() {
    const response = await fetch(`${API_BASE}/health`);
    return response.json();
}
