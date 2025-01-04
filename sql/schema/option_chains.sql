-- Drop existing tables if they exist
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
    last_updated       TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create optionchains table
CREATE TABLE IF NOT EXISTS optionchains
(
    ticker_symbol             VARCHAR(10),                         -- Ticker symbol
    expiration_date           VARCHAR(10),                         -- Expiration date
    strike_price              NUMERIC(10, 2),                      -- Strike price

-- Call Metrics
    call_bid                  NUMERIC(10, 2),                      -- Bid price (Call)
    call_ask                  NUMERIC(10, 2),                      -- Ask price (Call)
    call_mid                  NUMERIC(10, 2),                      -- Mid price (Call)
    call_volume               INT,                                 -- Volume (Call)
    call_oi                   INT,                                 -- Open interest (Call)
    call_delta                NUMERIC(10, 4),                      -- Delta (Call)
    call_gamma                NUMERIC(10, 4),                      -- Gamma (Call)
    call_theta                NUMERIC(10, 4),                      -- Theta (Call)
    call_vega                 NUMERIC(10, 4),                      -- Vega (Call)
    call_iv                   NUMERIC(10, 4),                      -- Implied volatility (Call)
    call_ivr                  NUMERIC(10, 4),                      -- IV rank (Call)
    call_dex                  NUMERIC(15, 4),                      -- Delta exposure (Call)
    call_gex                  NUMERIC(20, 4),                      -- Gamma exposure (Call)

-- Call Generated Columns
    call_break_even           NUMERIC(10, 4) GENERATED ALWAYS AS (
        strike_price + COALESCE(call_ask, 0)
        ) STORED,                                                  -- Break-even for Calls using Ask price
    call_vega_exposure        NUMERIC(15, 4) GENERATED ALWAYS AS (
        call_vega * call_oi
        ) STORED,                                                  -- Vega exposure (Call)
    call_theta_decay_exposure NUMERIC(15, 4) GENERATED ALWAYS AS (
        call_theta * call_oi
        ) STORED,                                                  -- Theta decay exposure (Call)
    call_elasticity           NUMERIC(10, 4) GENERATED ALWAYS AS (
        CASE WHEN call_delta != 0 THEN call_gamma / call_delta ELSE 0.0 END
        ) STORED,                                                  -- Elasticity (Call)
    call_delta_vega_ratio     NUMERIC(10, 4) GENERATED ALWAYS AS (
        CASE WHEN call_vega != 0 THEN call_delta / call_vega ELSE 0.0 END
        ) STORED,                                                  -- Delta/Vega ratio (Call)
    call_oi_volume_ratio      NUMERIC(10, 4) GENERATED ALWAYS AS (
        CASE WHEN call_volume != 0 THEN call_oi::NUMERIC / call_volume ELSE 0.0 END
        ) STORED,                                                  -- OI/Volume ratio (Call)

-- Put Metrics
    put_bid                   NUMERIC(10, 2),                      -- Bid price (Put)
    put_ask                   NUMERIC(10, 2),                      -- Ask price (Put)
    put_mid                   NUMERIC(10, 2),                      -- Mid price (Put)
    put_volume                INT,                                 -- Volume (Put)
    put_oi                    INT,                                 -- Open interest (Put)
    put_delta                 NUMERIC(10, 4),                      -- Delta (Put)
    put_gamma                 NUMERIC(10, 4),                      -- Gamma (Put)
    put_theta                 NUMERIC(10, 4),                      -- Theta (Put)
    put_vega                  NUMERIC(10, 4),                      -- Vega (Put)
    put_iv                    NUMERIC(10, 4),                      -- Implied volatility (Put)
    put_ivr                   NUMERIC(10, 4),                      -- IV rank (Put)
    put_dex                   NUMERIC(15, 4),                      -- Delta exposure (Put)
    put_gex                   NUMERIC(20, 4),                      -- Gamma exposure (Put)

-- Put Generated Columns
    put_break_even            NUMERIC(10, 4) GENERATED ALWAYS AS (
        strike_price - COALESCE(put_bid, 0)
        ) STORED,                                                  -- Break-even for Puts using Bid price
    put_vega_exposure         NUMERIC(15, 4) GENERATED ALWAYS AS (
        put_vega * put_oi
        ) STORED,                                                  -- Vega exposure (Put)
    put_theta_decay_exposure  NUMERIC(15, 4) GENERATED ALWAYS AS (
        put_theta * put_oi
        ) STORED,                                                  -- Theta decay exposure (Put)
    put_elasticity            NUMERIC(10, 4) GENERATED ALWAYS AS (
        CASE WHEN put_delta != 0 THEN put_gamma / put_delta ELSE 0.0 END
        ) STORED,                                                  -- Elasticity (Put)
    put_delta_vega_ratio      NUMERIC(10, 4) GENERATED ALWAYS AS (
        CASE WHEN put_vega != 0 THEN put_delta / put_vega ELSE 0.0 END
        ) STORED,                                                  -- Delta/Vega ratio (Put)
    put_oi_volume_ratio       NUMERIC(10, 4) GENERATED ALWAYS AS (
        CASE WHEN put_volume != 0 THEN put_oi::NUMERIC / put_volume ELSE 0.0 END
        ) STORED,                                                  -- OI/Volume ratio (Put)

-- Shared Columns
    implied_move              NUMERIC(10, 4),                      -- Implied move
    skew                      NUMERIC(10, 4),                      -- Call/Put IV skew
    skew_delta                NUMERIC(10, 4),                      -- Call/Put delta skew
    percentile_rank_iv        NUMERIC(10, 4),                      -- Percentile rank of IV
    percentile_rank_delta     NUMERIC(10, 4),                      -- Percentile rank of delta
    probability_of_profit     NUMERIC(10, 4),                      -- Probability of profit (POP)
    probability_of_touch      NUMERIC(10, 4),                      -- Probability of touch (POT)
    expected_move_percentage  NUMERIC(10, 4),                      -- Expected move percentage
    time_to_expiration        NUMERIC(10, 4),                      -- Time to expiration (TTE)
    notional_value_oi         NUMERIC(20, 4),                      -- Notional value of OI

    PRIMARY KEY (ticker_symbol, expiration_date, strike_price)
);
