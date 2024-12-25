-- Drop the table if it already exists
DROP TABLE IF EXISTS active_trades;
CREATE TABLE active_trades
(
    conid                     INTEGER,
    ticker_symbol             VARCHAR(10),
    expiration_date           VARCHAR(10),
    strike_price              NUMERIC(10, 2),
    strategy                  VARCHAR(10),
    position                  INTEGER,
    entry_dte                 INTEGER,
    current_dte               INTEGER,
    entry_underlying          NUMERIC(10, 2) DEFAULT 0.0,
    current_underlying        NUMERIC(10, 2) DEFAULT 0.0,
    entry_price               NUMERIC(10, 2) DEFAULT 0.0,
    current_price             NUMERIC(10, 2) DEFAULT 0.0,
    entry_delta               NUMERIC(10, 4) DEFAULT 0.0,
    current_delta             NUMERIC(10, 4) DEFAULT 0.0,
    entry_gamma               NUMERIC(10, 4) DEFAULT 0.0,
    current_gamma             NUMERIC(10, 4) DEFAULT 0.0,
    entry_theta               NUMERIC(10, 4) DEFAULT 0.0,
    current_theta             NUMERIC(10, 4) DEFAULT 0.0,
    entry_vega                NUMERIC(10, 4) DEFAULT 0.0,
    current_vega              NUMERIC(10, 4) DEFAULT 0.0,
    entry_delta_theta_ratio NUMERIC(10, 2) GENERATED ALWAYS AS (
        CASE
            WHEN entry_theta != 0 THEN entry_delta / entry_theta
            ELSE NULL
            END
        ) STORED,
    current_delta_theta_ratio NUMERIC(10, 2) GENERATED ALWAYS AS (
        CASE
            WHEN current_theta != 0 THEN current_delta / current_theta
            ELSE NULL
            END
        ) STORED,
    entry_iv                  NUMERIC(5, 2)  DEFAULT 0.0,
    current_iv                NUMERIC(5, 2)  DEFAULT 0.0,
    entry_ivr                 NUMERIC(5, 2)  DEFAULT 0.0,
    current_ivr               NUMERIC(5, 2)  DEFAULT 0.0,
    entry_oi                  INTEGER        DEFAULT 0.0,
    current_oi                INTEGER        DEFAULT 0.0,
    entry_gex                 NUMERIC(10, 2) DEFAULT 0.0,
    current_gex               NUMERIC(10, 2) DEFAULT 0.0,
    entry_dex                 NUMERIC(15, 2) DEFAULT 0.0,
    current_dex               NUMERIC(15, 2) DEFAULT 0.0,
    cumulative_delta          NUMERIC(10, 4) GENERATED ALWAYS AS (current_delta * position) STORED,
    cumulative_gamma          NUMERIC(10, 4) GENERATED ALWAYS AS (current_gamma * position) STORED,
    cumulative_theta          NUMERIC(10, 4) GENERATED ALWAYS AS (current_theta * position) STORED,
    cumulative_vega           NUMERIC(10, 4) GENERATED ALWAYS AS (current_vega * position) STORED,
    unrealized_pnl            NUMERIC(10, 2) GENERATED ALWAYS AS ((current_price - entry_price) * position * 100) STORED,
    entry_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (ticker_symbol, expiration_date, strike_price, strategy)
);
