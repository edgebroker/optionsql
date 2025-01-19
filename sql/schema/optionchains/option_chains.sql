-- Drop existing tables if they exist
DROP VIEW IF EXISTS adaptive_thresholds;
DROP TABLE IF EXISTS optionchains;
DROP TABLE IF EXISTS ticker;

-- Create ticker table
CREATE TABLE IF NOT EXISTS ticker
(
    ticker_symbol      VARCHAR(10) PRIMARY KEY,
    segment            VARCHAR(50),
    current_price      NUMERIC(10, 2),
    iv_historical_low  NUMERIC(10, 4),
    iv_historical_high NUMERIC(10, 4),
    next_earnings_date VARCHAR(10),
    next_earnings_time VARCHAR(20),
    last_updated       TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create optionchains table
CREATE TABLE IF NOT EXISTS optionchains
(
    ticker_symbol             VARCHAR(10),                         -- Ticker symbol
    expiration_date           VARCHAR(10),                         -- Expiration date
    strike_price              NUMERIC(10, 2),                      -- Strike price
    underlying_price          NUMERIC(10, 2),                      -- Current underlying price

-- Call Metrics
    call_bid                  NUMERIC(10, 2),                      -- Bid price (Call)
    call_ask                  NUMERIC(10, 2),                      -- Ask price (Call)
    call_mid                  NUMERIC(10, 2),                      -- Mid price (Call)
    call_spread               NUMERIC(10, 4) GENERATED ALWAYS AS (
        GREATEST(0, COALESCE(call_ask, 0) - COALESCE(call_bid, 0))
        ) STORED,
    call_volume               INT,                                 -- Volume (Call)
    call_oi                   INT,                                 -- Open interest (Call)
    call_delta                NUMERIC(10, 4),                      -- Delta (Call)
    call_gamma                NUMERIC(10, 4),                      -- Gamma (Call)
    call_theta                NUMERIC(10, 4),                      -- Theta (Call)
    call_vega                 NUMERIC(10, 4),                      -- Vega (Call)
    call_iv                   NUMERIC(10, 4),                      -- Implied volatility (Call)
    call_ivr                  NUMERIC(10, 4),                      -- IV rank (Call)

-- Call Exposure Metrics (Generated)
    call_dex                  NUMERIC(15, 4) GENERATED ALWAYS AS (
        call_delta * call_oi * 100
        ) STORED,                                                  -- Delta Exposure (Call)

    call_gex                  NUMERIC(20, 4) GENERATED ALWAYS AS (
        call_gamma * call_oi * 100
        ) STORED,                                                  -- Gamma Exposure (Call)

-- Call Generated Columns
    call_break_even           NUMERIC(10, 4) GENERATED ALWAYS AS (
        strike_price + COALESCE(call_ask, 0)
        ) STORED,                                                  -- Break-even for Calls using Ask price
    call_vega_exp             NUMERIC(15, 4) GENERATED ALWAYS AS (
        call_vega * call_oi
        ) STORED,                                                  -- Vega exposure (Call)
    call_theta_decay_exp      NUMERIC(15, 4) GENERATED ALWAYS AS (
        call_theta * call_oi
        ) STORED,                                                  -- Theta decay exposure (Call)
    call_elasticity           NUMERIC(10, 4) GENERATED ALWAYS AS (
        CASE WHEN call_delta != 0 THEN call_gamma / call_delta ELSE 0.0 END
        ) STORED,                                                  -- Elasticity (Call)
    call_dvr                  NUMERIC(10, 4) GENERATED ALWAYS AS (
        CASE WHEN call_vega != 0 THEN call_delta / call_vega ELSE 0.0 END
        ) STORED,                                                  -- Delta/Vega ratio (Call)
    call_ovr                  NUMERIC(10, 4) GENERATED ALWAYS AS (
        CASE
            WHEN call_volume = 0 THEN NULL  -- Avoid misleading 0
            WHEN call_oi::NUMERIC / call_volume > 100 THEN 100  -- Cap at 100
            ELSE call_oi::NUMERIC / call_volume
            END
        ) STORED,                                                  -- OI/Volume ratio (Call)
    call_ror NUMERIC(10, 4) GENERATED ALWAYS AS (
        CASE
            WHEN (strike_price - underlying_price) > 0 THEN
                (call_bid / (strike_price - underlying_price + call_bid)) * 100
            ELSE
                0
            END
        ) STORED,                                                   -- Return on Risk (Call)
    call_bpr NUMERIC(10, 2) GENERATED ALWAYS AS (
        GREATEST(
                (0.20 * underlying_price - GREATEST(0, strike_price - underlying_price) + call_bid) * 100,
                (0.10 * underlying_price + call_bid) * 100
                )
        ) STORED,                                                   -- Buying Power Reduction (Call)
-- Call-Specific Probability Metrics
    call_pop                  NUMERIC(10, 4) GENERATED ALWAYS AS (
        1 - ABS(call_delta)
        ) STORED,                                                  -- POP (Call)
    call_pot                  NUMERIC(10, 4) GENERATED ALWAYS AS (
        2 * ABS(call_delta)
        ) STORED,                                                  -- POT (Call)
    call_pr_iv                NUMERIC(10, 4),                      -- IV Percentile Rank (Call)
    call_pr_delta             NUMERIC(10, 4) GENERATED ALWAYS AS (
        call_delta
        ) STORED,                                                  -- Delta Percentile Rank (Call)
    call_nv_oi                NUMERIC(20, 4) GENERATED ALWAYS AS (
        call_oi * strike_price * 100
        ) STORED,                                                  -- Notional Value (Call)

-- Put Metrics
    put_bid                   NUMERIC(10, 2),                      -- Bid price (Put)
    put_ask                   NUMERIC(10, 2),                      -- Ask price (Put)
    put_mid                   NUMERIC(10, 2),                      -- Mid price (Put)
    put_spread                NUMERIC(10, 4) GENERATED ALWAYS AS (
        GREATEST(0, COALESCE(put_ask, 0) - COALESCE(put_bid, 0))
        ) STORED,
    put_volume                INT,                                 -- Volume (Put)
    put_oi                    INT,                                 -- Open interest (Put)
    put_delta                 NUMERIC(10, 4),                      -- Delta (Put)
    put_gamma                 NUMERIC(10, 4),                      -- Gamma (Put)
    put_theta                 NUMERIC(10, 4),                      -- Theta (Put)
    put_vega                  NUMERIC(10, 4),                      -- Vega (Put)
    put_iv                    NUMERIC(10, 4),                      -- Implied volatility (Put)
    put_ivr                   NUMERIC(10, 4),                      -- IV rank (Put)

-- Put Exposure Metrics (Generated)
    put_dex                   NUMERIC(15, 4) GENERATED ALWAYS AS (
        put_delta * put_oi * 100
        ) STORED,                                                  -- Delta Exposure (Put)

    put_gex                   NUMERIC(20, 4) GENERATED ALWAYS AS (
        put_gamma * put_oi * 100
        ) STORED,                                                  -- Gamma Exposure (Put)

-- Put Generated Columns
    put_break_even            NUMERIC(10, 4) GENERATED ALWAYS AS (
        strike_price - COALESCE(put_bid, 0)
        ) STORED,                                                  -- Break-even for Puts using Bid price
    put_vega_exp              NUMERIC(15, 4) GENERATED ALWAYS AS (
        put_vega * put_oi
        ) STORED,                                                  -- Vega exposure (Put)
    put_theta_decay_exp       NUMERIC(15, 4) GENERATED ALWAYS AS (
        put_theta * put_oi
        ) STORED,                                                  -- Theta decay exposure (Put)
    put_elasticity            NUMERIC(10, 4) GENERATED ALWAYS AS (
        CASE WHEN put_delta != 0 THEN put_gamma / put_delta ELSE 0.0 END
        ) STORED,                                                  -- Elasticity (Put)
    put_dvr                   NUMERIC(10, 4) GENERATED ALWAYS AS (
        CASE WHEN put_vega != 0 THEN put_delta / put_vega ELSE 0.0 END
        ) STORED,                                                  -- Delta/Vega ratio (Put)
    put_ovr                   NUMERIC(10, 4) GENERATED ALWAYS AS (
        CASE
            WHEN put_volume = 0 THEN NULL
            WHEN put_oi::NUMERIC / put_volume > 100 THEN 100
            ELSE put_oi::NUMERIC / put_volume
            END
        ) STORED,                                                  -- OI/Volume ratio (Put)
    put_ror                 NUMERIC(10, 4) GENERATED ALWAYS AS (
        CASE
            WHEN (strike_price - put_bid) > 0 THEN
                (put_bid / (strike_price - put_bid)) * 100
            ELSE 0
            END
        ) STORED,                                                   -- Return on Risk (Put)
    put_bpr NUMERIC(10, 2) GENERATED ALWAYS AS (
        (strike_price * 100 - put_bid * 100)
        ) STORED,                                                   -- Buying Power Reduction (Put)
    
-- Put-Specific Probability Metrics
    put_pop                  NUMERIC(10, 4) GENERATED ALWAYS AS (
        1 + put_delta
        ) STORED,                                                  -- POP (Put)
    put_pot                  NUMERIC(10, 4) GENERATED ALWAYS AS (
        2 * ABS(put_delta)
        ) STORED,                                                  -- POT (Put)
    put_pr_iv                NUMERIC(10, 4),                       -- IV Percentile Rank (Put)
    put_pr_delta             NUMERIC(10, 4) GENERATED ALWAYS AS (
        (put_delta + 1) / 2
        ) STORED,                                                  -- Delta Percentile Rank (Put)
    put_nv_oi                NUMERIC(20, 4) GENERATED ALWAYS AS (
        put_oi * strike_price * 100
        ) STORED,                                                  -- Notional Value (Put)

-- Shared Metrics
    pcr NUMERIC(10, 4) GENERATED ALWAYS AS (                       -- Put/Call Ratio
        CASE
            WHEN (call_oi + put_oi) > 0 THEN put_oi::NUMERIC / NULLIF(call_oi + put_oi, 0)
            ELSE 0
            END
        ) STORED,
    implied_move             NUMERIC(10, 4) GENERATED ALWAYS AS (
        underlying_price * put_iv * 0.025
        ) STORED,                                                  -- Implied move
    skew                     NUMERIC(10, 4) GENERATED ALWAYS AS (
        call_iv - put_iv
        ) STORED,                                                  -- IV Skew
    skew_delta NUMERIC(10, 4) GENERATED ALWAYS AS (
        call_delta - put_delta
        ) STORED,                                                  -- skew delta
    tte                      NUMERIC(10, 4),

    PRIMARY KEY (ticker_symbol, expiration_date, strike_price)
);


CREATE VIEW adaptive_thresholds AS
SELECT
    oc.ticker_symbol,
    oc.expiration_date,
    PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY oc.put_oi) AS median_oi,
    PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY oc.put_spread) AS tight_spread,
    PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY oc.put_ror) AS high_ror,
    AVG(oc.put_bpr) AS avg_bpr,
    PERCENTILE_CONT(0.6) WITHIN GROUP (ORDER BY oc.put_ivr) AS high_ivr,
    AVG(oc.put_gex) AS avg_gex,
    AVG(oc.put_dex) AS avg_dex,
    AVG(
            CASE WHEN oc.call_oi + oc.put_oi > 0 THEN oc.put_oi::NUMERIC / NULLIF(oc.call_oi, 0) ELSE NULL END
    ) AS avg_pcr,
    PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY oc.skew) AS controlled_skew,
    PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY oc.skew_delta) AS balanced_skew_delta,
    PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY oc.put_theta_decay_exp) AS minimal_theta_decay,
    PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY oc.put_vega_exp) AS high_vega_exp,
    PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY oc.put_elasticity) AS balanced_elasticity,
    PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY oc.put_dvr) AS balanced_dvr
FROM optionchains oc
GROUP BY oc.ticker_symbol, oc.expiration_date;
