DROP TABLE IF EXISTS signals;
DROP TABLE IF EXISTS trades;

CREATE TABLE IF NOT EXISTS signals
(
    signal_id        SERIAL PRIMARY KEY,

    -- Core Trade Data
    ticker_symbol    VARCHAR(10)    NOT NULL,
    underlying_price NUMERIC(10, 2) NOT NULL,
    expiration_date  DATE           NOT NULL,
    strike_price     NUMERIC(10, 2) NOT NULL,
    break_even       NUMERIC(10, 2),

    -- Profitability Metrics
    profit           NUMERIC(15, 2),
    ror              NUMERIC(10, 4),
    pop              NUMERIC(5, 2),
    pot              NUMERIC(10, 4),
    bpr              NUMERIC(15, 2),

    -- Detailed Option Metrics (Full Greeks)
    bid              NUMERIC(10, 2),
    ask              NUMERIC(10, 2),
    mid              NUMERIC(10, 2),
    volume           INT,
    oi               INT,
    delta            NUMERIC(10, 4),
    gamma            NUMERIC(10, 4),
    theta            NUMERIC(10, 4),
    vega             NUMERIC(10, 4),
    iv               NUMERIC(10, 4),
    ivr              NUMERIC(10, 4),
    dex              NUMERIC(15, 4),
    gex              NUMERIC(20, 4),

    -- Risk and Exposure Snapshots
    vega_exp         NUMERIC(15, 4),
    theta_decay_exp  NUMERIC(15, 4),
    elasticity       NUMERIC(10, 4),
    dvr              NUMERIC(10, 4),
    ovr              NUMERIC(10, 4),

    -- Market Sentiment
    sentiment        VARCHAR(50),
    pcr              NUMERIC(10, 4),

    -- Signal Lifecycle
    tte              NUMERIC(10, 4),
    generated_at     TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS trades
(
    signal_id        INT PRIMARY KEY REFERENCES signals (signal_id),

    -- Core Trade Data
    ticker_symbol    VARCHAR(10)    NOT NULL,
    underlying_price NUMERIC(10, 2) NOT NULL,
    expiration_date  DATE           NOT NULL,
    strike_price     NUMERIC(10, 2) NOT NULL,
    break_even       NUMERIC(10, 2),

    -- Profitability Metrics
    ror              NUMERIC(10, 4),
    pop              NUMERIC(5, 2),
    pot              NUMERIC(10, 4),

    -- Detailed Option Metrics (Full Greeks)
    bid              NUMERIC(10, 2),
    ask              NUMERIC(10, 2),
    mid              NUMERIC(10, 2),
    volume           INT,
    oi               INT,
    delta            NUMERIC(10, 4),
    gamma            NUMERIC(10, 4),
    theta            NUMERIC(10, 4),
    vega             NUMERIC(10, 4),
    iv               NUMERIC(10, 4),
    ivr              NUMERIC(10, 4),
    dex              NUMERIC(15, 4),
    gex              NUMERIC(20, 4),

    -- Risk and Exposure Snapshots
    vega_exp         NUMERIC(15, 4),
    theta_decay_exp  NUMERIC(15, 4),
    elasticity       NUMERIC(10, 4),
    dvr              NUMERIC(10, 4),
    ovr              NUMERIC(10, 4),

    -- Market Sentiment
    sentiment        VARCHAR(50),
    pcr              NUMERIC(10, 4),

    -- Signal Lifecycle
    tte              NUMERIC(10, 4)
);
