-- Drop old tables if they exist
DROP TABLE IF EXISTS sentiment_ticker_expiration;
DROP TABLE IF EXISTS sentiment_ticker_overall;
DROP TABLE IF EXISTS top_strikes_ticker_expiration;
DROP TABLE IF EXISTS zero_gamma_ticker_expiration;
DROP TABLE IF EXISTS ticker_expirations;
DROP TABLE IF EXISTS key_levels;

-- Create: sentiment_ticker_expiration
CREATE TABLE IF NOT EXISTS sentiment_ticker_expiration
(
    ticker_symbol       VARCHAR(10),    -- Ticker symbol
    expiration_date     VARCHAR(10),    -- Expiration date

    -- Derived Metrics
    sentiment_strength  NUMERIC(15, 4), -- Sentiment strength = PCR * DEX
    stability_indicator NUMERIC(15, 4), -- Stability indicator = GEX

    PRIMARY KEY (ticker_symbol, expiration_date)
);

-- Create: sentiment_ticker_overall
CREATE TABLE IF NOT EXISTS sentiment_ticker_overall
(
    ticker_symbol               VARCHAR(10),    -- Ticker symbol

    -- Aggregated Metrics
    overall_sentiment_strength  NUMERIC(15, 4), -- Overall sentiment strength = avg PCR * total DEX
    overall_stability_indicator NUMERIC(15, 4), -- Overall stability indicator = total GEX

    PRIMARY KEY (ticker_symbol)
);

-- Create: top_strikes_ticker_expiration
CREATE TABLE IF NOT EXISTS top_strikes_ticker_expiration
(
    ticker_symbol   VARCHAR(10),       -- Ticker symbol
    expiration_date VARCHAR(10),       -- Expiration date
    strike_price    NUMERIC(10, 2),    -- Strike price

    -- Derived Metrics
    zgs_count       INTEGER DEFAULT 0, -- Zero Gamma Strike count
    top_count       INTEGER DEFAULT 0, -- Top Strike count

    PRIMARY KEY (ticker_symbol, expiration_date, strike_price)
);

-- Create: ticker_expirations
CREATE TABLE IF NOT EXISTS ticker_expirations
(
    ticker_symbol         VARCHAR(10),    -- Ticker symbol
    expiration_date       VARCHAR(10),    -- Expiration date

    -- Aggregated Metrics
    avg_call_delta        NUMERIC(10, 4), -- Average Call delta
    avg_put_delta         NUMERIC(10, 4), -- Average Put delta
    put_call_ratio        NUMERIC(10, 4), -- Put/Call Ratio
    expected_move_dollars NUMERIC(10, 4), -- Expected move in dollar terms
    expected_move_percent NUMERIC(8, 4),  -- Expected move as a percentage

    PRIMARY KEY (ticker_symbol, expiration_date)
);

-- Create: key_levels
CREATE TABLE IF NOT EXISTS key_levels
(
    ticker_symbol VARCHAR(10),    -- Ticker symbol
    strike_price  NUMERIC(10, 2), -- Strike price

    -- Derived Metrics
    max_zgs_count INTEGER,        -- Max Zero Gamma Strike count
    max_top_count INTEGER,        -- Max Top Strike count

    PRIMARY KEY (ticker_symbol, strike_price)
);
