/*
 * Salyant SDR — live mailbox integration
 *
 * This intentionally does NOT destroy the existing dashboard.
 * It provides a clean live data layer which the UI can call.
 */

(function () {
  const ACCOUNT_ORDER = [
    'acc-hr',
    'acc-main',
    'acc-sales-net',
    'acc-sales-way'
  ];

  const ACCOUNT_META = {
    'acc-hr': {
      name: 'HR',
      address: 'hr@salyant.co.uk'
    },
    'acc-main': {
      name: 'Main',
      address: 'yash@salyant.co.uk'
    },
    'acc-sales-net': {
      name: 'Sales',
      address: 'automate@salyant.net'
    },
    'acc-sales-way': {
      name: 'Sales Way',
      address: 'ai@thesalyantway.co.uk'
    }
  };

  function normalizeAccounts(response) {
    const rows = Array.isArray(response)
      ? response
      : Array.isArray(response?.accounts)
        ? response.accounts
        : [];

    const EMAIL_TO_ID = {
      'hr@salyant.co.uk': 'acc-hr',
      'yash@salyant.co.uk': 'acc-main',
      'automate@salyant.net': 'acc-sales-net',
      'ai@thesalyantway.co.uk': 'acc-sales-way'
    };

    return rows.map(account => {
      const addresses = Array.isArray(account.addr)
        ? account.addr
        : Array.isArray(account.emailAddress)
          ? account.emailAddress
          : [];

      const email =
        account.email ||
        account.emailAddress ||
        addresses.find(x => x.isPrimary)?.mailId ||
        addresses[0]?.mailId ||
        account.address ||
        '';

      const id =
        EMAIL_TO_ID[email] ||
        account.id ||
        null;

      return {
        ...(ACCOUNT_META[id] || {}),
        ...account,
        id
      };
    }).filter(account => account.id);
  }

  function normalizeMail(response) {
    if (Array.isArray(response)) {
      return response;
    }

    if (Array.isArray(response?.messages)) {
      return response.messages;
    }

    if (Array.isArray(response?.data)) {
      return response.data;
    }

    return [];
  }

  window.SalyantLive = {
    ACCOUNT_ORDER,
    ACCOUNT_META,

    async loadAccounts() {
      const response = await window.SalyantAPI.accounts();
      return normalizeAccounts(response);
    },

    async loadMailbox(account, limit = 25) {
      if (!ACCOUNT_ORDER.includes(account)) {
        throw new Error(`Unknown mailbox: ${account}`);
      }

      const response = await window.SalyantAPI.mail(account, limit);
      return normalizeMail(response);
    },

    async loadAllMail(limit = 25) {
      const result = {};

      await Promise.all(
        ACCOUNT_ORDER.map(async account => {
          result[account] =
            await window.SalyantLive.loadMailbox(account, limit);
        })
      );

      return result;
    },

    async send(payload) {
      return window.SalyantAPI.send(payload);
    },

    async chat(message, account = 'acc-main', sessionId = 'director-session') {
      return window.SalyantAPI.chat(message, account, sessionId);
    },

    async health() {
      return window.SalyantAPI.health();
    }
  };

  /*
   * Make live API state available to the existing dashboard.
   */
  window.SalyantLiveState = {
    accounts: [],
    mail: {},
    healthy: false,
    loading: false,
    lastError: null
  };

  window.refreshSalyantLiveData = async function () {
    const state = window.SalyantLiveState;

    state.loading = true;
    state.lastError = null;

    try {
      state.accounts = await window.SalyantLive.loadAccounts();
      state.mail = await window.SalyantLive.loadAllMail(25);
      state.healthy = true;

      window.dispatchEvent(
        new CustomEvent('salyant-live-ready', {
          detail: state
        })
      );

      return state;
    } catch (error) {
      state.healthy = false;
      state.lastError = error;

      window.dispatchEvent(
        new CustomEvent('salyant-live-error', {
          detail: error
        })
      );

      throw error;
    } finally {
      state.loading = false;
    }
  };
})();
