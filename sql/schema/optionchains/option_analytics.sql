-- Drop existing tables if they exist
DROP TABLE IF EXISTS zero_gamma_ticker_expiration;
DROP TABLE IF EXISTS top_strikes_ticker_expiration;
DROP TABLE IF EXISTS sentiment_ticker_expiration;
DROP TABLE IF EXISTS ticker_expirations;
DROP TABLE IF EXISTS key_levels;

-- Create zero_gamma_ticker_expiration table
CREATE TABLE zero_gamma_ticker_expiration
(
    ticker_symbol    VARCHAR(10),    -- Ticker symbol
    expiration_date  VARCHAR(10),    -- Expiration date
    zero_gamma_level NUMERIC(10, 2), -- Strike price where net GEX crosses zero
    flip_gamma       NUMERIC(15, 4), -- Gamma flip (absolute value of change around zero level)
    PRIMARY KEY (ticker_symbol, expiration_date, zero_gamma_level)
);

-- Create top_strikes_ticker_expiration table
CREATE TABLE top_strikes_ticker_expiration
(
    ticker_symbol      VARCHAR(10),       -- Ticker symbol
    expiration_date    VARCHAR(10),       -- Expiration date
    strike_price       NUMERIC(10, 2),    -- Strike price

    -- Metrics for identification
    net_gex            NUMERIC(20, 4),    -- Net Gamma Exposure at the strike (Call GEX - Put GEX)
    net_dex            NUMERIC(20, 4),    -- Net Delta Exposure at the strike (Call DEX + Put DEX)
    open_interest      INT,               -- Total Open Interest (Call OI + Put OI)
    volume             INT,               -- Total Volume (Call Volume + Put Volume)
    zgs_count          INTEGER DEFAULT 0, -- Zero Gamma Strike count
    top_count          INTEGER DEFAULT 0, -- Top Strike count
    mm_hedge_behaviour VARCHAR(20),       -- Market Maker Hedge Behavior (e.g., "Buy to Hedge", "Sell to Hedge")

    PRIMARY KEY (ticker_symbol, expiration_date, strike_price)
);

-- Create sentiment_ticker_expiration table
CREATE TABLE sentiment_ticker_expiration
(
    ticker_symbol         VARCHAR(10),    -- Ticker symbol
    expiration_date       VARCHAR(10),    -- Expiration date
    sentiment             VARCHAR(20),    -- Sentiment (e.g., Bullish, Bearish, Neutral)
    call_gex_total        NUMERIC(16, 4), -- Total Gamma Exposure (Call)
    put_gex_total         NUMERIC(16, 4), -- Total Gamma Exposure (Put)
    difference_percentage NUMERIC(10, 4), -- Percentage difference between Call and Put GEX
    put_call_ratio        NUMERIC(10, 4), -- Put/Call volume ratio
    PRIMARY KEY (ticker_symbol, expiration_date)
);

-- Create ticker_expirations table
CREATE TABLE IF NOT EXISTS ticker_expirations
(
    ticker_symbol          VARCHAR(10),
    expiration_date        VARCHAR(10),

    -- Call side metrics
    avg_call_delta         NUMERIC(10, 4),
    avg_call_volume        INT,
    avg_call_oi            INT,
    avg_call_bid           NUMERIC(10, 2),
    avg_call_ask           NUMERIC(10, 2),
    avg_call_mid           NUMERIC(10, 2),
    avg_call_iv            NUMERIC(10, 2),
    avg_call_gex           NUMERIC(20, 4),
    avg_call_dex           NUMERIC(15, 4),

    -- Put side metrics
    avg_put_delta          NUMERIC(10, 4),
    avg_put_volume         INT,
    avg_put_oi             INT,
    avg_put_bid            NUMERIC(10, 2),
    avg_put_ask            NUMERIC(10, 2),
    avg_put_mid            NUMERIC(10, 2),
    avg_put_iv             NUMERIC(10, 2),
    avg_put_gex            NUMERIC(20, 4),
    avg_put_dex            NUMERIC(15, 4),

    avg_key_level_distance NUMERIC(10, 2),
    put_call_ratio         NUMERIC(10, 4),

    expected_move_dollars  NUMERIC(10, 4), -- Expected move in dollar terms
    expected_move_percent  NUMERIC(8, 4),  -- Expected move as a percentage of current price
    implied_volatility     NUMERIC(8, 4),  -- Implied volatility used in the calculation
    days_to_expiration     INT,            -- Number of days until expiration
    calculation_date       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (ticker_symbol, expiration_date)
);


CREATE TABLE IF NOT EXISTS key_levels
(
    ticker_symbol      VARCHAR(10),
    strike_price       NUMERIC(10, 2),
    net_gex            NUMERIC(16, 4),
    net_dex            NUMERIC(16, 4),
    max_zgs_count      INTEGER,
    max_top_count      INTEGER,
    open_interest      INTEGER, -- Added column for Open Interest
    volume             INTEGER, -- Added column for Volume
    mm_hedge_behaviour VARCHAR(20),
    PRIMARY KEY (ticker_symbol, strike_price)
);
