/*
 * Salyant SDR — Production API client
 *
 * Browser -> Vercel same-origin proxy -> n8n -> Zoho / AI Router
 */

window.SALYANT_API = Object.freeze({
  chat: '/api/salyant-sdr/chat',
  accounts: '/api/salyant-sdr/api/accounts',
  mail: '/api/salyant-sdr/api/mail',
  send: '/api/salyant-sdr/api/send',
  health: '/api/salyant-sdr/health'
});

async function salyantFetch(url, options = {}) {
  const response = await fetch(url, {
    ...options,
    headers: {
      Accept: 'application/json',
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...(options.headers || {})
    }
  });

  const text = await response.text();

  let data;

  try {
    data = text ? JSON.parse(text) : {};
  } catch {
    data = { raw: text };
  }

  if (!response.ok) {
    const message =
      data?.message ||
      data?.error ||
      data?.raw ||
      `HTTP ${response.status}`;

    throw new Error(message);
  }

  return data;
}

window.SalyantAPI = {
  health() {
    return salyantFetch(window.SALYANT_API.health);
  },

  accounts() {
    return salyantFetch(window.SALYANT_API.accounts);
  },

  mail(account, limit = 25) {
    const ACCOUNT_MAP = {
      'acc-hr': 'hr@salyant.co.uk',
      'acc-main': 'yash@salyant.co.uk',
      'acc-sales-net': 'automate@salyant.net',
      'acc-sales-way': 'ai@thesalyantway.co.uk'
    };

    const params = new URLSearchParams({
      account: ACCOUNT_MAP[account] || account,
      limit: String(limit)
    });

    return salyantFetch(
      `${window.SALYANT_API.mail}?${params.toString()}`
    );
  },

  send(payload) {
    return salyantFetch(window.SALYANT_API.send, {
      method: 'POST',
      body: JSON.stringify(payload)
    });
  },

  chat(chatInput, account = 'acc-main', sessionId = 'director-session') {
    return salyantFetch(window.SALYANT_API.chat, {
      method: 'POST',
      body: JSON.stringify({
        chatInput,
        sessionId,
        account
      })
    });
  }
};
