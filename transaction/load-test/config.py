# ──────────────────────────────────────────────────────────────────────────────
# Load-test configuration
# ──────────────────────────────────────────────────────────────────────────────

# ── Service URLs ──────────────────────────────────────────────────────────────
BANKA_BASE_URL = "http://localhost:8080"
BANKB_BASE_URL = "http://localhost:8081"

# ── Bank codes (must match bank.code in the respective yaml) ─────────────────
BANKA_CODE = "AXIS_IN"
BANKB_CODE = "HDFC_IN"

# ── Bank A  →  Bank B ─────────────────────────────────────────────────────────
BANKA_SOURCE_ACCOUNT_ID     = 1
BANKA_SOURCE_ACCOUNT_NUMBER = "ACC-1003"
BANKB_DEST_ACCOUNT_NUMBER   = "ACC-1003"

# ── Bank B  →  Bank A ─────────────────────────────────────────────────────────
BANKB_SOURCE_ACCOUNT_ID     = 1
BANKB_SOURCE_ACCOUNT_NUMBER = "ACC-1003"
BANKA_DEST_ACCOUNT_NUMBER   = "ACC-1003"

# ── Payment amount ────────────────────────────────────────────────────────────
AMOUNT_PER_TX = "150.00"

# ── Test parameters ───────────────────────────────────────────────────────────
TOTAL_REQUESTS  = 2000    # number of payment requests per direction
CONCURRENCY     = 50    # max threads sending simultaneously
REQUEST_TIMEOUT = 60    # seconds — inter-bank calls wait for rail settlement

# ── Output ────────────────────────────────────────────────────────────────────
RESULTS_DIR = "results"
