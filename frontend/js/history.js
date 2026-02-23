const STORAGE_KEY = 'commitgen_history';
const MAX_ITEMS = 20;

/**
 * @typedef {Object} HistoryEntry
 * @property {string} id
 * @property {string[]} messages
 * @property {string} style
 * @property {string} language
 * @property {string} timestamp
 */

/**
 * Retorna todos os itens do histórico.
 * @returns {HistoryEntry[]}
 */
export function getHistory() {
    try {
        return JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]');
    } catch {
        return [];
    }
}

/**
 * Salva uma nova entrada no histórico.
 * @param {string[]} messages
 * @param {string} style
 * @param {string} language
 */
export function saveToHistory(messages, style, language) {
    const history = getHistory();
    const entry = {
        id: Date.now().toString(),
        messages,
        style,
        language,
        timestamp: new Date().toISOString(),
    };

    history.unshift(entry);

    // Limita ao máximo de itens
    if (history.length > MAX_ITEMS) {
        history.splice(MAX_ITEMS);
    }

    localStorage.setItem(STORAGE_KEY, JSON.stringify(history));
}

/**
 * Limpa todo o histórico.
 */
export function clearHistory() {
    localStorage.removeItem(STORAGE_KEY);
}

/**
 * Formata a data de forma legível.
 * @param {string} isoString
 * @returns {string}
 */
export function formatDate(isoString) {
    const date = new Date(isoString);
    const now = new Date();
    const diff = now - date;

    const minutes = Math.floor(diff / 60000);
    const hours = Math.floor(diff / 3600000);
    const days = Math.floor(diff / 86400000);

    if (minutes < 1) return 'agora mesmo';
    if (minutes < 60) return `${minutes}min atrás`;
    if (hours < 24) return `${hours}h atrás`;
    if (days === 1) return 'ontem';
    return date.toLocaleDateString('pt-BR');
}
